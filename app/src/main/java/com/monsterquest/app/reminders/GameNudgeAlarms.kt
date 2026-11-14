package com.monsterquest.app.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monsterquest.app.MainActivity
import com.monsterquest.app.R
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.core.GameNudges
import com.monsterquest.core.GameState

/**
 * Alarms for [GameNudges]: set when you leave the game, checked again when
 * they ring (only posted if the thing is still waiting), at most
 * [GameNudges.PER_DAY] a day and never in the quiet hours.
 */
object GameNudgeAlarms {
    const val CHANNEL = "game_nudges"
    private const val ACTION = "com.monsterquest.app.nudges.RING"
    private const val PREFS = "game_nudges"
    private const val KEY_SCHEDULED = "scheduled"
    private const val KEY_DAY = "day"
    private const val KEY_SENT = "sent"

    private fun code(id: String) = 9_000 + (id.hashCode() and 0x7FFF)

    private fun intent(context: Context, id: String, title: String = "", text: String = ""): PendingIntent = PendingIntent.getBroadcast(
        context,
        code(id),
        Intent(context, NudgeReceiver::class.java).setAction(ACTION).putExtra("id", id).putExtra("title", title).putExtra("text", text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets an alarm for every upcoming nudge (and cancels ones that no longer apply). */
    fun schedule(context: Context, state: GameState, nowMs: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val nudges = GameNudges.upcoming(state, nowMs)
        // A few minutes late is fine for these, so no exact alarms.
        nudges.forEach { n -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.atMs, intent(context, n.id, n.title, n.text)) }
        val now = nudges.map { it.id }.toSet()
        (before - now).forEach { am.cancel(intent(context, it)) }
        prefs.edit().putStringSet(KEY_SCHEDULED, now).apply()
    }

    fun handle(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val nowMs = System.currentTimeMillis()
        val state = SaveRepository(context).load()
        if (!GameNudges.stillDue(state, id, nowMs) || GameNudges.inQuietHours(nowMs)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toEpochDay()
        val sent = if (prefs.getLong(KEY_DAY, -1) == today) prefs.getInt(KEY_SENT, 0) else 0
        if (!GameNudges.withinDailyLimit(sent)) return
        show(context, id, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("text").orEmpty())
        prefs.edit().putLong(KEY_DAY, today).putInt(KEY_SENT, sent + 1).apply()
    }

    private fun show(context: Context, id: String, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Game updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When something is waiting: an adventure team back, a rare visitor about to leave, an egg ready. At most 3 a day, never at night."
            },
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(code(id), n) }
    }
}

/** Receives game-nudge alarms. */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                GameNudgeAlarms.handle(context, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
