package com.omega.intervaltimer

import android.os.Bundle
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omega.intervaltimer.data.SavedCounter
import com.omega.intervaltimer.data.SavedCountersStore
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.TimerState
import com.omega.intervaltimer.model.TimelineItemType
import com.omega.intervaltimer.model.WorkoutConfig
import com.omega.intervaltimer.model.defaultWorkoutConfig
import com.omega.intervaltimer.ui.TimerViewModel

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

private enum class AppScreen {
    HOME,
    CONFIG,
    RUN
}

private data class TimerPreset(
    val id: String,
    val name: String,
    val config: WorkoutConfig
)

@Composable
private fun TimerApp(vm: TimerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val store = remember { SavedCountersStore(context) }
    val builtInPresets = remember { defaultBuiltInPresets() }
    var savedCounters by remember { mutableStateOf(store.load()) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    val builtInDisplay = builtInPresets.map {
        TimerPreset(id = it.name, name = it.name, config = it.config)
    }
    val savedDisplay = savedCounters.map {
        TimerPreset(id = it.id, name = it.name, config = it.config)
    }
    val allCounters = builtInDisplay + savedDisplay

    when (currentScreen) {
        AppScreen.HOME -> HomeScreen(
            counters = allCounters,
            onSelectPreset = { preset ->
                vm.applyConfig(preset.config)
                currentScreen = AppScreen.RUN
            },
            onCreateCounter = { currentScreen = AppScreen.CONFIG }
        )

        AppScreen.CONFIG -> ConfigScreen(
            state = state,
            onBack = { currentScreen = AppScreen.HOME },
            onApplyAndRun = { formData ->
                vm.applyConfig(formData.toWorkoutConfig())
                currentScreen = AppScreen.RUN
            },
            onSave = { formData ->
                val counterName = formData.counterName.ifBlank { "Compteur sauvegarde" }
                val saved = SavedCounter(name = counterName, config = formData.toWorkoutConfig())
                val next = (savedCounters + saved)
                savedCounters = next
                store.save(next)
            }
        )

        AppScreen.RUN -> RunScreen(
            state = state,
            onBackHome = {
                vm.reset()
                currentScreen = AppScreen.HOME
            },
            onGoToConfig = { currentScreen = AppScreen.CONFIG },
            onStartPause = vm::startPause,
            onReset = vm::reset,
            onNext = vm::next
        )
    }
}

private data class DefaultPreset(
    val name: String,
    val config: WorkoutConfig
)

private fun defaultBuiltInPresets(): List<DefaultPreset> {
    return listOf(
        DefaultPreset("Lancement rapide", defaultWorkoutConfig())
    )
}

@Composable
private fun HomeScreen(
    counters: List<TimerPreset>,
    onSelectPreset: (TimerPreset) -> Unit,
    onCreateCounter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compteur a rebours", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Compteurs", fontWeight = FontWeight.Bold)
                counters.forEach { preset ->
                    Button(
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(preset.name)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCreateCounter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Creer un compteur")
                }
            }
        }
    }
}

private data class EditablePhase(
    val name: String,
    val durationSec: Int
)

private data class ConfigFormData(
    val counterName: String,
    val phases: List<EditablePhase>,
    val sets: Int,
    val cycles: Int,
    val restBetweenCyclesSec: Int
)

private fun ConfigFormData.toWorkoutConfig(): WorkoutConfig {
    val cleanedPhases = phases.mapIndexed { index, phase ->
        Phase(
            name = phase.name.ifBlank { "Phase ${index + 1}" },
            durationSec = phase.durationSec.coerceAtLeast(1)
        )
    }.ifEmpty {
        listOf(Phase(name = "Phase 1", durationSec = 30))
    }

    return WorkoutConfig(
        phases = cleanedPhases,
        sets = sets.coerceAtLeast(1),
        cycles = cycles.coerceAtLeast(1),
        restBetweenCyclesSec = restBetweenCyclesSec.coerceAtLeast(0)
    )
}

@Composable
private fun ConfigScreen(
    state: TimerState,
    onBack: () -> Unit,
    onApplyAndRun: (ConfigFormData) -> Unit,
    onSave: (ConfigFormData) -> Unit
) {
    val config = state.config

    var counterName by remember { mutableStateOf("") }
    val phases = remember { mutableStateListOf<EditablePhase>() }
    var sets by remember { mutableIntStateOf(config.sets) }
    var cycles by remember { mutableIntStateOf(config.cycles) }
    var restBetweenCyclesSec by remember { mutableIntStateOf(config.restBetweenCyclesSec) }
    var saveMessage by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        phases.clear()
        config.phases.forEach { phase ->
            phases += EditablePhase(name = phase.name, durationSec = phase.durationSec)
        }
        if (phases.isEmpty()) {
            phases += EditablePhase(name = "Phase 1", durationSec = 30)
        }
        sets = config.sets
        cycles = config.cycles
        restBetweenCyclesSec = config.restBetweenCyclesSec
        saveMessage = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = counterName,
                    onValueChange = { counterName = it },
                    label = { Text("Nom du compteur") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Phases", fontWeight = FontWeight.Bold)
                phases.forEachIndexed { index, phase ->
                    PhaseRow(
                        index = index,
                        phase = phase,
                        canRemove = phases.size > 1,
                        onUpdateName = { value ->
                            phases[index] = phase.copy(name = value)
                        },
                        onUpdateDuration = { value ->
                            phases[index] = phase.copy(durationSec = value)
                        },
                        onRemove = { phases.removeAt(index) }
                    )
                }

                Button(
                    onClick = {
                        phases += EditablePhase(
                            name = "Phase ${phases.size + 1}",
                            durationSec = 30
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ajouter une phase")
                }

                IntInputRow("Sets", sets) { sets = it }
                CyclesAndRestRow(
                    cycles = cycles,
                    onCyclesChange = { cycles = it },
                    restSec = restBetweenCyclesSec,
                    onRestChange = { restBetweenCyclesSec = it }
                )

                if (saveMessage.isNotBlank()) {
                    Text(saveMessage)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Accueil")
                    }
                    Button(
                        onClick = {
                            val form = ConfigFormData(
                                counterName = counterName,
                                phases = phases.toList(),
                                sets = sets,
                                cycles = cycles,
                                restBetweenCyclesSec = restBetweenCyclesSec
                            )
                            onSave(form)
                            saveMessage = "Compteur sauvegarde."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sauvegarder")
                    }
                }

                Button(
                    onClick = {
                        onApplyAndRun(
                            ConfigFormData(
                                counterName = counterName,
                                phases = phases.toList(),
                                sets = sets,
                                cycles = cycles,
                                restBetweenCyclesSec = restBetweenCyclesSec
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lancer ce compteur")
                }
            }
        }
    }
}

@Composable
private fun PhaseRow(
    index: Int,
    phase: EditablePhase,
    canRemove: Boolean,
    onUpdateName: (String) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NameDurationRow(
            label = "Phase ${index + 1}",
            name = phase.name,
            onNameChange = onUpdateName,
            duration = phase.durationSec,
            onDurationChange = onUpdateDuration
        )
        if (canRemove) {
            Button(onClick = onRemove) {
                Text("Supprimer")
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
    onReset: () -> Unit,
    onNext: () -> Unit
) {
    val currentItem = state.timeline.getOrNull(state.currentIndex)
    val isRestPhase = currentItem?.type == TimelineItemType.REST
    val totalDuration = state.timeline.sumOf { it.phase.durationSec }.coerceAtLeast(1)
    val progress = 1f - (state.totalRemainingSec.toFloat() / totalDuration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Execution", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phase: ${currentItem?.phase?.name ?: "Aucune"}")
                Text("Temps restant: ${formatTime(state.remainingSec)}")
                if (isRestPhase) {
                    Text("Repos entre les cycles")
                } else {
                    Text("Set: ${currentItem?.setIndex ?: "-"} | Cycle: ${currentItem?.cycleIndex ?: "-"}")
                }
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Progression globale: ${(progress * 100).toInt()}%")
                if (state.isFinished) Text("Termine")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartPause) {
                        Text(if (!state.isRunning || state.isPaused) "Start" else "Pause")
                    }
                    Button(onClick = onReset) { Text("Reset") }
                    Button(onClick = onNext) { Text("Next") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGoToConfig, modifier = Modifier.weight(1f)) {
                        Text("Configurer")
                    }
                    Button(onClick = onBackHome, modifier = Modifier.weight(1f)) {
                        Text("Accueil")
                    }
                }
            }
        }
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
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("$label nom") },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = duration.toString(),
            onValueChange = { onDurationChange(it.toIntOrNull() ?: 0) },
            label = { Text("$label sec") },
            modifier = Modifier.width(120.dp)
        )
    }
}

@Composable
private fun IntInputRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { onChange(it.toIntOrNull() ?: 1) },
            modifier = Modifier.width(120.dp),
            singleLine = true
        )
    }
}

@Composable
private fun CyclesAndRestRow(
    cycles: Int,
    onCyclesChange: (Int) -> Unit,
    restSec: Int,
    onRestChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cycles", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = cycles.toString(),
            onValueChange = { onCyclesChange(it.toIntOrNull() ?: 1) },
            modifier = Modifier.width(90.dp),
            singleLine = true
        )
        Text("Repos (sec)", modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = restSec.toString(),
            onValueChange = { onRestChange(it.toIntOrNull() ?: 0) },
            modifier = Modifier.width(110.dp),
            singleLine = true
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = (seconds.coerceAtLeast(0)) / 60
    val s = (seconds.coerceAtLeast(0)) % 60
    return "%02d:%02d".format(m, s)
}
