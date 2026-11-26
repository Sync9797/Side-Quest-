package com.monsterquest.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.BattleView
import com.monsterquest.app.ui.art.color
import com.monsterquest.core.BattleEvent
import com.monsterquest.core.Element
import com.monsterquest.core.StrikeStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Battle animation state: where each fighter is on screen, which event is
 * playing and how far along it is, and whose turn it is.
 */
class BattleFx {
    val centers = mutableStateMapOf<String, Offset>()
    var event by mutableStateOf<BattleEvent?>(null)
    val progress = Animatable(0f)
    var currentKey by mutableStateOf<String?>(null)
}

val LocalBattleFx = staticCompositionLocalOf<BattleFx?> { null }

private fun duration(e: BattleEvent) = when (e.style) {
    StrikeStyle.BOLT -> 750
    StrikeStyle.SLASH -> 520
    StrikeStyle.HEAL -> 650
    StrikeStyle.WARD -> 700
    StrikeStyle.MISS -> 450
}

/** When the hit lands (0..1 of the event): bolts fly first, slashes land at once. */
private fun impactAt(e: BattleEvent) = if (e.style == StrikeStyle.BOLT) 0.62f else 0.15f

/** Plays new battle events one after another as they arrive. */
@Composable
fun rememberBattleFx(b: BattleView, speed: Float = 1f): BattleFx {
    val fx = remember { BattleFx() }
    var shown by remember { mutableStateOf(-1) }
    fx.currentKey = b.current?.key
    LaunchedEffect(b.events.size) {
        // A new battle (e.g. the next villain monster) starts its numbering over.
        if ((b.events.lastOrNull()?.seq ?: -1) < shown) shown = -1
        // Battle speed: Fast halves every animation; Instant skips them.
        if (speed <= 0f) {
            shown = b.events.lastOrNull()?.seq ?: shown
            fx.event = null
            return@LaunchedEffect
        }
        for (e in b.events.filter { it.seq > shown }) {
            fx.event = e
            fx.progress.snapTo(0f)
            fx.progress.animateTo(1f, tween((duration(e) * speed).toInt(), easing = LinearEasing))
            shown = e.seq
            delay((90 * speed).toLong())
        }
        fx.event = null
    }
    return fx
}

/**
 * Registers a fighter's position for animations, rings the fighter who is
 * attacking (gold) or whose turn it is (pulsing), and shakes and flashes the
 * one being hit.
 */
fun Modifier.fighterFx(key: String, alive: Boolean = true): Modifier = composed {
    val fx = LocalBattleFx.current ?: return@composed this
    val e = fx.event
    val p = fx.progress.value
    val pulse by rememberInfiniteTransition(label = "turn").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    // Everyone standing breathes, each a little out of step with the others.
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = androidx.compose.animation.core.LinearEasing)), label = "breath",
    )
    val seed = (key.hashCode() and 0xFFFF) / 65535f * (2 * PI).toFloat()
    val active = e == null && fx.currentKey == key
    // Idle: a slow rise and fall; on its turn, a ready little hop.
    val idleY = if (!alive) 0f else if (active) -kotlin.math.abs(sin(breath * 2 + seed)) * 5f else sin(breath + seed) * 2f
    val support = e != null && (e.style == StrikeStyle.HEAL || e.style == StrikeStyle.WARD)
    val attacking = e != null && e.actorKey == key && !support
    val hit = e != null && e.targetKey == key && e.style != StrikeStyle.MISS && !support && p >= impactAt(e)
    val after = if (e != null) ((p - impactAt(e)) / (1 - impactAt(e))).coerceIn(0f, 1f) else 0f
    val shake = if (hit) (sin(after * 9 * PI) * 7 * (1 - after)).toFloat() else 0f
    // The attacker lunges toward its target and springs back; the target is knocked back a touch.
    val from = e?.let { fx.centers[it.actorKey] }
    val to = e?.let { fx.centers[it.targetKey] }
    val dir = if (from != null && to != null && from != to) (to - from).let { it / it.getDistance() } else Offset.Zero
    val lunge = if (attacking && e != null && e.style != StrikeStyle.BOLT) sin((p / impactAt(e)).coerceIn(0f, 1f) * PI).toFloat() * 18f
    else if (attacking && e != null) sin((p / impactAt(e)).coerceIn(0f, 1f) * PI).toFloat() * 6f else 0f
    val knock = if (hit) (1 - after) * 8f else 0f
    val dx = shake + dir.x * lunge + dir.x * knock
    val dy = idleY + dir.y * lunge + dir.y * knock
    val ring = when {
        attacking -> Gold
        e == null && fx.currentKey == key -> Gold.copy(alpha = pulse)
        else -> Color.Transparent
    }
    this
        .onGloballyPositioned { fx.centers[key] = it.boundsInRoot().center }
        .offset { IntOffset(dx.dp.roundToPx(), dy.dp.roundToPx()) }
        .border(3.dp, ring, CircleShape)
        .background(if (hit) HpRed.copy(alpha = 0.35f * (1 - after)) else Color.Transparent, CircleShape)
}

/** Draws projectiles, slashes, sparkles and damage numbers over the battle screen. */
@Composable
fun BattleFxOverlay(modifier: Modifier = Modifier) {
    val fx = LocalBattleFx.current ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val e = fx.event ?: return@Canvas
        val p = fx.progress.value
        val from = (fx.centers[e.actorKey] ?: return@Canvas) - origin
        val to = (fx.centers[e.targetKey] ?: return@Canvas) - origin
        val color = if (e.element == Element.NEUTRAL) Color(0xFFF2F0E6) else e.element.color()
        val impact = impactAt(e)
        val after = ((p - impact) / (1 - impact)).coerceIn(0f, 1f)
        when (e.style) {
            StrikeStyle.BOLT -> {
                if (p < impact) drawBolt(e.element, color, from, to, p / impact) else drawBurst(color, to, after)
            }
            StrikeStyle.SLASH -> drawSlash(color, to, (p / 0.7f).coerceIn(0f, 1f))
            StrikeStyle.HEAL -> drawHeal(to, p)
            StrikeStyle.WARD -> drawWard(to, p)
            StrikeStyle.MISS -> {}
        }
        // Floating number or "Miss".
        if (p >= impact) {
            val (text, tint, size) = when (e.style) {
                StrikeStyle.MISS -> Triple("Miss", Color(0xFFB8C1BE), 18f)
                StrikeStyle.HEAL -> Triple("+${e.amount}", HpGreen, 22f)
                StrikeStyle.WARD -> Triple(if (e.amount > 0) "Shield ${e.amount}" else "Def up", MpBlue, 20f)
                else -> Triple((if (e.crit) "CRIT " else "") + "-${e.amount}", if (e.crit || e.superEffective) Gold else Color.White, if (e.crit) 28f else 24f)
            }
            numberPaint.textSize = size.sp.toPx()
            numberPaint.color = tint.copy(alpha = 1f - after * 0.6f).toArgb()
            numberPaint.setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
            // Float upward, or below the target when it sits near the top edge.
            val rise = after * 26.dp.toPx()
            val y = if (to.y > 90.dp.toPx()) to.y - 30.dp.toPx() - rise else to.y + 52.dp.toPx() - rise * 0.5f
            drawIntoCanvas { it.nativeCanvas.drawText(text, to.x, y, numberPaint) }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** A projectile of the attack's type flying from [from] to [to]; [t] is 0..1 of the flight. */
private fun DrawScope.drawBolt(element: Element, color: Color, from: Offset, to: Offset, t: Float) {
    val eased = t * t * (3 - 2 * t)
    val head = lerp(from, to, eased)
    val r = 11.dp.toPx()
    when (element) {
        Element.FIRE -> {
            // Fireball with a trail of embers.
            for (i in 1..6) {
                val trail = lerp(from, to, (eased - i * 0.05f).coerceAtLeast(0f))
                drawCircle(Color(0xFFF3B27C).copy(alpha = 0.55f - i * 0.08f), r * (1 - i * 0.12f), trail)
            }
            drawCircle(color.copy(alpha = 0.45f), r * 2f, head)
            drawCircle(color, r, head)
            drawCircle(Color(0xFFFFF0C2), r * 0.5f, head)
        }
        Element.ELECTRIC -> {
            // A jagged lightning bolt from the attacker to the head.
            val path = Path().apply { moveTo(from.x, from.y) }
            val steps = 9
            for (i in 1..steps) {
                val pt = lerp(from, head, i / steps.toFloat())
                val jitter = if (i == steps) 0f else ((i * 37 + (t * 20).toInt() * 13) % 17 - 8) * 1.6f.dp.toPx()
                path.lineTo(pt.x + jitter, pt.y - jitter * 0.4f)
            }
            drawPath(path, color.copy(alpha = 0.4f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Color(0xFFFFF7D6), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, r * 0.7f, head)
        }
        Element.WATER -> {
            for (i in 1..5) {
                val drop = lerp(from, to, (eased - i * 0.06f).coerceAtLeast(0f))
                drawCircle(Color(0xFFA9D3F2).copy(alpha = 0.6f - i * 0.1f), r * 0.45f, drop + Offset(0f, sin(i * 1.7f) * 6.dp.toPx()))
            }
            drawCircle(color.copy(alpha = 0.5f), r * 1.6f, head)
            drawCircle(color, r, head)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.35f, head + Offset(-r * 0.35f, -r * 0.35f))
        }
        Element.FLIGHT -> {
            // A spinning crescent of wind.
            val spin = t * 4 * PI.toFloat()
            for (k in 0..1) {
                val a0 = spin + k * PI.toFloat()
                drawArc(
                    color, startAngle = Math.toDegrees(a0.toDouble()).toFloat(), sweepAngle = 140f, useCenter = false,
                    topLeft = head - Offset(r * 1.4f, r * 1.4f), size = androidx.compose.ui.geometry.Size(r * 2.8f, r * 2.8f),
                    style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawLine(color.copy(alpha = 0.35f), lerp(from, head, 0.5f), head, 2.dp.toPx())
        }
        Element.GAS -> {
            // A billowing cloud that grows as it travels.
            val grow = 0.7f + eased * 0.8f
            listOf(Offset(0f, 0f), Offset(-0.8f, 0.3f), Offset(0.8f, 0.2f), Offset(0.1f, -0.6f)).forEach { o ->
                drawCircle(color.copy(alpha = 0.55f), r * 0.8f * grow, head + Offset(o.x * r, o.y * r) * grow)
            }
            drawCircle(Color(0xFFCFE3A2).copy(alpha = 0.5f), r * 0.5f * grow, head)
        }
        Element.CORRUPT -> {
            drawCircle(Color(0xFF2D2142).copy(alpha = 0.8f), r * 1.4f, head)
            drawCircle(color, r * 1.4f, head, style = Stroke(2.5f.dp.toPx()))
            val spin = t * 6
            for (i in 0..2) {
                val a = spin + i * 2.1f
                drawCircle(color, r * 0.3f, head + Offset(cos(a) * r * 2f, sin(a) * r * 2f))
            }
        }
        Element.EARTH -> {
            // A spinning rock trailed by leaves.
            for (i in 1..5) {
                val leaf = lerp(from, to, (eased - i * 0.07f).coerceAtLeast(0f))
                val a = t * 12 + i
                drawOval(
                    Color(0xFFCFE09A).copy(alpha = 0.75f - i * 0.12f),
                    topLeft = leaf + Offset(cos(a) * 6.dp.toPx() - 5.dp.toPx(), sin(a) * 6.dp.toPx() - 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 6.dp.toPx()),
                )
            }
            val rock = Path().apply {
                val spin = t * 8
                for (k in 0 until 6) {
                    val ang = spin + k * PI.toFloat() / 3
                    val rad = r * (if (k % 2 == 0) 1.1f else 0.85f)
                    val pt = head + Offset(cos(ang) * rad, sin(ang) * rad)
                    if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(rock, Color(0xFF7A6A4E))
            drawPath(rock, Color(0xFF2E3D1C), style = Stroke(2.dp.toPx()))
        }
        Element.NEUTRAL, Element.MYTHICAL -> {
            drawCircle(color.copy(alpha = 0.45f), r * 1.8f, head)
            drawCircle(color, r * 0.9f, head)
        }
    }
}

/** The bolt lands: an expanding ring and sparks of its color. */
private fun DrawScope.drawBurst(color: Color, at: Offset, t: Float) {
    val r = (14 + 40 * t).dp.toPx()
    drawCircle(color.copy(alpha = 0.5f * (1 - t)), r * 0.7f, at)
    drawCircle(color.copy(alpha = 1 - t), r, at, style = Stroke((4 * (1 - t) + 1).dp.toPx()))
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4
        val d = r * 1.1f
        drawCircle(color.copy(alpha = 1 - t), 3.dp.toPx(), at + Offset(cos(a) * d, sin(a) * d))
    }
}

/** Three quick claw/sword slashes across the target. */
private fun DrawScope.drawSlash(color: Color, at: Offset, t: Float) {
    val len = 70.dp.toPx()
    for (i in 0..2) {
        val start = i * 0.18f
        val local = ((t - start) / 0.5f).coerceIn(0f, 1f)
        if (local <= 0f) continue
        val fade = if (t > 0.75f) 1 - (t - 0.75f) / 0.25f else 1f
        val dx = (i - 1) * 14.dp.toPx()
        val a = at + Offset(-len / 2 + dx, -len / 2)
        val b = at + Offset(len / 2 + dx, len / 2)
        val tip = lerp(a, b, local)
        drawLine(color.copy(alpha = 0.45f * fade), a, tip, 10.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = fade), a, tip, 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Green sparkles rising around a healed fighter. */
private fun DrawScope.drawHeal(at: Offset, t: Float) {
    for (i in 0 until 7) {
        val x = at.x + ((i * 53) % 70 - 35).dp.toPx()
        val y = at.y + (20 - 60 * ((t + i * 0.13f) % 1f)).dp.toPx()
        drawCircle(HpGreen.copy(alpha = 0.8f * (1 - t)), (3 + i % 3).dp.toPx(), Offset(x, y))
    }
    drawCircle(HpGreen.copy(alpha = 0.25f * (1 - t)), 40.dp.toPx(), at)
}

/** A shimmering barrier closing around the target: Ward and Fortify. */
private fun DrawScope.drawWard(at: Offset, t: Float) {
    val r = (56 - 18 * t).dp.toPx()
    val alpha = if (t < 0.7f) 0.85f else 0.85f * (1 - t) / 0.3f
    drawCircle(MpBlue.copy(alpha = alpha), r, at, style = Stroke(width = 3.dp.toPx()))
    drawCircle(MpBlue.copy(alpha = alpha * 0.25f), r, at)
    for (i in 0 until 6) {
        val a = (i / 6f + t * 0.5f) * 2 * PI
        drawCircle(Color.White.copy(alpha = alpha), 2.5f.dp.toPx(), Offset(at.x + (r * cos(a)).toFloat(), at.y + (r * sin(a)).toFloat()))
    }
}

/** Small portraits of who acts next, the current one first. */
@Composable
fun TurnOrderStrip(b: BattleView, art: @Composable (com.monsterquest.app.FighterView, Modifier) -> Unit) {
    if (b.turnOrder.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Turn order", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        b.turnOrder.forEachIndexed { i, key ->
            val f = b.fighter(key) ?: return@forEachIndexed
            val enemy = com.monsterquest.core.Combatant.isEnemy(key)
            val ring = when {
                i == 0 -> Gold
                enemy -> BossRed.copy(alpha = 0.7f)
                else -> Line
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (i == 0) 34.dp else 28.dp)
                        .border(if (i == 0) 2.dp else 1.dp, ring, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                ) { art(f, Modifier.fillMaxSize()) }
                if (i == 0) Text("Now", fontSize = 9.sp, color = Gold, fontWeight = FontWeight.Bold)
            }
            if (i < b.turnOrder.size - 1) Text(">", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
