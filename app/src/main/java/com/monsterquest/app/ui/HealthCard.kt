package com.monsterquest.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.core.Health
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val WaterBlue = Color(0xFF4FA8E8)
private val SleepIndigo = Color(0xFF7B7FE0)

/**
 * Health on the Home screen: a card that folds to one line (steps and water
 * today) and opens into steps by hour, a water tracker and a preview of the
 * sleep tracker to come. Nothing here pays rewards; it's just for you.
 */
@Composable
fun HealthCard(vm: GameViewModel, nowMs: Long) {
    val s = vm.state
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val steps = if (s.stepsDay == today.toEpochDay()) s.stepsToday else 0
    val glasses = Health.glasses(s, today.toEpochDay())
    val open = s.settings.healthOpen
    LaunchedEffect(open) { if (open) vm.refreshHourlySteps() }

    GameCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { vm.updateSettings { it.copy(healthOpen = !open) } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIcon(GameIcon.HEART, Modifier.size(22.dp), HpRed)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Health", style = MaterialTheme.typography.titleMedium)
                val slept = com.monsterquest.core.SleepLog.lastNight(s, today)?.let { " • ${com.monsterquest.core.SleepLog.label(it.asleepMin)} sleep" } ?: ""
                Text(
                    "${"%,d".format(steps)} steps • $glasses of ${s.waterGoal} glasses$slept",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownReveal(open) {
            Column {
                Spacer(Modifier.height(10.dp))
                StepsSection(vm, today, steps, nowMs)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                WaterSection(vm, glasses)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SleepSection(vm, today)
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: GameIcon, title: String, tint: Color, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, Modifier.size(18.dp), tint)
        Spacer(Modifier.width(6.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Today's steps, with a bar for every hour. Tap a bar to see its count. */
@Composable
private fun StepsSection(vm: GameViewModel, today: LocalDate, steps: Int, nowMs: Long) {
    val fromHealth = vm.healthHourly
    val hours = fromHealth ?: Health.hourly(vm.state, today)
    val nowHour = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
    var selected by remember { mutableIntStateOf(nowHour) }
    SectionHeader(GameIcon.WALK, "Steps", Mint, "Goal ${"%,d".format(Health.STEP_GOAL)}")
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("%,d".format(steps), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(" today", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
    // Progress toward the day's goal.
    val pct = (steps.toFloat() / Health.STEP_GOAL).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(pct).height(6.dp).clip(CircleShape).background(Mint))
    }
    Spacer(Modifier.height(10.dp))
    val max = (hours.maxOrNull() ?: 0).coerceAtLeast(100)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gold = Gold
    val mint = Mint
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = "Steps by hour" }
            .pointerInput(Unit) {
                detectTapGestures { o -> selected = (o.x / size.width * 24).toInt().coerceIn(0, 23) }
            },
    ) {
        val slot = size.width / 24
        val barW = slot * 0.62f
        for (h in 0 until 24) {
            val x = h * slot + (slot - barW) / 2
            // Faint track for every hour, then the hour's steps on top.
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
            val v = hours[h]
            if (v > 0) {
                val bh = (v.toFloat() / max * size.height).coerceAtLeast(barW)
                val c = when (h) {
                    selected -> gold
                    else -> if (h > nowHour) mint.copy(alpha = 0.35f) else mint
                }
                drawRoundRect(c, Offset(x, size.height - bh), Size(barW, bh), CornerRadius(barW / 2))
            } else if (h == selected) {
                drawRoundRect(gold.copy(alpha = 0.5f), Offset(x, size.height - barW), Size(barW, barW), CornerRadius(barW / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        listOf(0, 6, 12, 18).forEach { h ->
            Text(Health.hourLabel(h), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
    val busiest = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    Text(
        "${Health.hourLabel(selected)}–${Health.hourLabel((selected + 1) % 24)}: ${"%,d".format(hours[selected])} steps" +
            (busiest?.let { "  •  Busiest: ${Health.hourLabel(it)}" } ?: ""),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        if (fromHealth != null) "From Health Connect" else "Counted by the game while it gets step updates",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Glasses of water today, against a daily goal. Just for keeping track. */
@Composable
private fun WaterSection(vm: GameViewModel, glasses: Int) {
    val goal = vm.state.waterGoal
    val imperial = vm.state.settings.imperialUnits
    SectionHeader(GameIcon.DROP, "Water", WaterBlue, "1 glass = ${if (imperial) "${Health.GLASS_OZ} oz" else "${Health.GLASS_ML} ml"}")
    // Today's glasses, or the history of past days.
    var tab by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Today", "History").forEachIndexed { i, label ->
            val on = tab == i
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) WaterBlue.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (on) WaterBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { tab = i }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) WaterBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (tab == 1) {
        Spacer(Modifier.height(8.dp))
        WaterHistory(vm)
        return
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { vm.addWater(-1) }, enabled = glasses > 0, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$glasses of $goal glasses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                Health.waterAmount(glasses, imperial) + if (glasses >= goal) "  •  Goal reached!" else "",
                fontSize = 12.sp,
                color = if (glasses >= goal) WaterBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { vm.addWater(1) }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    // A drop for every glass of the goal (and any extra), filled as you drink.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        repeat(maxOf(goal, glasses).coerceAtMost(16)) { i ->
            GIcon(
                GameIcon.DROP,
                Modifier.size(16.dp),
                if (i < glasses) WaterBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Daily goal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.setWaterGoal(goal - 1) }, enabled = goal > Health.WATER_GOALS.first) { Text("−") }
        Text("$goal glasses", fontSize = 13.sp)
        TextButton(onClick = { vm.setWaterGoal(goal + 1) }, enabled = goal < Health.WATER_GOALS.last) { Text("+") }
    }
    // Two-way sync with Health Connect (Samsung Health, Fitbit, Google Fit...).
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshWater() }
    LaunchedEffect(Unit) { vm.refreshWater() }
    when (vm.waterAllowed) {
        true -> Text(
            "Synced with Health Connect: water from other apps counts here, and glasses you add here go there.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        false -> SmallButton(
            "Sync water with Health Connect",
            { permission.launch(com.monsterquest.app.data.HealthSteps.waterPermissions) },
            Modifier.fillMaxWidth().padding(top = 4.dp),
            outlined = true,
        )
        null -> {}
    }
}

/**
 * Water history: the streak of days that reached their goal, the best
 * streak, and every day with its glasses against the goal it had then.
 */
@Composable
fun WaterHistory(vm: GameViewModel) {
    val s = vm.state
    val imperial = s.settings.imperialUnits
    val today = LocalDate.now(ZoneId.systemDefault())
    val todayDay = today.toEpochDay()
    var shown by remember { mutableIntStateOf(14) }
    val month = Health.waterHistory(s, todayDay, 30)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        WaterStat("${Health.waterStreak(s, todayDay)}", "day streak")
        WaterStat("${Health.bestWaterStreak(s)}", "best streak")
        WaterStat("${month.count { it.met }}/30", "last 30 days")
    }
    Spacer(Modifier.height(8.dp))
    Health.waterHistory(s, todayDay, shown).forEach { d ->
        val date = LocalDate.ofEpochDay(d.day)
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (d.day) {
                    todayDay -> "Today"
                    todayDay - 1 -> "Yesterday"
                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
                },
                fontSize = 13.sp,
                modifier = Modifier.width(96.dp),
            )
            // How close to the goal, as a bar.
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(
                    Modifier.fillMaxWidth((d.glasses.toFloat() / d.goal).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape)
                        .background(if (d.met) WaterBlue else WaterBlue.copy(alpha = 0.45f)),
                )
            }
            Text(
                "${d.glasses}/${d.goal}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp).padding(start = 6.dp),
            )
            if (d.met) GIcon(GameIcon.CHECK, Modifier.size(16.dp), WaterBlue) else Spacer(Modifier.width(16.dp))
        }
    }
    val total = month.sumOf { it.glasses }
    Text(
        "Last 30 days: ${Health.waterAmount(total, imperial)} in all. Each day counts against the goal it had then.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (shown < 90) {
        TextButton(onClick = { shown = if (shown < 30) 30 else 90 }) { Text("Show more days") }
    }
}

@Composable
private fun WaterStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Last night's sleep and the week, from Health Connect or logged by hand.
 * Stages (deep, light, REM, awake) show when a watch or ring recorded them.
 */
@Composable
private fun SleepSection(vm: GameViewModel, today: LocalDate) {
    val s = vm.state
    val night = com.monsterquest.core.SleepLog.lastNight(s, today)
    var logging by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshSleep() }
    LaunchedEffect(Unit) { vm.refreshSleep() }

    SectionHeader(GameIcon.SLEEP, "Sleep", SleepIndigo, "Goal ${com.monsterquest.core.SleepLog.GOAL_MIN / 60}h")
    Spacer(Modifier.height(6.dp))
    if (night == null) {
        Text("No sleep tracked for last night yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.monsterquest.core.SleepLog.label(night.asleepMin), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(" last night", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("${clock(night.startMs)} → ${clock(night.endMs)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        if (night.hasStages) {
            // The night by stage: deep, REM, light and awake.
            val parts = listOf(
                Triple("Deep", night.deepMin, SleepIndigo),
                Triple("REM", night.remMin, SleepIndigo.copy(alpha = 0.75f)),
                Triple("Light", night.lightMin, SleepIndigo.copy(alpha = 0.5f)),
                Triple("Awake", night.awakeMin, HpYellow.copy(alpha = 0.7f)),
            ).filter { it.second > 0 }
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                parts.forEach { (_, m, c) -> Box(Modifier.weight(m.toFloat()).height(12.dp).background(c)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEach { (label, m, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                        Text(" $label ${com.monsterquest.core.SleepLog.label(m).replace("0h ", "")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val pct = (night.asleepMin.toFloat() / com.monsterquest.core.SleepLog.GOAL_MIN).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(pct).height(6.dp).clip(CircleShape).background(SleepIndigo))
            }
        }
    }
    // The week at a glance.
    val week = com.monsterquest.core.SleepLog.week(s, today)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        week.forEach { (_, n) ->
            val f = n?.let { (it.asleepMin.toFloat() / (10 * 60)).coerceIn(0.08f, 1f) } ?: 0.08f
            Box(
                Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(4.dp))
                    .background(if (n != null) SleepIndigo else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { (d, _) ->
            Text(
                d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    com.monsterquest.core.SleepLog.weekAverage(s, today)?.let {
        Text("This week: ${com.monsterquest.core.SleepLog.label(it)} a night on average", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Text(
        when {
            night != null && !night.manual -> "From Health Connect"
            night != null -> "Logged by hand"
            vm.sleepAllowed == null -> "Log your sleep by hand, or install Health Connect to sync it."
            else -> "Sync it from Health Connect (Samsung Health, Fitbit, Google Fit...) or log it by hand."
        },
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.sleepAllowed == false) {
            SmallButton("Sync sleep", { permission.launch(com.monsterquest.app.data.HealthSteps.sleepPermissions) }, Modifier.weight(1f))
        }
        SmallButton("Log sleep", { logging = true }, Modifier.weight(1f), outlined = true)
    }
    if (logging) LogSleepDialog(onDismiss = { logging = false }) { start, end -> vm.logSleep(start, end); logging = false }
}

private fun clock(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return "${if (t.hour % 12 == 0) 12 else t.hour % 12}:${"%02d".format(t.minute)} ${if (t.hour < 12) "AM" else "PM"}"
}

private fun clockOfMinutes(m: Int) = "${if ((m / 60) % 12 == 0) 12 else (m / 60) % 12}:${"%02d".format(m % 60)} ${if (m / 60 < 12) "AM" else "PM"}"

/** A time of day with − and + buttons in 15-minute steps. */
@Composable
private fun TimeStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 15 + 1440) % 1440) }) { Text("−") }
        Text(clockOfMinutes(value), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = { onChange((value + 15) % 1440) }) { Text("+") }
    }
}

/** Log last night by hand: bedtime and wake-up time in 15-minute steps. */
@Composable
private fun LogSleepDialog(onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(7 * 60) }
    val asleep = ((wake - bed + 1440) % 1440).let { if (it == 0) 1440 else it }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log last night") },
        text = {
            Column {
                TimeStepper("Went to bed", bed) { bed = it }
                TimeStepper("Woke up", wake) { wake = it }
                Text("${com.monsterquest.core.SleepLog.label(asleep)} of sleep", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val end = today.atStartOfDay(zone).plusMinutes(wake.toLong())
                val start = end.minusMinutes(asleep.toLong())
                onSave(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
