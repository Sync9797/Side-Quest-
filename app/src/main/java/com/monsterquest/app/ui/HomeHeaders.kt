package com.monsterquest.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.core.GameState
import com.monsterquest.core.TaskSource
import java.time.Instant
import java.time.ZoneId

/** "Good morning" / "Good afternoon" / "Good evening" for the hour. */
fun greeting(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    when (Instant.ofEpochMilli(nowMs).atZone(zone).hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

/**
 * The home page's welcome header (left pane on big screens, top on phones):
 * a greeting, the hero's name in large type, and the hero's portrait in a
 * glowing ring on the right.
 */
@Composable
fun WelcomeHeader(vm: GameViewModel, nowMs: Long) {
    val s = vm.state
    val h = s.hero
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(nowMs), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Welcome back, ${h.name}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 30.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        // The hero's portrait in a gold-to-blue ring, like an account picture.
        Box(
            Modifier
                .size(56.dp)
                .border(BorderStroke(3.dp, Brush.sweepGradient(listOf(Gold, MpBlue, XpPurple, Gold))), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            HeroArt(h.heroClass, Modifier.fillMaxSize(), portrait = false, outfit = HeroOutfit.of(s, hoodDown = true))
        }
    }
}

/** Today's events, to-dos and reminders still to do, for the agenda header. */
private fun agendaLine(s: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val open = s.tasks.filter { !it.completed }
    val events = open.count { t ->
        t.source == TaskSource.CALENDAR && t.startMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } == today
    }
    val todos = open.count { it.source == TaskSource.MANUAL }
    val reminders = s.reminders.count { it.ringsOn(today) && it.history[today.toEpochDay()] == null }
    fun n(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"
    if (events + todos + reminders == 0) return "All clear for today"
    return listOfNotNull(
        n(events, "event").takeIf { events > 0 } ?: "No events",
        n(todos, "to-do").takeIf { todos > 0 },
        n(reminders, "reminder").takeIf { reminders > 0 },
    ).joinToString(" • ") + " today"
}

/**
 * The header over the quest list (right pane on big screens): a large title
 * for the to-do list and events, today's counts, and a quick add button.
 */
@Composable
fun AgendaHeader(vm: GameViewModel, nowMs: Long, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(GameIcon.CALENDAR, Modifier.size(30.dp), Gold)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("To-do & events", fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(agendaLine(vm.state, nowMs), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = onAdd,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            GIcon(GameIcon.PLUS, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
        }
    }
}
