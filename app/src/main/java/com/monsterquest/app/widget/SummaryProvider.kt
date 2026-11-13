package com.monsterquest.app.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.monsterquest.app.data.SaveRepository

/**
 * Shares [WidgetSummary] with AI Widget Maker (and nothing else), so its
 * Monster Quest widgets can show your quests, eggs, adventures, steps and
 * sleep, and lets its buttons complete quests, claim adventures and hatch eggs.
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        // Only the widget app may read it.
        if (callingPackage !in READERS) return null
        val context = context ?: return null
        // As the widgets should show it: including taps waiting to be applied.
        val json = WidgetSummary.of(current(context)).toString()
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    /**
     * Actions from widget buttons: "complete" a quest, "claim" a finished
     * adventure or "hatch" a ready egg ([arg] is its id). Checked against the
     * game's rules, queued for the game to apply, and confirmed with a
     * message. Only AI Widget Maker may ask.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (callingPackage !in READERS) return null
        val context = context ?: return null
        val kind = WidgetActions.Kind.entries.find { it.word == method } ?: return result(false, "Unknown action.")
        val id = arg ?: return result(false, "Nothing to act on.")
        val now = System.currentTimeMillis()
        val state = current(context)
        if (!WidgetActions.allowed(state, kind, id, now)) {
            return result(false, when (kind) {
                WidgetActions.Kind.COMPLETE -> "That quest is already done."
                WidgetActions.Kind.CLAIM -> "That adventure isn't back yet."
                WidgetActions.Kind.HATCH -> "That egg isn't ready yet."
            })
        }
        WidgetActions.enqueue(context, kind, id, now)
        ping(context, force = true)
        val name = when (kind) {
            WidgetActions.Kind.COMPLETE -> state.tasks.find { it.id == id }?.title?.let { "Quest done: $it" }
            WidgetActions.Kind.CLAIM -> "Adventure claimed. Open the game to see your loot."
            WidgetActions.Kind.HATCH -> state.eggs.find { it.id == id }?.species?.name?.let { "Your $it egg is hatching! Open the game to meet it." }
        }
        return result(true, name ?: "Done.")
    }

    private fun result(ok: Boolean, message: String) = Bundle().apply {
        putBoolean("ok", ok)
        putString("message", message)
    }

    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val WIDGET_MAKER = "com.sidequest.widgetmaker"
        private val READERS = setOf(WIDGET_MAKER)
        private const val CHANGED = "com.sidequest.widgetmaker.GAME_CHANGED"
        private const val PING_GAP_MS = 15_000L
        @Volatile private var lastPing = 0L
        @Volatile private var trailing = false

        /** The game as widgets see it: the save, with reminder and widget taps still waiting to be applied. */
        fun current(context: Context) =
            WidgetActions.applyTo(context, com.monsterquest.app.reminders.ReminderActions.applyTo(context, SaveRepository(context).load()))

        /**
         * Tells the widget app the game changed, so its widgets update: at most every 15 seconds unless
         * [force]. A change inside those 15 seconds is sent when they're up rather than dropped, so a
         * quest added or ticked off right after another still reaches the widget.
         */
        fun ping(context: Context, force: Boolean = false) {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            val wait = PING_GAP_MS - (now - lastPing)
            if (!force && wait > 0) {
                if (!trailing) {
                    trailing = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        trailing = false
                        ping(app, force = true)
                    }, wait)
                }
                return
            }
            lastPing = now
            runCatching { app.sendBroadcast(Intent(CHANGED).setPackage(WIDGET_MAKER)) }
        }
    }
}
