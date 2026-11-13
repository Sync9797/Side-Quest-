package com.monsterquest.app

import com.monsterquest.app.widget.WidgetActions
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

    @Test
    fun questsTorchesAndActions() {
        val today = LocalDate.of(2026, 11, 11)
        val zone = java.time.ZoneOffset.UTC
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
        val egg = Egg("e1", "shadow_pup", StatBlock(), Personality.BALANCED, shiny = false, meters = 2000.0, walkedMeters = 2000.0)
        val state = GameState(
            tasks = listOf(
                com.monsterquest.core.QuestTask("q1", "Write report", dueMs = today.atTime(15, 0).toInstant(zone).toEpochMilli()),
                com.monsterquest.core.QuestTask("q2", "Water plants"),
                com.monsterquest.core.QuestTask("q3", "Old one", completedAtMs = 1L),
            ),
            torchSteps = 480,
            eggs = listOf(egg),
        )
        val json = WidgetSummary.of(state, today, now, zone)
        val quests = json["quests"] as JsonArray
        assertEquals(2, quests.size)
        // As the Quests screen lists them: the task without a date, then the scheduled one.
        assertEquals("q2", quests[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("", quests[0].jsonObject["when"]!!.jsonPrimitive.content)
        assertEquals("q1", quests[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Today 3 PM", quests[1].jsonObject["when"]!!.jsonPrimitive.content)
        assertEquals("480", json["torchSteps"]!!.jsonPrimitive.content)
        assertEquals("e1", (json["eggs"] as JsonArray)[0].jsonObject["id"]!!.jsonPrimitive.content)

        // Widget buttons follow the game's rules.
        org.junit.Assert.assertTrue(WidgetActions.allowed(state, WidgetActions.Kind.COMPLETE, "q1", now))
        org.junit.Assert.assertFalse(WidgetActions.allowed(state, WidgetActions.Kind.COMPLETE, "q3", now))
        org.junit.Assert.assertTrue(WidgetActions.allowed(state, WidgetActions.Kind.HATCH, "e1", now))
        org.junit.Assert.assertFalse(WidgetActions.allowed(state, WidgetActions.Kind.CLAIM, "nope", now))
        val done = WidgetActions.apply(state, WidgetActions.Action(WidgetActions.Kind.COMPLETE, "q1", now)).state
        org.junit.Assert.assertTrue(done.tasks.first { it.id == "q1" }.completed)
    }

    @Test
    fun questsFollowTheQuestsScreen() {
        val zone = java.time.ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 11)
        fun at(h: Int, d: LocalDate = today) = d.atTime(h, 0).toInstant(zone).toEpochMilli()
        val now = at(12)
        val cal = com.monsterquest.core.TaskSource.CALENDAR
        val tasks = listOf(
            com.monsterquest.core.QuestTask("later", "Dentist", dueMs = at(9, today.plusDays(3))),
            com.monsterquest.core.QuestTask("tonight", "Call mom", dueMs = at(19)),
            com.monsterquest.core.QuestTask("nodate", "Read a chapter"),
            // Ended unchecked: the screen shows it as overdue, so the widget does too.
            com.monsterquest.core.QuestTask("missed", "Standup", startMs = at(9), endMs = at(10), source = cal),
            com.monsterquest.core.QuestTask("now", "Lunch", startMs = at(11), endMs = at(13), source = cal),
            com.monsterquest.core.QuestTask("done", "Old", completedAtMs = 1L),
        )
        val state = GameState(tasks = tasks)
        val expected = com.monsterquest.core.Planner.sections(tasks, now, zone).let { s ->
            s.filter { it.group == com.monsterquest.core.Planner.Group.NO_DATE }.flatMap { it.tasks } +
                s.filter { it.group != com.monsterquest.core.Planner.Group.NO_DATE }.flatMap { it.tasks }
        }.map { it.id }
        assertEquals(listOf("nodate", "missed", "now", "tonight", "later"), expected)
        val quests = WidgetSummary.of(state, today, now, zone)["quests"] as JsonArray
        assertEquals(expected, quests.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals("Overdue", quests[1].jsonObject["when"]!!.jsonPrimitive.content)
    }
}
