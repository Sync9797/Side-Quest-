package com.monsterquest.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.PlayerMarkerArt
import com.monsterquest.core.Stars

/**
 * A new star: the level badge turns over from Lv 50 to ★1 · Lv 1 while a star
 * drops into place, then what the star brings appears point by point. Shown
 * once per star, over whatever screen you're on.
 */
@Composable
fun StarCelebration(vm: GameViewModel) {
    val s = vm.state
    val level = s.hero.level
    val star = Stars.of(level)
    // 0: the old badge; 1: the star lands and the badge turns over; 2+: the perks, one at a time.
    var stage by remember { mutableIntStateOf(0) }
    val perks = remember(star, level) { Stars.perks(star, level) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        stage = 1
        kotlinx.coroutines.delay(900)
        repeat(perks.size + 1) { stage++; kotlinx.coroutines.delay(280) }
    }
    val starScale by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(500), label = "star")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .testTag("star-celebration"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .padding(18.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.5.dp, Gold, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GIcon(GameIcon.STAR, Modifier.size(64.dp).scale(starScale), Gold)
            Text(
                if (star == 1) "YOUR FIRST STAR" else "STAR $star",
                color = Gold, fontSize = 22.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(10.dp))
            // The level badge, as at the top of the screen, turning over.
            AnimatedContent(
                targetState = stage >= 1,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "badge",
            ) { turned ->
                Text(
                    if (turned) "★$star  LV ${Stars.shownLevel(level)}" else "LV ${level - 1}",
                    fontWeight = FontWeight.Bold, color = Gold, letterSpacing = 1.sp, fontSize = 18.sp,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            Text(
                "Real level $level",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // How you'll look on the map from now on.
                PlayerMarkerArt(s.hero.heroClass, HeroOutfit.of(s, hoodDown = true), star, Modifier.size(84.dp))
                Text(
                    if (star == 1) "Every 50 levels you earn a star. Here's what your first one brings:" else "Here's what this star brings:",
                    fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            perks.forEachIndexed { i, p ->
                AnimatedVisibility(visible = stage >= 2 + i, enter = fadeIn(tween(350)) + slideInVertically { it / 3 }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        GIcon(
                            when {
                                "box" in p -> GameIcon.CHEST
                                "champions" in p -> GameIcon.SHIELD
                                "skill" in p -> GameIcon.BOOK
                                "map" in p -> GameIcon.PIN
                                i == 0 -> GameIcon.STAR
                                else -> GameIcon.SPARK
                            },
                            Modifier.size(18.dp).padding(top = 2.dp), Gold,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(p, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { vm.celebrateStar() },
                enabled = stage >= 2 + perks.size,
                modifier = Modifier.fillMaxWidth().testTag("star-onward"),
            ) { Text("ONWARD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center) }
        }
    }
}
