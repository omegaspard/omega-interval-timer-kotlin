package com.omega.intervaltimer.model

import java.util.UUID

data class Phase(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val durationSec: Int
)

data class WorkoutConfig(
    val intro: Phase?,
    val phases: List<Phase>,
    val sets: Int,
    val cycles: Int,
    val conclusion: Phase?
)

enum class TimelineItemType {
    INTRO,
    MAIN,
    CONCLUSION
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
        intro = Phase(name = "Introduction", durationSec = 10),
        phases = listOf(
            Phase(name = "Phase 1", durationSec = 30),
            Phase(name = "Phase 2", durationSec = 60),
            Phase(name = "Phase 3", durationSec = 90)
        ),
        sets = 2,
        cycles = 2,
        conclusion = Phase(name = "Conclusion", durationSec = 15)
    )
}
