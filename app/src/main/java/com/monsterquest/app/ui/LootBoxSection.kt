package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.LootBox
import com.monsterquest.core.MAX_LOOT_BOXES
import com.monsterquest.core.MAX_UNLOCKING_BOXES
import com.monsterquest.core.METERS_PER_MILE
import java.util.Locale

fun boxColor(r: BoxRarity): Color = when (r) {
    BoxRarity.COMMON -> Color(0xFFB0B0B0)
    BoxRarity.RARE -> MpBlue
    BoxRarity.EPIC -> XpPurple
    BoxRarity.LEGENDARY -> Gold
}

/** Loot boxes from phone-free sessions; each unlocks after walking a distance. */
@Composable
fun LootBoxSection(vm: GameViewModel) {
    val s = vm.state
    val context = LocalContext.current
    var pendingUnlock by remember { mutableStateOf<String?>(null) }
    val stepsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // With or without the step counter (GPS is the fallback), start unlocking.
        pendingUnlock?.let(vm::startUnlocking)
        pendingUnlock = null
    }
    val unlockingCount = s.lootBoxes.count { it.unlocking }

    SectionTitle("Loot boxes ${s.lootBoxes.size}/$MAX_LOOT_BOXES")
    Text(
        "Earned from phone-free sessions. Walk to unlock them; up to $MAX_UNLOCKING_BOXES can unlock at once.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (s.lootBoxes.any { it.unlocking } && !vm.stepSensorAvailable) {
        Text(
            "No step counter available: distance is tracked by GPS while the app is open.",
            fontSize = 12.sp,
            color = HpYellow,
        )
    }
    if (s.lootBoxes.isEmpty()) {
        Text(
            "No boxes yet. Win a phone-free session on the Quests tab.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    // Unlocking boxes first, then the rest by rarity.
    s.lootBoxes.sortedWith(compareBy<LootBox> { !it.unlocking }.thenByDescending { it.rarity.ordinal }).forEach { box ->
        LootBoxCard(
            box = box,
            canStartUnlocking = unlockingCount < MAX_UNLOCKING_BOXES,
            onUnlock = {
                val needsPermission = Build.VERSION.SDK_INT >= 29 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    pendingUnlock = box.id
                    stepsLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    vm.startUnlocking(box.id)
                }
            },
            onOpen = { vm.openBox(box.id) },
            onDiscard = { vm.discardBox(box.id) },
        )
    }
}

@Composable
private fun LootBoxCard(
    box: LootBox,
    canStartUnlocking: Boolean,
    onUnlock: () -> Unit,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
) {
    val r = box.rarity
    GameCard(Modifier.padding(vertical = 3.dp), border = if (box.ready) Gold else boxColor(r).copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(GameIcon.CHEST, Modifier.size(32.dp), r.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${r.level} • ${r.label} box", fontWeight = FontWeight.SemiBold, color = r.color())
                Text(r.contents, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val walked = box.walkedMeters / METERS_PER_MILE
                Text(
                    when {
                        box.ready -> "Ready to open!"
                        box.unlocking -> String.format(Locale.US, "Unlocking: %.2f / %s mi", walked, box.milesLabel)
                        else -> "Walk ${box.milesLabel} mile${if (box.miles == 1.0) "" else "s"} to open" + if (box.halfWalk) " (half walk)" else ""
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (box.unlocking || box.ready) {
            Spacer(Modifier.height(6.dp))
            Bar(box.progress.toFloat(), if (box.ready) Gold else HpGreen, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                box.ready -> SmallButton("Open!", onOpen, Modifier.weight(1f))
                box.unlocking -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(GameIcon.WALK, Modifier.size(16.dp), HpGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Keep walking…", fontSize = 13.sp)
                }
                else -> SmallButton(
                    if (canStartUnlocking) "Start unlocking" else "2 already unlocking",
                    onUnlock,
                    Modifier.weight(1f),
                    enabled = canStartUnlocking,
                )
            }
            TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Eggs being walked: progress, and a Hatch button when one is ready. */
@androidx.compose.runtime.Composable
fun EggsSection(vm: com.monsterquest.app.GameViewModel) {
    val eggs = vm.state.eggs
    if (eggs.isEmpty()) return
    SectionTitle("Eggs ${eggs.size}/${com.monsterquest.core.Eggs.MAX_EGGS}")
    TipCard(vm, com.monsterquest.core.Tip.EGGS)
    val warm = com.monsterquest.core.Eggs.warming(vm.state)
    val incubators = vm.state.count(com.monsterquest.core.Item.INCUBATOR)
    if (eggs.size > com.monsterquest.core.Eggs.WARMING) {
        Text(
            "Walking warms ${com.monsterquest.core.Eggs.WARMING} eggs at a time. Hatch one, or tap Warm to swap a waiting egg in.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    eggs.forEach { egg ->
        // A ready egg glows (and its card is edged in gold) until it's hatched.
        GameCard(Modifier.padding(vertical = 3.dp), border = if (egg.ready) Gold else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).readyGlow(egg.ready), contentAlignment = Alignment.Center) {
                    com.monsterquest.app.ui.art.MonsterArt(egg.species, Modifier.size(40.dp), silhouette = !egg.ready, shiny = egg.ready && egg.shiny)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${egg.species.name} egg", fontWeight = FontWeight.SemiBold)
                    val warming = egg.id in warm
                    Text(
                        when {
                            egg.ready -> "Ready to hatch!"
                            !warming -> String.format(Locale.US, "Waiting (%.1f / %.1f km): walking warms the first ${com.monsterquest.core.Eggs.WARMING}", egg.walkedMeters / 1000, egg.meters / 1000)
                            else -> String.format(Locale.US, "Warming: %.1f / %.1f km", egg.walkedMeters / 1000, egg.meters / 1000)
                        } + if (egg.incubated) " • Incubated" else "",
                        fontSize = 12.sp,
                        color = if (egg.ready) Gold else if (warming) MaterialTheme.colorScheme.onSurfaceVariant else HpYellow,
                        fontWeight = if (egg.ready) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (!egg.ready) Bar(egg.progress.toFloat(), if (warming) Gold else MaterialTheme.colorScheme.outline, Modifier.fillMaxWidth().padding(top = 3.dp), heightDp = 5)
                }
                if (egg.ready) {
                    androidx.compose.material3.Button(onClick = { vm.hatchEgg(egg.id) }) { Text("Hatch") }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        if (egg.id !in warm) SmallButton("Warm", { vm.warmEggFirst(egg.id) }, outlined = true)
                        if (incubators > 0 && !egg.incubated) SmallButton("Incubate", { vm.incubateEgg(egg.id) }, outlined = true)
                    }
                }
            }
        }
    }
}
