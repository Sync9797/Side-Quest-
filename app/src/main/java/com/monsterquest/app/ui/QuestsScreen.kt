package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.IconText
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Difficulty
import com.monsterquest.core.QuestTask
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.delay
import com.monsterquest.core.Priority
import com.monsterquest.core.Planner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import com.monsterquest.core.TaskSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QuestsScreen(vm: GameViewModel) {
    val context = LocalContext.current
    val state = vm.state
    var addTask by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuestTask?>(null) }
    // Tick so "due in" times and the within-the-hour highlight stay current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var showDone by remember { mutableStateOf(false) }

    // Events from the phone's calendar become quests automatically.
    var hasCalendar by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        if (granted) vm.syncCalendar() else vm.toast("Without calendar access you can still add tasks by hand.")
    }
    LaunchedEffect(hasCalendar) {
        if (hasCalendar && state.settings.calendarAutoSync) vm.syncCalendar(quiet = true)
    }

    // Sorting only reruns when the tasks or the clock change, not on every step or map update.
    val tasks = state.tasks
    val pending = remember(tasks) { tasks.filter { !it.completed } }
    val sections = remember(tasks, now) { Planner.sections(tasks, now) }
    val dueSoon = remember(pending, now) { pending.filter { Planner.isDueSoon(it, now) } }
    val done = remember(tasks) { tasks.filter { it.completed }.sortedByDescending { it.completedAtMs } }

    // Tapping a day on the calendar shows just that day's quests; tapping it again goes back.
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val twoPane = useTwoPane()

    val overview: @Composable () -> Unit = {
        Column {
            // Health (steps, water, and soon sleep): folds to one line so it stays out of the way.
            HealthCard(vm, now)
            if (com.monsterquest.core.Tutorial.active(vm.state)) FirstStepsCard(vm)
            GoalsCard(vm)
            MysteryCard(vm)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A task has no due date. Anything with a date goes on the calendar (in the calendar app).
                SmallButton("+ Task", { addTask = true }, Modifier.weight(1f))
                SmallButton(
                    "Calendar",
                    { if (!openCalendar(context)) vm.toast("No calendar app found.") },
                    Modifier.weight(1f),
                    outlined = true,
                )
                // Pull the calendar in again now, in case the game has fallen behind.
                SmallButton(
                    if (vm.syncingCalendar) "Syncing…" else "Sync",
                    {
                        if (hasCalendar) vm.syncCalendar() else calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    Modifier.weight(1f),
                    enabled = !vm.syncingCalendar,
                    outlined = true,
                )
            }
            // Today's quest rewards: full ones left, then half ones.
            val limits = com.monsterquest.core.QuestRewards.status(state, now)
            Text(
                when {
                    limits.fullLeft > 0 -> "Full rewards: ${limits.fullLeft} of ${com.monsterquest.core.QuestRewards.FULL_PER_DAY} left today" +
                        " • Hard: ${limits.hardLeft} left"
                    limits.halfLeft > 0 -> "Half rewards: ${limits.halfLeft} left today"
                    else -> "Today's quest rewards are used up. Quests still count for your streak."
                },
                fontSize = 12.sp,
                color = if (limits.fullLeft > 0) MaterialTheme.colorScheme.onSurfaceVariant else HpYellow,
                modifier = Modifier.padding(top = 6.dp),
            )
            // Anything due within the hour is the first thing you see.
            if (dueSoon.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DueSoonBanner(dueSoon, now)
            }
            if (!hasCalendar) {
                Spacer(Modifier.height(10.dp))
                GameCard(border = MpBlue) {
                    IconText(GameIcon.CALENDAR, "Sync your calendar", tint = MpBlue, bold = true)
                    Text(
                        "Events from your phone's calendar become quests automatically.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    SmallButton("Connect calendar", { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth())
                }
            } else if (vm.syncingCalendar) {
                Text("Syncing calendar…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            // This week's goal first, then the calendar.
            WeeklyCard(vm)
            Spacer(Modifier.height(10.dp))
            DashboardCard(vm, selectedDay = selectedDay) { day ->
                selectedDay = if (selectedDay == day) null else day
                // On a phone the list is below the calendar: bring the day's quests into view.
                if (!twoPane && selectedDay != null) scope.launch { listState.animateScrollToItem(2) }
            }
            Spacer(Modifier.height(10.dp))
            FocusCard(vm)
            Spacer(Modifier.height(10.dp))
        }
    }

    fun LazyListScope.taskList() {
        val day = selectedDay
        if (day != null) {
            val view = Planner.onDay(state.tasks, day)
            item(key = "day_header") { DayHeader(day, view.due.size + view.completed.size) { selectedDay = null } }
            if (view.due.isEmpty() && view.completed.isEmpty()) {
                item(key = "day_empty") {
                    Text("Nothing on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(view.due, key = { it.id }, contentType = { "quest" }) { task ->
                QuestRow(task, now, onClick = { editing = task }, onComplete = { vm.completeTask(task.id) }, onDelete = { vm.deleteTask(task.id) })
            }
            if (view.completed.isNotEmpty()) {
                item(key = "day_done") { SectionTitle("Completed (${view.completed.size})") }
                items(view.completed, key = { "dd_" + it.id }, contentType = { "done" }) { DoneLine(it) }
            }
            return
        }
        // Scheduled: everything with a due date (or a calendar time), grouped by when.
        val scheduled = sections.filter { it.group != Planner.Group.NO_DATE }
        val tasks = sections.firstOrNull { it.group == Planner.Group.NO_DATE }?.tasks.orEmpty()
        if (pending.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No quests yet. Add a task, or add events in your calendar and sync so they become quests.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        // Daily reminders (gentle alarms), then tasks, then the calendar.
        item(key = "reminders") { RemindersSection(vm, now) }
        // Tasks first: things without a due date (only shown when there are some), then the calendar.
        if (tasks.isNotEmpty()) {
            item(key = "tasks_header") {
                Column(Modifier.padding(top = 4.dp)) {
                    SectionTitle("Tasks (${tasks.size})")
                    Text("No due date: get to them whenever you can.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(tasks, key = { it.id }, contentType = { "quest" }) { task ->
                QuestRow(task, now, onClick = { editing = task }, onComplete = { vm.completeTask(task.id) }, onDelete = { vm.deleteTask(task.id) })
            }
        }
        if (scheduled.isNotEmpty()) {
            item(key = "active_header") { SectionTitle("Scheduled (${scheduled.sumOf { it.tasks.size }})") }
        }
        scheduled.forEach { sec ->
            item(key = "sec_${sec.group}") {
                // Missed calendar events can all be cleared at once (your own to-dos stay).
                val missed = if (sec.group == Planner.Group.OVERDUE) sec.tasks.count { it.source == TaskSource.CALENDAR } else 0
                GroupHeader(sec.group, sec.tasks.size, clearMissed = missed, onClearMissed = { vm.closeMissed() })
            }
            items(sec.tasks, key = { it.id }, contentType = { "quest" }) { task ->
                QuestRow(task, now, onClick = { editing = task }, onComplete = { vm.completeTask(task.id) }, onDelete = { vm.deleteTask(task.id) })
            }
        }
        item(key = "done_header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Completed (${done.size})")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide" else "Show") }
            }
        }
        if (showDone) items(done.take(50), key = { "done_" + it.id }, contentType = { "done" }) { DoneLine(it) }
        item(key = "mysteries") { MysteriesFold(vm) }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }

    // Phones: one scrolling column. Unfolded and tablets: calendar and dashboard on
    // the left, the quest list on the right (split at the hinge on a book-style fold).
    AdaptivePanes(
        firstWeight = 0.46f,
        compact = {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // A single screen (a closed phone) keeps just the welcome header.
                item(key = "welcome") { WelcomeHeader(vm, now) }
                item(key = "overview") { overview() }
                taskList()
            }
        },
        // Big screens: a welcome header over the left pane, the to-do and events header over the right.
        first = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp)) {
                WelcomeHeader(vm, now)
                overview()
            }
        },
        second = {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 16.dp)) {
                item(key = "agenda") { AgendaHeader(vm, now) { addTask = true } }
                taskList()
            }
        },
    )

    if (addTask) {
        QuestEditor(null, asTask = true, onDismiss = { addTask = false }) { t, d, _, p -> vm.addTask(t, d, null, p); addTask = false }
    }
    editing?.let { task ->
        QuestEditor(task, onDismiss = { editing = null }) { t, d, due, p -> vm.editTask(task.id, t, d, due, p); editing = null }
    }
}

private val timeFmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun whenText(start: Long): String {
    val today = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    val sameDay = today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val nextDay = tomorrow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        tomorrow.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(start))
    return when {
        sameDay -> "today $clock"
        nextDay -> "tomorrow $clock"
        else -> timeFmt.format(Date(start))
    }
}

private fun Priority.color(): Color = when (this) {
    Priority.HIGH -> HpRed
    Priority.NORMAL -> Gold
    Priority.LOW -> Color(0xFF7D8A87)
}

private fun groupColor(g: Planner.Group): Color? = when (g) {
    Planner.Group.OVERDUE -> HpRed
    Planner.Group.SOON, Planner.Group.HAPPENING -> HpYellow
    else -> null
}

@Composable
internal fun GroupHeader(g: Planner.Group, count: Int, clearMissed: Int = 0, onClearMissed: () -> Unit = {}) {
    val color = groupColor(g) ?: Gold
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (g == Planner.Group.SOON || g == Planner.Group.OVERDUE) {
            GIcon(GameIcon.HOURGLASS, Modifier.size(16.dp), color)
            Spacer(Modifier.width(6.dp))
        }
        Text(g.label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("  $count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (clearMissed > 0) {
            TextButton(onClick = { confirm = true }) { Text("Clear missed ($clearMissed)", fontSize = 13.sp) }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear missed events?") },
            text = {
                Text(
                    "$clearMissed calendar event${if (clearMissed == 1) "" else "s"} that already ended will leave your quests: no reward, no penalty. " +
                        "They stay in your phone's calendar. Your own to-dos stay on the list.",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onClearMissed() }) { Text("Clear them") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep them") } },
        )
    }
}

/** The heading of a single day's view, with the way back to every quest. */
@Composable
private fun DayHeader(epochDay: Long, count: Int, onShowAll: () -> Unit) {
    val date = java.time.LocalDate.ofEpochDay(epochDay)
    val today = java.time.LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(GameIcon.CALENDAR, Modifier.size(18.dp), Gold)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(if (count == 1) "1 quest" else "$count quests", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmallButton("Show all", onShowAll, outlined = true)
    }
}

@Composable
private fun DoneLine(task: QuestTask) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(GameIcon.CHECK, Modifier.size(14.dp), Mint)
        Spacer(Modifier.width(6.dp))
        Text(task.title, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Top-of-list reminder for anything due within the hour. */
@Composable
private fun DueSoonBanner(tasks: List<QuestTask>, now: Long) {
    GameCard(border = HpYellow) {
        IconText(
            GameIcon.HOURGLASS,
            if (tasks.size == 1) "1 quest due within the hour" else "${tasks.size} quests due within the hour",
            tint = HpYellow, textColor = HpYellow, bold = true,
        )
        tasks.sortedBy { it.dueAt }.take(4).forEach { t ->
            Text("• ${t.title} (${Planner.relative(t.dueAt ?: now, now)})", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun dueText(task: QuestTask, now: Long): String? {
    val due = task.dueAt ?: return null
    val label = whenText(due)
    return when {
        Planner.isOverdue(task, now) -> "Overdue • was due $label"
        task.source == TaskSource.CALENDAR && due <= now -> "Happening now • ends ${task.endMs?.let { whenText(it) } ?: ""}".trim()
        else -> (if (task.source == TaskSource.CALENDAR) "Starts $label" else "Due $label") + " • " + Planner.relative(due, now)
    }
}

@Composable
internal fun QuestRow(task: QuestTask, now: Long, onClick: () -> Unit, onComplete: () -> Unit, onDelete: () -> Unit) {
    val soon = Planner.isDueSoon(task, now) || (task.source == TaskSource.CALENDAR && Planner.groupOf(task, now) == Planner.Group.HAPPENING)
    val overdue = Planner.isOverdue(task, now)
    // Due within the hour: an amber border that gently pulses. Overdue: red.
    val pulse = if (soon && !overdue) Modifier.pulsingBorder(HpYellow) else Modifier
    // Minutes until a new to-do pays (null: it pays now).
    val waitMin = if (task.source == TaskSource.MANUAL && !task.completed) {
        com.monsterquest.core.QuestRewards.tooSoon(task, now)?.let { (it + 59_999) / 60_000 }
    } else null
    var confirmEarly by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    if (confirmClose) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close this quest?") },
            text = {
                Text(
                    "\"${task.title}\" leaves your list with no reward and no penalty." +
                        if (task.source == TaskSource.CALENDAR) " It stays in your phone's calendar, but won't come back as a quest." else "",
                )
            },
            confirmButton = { TextButton(onClick = { confirmClose = false; onDelete() }) { Text("Close it") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep it") } },
        )
    }
    if (confirmEarly && waitMin != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEarly = false },
            title = { Text("No reward yet") },
            text = { Text("New to-dos pay ${com.monsterquest.core.QuestRewards.MIN_AGE_MS / 60_000} minutes after they're added. \"${task.title}\" pays in $waitMin min. Mark it done now without a reward?") },
            confirmButton = { TextButton(onClick = { confirmEarly = false; onComplete() }) { Text("Done, no reward") } },
            dismissButton = { TextButton(onClick = { confirmEarly = false }) { Text("Wait") } },
        )
    }
    GameCard(Modifier.padding(vertical = 4.dp).then(pulse).clickable(onClick = onClick), border = if (overdue) HpRed else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Priority stripe.
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(task.priority.color()))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority == Priority.HIGH) {
                        Text("!", color = HpRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                dueText(task, now)?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = if (soon || overdue) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            overdue -> HpRed
                            soon -> HpYellow
                            else -> MpBlue
                        },
                    )
                }
                val sub = buildList {
                    if (task.source == TaskSource.CALENDAR) add(task.calendarName ?: "Calendar")
                    if (task.priority != Priority.NORMAL) add("${task.priority.label} priority")
                    add(task.difficulty.label)
                    add("+${task.difficulty.xp} XP")
                    add("+${task.difficulty.gold} gold")
                }.joinToString(" • ")
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A brand-new to-do only pays out once it's been around a little while.
                if (waitMin != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        GIcon(GameIcon.HOURGLASS, Modifier.size(12.dp), HpYellow)
                        Spacer(Modifier.width(4.dp))
                        Text("Reward in $waitMin min", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HpYellow)
                    }
                }
            }
            // Close a quest you won't get done (asks first).
            if (!task.completed) {
                TextButton(onClick = { confirmClose = true }, contentPadding = PaddingValues(4.dp)) {
                    GIcon(GameIcon.CLOSE, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SmallButton("Done", { if (waitMin != null) confirmEarly = true else onComplete() })
        }
    }
}

/**
 * A border that fades in and out, for cards due within the hour. Only the
 * border redraws each frame (the card's contents don't recompose), and only
 * the cards that need it run the animation, so the list scrolls smoothly.
 */
@Composable
private fun Modifier.pulsingBorder(color: androidx.compose.ui.graphics.Color): Modifier {
    val alpha = rememberInfiniteTransition(label = "due").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "duePulse",
    )
    return drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        drawRoundRect(
            color.copy(alpha = alpha.value),
            topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
        )
    }
}

/** Quick due-date choices: in an hour, this evening, tomorrow morning. */
private fun quickDue(kind: Int, now: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    when (kind) {
        0 -> return now + 3_600_000L
        1 -> {
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.HOUR_OF_DAY, if (c.get(Calendar.HOUR_OF_DAY) < 17) 18 else 21)
        }
        else -> {
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 9)
            c.set(Calendar.MINUTE, 0)
        }
    }
    return c.timeInMillis
}

/** Add a to-do, or edit one: name, priority, difficulty and due date. Calendar quests can only change priority. */
@Composable
private fun QuestEditor(
    task: QuestTask?,
    /** A new task: no due date, so the date picker is hidden. */
    asTask: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, Difficulty, Long?, Priority) -> Unit,
) {
    val context = LocalContext.current
    val calendar = task?.source == TaskSource.CALENDAR
    var title by remember { mutableStateOf(task?.title ?: "") }
    var difficulty by remember { mutableStateOf(task?.difficulty ?: Difficulty.NORMAL) }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NORMAL) }
    val now = System.currentTimeMillis()
    // New to-dos start due this evening; tasks and edits keep what they have.
    var due by remember { mutableStateOf(if (task == null && !asTask) quickDue(1, now) else task?.dueMs) }
    fun pickDateTime() {
        val c = Calendar.getInstance().apply { timeInMillis = due ?: quickDue(1, now) }
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, hh, mm ->
                due = Calendar.getInstance().apply { set(y, m, d, hh, mm, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    task == null && asTask -> "New task"
                    task == null -> "New to-do"
                    calendar -> "Calendar quest"
                    task.dueAt == null -> "Edit task"
                    else -> "Edit to-do"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (calendar) {
                    Text(task!!.title, fontWeight = FontWeight.SemiBold)
                    task.dueAt?.let { Text("Starts ${whenText(it)}", fontSize = 13.sp, color = MpBlue) }
                    Text("Time and name come from your calendar; you can set its priority.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What do you need to do?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Priority", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Priority.entries.reversed().forEach { p ->
                        SmallButton(p.label, { priority = p }, Modifier.weight(1f), outlined = p != priority)
                    }
                }
                if (!calendar && !asTask) {
                    Text("Due", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Text(
                        due?.let { "${whenText(it)} (${Planner.relative(it, now)})" } ?: "No due date",
                        color = if (due != null) MpBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallButton("1 hour", { due = quickDue(0, now) }, Modifier.weight(1f), outlined = true)
                        SmallButton(if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 17) "6 PM" else "9 PM", { due = quickDue(1, now) }, Modifier.weight(1f), outlined = true)
                        SmallButton("Tmrw 9 AM", { due = quickDue(2, now) }, Modifier.weight(1f), outlined = true)
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallButton("Pick date & time", ::pickDateTime, Modifier.weight(2f), outlined = true)
                        SmallButton("None", { due = null }, Modifier.weight(1f), outlined = true)
                    }
                }
                if (asTask) {
                    Text(
                        "Tasks have no due date. Give one a date later by tapping it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!calendar) {
                    Text("Difficulty", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Difficulty.entries.forEach { d ->
                            SmallButton(d.label, { difficulty = d }, Modifier.weight(1f), outlined = d != difficulty)
                        }
                    }
                    Text(
                        "Reward: +${difficulty.xp} XP, +${difficulty.gold} gold, +1 hunt token",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, difficulty, due, priority) }, enabled = calendar || title.isNotBlank()) {
                Text(if (task == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opens the phone's default calendar app at today. Returns false if there isn't one. */
fun openCalendar(context: Context): Boolean {
    val today = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .also { ContentUris.appendId(it, System.currentTimeMillis()) }
        .build()
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, today),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            // Try the next way of opening a calendar.
        }
    }
    return false
}
