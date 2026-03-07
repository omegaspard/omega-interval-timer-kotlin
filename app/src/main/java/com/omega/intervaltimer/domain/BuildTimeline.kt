package com.omega.intervaltimer.domain

import com.omega.intervaltimer.model.TimelineItem
import com.omega.intervaltimer.model.TimelineItemType
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.WorkoutConfig

fun buildTimeline(config: WorkoutConfig): List<TimelineItem> {
    val timeline = mutableListOf<TimelineItem>()

    for (cycle in 1..config.cycles.coerceAtLeast(1)) {
        for (set in 1..config.sets.coerceAtLeast(1)) {
            config.phases.forEach { phase ->
                timeline += TimelineItem(
                    type = TimelineItemType.MAIN,
                    phase = phase,
                    cycleIndex = cycle,
                    setIndex = set
                )
            }
        }
        if (cycle < config.cycles && config.restBetweenCyclesSec > 0) {
            timeline += TimelineItem(
                type = TimelineItemType.REST,
                phase = Phase(name = "Repos", durationSec = config.restBetweenCyclesSec),
                cycleIndex = cycle,
                setIndex = null
            )
        }
    }

    return timeline
}
