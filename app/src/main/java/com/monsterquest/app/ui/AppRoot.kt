package com.monsterquest.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.ArtQuality
import com.monsterquest.app.ui.art.MonsterArt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.color
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel

enum class Tab(val label: String, val icon: GameIcon) {
    QUESTS("Quests", GameIcon.QUESTS),
    HERO("Hero", GameIcon.HERO),
    MONSTERS("Monsters", GameIcon.MONSTERS),
    MAP("Map", GameIcon.MAP),
    BAG("Bag", GameIcon.BAG),
    STORE("Store", GameIcon.STORE),
}

/** The bottom menu, left to right; the Hero button in the middle opens Hero and Monsters. */
private val leftTabs = listOf(Tab.QUESTS, Tab.MAP)
private val rightTabs = listOf(Tab.BAG, Tab.STORE)

@Composable
fun AppRoot(vm: GameViewModel, initialTab: Tab = Tab.QUESTS, initialSettings: Boolean = false) {
    // What you just won, with icons, on top of whatever screen you're on (a dungeon, a battle, the map...).
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        AppScreens(vm, initialTab, initialSettings)
        vm.lootPopup?.let { LootPopupCard(it) { vm.lootPopup = null } }
        vm.availableUpdate?.let { UpdateDialog(vm, it) }
    }
}

/** A newer version is out: what changed, and a button to download it. */
@Composable
private fun UpdateDialog(vm: GameViewModel, u: com.monsterquest.app.data.AvailableUpdate) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.dismissUpdate(skip = false) },
        title = { Text("Update available: ${u.version}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("You have ${vm.installedVersion}. Download the new version and open it to install; your save is kept.", fontSize = 13.sp)
                if (u.notes.isNotBlank()) {
                    Text("What's new", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text(u.notes.lines().take(14).joinToString("\n"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u.downloadUrl)))
                }
                vm.dismissUpdate(skip = false)
            }) { Text("Download") }
        },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(onClick = { vm.dismissUpdate(skip = true) }) { Text("Skip this version") }
                androidx.compose.material3.TextButton(onClick = { vm.dismissUpdate(skip = false) }) { Text("Later") }
            }
        },
    )
}

@Composable
private fun AppScreens(vm: GameViewModel, initialTab: Tab, initialSettings: Boolean) {
    val state = vm.state
    ArtQuality.reduced = state.settings.reducedEffects
    if (!state.starterChosen) {
        // A new player: the basics, then the tutorial dungeon, where the starter joins in the first room.
        val run = state.dungeonRun
        if (run != null) DungeonScreen(vm, run) else TutorialIntro(onStart = vm::startTutorial)
        return
    }
    val focus = state.focus
    if (focus != null) {
        FocusOverlay(focus, onGiveUp = vm::giveUpFocus, onDone = vm::onFocusTimerDone, penaltyGold = com.monsterquest.core.GameEngine.focusPenalty(state))
        return
    }
    // A one-on-one fight is about to start: who goes first? (A dialog, so it shows over any screen.)
    vm.leadChoice?.let { c -> LeadPicker(vm, c, HeroOutfit.of(state.equippedGear, state.equippedArmor, state.equippedPieces)) }
    val battle = vm.battle
    if (battle != null) {
        BattleScreen(battle, onAction = vm::battleAction, onClose = vm::closeBattle, onNext = vm::nextVillainMonster, outfit = HeroOutfit.of(state.equippedGear, state.equippedArmor, state.equippedPieces), onTarget = vm::selectTarget, onReplace = vm::replaceFainted, onLearn = vm::learnMove, speed = state.settings.battleSpeed.factor, scene = vm.scene)
        return
    }
    // Inside a dungeon: its rooms take over the screen until you leave.
    state.dungeonRun?.let { run ->
        DungeonScreen(vm, run)
        return
    }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettings) }
    // A widget asked for a screen: eggs are in the Bag, adventures under Monsters.
    androidx.compose.runtime.LaunchedEffect(vm.openTarget) {
        when (vm.openTarget) {
            "eggs" -> tab = Tab.BAG
            "adventures" -> { tab = Tab.MONSTERS; vm.monstersTabRequest = 2 }
            "quests" -> tab = Tab.QUESTS
            else -> return@LaunchedEffect
        }
        settingsOpen = false
        vm.openTarget = null
    }
    // Back steps out of Settings, then from any tab to Quests; only Back on Quests leaves the app.
    BackHandler(enabled = !settingsOpen && tab != Tab.QUESTS) { tab = Tab.QUESTS }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    var heroMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = heroMenu) { heroMenu = false }
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            vm.clearMessage()
            snackbar.showSnackbar(msg)
        }
    }

    vm.focusResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissFocusResult,
            title = { Text(if (vm.focusSucceeded) "Session complete!" else "Session failed") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissFocusResult) { Text("OK") } },
        )
    }

    // Monsters that levelled up training during a phone-free session (after its result is closed).
    if (vm.focusResult == null) {
        vm.trainingLevelUps.firstOrNull()?.let { up ->
            LevelUpDialog(up, null, 0, 1, onLearn = vm::learnMove) { vm.dismissTrainingLevelUp() }
        }
    }

    // A brand-new player: a short welcome, then the First steps checklist on Home.
    if (com.monsterquest.core.Tutorial.showIntro(state)) WelcomeDialog(state.hero.name) { vm.finishIntro() }

    vm.adventureResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissAdventureResult,
            title = { Text(if (vm.adventureSucceeded) "Adventure complete!" else "Adventure failed") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissAdventureResult) { Text("OK") } },
        )
    }

    vm.boxResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissBoxResult,
            title = { Text(if (vm.boxBoss != null) "A boss emerges!" else "Loot box opened!") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissBoxResult) { Text(if (vm.boxBoss != null) "Fight!" else "Awesome") } },
        )
    }


    if (state.villainSpoils.isNotEmpty()) SpoilsDialog(vm)
    // Yesterday's gains and last night's sleep, on the first open each morning.
    if (vm.showRecap) MorningRecap(vm)
    if (state.starterGiftPending && !vm.starterGiftLater) StarterGiftDialog(vm)
    state.pendingDenCatch?.let { DenCatchDialog(vm, it) }
    // A den cast: the crystal shakes before the result.
    vm.denSeal?.let { (roll, _) -> BindingSeal(roll, Gold, "The binding burst apart (no harm done). Try another cast!", vm::finishDenSeal) }

    // The menu stays at the bottom on every screen size, folded or open, so it's
    // always within thumb reach.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val item: @Composable RowScope.(Tab) -> Unit = { t ->
                    NavigationBarItem(
                        selected = tab == t && !settingsOpen,
                        onClick = { tab = t; settingsOpen = false; heroMenu = false },
                        icon = { GIcon(t.icon, Modifier.size(24.dp)) },
                        label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                leftTabs.forEach { item(it) }
                HeroMenuButton(
                    current = tab.takeIf { (it == Tab.HERO || it == Tab.MONSTERS) && !settingsOpen },
                    open = heroMenu,
                    onToggle = { heroMenu = !heroMenu },
                )
                rightTabs.forEach { item(it) }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ResourceHeader(
                    state.hero.level, state.gold, state.torches,
                    onSettings = { settingsOpen = !settingsOpen },
                    saverOn = vm.batterySaver,
                    onSaver = { vm.batterySaver = !vm.batterySaver },
                    xpFraction = state.hero.xp.toFloat() / com.monsterquest.core.heroXpForNext(state.hero.level).coerceAtLeast(1),
                    torchCap = com.monsterquest.core.Torches.cap(state),
                )
                if (settingsOpen) {
                    SettingsScreen(vm) { settingsOpen = false }
                } else when (tab) {
                    Tab.QUESTS -> QuestsScreen(vm)
                    Tab.HERO -> HeroScreen(vm)
                    Tab.MONSTERS -> MonstersScreen(vm)
                    Tab.MAP -> MapScreen(vm)
                    Tab.BAG -> BagScreen(vm)
                    Tab.STORE -> StoreScreen(vm)
                }
            }
        }
    }
    if (heroMenu) HeroMenuBubbles(onPick = { tab = it; settingsOpen = false; heroMenu = false }, onDismiss = { heroMenu = false })
    // Battery saver: black screen on top while the map keeps running underneath.
    if (vm.batterySaver) BatterySaverOverlay(vm)
    // Wild monsters that found you while walking in battery-saver mode (from any screen).
    vm.secretAmbush?.let { SecretAmbushPopup(vm, it) }
    if (vm.drivingWarning) DrivingWarning { vm.drivingWarning = false }
    }
}

/** Moving faster than 15 mph: monsters stop appearing, and a reminder to keep your eyes on the road. */
@Composable
private fun DrivingWarning(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Moving too fast") },
        text = {
            Text(
                "You're going faster than ${com.monsterquest.core.SpeedGuard.LIMIT_MPH.toInt()} mph, so monsters have stopped " +
                    "appearing.\n\nPlease don't play while driving. Keep your eyes on the road. If you're a passenger, enjoy " +
                    "the ride: monsters come back once you're on foot again.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("I'm not driving") } },
    )
}

/** A den monster is beaten: a few throws to tame it before it slips away. */
@Composable
private fun DenCatchDialog(vm: GameViewModel, c: com.monsterquest.core.DenCatch) {
    val m = c.monster
    val pct = (com.monsterquest.core.Dens.throwOdds(m, c.bait) * 100).toInt()
    val perfectPct = (com.monsterquest.core.Dens.throwOdds(m, c.bait, com.monsterquest.core.TraceGrade.PERFECT) * 100).toInt()
    var casting by remember { mutableStateOf(false) }
    if (casting) {
        val glyph = remember(m.uid) { com.monsterquest.core.Binding.glyphFor(m.species) }
        BindingCast(
            title = "Binding the ${m.species.name}",
            glyph = glyph,
            gradeOdds = com.monsterquest.core.TraceGrade.entries.associateWith { (com.monsterquest.core.Dens.throwOdds(m, c.bait, it) * 100).toInt() },
            onCancel = { casting = false },
        ) { grade ->
            casting = false
            vm.denThrow(grade)
        }
    }
    val overall = (com.monsterquest.core.Dens.oddsOver(m, c.throwsLeft, c.bait) * 100).toInt()
    val berries = com.monsterquest.core.Item.entries.filter { it.kind == com.monsterquest.core.ItemKind.BAIT && vm.state.count(it) > 0 && c.bait < it.power }
    val bonus = c.throwsTotal - com.monsterquest.core.Dens.BASE_THROWS
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Tame the ${m.species.name}!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterArt(m.species, Modifier.size(110.dp), shiny = m.shiny)
                Text("Lv ${m.level} • ${com.monsterquest.core.GameEngine.perfectStatCount(m)} perfect stats", fontWeight = FontWeight.SemiBold)
                Text(
                    "Catch rate: $pct%" + if (c.bait > 1.0) " on this cast (berry ${c.bait}x)" else " per cast",
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "$overall% to tame it with your ${c.throwsLeft} cast${if (c.throwsLeft == 1) "" else "s"} left, at a Good trace " +
                        "(including a 2% critical catch on every cast). A Perfect trace gives ${perfectPct}%." +
                        if (bonus > 0) " +$bonus cast${if (bonus == 1) "" else "s"} for your monsters still standing." else "",
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(c.throwsTotal) { i ->
                        GIcon(GameIcon.ORB, Modifier.size(18.dp), if (i < c.throwsLeft) Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    }
                }
                // Feed a berry before the next throw (it doesn't use a throw).
                berries.forEach { berry ->
                    OutlinedButton(onClick = { vm.denFeed(berry) }, modifier = Modifier.padding(top = 4.dp)) {
                        GIcon(berry.icon(), Modifier.size(16.dp), berry.color())
                        Spacer(Modifier.width(6.dp))
                        Text("Feed ${berry.label} x${vm.state.count(berry)}  •  ${berry.power}x", fontSize = 13.sp)
                    }
                }
                if (vm.state.storageFull) Text("Monster storage is full: make room first.", color = HpRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (vm.state.settings.simpleCasting) vm.denThrow() else casting = true },
                enabled = !vm.state.storageFull,
            ) { Text("Cast Binding") }
        },
        dismissButton = { TextButton(onClick = vm::denGiveUp) { Text("Let it go") } },
    )
}

/** For players from before 0.5: pick one of the new starters, free. */
@Composable
private fun StarterGiftDialog(vm: GameViewModel) {
    val level = com.monsterquest.core.GameEngine.giftStarterLevel(vm.state.hero.level)
    AlertDialog(
        onDismissRequest = { vm.starterGiftLater = true },
        title = { Text("New starters have arrived!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Thanks for playing from the start. Three rare starters joined Side Quest: pick one to join you " +
                        "at level $level. Each evolves twice into a pseudo-legendary.",
                    fontSize = 13.sp,
                )
                com.monsterquest.core.GameEngine.starters.map { com.monsterquest.core.Bestiary.get(it) }.forEach { sp ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonsterArt(sp, Modifier.size(56.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${sp.typeLabel} • " + com.monsterquest.core.Bestiary.lineOf(sp).drop(1).joinToString(" > ") { it.name },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SmallButton("Choose", { vm.claimStarterGift(sp.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.starterGiftLater = true }) { Text("Decide later") } },
    )
}

/** After beating a villain's team: restore one of their monsters (75% it joins you). */
@Composable
private fun SpoilsDialog(vm: GameViewModel) {
    val s = vm.state
    val potions = s.count(com.monsterquest.core.Item.RESTORE_POTION)
    AlertDialog(
        onDismissRequest = vm::dismissSpoils,
        title = { Text("Redeem a cursed monster?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Use a Restore Potion on one of their monsters. There's a 75% chance it joins you, " +
                        "still carrying the shadow curse until you battle together. You have $potions.",
                    fontSize = 13.sp,
                )
                if (s.storageFull) Text("Your monster storage is full.", color = HpRed, fontSize = 13.sp)
                s.villainSpoils.forEachIndexed { i, m ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonsterArt(m.species, Modifier.size(48.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, fontWeight = FontWeight.SemiBold)
                            Text("Lv ${m.level} ${m.species.typeLabel}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SmallButton("Restore", { vm.redeemVillainMonster(i) }, enabled = potions > 0 && !s.storageFull)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissSpoils) { Text("Leave them") } },
    )
}

/** The raised middle button. Tap or hold it to choose Hero or Monsters. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.HeroMenuButton(current: Tab?, open: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val lift by animateFloatAsState(if (open) 1.08f else 1f, label = "heroLift")
    // Sized like its neighbours (the bar only sets a minimum height, so filling it would take the screen).
    Column(
        Modifier.weight(1f).align(Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = lift; scaleY = lift }
                .clip(CircleShape)
                .background(if (current != null || open) primary else primary.copy(alpha = 0.18f))
                .border(2.dp, primary, CircleShape)
                .combinedClickable(onClickLabel = "Choose Hero or Monsters", onLongClick = onToggle, onClick = onToggle)
                .semantics { contentDescription = "Hero menu" },
            contentAlignment = Alignment.Center,
        ) {
            GIcon(
                if (open) GameIcon.CLOSE else (current ?: Tab.HERO).icon,
                Modifier.size(26.dp),
                if (current != null || open) MaterialTheme.colorScheme.onPrimary else primary,
            )
        }
        Text(
            current?.label ?: "Hero",
            maxLines = 1,
            softWrap = false,
            fontSize = 11.sp,
            color = if (current != null) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Hero and Monsters, popping up above the middle button. Tapping outside closes them. */
@Composable
private fun HeroMenuBubbles(onPick: (Tab) -> Unit, onDismiss: () -> Unit) {
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn() + scaleIn(initialScale = 0.4f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                slideInVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                listOf(Tab.HERO, Tab.MONSTERS).forEach { t ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { GIcon(t.icon, Modifier.size(30.dp), MaterialTheme.colorScheme.primary) }
                        Text(
                            t.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Before a fight against a lone foe: pick the monster who fights beside your hero; the rest wait on the bench. */
@Composable
private fun LeadPicker(vm: GameViewModel, choice: GameViewModel.LeadChoice, @Suppress("UNUSED_PARAMETER") outfit: HeroOutfit) {
    androidx.compose.material3.AlertDialog(
        // Backing out still starts the fight, with the first monster in your party.
        onDismissRequest = { vm.chooseLead(null) },
        title = { Text("Who fights beside you?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${choice.enemy.displayName} (Lv ${choice.enemy.level}) stands alone. ${vm.state.hero.name} fights with one monster; the others wait on the bench and can swap in.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choice.options.forEach { m ->
                    LeadRow(
                        title = m.displayName,
                        subtitle = "Lv ${m.level} ${m.species.typeLabel}" + if (m.damage > 0) " • hurt" else "",
                        onClick = { vm.chooseLead(m.uid) },
                    ) { MonsterArt(m.species, Modifier.size(44.dp), shiny = m.shiny) }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun LeadRow(title: String, subtitle: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
