package com.monsterquest.app.ui

import kotlin.math.roundToInt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.GameIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, heightDp: Int = 8) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .height(heightDp.dp)
            .clip(RoundedCornerShape(50)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

fun hpColor(fraction: Float): Color = when {
    fraction > 0.5f -> HpGreen
    fraction > 0.2f -> HpYellow
    else -> HpRed
}

@Composable
fun LabeledBar(label: String, value: Int, max: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value / $max", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Bar(value.toFloat() / max.coerceAtLeast(1), color, Modifier.fillMaxWidth())
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
fun GameCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border?.let { androidx.compose.foundation.BorderStroke(2.dp, it) },
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

/** A value over a label. Give it Modifier.weight(1f) in a Row so a row of pills always fits. */
@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, note: String? = null, noteColor: Color = Mint) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // E.g. "+18 gear": how much of the number comes from weapon and armor.
        if (note != null) Text(note, fontSize = 10.sp, color = noteColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A button with tight padding whose text ellipsizes instead of overflowing on narrow screens. */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, content = content)
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, content = content)
    }
}

/**
 * The top bar: your level (with its star and a thin XP bar), gold and torches
 * as compact pills on the left, and the battery saver and settings buttons
 * together on the right. It keeps a fixed spacing, so it doesn't stretch out
 * on a wide foldable screen.
 */
@Composable
fun ResourceHeader(
    level: Int,
    gold: Int,
    tokens: Int,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    /** The battery saver button: while it's on, wild monsters can find you as you walk. */
    saverOn: Boolean = false,
    onSaver: (() -> Unit)? = null,
    /** How far through the current level the hero is (0..1). */
    xpFraction: Float = 0f,
    /** The most torches the hero can hold now (shown as "12/15"); null hides it. */
    torchCap: Int? = null,
) {
    val pill = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every 50 levels earns a star; the level shown starts over within it.
        val stars = com.monsterquest.core.Stars.of(level)
        Column(
            Modifier
                .background(pill, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                .semantics(mergeDescendants = true) { contentDescription = com.monsterquest.core.Stars.label(level) },
        ) {
            Text(
                (if (stars > 0) "★$stars  " else "") + "LV ${com.monsterquest.core.Stars.shownLevel(level)}",
                fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, letterSpacing = 1.sp, fontSize = 14.sp,
            )
            // Progress to the next level.
            Box(Modifier.padding(top = 3.dp).width(52.dp).height(3.dp).background(Gold.copy(alpha = 0.25f), androidx.compose.foundation.shape.RoundedCornerShape(50))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(xpFraction.coerceIn(0f, 1f)).background(Gold, androidx.compose.foundation.shape.RoundedCornerShape(50)))
            }
        }
        Row(
            Modifier
                .background(pill, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$gold gold" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconText(GameIcon.COIN, "%,d".format(gold), tint = Gold, textColor = Gold, bold = true)
        }
        Row(
            Modifier
                .background(pill, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$tokens torch${if (tokens == 1) "" else "es"}" + (torchCap?.let { " of $it" } ?: "")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconText(GameIcon.FLAME, torchCap?.let { "$tokens/$it" } ?: "$tokens", tint = Color(0xFFF2A65A), textColor = Color(0xFFF2A65A), bold = true)
        }
        Spacer(Modifier.weight(1f))
        if (onSaver != null) {
            androidx.compose.material3.IconButton(onClick = onSaver, modifier = Modifier.semantics { contentDescription = "Battery saver" }) {
                com.monsterquest.app.ui.art.GIcon(GameIcon.BATTERY, Modifier.size(22.dp), if (saverOn) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onSettings != null) {
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Settings" }) {
                com.monsterquest.app.ui.art.GIcon(com.monsterquest.app.ui.art.GameIcon.GEAR, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A distance for people: feet up close, then miles (or meters and
 * kilometers), rounded so it doesn't flicker as GPS wobbles.
 */
fun formatDistance(meters: Double, imperial: Boolean): String {
    if (!imperial) {
        return if (meters < 1000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
        else String.format(java.util.Locale.US, "%.1f km", meters / 1000)
    }
    // Feet up close, then miles (a tenth of a mile is 528 ft).
    val feet = meters * 3.28084
    val miles = feet / 5280
    return when {
        feet < 1000 -> "${((feet / 10).roundToInt() * 10).coerceAtLeast(10)} ft"
        miles < 10 -> String.format(java.util.Locale.US, "%.1f mi", miles)
        else -> "${miles.roundToInt()} mi"
    }
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

/**
 * Shows or hides a dropdown's contents by growing down from (and shrinking
 * back up to) its header, with a fade. Use it for every section that opens
 * below its title, so they all move the same way.
 */
@Composable
fun DropdownReveal(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
    ) { content() }
}

/** HP now (solid), what the potion adds (lighter), and the rest (track). */
@Composable
fun HealPreviewBar(hp: Int, after: Int, maxHp: Int) {
    val now = hp.toFloat() / maxHp
    val healed = after.toFloat() / maxHp
    val color = hpColor(now)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gain = HpGreen
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .semantics { contentDescription = "HP $hp of $maxHp, $after after healing" },
    ) {
        drawRect(track)
        if (healed > now) drawRect(gain.copy(alpha = 0.4f), size = size.copy(width = size.width * healed))
        drawRect(color, size = size.copy(width = size.width * now))
    }
}

/**
 * A playful wiggle every couple of seconds while [active], to say "something
 * is waiting here" (a finished adventure). Only rotation changes, drawn on
 * its own layer, so the button's contents don't recompose each frame.
 */
@Composable
fun Modifier.wobble(active: Boolean): Modifier {
    if (!active) return this
    val angle = androidx.compose.animation.core.rememberInfiniteTransition(label = "wobble").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 2_200
                0f at 0
                -6f at 90
                6f at 210
                -5f at 330
                4f at 450
                -2f at 560
                0f at 660
                0f at 2_200
            },
        ),
        label = "wobbleAngle",
    )
    return this.then(androidx.compose.ui.Modifier.graphicsLayer { rotationZ = angle.value })
}

/**
 * A soft glow that breathes behind the content while [active] (an egg ready
 * to hatch). Drawn behind on its own, so only the glow redraws each frame.
 */
@Composable
fun Modifier.readyGlow(active: Boolean, color: Color = Gold): Modifier {
    if (!active) return this
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1_100),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )
    return this.then(
        androidx.compose.ui.Modifier.drawBehind {
            val r = size.minDimension * (0.62f + 0.12f * pulse.value)
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(color.copy(alpha = 0.75f * pulse.value), color.copy(alpha = 0.25f * pulse.value), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        },
    )
}
