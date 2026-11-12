package com.monsterquest.app.widget

import android.content.Context
import com.monsterquest.core.Adventures
import com.monsterquest.core.Eggs
import com.monsterquest.core.GameEngine
import com.monsterquest.core.GameState
import kotlin.random.Random

/**
 * Taps from AI Widget Maker's Monster Quest widgets (complete a quest, claim
 * an adventure, hatch an egg), waiting to be applied to the save. Like the
 * reminder buttons, the game applies them when it's open (right away) or the
 * next time it starts, so a running game never overwrites them; widgets
 * show them as done straight away.
 */
object WidgetActions {
    private const val PREFS = "widget_actions"
    private const val KEY = "queue"

    enum class Kind(val word: String) { COMPLETE("complete"), CLAIM("claim"), HATCH("hatch") }

    data class Action(val kind: Kind, val id: String, val atMs: Long)

    @Synchronized
    fun enqueue(context: Context, kind: Kind, id: String, atMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val q = prefs.getString(KEY, "").orEmpty()
        val line = "${kind.word}|$id|$atMs"
        prefs.edit().putString(KEY, if (q.isEmpty()) line else "$q\n$line").commit()
    }

    /** Takes every queued action (oldest first) and clears the queue. */
    @Synchronized
    fun drain(context: Context): List<Action> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val q = prefs.getString(KEY, "").orEmpty()
        if (q.isEmpty()) return emptyList()
        prefs.edit().remove(KEY).commit()
        return parse(q)
    }

    private fun parse(q: String): List<Action> = q.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size != 3) return@mapNotNull null
        val kind = Kind.entries.find { it.word == p[0] } ?: return@mapNotNull null
        Action(kind, p[1], p[2].toLongOrNull() ?: return@mapNotNull null)
    }

    /** One action on [state]: the game's own rules, as if it was tapped in the game. */
    fun apply(state: GameState, a: Action, rng: Random = Random.Default) = when (a.kind) {
        Kind.COMPLETE -> GameEngine.completeTask(state, a.id, a.atMs)
        Kind.CLAIM -> Adventures.claim(state, a.id, a.atMs, rng)
        Kind.HATCH -> Eggs.hatch(state, a.id, rng)
    }

    /** A read-only preview of the queue applied to [state], for widgets (the queue is left alone). */
    fun applyTo(context: Context, state: GameState): GameState {
        val q = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return parse(q).fold(state) { s, a -> apply(s, a, Random(a.atMs)).state }
    }

    /** Whether [kind] on [id] can happen now (so a widget button never queues something the game would refuse). */
    fun allowed(state: GameState, kind: Kind, id: String, nowMs: Long): Boolean = when (kind) {
        Kind.COMPLETE -> state.tasks.any { it.id == id && !it.completed }
        Kind.CLAIM -> state.adventures.any { it.adventure.id == id && it.done(nowMs) }
        Kind.HATCH -> state.eggs.any { it.id == id && it.ready }
    }

    fun listen(context: Context, onChange: () -> Unit): android.content.SharedPreferences.OnSharedPreferenceChangeListener {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) onChange() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(l)
        return l
    }
}
