package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.core.WeeklyChampion

/** This week's champion on the Hero tab: who, the rule, your best, your record and badges, and the Challenge button. */
@Composable
fun ChampionCard(vm: GameViewModel) {
    val s = vm.state
    val now = System.currentTimeMillis()
    val c = WeeklyChampion.current(s, now)
    val wk = WeeklyChampion.week(s, now)
    val record = WeeklyChampion.record(s, c.rule)
    SectionTitle("Weekly champion")
    GameCard(border = Gold, modifier = Modifier.testTag("champion-card")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonsterArt(c.species, Modifier.size(72.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold, color = Gold)
                    Text("Lv ${c.level} • ${c.species.typeLabel} • new champion every Monday", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Rule: ${c.rule.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Text(c.rule.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOf(
                    wk.bestRounds?.let { "This week: best $it round${if (it == 1) "" else "s"}" } ?: "Not beaten this week",
                    record?.let { "${c.rule.label} record: $it" } ?: "No ${c.rule.label} record yet",
                ).joinToString(" • "),
                fontSize = 12.sp,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GIcon(GameIcon.STAR, Modifier.size(16.dp), Gold)
                Spacer(Modifier.width(4.dp))
                Text("Champion's Badges: ${s.championBadges}", fontSize = 12.sp, color = Gold, modifier = Modifier.weight(1f))
            }
            Text(
                if (wk.prized) "This week's prize is claimed. Beat your record for a badge."
                else "Beat it for ${WeeklyChampion.PRIZE_GOLD} gold and ${WeeklyChampion.PRIZE_CRYSTALS} Resonant Crystals; beat your record for a badge.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = vm::challengeChampion, modifier = Modifier.fillMaxWidth().testTag("champion-challenge")) { Text("Challenge") }
        }
    }
}
