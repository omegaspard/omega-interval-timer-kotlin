package com.omega.intervaltimer.domain

import com.omega.intervaltimer.model.TimelineItem
import com.omega.intervaltimer.model.TimelineItemType
import com.omega.intervaltimer.model.WorkoutConfig

fun buildTimeline(config: WorkoutConfig): List<TimelineItem> {
    val timeline = mutableListOf<TimelineItem>()

    config.intro?.let {
        timeline += TimelineItem(
            type = TimelineItemType.INTRO,
            phase = it,
            cycleIndex = null,
            setIndex = null
        )
    }

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
    }

    config.conclusion?.let {
        timeline += TimelineItem(
            type = TimelineItemType.CONCLUSION,
            phase = it,
            cycleIndex = null,
            setIndex = null
        )
    }

    return timeline
}
