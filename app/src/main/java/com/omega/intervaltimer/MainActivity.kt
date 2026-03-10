package com.omega.intervaltimer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omega.intervaltimer.domain.buildTimeline
import com.omega.intervaltimer.data.LaunchHistoryItem
import com.omega.intervaltimer.data.SavedCounter
import com.omega.intervaltimer.data.SavedCountersStore
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.TimerState
import com.omega.intervaltimer.model.TimelineItemType
import com.omega.intervaltimer.model.WorkTimer
import com.omega.intervaltimer.model.WorkoutConfig
import com.omega.intervaltimer.model.defaultWorkoutConfig
import com.omega.intervaltimer.ui.TimerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                TimerApp()
            }
        }
    }
}

private enum class AppScreen { HOME, CONFIG, RUN, HISTORY }

private data class TimerPreset(val id: String, val name: String, val config: WorkoutConfig)

private data class EditableWorkTimer(
    val name: String,
    val durationSec: Int,
    val restAfterSec: Int = 10
)

private data class ConfigFormData(
    val counterName: String,
    val preparationName: String,
    val preparationDurationSec: Int,
    val workTimers: List<EditableWorkTimer>,
    val sets: Int,
    val restBetweenSetsSec: Int,
    val cycles: Int,
    val restBetweenCyclesSec: Int,
    val setAsQuickLaunch: Boolean
)

@Composable
private fun TimerApp(vm: TimerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val store = remember { SavedCountersStore(context) }
    var savedCounters by remember { mutableStateOf(store.load()) }
    var quickLaunchConfig by remember { mutableStateOf(store.loadQuickLaunchConfig() ?: defaultWorkoutConfig()) }
    var launchHistory by remember { mutableStateOf(store.loadLaunchHistory()) }
    var selectedSavedCounterId by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    val quickPreset = TimerPreset(id = "default", name = "Lancement rapide", config = quickLaunchConfig)
    val savedPresets = savedCounters.map { TimerPreset(it.id, it.name, it.config) }

    fun recordLaunch(counterName: String) {
        store.incrementLaunch(counterName)
        launchHistory = store.loadLaunchHistory()
    }

    when (currentScreen) {
        AppScreen.HOME -> HomeScreen(
            quickLaunchPreset = quickPreset,
            savedCounters = savedPresets,
            onSelectQuickLaunch = {
                vm.applyConfig(quickPreset.config)
                selectedSavedCounterId = null
                recordLaunch(quickPreset.name)
                currentScreen = AppScreen.RUN
            },
            onSelectSavedCounter = { preset ->
                vm.applyConfig(preset.config)
                selectedSavedCounterId = preset.id
                recordLaunch(preset.name)
                currentScreen = AppScreen.RUN
            },
            onDeleteSavedCounter = { preset ->
                val next = savedCounters.filterNot { it.id == preset.id }
                savedCounters = next
                store.save(next)
                if (selectedSavedCounterId == preset.id) {
                    selectedSavedCounterId = null
                }
            },
            onCreateCounter = { currentScreen = AppScreen.CONFIG },
            onOpenHistory = { currentScreen = AppScreen.HISTORY }
        )

        AppScreen.CONFIG -> ConfigScreen(
            state = state,
            canDeleteSavedCounter = selectedSavedCounterId != null,
            onBack = { currentScreen = AppScreen.HOME },
            onApplyAndRun = { form ->
                val config = form.toWorkoutConfig()
                vm.applyConfig(config)
                if (form.setAsQuickLaunch) {
                    quickLaunchConfig = config
                    store.saveQuickLaunchConfig(config)
                }
                val nameForHistory = form.counterName.ifBlank { "Configuration manuelle" }
                recordLaunch(nameForHistory)
                currentScreen = AppScreen.RUN
            },
            onSave = { form ->
                val config = form.toWorkoutConfig()
                val savedName = form.counterName.trim()
                if (savedName.isBlank()) {
                    "Le nom du compteur est obligatoire."
                } else {
                    val nameExists = savedCounters.any { it.name.equals(savedName, ignoreCase = true) }
                    if (nameExists) {
                        "Un compteur avec ce nom existe deja."
                    } else {
                        val saved = SavedCounter(name = savedName, config = config)
                        val next = savedCounters + saved
                        savedCounters = next
                        store.save(next)
                        if (form.setAsQuickLaunch) {
                            quickLaunchConfig = config
                            store.saveQuickLaunchConfig(config)
                        }
                        null
                    }
                }
            },
            onDeleteSavedCounter = {
                val targetId = selectedSavedCounterId ?: return@ConfigScreen
                val next = savedCounters.filterNot { it.id == targetId }
                savedCounters = next
                store.save(next)
                selectedSavedCounterId = null
                vm.reset()
                currentScreen = AppScreen.HOME
            }
        )

        AppScreen.RUN -> RunScreen(
            state = state,
            onBackHome = {
                vm.reset()
                selectedSavedCounterId = null
                currentScreen = AppScreen.HOME
            },
            onGoToConfig = { currentScreen = AppScreen.CONFIG },
            onStartPause = vm::startPause,
            onRestart = {
                vm.reset()
                vm.startPause()
            }
        )

        AppScreen.HISTORY -> HistoryScreen(
            history = launchHistory,
            onBack = { currentScreen = AppScreen.HOME },
            onClear = {
                store.clearLaunchHistory()
                launchHistory = emptyList()
            }
        )
    }
}

private fun ConfigFormData.toWorkoutConfig(): WorkoutConfig {
    val prep = if (preparationDurationSec > 0) {
        Phase(
            name = preparationName.ifBlank { "Preparation" },
            durationSec = preparationDurationSec.coerceAtLeast(1)
        )
    } else null
    val work = workTimers.mapIndexed { index, timer ->
        WorkTimer(
            name = timer.name.ifBlank { "Travail ${index + 1}" },
            durationSec = timer.durationSec.coerceAtLeast(1),
            restAfterSec = timer.restAfterSec.coerceAtLeast(0)
        )
    }.ifEmpty { listOf(WorkTimer(name = "Travail 1", durationSec = 30, restAfterSec = 10)) }
    return WorkoutConfig(
        preparation = prep,
        workTimers = work,
        sets = sets.coerceAtLeast(1),
        restBetweenSetsSec = restBetweenSetsSec.coerceAtLeast(0),
        cycles = cycles.coerceAtLeast(1),
        restBetweenCyclesSec = restBetweenCyclesSec.coerceAtLeast(0)
    )
}

@Composable
private fun HomeScreen(
    quickLaunchPreset: TimerPreset,
    savedCounters: List<TimerPreset>,
    onSelectQuickLaunch: () -> Unit,
    onSelectSavedCounter: (TimerPreset) -> Unit,
    onDeleteSavedCounter: (TimerPreset) -> Unit,
    onCreateCounter: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compteur a rebours", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lancement rapide", fontWeight = FontWeight.Bold)
                Button(onClick = onSelectQuickLaunch, modifier = Modifier.fillMaxWidth()) {
                    Text(quickLaunchPreset.name)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compteurs sauvegardes", fontWeight = FontWeight.Bold)
                if (savedCounters.isEmpty()) {
                    Text("Aucun compteur sauvegarde.")
                } else {
                    savedCounters.forEach { preset ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { onSelectSavedCounter(preset) }, modifier = Modifier.weight(1f)) {
                                Text(preset.name)
                            }
                            Button(onClick = { onDeleteSavedCounter(preset) }) {
                                Text("Supprimer")
                            }
                        }
                    }
                }
                Button(onClick = onCreateCounter, modifier = Modifier.fillMaxWidth()) {
                    Text("Creer un compteur")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Historique", fontWeight = FontWeight.Bold)
                Button(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("Voir historique")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history: List<LaunchHistoryItem>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Historique", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) {
                    Text("Aucun lancement enregistre.")
                } else {
                    history.forEach {
                        Text("${it.counterName} - ${formatHistoryDate(it.launchedAtMs)}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Accueil") }
                    Button(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Effacer historique") }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    state: TimerState,
    canDeleteSavedCounter: Boolean,
    onBack: () -> Unit,
    onApplyAndRun: (ConfigFormData) -> Unit,
    onSave: (ConfigFormData) -> String?,
    onDeleteSavedCounter: () -> Unit
) {
    val cfg = state.config
    var counterName by remember { mutableStateOf("") }
    var preparationName by remember { mutableStateOf(cfg.preparation?.name ?: "Preparation") }
    var preparationDurationSec by remember { mutableIntStateOf(cfg.preparation?.durationSec ?: 0) }
    val workTimers = remember { mutableStateListOf<EditableWorkTimer>() }
    var sets by remember { mutableIntStateOf(cfg.sets) }
    var restBetweenSetsSec by remember { mutableIntStateOf(cfg.restBetweenSetsSec) }
    var cycles by remember { mutableIntStateOf(cfg.cycles) }
    var restBetweenCyclesSec by remember { mutableIntStateOf(cfg.restBetweenCyclesSec) }
    var setAsQuickLaunch by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }

    LaunchedEffect(cfg) {
        preparationName = cfg.preparation?.name ?: "Preparation"
        preparationDurationSec = cfg.preparation?.durationSec ?: 0
        workTimers.clear()
        cfg.workTimers.forEach { workTimers += EditableWorkTimer(it.name, it.durationSec, it.restAfterSec) }
        if (workTimers.isEmpty()) workTimers += EditableWorkTimer("Travail 1", 30, 10)
        sets = cfg.sets
        restBetweenSetsSec = cfg.restBetweenSetsSec
        cycles = cfg.cycles
        restBetweenCyclesSec = cfg.restBetweenCyclesSec
        setAsQuickLaunch = false
        saveError = ""
    }

    fun currentForm() = ConfigFormData(
        counterName = counterName,
        preparationName = preparationName,
        preparationDurationSec = preparationDurationSec,
        workTimers = workTimers.toList(),
        sets = sets,
        restBetweenSetsSec = restBetweenSetsSec,
        cycles = cycles,
        restBetweenCyclesSec = restBetweenCyclesSec,
        setAsQuickLaunch = setAsQuickLaunch
    )
    val totalDurationSec = buildTimeline(currentForm().toWorkoutConfig()).sumOf { it.phase.durationSec }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = counterName,
                    onValueChange = { counterName = it },
                    placeholder = { Text("Nom du compteur", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Preparation", fontWeight = FontWeight.Bold)
                NameDurationRow("Pre-set", preparationName, { preparationName = it }, preparationDurationSec, { preparationDurationSec = it })
                Text("Travail", fontWeight = FontWeight.Bold)
                workTimers.forEachIndexed { index, t ->
                    NameDurationRestRow(
                        label = "Timer ${index + 1}",
                        name = t.name,
                        onNameChange = { workTimers[index] = t.copy(name = it) },
                        duration = t.durationSec,
                        onDurationChange = { workTimers[index] = t.copy(durationSec = it) },
                        restSec = t.restAfterSec,
                        onRestChange = { workTimers[index] = t.copy(restAfterSec = it) },
                        showRestField = index < workTimers.lastIndex
                    )
                    if (workTimers.size > 1) {
                        Button(onClick = { workTimers.removeAt(index) }) { Text("Supprimer") }
                    }
                }
                Button(onClick = { workTimers += EditableWorkTimer("Travail ${workTimers.size + 1}", 30, 10) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ajouter un timer de travail")
                }
                Text("Sets", fontWeight = FontWeight.Bold)
                SetsAndRestRow(sets, { sets = it }, restBetweenSetsSec, { restBetweenSetsSec = it })
                Text("Cycles", fontWeight = FontWeight.Bold)
                CyclesAndRestRow(cycles, { cycles = it }, restBetweenCyclesSec, { restBetweenCyclesSec = it })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = setAsQuickLaunch, onCheckedChange = { setAsQuickLaunch = it })
                    Text("Utiliser cette configuration pour Lancement rapide", modifier = Modifier.padding(top = 12.dp))
                }
                Text("Temps total: ${formatTime(totalDurationSec)}")
                if (saveError.isNotBlank()) {
                    Text(saveError)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Accueil") }
                    Button(
                        onClick = {
                            saveError = onSave(currentForm()) ?: ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Sauvegarder") }
                }
                if (canDeleteSavedCounter) {
                    Button(onClick = onDeleteSavedCounter, modifier = Modifier.fillMaxWidth()) {
                        Text("Supprimer ce compteur")
                    }
                }
                Button(onClick = { onApplyAndRun(currentForm()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lancer ce compteur")
                }
            }
        }
    }
}

@Composable
private fun RunScreen(
    state: TimerState,
    onBackHome: () -> Unit,
    onGoToConfig: () -> Unit,
    onStartPause: () -> Unit,
    onRestart: () -> Unit
) {
    val current = state.timeline.getOrNull(state.currentIndex)
    val totalDuration = state.timeline.sumOf { it.phase.durationSec }.coerceAtLeast(1)
    val totalRemaining = state.totalRemainingSec.coerceAtLeast(0)
    val currentPhaseDuration = current?.phase?.durationSec?.coerceAtLeast(1) ?: 1
    val phaseProgress = 1f - (state.remainingSec.coerceAtLeast(0).toFloat() / currentPhaseDuration.toFloat())
    val totalProgress = 1f - (totalRemaining.toFloat() / totalDuration.toFloat())
    val phaseLabel = when {
        current?.type == TimelineItemType.WORK -> "TRAVAIL"
        current?.type == TimelineItemType.REST && current.phase.name == "Repos cycle" -> "REPOS CYCLE"
        current?.type == TimelineItemType.REST -> "REPOS"
        current?.type == TimelineItemType.PREPARATION -> "PREPARATION"
        else -> "AUCUNE"
    }
    val phaseColor = when {
        current?.type == TimelineItemType.WORK -> "#EF4444"
        current?.type == TimelineItemType.REST && current.phase.name == "Repos cycle" -> "#3B82F6"
        current?.type == TimelineItemType.REST -> "#22C55E"
        else -> "#6B7280"
    }
    val primaryIcon = when {
        state.isFinished -> "↻"
        state.isRunning && !state.isPaused -> "⏸"
        else -> "▶"
    }
    val payload = JSONObject().apply {
        put("phaseLabel", phaseLabel)
        put("phaseTime", formatTime(state.remainingSec))
        put("totalRemaining", formatTime(totalRemaining))
        put("currentCycle", current?.cycleIndex ?: 0)
        put("totalCycles", state.config.cycles.coerceAtLeast(1))
        put("currentSet", current?.setIndex ?: 0)
        put("totalSets", state.config.sets.coerceAtLeast(1))
        put("phaseProgress", phaseProgress.coerceIn(0f, 1f).toDouble())
        put("totalProgress", totalProgress.coerceIn(0f, 1f).toDouble())
        put("phaseColor", phaseColor)
        put("primaryIcon", primaryIcon)
        put("isFinished", state.isFinished)
    }.toString()

    val jsBridge = remember(onBackHome, onStartPause, onRestart, onGoToConfig, state.isFinished) {
        ExecutionJsBridge(
            onHome = onBackHome,
            onPrimary = { if (state.isFinished) onRestart() else onStartPause() },
            onRestart = onRestart,
            onConfig = onGoToConfig
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(jsBridge, "Bridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("window.renderTimerFromString(${JSONObject.quote(payload)});", null)
                    }
                }
                loadUrl("file:///android_asset/execution_view.html")
            }
        },
        update = { webView ->
            webView.evaluateJavascript("window.renderTimerFromString(${JSONObject.quote(payload)});", null)
        }
    )

    DisposableEffect(Unit) {
        onDispose { /* no-op */ }
    }
}

private class ExecutionJsBridge(
    private val onHome: () -> Unit,
    private val onPrimary: () -> Unit,
    private val onRestart: () -> Unit,
    private val onConfig: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun home() {
        handler.post { onHome() }
    }

    @JavascriptInterface
    fun primary() {
        handler.post { onPrimary() }
    }

    @JavascriptInterface
    fun restart() {
        handler.post { onRestart() }
    }

    @JavascriptInterface
    fun config() {
        handler.post { onConfig() }
    }
}

@Composable
private fun NameDurationRow(
    label: String,
    name: String,
    onNameChange: (String) -> Unit,
    duration: Int,
    onDurationChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("$label nom") }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = duration.toString(), onValueChange = { onDurationChange(it.toIntOrNull() ?: 0) }, label = { Text("$label sec") }, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun NameDurationRestRow(
    label: String,
    name: String,
    onNameChange: (String) -> Unit,
    duration: Int,
    onDurationChange: (Int) -> Unit,
    restSec: Int,
    onRestChange: (Int) -> Unit,
    showRestField: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("$label nom") }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = duration.toString(), onValueChange = { onDurationChange(it.toIntOrNull() ?: 0) }, label = { Text("Temps sec") }, modifier = Modifier.width(95.dp), singleLine = true)
        if (showRestField) {
            OutlinedTextField(value = restSec.toString(), onValueChange = { onRestChange(it.toIntOrNull() ?: 0) }, label = { Text("Repos sec") }, modifier = Modifier.width(95.dp), singleLine = true)
        }
    }
}

@Composable
private fun SetsAndRestRow(sets: Int, onSetsChange: (Int) -> Unit, restSec: Int, onRestChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sets", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(value = sets.toString(), onValueChange = { onSetsChange(it.toIntOrNull() ?: 1) }, modifier = Modifier.width(90.dp), singleLine = true)
        Text("Repos (sec)", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(value = restSec.toString(), onValueChange = { onRestChange(it.toIntOrNull() ?: 0) }, modifier = Modifier.width(110.dp), singleLine = true)
    }
}

@Composable
private fun CyclesAndRestRow(cycles: Int, onCyclesChange: (Int) -> Unit, restSec: Int, onRestChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cycles", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(value = cycles.toString(), onValueChange = { onCyclesChange(it.toIntOrNull() ?: 1) }, modifier = Modifier.width(90.dp), singleLine = true)
        Text("Repos (sec)", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(value = restSec.toString(), onValueChange = { onRestChange(it.toIntOrNull() ?: 0) }, modifier = Modifier.width(110.dp), singleLine = true)
    }
}

private fun formatTime(seconds: Int): String {
    val m = (seconds.coerceAtLeast(0)) / 60
    val s = (seconds.coerceAtLeast(0)) % 60
    return "%02d:%02d".format(m, s)
}

private fun formatHistoryDate(launchedAtMs: Long): String {
    if (launchedAtMs <= 0L) return "Date inconnue"
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(launchedAtMs))
}
