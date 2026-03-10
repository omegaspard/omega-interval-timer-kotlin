package com.omega.intervaltimer.model

import java.util.UUID

data class Phase(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationSec: Int
)

data class WorkTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationSec: Int,
    val restAfterSec: Int = 10
)

data class WorkoutConfig(
    val preparation: Phase?,
    val workTimers: List<WorkTimer>,
    val sets: Int,
    val restBetweenSetsSec: Int = 0,
    val cycles: Int,
    val restBetweenCyclesSec: Int = 0
)

enum class TimelineItemType {
    PREPARATION,
    WORK,
    REST
}

data class TimelineItem(
    val type: TimelineItemType,
    val phase: Phase,
    val cycleIndex: Int?,
    val setIndex: Int?
)

data class TimerState(
    val config: WorkoutConfig = defaultWorkoutConfig(),
    val timeline: List<TimelineItem> = emptyList(),
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val currentIndex: Int = 0,
    val remainingSec: Int = 0,
    val totalRemainingSec: Int = 0
)

fun defaultWorkoutConfig(): WorkoutConfig {
    return WorkoutConfig(
        preparation = Phase(name = "Preparation", durationSec = 30),
        workTimers = listOf(
            WorkTimer(name = "Travail", durationSec = 20, restAfterSec = 10)
        ),
        sets = 4,
        restBetweenSetsSec = 45,
        cycles = 3,
        restBetweenCyclesSec = 180
    )
}
