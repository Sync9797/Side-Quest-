package com.monsterquest.app

import com.monsterquest.app.data.CalendarReader
import com.monsterquest.core.STEPS_PER_MILE
import com.monsterquest.core.METERS_PER_MILE
import com.monsterquest.core.Villains
import com.monsterquest.core.VillainCamp
import com.monsterquest.core.Stat
import com.monsterquest.core.WeaponType
import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsterquest.app.data.PlacesRepository
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.app.focus.FocusService
import com.monsterquest.app.focus.FocusStore
import com.monsterquest.core.Adventure
import com.monsterquest.core.Adventures
import com.monsterquest.core.Battle
import com.monsterquest.core.BattleAction
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Difficulty
import com.monsterquest.core.EncounterType
import com.monsterquest.core.GameEngine
import com.monsterquest.core.GameState
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.LootBoxes
import com.monsterquest.core.Monster
import com.monsterquest.core.Shrine
import com.monsterquest.core.Settings
import com.monsterquest.core.StepSource
import com.monsterquest.app.data.HealthSteps
import com.monsterquest.core.Update
import com.monsterquest.core.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a shrine just gave you. */
data class LootPopup(val title: String, val rewards: List<com.monsterquest.core.Reward>, val id: Long = System.nanoTime())

/** Days of water history read from Health Connect on each sync. */
private const val WATER_SYNC_DAYS = 30L

/** How often the real weather is looked up again. */
private const val WEATHER_REFRESH_MS = 20 * 60_000L

/** After a failed lookup, wait this long before trying again. */
private const val WEATHER_RETRY_MS = 5 * 60_000L

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(app)

    var state by mutableStateOf(repo.load())
        private set

    /** One-off message for the snackbar. */
    var message by mutableStateOf<String?>(null)
        private set

    var battle by mutableStateOf<BattleView?>(null)
        private set
    private var activeBattle: Battle? = null

    var location by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    /** Shrines from the place lookup (real where it loaded, generated stand-ins where not, plus waystones). */
    private var areaShrines by mutableStateOf<List<Shrine>>(emptyList())
    /** Landmarks read from the map's own tiles, which become shrines where the place lookup hasn't loaded. */
    private var mapShrines by mutableStateOf<List<Shrine>>(emptyList())
    private val shownShrines by androidx.compose.runtime.derivedStateOf {
        // Where the lookup has real places, use them; where it doesn't but the map does, the map's landmarks
        // replace the generated stand-ins (waystones stay to fill the gaps).
        val fromMap = mapShrines.filter { val k = World.tileKey(it.lat, it.lon); k !in osmTiles && k in mapTiles }
            .groupBy { World.tileKey(it.lat, it.lon) }.values.flatMap { World.selectForTile(it) }
        val mapCovered = fromMap.mapTo(HashSet()) { World.tileKey(it.lat, it.lon) }
        areaShrines.filter { !(it.id.startsWith("gen:") && World.tileKey(it.lat, it.lon) in mapCovered) } + fromMap
    }
    /** The shrines on the map now. */
    val shrines: List<Shrine> get() = shownShrines

    /** Points along nearby lakes, rivers and coast (from the map tiles). */
    private var waterPoints: List<Pair<Double, Double>> = emptyList()
    private var waterKnown = false
    /** Real places that host dungeons and traders, and points along streets (empty where map data isn't loaded). */
    private var osmVenues by mutableStateOf<List<com.monsterquest.core.Venue>>(emptyList())
    private var osmStreets by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    private var osmTiles by mutableStateOf<Set<String>>(emptySet())
    /** The same, read from the map's own vector tiles around you (fills in what OpenStreetMap lookups missed). */
    private var mapVenues by mutableStateOf<List<com.monsterquest.core.Venue>>(emptyList())
    private var mapStreets by mutableStateOf<List<Pair<Double, Double>>>(emptyList())
    private var mapTiles by mutableStateOf<Set<String>>(emptySet())
    private val venues by androidx.compose.runtime.derivedStateOf { com.monsterquest.core.Venues.merge(osmVenues, mapVenues) }
    /** Map-tile streets only count where the OpenStreetMap lookup has none (so streets aren't doubled). */
    private val streets by androidx.compose.runtime.derivedStateOf { osmStreets + mapStreets.filter { World.tileKey(it.first, it.second) !in osmTiles } }
    /** Map tiles whose places and streets are known; things are scattered elsewhere. */
    private val venueTiles by androidx.compose.runtime.derivedStateOf { osmTiles + mapTiles }

    /**
     * Places and streets the map read from its own vector tiles. [tiles] are
     * the game tiles those map tiles fully cover. The map keeps what it read
     * before for tiles it hasn't loaded this time.
     */
    fun addMapPlaces(
        found: List<com.monsterquest.core.Venue>,
        foundStreets: List<Pair<Double, Double>>,
        tiles: Set<String>,
        foundShrines: List<Shrine> = emptyList(),
    ) {
        if (tiles.isEmpty()) return
        fun keep(lat: Double, lon: Double) = World.tileKey(lat, lon) !in tiles
        val nextShrines = mapShrines.filter { keep(it.lat, it.lon) } + foundShrines.filter { World.tileKey(it.lat, it.lon) in tiles }
        val nextVenues = mapVenues.filter { keep(it.lat, it.lon) } + found.filter { World.tileKey(it.lat, it.lon) in tiles }
        val nextStreets = mapStreets.filter { keep(it.first, it.second) } + foundStreets.filter { World.tileKey(it.first, it.second) in tiles }
        // Keep it to the neighbourhood: tiles far behind you are dropped.
        val here = location
        val near = { lat: Double, lon: Double -> here == null || World.distanceMeters(here.first, here.second, lat, lon) < 4_000 }
        val v = nextVenues.filter { near(it.lat, it.lon) }
        val st = nextStreets.filter { near(it.first, it.second) }
        val t = (mapTiles + tiles).filterTo(HashSet()) { k ->
            val b = World.tileBounds(k)
            near((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
        }
        val sh = nextShrines.filter { near(it.lat, it.lon) }
        if (sh != mapShrines) mapShrines = sh
        if (v != mapVenues) mapVenues = v
        if (st != mapStreets) mapStreets = st
        if (t != mapTiles) mapTiles = t
    }
    /** Where the quick, saved-only shrines were last shown. */
    private var quickAt: Pair<Double, Double>? = null
    /** Some map tiles didn't load: try again after this time. */
    private var placesRetryAt = Long.MAX_VALUE

    /** Whether a point lies in a tile without place data (so it gets scattered places). */
    private fun scattered(lat: Double, lon: Double) = World.tileKey(lat, lon) !in venueTiles
    private var waterAt: Pair<Double, Double>? = null
    private var waterDistanceCache: Double? = null

    /** How far the nearest water is (null until the map tiles have loaded). Worked out again after moving 30 m. */
    fun waterDistance(): Double? {
        val here = location ?: return null
        if (placesFetchedAt == null || loadingPlaces) return waterDistanceCache
        val at = waterAt
        if (at == null || World.distanceMeters(at.first, at.second, here.first, here.second) > 30) {
            waterAt = here
            // No water found counts as far from water only if every tile around really loaded.
            waterDistanceCache = waterPoints.minOfOrNull { World.distanceMeters(here.first, here.second, it.first, it.second) }
                ?: if (waterKnown) Double.MAX_VALUE else null
        }
        return waterDistanceCache
    }
    var loadingPlaces by mutableStateOf(false)
        private set
    private var placesFetchedAt: Pair<Double, Double>? = null


    // Saves happen in order on a background thread; rapid changes are conflated.
    private val pendingSave = MutableStateFlow(state)

    /** A newer version waiting on GitHub (checked when the game starts). */
    var availableUpdate by mutableStateOf<com.monsterquest.app.data.AvailableUpdate?>(null)
        private set
    /** The result of the last update check started from Settings, to show there. */
    var updateCheckNote by mutableStateOf<String?>(null)
        private set

    /** The version installed, e.g. "3.15.1". */
    val installedVersion: String
        get() = runCatching { getApplication<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName } }.getOrNull() ?: "0"

    /** Looks for a newer version. On start it stays quiet unless there is one; from Settings it says what it found. */
    fun checkForUpdate(manual: Boolean = false) {
        if (!manual && !state.settings.checkUpdates) return
        // Not in tests (screenshots run the app offline-ish, with no real version installed).
        if (android.os.Build.FINGERPRINT == "robolectric") return
        if (manual) updateCheckNote = "Checking..."
        viewModelScope.launch {
            val ua = "SideQuest/$installedVersion (${getApplication<Application>().packageName})"
            val found = withContext(Dispatchers.IO) { com.monsterquest.app.data.UpdateChecker.check(installedVersion, ua) }
            when {
                found == null -> if (manual) updateCheckNote = "You have the latest version ($installedVersion)."
                !manual && found.version == state.settings.skippedVersion -> {}
                else -> {
                    availableUpdate = found
                    if (manual) updateCheckNote = null
                }
            }
        }
    }

    fun dismissUpdate(skip: Boolean) {
        val u = availableUpdate ?: return
        if (skip) updateSettings { it.copy(skippedVersion = u.version) }
        availableUpdate = null
    }

    init {
        checkForUpdate()
        viewModelScope.launch(Dispatchers.IO) {
            var widgetKey: Any? = null
            pendingSave.drop(1).collect {
                repo.save(it)
                // Keep the home-screen widget current when anything it shows changes.
                val key = listOf(it.tasks, it.reminders, it.hero.level)
                if (key != widgetKey) {
                    widgetKey = key
                    runCatching { com.monsterquest.app.widget.QuestWidget.refresh(getApplication()) }
                }
            }
        }
    }

    // ---- daily reminders -------------------------------------------------------

    /** Kept so the preferences listener isn't garbage-collected. */
    private val reminderTapListener = com.monsterquest.app.reminders.ReminderActions.listen(app) { applyReminderTaps() }

    /** Same for buttons on AI Widget Maker's Monster Quest widgets. */
    private val widgetTapListener = com.monsterquest.app.widget.WidgetActions.listen(app) { applyWidgetTaps() }

    /** Quest done, adventure claimed, egg hatched from a widget: applied with the game's own rules, at the time of the tap. */
    fun applyWidgetTaps() {
        val taps = com.monsterquest.app.widget.WidgetActions.drain(getApplication())
        if (taps.isEmpty()) return
        var s = state
        val notes = mutableListOf<String>()
        val rewards = mutableListOf<com.monsterquest.core.Reward>()
        for (a in taps) {
            val u = com.monsterquest.app.widget.WidgetActions.apply(s, a)
            s = u.state
            notes += u.message
            rewards += u.rewards
        }
        set(s)
        message = notes.filter { it.isNotBlank() }.joinToString(" ")
        if (rewards.isNotEmpty()) showRewards("From your widget", rewards)
    }

    /** A screen to open, asked for by a widget ("eggs", "adventures", "quests"); the UI clears it once it's there. */
    var openTarget by mutableStateOf<String?>(null)

    /** The Monsters screen's sub-tab to switch to, then cleared. */
    var monstersTabRequest by mutableStateOf<Int?>(null)

    /** Applies Done / Skip taps from notifications, each at the time it was tapped. */
    fun applyReminderTaps() {
        val taps = com.monsterquest.app.reminders.ReminderActions.drain(getApplication())
        if (taps.isEmpty()) return
        var s = state
        val notes = mutableListOf<String>()
        for ((id, done, at) in taps) {
            val u = if (done) com.monsterquest.core.Reminders.complete(s, id, at) else com.monsterquest.core.Reminders.skip(s, id, at)
            s = u.state
            notes += u.message
        }
        set(s)
        message = notes.joinToString(" ")
    }

    private fun rescheduleReminders() {
        runCatching { com.monsterquest.app.reminders.ReminderAlarms.scheduleAll(getApplication(), state.reminders) }
    }

    private fun applyReminders(u: Update) {
        apply(u)
        rescheduleReminders()
    }

    fun addReminder(title: String, hour: Int, minute: Int, days: Set<Int>) =
        applyReminders(com.monsterquest.core.Reminders.add(state, title, hour, minute, days))
    fun editReminder(id: String, title: String, hour: Int, minute: Int, days: Set<Int>) =
        applyReminders(com.monsterquest.core.Reminders.edit(state, id, title, hour, minute, days))
    fun setReminderEnabled(id: String, on: Boolean) = applyReminders(com.monsterquest.core.Reminders.setEnabled(state, id, on))
    fun deleteReminder(id: String) = applyReminders(com.monsterquest.core.Reminders.delete(state, id))
    fun completeReminder(id: String) = apply(com.monsterquest.core.Reminders.complete(state, id, System.currentTimeMillis()))
    fun skipReminder(id: String) = apply(com.monsterquest.core.Reminders.skip(state, id, System.currentTimeMillis()))

    private fun set(next: GameState) {
        // A new day or week starts its goals from the stats just before this change.
        // A new buddy (first party slot) starts its walk toward candy from zero.
        val today = java.time.LocalDate.now().toEpochDay()
        var s = com.monsterquest.core.Buddy.sync(com.monsterquest.core.Goals.rollover(state, next, today))
        // The mystery of the week: a new one each week, revealed by taming one.
        s = com.monsterquest.core.Mystery.track(state, com.monsterquest.core.Mystery.rollover(s, today))
        val walked = s.stats.totalSteps - state.stats.totalSteps
        // Add today's gains (levels, XP, loot, healing, tasks) for tomorrow's morning recap.
        state = com.monsterquest.core.Recap.track(state, s, System.currentTimeMillis()).withJournal()
        pendingSave.value = s
        if (walked > 0) rollSecretEncounter(walked.toInt())
    }

    // ---- secret walking encounters -------------------------------------------

    /** Steps toward the next secret-encounter roll (every [SecretEncounters.STEPS]). */
    private var secretBank = 0
    /** True while the map is on screen; surprise attacks only happen there. */
    var mapVisible = false
    private var lastBattleEndMs = 0L

    /** Monsters that just leapt out at you: the map shows "Ambushed!" and buzzes, then the fight starts. */
    var secretAmbush by mutableStateOf<List<Monster>?>(null)
        private set

    /** The thug leading a surprise attack (null when it's wild monsters). */
    var secretThug by mutableStateOf<com.monsterquest.core.Thug?>(null)
        private set

    /** The surprise attack is a rare visitor (easier to tame). */
    private var secretVisitor = false

    private fun rollSecretEncounter(steps: Int) {
        val rng = kotlin.random.Random.Default
        // With the battery saver on, monsters and thugs notice you more often.
        val (bank, hit) = com.monsterquest.core.SecretEncounters.roll(secretBank, steps, rng, saver = batterySaver)
        secretBank = bank
        val now = System.currentTimeMillis()
        // Wild monsters only find you while the battery saver is on (you're out walking, phone away).
        if (!hit || !batterySaver || !state.settings.ambushes || battle != null || secretAmbush != null || tooFast(now)) return
        resumeSaverAfterBattle = true
        if (state.focus != null || state.monsters.isEmpty() || now - lastBattleEndMs < com.monsterquest.core.SecretEncounters.COOLDOWN_MS) return
        // A rare visitor on its way (after a two-hour phone-free session or a weather event) finds you first.
        GameEngine.takeVisitor(state, now, rng)?.let { (s, m) ->
            set(s)
            secretThug = null
            secretVisitor = true
            secretAmbush = listOf(m)
            return
        }
        secretVisitor = false
        val fighting = GameEngine.fightingCount(state)
        if (fighting > 0 && com.monsterquest.core.SecretEncounters.isThug(state.hero.level, rng)) {
            val (thug, crew) = com.monsterquest.core.Villains.ambusher(state.hero.level, fighting, rng)
            secretThug = thug
            secretAmbush = crew
            return
        }
        val hour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
        secretThug = null
        // Food set out and the mystery of the week draw their monsters; the weather and nearby water shift the types.
        val (food, rare) = com.monsterquest.core.Foods.encounterBoosts(state, now)
        val mystery = com.monsterquest.core.Mystery.boosts(state)
        val boosts = (food.keys + mystery.keys).associateWith { (food[it] ?: 1.0) * (mystery[it] ?: 1.0) }
        val types = com.monsterquest.core.WeatherEvent.encounterBoosts(weather?.kind?.takeIf { state.settings.liveWeather }, waterDistance())
        secretAmbush = com.monsterquest.core.SecretEncounters.encounter(state.hero.level, hour, rng, boosts, rare, types)
    }

    /** The "Ambushed!" pop-up has shown: the monsters (or a thug and their crew) strike first. */
    fun startSecretAmbush() {
        val group = secretAmbush ?: return
        val thug = secretThug
        secretAmbush = null
        secretThug = null
        if (battle != null) return
        batterySaver = false
        val visitor = secretVisitor
        secretVisitor = false
        startBattle(
            group.first(), if (thug != null) EncounterType.THUG else EncounterType.WILD, null, group.drop(1), ambush = true, thug = thug,
            captureBoost = if (visitor) GameEngine.VISITOR_CATCH_BOOST else 1.0,
        )
    }

    /** Test hook: make a surprise attack happen now. */
    @VisibleForTesting
    fun debugSecretAmbush(group: List<Monster>, thug: com.monsterquest.core.Thug? = null) {
        secretThug = thug
        secretAmbush = group
    }

    // ---- speed guard ---------------------------------------------------------

    private var speed = com.monsterquest.core.SpeedGuard.State()

    /** Shown when you start moving faster than 15 mph: spawns pause, and a reminder not to play while driving. */
    var drivingWarning by mutableStateOf(false)

    /** Moving too fast (a car or bus): no monsters appear. */
    fun tooFast(nowMs: Long = System.currentTimeMillis()): Boolean = com.monsterquest.core.SpeedGuard.paused(speed, nowMs)

    @VisibleForTesting
    fun debugSpeed(lat: Double, lon: Double, nowMs: Long, mps: Float) {
        speed = com.monsterquest.core.SpeedGuard.update(speed, lat, lon, nowMs, mps)
        if (speed.justSpedUp) drivingWarning = true
    }

    private fun apply(u: Update) {
        set(u.state)
        message = u.message
        showRewards(u.rewardTitle, u.rewards)
    }

    /** Shows what was won with icons (the same pop-up as a shrine's loot). */
    private fun showRewards(title: String?, rewards: List<com.monsterquest.core.Reward>) {
        if (rewards.isNotEmpty()) lootPopup = LootPopup(title ?: "Rewards", rewards)
    }

    /** Test hook: replace the save without touching disk. */
    @VisibleForTesting
    fun debugLoad(s: GameState) = set(s)

    /** Test hook: pretend to be somewhere with the given shrines, skipping the network. */
    @VisibleForTesting
    /** For screenshots and tests: the weather as if it had just been looked up. */
    fun debugSetWeather(reading: com.monsterquest.core.WeatherReading?) {
        weather = reading
    }

    fun debugSetLocation(lat: Double, lon: Double, nearby: List<Shrine>) {
        placesFetchedAt = lat to lon
        location = lat to lon
        areaShrines = nearby
    }

    fun clearMessage() {
        message = null
    }

    fun toast(text: String) {
        message = text
    }

    // ---- setup / hero ------------------------------------------------------

    fun chooseStarter(speciesId: String) = apply(GameEngine.chooseStarter(state, speciesId))

    /** The "new starters" gift was put off until next time the app opens. */
    var starterGiftLater by mutableStateOf(false)

    fun claimStarterGift(speciesId: String) = apply(GameEngine.claimStarterGift(state, speciesId))
    fun allocate(attr: HeroAttribute) = apply(GameEngine.allocatePoint(state, attr))
    fun chooseClass(cls: HeroClass) = apply(GameEngine.chooseClass(state, cls))
    fun chooseSubclass(sub: com.monsterquest.core.Subclass) = apply(GameEngine.chooseSubclass(state, sub))
    fun learnSkill(slot: Int, skill: com.monsterquest.core.Trait) = apply(GameEngine.learnSkill(state, slot, skill))
    fun upgrade() = apply(GameEngine.applyUpgrade(state))
    fun buyStorage(bag: Boolean) = apply(GameEngine.buyStorage(state, bag))
    fun renameHero(name: String) {
        val n = name.trim().take(16)
        if (n.isNotEmpty()) set(state.copy(hero = state.hero.copy(name = n)))
    }

    // ---- quests ------------------------------------------------------------

    fun addTask(title: String, difficulty: Difficulty, dueMs: Long? = null, priority: com.monsterquest.core.Priority = com.monsterquest.core.Priority.NORMAL) =
        apply(GameEngine.addTask(state, title, difficulty, dueMs = dueMs, priority = priority))
    fun editTask(id: String, title: String, difficulty: Difficulty, dueMs: Long?, priority: com.monsterquest.core.Priority) =
        apply(GameEngine.editTask(state, id, title, difficulty, dueMs, priority))
    fun deleteTask(id: String) = apply(GameEngine.deleteTask(state, id))
    fun completeTask(id: String) = apply(GameEngine.completeTask(state, id, System.currentTimeMillis()))

    var syncingCalendar by mutableStateOf(false)
        private set

    /** Pull events from the phone's calendar into quests. */
    /** A sync was asked for while one was running (e.g. quick calendar toggles); run once more after. */
    private var resyncPending = false

    fun syncCalendar(quiet: Boolean = false) {
        if (syncingCalendar) {
            resyncPending = true
            return
        }
        syncingCalendar = true
        viewModelScope.launch {
            try {
                val prefs = state.settings
                val events = withContext(Dispatchers.IO) {
                    CalendarReader.read(
                        getApplication(),
                        daysAhead = prefs.calendarDaysAhead,
                        calendarIds = if (prefs.syncAllCalendars) null else prefs.syncCalendars,
                    )
                }
                    .filter { prefs.includeAllDayEvents || !it.allDay }
                val u = GameEngine.syncCalendar(state, events)
                if (quiet) set(u.state) else apply(u)
            } catch (e: SecurityException) {
                if (!quiet) message = "Calendar permission is needed to sync."
            } catch (e: Exception) {
                if (!quiet) message = "Calendar sync failed: ${e.message}"
            } finally {
                syncingCalendar = false
                if (resyncPending) {
                    resyncPending = false
                    syncCalendar(quiet = true)
                }
            }
        }
    }


    // ---- monsters & items --------------------------------------------------

    // ---- phone-free sessions -----------------------------------------------

    /** Result of the last phone-free session, shown in a dialog. */
    var focusResult by mutableStateOf<String?>(null)
        private set
    var focusSucceeded by mutableStateOf(false)
        private set

    fun startFocus(minutes: Int) {
        val u = GameEngine.startFocus(state, minutes, System.currentTimeMillis())
        apply(u)
        val f = u.state.focus ?: return
        FocusStore.clear(getApplication())
        FocusService.start(getApplication(), f.id, f.startMs, f.endMs)
    }

    fun giveUpFocus() = endFocus(success = false)

    fun dismissFocusResult() {
        focusResult = null
    }

    /**
     * Called whenever the app comes to the foreground. A phone-free session
     * ends here if the service recorded an outcome (the player left the game,
     * or time ran out); otherwise it keeps running.
     */
    fun onAppResumed() {
        regenerate()
        // Done / Skip taps from reminder notifications while the game was closed, then re-arm the alarms.
        applyReminderTaps()
        // Buttons tapped on widgets while the game was closed.
        applyWidgetTaps()
        rescheduleReminders()
        GameEngine.dailyRecovery(state, System.currentTimeMillis())?.let(::apply)
        // The morning's torches for the dungeons.
        com.monsterquest.core.Torches.morning(state, System.currentTimeMillis())?.let(::apply)
        // Pull in last night's sleep, then show the morning recap if it's the first open today.
        refreshSleep(thenRecap = true)
        refreshWater()
        val f = state.focus ?: return
        when (FocusStore.outcome(getApplication(), f.id)) {
            FocusStore.Outcome.FAILED -> endFocus(success = false)
            FocusStore.Outcome.SUCCESS -> endFocus(success = true)
            null -> if (System.currentTimeMillis() >= f.endMs) endFocus(success = true)
        }
    }

    /** The session's timer ran out while the game was open. */
    fun onFocusTimerDone() {
        val f = state.focus ?: return
        if (System.currentTimeMillis() >= f.endMs) endFocus(success = true)
    }

    private fun endFocus(success: Boolean) {
        if (state.focus == null) return
        val u = GameEngine.resolveFocus(state, success)
        set(u.state)
        focusSucceeded = success
        focusResult = u.message
        // Party training: show who levelled up once the result is dismissed.
        trainingLevelUps = u.levelUps
        FocusService.stop(getApplication())
        FocusStore.clear(getApplication())
    }

    // ---- loot boxes ---------------------------------------------------------

    /** Result of opening a loot box, shown in a dialog. */
    var boxResult by mutableStateOf<String?>(null)
        private set

    /** True when the phone's step counter is usable; otherwise GPS distance is used. */
    var stepSensorAvailable by mutableStateOf(false)

    private var lastWalkPoint: Pair<Double, Double>? = null

    /** Step counter reading: unlocks loot boxes and pays XP/healing every 100 steps. */
    fun onStepCount(totalSteps: Long) {
        if (state.settings.stepSource != StepSource.PHONE) return
        val u = GameEngine.onStepCount(state, totalSteps)
        set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }

    fun startUnlocking(id: String) = apply(LootBoxes.startUnlocking(state, id))
    fun discardBox(id: String) = apply(LootBoxes.discard(state, id))

    fun openBox(id: String) {
        val ready = state.lootBoxes.find { it.id == id }?.ready == true
        val opening = LootBoxes.open(state, id)
        set(opening.update.state)
        if (ready) {
            boxResult = opening.update.message
            boxBoss = opening.boss
        } else {
            message = opening.update.message
        }
    }

    /** Boss that came out of a Level 4 box; the result dialog's button starts the fight. */
    var boxBoss by mutableStateOf<Monster?>(null)
        private set

    fun dismissBoxResult() {
        boxResult = null
        boxBoss?.let { startBattle(it, EncounterType.BOX_BOSS, null) }
        boxBoss = null
    }

    fun forge(type: WeaponType) = apply(GameEngine.forge(state, type))
    fun forgeArmor(type: com.monsterquest.core.ArmorType) = apply(GameEngine.forgeArmor(state, type))
    fun smeltArmor(id: String) = apply(GameEngine.smeltArmor(state, id))
    fun upgradeSpell(id: String) = apply(GameEngine.upgradeSpell(state, id))
    fun chooseSpellPath(id: String, path: Int) = apply(com.monsterquest.core.SpellMastery.choose(state, id, path))
    fun masterSpell(id: String) = apply(com.monsterquest.core.SpellMastery.master(state, id))
    fun usePotion(item: Item, target: String) = apply(GameEngine.usePotion(state, item, target))
    fun perfectStat(uid: String, stat: Stat) = apply(GameEngine.perfectStat(state, uid, stat))

    /** GPS fallback for phones without a step counter: count distance moved while the app is open. */
    // ---- settings ------------------------------------------------------------

    fun updateSettings(change: (Settings) -> Settings) = set(state.copy(settings = change(state.settings)))

    fun clearCalendarQuests() = apply(GameEngine.clearCalendarQuests(state))

    /** The phone's calendars, for choosing which to sync. */
    var phoneCalendars by mutableStateOf<List<com.monsterquest.app.data.PhoneCalendar>>(emptyList())
        private set

    fun loadCalendars() {
        viewModelScope.launch {
            phoneCalendars = runCatching { withContext(Dispatchers.IO) { CalendarReader.calendars(getApplication()) } }.getOrDefault(emptyList())
        }
    }

    /**
     * Turns one calendar on or off for syncing, then re-syncs so its events
     * appear (or unfinished ones disappear) right away.
     */
    fun toggleCalendar(id: Long) {
        val prefs = state.settings
        // Switching from "all": start from every calendar that included.
        val current = if (prefs.syncAllCalendars) phoneCalendars.filter { !it.isNoise }.map { it.id }.toSet() else prefs.syncCalendars
        val next = if (id in current) current - id else current + id
        updateSettings { it.copy(syncAllCalendars = false, syncCalendars = next) }
        if (next.isEmpty()) message = "No calendars chosen: nothing will sync until you pick one."
        syncCalendar(quiet = true)
    }

    fun syncAllCalendars() {
        updateSettings { it.copy(syncAllCalendars = true, syncCalendars = emptySet()) }
        syncCalendar(quiet = true)
    }

    fun setStepSource(source: StepSource) {
        set(GameEngine.setStepSource(state, source))
        message = "Steps now come from ${source.label}."
        refreshHealthStatus()
        refreshHealthSteps()
    }

    /** Whether Health Connect is installed and the game may read steps. */
    var healthStatus by mutableStateOf<HealthSteps.Status?>(null)
        private set

    fun refreshHealthStatus() {
        viewModelScope.launch { healthStatus = runCatching { HealthSteps.status(getApplication()) }.getOrNull() }
    }

    /**
     * Today's steps by hour from Health Connect (when that's the step source),
     * or null to use the game's own hourly count.
     */
    var healthHourly by mutableStateOf<List<Int>?>(null)
        private set

    fun refreshHourlySteps() {
        val source = state.settings.stepSource
        if (!source.usesHealthConnect) {
            healthHourly = null
            return
        }
        viewModelScope.launch {
            healthHourly = HealthSteps.stepsByHour(getApplication(), java.time.LocalDate.now(), samsungOnly = source == StepSource.SAMSUNG_HEALTH)
        }
    }

    // ---- sleep and the morning recap ---------------------------------------------

    /** Whether Health Connect lets the game read sleep (null: Health Connect isn't available). */
    var sleepAllowed by mutableStateOf<Boolean?>(null)
        private set

    /** Reads the last week's sleep from Health Connect into the log, then (on resume) checks for the morning recap. */
    fun refreshSleep(thenRecap: Boolean = false) {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            sleepAllowed = HealthSteps.sleepAllowed(app)
            if (sleepAllowed == true) {
                HealthSteps.recentSleep(app)?.let { nights ->
                    var s = state
                    nights.forEach { s = com.monsterquest.core.SleepLog.record(s, it) }
                    if (s != state) set(s)
                }
            }
            restFromSleep()
            if (thenRecap && !showRecap && com.monsterquest.core.Recap.due(state, System.currentTimeMillis())) {
                // Let the loading screen finish first.
                kotlinx.coroutines.delay(2_200)
                showRecap = true
            }
        }
    }

    /** Logs a night by hand (when there's no Health Connect sleep data). */
    fun logSleep(startMs: Long, endMs: Long) {
        set(com.monsterquest.core.SleepLog.record(state, com.monsterquest.core.SleepNight(startMs, endMs, manual = true)))
        restFromSleep()
    }

    /** A full night's sleep that ended today refills Energy (once a day). */
    private fun restFromSleep() {
        val now = System.currentTimeMillis()
        val u = com.monsterquest.core.SleepLog.restEnergy(state, now)
        if (u.state != state) set(u.state)
        // How well you slept sets today's rest: a boost or a penalty.
        val r = com.monsterquest.core.Rest.update(state, now)
        if (r.state != state) set(r.state)
        val text = listOf(u.message, r.message).filter { it.isNotEmpty() }.joinToString(" ")
        if (text.isNotEmpty()) message = text
    }

    /** The morning recap is on screen. */
    var showRecap by mutableStateOf(false)
        private set

    /** Opens the recap now (screenshots and tests). */
    fun openRecap() { showRecap = true }

    fun dismissRecap() {
        showRecap = false
        set(com.monsterquest.core.Recap.markShown(state, System.currentTimeMillis()))
    }

    /** Water tracker (no rewards): +1 / −1 glass today. */
    fun addWater(delta: Int) {
        val before = com.monsterquest.core.Health.glasses(state, java.time.LocalDate.now().toEpochDay())
        set(com.monsterquest.core.Health.addWater(state, delta, System.currentTimeMillis()))
        val changed = com.monsterquest.core.Health.glasses(state, java.time.LocalDate.now().toEpochDay()) - before
        if (changed == 0 || waterAllowed != true) return
        // Mirror the change in Health Connect.
        val app = getApplication<android.app.Application>()
        val ml = com.monsterquest.core.Health.glassMl(state.settings.imperialUnits)
        viewModelScope.launch {
            if (changed > 0) {
                repeat(changed) { HealthSteps.addWater(app, ml) }
            } else {
                var removed = 0
                repeat(-changed) { if (HealthSteps.removeLastWater(app)) removed++ }
                // Glasses logged in another app can only be taken back there; the next sync restores them.
                if (removed < -changed) message = "That water was logged in another app: remove it there (Samsung Health, Fit...)."
            }
        }
    }

    /** Whether water syncs with Health Connect (null: Health Connect isn't available). */
    var waterAllowed by mutableStateOf<Boolean?>(null)
        private set

    /** Reads the last 30 days of water from Health Connect (when allowed) into the tracker. */
    fun refreshWater() {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            waterAllowed = HealthSteps.waterAllowed(app)
            if (waterAllowed == true) {
                HealthSteps.waterByDay(app, WATER_SYNC_DAYS)?.let { byDay ->
                    val s = com.monsterquest.core.Health.syncWater(state, byDay, com.monsterquest.core.Health.glassMl(state.settings.imperialUnits))
                    if (s != state) set(s)
                }
            }
        }
    }
    fun setWaterGoal(goal: Int) = set(com.monsterquest.core.Health.setWaterGoal(state, goal))

    /** Reads today's steps from Health Connect when that's the chosen source. */
    fun refreshHealthSteps() {
        val source = state.settings.stepSource
        if (!source.usesHealthConnect) return
        refreshHourlySteps()
        viewModelScope.launch {
            val total = HealthSteps.stepsToday(getApplication(), samsungOnly = source == StepSource.SAMSUNG_HEALTH) ?: return@launch
            val u = GameEngine.onDailyStepTotal(state, GameEngine.epochDay(System.currentTimeMillis()), total)
            set(u.state)
            if (u.message.isNotEmpty()) message = u.message
        }
    }

    private fun walkFromGps(lat: Double, lon: Double) {
        val source = state.settings.stepSource
        if (!(source == StepSource.GPS || (source == StepSource.PHONE && !stepSensorAvailable))) return
        val last = lastWalkPoint
        if (last != null) {
            val d = World.distanceMeters(last.first, last.second, lat, lon)
            // Ignore GPS jitter and big jumps (e.g. a fix after being in a car or tunnel).
            if (d < 5) return
            if (d < 300) {
                val u = GameEngine.walk(state, (d / METERS_PER_MILE * STEPS_PER_MILE).toInt())
                set(u.state)
                if (u.message.isNotEmpty()) message = u.message
            }
        }
        lastWalkPoint = lat to lon
    }

    // ---- adventures ---------------------------------------------------------

    /** Result of a returning adventure, shown in a dialog. */
    var adventureResult by mutableStateOf<String?>(null)
        private set
    var adventureSucceeded by mutableStateOf(false)
        private set

    /** Today's adventures at real places near you, closest first. */
    fun adventureOffers(nowMs: Long): List<Adventure> {
        val here = location ?: return emptyList()
        val nearest = shrines.sortedBy { World.distanceMeters(here.first, here.second, it.lat, it.lon) }
        return Adventures.offersNear(state, nearest, nowMs)
    }

    fun sendAdventure(adventure: Adventure, uids: List<String>) =
        apply(Adventures.send(state, adventure, uids, System.currentTimeMillis()))

    fun claimAdventure(id: String) {
        val u = Adventures.claim(state, id, System.currentTimeMillis())
        set(u.state)
        showRewards(u.rewardTitle, u.rewards)
        adventureSucceeded = u.message.contains("success!")
        adventureResult = u.message
    }

    fun dismissAdventureResult() {
        adventureResult = null
    }

    // ---- weapons -----------------------------------------------------------

    fun equip(id: String) = apply(GameEngine.equip(state, id))
    fun unequip() = apply(GameEngine.unequip(state))
    fun dropWeapon(id: String) = apply(GameEngine.dropWeapon(state, id))
    fun equipOffhand(id: String) = apply(GameEngine.equipOffhand(state, id))
    fun unequipOffhand() = apply(GameEngine.unequipOffhand(state))
    fun sellWeapon(id: String) = apply(GameEngine.sellWeapon(state, id))
    fun smelt(id: String) = apply(GameEngine.smelt(state, id))
    fun sellArmor(id: String) = apply(GameEngine.sellArmor(state, id))
    fun equipArmor(id: String) = apply(GameEngine.equipArmor(state, id))
    fun unequipArmor(slot: com.monsterquest.core.ArmorSlot = com.monsterquest.core.ArmorSlot.BODY) = apply(GameEngine.unequipArmor(state, slot))
    fun dropArmor(id: String) = apply(GameEngine.dropArmor(state, id))

    fun restore(uid: String) = apply(GameEngine.restore(state, uid))
    fun setMoves(uid: String, moveIds: List<String>) = apply(GameEngine.setMoves(state, uid, moveIds))
    fun learnMove(uid: String, newId: String, replaceId: String) {
        apply(GameEngine.learnMove(state, uid, newId, replaceId))
        if (state.settings.bigMomentHaptics) Haptics.learned(getApplication())
    }
    fun readTome(index: Int, spellId: String) = apply(GameEngine.readTome(state, index, spellId))
    fun claimAchievement(id: String) = apply(com.monsterquest.core.Achievements.claim(state, id))
    fun claimGoal(goal: com.monsterquest.core.Goal) = apply(com.monsterquest.core.Goals.claim(state, goal))
    fun claimBounty() = apply(com.monsterquest.core.Bounty.claim(state, java.time.LocalDate.now().toEpochDay()))
    fun claimTutorialStep(id: String) = apply(com.monsterquest.core.Tutorial.claim(state, id))
    fun finishIntro() = set(state.copy(tutorialIntroSeen = true))
    fun dismissTutorial() = set(state.copy(tutorialDismissed = true))

    /** Level-ups from party training during a phone-free session, shown one by one. */
    var trainingLevelUps by mutableStateOf<List<com.monsterquest.core.LevelUp>>(emptyList())
    fun dismissTrainingLevelUp() {
        trainingLevelUps = trainingLevelUps.drop(1)
    }

    // ---- save backup -----------------------------------------------------------

    /** The whole game as text, to save to a file the player keeps. */
    fun exportSave(): String = state.toJson()

    /** Reads a save file; null if it isn't one. */
    fun parseSave(text: String): GameState? = runCatching { GameState.fromJson(text) }.getOrNull()?.takeIf { it.starterChosen }

    /** Replaces the game with an imported save (the current one is kept on the phone first). */
    fun importSave(imported: GameState) {
        repo.keepBeforeImport()
        set(imported)
        message = "Save imported: ${imported.hero.name}, level ${imported.hero.level}."
    }
    fun resetMoves(uid: String) =
        set(state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(moves = null) else it }))

    /** Hourly rest healing for the hero and every monster (knocked-out ones wake at 10%). */
    fun regenerate() {
        val u = GameEngine.regenerate(state, System.currentTimeMillis())
        if (u.state != state) set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }

    fun setSwapSlot(uid: String, index: Int) = apply(GameEngine.setSwapSlot(state, uid, index))
    fun toggleParty(uid: String) = apply(GameEngine.toggleParty(state, uid))
    fun setPartySlot(uid: String, slot: Int) {
        val u = GameEngine.setPartySlot(state, uid, slot)
        set(u.state)
        if (u.message.isNotEmpty()) message = u.message
    }
    fun release(uid: String) = apply(GameEngine.release(state, uid))
    fun releaseMany(uids: List<String>) = apply(GameEngine.releaseMany(state, uids))
    fun candyLevelUp(uid: String) = apply(GameEngine.candyLevelUp(state, uid))
    fun evolve(uid: String) = apply(GameEngine.evolve(state, uid))
    fun rename(uid: String, name: String) = apply(GameEngine.rename(state, uid, name))
    fun feedTreat(uid: String) = apply(GameEngine.feedTreat(state, uid))
    fun rerollPersonality(uid: String) = apply(GameEngine.rerollPersonality(state, uid))
    fun breed(a: String, b: String) = apply(com.monsterquest.core.Eggs.breed(state, a, b, System.currentTimeMillis()))
    fun hatchEgg(id: String) = apply(com.monsterquest.core.Eggs.hatch(state, id))
    fun incubateEgg(id: String) = apply(com.monsterquest.core.Eggs.incubate(state, id))
    fun warmEggFirst(id: String) = apply(com.monsterquest.core.Eggs.warmFirst(state, id))
    fun buy(item: Item) = apply(GameEngine.buy(state, item))
    fun sell(item: Item) = apply(GameEngine.sellItem(state, item))
    fun discard(item: Item) = apply(GameEngine.discard(state, item))

    // ---- map ---------------------------------------------------------------

    fun onLocation(lat: Double, lon: Double, speedMps: Float? = null) {
        val now = System.currentTimeMillis()
        speed = com.monsterquest.core.SpeedGuard.update(speed, lat, lon, now, speedMps)
        if (speed.justSpedUp) drivingWarning = true
        location = lat to lon
        // On Silver and Gold days a shrine of that tier rises near you (and follows you until you use one).
        val roamed = com.monsterquest.core.RoamingShrines.update(state, lat, lon, now)
        if (roamed.roaming != state.roaming) {
            set(roamed)
            roamed.roaming?.let { message = "A ${it.shrine.name} rose nearby! Look for its glow on the map." }
        }
        // Distance covered in a car isn't walking.
        if (tooFast(now)) lastWalkPoint = lat to lon else walkFromGps(lat, lon)
        val last = placesFetchedAt
        if (!loadingPlaces && (last == null || World.distanceMeters(last.first, last.second, lat, lon) > 500 || now >= placesRetryAt)) {
            placesRetryAt = Long.MAX_VALUE
            loadingPlaces = true
            placesFetchedAt = lat to lon
            viewModelScope.launch {
                val ua = "SideQuest/0.1 (${getApplication<Application>().packageName})"
                val dir = java.io.File(getApplication<Application>().filesDir, "tiles")
                // Shrines at once from what the phone has saved (or stand-ins), while the lookup runs.
                val quick = withContext(Dispatchers.IO) { PlacesRepository.areaNear(dir, lat, lon, ua, offline = true) }
                if (areaShrines.isEmpty() || World.distanceMeters(lat, lon, quickAt?.first ?: lat, quickAt?.second ?: lon) > 500) {
                    areaShrines = quick.shrines
                    osmVenues = quick.venues
                    osmStreets = quick.streets
                    osmTiles = quick.venueTiles
                }
                quickAt = lat to lon
                val area = withContext(Dispatchers.IO) { PlacesRepository.areaNear(dir, lat, lon, ua) }
                areaShrines = area.shrines
                waterPoints = area.water
                osmVenues = area.venues
                osmStreets = area.streets
                osmTiles = area.venueTiles
                // Some tiles didn't load (a busy area can time out): try again in a couple of minutes.
                if (!area.complete) placesRetryAt = System.currentTimeMillis() + 2 * 60_000L
                waterKnown = area.waterKnown
                waterAt = null
                loadingPlaces = false
            }
        }
        refreshWeather(lat, lon, now)
    }

    // ---- real weather ----------------------------------------------------------

    /** The real weather where you are (null until it's looked up, offline, or turned off). */
    var weather by mutableStateOf<com.monsterquest.core.WeatherReading?>(null)
        private set
    private var weatherAt: Pair<Double, Double>? = null
    private var loadingWeather = false

    /** Looks the weather up again every [WEATHER_REFRESH_MS], or after a long trip. */
    private fun refreshWeather(lat: Double, lon: Double, now: Long) {
        if (!state.settings.liveWeather) {
            weather = null
            return
        }
        val w = weather
        val at = weatherAt
        val stale = w == null || now - w.atMs > WEATHER_REFRESH_MS ||
            (at != null && World.distanceMeters(at.first, at.second, lat, lon) > 10_000)
        if (loadingWeather || !stale) return
        // Don't hammer the service when offline: wait a while before trying again.
        if (w == null && now - lastWeatherTry < WEATHER_RETRY_MS) return
        loadingWeather = true
        lastWeatherTry = now
        viewModelScope.launch {
            val ua = "SideQuest/0.1 (${getApplication<Application>().packageName})"
            val reading = withContext(Dispatchers.IO) { com.monsterquest.app.data.WeatherRepository.current(lat, lon, ua, now) }
            if (reading != null) {
                weather = reading
                weatherAt = lat to lon
                // Storms, heatwaves, fog and snow send a rare visitor of their type, once a day each.
                com.monsterquest.core.WeatherEvent.of(reading.kind)?.let { e ->
                    com.monsterquest.core.WeatherEvent.trigger(state, e, now, java.time.LocalDate.now().toEpochDay(), kotlin.random.Random.Default)?.let { apply(it) }
                }
            }
            loadingWeather = false
        }
    }
    private var lastWeatherTry = 0L

    /**
     * The nearest landmark to a spot: a real named place (cafe, park, museum...)
     * within 250 m, or any shrine within 150 m. Returns it and how far it is from the spot.
     */
    fun landmarkNear(lat: Double, lon: Double): Pair<Shrine, Double>? {
        val withDistance = shrines.map { it to World.distanceMeters(lat, lon, it.lat, it.lon) }
        return withDistance.filter { (s, d) -> s.id.startsWith("osm:") && d <= 250 }.minByOrNull { it.second }
            ?: withDistance.filter { it.second <= 150 }.minByOrNull { it.second }
    }

    /** A distance in the player's chosen units. */
    private fun dist(meters: Double) = com.monsterquest.app.ui.formatDistance(meters, state.settings.imperialUnits)

    fun useFood(food: com.monsterquest.core.Item) = apply(com.monsterquest.core.Foods.use(state, food, System.currentTimeMillis()))

    // ---- battery saver -------------------------------------------------------

    /** The screen is blacked out while the game keeps tracking walking (and monsters can find you). */
    var batterySaver by mutableStateOf(false)

    /** The battery saver was turned on by turning the phone upside down (so turning it upright ends it). */
    var saverByFlip by mutableStateOf(false)

    fun distanceTo(lat: Double, lon: Double): Double? =
        location?.let { World.distanceMeters(it.first, it.second, lat, lon) }

    fun visitShrine(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${dist(World.SHRINE_RANGE_M)})."
            return
        }
        val u = GameEngine.visitShrine(state, shrine, System.currentTimeMillis())
        set(u.state)
        if (u.rewards.isEmpty()) {
            message = u.message
        } else {
            lootPopup = LootPopup(shrine.name, u.rewards)
            // Only the bag-full warning still needs a text message.
            if (u.message.contains("Bag full")) message = "Bag full: some loot was left behind."
        }
    }

    /** Loot just collected, shown briefly over the map. */
    var lootPopup by mutableStateOf<LootPopup?>(null)

    fun challengeBoss(shrine: Shrine) {
        val d = distanceTo(shrine.lat, shrine.lon)
        if (d == null || d > World.SHRINE_RANGE_M) {
            message = "Get closer to ${shrine.name} (within ${dist(World.SHRINE_RANGE_M)})."
            return
        }
        val (boss, mode) = GameEngine.bossEncounter(state, shrine, System.currentTimeMillis())
        if (boss == null) {
            message = mode
            return
        }
        val type = when (mode) {
            "silver" -> EncounterType.SILVER_BOSS
            "taming" -> EncounterType.BOSS_TAMING
            else -> EncounterType.BOSS_CHALLENGE
        }
        startBattle(boss, type, shrine.id)
    }

    /** Test hook: start a fight against [enemy] and its [pack]. */
    @VisibleForTesting
    fun debugStartBattle(enemy: Monster, pack: List<Monster> = emptyList(), ambush: Boolean = false) =
        startBattle(enemy, EncounterType.WILD, null, pack, ambush)

    /** Sends a swap monster in for one that just fainted (doesn't use a turn). */
    fun replaceFainted(inUid: String, outKey: String) {
        val b = activeBattle ?: return
        // Everyone out front was down: sending someone in carries the fight on (and it may end), like a turn.
        if (b.mustSwap) {
            battleAction(BattleAction.Swap(inUid, outKey))
            return
        }
        if (!b.replaceFainted(inUid, outKey)) return
        battle = battle?.let { battleView(b, it.resultText, it.nextOpponent) }
    }

    fun selectTarget(key: String) {
        val b = activeBattle ?: return
        b.selectTarget(key)
        battle = battle?.let { battleView(b, it.resultText, it.nextOpponent) }
    }

    fun hunt() {
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        val (s, enemy) = GameEngine.startHunt(state)
        if (enemy == null) {
            message = "No hunt tokens. Complete quests or visit shrines to earn more."
            return
        }
        set(s)
        startBattle(enemy, EncounterType.WILD, null)
    }

    // ---- villains -----------------------------------------------------------

    /** A fight against a villain's team in progress: which monster we're on. */
    private data class VillainRun(val camp: VillainCamp, val index: Int)
    private var villainRun: VillainRun? = null

    private var planFor: List<Any?>? = null
    private var planCache = com.monsterquest.core.VenuePlan.EMPTY

    /**
     * Today's dungeons, dens and traders: at real places, at most one dungeon
     * per block (busy downtowns aren't wall-to-wall), with scattered ones
     * filling in the gaps, everything spaced apart and clear of shrines.
     */
    private fun mapPlan(nowMs: Long): com.monsterquest.core.VenuePlan {
        val (lat, lon) = location ?: return com.monsterquest.core.VenuePlan.EMPTY
        val day = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val key = listOf(System.identityHashCode(state), System.identityHashCode(venues), System.identityHashCode(shrines), day, (lat * 2000).toInt(), (lon * 2000).toInt())
        if (key == planFor) return planCache
        val plan = com.monsterquest.core.Venues.topUp(
            com.monsterquest.core.Venues.plan(state, venues, nowMs),
            com.monsterquest.core.Dungeons.near(state, lat, lon, nowMs),
            com.monsterquest.core.Dens.near(state, lat, lon, nowMs),
            com.monsterquest.core.Wanderers.tradersNear(state, lat, lon, nowMs),
            avoid = shrines.map { it.lat to it.lon },
        )
        planFor = key
        planCache = plan
        return plan
    }

    fun densNear(nowMs: Long): List<com.monsterquest.core.Den> = mapPlan(nowMs).dens

    fun challengeDen(den: com.monsterquest.core.Den) {
        val d = distanceTo(den.lat, den.lon)
        if (d == null || d > com.monsterquest.core.Dens.RANGE_M) {
            message = "Get closer to the ${den.name} (within ${dist(com.monsterquest.core.Dens.RANGE_M)})."
            return
        }
        if (state.monsters.isEmpty()) {
            message = "You need a monster first."
            return
        }
        startBattle(den.monster, EncounterType.DEN, den.id)
    }

    /** A den cast's result waits here while the crystal shakes; [finishDenSeal] applies it. */
    var denSeal by mutableStateOf<Pair<com.monsterquest.core.CastRoll, com.monsterquest.core.Update>?>(null)
        private set

    fun denThrow(grade: com.monsterquest.core.TraceGrade = com.monsterquest.core.TraceGrade.GOOD) {
        val (u, roll) = com.monsterquest.core.Dens.cast(state, grade = grade)
        if (roll == null) apply(u) else denSeal = roll to u
    }

    fun finishDenSeal() {
        val (_, u) = denSeal ?: return
        denSeal = null
        apply(u)
    }
    fun denFeed(berry: com.monsterquest.core.Item) = apply(com.monsterquest.core.Dens.feed(state, berry))
    fun denGiveUp() = apply(com.monsterquest.core.Dens.giveUp(state))

    fun villainsNear(nowMs: Long): List<VillainCamp> {
        val (lat, lon) = location ?: return emptyList()
        // Thugs and villains stand on real streets where we know them.
        val onStreets = com.monsterquest.core.Venues.streetCamps(state, streets, nowMs, nightlife = venues.filter { it.role == com.monsterquest.core.VenueRole.NIGHTLIFE })
        return onStreets + Villains.near(state, lat, lon, nowMs).filter { scattered(it.lat, it.lon) }
    }

    fun challengeVillain(camp: VillainCamp) {
        val d = distanceTo(camp.lat, camp.lon)
        if (d == null || d > Villains.RANGE_M) {
            message = "Get closer to ${camp.leader} (within ${dist(Villains.RANGE_M)})."
            return
        }
        villainRun = VillainRun(camp, 0)
        startBattle(camp.team.first(), EncounterType.VILLAIN, null)
    }

    /** After beating one of a villain's monsters, face the next. */
    fun nextVillainMonster() {
        val run = villainRun ?: return
        val next = run.copy(index = run.index + 1)
        val enemy = run.camp.team.getOrNull(next.index) ?: return
        villainRun = next
        startBattle(enemy, EncounterType.VILLAIN, null)
    }

    fun redeemVillainMonster(index: Int) = apply(Villains.redeem(state, index))

    // ---- wanderers: traders and nomads -------------------------------------

    fun tradersNear(nowMs: Long): List<com.monsterquest.core.Trader> {
        // A trader by a grocery store in each neighbourhood, or a wandering one where there's none.
        return mapPlan(nowMs).traders
    }

    fun nomadsNear(nowMs: Long): List<com.monsterquest.core.Nomad> {
        val (lat, lon) = location ?: return emptyList()
        return com.monsterquest.core.Wanderers.nomadsNear(state, lat, lon, nowMs)
    }

    fun buyFromTrader(trader: com.monsterquest.core.Trader, index: Int) {
        val d = distanceTo(trader.lat, trader.lon)
        if (d == null || d > com.monsterquest.core.Wanderers.RANGE_M) {
            message = "Get closer to ${trader.name} (within ${dist(com.monsterquest.core.Wanderers.RANGE_M)})."
            return
        }
        apply(com.monsterquest.core.Wanderers.buy(state, trader, index, System.currentTimeMillis()))
    }

    private var nomadRun: com.monsterquest.core.Nomad? = null

    fun challengeNomad(nomad: com.monsterquest.core.Nomad) {
        val d = distanceTo(nomad.lat, nomad.lon)
        if (d == null || d > com.monsterquest.core.Wanderers.RANGE_M) {
            message = "Get closer to ${nomad.name} (within ${dist(com.monsterquest.core.Wanderers.RANGE_M)})."
            return
        }
        nomadRun = nomad
        startBattle(
            nomad.active.first(), EncounterType.NOMAD, null, pack = nomad.active.drop(1), thug = nomad.fighter,
            enemyReserve = nomad.reserve, swapLimit = com.monsterquest.core.Wanderers.NOMAD_SWAPS, trainer = nomad.name,
        )
    }

    // ---- dungeons ---------------------------------------------------------------

    /** Today's dungeons nearby (kept clear of shrines). */
    fun dungeonsNear(nowMs: Long): List<com.monsterquest.core.Dungeon> {
        // A dungeon in a coffee shop, doctor's office or restaurant on each block, or a scattered one where there's none.
        return mapPlan(nowMs).dungeons
    }

    fun enterDungeon(d: com.monsterquest.core.Dungeon) {
        val dist = distanceTo(d.lat, d.lon)
        if (dist == null || dist > com.monsterquest.core.Dungeons.RANGE_M) {
            message = "Walk to the ${d.name} first (within ${dist(com.monsterquest.core.Dungeons.RANGE_M)})."
            return
        }
        apply(com.monsterquest.core.Dungeons.enter(state, d))
    }

    /** Opens the left or right door: a fight starts a battle, a trap waits for your choice, the rest happen at once. */
    fun openDoor(left: Boolean) {
        val u = com.monsterquest.core.Dungeons.open(state, left)
        set(u.state)
        showRewards(u.rewardTitle, u.rewards)
        if (state.dungeonRun?.fighting != null) startDungeonFight()
    }

    /** Starts (or restarts, after the app was closed) the fight in the current room. */
    fun startDungeonFight() {
        val run = state.dungeonRun ?: return
        val room = run.fighting ?: return
        if (battle != null) return
        val foes = com.monsterquest.core.Dungeons.monstersFor(run, room)
        when (room) {
            com.monsterquest.core.RoomKind.BOSS -> {
                val champ = com.monsterquest.core.Dungeons.champion(run.dungeon)
                startBattle(foes.first(), EncounterType.DUNGEON_BOSS, null, foes.drop(1), thug = champ, trainer = champ.title, tutorial = run.dungeon.tutorial)
            }
            com.monsterquest.core.RoomKind.COMBAT -> startBattle(foes.first(), EncounterType.DUNGEON_GUARD, null, foes.drop(1), tutorial = run.dungeon.tutorial)
            else -> startBattle(foes.first(), EncounterType.WILD, null, foes.drop(1), tutorial = run.dungeon.tutorial)
        }
    }

    fun springTrap() = apply(com.monsterquest.core.Dungeons.springTrap(state))

    /** Pharmacies, gyms and hospitals nearby. */
    fun facilitiesNear(): List<com.monsterquest.core.Venue> = com.monsterquest.core.Venues.facilities(venues, streets)

    /** Uses a pharmacy, gym or hospital (you have to be there). */
    fun useFacility(v: com.monsterquest.core.Venue) {
        val d = distanceTo(v.lat, v.lon)
        if (d == null || d > com.monsterquest.core.Facilities.RANGE_M) {
            message = "Walk to ${v.name ?: v.kind ?: "it"} first (within ${dist(com.monsterquest.core.Facilities.RANGE_M)})."
            return
        }
        val now = System.currentTimeMillis()
        apply(
            when (v.role) {
                com.monsterquest.core.VenueRole.PHARMACY -> com.monsterquest.core.Facilities.infirmary(state, now)
                com.monsterquest.core.VenueRole.HOSPITAL -> com.monsterquest.core.Facilities.hospital(state, now)
                com.monsterquest.core.VenueRole.SCHOOL -> com.monsterquest.core.Facilities.study(state, now, venue = v)
                else -> com.monsterquest.core.Facilities.train(state, now)
            },
        )
    }

    /** A new player's first dungeon (after the intro). */
    fun startTutorial() = set(com.monsterquest.core.Dungeons.startTutorial(state))
    fun tutorialStarter(speciesId: String) = apply(com.monsterquest.core.Dungeons.pickStarter(state, speciesId))
    fun leaveDungeon() = apply(com.monsterquest.core.Dungeons.leave(state))

    /** The scene a battle (or dungeon) is drawn in: the dungeon's, or the outdoors at this time of day. */
    val scene: com.monsterquest.app.ui.Scene
        get() = state.dungeonRun?.dungeon?.theme?.let { com.monsterquest.app.ui.Scene.of(it) }
            ?: com.monsterquest.app.ui.Scene.outdoors(java.time.LocalTime.now().hour)

    fun toggleWanted(speciesId: String) = apply(com.monsterquest.core.Wanted.toggle(state, speciesId))
    fun claimMystery() = apply(com.monsterquest.core.Mystery.claim(state))
    fun dismissSpoils() = set(Villains.dismissSpoils(state))

    // ---- battle ------------------------------------------------------------

    private fun startBattle(
        enemy: Monster,
        type: EncounterType,
        shrineId: String?,
        pack: List<Monster> = emptyList(),
        ambush: Boolean = false,
        thug: com.monsterquest.core.Thug? = null,
        captureBoost: Double = 1.0,
        enemyReserve: List<Monster> = emptyList(),
        swapLimit: Int? = null,
        trainer: String? = null,
        tutorial: Boolean = false,
    ) {
        var s = state
        for (m in listOf(enemy) + pack + enemyReserve) s = GameEngine.markSeen(s, m.speciesId)
        set(s)
        fun begin(lead: String?) {
            val b = GameEngine.newBattle(
                state, enemy, type, shrineId, pack = pack, ambush = ambush, thug = thug,
                weather = weather?.kind?.takeIf { state.settings.liveWeather },
                captureBoost = captureBoost,
                enemyReserve = enemyReserve, swapLimit = swapLimit, trainer = trainer, tutorial = tutorial,
                lead = lead,
            )
            activeBattle = b
            battle = battleView(b, null)
        }
        val options = GameEngine.leadOptions(state, swapLimit)
        // A lone foe: you pick which monster fights beside the hero (when there's a choice).
        if (!tutorial && options.size > 1 && GameEngine.isOneOnOne(pack, thug, type in com.monsterquest.core.FULL_TEAM_ENCOUNTERS)) {
            leadChoice = LeadChoice(enemy, options, ::begin)
        } else {
            begin(null)
        }
    }

    /** A fight against a lone foe, waiting for you to pick the monster beside the hero. */
    class LeadChoice(val enemy: Monster, val options: List<Monster>, internal val start: (String?) -> Unit)

    var leadChoice by mutableStateOf<LeadChoice?>(null)
        private set

    /** Starts the waiting fight with this monster (uid) beside the hero (null: the first in the party). */
    fun chooseLead(uid: String?) {
        val c = leadChoice ?: return
        leadChoice = null
        c.start(uid)
    }

    fun battleAction(action: BattleAction) {
        val b = activeBattle ?: return
        if (b.outcome != BattleOutcome.ONGOING) return
        val casts = b.castCount
        b.act(action)
        // Big moments buzz: a tame (a critical one more so).
        val cast = b.lastCast
        if (state.settings.bigMomentHaptics && b.castCount != casts && cast?.success == true) {
            if (cast.critical) Haptics.criticalCatch(getApplication()) else Haptics.tamed(getApplication())
        }
        var resultText: String? = null
        var nextOpponent: String? = null
        var levelUps: List<com.monsterquest.core.LevelUp>? = null
        if (b.outcome != BattleOutcome.ONGOING) {
            val u = GameEngine.applyBattleResult(state, b.result(), System.currentTimeMillis())
            set(u.state)
            resultText = u.message
            // What the fight paid (and a cleared dungeon's, a nomad's or a crew's prize), shown with icons.
            var won = u.rewards
            var wonTitle = u.rewardTitle
            levelUps = u.levelUps
            if (state.settings.bigMomentHaptics && u.levelUps.isNotEmpty()) Haptics.levelUp(getApplication())
            // A dungeon room's fight: on to the next room, the champion's reward, or out.
            if (state.dungeonRun?.fighting != null) {
                val d = com.monsterquest.core.Dungeons.afterFight(state, b.outcome, System.currentTimeMillis())
                set(d.state)
                if (d.message.isNotEmpty()) resultText += " " + d.message
                if (d.rewards.isNotEmpty()) { won = won + d.rewards; wonTitle = d.rewardTitle ?: wonTitle }
            }
            nomadRun?.let { nomad ->
                if (b.outcome == BattleOutcome.WON) {
                    val reward = com.monsterquest.core.Wanderers.nomadReward(state, nomad, System.currentTimeMillis())
                    set(reward.state)
                    resultText += " " + reward.message
                    won = won + reward.rewards
                    wonTitle = reward.rewardTitle ?: wonTitle
                } else {
                    resultText += " ${nomad.name} nods: \"Come find me again today if you want a rematch.\""
                }
                nomadRun = null
            }
            val run = villainRun
            if (run != null) {
                val next = run.camp.team.getOrNull(run.index + 1)
                if (b.outcome == BattleOutcome.WON && next != null) {
                    nextOpponent = next.displayName
                } else if (b.outcome == BattleOutcome.WON) {
                    val reward = Villains.reward(state, run.camp, System.currentTimeMillis())
                    set(reward.state)
                    resultText += " " + reward.message
                    won = won + reward.rewards
                    wonTitle = reward.rewardTitle ?: wonTitle
                    villainRun = null
                } else {
                    resultText += " ${run.camp.leader}'s crew drives you off."
                    villainRun = null
                }
            }
            showRewards(wonTitle, won)
        }
        battle = battleView(
            b, resultText ?: battle?.resultText, nextOpponent ?: battle?.nextOpponent,
            levelUps ?: battle?.levelUps.orEmpty(),
        )
    }

    /** The battle as the screen shows it, with the species you've caught marked. */
    private fun battleView(
        b: com.monsterquest.core.Battle,
        resultText: String?,
        nextOpponent: String? = null,
        levelUps: List<com.monsterquest.core.LevelUp> = emptyList(),
    ) = BattleView.of(b, resultText, nextOpponent, levelUps).copy(caught = state.caught, simpleCasting = state.settings.simpleCasting)

    /** The battery saver was on when a wild fight started: it comes back on afterwards. */
    private var resumeSaverAfterBattle = false

    fun closeBattle() {
        if (resumeSaverAfterBattle) {
            resumeSaverAfterBattle = false
            batterySaver = true
        }
        lastBattleEndMs = System.currentTimeMillis()
        activeBattle = null
        battle = null
        villainRun = null
        nomadRun = null
    }
}
