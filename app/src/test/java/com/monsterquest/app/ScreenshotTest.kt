package com.monsterquest.app

import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.monsterquest.app.ui.AdventuresTab
import com.monsterquest.app.ui.AppRoot
import androidx.compose.foundation.layout.padding
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.MonsterArt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.monsterquest.app.ui.FoldInfo
import com.monsterquest.app.ui.LocalFold
import com.monsterquest.app.ui.MonsterQuestTheme
import com.monsterquest.app.ui.Tab
import com.monsterquest.core.ActiveAdventure
import com.monsterquest.core.Adventure
import com.monsterquest.core.Bestiary
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.LootBox
import com.monsterquest.core.Difficulty
import com.monsterquest.core.GameState
import com.monsterquest.core.Hero
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.Monster
import com.monsterquest.core.QuestTask
import com.monsterquest.core.TaskSource
import com.monsterquest.core.Armory
import com.monsterquest.core.WeaponType
import com.monsterquest.core.World
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Renders every screen at foldable sizes and writes PNGs to app/build/screenshots.
 * CI publishes them to the `screenshots` branch so layouts can be reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val device: String) {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun sampleState(): GameState {
        val rng = Random(3)
        val monsters = listOf(
            Monster.create(Bestiary.get("ember_pup"), 20, rng).copy(nickname = "Sir Barksalot III"),
            Monster.create(Bestiary.get("zapmouse"), 12, rng),
            Monster.create(Bestiary.get("emberkit"), 30, rng),
            Monster.create(Bestiary.get("toxbug"), 9, rng).copy(fainted = true),
            Monster.create(Bestiary.get("dread_lich"), 15, rng),
        )
        val now = System.currentTimeMillis()
        return GameState(
            hero = Hero(
                name = "Aaronimus",
                level = 12,
                xp = 230,
                unspentPoints = 4,
                attributes = HeroAttribute.entries.associateWith { 14 },
                heroClass = HeroClass.ROGUE,
                classChosen = true,
            ),
            equippedArmor = com.monsterquest.core.Armory.rollArmor(12, Random(6), com.monsterquest.core.ArmorType.LEATHER, minimum = com.monsterquest.core.GearRarity.EPIC),
            armor = listOf(com.monsterquest.core.Armory.rollArmor(12, Random(7), com.monsterquest.core.ArmorType.PLATE)),
            seen = Bestiary.all.take(14).map { it.id }.toSet() + "dread_lich",
            stepsToday = 6_420,
            stepsDay = java.time.LocalDate.now().toEpochDay(),
            stepHistory = (0L..12L).associate { java.time.LocalDate.now().toEpochDay() - it to (if (it % 3 == 0L) 7_000 else 2_500) },
            questDays = listOf(0L, 1L, 2L, 4L, 5L, 8L, 9L, 15L).associate { java.time.LocalDate.now().toEpochDay() - it to 2 },
            gold = 1234,
            huntTokens = 6,
            pendingUpgrades = 1,
            starterChosen = true,
            monsters = monsters,
            party = monsters.map { it.uid },
            gear = listOf(Armory.roll(12, Random(3), WeaponType.SWORD, luck = 40), Armory.roll(12, Random(4), WeaponType.STAFF)),
            lootBoxes = listOf(
                LootBox("b1", BoxRarity.RARE, 0, unlocking = true, walkedMeters = 2_000.0),
                LootBox("b2", BoxRarity.COMMON, 0, unlocking = true, walkedMeters = BoxRarity.COMMON.meters),
                LootBox("b3", BoxRarity.LEGENDARY, 0),
            ),
            equippedGear = Armory.roll(12, Random(5), WeaponType.DAGGER, luck = 80),
            spellPages = mapOf("gust_bolt" to 3, "venom_bolt" to 1, "ember_bolt" to 4),
            parts = mapOf(WeaponType.DAGGER to 3, WeaponType.BROADSWORD to 5),
            inventory = mapOf(Item.POTION to 4, Item.GREATER_POTION to 2, Item.MANA_DRAUGHT to 3, Item.BINDING_CRYSTAL to 6, Item.RESONANT_CRYSTAL to 2, Item.TREAT to 1),
            tasks = listOf(
                QuestTask("a", "Take out the recycling and the trash before the truck comes", Difficulty.EASY, dueMs = now + 20 * 60_000),
                QuestTask("b", "Finish quarterly report", Difficulty.HARD, dueMs = now + 26 * 3_600_000L, priority = com.monsterquest.core.Priority.HIGH),
                QuestTask("e", "Water the plants", Difficulty.EASY, priority = com.monsterquest.core.Priority.LOW),
                QuestTask("c", "Dentist appointment", Difficulty.NORMAL, TaskSource.CALENDAR, now + 3_600_000, now + 5_400_000, "Personal"),
                QuestTask("d", "Gym", Difficulty.NORMAL, completedAtMs = now - 1000),
            ),
        )
    }

    private fun vm(state: GameState = sampleState()) = GameViewModel(app).also { it.debugLoad(state) }

    private fun shot(
        name: String,
        fold: FoldInfo? = null,
        advanceMs: Long = 1_000,
        theme: com.monsterquest.core.ThemeMode = com.monsterquest.core.ThemeMode.DARK,
        content: @Composable () -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme(theme) { content() }
            }
        }
        capture(name, advanceMs)
    }

    /** Lets time pass, then saves what's on screen. */
    private fun capture(name: String, advanceMs: Long) {
        rule.mainClock.advanceTimeBy(advanceMs)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "${device}_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A half-open horizontal hinge across the middle of the screen (tabletop posture). */
    private fun tabletop(): FoldInfo {
        val dm = app.resources.displayMetrics
        val mid = dm.heightPixels / 2
        return FoldInfo(isSeparating = true, isVertical = false, bounds = Rect(0, mid, dm.widthPixels, mid))
    }

    @Test fun starter() { val v = vm(GameState()); shot("01_starter") { AppRoot(v) } }
    @Test fun quests() {
        val v = vm()
        shot("02_quests") { AppRoot(v, Tab.QUESTS) }
        // Headers: a welcome on the left (or top), the to-do and events title over the list.
        rule.onNodeWithText("Welcome back, Aaronimus").assertExists()
        // The to-do and events header is for the right pane only: a closed phone shows just the welcome.
        if (app.resources.configuration.screenWidthDp >= 600) rule.onNodeWithText("To-do & events").assertExists()
        else rule.onNodeWithText("To-do & events").assertDoesNotExist()
        // Buttons: + Task, Calendar and Sync (no more + To-do).
        rule.onNodeWithText("+ Task").assertExists()
        rule.onNodeWithText("Sync").assertExists()
        check(rule.onAllNodesWithText("+ To-do").fetchSemanticsNodes().isEmpty())
        // Today's quest reward allowance.
        rule.onNodeWithText("Full rewards: 8 of 8 left today", substring = true).assertExists()
        // Due within the hour: the to-do in 20 minutes and the dentist in an hour.
        rule.onNodeWithText("2 quests due within the hour").assertExists()
        // Tap today on the calendar: just today's quests. Tap it again: everything.
        // Today's calendar cell, by its tag (its number alone can match other text late in the day).
        val today = androidx.compose.ui.test.hasTestTag("calendar-day-${java.time.LocalDate.now()}")
        // The calendar sits below This week, possibly past the screen's edge: click through semantics.
        rule.onAllNodes(today)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        capture("02c_quests_day", 1_000)
        rule.onNodeWithText("Show all").assertExists()
        if (app.resources.configuration.screenWidthDp >= 600) {
            // Side by side, the calendar stays in view: tap the day again.
            rule.onAllNodes(today)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        } else {
            // On a phone the list scrolled to the day's quests: Show all goes back.
            rule.onNodeWithText("Show all").performClick()
        }
        // The day's list animates away: give it up to 3 seconds.
        repeat(6) {
            if (rule.onAllNodesWithText("Show all").fetchSemanticsNodes().isNotEmpty()) rule.mainClock.advanceTimeBy(500)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Show all").assertDoesNotExist()
    }

    /** The grouped quest list on its own: due-soon highlight, priorities, sections. */
    @Test fun questList() {
        val now = System.currentTimeMillis()
        val tasks = sampleState().tasks.filter { !it.completed }
        val sections = com.monsterquest.core.Planner.sections(tasks, now)
        shot("02b_quest_list", content = onBackground {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                sections.forEach { sec ->
                    com.monsterquest.app.ui.GroupHeader(sec.group, sec.tasks.size)
                    sec.tasks.forEach { com.monsterquest.app.ui.QuestRow(it, now, {}, {}, {}) }
                }
            }
        })
        rule.onNodeWithText("Due within the hour").assertExists()
        rule.onNodeWithText("Tasks").assertExists()
    }
    @Test fun hero() { val v = vm(); shot("03_hero") { AppRoot(v, Tab.HERO) } }

    /** A Scholar's Tome from a long phone-free session: pick a spell for its pages. */
    @Test fun scholarsTome() {
        val v = vm(sampleState().copy(tomes = listOf(5)))
        shot("03b_scholars_tome") { AppRoot(v, Tab.HERO) }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText("Choose a spell").performScrollTo()
        rule.onNodeWithText("Choose a spell").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Read the tome: 5 pages").assertExists()
        val spell = com.monsterquest.core.GameEngine.tomeChoices(v.state).first()
        rule.onAllNodes(androidx.compose.ui.test.hasText(spell.name) and androidx.compose.ui.test.hasClickAction())[0].performClick()
        rule.waitForIdle()
        check(v.state.tomes.isEmpty()) { "the tome was read" }
    }
    @Test fun monsters() {
        val v = vm()
        shot("04_monsters") { AppRoot(v, Tab.MONSTERS) }
        // Sort options, with Potential after Newest.
        rule.onNodeWithText("Newest").assertExists()
        rule.onNodeWithText("Potential").assertExists()
    }
    /** Hold a move on a monster's page and drag it down a slot: the order changes. */
    @Test fun reorderMoves() {
        val v = vm()
        val mon = v.state.partyMonsters.first()
        val first = mon.activeMoves[0]
        val second = mon.activeMoves[1]
        shot("04b_monster_moves") { AppRoot(v, Tab.MONSTERS) }
        rule.mainClock.autoAdvance = true
        // Open its page (on big screens another monster's page may be open beside the list).
        rule.onAllNodes(androidx.compose.ui.test.hasText(mon.displayName, substring = true) and androidx.compose.ui.test.hasClickAction())[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        val card = rule.onAllNodes(androidx.compose.ui.test.hasText(first.name) and androidx.compose.ui.test.hasClickAction())[0]
        card.performScrollTo()
        rule.waitForIdle()
        card.performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(6) { moveBy(androidx.compose.ui.geometry.Offset(0f, height * 0.2f)) }
            up()
        }
        rule.waitForIdle()
        val after = v.state.monsters.first { it.uid == mon.uid }.activeMoves.map { it.id }
        check(after.take(2) == listOf(second.id, first.id)) { "order after drag: $after" }
    }
    /** Settings > Backup: export and import the save, and the round trip works. */
    @Test fun saveBackup() {
        val v = vm()
        shot("44_settings_backup") { AppRoot(v, initialSettings = true) }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText("Export save").performScrollTo().assertExists()
        rule.onNodeWithText("Import save").assertExists()
        val text = v.exportSave()
        val back = v.parseSave(text)
        check(back != null && back.hero.level == v.state.hero.level && back.monsters.size == v.state.monsters.size) { "round trip" }
        check(v.parseSave("not a save") == null)
        v.importSave(back!!.copy(gold = 4242))
        check(v.state.gold == 4242)
    }

    @Test fun bag() {
        val v = vm()
        shot("05_bag") { AppRoot(v, Tab.BAG) }
        rule.onNodeWithText("Bag expansion").assertDoesNotExist()
    }
    @Test fun store() {
        val v = vm()
        shot("05b_store") { AppRoot(v, Tab.STORE) }
        // Storage lives in the Store: free upgrades and pricey expansions.
        rule.onNodeWithText("Bag expansion").assertExists()
        rule.onNodeWithText("Monster storage expansion").assertExists()
    }
    /** The middle Hero button opens Hero and Monsters bubbles; picking one goes there. */
    @Test fun heroMenu() {
        val v = vm()
        shot("05c_hero_menu") { AppRoot(v, Tab.QUESTS) }
        rule.onNodeWithContentDescription("Hero menu").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        capture("05c_hero_menu_open", 0)
        rule.onAllNodes(androidx.compose.ui.test.hasText("Monsters") and androidx.compose.ui.test.hasClickAction()).onLast()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.onNodeWithText("Type chart").assertExists()
    }

    @Test fun map() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        shot("06_map") { AppRoot(v, Tab.MAP) }
    }

    /** Real rain on the map: a badge that says so, and what it brings out. */
    @Test fun mapWeather() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        v.debugSetWeather(com.monsterquest.core.WeatherReading(com.monsterquest.core.LiveWeather.RAIN, 14.0, 12.0, System.currentTimeMillis()))
        shot("06c_map_weather") { AppRoot(v, Tab.MAP) }
        rule.onNodeWithText("Rain 57°F").assertExists()
        rule.onNodeWithText("Rain 57°F").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithText("More likely now").assertExists()
        rule.onNodeWithText("Battles start in Rain", substring = true).assertExists()
    }

    /** The map in dark mode (Settings > Appearance > Map). */
    @Test fun mapDark() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.updateSettings { it.copy(mapStyle = com.monsterquest.core.MapStyle.DARK) }
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        shot("06b_map_dark") { AppRoot(v, Tab.MAP) }
        rule.onNodeWithText("dens", substring = true).assertExists()
    }

    @Test fun battle() {
        val v = vm()
        v.hunt()
        v.chooseLead(null)
        shot("07_battle") { AppRoot(v) }
    }

    /** Against a lone foe, pick which monster fights beside the hero; the rest wait on the bench. */
    @Test fun whoStarts() {
        val v = vm()
        v.hunt()
        val pick = checkNotNull(v.leadChoice) { "a lone foe asks which monster fights beside the hero" }
        check(pick.options.size > 1)
        shot("07c_who_starts") { AppRoot(v) }
        rule.onNodeWithText("Who fights beside you?").assertExists()
        val m = pick.options[1]
        rule.onAllNodesWithText(m.displayName)[0].performClick()
        rule.waitForIdle()
        val b = checkNotNull(v.battle) { "the fight starts" }
        // Always the hero and one monster: the one picked.
        check(b.allies.map { it.key } == listOf("hero", m.uid)) { "hero and ${m.displayName}: ${b.allies.map { it.key }}" }
    }

    /** A foe you've already caught has a small orb next to its name. */
    @Test fun battleCaughtMark() {
        val v = vm(sampleState().copy(caught = Bestiary.all.map { it.id }.toSet()))
        v.hunt()
        v.chooseLead(null)
        shot("07b_battle_caught") { AppRoot(v) }
        check(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Already caught")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun battleNotCaughtMark() {
        val v = vm(sampleState().copy(caught = emptySet()))
        v.hunt()
        v.chooseLead(null)
        rule.setContent { MonsterQuestTheme(com.monsterquest.core.ThemeMode.DARK) { AppRoot(v) } }
        rule.waitForIdle()
        // Monsters you own count as caught, so the foe may still be marked if it's one of your species.
        val foeCaught = v.battle!!.enemies.any { v.battle!!.alreadyCaught(it) }
        val marks = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Already caught")).fetchSemanticsNodes()
        check(marks.isNotEmpty() == foeCaught) { "mark shown: ${marks.isNotEmpty()}, foe caught: $foeCaught" }
    }

    /** The potion picker shows HP bars and how much the potion would heal. */
    @Test fun potionPreview() {
        val base = sampleState()
        val hurt = base.monsters.first()
        val max = hurt.stats.hp
        val dmg = max * 3 / 4
        val v = vm(base.copy(monsters = base.monsters.map { if (it.uid == hurt.uid) it.copy(damage = dmg) else it }))
        val heal = com.monsterquest.core.GameEngine.potionHeal(com.monsterquest.core.Item.POTION, max)
        val now = max - dmg
        val after = minOf(max, now + heal)
        shot("43_potion_preview") { com.monsterquest.app.ui.PotionTargetDialog(v, com.monsterquest.core.Item.POTION) {} }
        rule.onNodeWithText("$now → $after HP (+${after - now})").assertExists()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("HP $now of $max, $after after healing")).assertExists()
        // Hurt monsters come first, right after you.
        val names = rule.onAllNodes(androidx.compose.ui.test.hasText("Heal") or androidx.compose.ui.test.hasText("Full HP")).fetchSemanticsNodes()
        check(names.isNotEmpty())
    }

    /** In battle, choosing a potion's target shows the same heal preview. */
    @Test fun battlePotionPreview() {
        val base = sampleState()
        // Hurt the whole party so whoever is listed has something to heal.
        // (Not so hurt that the wild monster can knock the hero out before the first turn.)
        val v = vm(base.copy(heroDamage = 40, monsters = base.monsters.map { it.copy(damage = it.stats.hp / 2) }))
        v.hunt()
        v.chooseLead(null)
        shot("44_battle_potion") { AppRoot(v) }
        val click = { n: androidx.compose.ui.test.SemanticsNodeInteraction ->
            n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
        }
        click(rule.onNodeWithText("Items", substring = true))
        // Exactly "Potion xN" (a substring would also match "Restore Potion").
        click(rule.onNodeWithText("${com.monsterquest.core.Item.POTION.label} x${v.state.count(com.monsterquest.core.Item.POTION)}"))
        check(rule.onAllNodes(androidx.compose.ui.test.hasText(" → ", substring = true)).fetchSemanticsNodes().isNotEmpty()) { "heal preview text" }
        capture("44b_battle_potion_targets", 300)
        check(rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("after healing", substring = true)).fetchSemanticsNodes().isNotEmpty()) { "heal preview bar" }
    }

    /** Binding: tracing the glyph along its lines, quickly, grades well and resolves the cast. */
    @Test fun bindingTrace() {
        val glyph = com.monsterquest.core.Binding.glyphFor(Bestiary.get("zapmouse"))
        var got: com.monsterquest.core.TraceGrade? = null
        shot("45_binding_trace") {
            androidx.compose.foundation.layout.Box(Modifier.background(androidx.compose.ui.graphics.Color(0xFF0D171A)).padding(24.dp)) {
                com.monsterquest.app.ui.TracePad(glyph, Modifier.size(300.dp)) { got = it }
            }
        }
        // Many points along each line, so the finger follows the glyph closely.
        val path = glyph.points.zipWithNext().flatMap { (a, b) ->
            (0 until 8).map { i -> com.monsterquest.core.GlyphPoint(a.x + (b.x - a.x) * i / 8f, a.y + (b.y - a.y) * i / 8f) }
        } + glyph.points.last()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Binding glyph: Bolt")).performTouchInput {
            down(androidx.compose.ui.geometry.Offset(path[0].x * width, path[0].y * height))
            path.drop(1).forEach { moveTo(androidx.compose.ui.geometry.Offset(it.x * width, it.y * height)) }
            up()
        }
        // Mid-flare: the glyph lights up and throws sparks.
        rule.mainClock.advanceTimeBy(250)
        rule.waitForIdle()
        capture("45b_binding_traced", 50)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        check(got != null && got!! >= com.monsterquest.core.TraceGrade.GOOD) { "graded $got" }
    }

    /** After a cast the crystal shakes (3 times for a tame), then seals: "Bound!". */
    @Test fun bindingSeal() {
        var done = false
        shot("46_binding_seal", advanceMs = 1_200) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                com.monsterquest.app.ui.SealAnimation(com.monsterquest.core.CastRoll(success = true, shakes = 3), com.monsterquest.app.ui.Gold) { done = true }
            }
        }
        check(!done) { "still shaking" }
        rule.mainClock.advanceTimeBy(2_400)
        rule.waitForIdle()
        rule.onNodeWithText("Bound!").assertExists()
        capture("46b_binding_bound", 50)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        check(done) { "the seal finishes" }
    }

    /** A failed cast: the energy bursts harmlessly, says so, and hands back to the fight. */
    @Test fun bindingBurst() {
        var done = false
        shot("47_binding_burst", advanceMs = 1_000) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                com.monsterquest.app.ui.SealAnimation(com.monsterquest.core.CastRoll(success = false, shakes = 1), com.monsterquest.app.ui.Gold) { done = true }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        capture("47b_binding_burst_blast", 120)
        rule.onNodeWithText("Broke free!").assertExists()
        rule.onNodeWithText("Back to the battle", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        check(done) { "returns to the battle" }
    }

    /** Batch release: pick monsters from the collection, confirm, and they're gone (with candy). */
    @Test fun batchRelease() {
        val base = sampleState()
        val extra = listOf("Pebbles", "Sparky", "Bramble").mapIndexed { i, n ->
            Monster.create(Bestiary.get("zapmouse"), 5 + i, Random(90 + i)).copy(nickname = n)
        }
        val v = vm(base.copy(monsters = base.monsters + extra))
        val bench = extra
        val before = v.state.monsters.size
        shot("48_batch_release") { AppRoot(v, Tab.MONSTERS) }
        val click = { n: androidx.compose.ui.test.SemanticsNodeInteraction ->
            n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            // A local state change: let Compose see it before the clock moves.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
        }
        click(rule.onNodeWithText("Release…"))
        rule.onNodeWithText("Tap monsters to release").assertExists()
        // The collection grid is lazy: scroll each tile into view, then tap it.
        // Scrolling waits for frames, so let the clock run from here on.
        rule.mainClock.autoAdvance = true
        val grid = rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
        bench.take(2).forEach { m ->
            val tile = androidx.compose.ui.test.hasText(m.displayName, substring = true) and androidx.compose.ui.test.hasClickAction()
            grid.performScrollToNode(tile)
            rule.waitForIdle()
            click(rule.onAllNodes(tile)[0])
        }
        capture("48b_batch_release_picked", 100)
        grid.performScrollToNode(androidx.compose.ui.test.hasText("Release 2"))
        rule.waitForIdle()
        click(rule.onNodeWithText("Release 2"))
        rule.onNodeWithText("Release 2 monsters?").assertExists()
        click(rule.onAllNodes(androidx.compose.ui.test.hasText("Release") and androidx.compose.ui.test.hasClickAction()).onLast())
        check(v.state.monsters.size == before - 2) { "released 2: ${v.state.monsters.size} of $before" }
        check(bench.take(2).none { m -> v.state.monsters.any { it.uid == m.uid } }) { "the picked ones went" }
    }

    @Test fun focus() {
        val v = vm()
        v.startFocus(30)
        shot("09_focus") { AppRoot(v) }
        // Giving up asks first, so it can't happen by accident.
        rule.onNodeWithText("Give up (−gold, −1 hunt token)").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Give up this session?").assertExists()
        rule.onNodeWithText("Keep going").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Give up this session?").assertDoesNotExist()
        check(v.state.focus != null) { "Keep going must not end the session" }
    }

    @Test fun adventures() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val base = sampleState()
        val now = System.currentTimeMillis()
        val running = ActiveAdventure(
            Adventure("demo", "Riverside Park", "Recover a lost relic near Riverside Park", 2, 12, 60 * 60_000L),
            base.monsters.drop(3).take(2).map { it.uid },
            now - 20 * 60_000L,
            0.72,
        )
        val v = vm(base.copy(adventures = listOf(running)))
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        shot("10_adventures") { AdventuresTab(v) }
    }

    /** Eggs on Home: a ready one glows (gold edge, Hatch button), one still warming. */
    @Test fun eggsReady() {
        val base = sampleState()
        val ready = com.monsterquest.core.Eggs.gift(Bestiary.get("emberkit"), Random(4), 3).let { it.copy(walkedMeters = it.meters) }
        val warming = com.monsterquest.core.Eggs.gift(Bestiary.get("drizzlet"), Random(5), 3).let { it.copy(walkedMeters = it.meters * 0.4) }
        val v = vm(base.copy(eggs = listOf(ready, warming)))
        shot("10c_eggs_ready", advanceMs = 600) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)).padding(16.dp),
            ) { com.monsterquest.app.ui.EggsSection(v) }
        }
        rule.onNodeWithText("Ready to hatch!").assertExists()
        rule.onNodeWithText("Hatch").assertExists()
    }

    /** A finished adventure: the Adventures button says so and wiggles until it's claimed. */
    @Test fun adventureReady() {
        val base = sampleState()
        val now = System.currentTimeMillis()
        val done = ActiveAdventure(
            Adventure("demo", "Riverside Park", "Recover a lost relic near Riverside Park", 2, 12, 60 * 60_000L),
            base.monsters.drop(3).take(2).map { it.uid },
            now - 2 * 60 * 60_000L,
            0.72,
        )
        val v = vm(base.copy(adventures = listOf(done)))
        // Caught mid-wiggle.
        shot("10b_adventure_ready", advanceMs = 200) { com.monsterquest.app.ui.MonstersScreen(v) }
        rule.onNodeWithText("Adventures (1!)").assertExists()
    }

    /** Every monster's artwork, for reviewing the designs. */
    @Test fun bestiary() {
        shot("11_bestiary") {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)),
            ) {
                Bestiary.all.chunked(6).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { sp ->
                            MonsterArt(sp, androidx.compose.ui.Modifier.size(56.dp))
                        }
                    }
                }
                androidx.compose.foundation.layout.Row {
                    com.monsterquest.core.HeroClass.entries.forEach { HeroArt(it, androidx.compose.ui.Modifier.size(80.dp)) }
                }
            }
        }
    }

    private fun onBackground(content: @Composable () -> Unit) = @Composable {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0D171A)),
        ) { content() }
    }

    @Test fun dashboard() {
        val today = java.time.LocalDate.now().toEpochDay()
        // Water goals met most of the last two weeks: blue dots on the calendar and a streak.
        val v = vm(sampleState().copy(water = (1L..14L).filter { it != 6L }.associate { (today - it) to 9 }))
        shot("12_dashboard", content = onBackground {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.monsterquest.app.ui.WeeklyCard(v)
                com.monsterquest.app.ui.DashboardCard(v)
            }
        })
    }

    @Test fun journal() {
        val v = vm()
        shot("13_journal", content = onBackground { com.monsterquest.app.ui.JournalTab(v) })
        // Open an entry: its lore and its max stats.
        val sp = Bestiary.all.first { it.id in v.state.seen }
        rule.mainClock.autoAdvance = true
        val cell = androidx.compose.ui.test.hasText(sp.name) and androidx.compose.ui.test.hasClickAction()
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(cell)
        rule.onAllNodes(cell)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText(com.monsterquest.core.Lore.of(sp)).assertExists()
        rule.onNodeWithText("Max stats").assertExists()
        rule.onNodeWithText("Total", substring = true).assertExists()
    }

    /** The hero wearing each kind of gear, and bare-handed. */
    @Test fun outfits() {
        val legendary = com.monsterquest.core.GearRarity.LEGENDARY
        val outfits = listOf(
            HeroClass.NONE to HeroOutfit(),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.BROADSWORD, armor = com.monsterquest.core.ArmorType.PLATE),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.SWORD, legendary, com.monsterquest.core.Element.FIRE, com.monsterquest.core.ArmorType.PLATE, legendary),
            HeroClass.ROGUE to HeroOutfit(WeaponType.DAGGER, com.monsterquest.core.GearRarity.EPIC, com.monsterquest.core.Element.GAS, com.monsterquest.core.ArmorType.LEATHER, com.monsterquest.core.GearRarity.RARE),
            HeroClass.MAGE to HeroOutfit(WeaponType.STAFF, armor = com.monsterquest.core.ArmorType.ROBE, armorRarity = com.monsterquest.core.GearRarity.EPIC),
            HeroClass.MAGE to HeroOutfit(WeaponType.WAND, legendary, com.monsterquest.core.Element.ELECTRIC, com.monsterquest.core.ArmorType.ROBE, legendary),
            // Hoods, cloaks, pants and boots.
            HeroClass.ROGUE to HeroOutfit(WeaponType.DAGGER, armor = com.monsterquest.core.ArmorType.LEATHER, hood = com.monsterquest.core.GearRarity.EPIC, cloak = legendary, pants = com.monsterquest.core.GearRarity.RARE, boots = com.monsterquest.core.GearRarity.NORMAL),
            HeroClass.NONE to HeroOutfit(WeaponType.SWORD, hood = com.monsterquest.core.GearRarity.NORMAL, cloak = com.monsterquest.core.GearRarity.RARE, boots = legendary),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.BROADSWORD, armor = com.monsterquest.core.ArmorType.PLATE, pants = legendary, boots = com.monsterquest.core.GearRarity.EPIC),
            // Helms, from plain to legendary.
            HeroClass.WARRIOR to HeroOutfit(WeaponType.SWORD, armor = com.monsterquest.core.ArmorType.PLATE, hood = com.monsterquest.core.GearRarity.NORMAL, helm = true),
            HeroClass.WARRIOR to HeroOutfit(WeaponType.BROADSWORD, armor = com.monsterquest.core.ArmorType.PLATE, armorRarity = com.monsterquest.core.GearRarity.EPIC, hood = com.monsterquest.core.GearRarity.EPIC, helm = true),
            HeroClass.ROGUE to HeroOutfit(WeaponType.DAGGER, armor = com.monsterquest.core.ArmorType.LEATHER, hood = legendary, helm = true),
        )
        shot("14_outfits", content = onBackground {
            androidx.compose.foundation.layout.Column {
                outfits.chunked(3).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { (cls, o) -> HeroArt(cls, androidx.compose.ui.Modifier.size(110.dp), outfit = o) }
                    }
                }
                // An evolution line from first form to final form, plus a few silhouettes.
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("emberkit")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp)) }
                }
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("bubblefin")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp)) }
                }
                androidx.compose.foundation.layout.Row {
                    Bestiary.lineOf(Bestiary.get("toxbug")).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(84.dp), silhouette = it.stage > 1) }
                }
            }
        })
    }

    /** The three starters' lines and some shinies. */
    @Test fun starters() {
        shot("23_starters", content = onBackground {
            androidx.compose.foundation.layout.Column {
                com.monsterquest.core.GameEngine.starters.forEach { id ->
                    androidx.compose.foundation.layout.Row {
                        Bestiary.lineOf(Bestiary.get(id)).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(104.dp)) }
                    }
                }
                androidx.compose.foundation.layout.Row {
                    listOf("thunderhorn", "emberkit", "goopling").forEach { MonsterArt(Bestiary.get(it), androidx.compose.ui.Modifier.size(104.dp), shiny = true) }
                }
                androidx.compose.foundation.layout.Row {
                    listOf("magma_behemoth", "dread_lich").forEach { MonsterArt(Bestiary.get(it), androidx.compose.ui.Modifier.size(104.dp), shiny = true) }
                    MonsterArt(Bestiary.get("worldroot_colossus"), androidx.compose.ui.Modifier.size(104.dp))
                }
            }
        })
    }

    /** The five newest evolution lines, first form to final form. */
    @Test fun newMonsters() {
        shot("15_new_monsters", content = onBackground {
            androidx.compose.foundation.layout.Column {
                listOf("goopling", "pebblit", "zipwing", "shellsnap", "impling", "twiglet", "moss_pup").forEach { id ->
                    androidx.compose.foundation.layout.Row {
                        Bestiary.lineOf(Bestiary.get(id)).forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(104.dp)) }
                    }
                }
            }
        })
    }

    @Test fun settings() {
        val v = vm()
        shot("16_settings") { AppRoot(v, initialSettings = true) }
        rule.onNodeWithText("Match app").assertExists()
    }

    @Test fun batterySaver() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        // A rare visitor on its way shows on the dark screen.
        val base = sampleState()
        val visitor = com.monsterquest.core.GameEngine.rareVisitor(base, System.currentTimeMillis(), Random(3))!!
        val v = vm(com.monsterquest.core.GameEngine.addVisitor(base, visitor, System.currentTimeMillis()))
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        v.batterySaver = true
        v.saverByFlip = true
        shot("17_battery_saver") { AppRoot(v, Tab.MAP) }
    }

    /** Mid-animation: attacks in flight, the attacker ringed, the turn order strip. */
    @Test fun battleFx() {
        val v = vm()
        v.hunt()
        v.chooseLead(null)
        v.battleAction(com.monsterquest.core.BattleAction.Attack)
        // Partway through: the bolt is in flight.
        shot("18_battle_fx", advanceMs = 330) { AppRoot(v) }
        capture("19_battle_hit", 300)
    }

    @Test fun lootPopup() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        v.lootPopup = LootPopup(
            "Corner Cafe",
            listOf(
                com.monsterquest.core.Reward.Gold(12),
                com.monsterquest.core.Reward.Items(Item.BINDING_CRYSTAL, 2),
                com.monsterquest.core.Reward.Items(Item.POTION, 1),
                com.monsterquest.core.Reward.WeaponDrop(Armory.roll(12, Random(9), WeaponType.STAFF, luck = 60)),
                com.monsterquest.core.Reward.Pages("gust_bolt", 1, learned = false),
            ),
        )
        shot("20_loot_popup", advanceMs = 900) { AppRoot(v, Tab.MAP) }
    }

    @Test fun starterGift() {
        val v = vm(sampleState().copy(starterGiftPending = true))
        shot("24_starter_gift") { AppRoot(v) }
        // Dialogs open in their own window (not in the screenshot), so check it's showing.
        rule.onNodeWithText("New starters have arrived!").assertExists()
        rule.onNodeWithText("Drizzlet").assertExists()
    }

    @Test fun help() {
        shot("21_help", content = onBackground { com.monsterquest.app.ui.HelpScreen {} })
    }

    /** A 4 vs 3 pack ambush. */
    @Test fun packBattle() {
        val v = vm()
        val r = Random(11)
        v.debugStartBattle(
            Monster.create(Bestiary.get("impling"), 13, r),
            listOf(Monster.create(Bestiary.get("goopling"), 12, r), Monster.create(Bestiary.get("zipwing"), 13, r)),
            ambush = true,
        )
        shot("22_pack_battle", advanceMs = 5_000) { AppRoot(v) }
    }

    /** A shrine at a real place: themed name, the place and street underneath, and directions. */
    @Test fun placeDetails() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        val shop = World.shrineFromPlace(
            "node1", "Barnes & Noble", 40.75803, -73.98552, com.monsterquest.core.ShrineTier.ITEM,
            kind = "Bookstore", street = "Wards Road", category = World.PlaceCategory.SHOP,
        )
        v.debugSetLocation(40.7580, -73.9855, listOf(shop))
        shot("25_place_details") { AppRoot(v, Tab.MAP) }
        // Wide screens list nearby places beside the map; open the shrine from there.
        val nodes = rule.onAllNodesWithText("Supply Cache")
        if (nodes.fetchSemanticsNodes().isNotEmpty()) {
            // Tapping pans the map there and shows a card with directions.
            nodes[0].performClick()
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            capture("28_nearby_focus", 400)
            rule.onNodeWithText("Walk here").assertExists()
            rule.onNodeWithText("Details").performClick()
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            // On the focus card and in the shrine's dialog.
            rule.onAllNodesWithText("Barnes & Noble on Wards Road").assertCountEquals(2)
        }
    }

    /** Hero magic: ranks, mastery and the upgrade button. */
    @Test fun heroMagic() {
        val v = vm()
        shot("26_hero_magic") { AppRoot(v, Tab.HERO) }
        rule.onNodeWithText("Ember Bolt", substring = true).assertExists()
        check(rule.onAllNodesWithText("Rank 2").fetchSemanticsNodes().size == 3) { "Ember Bolt, Mend and Ward can rank up" }
        // Cast Ward in battle (the shield animation).
        v.hunt()
        v.chooseLead(null)
        v.battleAction(com.monsterquest.core.BattleAction.CastSpell(com.monsterquest.core.Spells.AEGIS, "hero"))
        capture("27_ward", 400)
    }

    /** Level 47 Berserker with two skills and a staff they haven't learned yet. */
    private fun skilledHero(): GameState {
        val base = sampleState()
        val staff = com.monsterquest.core.Gear("staff1", com.monsterquest.core.WeaponType.STAFF, com.monsterquest.core.GearRarity.EPIC, 40, proficiency = 3)
        return base.copy(
            hero = base.hero.copy(
                level = 47,
                heroClass = com.monsterquest.core.HeroClass.WARRIOR,
                classChosen = true,
                subclass = com.monsterquest.core.Subclass.BERSERKER,
                skills = listOf(com.monsterquest.core.Trait.MENACE, com.monsterquest.core.Trait.KLEPTOMANIAC),
            ),
            gold = 2000,
            gear = base.gear + staff,
        )
    }

    @Test fun heroSkills() {
        val v = vm(skilledHero())
        shot("28_hero_skills") { AppRoot(v, Tab.HERO) }
        check(rule.onAllNodesWithText("Berserker", substring = true).fetchSemanticsNodes().isNotEmpty()) { "subclass shown" }
        rule.onNodeWithText("Menace  ★★☆☆☆").assertExists()
        rule.onNodeWithText("Slot 3: opens at level 60").assertExists()
        rule.onNodeWithText("Off hand", substring = true).assertExists()
        // The class is a dropdown: the other classes only show once it's opened.
        check(rule.onAllNodesWithText("Mage").fetchSemanticsNodes().isEmpty()) { "other classes hidden" }
        // The class card (not the subclass one): it's the one with the class bonus line.
        rule.onNodeWithText("Bonus grows", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithText("Mage").assertExists()
        rule.onNodeWithText("Hide ▴").assertExists()
    }

    @Test fun shopSell() {
        val v = vm(skilledHero())
        shot("29_shop_sell") { AppRoot(v, Tab.STORE) }
        check(rule.onAllNodesWithText("Smelt +3").fetchSemanticsNodes().isNotEmpty()) { "epic weapons smelt into 3 parts" }
    }

    @Test fun bagGear() {
        val v = vm(skilledHero())
        shot("29b_bag_gear") { AppRoot(v, Tab.BAG) }
        // An Epic is -6 when new; after 3 of 10 battles, -5 is left.
        rule.onNodeWithText("Unfamiliar: -5 to all stats • practice 3/10 battles").assertExists()
    }

    /** Fold and unfold on the map: the map moves between layouts without breaking. */
    @Test fun foldUnfold() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        val dm = app.resources.displayMetrics
        val book = FoldInfo(isSeparating = true, isVertical = true, bounds = Rect(dm.widthPixels / 2, 0, dm.widthPixels / 2, dm.heightPixels))
        var fold by androidx.compose.runtime.mutableStateOf<FoldInfo?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalFold provides fold) { MonsterQuestTheme { AppRoot(v, Tab.MAP) } }
        }
        capture("29_fold_a", 1_000)
        repeat(2) {
            rule.runOnUiThread { fold = book }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.waitForIdle()
            capture("29_fold_b", 1_000)
            // Unfolded: the Nearby list sits beside the map.
            rule.onNodeWithText("Nearby").assertExists()
            rule.runOnUiThread { fold = null }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.waitForIdle()
            capture("29_fold_c", 1_000)
            if (app.resources.configuration.screenWidthDp < 600) rule.onNodeWithText("Nearby").assertDoesNotExist()
        }
        rule.onNodeWithText("OpenStreetMap contributors", substring = true).assertExists()
    }

    /** The map tilts toward the horizon as you zoom in; places sit where they belong around the hero. */
    @Test fun mapTiltsAndProjects() {
        val t = com.monsterquest.app.ui.GameMap.Companion
        check(t.tiltFor(14.0) < t.tiltFor(16.5) && t.tiltFor(16.5) < t.tiltFor(19.0)) { "tilt grows with zoom" }
        check(t.tiltFor(20.0) <= 60.0)
        val gm = com.monsterquest.app.ui.GameMap(fake = true)
        gm.widthPx = 1000; gm.heightPx = 1000
        gm.centerOn(40.0, -74.0)
        val mid = gm.toScreen(40.0, -74.0)!!
        check(kotlin.math.abs(mid.x - 500f) < 0.5f && kotlin.math.abs(mid.y - 500f) < 0.5f) { "hero centered: $mid" }
        val north = gm.toScreen(40.001, -74.0)!!
        val east = gm.toScreen(40.0, -73.999)!!
        check(north.y < mid.y && east.x > mid.x) { "north up, east right" }
        val before = east.x - mid.x
        gm.zoomBy(1.0)
        val after = gm.toScreen(40.0, -73.999)!!.x - 500f
        check(kotlin.math.abs(after / before - 2f) < 0.01f) { "zooming in one level doubles distances" }
    }

    /** A den's monster after the fight: the taming throws. */
    @Test fun denCatch() {
        val m = Monster.create(Bestiary.get("ember_pup"), 16, Random(4))
        val v = vm(sampleState().let { st -> st.copy(pendingDenCatch = com.monsterquest.core.DenCatch("den:1:2:3", m, 6, 8), inventory = st.inventory + (com.monsterquest.core.Item.SWEET_BERRY to 2)) })
        shot("30_den_catch") { AppRoot(v) }
        rule.onNodeWithText("Tame the", substring = true).assertExists()
        rule.onNodeWithText("Cast Binding").assertExists()
        // The catch rate per throw and the overall odds with the throws left.
        rule.onNodeWithText("Catch rate:", substring = true).assertExists()
        rule.onNodeWithText("with your 6 casts left", substring = true).assertExists()
        rule.onNodeWithText("+3 casts for your monsters still standing", substring = true).assertExists()
        // Feed a berry: it doesn't use a throw, and the next one is likelier.
        rule.onNodeWithText("Feed Sweet Berry x2", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        check(v.state.pendingDenCatch?.bait == 1.5 && v.state.pendingDenCatch?.throwsLeft == 6) { "berry fed, no throw used" }
    }

    /** Every evolution line, first form to last, to review the evolved creatures. */
    private fun evolutionSheet(name: String, part: Int) {
        val lines = Bestiary.all.filter { it.stage == 1 && it.stages > 1 }.map { Bestiary.lineOf(it) }
        val half = lines.chunked((lines.size + 1) / 2)[part]
        shot(name, content = onBackground {
            androidx.compose.foundation.layout.Column {
                half.forEach { line ->
                    androidx.compose.foundation.layout.Row {
                        line.forEach { MonsterArt(it, androidx.compose.ui.Modifier.size(66.dp)) }
                    }
                }
            }
        })
    }

    @Test fun evolutionsA() = evolutionSheet("31_evolutions_a", 0)
    @Test fun evolutionsB() = evolutionSheet("32_evolutions_b", 1)

    /** After a win: LEVEL UP ribbons on the fighters, then a pop-up per fighter. */
    @Test fun levelUps() {
        val v = vm()
        v.hunt()
        v.chooseLead(null)
        // A lone wild monster is the hero alone now: put a bench monster on the field for the banner.
        val b0 = v.battle!!.let { it.copy(allies = it.allies + it.swaps.first()) }
        val pal = b0.allies[1]
        fun stats(vararg s: Pair<String, Pair<Int, Int>>) = s.map { (l, ba) -> com.monsterquest.core.StatLine(l, ba.first, ba.second) }
        val ups = listOf(
            com.monsterquest.core.LevelUp(
                "hero", "Aaronimus", null, 12, 13,
                stats("HP" to (202 to 212), "Energy" to (38 to 38), "Attack" to (26 to 26), "Magic" to (26 to 26), "Defense" to (26 to 26), "Speed" to (29 to 29)),
                notes = listOf("+3 attribute points to spend on the Hero tab."),
            ),
            com.monsterquest.core.LevelUp(
                pal.key, pal.name, pal.speciesId, 20, 21,
                stats("HP" to (118 to 124), "Attack" to (44 to 46), "Magic" to (22 to 23), "Defense" to (30 to 31), "Speed" to (41 to 43)),
                learned = listOf("Ember Fang"),
                canLearn = listOf("Flame Torrent"),
                canLearnIds = listOf("flame_torrent"),
                loadout = listOf("spit_fire", "cinder_jab", "heat_claws", "fang_snap"),
            ),
        )
        val b = b0.copy(outcome = com.monsterquest.core.BattleOutcome.WON, resultText = "Victory! +40 XP, +30 gold.", levelUps = ups, current = null)
        var learnedMove: Triple<String, String, String>? = null
        shot("33_level_up_banners", advanceMs = 1_500) {
            com.monsterquest.app.ui.BattleScreen(b, onAction = {}, onClose = {}, onLearn = { uid, new, old -> learnedMove = Triple(uid, new, old) })
        }
        check(rule.onAllNodesWithText("LEVEL UP!").fetchSemanticsNodes().size == 2) { "a ribbon over each fighter that levelled" }
        rule.onNodeWithText("Continue").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Aaronimus grew to Lv 13!").assertExists()
        rule.onNodeWithText("Next (1/2)").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("${pal.name} grew to Lv 21!").assertExists()
        rule.onNodeWithText("Learned Ember Fang!").assertExists()
        // It wants Flame Torrent too: compare, then forget Spit Fire for it.
        rule.onNodeWithText("${pal.name} wants to learn Flame Torrent!").assertExists()
        rule.onNodeWithText("Skip for now").assertExists()
        rule.onAllNodes(androidx.compose.ui.test.hasText("Spit Fire", substring = true) and androidx.compose.ui.test.hasClickAction())[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        check(learnedMove == Triple(pal.key, "flame_torrent", "spit_fire")) { "learned $learnedMove" }
        rule.onNodeWithText("Learned Flame Torrent in place of Spit Fire!").assertExists()
        rule.onNodeWithText("OK").performClick()
    }

    /** A thug jumps you with one monster for each of yours, and fights alongside them. */
    @Test fun thugAmbush() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        val count = com.monsterquest.core.GameEngine.fightingCount(v.state)
        val (thug, crew) = com.monsterquest.core.Villains.ambusher(12, count, Random(8))
        shot("36_thug_ambush") { AppRoot(v, Tab.MAP) }
        rule.runOnUiThread { v.debugSecretAmbush(crew, thug) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("${thug.title} of the ${thug.crew} jumps you!").assertExists()
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        val b = checkNotNull(v.battle) { "the fight starts on its own" }
        check(b.enemies.size == count + 1) { "the thug fights too: ${b.enemies.size}" }
        check(b.enemies.last().thug)
        capture("37_thug_battle", 1_000)
    }

    /** Daily reminders on the Quests list: one waiting to be done, one switched on for later. */
    @Test fun reminders() {
        val now = System.currentTimeMillis()
        val t = java.time.LocalTime.now()
        val base = sampleState()
        // Rang 20 minutes ago (but never before midnight, so it's always today's).
        val rang = maxOf(0, t.hour * 60 + t.minute - 20)
        var s = com.monsterquest.core.Reminders.add(base, "Make bed", rang / 60, rang % 60, (1..7).toSet(), Random(1)).state
        s = com.monsterquest.core.Reminders.add(s, "Take vitamins", 21, 30, (1..5).toSet(), Random(2)).state
        val v = vm(s)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MonsterQuestTheme(com.monsterquest.core.ThemeMode.DARK) {
                androidx.compose.foundation.layout.Column(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    com.monsterquest.app.ui.RemindersSection(v, now)
                }
            }
        }
        capture("43_reminders", 500)
        rule.onNodeWithText("Make bed").assertExists()
        rule.onNodeWithText("9:30 PM • Weekdays").assertExists()
        // The open one has Done and Skip right on it.
        // Late in the evening Take vitamins (9:30 PM) is open too: tick off whatever is open.
        while (rule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) {
            rule.onAllNodesWithText("Done")[0].performClick()
            rule.waitForIdle()
        }
        check(com.monsterquest.core.Reminders.streak(v.state.reminders.first { it.title == "Make bed" }, System.currentTimeMillis()) == 1)
    }

    /** The loading screen at start-up: crest, title and loading bar. */
    @Test fun bootSplash() {
        shot("42_splash", advanceMs = 900) { com.monsterquest.app.ui.BootSplash("Aaronimus", durationMs = 1_600) {} }
        rule.onNodeWithText("Side Quest").assertExists()
        rule.onNodeWithText("Welcome back, Aaronimus").assertExists()
    }

    /** The three kinds of thug: Brute, Hexer and Cutthroat. */
    @Test fun thugLooks() {
        shot("41_thugs") {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                listOf(com.monsterquest.core.HeroClass.WARRIOR, com.monsterquest.core.HeroClass.MAGE, com.monsterquest.core.HeroClass.ROGUE).forEach {
                    com.monsterquest.app.ui.art.ThugArt(it, Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }

    /** Two monsters wait in the swap slots; Swap sends one in. */
    /** The Health card on Home: folded to one line, then open with steps by hour, water and the sleep preview. */
    @Test fun health() {
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val hours = mapOf(7 to 420, 8 to 1_350, 9 to 210, 12 to 980, 13 to 400, 17 to 1_800, 18 to 650)
        var base = sampleState().copy(
            stepsToday = hours.values.sum(),
            stepsDay = today.toEpochDay(),
            stepsByHour = hours.mapKeys { com.monsterquest.core.Health.hourKey(today, it.key) },
        )
        base = com.monsterquest.core.Health.addWater(base, 5, now)
        // The water history: a week of goal days, a missed day, and more before it.
        base = base.copy(water = base.water + (1L..12L).filter { it != 8L }.associate { (today.toEpochDay() - it) to (if (it % 5 == 0L) 10 else 8) })
        // Last night from a watch, with stages, and a few nights before it.
        val wake = today.atTime(6, 45).atZone(zone)
        base = com.monsterquest.core.SleepLog.record(
            base,
            com.monsterquest.core.SleepNight(wake.minusMinutes(455).toInstant().toEpochMilli(), wake.toInstant().toEpochMilli(), deepMin = 85, lightMin = 230, remMin = 95, awakeMin = 45),
            zone,
        )
        listOf(1L to 420L, 2L to 380L, 4L to 500L).forEach { (back, mins) ->
            val w = wake.minusDays(back)
            base = com.monsterquest.core.SleepLog.record(base, com.monsterquest.core.SleepNight(w.minusMinutes(mins).toInstant().toEpochMilli(), w.toInstant().toEpochMilli(), manual = true), zone)
        }
        val v = vm(base)
        shot("40_health_closed") { AppRoot(v, Tab.QUESTS) }
        rule.onNodeWithText("Health").assertExists()
        rule.onNodeWithText("5 of 8 glasses", substring = true).assertExists()
        rule.onNodeWithText("Steps by hour").assertDoesNotExist()
        v.updateSettings { it.copy(healthOpen = true) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        capture("41_health_open", 1_000)
        rule.onNodeWithText("Log sleep").assertExists()
        rule.onNodeWithText("6h 50m").assertExists()
        rule.onNodeWithText("Daily goal").assertExists()
        // Water is just a tally: + adds a glass, no rewards.
        val gold = v.state.gold
        // The first "+" is the add-a-glass button (the second raises the goal).
        rule.onAllNodesWithText("+")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        check(com.monsterquest.core.Health.glasses(v.state, today.toEpochDay()) == 6) { "+ adds a glass" }
        check(v.state.gold == gold) { "water pays nothing" }
        rule.onNodeWithText("History").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithText("best streak").assertExists()
        rule.onNodeWithText("Yesterday").assertExists()
        capture("42_water_history", 1_000)
    }

    /** The morning recap: yesterday's gains and last night's sleep. */
    @Test fun morningRecap() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        var base = sampleState().copy(
            tallies = mapOf(
                today.toEpochDay() - 1 to com.monsterquest.core.DayTally(
                    heroLevels = 1, monsterLevels = 3, monsterXp = 1_240, gold = 380,
                    items = mapOf(com.monsterquest.core.Item.POTION to 2, com.monsterquest.core.Item.BINDING_CRYSTAL to 3), gear = 1,
                    hpRestored = 415, tasks = 6,
                ),
            ),
            stepHistory = mapOf(today.toEpochDay() - 1 to 9_874),
        )
        val wake = today.atTime(7, 10).atZone(zone)
        base = com.monsterquest.core.SleepLog.record(
            base,
            com.monsterquest.core.SleepNight(wake.minusMinutes(470).toInstant().toEpochMilli(), wake.toInstant().toEpochMilli(), deepMin = 95, lightMin = 250, remMin = 100, awakeMin = 25),
            zone,
        )
        val v = vm(base)
        v.openRecap()
        shot("42_morning_recap") { AppRoot(v, Tab.QUESTS) }
        rule.onNodeWithText("Monster XP").assertExists()
        // In the recap (the Home dashboard behind it also shows this week's steps).
        check(rule.onAllNodesWithText("9,874").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Steps").assertExists()
        rule.onNodeWithText("+1,240 (3 levels)").assertExists()
        rule.onNodeWithText("380 gold, 5 items, 1 gear").assertExists()
        rule.onNodeWithText("7h 25m").assertExists()
        rule.onNodeWithText("Let's go").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        check(!v.showRecap) { "Let's go closes the recap" }
        check(v.state.recapShownDay == today.toEpochDay()) { "shown once a day" }
    }

    /** Holding a move shows its accuracy and side effects. */
    @Test fun moveDetails() {
        shot("39_move_details") {
            com.monsterquest.app.ui.MoveDetailsDialog(com.monsterquest.core.Moves.get("scare"), listOf(com.monsterquest.core.Element.FIRE)) {}
        }
        rule.onNodeWithText("Accuracy").assertExists()
        rule.onNodeWithText("100%").assertExists()
        rule.onNodeWithText("Got it").assertExists()
    }

    @Test fun battleSwap() {
        val base = sampleState()
        val r = Random(30)
        val extra = listOf(Monster.create(Bestiary.get("fluffchick"), 14, r), Monster.create(Bestiary.get("shellsnap"), 13, r))
        val v = vm(base.copy(monsters = base.monsters + extra, swaps = extra.map { it.uid }))
        v.hunt()
        v.chooseLead(null)
        shot("38_swap") { AppRoot(v) }
        // On small screens the button can sit below the panel's fold: click it through its semantics.
        rule.onNodeWithText("Swap").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithText("Send in from the bench").assertExists()
        // The entry in the swap menu (the name alone also shows elsewhere on big screens).
        rule.onAllNodes(
            androidx.compose.ui.test.hasText("${extra[0].displayName}  Lv ${extra[0].level}", substring = true) and
                androidx.compose.ui.test.hasClickAction(),
        )[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("${extra[0].displayName} takes whose place?").assertExists()
        capture("39_swap_pick", 300)
        // One on one, the hero steps back and the monster fights in their place.
        rule.onAllNodes(androidx.compose.ui.test.hasText("Aaronimus", substring = true) and androidx.compose.ui.test.hasClickAction())
            .onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        check(v.battle!!.allies.none { it.isHero }) { "the hero stepped back" }
        check(v.battle!!.swaps.any { it.isHero }) { "the hero waits on the bench" }
    }

    @Test fun drivingWarning() {
        val v = vm()
        shot("40_driving") { AppRoot(v, Tab.QUESTS) }
        rule.runOnUiThread {
            v.debugSpeed(40.0, -74.0, 1_000, 1.2f)
            v.debugSpeed(40.002, -74.0, 6_000, 14f)
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithText("Moving too fast").assertExists()
        check(v.tooFast(6_000))
    }

    /** The secret walking encounter: "Ambushed!" over the map, then the fight starts by itself. */
    @Test fun secretAmbush() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val v = vm()
        v.debugSetLocation(40.7580, -73.9855, World.proceduralShrines(40.7580, -73.9855))
        val r = Random(21)
        val trio = List(3) { Monster.create(Bestiary.get("goopling"), 12, r) }
        shot("34_secret_ambush") { AppRoot(v, Tab.MAP) }
        rule.runOnUiThread { v.debugSecretAmbush(trio) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("AMBUSHED!").assertExists()
        rule.onNodeWithText("3 wild Gooplings leap out in front of you!").assertExists()
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        val b = checkNotNull(v.battle) { "the fight starts on its own" }
        check(b.enemies.size == 3)
        capture("35_secret_ambush_battle", 1_000)
    }

    /** The light theme on the busiest screens. */
    @Test fun lightQuests() { val v = vm(); shot("40_light_quests", theme = com.monsterquest.core.ThemeMode.LIGHT) { AppRoot(v, Tab.QUESTS) } }
    @Test fun lightHero() { val v = vm(); shot("41_light_hero", theme = com.monsterquest.core.ThemeMode.LIGHT) { AppRoot(v, Tab.HERO) } }
    @Test fun lightMonsters() { val v = vm(); shot("42_light_monsters", theme = com.monsterquest.core.ThemeMode.LIGHT) { AppRoot(v, Tab.MONSTERS) } }
    @Test fun lightBattle() {
        val v = vm()
        v.hunt()
        v.chooseLead(null)
        shot("43_light_battle", theme = com.monsterquest.core.ThemeMode.LIGHT) { AppRoot(v) }
    }
    @Test fun lightSettings() { val v = vm(); shot("44_light_settings", theme = com.monsterquest.core.ThemeMode.LIGHT) { AppRoot(v, initialSettings = true) } }

    @Test fun battleTabletop() {
        val v = vm()
        v.hunt()
        v.chooseLead(null)
        shot("08_battle_tabletop", tabletop()) { AppRoot(v) }
        // The commands must be on screen, not pushed below the bottom edge.
        rule.onNodeWithText("Flee").assertIsDisplayed()
    }

    /** A new player's intro pages, then the tutorial dungeon with its guide. */
    @Test fun tutorialDungeon() {
        val v = vm(GameState())
        shot("40_tutorial_intro") { AppRoot(v) }
        v.startTutorial()
        v.tutorialStarter("voltfawn")
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        capture("41_tutorial_room", 1_000)
        rule.onNodeWithText("Treasury", substring = true).assertExists()
    }

    /** A dungeon room: the painted forge, progress, the team and two doors. */
    @Test fun dungeonRoom() {
        val d = com.monsterquest.core.Dungeon("dg:1:1:1", "Ember Forge", com.monsterquest.core.DungeonTheme.FORGE, 3, 12, 0.0, 0.0, seed = 7L)
        val v = vm(sampleState().copy(dungeonRun = com.monsterquest.core.DungeonRun(d, step = 2, gold = 120, last = "A treasury! The chest holds 96 gold and 2 torches.")))
        shot("42_dungeon_room") { AppRoot(v) }
        rule.onNodeWithText("Choose a door").assertExists()
    }

    /** A trap: its kind, the odds, the damage, and a torch to pass safely. */
    @Test fun dungeonTrap() {
        // A 4-star run whose left door is a trap you can see.
        val run = (0L until 400L).flatMap { seed ->
            val d = com.monsterquest.core.Dungeon("dg:1:1:1", "Ember Forge", com.monsterquest.core.DungeonTheme.FORGE, 4, 14, 0.0, 0.0, seed = seed)
            (0 until d.rooms).map { com.monsterquest.core.DungeonRun(d, step = it) }
        }.first { com.monsterquest.core.Dungeons.doors(it).let { (l, r) -> l == com.monsterquest.core.RoomKind.TRAP && r != com.monsterquest.core.RoomKind.TRAP } && !com.monsterquest.core.Dungeons.hidden(it, true) && !com.monsterquest.core.Dungeons.hidden(it, false) }
        val v = vm(sampleState().copy(torches = 6, dungeonRun = run))
        shot("43_dungeon_trap_doors") { AppRoot(v) }
        rule.onAllNodesWithText("Trap")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithText("Light a torch (you have 6)").assertExists()
        capture("43b_dungeon_trap", 500)
    }

    /** Deep in a 5-star dungeon: pitch dark, every door unknown, and a torch to light the way. */
    @Test fun dungeonDark() {
        val d = com.monsterquest.core.Dungeon("dg:1:1:2", "Shadow Crypt", com.monsterquest.core.DungeonTheme.FORGE, 5, 30, 0.0, 0.0, seed = 3L)
        val v = vm(sampleState().copy(torches = 4, dungeonRun = com.monsterquest.core.DungeonRun(d, step = d.rooms - 2)))
        shot("44_dungeon_dark") { AppRoot(v) }
        rule.onNodeWithText("Light a torch (lights 3 rooms)").assertExists()
        rule.onAllNodesWithText("Unknown").fetchSemanticsNodes().let { check(it.size == 2) { "both doors dark" } }
    }
}

/** Galaxy Z Fold cover screen (the narrowest common foldable). */
@Config(sdk = [34], qualifiers = "w344dp-h882dp-xxhdpi")
class CoverScreenshots : ScreenshotBase("cover")

/** Galaxy Z Fold inner screen, unfolded. */
@Config(sdk = [34], qualifiers = "w673dp-h841dp-xhdpi")
class InnerScreenshots : ScreenshotBase("inner")

/** Regular phone. */
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshots : ScreenshotBase("phone")
