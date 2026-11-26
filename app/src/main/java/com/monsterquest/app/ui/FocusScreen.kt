package com.monsterquest.app.ui

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.FOCUS_DURATIONS
import com.monsterquest.core.FocusSession
import com.monsterquest.core.GameEngine
import com.monsterquest.core.LootBoxes
import com.monsterquest.core.MAX_LOOT_BOXES
import kotlinx.coroutines.delay

/** Card on the Quests tab for starting a phone-free session. */
@Composable
fun FocusCard(vm: GameViewModel) {
    var minutes by remember { mutableIntStateOf(30) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way; without notifications the session still works, it's just silent.
        vm.startFocus(minutes)
    }
    val open = vm.state.settings.focusOpen
    GameCard(border = MpBlue) {
        // Tap the title to fold the card to one line (remembered).
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.updateSettings { it.copy(focusOpen = !open) } }
                .testTag("focus-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIcon(GameIcon.PHONE_OFF, Modifier.size(20.dp), MpBlue)
            Spacer(Modifier.width(8.dp))
            Text("Phone-free challenge", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownReveal(open) {
        Column {
        Text(
            "Start a timer and put your phone down. The screen stays on with the timer; stay in the game until time's up to earn rewards.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Two rows of three so the chips fit on the narrowest screens.
        FOCUS_DURATIONS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    SmallButton("$m min", { minutes = m }, Modifier.weight(1f), outlined = m != minutes)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Win a loot box: " + LootBoxes.odds(minutes).entries.joinToString(" • ") { (r, pct) -> "Lv ${r.level} $pct%" },
            fontSize = 12.sp,
            color = HpGreen,
        )
        // Long sessions pay extra on top of the box.
        val bonus = GameEngine.focusBonus(minutes)
        val extras = buildList {
            if (minutes >= GameEngine.HALF_WALK_MINUTES) add("half the walk to open")
            if (bonus.tomePages > 0) add("a Scholar's Tome (${bonus.tomePages} pages for a spell you choose)")
            if (bonus.levelShare > 0) add("party training (+${(bonus.levelShare * 100).toInt()}% of a level)")
            if (bonus.energyRefill > 0) add(if (bonus.energyRefill >= 100) "full Energy" else "+${bonus.energyRefill}% Energy")
        }
        if (extras.isNotEmpty()) {
            Text("Plus: " + extras.joinToString(", ") + ".", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Boxes unlock by walking: Lv 1 = 1 mi, Lv 2 = 3 mi, Lv 3 = 5 mi, Lv 4 = 10 mi. Level 4 boxes (boss inside!) need 60+ minutes.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Leave the game early: −${GameEngine.focusPenalty(vm.state)} gold, −1 hunt token",
            fontSize = 12.sp,
            color = HpRed,
        )
        Spacer(Modifier.height(8.dp))
        val full = vm.state.lootBoxes.size >= MAX_LOOT_BOXES
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.startFocus(minutes)
            },
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (full) "Box storage full ($MAX_LOOT_BOXES). Open one in the Bag" else "Start $minutes-minute session") }
        }
        }
    }
}

/**
 * Shown while a session runs: the countdown, with the screen kept on so the
 * phone never locks. After a few seconds it dims to a dark, low-brightness
 * timer (tap to brighten). Leaving the game fails the session.
 */
@Composable
fun FocusOverlay(session: FocusSession, onGiveUp: () -> Unit, onDone: () -> Unit = {}, penaltyGold: Int? = null) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= session.endMs) onDone()
            delay(1_000)
        }
    }
    var confirmGiveUp by remember { mutableStateOf(false) }
    val dimmed = !confirmGiveUp && now - lastTouch > DIM_AFTER_MS
    // Back would leave the game and fail the session; just brighten the screen instead.
    BackHandler { lastTouch = System.currentTimeMillis() }
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    LaunchedEffect(dimmed) {
        activity?.window?.let {
            it.attributes = it.attributes.apply {
                screenBrightness = if (dimmed) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    val left = (session.endMs - now).coerceAtLeast(0)

    if (confirmGiveUp) {
        GiveUpDialog(
            left,
            penaltyGold,
            onKeepGoing = { confirmGiveUp = false; lastTouch = System.currentTimeMillis() },
            onGiveUp = { confirmGiveUp = false; onGiveUp() },
        )
    }
    Surface(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { lastTouch = System.currentTimeMillis() } },
        color = if (dimmed) Color.Black else MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val dim = Color(0xFF4A5557)
                GIcon(GameIcon.PHONE_OFF, Modifier.size(72.dp), if (dimmed) dim else MpBlue)
                Text("Phone-free session", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (dimmed) dim else Gold)
                Spacer(Modifier.height(12.dp))
                val h = left / 3_600_000
                val m = (left / 60_000) % 60
                val sec = (left / 1000) % 60
                Text(
                    if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = if (dimmed) Color(0xFF6B7779) else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (dimmed) "Tap to brighten" else "Put your phone down. The screen stays on and dims; leaving Side Quest fails the session (phone calls are fine).",
                    textAlign = TextAlign.Center,
                    color = if (dimmed) dim else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!dimmed) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(onClick = { confirmGiveUp = true }) { Text("Give up (−gold, −1 hunt token)") }
                }
            }
        }
    }
}

private const val DIM_AFTER_MS = 15_000L

/** No accidental quitting: giving up a session has to be confirmed. */
@Composable
private fun GiveUpDialog(left: Long, penaltyGold: Int?, onKeepGoing: () -> Unit, onGiveUp: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text("Give up this session?") },
        text = {
            Text(
                "Only ${formatDuration(left)} to go. Giving up costs " +
                    (penaltyGold?.let { "$it gold" } ?: "some gold") +
                    " and a hunt token, and you won't win the loot box.",
            )
        },
        confirmButton = { Button(onClick = onKeepGoing) { Text("Keep going") } },
        dismissButton = { TextButton(onClick = onGiveUp) { Text("Give up", color = HpRed) } },
    )
}
