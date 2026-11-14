package com.monsterquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.core.Dungeon
import com.monsterquest.core.DungeonRun
import com.monsterquest.core.Dungeons
import com.monsterquest.core.RoomKind

/** Stars for a dungeon's difficulty, e.g. "★★★☆☆". */
fun starsText(n: Int): String = "★".repeat(n) + "☆".repeat(5 - n)

private fun RoomKind.icon(): GameIcon = when (this) {
    RoomKind.MONSTERS -> GameIcon.PAW
    RoomKind.ELITE -> GameIcon.STAR
    RoomKind.COMBAT -> GameIcon.SKULL
    RoomKind.TRAP -> GameIcon.TARGET
    RoomKind.TREASURY -> GameIcon.CHEST
    RoomKind.FOUNTAIN -> GameIcon.DROP
    RoomKind.EMPTY -> GameIcon.MOON
    RoomKind.DEAD_END -> GameIcon.CLOSE
    RoomKind.BOSS -> GameIcon.FIGHT
    RoomKind.STARTER -> GameIcon.MONSTERS
}

private fun RoomKind.color(): Color = when (this) {
    RoomKind.MONSTERS -> Color(0xFF8FD18A)
    RoomKind.ELITE -> Color(0xFFE0574A)
    RoomKind.COMBAT -> Color(0xFFB57FE0)
    RoomKind.TRAP -> Color(0xFFF2A65A)
    RoomKind.TREASURY -> Color(0xFFEBC678)
    RoomKind.FOUNTAIN -> Color(0xFF7FD6C2)
    RoomKind.EMPTY -> Color(0xFF9AA3A1)
    RoomKind.DEAD_END -> Color(0xFF8C7A6B)
    RoomKind.BOSS -> Color(0xFFFF6B5E)
    RoomKind.STARTER -> Gold
}

/**
 * Inside a dungeon: the painted room, how far you are, your team's health,
 * what just happened, and two doors to choose from (the champion at the end).
 */
@Composable
fun DungeonScreen(vm: GameViewModel, run: DungeonRun) {
    val d = run.dungeon
    val s = vm.state
    // The tutorial's first room: choose the monster that joins you.
    if (d.tutorial && !s.starterChosen) {
        StarterScreen(onChoose = vm::tutorialStarter)
        return
    }
    var trapDoor by remember { mutableStateOf<Boolean?>(null) }
    // A hidden door picked: light a torch to look, or walk in blind.
    var unknownDoor by remember { mutableStateOf<Boolean?>(null) }
    val torches = Dungeons.torchesAtHand(s)
    var confirmLeave by remember { mutableStateOf(false) }
    // A room's fight was interrupted (the app closed): pick it back up.
    LaunchedEffect(run.fighting) { if (run.fighting != null) vm.startDungeonFight() }
    // Each new room fades in.
    val enter = remember(run.step) { Animatable(0f) }
    LaunchedEffect(run.step) { enter.animateTo(1f, tween(500)) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(Scene.of(d.theme), Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f + 0.65f * enter.value })
        // Your party walks into each new room along the ground.
        PartyWalk(vm, run.step, Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (d.tutorial) "Tutorial dungeon" else "${starsText(d.stars)}  •  Lv ${d.level}", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            // Progress: a dot per room, then the champion.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(d.rooms) { i ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (i < run.step) Gold else Color.White.copy(alpha = 0.3f)))
                }
                GIcon(GameIcon.FIGHT, Modifier.size(16.dp), if (run.atBoss) Color(0xFFFF6B5E) else Color.White.copy(alpha = 0.4f))
            }
            Text(
                if (run.atBoss) "The champion's chamber" else "Room ${run.step + 1} of ${d.rooms}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            // Your team's health.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val heroFrac = 1f - s.heroDamage.toFloat() / s.hero.maxHp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GIcon(GameIcon.HERO, Modifier.size(28.dp), Color.White)
                    Bar(heroFrac, hpColor(heroFrac), Modifier.width(36.dp), heightDp = 4)
                }
                s.partyMonsters.forEach { m ->
                    val frac = if (m.fainted) 0f else m.currentHp.toFloat() / m.stats.hp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MonsterArt(m.species, Modifier.size(28.dp), dim = m.fainted, shiny = m.shiny)
                        Bar(frac, hpColor(frac), Modifier.width(36.dp), heightDp = 4)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // What just happened.
            if (run.last.isNotEmpty()) {
                Text(
                    run.last,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp)
                        .graphicsLayer { alpha = enter.value },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (run.atBoss) {
                val champ = Dungeons.champion(d)
                Text(champ.title, color = Color(0xFFFF6B5E), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "Defeat the champion and their monsters to clear the dungeon. Reward: +1 ${d.bossStat.label} for good (once a day) and a roll on the rare reward pool.",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(onClick = { vm.openDoor(true) }, modifier = Modifier.fillMaxWidth()) { Text("Face the champion") }
            } else if (d.tutorial) {
                // The tutorial: one door at a time, with what each room is about.
                val room = Dungeons.doors(run).first
                TutorialGuide(room)
                Spacer(Modifier.height(8.dp))
                Door(room, false, Modifier.fillMaxWidth(0.6f)) { if (room == RoomKind.TRAP) trapDoor = true else vm.openDoor(true) }
            } else {
                // The deep half of a hard dungeon is pitch dark: a torch lights the way for a few rooms.
                val dark = Dungeons.dark(run)
                val lit = Dungeons.lit(run)
                when {
                    lit -> Text(
                        "Your torch burns: ${run.litUntil - run.step} room${if (run.litUntil - run.step == 1) "" else "s"} of light left",
                        color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    dark -> {
                        Text("It's pitch dark. You can't see a single door sign.", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Button(onClick = { vm.lightWay() }, enabled = torches > 0, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(if (torches > 0) "Light a torch (lights ${Dungeons.TORCH_ROOMS} rooms)" else "No torches to light")
                        }
                    }
                }
                TipCard(vm, com.monsterquest.core.Tip.DUNGEON)
                if (dark) TipCard(vm, com.monsterquest.core.Tip.DARK)
                Text("Choose a door", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val (left, right) = Dungeons.doors(run)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(true to left, false to right).forEach { (isLeft, room) ->
                        val hidden = Dungeons.hidden(run, isLeft)
                        Door(
                            room, hidden, Modifier.weight(1f),
                        ) {
                            when {
                                hidden -> unknownDoor = isLeft
                                room == RoomKind.TRAP -> trapDoor = isLeft
                                else -> vm.openDoor(isLeft)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!d.tutorial) {
                IconText(GameIcon.FLAME, "$torches torch${if (torches == 1) "" else "es"} to light", tint = Gold, textColor = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Found so far: ${run.gold} gold" + (if (run.torches > 0) ", ${run.torches} torches" else "") +
                    (if (run.items.isNotEmpty()) ", ${run.items.values.sum()} items" else "") +
                    (if (run.pages.isNotEmpty()) ", ${run.pages.values.sum()} pages" else "") +
                    (if (run.gear.isNotEmpty() || run.armor.isNotEmpty()) ", gear" else ""),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (!d.tutorial) TextButton(onClick = { confirmLeave = true }) { Text("Leave the dungeon", color = Color.White) }
        }
    }

    trapDoor?.let { isLeft ->
        val chance = (Dungeons.trapChance(s, run, isLeft) * 100).toInt()
        val kind = Dungeons.trapAt(run, isLeft)
        val harm = (Dungeons.trapDamagePct(d, kind) * 100).toInt()
        val (l, r) = Dungeons.doors(run)
        val other = if (isLeft) r else l
        val otherHidden = Dungeons.hidden(run, !isLeft)
        AlertDialog(
            onDismissRequest = { trapDoor = null },
            title = { Text(if (d.tutorial) "A trap!" else "A trap: ${kind.label.lowercase()}!") },
            text = {
                Text(
                    if (d.tutorial) "The floor is laced with traps. Here you'll get through safely; in real dungeons there's a risk, and a torch lights the way past."
                    else "You have a $chance% chance to get through unhurt. If it goes wrong, everyone loses about $harm% of their health, and a monster with little left is knocked out. " +
                        "A torch shows every plate and wire: light one to pass safely." +
                        if (other != RoomKind.TRAP) " Or take the other door (${if (otherHidden) "unknown" else other.label.lowercase()})." else "",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (!d.tutorial) {
                        Button(onClick = { trapDoor = null; vm.torchPastTrap(isLeft) }, enabled = torches > 0) {
                            Text(if (torches > 0) "Light a torch (you have $torches)" else "No torches to light")
                        }
                    }
                    TextButton(onClick = { trapDoor = null; vm.springTrap(isLeft) }) { Text(if (d.tutorial) "Go on" else "Risk it ($chance%)") }
                }
            },
            dismissButton = {
                if (other != RoomKind.TRAP || otherHidden) TextButton(onClick = {
                    trapDoor = null
                    if (otherHidden) unknownDoor = !isLeft else vm.openDoor(!isLeft)
                }) { Text("Take the other door") }
                else TextButton(onClick = { trapDoor = null }) { Text("Not yet") }
            },
        )
    }
    // A dead end: light a torch to find the way on, or turn back (a room lost).
    if (run.deadEnd != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("A dead end!") },
            text = {
                Text(
                    "The passage stops at a blank wall. Light a torch to search for a hidden way on, or turn back: " +
                        "you'll lose a room, and the halls behind you will have shifted to new doors.",
                )
            },
            confirmButton = {
                Button(onClick = { vm.deadEnd(useTorch = true) }, enabled = torches > 0) {
                    Text(if (torches > 0) "Light a torch ($torches)" else "No torches to light")
                }
            },
            dismissButton = { TextButton(onClick = { vm.deadEnd(useTorch = false) }) { Text("Turn back") } },
        )
    }
    // A dark vault: a torch shows the rich chest; in the dark you find the plain one.
    if (run.vault != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("A dark vault") },
            text = {
                Text(
                    "Something glints in the dark, but you can't see what. Light a torch to find the rich chest " +
                        "(twice the gold, more potions and crystals, better gear), or feel your way to the plain one.",
                )
            },
            confirmButton = {
                Button(onClick = { vm.openVault(useTorch = true) }, enabled = torches > 0) {
                    Text(if (torches > 0) "Light a torch ($torches)" else "No torches to light")
                }
            },
            dismissButton = { TextButton(onClick = { vm.openVault(useTorch = false) }) { Text("Feel in the dark") } },
        )
    }
    unknownDoor?.let { isLeft ->
        AlertDialog(
            onDismissRequest = { unknownDoor = null },
            title = { Text("An unknown door") },
            text = {
                Text(
                    "It's too dark to see what's behind it. Light a torch to look first, or walk in blind: " +
                        "if it's a trap, it springs before you can react.",
                )
            },
            confirmButton = {
                Button(onClick = { unknownDoor = null; vm.scoutDoor(isLeft) }, enabled = torches > 0) {
                    Text(if (torches > 0) "Light a torch to look ($torches)" else "No torches to light")
                }
            },
            dismissButton = { TextButton(onClick = { unknownDoor = null; vm.openDoorBlind(isLeft) }) { Text("Walk in blind") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the ${d.name}?") },
            text = { Text("You keep everything you've found. The torches you spent to enter are gone, and the champion waits for another day.") },
            confirmButton = { Button(onClick = { confirmLeave = false; vm.leaveDungeon() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

/** A door: its room's sign (or "?" when hidden). */
@Composable
private fun Door(room: RoomKind, hidden: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (hidden) Color(0xFFBFC7D1) else room.color()
    Column(
        modifier
            .height(170.dp)
            .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(Color(0xFF3A2A1E).copy(alpha = 0.92f))
            .border(3.dp, tint, RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hidden) {
            Text("?", fontSize = 44.sp, fontWeight = FontWeight.Black, color = tint)
            Text("Unknown", color = Color.White, fontWeight = FontWeight.SemiBold)
        } else {
            GIcon(room.icon(), Modifier.size(44.dp), tint)
            Spacer(Modifier.height(6.dp))
            Text(room.label, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(room.hint, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A dungeon's entrance on the map: what's inside, before you spend torches. */
@Composable
fun DungeonDialog(vm: GameViewModel, d: Dungeon, distance: Double?, onDismiss: () -> Unit) {
    val inRange = distance != null && distance <= Dungeons.RANGE_M
    val s = vm.state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(d.name)
                Text("${d.theme.label} • ${starsText(d.stars)}", color = Gold, fontSize = 14.sp)
                d.place?.let { Text("In $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Recommended level ${d.level} (you're ${com.monsterquest.core.Stars.label(s.hero.level)}) • ${d.rooms} rooms, then the champion • " +
                        "costs ${d.torchCost} torch${if (d.torchCost == 1) "" else "es"} (you have ${s.torches})",
                    fontSize = 13.sp,
                )
                Text("You may meet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Dungeons.residents(d).forEach { sp ->
                        val seen = sp.id in s.seen
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MonsterArt(sp, Modifier.size(44.dp), silhouette = !seen)
                            Text(if (seen) sp.name else "???", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text(
                    "Plus cursed guardians that can't be tamed, traps, treasure and fairy fountains.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val champ = Dungeons.champion(d)
                Text("Champion: ${champ.title} (Lv ${champ.level} ${d.bossClass.label})", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Reward pool", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Dungeons.bossRewards(d).forEach { r ->
                    val (icon, tint) = r.look()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.monsterquest.app.ui.art.GIcon(icon, Modifier.size(18.dp), tint)
                        Spacer(Modifier.width(8.dp))
                        Text(r.label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${r.pct}%", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                    }
                }
                BagNote(s)
                if (!inRange) {
                    Text(
                        "Walk within ${formatDistance(Dungeons.RANGE_M, s.settings.imperialUnits)} to enter (${distance?.let { formatDistance(it, s.settings.imperialUnits) } ?: "?"} away).",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.enterDungeon(d); onDismiss() }, enabled = inRange && s.torches >= d.torchCost) { Text("Enter (${d.torchCost} torch${if (d.torchCost == 1) "" else "es"})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** The tutorial's guide: what this room is, and what to do. */
@Composable
private fun TutorialGuide(room: RoomKind) {
    val text = when (room) {
        RoomKind.TREASURY -> "Behind this door is a treasury. Chests hold gold, crystals, potions, spell pages and sometimes torches. Open it: you'll need what's inside."
        RoomKind.MONSTERS -> "A monster lair. Wild monsters live in dungeons and dens. Weaken it, then press Tame, pick a Binding Crystal and trace the glyph in one stroke. (Here, every cast works.)"
        RoomKind.TRAP -> "A trap room. In real dungeons you'll see your odds first: risk it, or light a torch to pass safely. If it goes wrong, everyone takes damage and a weak monster can be knocked out. This one is safe."
        RoomKind.COMBAT -> "A guarded hall. Cursed guardians defend dungeons. They can't be tamed: fight your way through together."
        RoomKind.FOUNTAIN -> "A fairy fountain. It restores you and your monsters to full strength. Rest up: the champion is next."
        else -> "Each room in a dungeon is a choice of two doors. Their signs tell you what's behind them (some are hidden)."
    }
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.25f)).padding(12.dp),
    )
}

/** A new player's first screens: the game in four short pages, then into the tutorial dungeon. */
@Composable
fun TutorialIntro(onStart: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        Triple(GameIcon.QUESTS, "Your life is the quest", "Your tasks and calendar are your quests. Finishing them earns XP and gold for your hero."),
        Triple(GameIcon.WALK, "Walk to light torches", "Every 1,000 steps lights a torch, and a few wait for you each morning. Torches open dungeons. Walking also heals your team."),
        Triple(GameIcon.MOON, "Rest well", "8 hours of sleep makes your team stronger for the day; under 7 makes it weaker. Time off your phone earns loot boxes."),
        Triple(GameIcon.MAP, "Explore and tame", "Monsters live in dens and dungeons on the map. Fight, tame and raise them, and beat dungeon champions to grow stronger for good."),
    )
    val (icon, title, body) = pages[page]
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(Scene.DUSK, Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f })
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("SIDE QUEST", fontSize = 26.sp, color = Gold, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            GIcon(icon, Modifier.size(72.dp), Gold)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i == page) Gold else Color.White.copy(alpha = 0.3f))) }
            }
            Spacer(Modifier.height(24.dp))
            if (page < pages.lastIndex) {
                Button(onClick = { page++ }) { Text("Next") }
            } else {
                Button(onClick = onStart) { Text("Enter your first dungeon") }
            }
        }
    }
}

/**
 * The party walking along the dungeon floor: on every new room they stride in
 * from the left (the hero leading, monsters following, each bobbing with its
 * steps and kicking up dust), then stand and breathe until the next door.
 */
@Composable
private fun PartyWalk(vm: GameViewModel, step: Int, modifier: Modifier) {
    val s = vm.state
    val outfit = com.monsterquest.app.ui.art.HeroOutfit.of(s.equippedGear, s.equippedArmor, s.equippedPieces)
    val walk = remember(step) { Animatable(0f) }
    LaunchedEffect(step) { walk.animateTo(1f, tween(1700, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "party")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(tween(560, easing = androidx.compose.animation.core.LinearEasing)),
        label = "stride",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().fillMaxSize()) {
        val walking = walk.value < 1f
        val members = listOf<Pair<com.monsterquest.core.Monster?, Int>>(null to 76) + s.partyMonsters.map { it to 58 }
        // Feet on the painted ground (about 86% down the screen).
        val feetY = maxHeight * 0.87f
        val startX = -maxWidth * 0.7f
        val x = startX + (maxWidth * 0.7f) * walk.value
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = x.toPx() }
                .padding(top = feetY - 80.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Followers first (left), the hero leading on the right.
            members.reversed().forEachIndexed { i, (m, dimDp) ->
                val down = m?.fainted == true
                val stride = phase + i * 1.3f
                val bob = when {
                    down -> 0f
                    walking -> kotlin.math.abs(kotlin.math.sin(stride)) * 7f
                    else -> (kotlin.math.sin(phase * 0.5f + i) + 1f) * 1.2f
                }
                Box(
                    Modifier.padding(horizontal = 2.dp).graphicsLayer {
                        translationY = -bob * density
                        rotationZ = if (walking && !down) kotlin.math.sin(stride) * 4f else 0f
                        // A little squash on each footfall.
                        scaleY = if (walking && !down) 1f - (1f - kotlin.math.abs(kotlin.math.sin(stride))) * 0.05f else 1f
                    },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (walking && !down) {
                        // Dust kicked up behind each step.
                        val puff = (kotlin.math.sin(stride) + 1f) / 2f
                        Box(
                            Modifier
                                .size((10 + 8 * puff).dp)
                                .graphicsLayer { translationX = -dimDp * 0.45f * density; alpha = 0.35f * (1f - puff) }
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFD9C8A8)),
                        )
                    }
                    if (m == null) {
                        com.monsterquest.app.ui.art.HeroArt(s.hero.heroClass, Modifier.size(dimDp.dp), portrait = false, outfit = outfit)
                    } else {
                        MonsterArt(m.species, Modifier.size(dimDp.dp), dim = down, shiny = m.shiny)
                    }
                }
            }
        }
    }
}

/**
 * Before a dungeon or an adventure: everything found comes home if the bag
 * has room now (even past its size); with a full bag, nothing found can be kept.
 */
@Composable
internal fun BagNote(s: com.monsterquest.core.GameState) {
    if (s.bagFull) {
        Text(
            "Your bag is full (${s.bagCount}/${s.bagCapacity}). Make room first, or you won't be able to keep any items you find.",
            fontSize = 12.sp, color = HpRed, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        Text(
            "Bag ${s.bagCount}/${s.bagCapacity}: everything you find comes home, even past your bag's size.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
