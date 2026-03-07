package com.omega.intervaltimer.data

import android.content.Context
import com.omega.intervaltimer.model.Phase
import com.omega.intervaltimer.model.WorkoutConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedCounter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: WorkoutConfig
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

    private fun configToJson(config: WorkoutConfig): JSONObject {
        return JSONObject().apply {
            put("phases", JSONArray().apply {
                config.phases.forEach { put(phaseToJson(it)) }
            })
            put("sets", config.sets)
            put("cycles", config.cycles)
            put("restBetweenCyclesSec", config.restBetweenCyclesSec)
        }
    }

    private fun configFromJson(obj: JSONObject): WorkoutConfig {
        val phasesArray = obj.optJSONArray("phases") ?: JSONArray()

        val phases = buildList {
            for (i in 0 until phasesArray.length()) {
                val phaseObj = phasesArray.optJSONObject(i) ?: continue
                add(phaseFromJson(phaseObj))
            }
        }.ifEmpty {
            listOf(
                Phase(name = "Phase 1", durationSec = 30),
                Phase(name = "Phase 2", durationSec = 60),
                Phase(name = "Phase 3", durationSec = 90)
            )
        }

        return WorkoutConfig(
            phases = phases,
            sets = obj.optInt("sets", 1).coerceAtLeast(1),
            cycles = obj.optInt("cycles", 1).coerceAtLeast(1),
            restBetweenCyclesSec = obj.optInt("restBetweenCyclesSec", 0).coerceAtLeast(0)
        )
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
    }
}
