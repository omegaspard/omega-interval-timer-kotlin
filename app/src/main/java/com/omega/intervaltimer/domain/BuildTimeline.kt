package com.omega.intervaltimer.domain

import com.omega.intervaltimer.model.TimelineItem
import com.omega.intervaltimer.model.TimelineItemType
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.WorkTimer
import com.omega.intervaltimer.model.WorkoutConfig

fun buildTimeline(config: WorkoutConfig): List<TimelineItem> {
    val timeline = mutableListOf<TimelineItem>()
    val workTimers = config.workTimers.ifEmpty {
        listOf(WorkTimer(name = "Travail", durationSec = 30, restAfterSec = 10))
    }

    config.preparation?.let { prep ->
        if (prep.durationSec > 0) {
            timeline += TimelineItem(
                type = TimelineItemType.PREPARATION,
                phase = prep,
                cycleIndex = null,
                setIndex = null
            )
        }
    }

    for (cycle in 1..config.cycles.coerceAtLeast(1)) {
        for (set in 1..config.sets.coerceAtLeast(1)) {
            workTimers.forEachIndexed { index, timer ->
                timeline += TimelineItem(
                    type = TimelineItemType.WORK,
                    phase = Phase(name = timer.name, durationSec = timer.durationSec),
                    cycleIndex = cycle,
                    setIndex = set
                )
                val hasNextTimer = index < workTimers.lastIndex
                if (hasNextTimer && timer.restAfterSec > 0) {
                    timeline += TimelineItem(
                        type = TimelineItemType.REST,
                        phase = Phase(name = "Repos phase", durationSec = timer.restAfterSec),
                        cycleIndex = cycle,
                        setIndex = set
                    )
                }
            }
            if (set < config.sets && config.restBetweenSetsSec > 0) {
                timeline += TimelineItem(
                    type = TimelineItemType.REST,
                    phase = Phase(name = "Repos set", durationSec = config.restBetweenSetsSec),
                    cycleIndex = cycle,
                    setIndex = set
                )
            }
        }
        if (cycle < config.cycles && config.restBetweenCyclesSec > 0) {
            timeline += TimelineItem(
                type = TimelineItemType.REST,
                phase = Phase(name = "Repos cycle", durationSec = config.restBetweenCyclesSec),
                cycleIndex = cycle,
                setIndex = null
            )
        }
    }

    return timeline
}
