package com.monsterquest.app.widget

import com.monsterquest.app.ui.formatDistance
import com.monsterquest.core.Bounty
import com.monsterquest.core.GameState
import com.monsterquest.core.QuestTask
import com.monsterquest.core.Stat
import com.monsterquest.core.TaskSource
import com.monsterquest.core.Torches
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    fun of(
        state: GameState,
        today: LocalDate = LocalDate.now(),
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): JsonObject = buildJsonObject {
        put("v", 2)
        put("hero", state.hero.name)
        put("level", state.hero.level)
        val day = today.toEpochDay()
        val steps = state.stepHistory[day]?.toLong() ?: state.healthSteps?.takeIf { state.healthDay == day } ?: 0L
        put("steps", steps)
        put("torches", state.torches)
        put("torchCap", Torches.cap(state))
        // Steps walked toward the next torch.
        put("torchSteps", state.torchSteps)
        put("stepsPerTorch", Torches.STEPS_PER_TORCH)
        put("gold", state.gold)
        val imperial = state.settings.imperialUnits
        put("eggs", buildJsonArray {
            state.eggs.forEach { egg ->
                add(buildJsonObject {
                    put("id", egg.id)
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
                    put("id", a.adventure.id)
                    put("title", a.adventure.title)
                    put("place", a.adventure.placeName)
                    put("team", a.monsterUids.mapNotNull { names[it] }.joinToString(", "))
                    put("startMs", a.startMs)
                    put("endMs", a.endMs)
                })
            }
        })
        // The next quests: calendar ones by start time, then to-dos by due date and priority.
        put("quests", buildJsonArray {
            nextQuests(state, nowMs).take(5).forEach { t ->
                add(buildJsonObject {
                    put("id", t.id)
                    put("title", t.title)
                    put("when", whenLabel(t.startMs ?: t.dueMs, nowMs, zone))
                    put("calendar", t.source == TaskSource.CALENDAR)
                    put("difficulty", t.difficulty.name.lowercase())
                })
            }
        })
        put("questsOpen", state.tasks.count { !it.completed })
        // The party, in battle order, with health.
        put("party", buildJsonArray {
            state.party.mapNotNull { uid -> state.monsters.find { it.uid == uid } }.forEach { m ->
                add(buildJsonObject {
                    put("name", m.displayName)
                    put("level", m.level)
                    put("hp", m.currentHp)
                    put("maxHp", m.stat(Stat.HP))
                })
            }
        })
        // This week's bounty.
        put("bounty", buildJsonArray {
            Bounty.lines(state).forEach { l ->
                add(buildJsonObject {
                    put("text", l.text)
                    put("progress", l.progress)
                    put("goal", l.goal)
                })
            }
        })
        put("bountyReady", Bounty.ready(state, today.toEpochDay()))
        // A phone-free session in progress.
        state.focus?.let { f ->
            putJsonObject("focus") {
                put("startMs", f.startMs)
                put("endMs", f.endMs)
                put("minutes", f.minutes)
            }
        }
        state.sleep.maxByOrNull { it.key }?.value?.let { night ->
            putJsonObject("sleep") {
                put("minutes", night.asleepMin)
                put("deep", night.deepMin)
                put("rem", night.remMin)
            }
        }
    }

    private fun nextQuests(state: GameState, nowMs: Long): List<QuestTask> {
        val open = state.tasks.filter { !it.completed }
        val calendar = open.filter { it.source == TaskSource.CALENDAR && (it.endMs ?: it.startMs ?: 0) >= nowMs }.sortedBy { it.startMs }
        val manual = open.filter { it.source != TaskSource.CALENDAR }
        val todos = manual.filter { it.dueMs != null }.sortedBy { it.dueMs } + manual.filter { it.dueMs == null }.sortedByDescending { it.priority.ordinal }
        // Whatever comes first in time leads; undated to-dos follow.
        return (calendar + todos).sortedBy { it.startMs ?: it.dueMs ?: Long.MAX_VALUE }
    }

    /** "Today 3 PM", "Tomorrow", "Sat 9 AM", "Nov 20", or "" when it has no time. */
    fun whenLabel(ms: Long?, nowMs: Long, zone: ZoneId): String {
        if (ms == null) return ""
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val d = t.toLocalDate()
        val clock = t.toLocalTime().let { if (it.hour == 0 && it.minute == 0) "" else DateTimeFormatter.ofPattern(if (it.minute == 0) " h a" else " h:mm a", Locale.US).format(it) }
        val day = when {
            d.isBefore(today) -> "Overdue"
            d == today -> "Today"
            d == today.plusDays(1) -> "Tomorrow"
            d.isBefore(today.plusDays(7)) -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
            else -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        }
        return if (day == "Overdue") day else day + clock
    }
}
