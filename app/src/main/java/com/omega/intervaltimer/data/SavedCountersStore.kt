package com.omega.intervaltimer.data

import android.content.Context
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.WorkTimer
import com.omega.intervaltimer.model.WorkoutConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedCounter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: WorkoutConfig
)

data class LaunchHistoryItem(
    val counterName: String,
    val launchedAtMs: Long
)

class SavedCountersStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<SavedCounter> {
        val raw = prefs.getString(KEY_COUNTERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val configObj = obj.optJSONObject("config") ?: continue
                    add(
                        SavedCounter(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = name,
                            config = configFromJson(configObj)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(counters: List<SavedCounter>) {
        val array = JSONArray()
        counters.forEach { counter ->
            array.put(
                JSONObject().apply {
                    put("id", counter.id)
                    put("name", counter.name)
                    put("config", configToJson(counter.config))
                }
            )
        }
        prefs.edit().putString(KEY_COUNTERS, array.toString()).apply()
    }

    fun loadQuickLaunchConfig(): WorkoutConfig? {
        val raw = prefs.getString(KEY_QUICK_LAUNCH_CONFIG, null) ?: return null
        return try {
            configFromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun saveQuickLaunchConfig(config: WorkoutConfig) {
        prefs.edit()
            .putString(KEY_QUICK_LAUNCH_CONFIG, configToJson(config).toString())
            .apply()
    }

    fun loadLaunchHistory(): List<LaunchHistoryItem> {
        val raw = prefs.getString(KEY_LAUNCH_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("counterName", "").trim()
                    val launchedAtMs = obj.optLong("launchedAtMs", 0L)
                    if (name.isNotBlank()) {
                        add(
                            LaunchHistoryItem(
                                counterName = name,
                                launchedAtMs = launchedAtMs
                            )
                        )
                    }
                }
            }.sortedByDescending { it.launchedAtMs }
        } catch (_: Exception) {
            // Backward compatibility with the previous "name -> total count" map format.
            try {
                val legacy = JSONObject(raw)
                legacy.keys().asSequence().mapNotNull { name ->
                    val count = legacy.optInt(name, 0)
                    if (name.isBlank() || count <= 0) null else LaunchHistoryItem(
                        counterName = "$name (x$count)",
                        launchedAtMs = 0L
                    )
                }.toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun incrementLaunch(counterName: String) {
        val cleanedName = counterName.trim()
        if (cleanedName.isBlank()) return
        val next = listOf(
            LaunchHistoryItem(
                counterName = cleanedName,
                launchedAtMs = System.currentTimeMillis()
            )
        ) + loadLaunchHistory()
        saveLaunchHistory(next)
    }

    fun clearLaunchHistory() {
        prefs.edit().remove(KEY_LAUNCH_HISTORY).apply()
    }

    private fun saveLaunchHistory(items: List<LaunchHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("counterName", item.counterName)
                    put("launchedAtMs", item.launchedAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_LAUNCH_HISTORY, array.toString()).apply()
    }

    private fun configToJson(config: WorkoutConfig): JSONObject {
        return JSONObject().apply {
            put("preparation", config.preparation?.let { phaseToJson(it) })
            put("workTimers", JSONArray().apply {
                config.workTimers.forEach { put(workTimerToJson(it)) }
            })
            put("sets", config.sets)
            put("restBetweenSetsSec", config.restBetweenSetsSec)
            put("cycles", config.cycles)
            put("restBetweenCyclesSec", config.restBetweenCyclesSec)
        }
    }

    private fun configFromJson(obj: JSONObject): WorkoutConfig {
        val preparation = obj.optJSONObject("preparation")?.let { phaseFromJson(it) }

        val workTimers = obj.optJSONArray("workTimers")?.let { workTimersFromJson(it) } ?: emptyList()
        val legacyWorkTimers = if (workTimers.isEmpty()) {
            val phasesArray = obj.optJSONArray("phases") ?: JSONArray()
            buildList {
                for (i in 0 until phasesArray.length()) {
                    val phaseObj = phasesArray.optJSONObject(i) ?: continue
                    val phase = phaseFromJson(phaseObj)
                    add(
                        WorkTimer(
                            name = phase.name,
                            durationSec = phase.durationSec,
                            restAfterSec = 10
                        )
                    )
                }
            }
        } else {
            emptyList()
        }
        val finalWorkTimers = (workTimers.ifEmpty { legacyWorkTimers }).ifEmpty {
            listOf(WorkTimer(name = "Travail 1", durationSec = 30, restAfterSec = 10))
        }

        return WorkoutConfig(
            preparation = preparation,
            workTimers = finalWorkTimers,
            sets = obj.optInt("sets", 1).coerceAtLeast(1),
            restBetweenSetsSec = obj.optInt("restBetweenSetsSec", 0).coerceAtLeast(0),
            cycles = obj.optInt("cycles", 1).coerceAtLeast(1),
            restBetweenCyclesSec = obj.optInt("restBetweenCyclesSec", 0).coerceAtLeast(0)
        )
    }

    private fun workTimersFromJson(array: JSONArray): List<WorkTimer> {
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    WorkTimer(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Travail"),
                        durationSec = obj.optInt("durationSec", 30).coerceAtLeast(1),
                        restAfterSec = obj.optInt("restAfterSec", 10).coerceAtLeast(0)
                    )
                )
            }
        }
    }

    private fun workTimerToJson(timer: WorkTimer): JSONObject {
        return JSONObject().apply {
            put("id", timer.id)
            put("name", timer.name)
            put("durationSec", timer.durationSec)
            put("restAfterSec", timer.restAfterSec)
        }
    }

    private fun phaseToJson(phase: Phase): JSONObject {
        return JSONObject().apply {
            put("id", phase.id)
            put("name", phase.name)
            put("durationSec", phase.durationSec)
        }
    }

    private fun phaseFromJson(obj: JSONObject): Phase {
        return Phase(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Phase"),
            durationSec = obj.optInt("durationSec", 1).coerceAtLeast(1)
        )
    }

    companion object {
        private const val PREFS_NAME = "saved_counters_store"
        private const val KEY_COUNTERS = "saved_counters"
        private const val KEY_QUICK_LAUNCH_CONFIG = "quick_launch_config"
        private const val KEY_LAUNCH_HISTORY = "launch_history"
    }
}
