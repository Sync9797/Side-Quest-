package com.monsterquest.app.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monsterquest.app.MainActivity
import com.monsterquest.app.R
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.core.Reminder
import com.monsterquest.core.Reminders

/**
 * Rings daily reminders: an alarm at each reminder's next time, then one
 * gentle notification with Done, Skip and Snooze buttons. Taps on the
 * notification are queued ([ReminderActions]) and applied by the game, so
 * the player never has to open the app.
 */
object ReminderAlarms {
    const val CHANNEL = "reminders"
    const val SNOOZE_MS = 15 * 60_000L
    private const val EXTRA_ID = "reminderId"
    const val ACTION_RING = "com.monsterquest.app.reminders.RING"
    const val ACTION_DONE = "com.monsterquest.app.reminders.DONE"
    const val ACTION_SKIP = "com.monsterquest.app.reminders.SKIP"
    const val ACTION_SNOOZE = "com.monsterquest.app.reminders.SNOOZE"
    private const val PREFS = "reminder_alarms"
    private const val KEY_SCHEDULED = "scheduled"

    private fun notificationId(id: String) = 5_000 + (id.hashCode() and 0xFFFF)

    private fun intent(context: Context, action: String, id: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ringIntent(context: Context, id: String) = intent(context, ACTION_RING, id, notificationId(id))

    private fun setAlarm(context: Context, atMs: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Exact when allowed; otherwise Android may ring it a few minutes late.
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    /** Sets (or clears) the next alarm for every reminder, and cancels ones that were deleted. */
    fun scheduleAll(context: Context, reminders: List<Reminder>, nowMs: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val now = mutableSetOf<String>()
        for (r in reminders) {
            val next = Reminders.nextRing(r, nowMs)
            if (next == null) {
                am.cancel(ringIntent(context, r.id))
            } else {
                setAlarm(context, next, ringIntent(context, r.id))
                now += r.id
            }
        }
        (before - now).forEach { am.cancel(ringIntent(context, it)) }
        prefs.edit().putStringSet(KEY_SCHEDULED, now).apply()
    }

    private fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your reminders, once at their time. Mark them done or skip them right from the notification."
            },
        )
    }

    /** The reminder's notification: one gentle ring with Done, Skip and Snooze. */
    fun show(context: Context, r: Reminder, streak: Int) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val code = notificationId(r.id)
        val text = buildString {
            append(r.timeLabel)
            if (streak > 0) append(" • Streak $streak day${if (streak == 1) "" else "s"}")
            append(" • Mark it done within 3 hours for a reward")
        }
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(r.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setTimeoutAfter(Reminders.ON_TIME_MS)
            .addAction(Notification.Action.Builder(null, "Done", intent(context, ACTION_DONE, r.id, code + 1)).build())
            .addAction(Notification.Action.Builder(null, "Skip", intent(context, ACTION_SKIP, r.id, code + 2)).build())
            .addAction(Notification.Action.Builder(null, "Snooze 15 min", intent(context, ACTION_SNOOZE, r.id, code + 3)).build())
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(code, n) }
    }

    /** Replaces the reminder's notification with a short confirmation that clears itself. */
    private fun confirm(context: Context, id: String, title: String, text: String) {
        createChannel(context)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(6_000)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(notificationId(id), n) }
    }

    fun handle(context: Context, intent: Intent) {
        val repo = SaveRepository(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val state = repo.load()
                scheduleAll(context, state.reminders)
                GameNudgeAlarms.schedule(context, state)
            }
            ACTION_RING -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                // Apply any queued taps first so the streak shown is up to date.
                val state = ReminderActions.applyTo(context, repo.load())
                val r = state.reminders.find { it.id == id }
                if (r != null && Reminders.isOpen(r, System.currentTimeMillis())) show(context, r, Reminders.streak(r, System.currentTimeMillis()))
                scheduleAll(context, state.reminders)
            }
            ACTION_DONE, ACTION_SKIP -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val done = intent.action == ACTION_DONE
                ReminderActions.enqueue(context, id, done, System.currentTimeMillis())
                val title = repo.load().reminders.find { it.id == id }?.title ?: "Reminder"
                confirm(context, id, title, if (done) "Done! Your reward is added in the game." else "Skipped for today. Your streak is safe.")
            }
            ACTION_SNOOZE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId(id))
                setAlarm(context, System.currentTimeMillis() + SNOOZE_MS, ringIntent(context, id))
            }
        }
    }
}

/** Receives reminder alarms and notification buttons (and re-arms alarms after a reboot). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                ReminderAlarms.handle(context, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * Done and Skip taps from notifications, waiting to be applied to the save.
 * The game applies them (with the time of the tap) when it's open, or the
 * next time it starts, so a running game never overwrites them.
 */
object ReminderActions {
    private const val PREFS = "reminder_actions"
    private const val KEY = "queue"

    @Synchronized
    fun enqueue(context: Context, id: String, done: Boolean, atMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val q = prefs.getString(KEY, "").orEmpty()
        val line = "$id|${if (done) "D" else "S"}|$atMs"
        prefs.edit().putString(KEY, if (q.isEmpty()) line else "$q\n$line").commit()
    }

    /** Takes every queued tap (oldest first) and clears the queue. */
    @Synchronized
    fun drain(context: Context): List<Triple<String, Boolean, Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val q = prefs.getString(KEY, "").orEmpty()
        if (q.isEmpty()) return emptyList()
        prefs.edit().remove(KEY).commit()
        return q.lines().mapNotNull { l ->
            val p = l.split("|")
            if (p.size == 3) Triple(p[0], p[1] == "D", p[2].toLongOrNull() ?: return@mapNotNull null) else null
        }
    }

    /** A read-only preview of the queue applied to [state] (the queue is left alone). */
    fun applyTo(context: Context, state: com.monsterquest.core.GameState): com.monsterquest.core.GameState {
        val q = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        var s = state
        q.lines().forEach { l ->
            val p = l.split("|")
            if (p.size == 3) {
                val at = p[2].toLongOrNull() ?: return@forEach
                s = if (p[1] == "D") Reminders.complete(s, p[0], at).state else Reminders.skip(s, p[0], at).state
            }
        }
        return s
    }

    fun listen(context: Context, onChange: () -> Unit): android.content.SharedPreferences.OnSharedPreferenceChangeListener {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) onChange() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(l)
        return l
    }
}
