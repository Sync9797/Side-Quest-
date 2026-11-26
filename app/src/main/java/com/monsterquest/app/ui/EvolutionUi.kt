package com.monsterquest.app.ui

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.EvolutionShow
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.core.Element
import com.monsterquest.core.Mythic
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An evolution, played full screen: the monster glows, then flickers between
 * its old and new shapes as white silhouettes, faster and faster, until a
 * burst of light shows the new form. The hidden legendary's last evolution
 * (level 100) is bigger: the screen shakes, stars rush in, and the reveal
 * lingers. Tap Skip to jump to the end.
 */
@Composable
fun EvolutionScreen(show: EvolutionShow, onDone: () -> Unit) {
    val grand = show.into.element == Element.MYTHICAL
    val duration = if (grand) 7_000 else 4_600
    val progress = remember(show) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(show) { progress.animateTo(1f, tween(duration, easing = LinearEasing)) }
    val p = progress.value
    // Phases: glow (to 0.12), flicker (to 0.72), flash (to 0.8), reveal.
    val flickering = p in 0.12f..0.72f
    val f = ((p - 0.12f) / 0.6f).coerceIn(0f, 1f)
    // The swaps speed up: about 2 a second at first, 14 by the end.
    val showNew = when {
        p >= 0.72f -> true
        flickering -> sin(2 * PI * (f * 3.5 + f * f * 9.0)) > 0
        else -> false
    }
    val white = when {
        p < 0.12f -> p / 0.12f * 0.85f
        p < 0.72f -> 1f
        p < 0.8f -> 1f
        else -> (1f - (p - 0.8f) / 0.12f).coerceIn(0f, 1f)
    }
    val flash = when {
        p < 0.68f -> 0f
        p < 0.74f -> (p - 0.68f) / 0.06f
        else -> (1f - (p - 0.74f) / 0.14f).coerceIn(0f, 1f)
    }
    val shake = if (grand && p in 0.4f..0.8f) sin(p * 260f) * 10f * ((p - 0.4f) / 0.4f) else 0f
    val done = p >= 1f
    val tint = show.into.element.color()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070D10))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .testTag("evolution"),
        contentAlignment = Alignment.Center,
    ) {
        // Behind: a glow, rays at the reveal, and (the grand one) stars rushing in.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.42f)
            val r = size.minDimension * 0.42f
            val glowA = if (p < 0.72f) 0.25f + 0.35f * p else 0.6f
            drawCircle(Brush.radialGradient(listOf((if (p < 0.72f) Color.White else tint).copy(alpha = glowA), Color.Transparent), c, r), r, c)
            if (p >= 0.74f) {
                val k = ((p - 0.74f) / 0.26f).coerceIn(0f, 1f)
                for (i in 0 until 16) {
                    val a = i / 16f * 2 * PI.toFloat() + p * 0.6f
                    drawLine(tint.copy(alpha = 0.35f * k), c, c + Offset(cos(a), sin(a)) * (r * (0.6f + 0.6f * k)), strokeWidth = if (i % 2 == 0) 5f else 2f)
                }
            }
            if (grand && p in 0.2f..0.85f) {
                for (i in 0 until 40) {
                    val a = i * 2.39996f
                    val start = r * (1.6f + (i % 5) * 0.12f)
                    val t = ((p * 3f + i * 0.037f) % 1f)
                    val d = start * (1f - t)
                    drawCircle(Color(0xFFFFF6D0).copy(alpha = 0.8f * t), if (i % 3 == 0) 4f else 2.5f, c + Offset(cos(a), sin(a)) * d)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp).graphicsLayer { translationX = shake }) {
            Text(
                if (done || p >= 0.8f) "${show.name} evolved into ${show.into.name}!" else "What? ${show.name} is evolving!",
                color = Color(0xFFF2EAD8), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            val sp = if (showNew) show.into else show.from
            MonsterArt(sp, Modifier.size(if (grand && showNew) 240.dp else 210.dp), shiny = show.shiny, idle = done, whiteness = white)
            Spacer(Modifier.height(16.dp))
            if (p >= 0.8f) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    show.into.types.forEach { t ->
                        Row(
                            Modifier.background(typeColor(t), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GIcon(t.icon(), Modifier.size(14.dp), Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(t.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (done) {
                Button(onClick = onDone, modifier = Modifier.testTag("evolution-done")) { Text("Continue") }
            } else {
                TextButton(onClick = { scope.launch { progress.snapTo(1f) } }) { Text("Skip", color = Color(0x99F2EAD8)) }
            }
        }
        // The burst of light.
        if (flash > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash)))
    }
}

/**
 * The hidden legendary has reached its evolution level: the screen dims and
 * says only "Oh?...". Next plays the evolution.
 */
@Composable
fun MythicOhPopup(vm: GameViewModel) {
    val m = Mythic.dueToEvolve(vm.state) ?: return
    var ready by remember(m.uid) { mutableStateOf(false) }
    LaunchedEffect(m.uid) {
        kotlinx.coroutines.delay(900)
        ready = true
    }
    val pulse by rememberInfiniteTransition(label = "oh").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .testTag("mythic-oh"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.5.dp, Gold, RoundedCornerShape(20.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonsterArt(m.species, Modifier.size(150.dp), shiny = m.shiny, whiteness = 0.15f + 0.3f * pulse)
            Spacer(Modifier.height(12.dp))
            Text("Oh?...", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { vm.mythicEvolve(m.uid) }, enabled = ready, modifier = Modifier.testTag("mythic-next")) { Text("Next") }
        }
    }
}

/** The Murk Scale on the Hero tab: what it is, and how this week is going (the tracker). */
@Composable
fun ScaleCard(vm: GameViewModel) {
    val s = vm.state
    if (!s.mythScale) return
    val now = System.currentTimeMillis()
    val (done, total) = Mythic.weekTasks(s, now)
    GameCard(border = Color(0xFF4F8FC9), modifier = Modifier.testTag("scale-card")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(30.dp)) {
                    val w = size.width
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.5f, w * 0.05f)
                        quadraticBezierTo(w * 0.92f, w * 0.25f, w * 0.85f, w * 0.6f)
                        quadraticBezierTo(w * 0.72f, w * 0.92f, w * 0.5f, w * 0.98f)
                        quadraticBezierTo(w * 0.28f, w * 0.92f, w * 0.15f, w * 0.6f)
                        quadraticBezierTo(w * 0.08f, w * 0.25f, w * 0.5f, w * 0.05f)
                        close()
                    }
                    drawPath(path, Brush.linearGradient(listOf(Color(0xFF4F8FC9), Color(0xFFE0704A))))
                    drawPath(path, Color(0xFFEBC678), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(Mythic.SCALE_NAME, fontWeight = FontWeight.Bold, color = Gold)
                    Text("Worn on your chest. It came from something that fled into the water.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(Mythic.scaleText(s, now), fontSize = 13.sp)
            if (!Mythic.owned(s) && total > 0) {
                Spacer(Modifier.height(6.dp))
                LabeledBar("This week's tasks", done, total, Color(0xFF4F8FC9))
            }
        }
    }
}
