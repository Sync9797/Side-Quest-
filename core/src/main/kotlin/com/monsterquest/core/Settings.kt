package com.monsterquest.core

import kotlinx.serialization.Serializable

/** Where walking is counted from. */
@Serializable
enum class StepSource(val label: String, val description: String) {
    PHONE("Phone step counter", "The phone's built-in step sensor. Counts all day, even when the game is closed."),
    SAMSUNG_HEALTH(
        "Samsung Health",
        "Steps from Samsung Health (including a Galaxy Watch). Samsung Health shares them through Health Connect: " +
            "turn it on in Samsung Health, then Settings, then Health Connect.",
    ),
    HEALTH_CONNECT("Health Connect", "Steps from any app that writes to Health Connect: Google Fit, Fitbit, Garmin, Samsung Health and more."),
    GPS("GPS distance", "Distance walked while the game is open. Use this if nothing else is available."),
    ;

    val usesHealthConnect: Boolean get() = this == SAMSUNG_HEALTH || this == HEALTH_CONNECT
}

/** Screen refresh rate the game asks for. */
@Serializable
enum class FrameRate(val label: String, val hz: Float?, val description: String) {
    HIGH("120 fps", 120f, "Smoothest animation on screens that support it (up to 120 Hz)."),
    STANDARD("60 fps", 60f, "Uses less battery."),
    AUTO("Auto", null, "Let the phone decide."),
}

/** Light or dark screens. */
@Serializable
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** The map's look: follow the app's light/dark theme, or always light or dark. */
enum class MapStyle(val label: String) { AUTO("Match app"), LIGHT("Light"), DARK("Dark") }

/** How fast battle animations play. */
enum class BattleSpeed(val label: String, val factor: Float) { NORMAL("Normal", 1f), FAST("Fast", 0.5f), INSTANT("Instant", 0f) }

/** The game's own gold-and-teal colors, or the phone's Material You colors. */
@Serializable
enum class ColorStyle(val label: String, val description: String) {
    GAME("Side Quest", "The game's own gold and teal colors."),
    SYSTEM("Match system colors", "Material You: use the colors from your wallpaper and system theme (Android 12+)."),
}

/** Player preferences, saved with the game. */
@Serializable
data class Settings(
    val stepSource: StepSource = StepSource.PHONE,
    val frameRate: FrameRate = FrameRate.HIGH,
    /** Show distances in feet and miles (true) or meters and kilometers. */
    val imperialUnits: Boolean = true,
    /** Turn off glows, auras, mist and engraving for smoother scrolling on older phones. */
    val reducedEffects: Boolean = false,
    /** Look for a newer version of the game each time it starts. */
    val checkUpdates: Boolean = true,
    /** A version the player chose to skip (not offered again; a later one still is). */
    val skippedVersion: String? = null,
    /** Notifications when something in the game is waiting (an adventure back, a rare visitor leaving, an egg ready). */
    val gameNudges: Boolean = true,
    /** The version whose "What's new" the player has seen (null: none yet). */
    val lastSeenVersion: String? = null,
    /** The map turns with the phone, so what's in front of you is up the screen (else north is up). */
    val followHeading: Boolean = false,
    /** Ask for location less often to save battery (the map updates more slowly). */
    val batterySaverGps: Boolean = false,
    /** On the map, turning the phone upside down blacks out the screen to save battery. */
    val flipBatterySaver: Boolean = true,
    /** Aggressive monsters that reach you can start a fight before you're ready. */
    val ambushes: Boolean = true,
    /** Vibrate when a new monster appears nearby. */
    val vibrateOnSpawn: Boolean = true,
    val calendarAutoSync: Boolean = true,
    /** Sync every calendar on the phone (except holidays and birthdays)... */
    val syncAllCalendars: Boolean = true,
    /** ...or only these calendars, by id. */
    val syncCalendars: Set<Long> = emptySet(),
    val calendarDaysAhead: Int = 7,
    val includeAllDayEvents: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorStyle: ColorStyle = ColorStyle.GAME,
    val mapStyle: MapStyle = MapStyle.AUTO,
    /** Binding casts with one tap (a Good trace) instead of tracing the glyph. */
    val simpleCasting: Boolean = false,
    /** The Health card on Home is open (true) or folded to one line. */
    val healthOpen: Boolean = false,
    /** Look up the real weather where you are; it changes which monsters are out. */
    val liveWeather: Boolean = true,
    val battleSpeed: BattleSpeed = BattleSpeed.NORMAL,
    /** Buzz on big moments: taming, critical catches, level-ups, new moves. */
    val bigMomentHaptics: Boolean = true,
)

val CALENDAR_DAYS_AHEAD_OPTIONS = listOf(1, 3, 7, 14)
