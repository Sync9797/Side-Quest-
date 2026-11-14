package com.monsterquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.core.Goal
import com.monsterquest.core.Goals

/** Today's and this week's goals on Home: progress, rewards and Claim buttons. Folds to one line. */
@Composable
fun GoalsCard(vm: GameViewModel) {
    val s = vm.state
    val today = java.time.LocalDate.now().toEpochDay()
    val daily = Goals.daily(today)
    val weekly = Goals.weekly(today)
    val bountyReady = com.monsterquest.core.Bounty.ready(s, today)
    val ready = (daily + weekly).count { Goals.done(s, it) && it.id !in s.goalsClaimed } + (if (bountyReady) 1 else 0)
    val doneToday = daily.count { it.id in s.goalsClaimed }
    var open by rememberSaveable { mutableStateOf(false) }
    GameCard(Modifier.padding(top = 8.dp), border = if (ready > 0) Gold else null) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                IconText(GameIcon.TARGET, "Goals", tint = Gold, textColor = Gold, bold = true, fontSize = 14.sp)
                Text(
                    "Today $doneToday/${daily.size} • This week ${weekly.count { it.id in s.goalsClaimed }}/${weekly.size}" +
                        if (ready > 0) " • $ready to claim!" else "",
                    fontSize = 12.sp,
                    color = if (ready > 0) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            Text("Today", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            daily.forEach { GoalRow(vm, it) }
            Text("Finish all three: +${Goals.DAILY_SET_GOLD} gold and a Monster Treat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This week", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            weekly.forEach { GoalRow(vm, it) }
            Text("Finish all three: +${Goals.WEEKLY_SET_GOLD} gold and a Golden Berry.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BountySection(vm, today)
        }
    }
}

@Composable
private fun GoalRow(vm: GameViewModel, g: Goal) {
    val s = vm.state
    val claimed = g.id in s.goalsClaimed
    val p = Goals.progress(s, g)
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.text, fontSize = 13.sp, color = if (claimed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(
                    "+${g.gold} gold" + (g.item?.let { ", ${it.label}" } ?: ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                claimed -> GIcon(GameIcon.CHECK, Modifier.size(18.dp), HpGreen)
                p >= g.goal -> Button(onClick = { vm.claimGoal(g) }) { Text("Claim") }
                else -> Text("%,d/%,d".format(p, g.goal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!claimed) Bar(p.toFloat() / g.goal, Gold, Modifier.fillMaxWidth().padding(top = 2.dp), heightDp = 4)
    }
}

/** The weekly bounty: the same three real-life goals every week, for a guaranteed Rare-or-better gear piece. */
@Composable
private fun BountySection(vm: GameViewModel, today: Long) {
    val s = vm.state
    TipCard(vm, com.monsterquest.core.Tip.BOUNTY, Modifier.padding(top = 8.dp))
    val claimed = com.monsterquest.core.Bounty.claimed(s, today)
    val lines = com.monsterquest.core.Bounty.lines(s)
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Weekly bounty", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        when {
            claimed -> GIcon(GameIcon.CHECK, Modifier.size(18.dp), HpGreen)
            lines.all { it.done } -> Button(onClick = { vm.claimBounty() }) { Text("Claim") }
        }
    }
    lines.forEach { l ->
        Column(Modifier.padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.text, fontSize = 13.sp, modifier = Modifier.weight(1f), color = if (l.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (l.done) GIcon(GameIcon.CHECK, Modifier.size(16.dp), HpGreen)
                else Text("%,d/%,d".format(l.progress, l.goal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!claimed && !l.done) Bar(l.progress.toFloat() / l.goal, XpPurple, Modifier.fillMaxWidth().padding(top = 2.dp), heightDp = 4)
        }
    }
    Text(
        "All three: +${com.monsterquest.core.Bounty.GOLD} gold, a Resonant Crystal and a guaranteed Rare or Epic weapon or armor piece.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
