package com.monsterquest.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import com.monsterquest.core.ColorStyle
import com.monsterquest.core.ThemeMode

/**
 * The game's accent colors. Dark mode keeps the Questbound palette (deep
 * teal panels, warm gold, soft mint); light mode uses deeper versions that
 * read well on parchment; Material You swaps gold and mint for the system's
 * own accent colors.
 */
data class GamePalette(
    val gold: Color,
    val mint: Color,
    val hpGreen: Color,
    val hpYellow: Color,
    val hpRed: Color,
    val mpBlue: Color,
    val xpPurple: Color,
    val bossRed: Color,
    val silver: Color,
    val line: Color,
    /** Text drawn on top of a gold or mint fill. */
    val onAccent: Color,
    val dark: Boolean,
)

private val DarkPalette = GamePalette(
    gold = Color(0xFFEBC678), mint = Color(0xFF9CD2BD), hpGreen = Color(0xFF8FD4AE), hpYellow = Color(0xFFE9CB83),
    hpRed = Color(0xFFF2A198), mpBlue = Color(0xFF9CBFE4), xpPurple = Color(0xFFC3A6F0), bossRed = Color(0xFFE88A7E),
    silver = Color(0xFFC0C7D1), line = Color(0xFF2A4042), onAccent = Color(0xFF0D171A), dark = true,
)

private val LightPalette = GamePalette(
    gold = Color(0xFF9A6A12), mint = Color(0xFF2E7D63), hpGreen = Color(0xFF2E8B57), hpYellow = Color(0xFFA86B00),
    hpRed = Color(0xFFC0392B), mpBlue = Color(0xFF2F6DB0), xpPurple = Color(0xFF7046B8), bossRed = Color(0xFFB53A2E),
    silver = Color(0xFF6B7480), line = Color(0xFFC9D2CE), onAccent = Color(0xFFFFFFFF), dark = false,
)

/** The palette in use; set by [MonsterQuestTheme] (the color names below read it). */
internal object GameColors {
    @Volatile
    var palette: GamePalette = DarkPalette
}

val Gold: Color get() = GameColors.palette.gold
val Mint: Color get() = GameColors.palette.mint
val HpGreen: Color get() = GameColors.palette.hpGreen
val HpYellow: Color get() = GameColors.palette.hpYellow
val HpRed: Color get() = GameColors.palette.hpRed
val MpBlue: Color get() = GameColors.palette.mpBlue
val XpPurple: Color get() = GameColors.palette.xpPurple
val BossRed: Color get() = GameColors.palette.bossRed
val SilverColor: Color get() = GameColors.palette.silver
val Line: Color get() = GameColors.palette.line
/** Text on a gold or mint fill. */
val OnAccent: Color get() = GameColors.palette.onAccent

/** The game's gold in both themes, for things drawn the same everywhere (e.g. the level-up ribbon). */
val FixedGold = Color(0xFFEBC678)

/** Button/accent color per attack type. */
fun typeColor(e: com.monsterquest.core.Element): Color = when (e) {
    com.monsterquest.core.Element.NEUTRAL -> Color(0xFF4E5E5B)
    com.monsterquest.core.Element.FIRE -> Color(0xFF9A4A33)
    com.monsterquest.core.Element.ELECTRIC -> Color(0xFF8C7426)
    com.monsterquest.core.Element.FLIGHT -> Color(0xFF3F6684)
    com.monsterquest.core.Element.WATER -> Color(0xFF2E5C8A)
    com.monsterquest.core.Element.GAS -> Color(0xFF5B7334)
    com.monsterquest.core.Element.CORRUPT -> Color(0xFF5B4580)
    com.monsterquest.core.Element.EARTH -> Color(0xFF5E7A36)
    com.monsterquest.core.Element.MYTHICAL -> Color(0xFF6A4E9A)
}

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.gold,
    onPrimary = Color(0xFF263027),
    secondary = DarkPalette.mint,
    onSecondary = Color(0xFF14292A),
    tertiary = DarkPalette.mint,
    background = Color(0xFF0D171A),
    onBackground = Color(0xFFEEEADE),
    surface = Color(0xFF142327),
    onSurface = Color(0xFFEEEADE),
    surfaceVariant = Color(0xFF1B2E32),
    onSurfaceVariant = Color(0xFFA0B2AE),
    outline = DarkPalette.line,
    secondaryContainer = Color(0xFF25413D),
    onSecondaryContainer = Color(0xFFBCE5D1),
    error = DarkPalette.hpRed,
)

/** Parchment and ink, with the same gold and teal accents. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB07D1E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = LightPalette.mint,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = LightPalette.mint,
    background = Color(0xFFF4F0E6),
    onBackground = Color(0xFF1C2627),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF1C2627),
    surfaceVariant = Color(0xFFE9E3D4),
    onSurfaceVariant = Color(0xFF586462),
    outline = LightPalette.line,
    secondaryContainer = Color(0xFFD3ECE1),
    onSecondaryContainer = Color(0xFF123B2F),
    error = LightPalette.hpRed,
)

/** Material You: gold and mint follow the system's accent colors. */
private fun dynamicPalette(scheme: ColorScheme, dark: Boolean): GamePalette =
    (if (dark) DarkPalette else LightPalette).copy(
        gold = scheme.primary,
        mint = scheme.tertiary,
        line = scheme.outlineVariant,
        onAccent = scheme.onPrimary,
    )

/** Serif headings like Questbound; the rest stays in the system sans-serif. */
private val Type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
    ).let { ty ->
        // Line height in proportion to the text size: the default 24sp line made
        // small (11-13sp) text look double-spaced on every screen.
        fun TextStyle.tight() = copy(lineHeight = (lineHeight.value / fontSize.value).coerceAtMost(1.4f).em)
        ty.copy(
            displayLarge = ty.displayLarge.tight(), displayMedium = ty.displayMedium.tight(), displaySmall = ty.displaySmall.tight(),
            headlineLarge = ty.headlineLarge.tight(), headlineMedium = ty.headlineMedium.tight(), headlineSmall = ty.headlineSmall.tight(),
            titleLarge = ty.titleLarge.tight(), titleMedium = ty.titleMedium.tight(), titleSmall = ty.titleSmall.tight(),
            bodyLarge = ty.bodyLarge.tight(), bodyMedium = ty.bodyMedium.tight(), bodySmall = ty.bodySmall.tight(),
            labelLarge = ty.labelLarge.tight(), labelMedium = ty.labelMedium.tight(), labelSmall = ty.labelSmall.tight(),
        )
    }
}

/** Changes whenever the palette does, so everything below redraws in the new colors. */
private val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** True when Material You colors are available (Android 12+). */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Whether the game is showing dark screens right now. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MonsterQuestTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    colorStyle: ColorStyle = ColorStyle.GAME,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val dynamic = colorStyle == ColorStyle.SYSTEM && dynamicColorSupported
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val palette = when {
        dynamic -> dynamicPalette(scheme, dark)
        dark -> DarkPalette
        else -> LightPalette
    }
    GameColors.palette = palette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
