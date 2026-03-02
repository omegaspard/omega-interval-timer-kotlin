package com.omega.intervaltimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omega.intervaltimer.model.defaultWorkoutConfig
import com.omega.intervaltimer.ui.TimerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                TimerScreen()
            }
        }
    }
}

@Composable
private fun TimerScreen(vm: TimerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val config = state.config

    var introName by remember { mutableStateOf(config.intro?.name ?: "Introduction") }
    var introDuration by remember { mutableIntStateOf(config.intro?.durationSec ?: 0) }
    var p1Name by remember { mutableStateOf(config.phases[0].name) }
    var p1Duration by remember { mutableIntStateOf(config.phases[0].durationSec) }
    var p2Name by remember { mutableStateOf(config.phases[1].name) }
    var p2Duration by remember { mutableIntStateOf(config.phases[1].durationSec) }
    var p3Name by remember { mutableStateOf(config.phases[2].name) }
    var p3Duration by remember { mutableIntStateOf(config.phases[2].durationSec) }
    var sets by remember { mutableIntStateOf(config.sets) }
    var cycles by remember { mutableIntStateOf(config.cycles) }
    var conclusionName by remember { mutableStateOf(config.conclusion?.name ?: "Conclusion") }
    var conclusionDuration by remember { mutableIntStateOf(config.conclusion?.durationSec ?: 0) }

    val currentItem = state.timeline.getOrNull(state.currentIndex)
    val totalDuration = state.timeline.sumOf { it.phase.durationSec }.coerceAtLeast(1)
    val progress = 1f - (state.totalRemainingSec.toFloat() / totalDuration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compteur a rebours sequentiel", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuration", fontWeight = FontWeight.Bold)
                NameDurationRow("Intro", introName, { introName = it }, introDuration, { introDuration = it })
                NameDurationRow("Phase 1", p1Name, { p1Name = it }, p1Duration, { p1Duration = it })
                NameDurationRow("Phase 2", p2Name, { p2Name = it }, p2Duration, { p2Duration = it })
                NameDurationRow("Phase 3", p3Name, { p3Name = it }, p3Duration, { p3Duration = it })
                NameDurationRow("Conclusion", conclusionName, { conclusionName = it }, conclusionDuration, { conclusionDuration = it })

                IntInputRow("Sets", sets) { sets = it }
                IntInputRow("Cycles", cycles) { cycles = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.updateConfig(
                            introName, introDuration,
                            p1Name, p1Duration,
                            p2Name, p2Duration,
                            p3Name, p3Duration,
                            sets, cycles,
                            conclusionName, conclusionDuration
                        )
                    }) {
                        Text("Appliquer")
                    }

                    Button(onClick = {
                        val d = defaultWorkoutConfig()
                        introName = d.intro?.name ?: "Introduction"
                        introDuration = d.intro?.durationSec ?: 0
                        p1Name = d.phases[0].name
                        p1Duration = d.phases[0].durationSec
                        p2Name = d.phases[1].name
                        p2Duration = d.phases[1].durationSec
                        p3Name = d.phases[2].name
                        p3Duration = d.phases[2].durationSec
                        sets = d.sets
                        cycles = d.cycles
                        conclusionName = d.conclusion?.name ?: "Conclusion"
                        conclusionDuration = d.conclusion?.durationSec ?: 0
                        vm.updateConfig(
                            introName, introDuration,
                            p1Name, p1Duration,
                            p2Name, p2Duration,
                            p3Name, p3Duration,
                            sets, cycles,
                            conclusionName, conclusionDuration
                        )
                    }) {
                        Text("Preset")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Execution", fontWeight = FontWeight.Bold)
                Text("Phase: ${currentItem?.phase?.name ?: "Aucune"}")
                Text("Temps restant: ${formatTime(state.remainingSec)}")
                Text("Set: ${currentItem?.setIndex ?: "-"} | Cycle: ${currentItem?.cycleIndex ?: "-"}")
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Progression globale: ${(progress * 100).toInt()}%")
                if (state.isFinished) Text("Termine")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.startPause() }) {
                        Text(if (!state.isRunning || state.isPaused) "Start" else "Pause")
                    }
                    Button(onClick = { vm.reset() }) { Text("Reset") }
                    Button(onClick = { vm.next() }) { Text("Next") }
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

private fun formatTime(seconds: Int): String {
    val m = (seconds.coerceAtLeast(0)) / 60
    val s = (seconds.coerceAtLeast(0)) % 60
    return "%02d:%02d".format(m, s)
}
