package com.monsterquest.app

import com.monsterquest.app.widget.WidgetSummary
import com.monsterquest.core.Egg
import com.monsterquest.core.GameState
import com.monsterquest.core.Hero
import com.monsterquest.core.Personality
import com.monsterquest.core.SleepNight
import com.monsterquest.core.StatBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The summary AI Widget Maker's Monster Quest widgets read. */
class WidgetSummaryTest {
    @Test
    fun summary() {
        val today = LocalDate.of(2026, 11, 11)
        val egg = Egg("e1", "shadow_pup", StatBlock(), Personality.BALANCED, shiny = false, meters = 2000.0, walkedMeters = 500.0)
        val state = GameState(
            hero = Hero(name = "Aaron", level = 23),
            stepHistory = mapOf(today.toEpochDay() to 3508),
            eggs = listOf(egg, egg.copy(id = "e2", walkedMeters = 2000.0)),
            sleep = mapOf(today.toEpochDay() to SleepNight(0, 8 * 3_600_000L, deepMin = 80, awakeMin = 30)),
        ).copy(settings = GameState().settings.copy(imperialUnits = false))
        val json = WidgetSummary.of(state, today)
        assertEquals("Aaron", json["hero"]!!.jsonPrimitive.content)
        assertEquals("3508", json["steps"]!!.jsonPrimitive.content)
        val eggs = json["eggs"] as JsonArray
        assertEquals("25", eggs[0].jsonObject["progress"]!!.jsonPrimitive.content)
        assertEquals("1.5 km", eggs[0].jsonObject["left"]!!.jsonPrimitive.content)
        assertEquals("true", eggs[1].jsonObject["ready"]!!.jsonPrimitive.content)
        assertEquals("450", json["sleep"]!!.jsonObject["minutes"]!!.jsonPrimitive.content)
    }
}
