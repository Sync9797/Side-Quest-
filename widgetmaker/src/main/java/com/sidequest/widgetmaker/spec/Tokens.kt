package com.sidequest.widgetmaker.spec

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Live values a design's text can show. The widget fills them in each time it
 * redraws (when you open the app, and at least every 30 minutes); for a clock
 * that ticks every minute, use a [ClockNode] instead.
 */
data class Tokens(
    val now: LocalDateTime,
    /** 0-100, or null if unknown. */
    val battery: Int?,
    val charging: Boolean,
    val locale: Locale = Locale.getDefault(),
) {
    fun values(): Map<String, String> = mapOf(
        "date" to now.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
        "day" to now.format(DateTimeFormatter.ofPattern("EEEE", locale)),
        "month" to now.format(DateTimeFormatter.ofPattern("MMMM", locale)),
        "dayOfMonth" to now.dayOfMonth.toString(),
        "year" to now.year.toString(),
        "greeting" to greeting(now.hour),
        "battery" to (battery?.toString() ?: "--"),
        "charging" to if (charging) "Charging" else "",
    )

    /** Replaces {token} with its value; unknown tokens are left as they are. */
    fun fill(text: String): String {
        val values = values()
        return TOKEN.replace(text) { m -> values[m.groupValues[1]] ?: m.value }
    }

    /** A progress value: a number, or a token such as {battery}. */
    fun progress(value: String): Float {
        val number = fill(value).trim().removeSuffix("%").toFloatOrNull() ?: 0f
        return (number / 100f).coerceIn(0f, 1f)
    }

    companion object {
        private val TOKEN = Regex("\\{(\\w+)\\}")

        val NAMES = listOf("date", "day", "month", "dayOfMonth", "year", "greeting", "battery", "charging")

        fun greeting(hour: Int) = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Good night"
        }
    }
}
