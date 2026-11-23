package com.monsterquest.app.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MIST_DEEP = Color(0xFF2E5A63)
private val MIST = Color(0xFF6FC7B8)
private val MIST_LIGHT = Color(0xFFCDEFE6)
private val VIOLET = Color(0xFFB79CEB)

/**
 * The Farwisp: a ball of drifting mist. Wisps of gas swirl round it at
 * different speeds, the glowing heart breathes in and out, and specks of
 * vapor rise off it and fade.
 */
@Composable
fun FarwispOrb(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "farwisp")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "breathe")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "rise")
    Canvas(modifier.semantics { contentDescription = "Farwisp" }) {
        val c = center
        val r = size.minDimension / 2f
        // A soft halo of mist.
        drawCircle(Brush.radialGradient(listOf(MIST.copy(alpha = 0.35f + 0.15f * breathe), Color.Transparent), c, r), r, c)
        // The ball itself: deep at the edge, pale toward the heart.
        val ball = r * 0.62f
        drawCircle(Brush.radialGradient(listOf(MIST_LIGHT, MIST, MIST_DEEP), c + Offset(-ball * 0.2f, -ball * 0.25f), ball * 1.25f), ball, c)
        // Swirling wisps: arcs of mist circling at three speeds, each wavering in size.
        for (i in 0 until 3) {
            val speed = 1f + i * 0.6f
            val start = (spin * 360f * speed * (if (i % 2 == 0) 1 else -1) + i * 120f) % 360f
            val wr = ball * (0.55f + 0.22f * i + 0.04f * sin((spin * 2 * PI * speed + i).toFloat()))
            drawArc(
                color = (if (i == 1) VIOLET else MIST_LIGHT).copy(alpha = 0.55f - i * 0.1f),
                startAngle = start, sweepAngle = 110f + 30f * breathe, useCenter = false,
                topLeft = Offset(c.x - wr, c.y - wr * 0.82f), size = androidx.compose.ui.geometry.Size(wr * 2, wr * 1.64f),
                style = Stroke(width = r * (0.09f - i * 0.018f), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        // The heart: a bright core that breathes.
        val core = ball * (0.28f + 0.08f * breathe)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), MIST_LIGHT.copy(alpha = 0.6f), Color.Transparent), c, core * 1.8f), core * 1.8f, c)
        // A glint on the glass-like surface.
        drawCircle(Color.White.copy(alpha = 0.55f), ball * 0.12f, c + Offset(-ball * 0.38f, -ball * 0.42f))
        // Specks of vapor drifting up and fading.
        for (k in 0 until 4) {
            val p = (rise + k / 4f) % 1f
            val a = (k * 1.7f + 0.6f).toDouble()
            val x = c.x + (cos(a) * ball * 0.7).toFloat() + sin((p * 6.28f + k).toDouble()).toFloat() * r * 0.06f
            val y = c.y - ball * 0.3f - p * r * 0.75f
            drawCircle(MIST_LIGHT.copy(alpha = (1f - p) * 0.7f), r * (0.05f - 0.02f * p), Offset(x, y))
        }
    }
}
