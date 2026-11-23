package com.monsterquest.app.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.monsterquest.core.Item
import com.monsterquest.core.ItemKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How an item moves in the Bag (see [AnimatedItemIcon]). */
private enum class Motion { NONE, CRYSTAL, POTION, HEART, FEATHER, BERRY, HOP, SWAY, WIGGLE, FLOP, SIZZLE, STEAM, GLINT, SMOKE, FLICKER, MOOD, INCUBATOR }

private fun motionOf(item: Item): Motion = when {
    item.kind == ItemKind.CAPTURE -> Motion.CRYSTAL
    item == Item.RESTORE_POTION -> Motion.HEART
    item.kind == ItemKind.HEAL_HP || item.kind == ItemKind.HEAL_MP -> Motion.POTION
    item == Item.PHOENIX_FEATHER -> Motion.FEATHER
    item.kind == ItemKind.BAIT -> Motion.BERRY
    item == Item.TREAT -> Motion.HOP
    item == Item.MOOD_CRYSTAL -> Motion.MOOD
    item == Item.INCUBATOR -> Motion.INCUBATOR
    item.kind == ItemKind.FOOD -> when (item.name.substringBefore("_FINE").substringBefore("_GOURMET")) {
        "SEED_MIX" -> Motion.HOP
        "ORCHARD_FRUIT" -> Motion.SWAY
        "WRIGGLE_GRUBS" -> Motion.WIGGLE
        "FRESH_FISH" -> Motion.FLOP
        "RAW_MEAT" -> Motion.SIZZLE
        "RICH_COMPOST" -> Motion.STEAM
        "MINERAL_SALTS" -> Motion.GLINT
        "SPIRIT_INCENSE" -> Motion.SMOKE
        "DRAGON_FEAST" -> Motion.FLICKER
        else -> Motion.HOP
    }
    else -> Motion.NONE
}

/** A crystal's tier, 0 (Binding) to 5 (Perfect): better ones glow, sparkle and shift colour. */
private fun crystalTier(item: Item): Int = listOf(
    Item.BINDING_CRYSTAL, Item.RESONANT_CRYSTAL, Item.RADIANT_CRYSTAL, Item.PRISMATIC_CRYSTAL, Item.SOVEREIGN_CRYSTAL, Item.PERFECT_ORB,
).indexOf(item).coerceAtLeast(0)

private val GOLD = Color(0xFFEBC678)

/** [c] with its hue turned by [deg] degrees. */
private fun hueShift(c: Color, deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A four-point sparkle at [at], [r] across, fading in and out on a [period]-second cycle. */
private fun DrawScope.sparkle(at: Offset, r: Float, t: Float, period: Float, color: Color) {
    val p = (t / period) % 1f
    val a = if (p in 0.7f..0.9f) sin((p - 0.7f) / 0.2f * PI.toFloat()) else 0f
    if (a <= 0f) return
    val path = Path().apply {
        moveTo(at.x, at.y - r); lineTo(at.x + r * 0.28f, at.y - r * 0.28f); lineTo(at.x + r, at.y)
        lineTo(at.x + r * 0.28f, at.y + r * 0.28f); lineTo(at.x, at.y + r); lineTo(at.x - r * 0.28f, at.y + r * 0.28f)
        lineTo(at.x - r, at.y); lineTo(at.x - r * 0.28f, at.y - r * 0.28f); close()
    }
    drawPath(path, color.copy(alpha = a))
}

/** Puffs rising from [from] and fading: bubbles, embers, steam or smoke. */
private fun DrawScope.rising(from: Offset, t: Float, color: Color, count: Int, period: Float, height: Float, r: Float, spread: Float) {
    for (k in 0 until count) {
        val p = ((t / period) + k.toFloat() / count) % 1f
        val x = from.x + sin((p * 6.28f + k * 1.9f).toDouble()).toFloat() * spread + (k - (count - 1) / 2f) * spread * 0.8f
        val y = from.y - p * height
        val alpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
        drawCircle(color.copy(alpha = alpha * 0.8f), r * (0.7f + 0.6f * p), Offset(x, y))
    }
}

/**
 * An item's icon in the Bag, moving in its own way: crystals float above a
 * shadow (better ones glow, sparkle and shift colour), potions bob and bubble,
 * the Restore Potion beats like a heart, the feather drifts on embers, food
 * hops, sways, wriggles, sizzles or smoulders (Fine food glows, Gourmet
 * sparkles too). Still when effects are reduced.
 */
@Composable
fun AnimatedItemIcon(item: Item, size: Dp) {
    if (item == Item.FARWISP) {
        FarwispOrb(Modifier.size(size))
        return
    }
    val motion = motionOf(item)
    if (ArtQuality.reduced || motion == Motion.NONE) {
        GIcon(item.icon(), Modifier.size(size), item.color())
        return
    }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { clock.longValue = it - start }
    }
    fun sec() = clock.longValue / 1000f
    val tier = crystalTier(item)
    val hueing = motion == Motion.MOOD || (motion == Motion.CRYSTAL && tier in setOf(3, 5))
    // Colour shifts in 15 steps, so the icon is redrawn a few times a second, not every frame.
    val hueStep by remember(hueing) { derivedStateOf { if (hueing) ((clock.longValue / (if (tier == 5) 250 else 450)) % 15).toInt() else 0 } }
    val tint = if (hueing) hueShift(item.color(), hueStep * 24f) else item.color()
    val foodTier = if (item.kind == ItemKind.FOOD) item.power.toInt() else 0
    val base = item.color()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Behind: shadows, halos and glows.
        Canvas(Modifier.matchParentSize()) {
            val t = sec()
            val w = this.size.width
            when (motion) {
                Motion.CRYSTAL -> {
                    val lift = (sin(t * 2 * PI / 3.4 - tier * 0.4) + 1).toFloat() / 2f
                    drawOval(Color.Black.copy(alpha = 0.35f - 0.15f * lift), Offset(w * (0.3f + 0.06f * lift), w * 0.9f), Size(w * (0.4f - 0.12f * lift), w * 0.08f))
                    if (tier >= 2) drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f + 0.12f * lift), Color.Transparent)), w * (0.55f + 0.04f * tier))
                }
                Motion.INCUBATOR -> drawCircle(Brush.radialGradient(listOf(Color(0xFFF2A050).copy(alpha = 0.25f + 0.25f * abs(sin(t * 1.2f))), Color.Transparent)), w * 0.65f)
                else -> {}
            }
            // Fine and Gourmet food glow gold.
            if (foodTier >= 2) drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.15f + 0.12f * abs(sin(t * 1.1f))), Color.Transparent)), w * 0.62f)
        }
        // The icon itself, moved by its motion.
        GIcon(
            item.icon(),
            Modifier.size(size).graphicsLayer {
                val t = sec()
                fun wave(period: Float, phase: Float = 0f) = sin((t / period + phase) * 2 * PI).toFloat()
                val px = this.size.width / 28f
                when (motion) {
                    Motion.CRYSTAL -> translationY = px * (-1.5f - 2.5f * wave(3.4f, -tier * 0.4f / 3.4f))
                    Motion.POTION, Motion.BERRY, Motion.GLINT, Motion.MOOD -> translationY = px * -2f * (wave(2.4f) + 1) / 2
                    Motion.HEART -> { val b = t % 1.8f; val s = if (b < 0.25f) sin(b / 0.25f * PI).toFloat() * 0.14f else if (b in 0.45f..0.7f) sin((b - 0.45f) / 0.25f * PI).toFloat() * 0.09f else 0f; scaleX = 1 + s; scaleY = 1 + s }
                    Motion.FEATHER -> { translationY = px * -2.5f * (wave(3.2f) + 1) / 2; rotationZ = 7f * wave(3.2f, 0.25f) }
                    Motion.HOP -> { val p = (t / 1.4f) % 1f; translationY = if (p > 0.6f) -px * 4f * sin((p - 0.6f) / 0.4f * PI).toFloat() else 0f }
                    Motion.SWAY -> { transformOrigin = TransformOrigin(0.5f, 0f); rotationZ = 7f * wave(2.8f) }
                    Motion.WIGGLE -> { rotationZ = 9f * wave(0.7f); translationX = px * wave(0.7f, 0.25f) }
                    Motion.FLOP -> { val p = (t / 2.2f) % 1f; rotationZ = if (p < 0.4f) -14f * sin(p / 0.4f * 2 * PI).toFloat() else 0f }
                    Motion.SIZZLE -> { val s = 1 + 0.06f * wave(1.2f); scaleX = s; scaleY = s }
                    Motion.STEAM -> { transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = 4f * wave(3f) }
                    Motion.SMOKE -> translationY = px * -0.8f * wave(3f)
                    Motion.FLICKER -> { transformOrigin = TransformOrigin(0.5f, 1f); scaleY = 1 + 0.08f * wave(0.45f); rotationZ = 3f * wave(0.9f) }
                    Motion.INCUBATOR -> { val p = (t / 2.4f) % 1f; rotationZ = if (p < 0.25f) 6f * sin(p / 0.25f * 4 * PI).toFloat() else 0f }
                    Motion.NONE -> {}
                }
            },
            tint,
        )
        // In front: bubbles, embers, steam, smoke and sparkles.
        Canvas(Modifier.matchParentSize()) {
            val t = sec()
            val w = this.size.width
            when (motion) {
                Motion.POTION -> rising(Offset(w * 0.5f, w * 0.75f), t, Color.White, 3, 2.2f, w * 0.5f, w * 0.04f, w * 0.08f)
                Motion.FEATHER -> rising(Offset(w * 0.3f, w * 0.85f), t, Color(0xFFF3B27C), 3, 2.6f, w * 0.6f, w * 0.035f, w * 0.06f)
                Motion.SIZZLE, Motion.STEAM -> rising(Offset(w * 0.5f, w * 0.35f), t, Color(0xFFCFE0DC), 3, 3f, w * 0.45f, w * 0.06f, w * 0.1f)
                Motion.SMOKE -> rising(Offset(w * 0.55f, w * 0.3f), t, Color(0xFFCDB8EC), 3, 3.2f, w * 0.55f, w * 0.05f, w * 0.12f)
                Motion.FLICKER -> rising(Offset(w * 0.5f, w * 0.2f), t, Color(0xFFF3B27C), 2, 1.6f, w * 0.3f, w * 0.03f, w * 0.06f)
                Motion.GLINT -> { sparkle(Offset(w * 0.8f, w * 0.18f), w * 0.12f, t, 3f, Color.White); sparkle(Offset(w * 0.2f, w * 0.3f), w * 0.09f, t + 1.4f, 3f, Color.White) }
                Motion.CRYSTAL -> if (tier >= 3) sparkle(Offset(w * 0.82f, w * 0.15f), w * 0.14f, t, 3f, Color.White)
                Motion.BERRY -> if (item == Item.GOLDEN_BERRY) { sparkle(Offset(w * 0.85f, w * 0.2f), w * 0.13f, t, 2.6f, Color.White); sparkle(Offset(w * 0.15f, w * 0.25f), w * 0.1f, t + 1.2f, 2.6f, Color.White) }
                Motion.MOOD -> sparkle(Offset(w * 0.8f, w * 0.15f), w * 0.12f, t, 2.4f, Color.White)
                else -> {}
            }
            if (foodTier >= 3) { sparkle(Offset(w * 0.88f, w * 0.1f), w * 0.12f, t, 2.8f, GOLD); sparkle(Offset(w * 0.1f, w * 0.2f), w * 0.09f, t + 1.5f, 2.8f, GOLD) }
        }
    }
}
