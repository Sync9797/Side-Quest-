package com.monsterquest.app.widget

import com.monsterquest.app.ui.formatDistance
import com.monsterquest.core.GameState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A small summary of the game for home screen widgets made with AI Widget
 * Maker: the hero, today's steps and torches, eggs and how far to hatch,
 * monsters out on adventures, and last night's sleep. Nothing else leaves
 * the game.
 */
object WidgetSummary {
    fun of(state: GameState, today: LocalDate = LocalDate.now()): JsonObject = buildJsonObject {
        put("v", 1)
        put("hero", state.hero.name)
        put("level", state.hero.level)
        val day = today.toEpochDay()
        val steps = state.stepHistory[day]?.toLong() ?: state.healthSteps?.takeIf { state.healthDay == day } ?: 0L
        put("steps", steps)
        put("torches", state.torches)
        put("gold", state.gold)
        val imperial = state.settings.imperialUnits
        put("eggs", buildJsonArray {
            state.eggs.forEach { egg ->
                add(buildJsonObject {
                    put("name", egg.species.name)
                    put("progress", (egg.progress * 100).roundToInt())
                    put("left", formatDistance((egg.meters - egg.walkedMeters).coerceAtLeast(0.0), imperial))
                    put("ready", egg.ready)
                })
            }
        })
        val names = state.monsters.associate { it.uid to it.displayName }
        put("adventures", buildJsonArray {
            state.adventures.forEach { a ->
                add(buildJsonObject {
                    put("title", a.adventure.title)
                    put("place", a.adventure.placeName)
                    put("team", a.monsterUids.mapNotNull { names[it] }.joinToString(", "))
                    put("startMs", a.startMs)
                    put("endMs", a.endMs)
                })
            }
        })
        state.sleep.maxByOrNull { it.key }?.value?.let { night ->
            putJsonObject("sleep") {
                put("minutes", night.asleepMin)
                put("deep", night.deepMin)
                put("rem", night.remMin)
            }
        }
    }
}
