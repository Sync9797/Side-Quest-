package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Serializable
enum class HeroAttribute(val label: String, val blurb: String) {
    ATTACK("Attack", "Physical damage"),
    ENERGY("Energy", "Energy pool for spells"),
    MAGIC("Magic", "Spell damage and healing"),
    DEFENSE("Defense", "Reduces damage, raises max HP"),
    SPEED("Speed", "Act first, flee more easily"),
}

const val STAT_POINTS_PER_LEVEL = 3
const val STARTING_ATTRIBUTE = 8

/** Every this-many hero levels the player earns a storage upgrade. */
const val UPGRADE_LEVEL_INTERVAL = 5
const val BAG_UPGRADE_AMOUNT = 5
const val STORAGE_UPGRADE_AMOUNT = 5
/** Slots one bought expansion adds (bag or monster storage). */
const val PAID_UPGRADE_AMOUNT = 5
/** First bought expansion's price; each one after costs [PAID_UPGRADE_GROWTH]x the last. */
const val BAG_EXPANSION_COST = 1_500
const val STORAGE_EXPANSION_COST = 2_000
const val PAID_UPGRADE_GROWTH = 1.5

/**
 * Hero classes, chosen at [CLASS_UNLOCK_LEVEL]. The effect starts small and
 * grows with level (see [Hero.classBonus]).
 */
@Serializable
enum class HeroClass(val label: String, val summary: String) {
    NONE("Adventurer", "No class yet"),
    WARRIOR("Warrior", "Stronger and faster, weaker magic"),
    MAGE("Mage", "Stronger magic and defense, weaker attack"),
    ROGUE("Rogue", "Faster, and dodges some attacks"),
}

const val CLASS_UNLOCK_LEVEL = 5
const val CLASS_CHANGE_COST = 300

@Serializable
data class Hero(
    val name: String = "Hero",
    val level: Int = 1,
    val xp: Int = 0,
    val unspentPoints: Int = 0,
    val attributes: Map<HeroAttribute, Int> = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE },
    val knownSpells: Set<String> = Spells.starting,
    /** Rank of each known spell above 1 (missing means rank 1). */
    val spellRanks: Map<String, Int> = emptyMap(),
    /** Rank-5 spells' chosen paths (1 or 2), see [SpellMastery]. */
    val spellPaths: Map<String, Int> = emptyMap(),
    /** Mastery (1..5) of each spell's path. */
    val spellMastery: Map<String, Int> = emptyMap(),
    val heroClass: HeroClass = HeroClass.NONE,
    /** Set once a class (or staying an Adventurer) is picked at level 5. */
    val classChosen: Boolean = false,
    /** Picked at [SUBCLASS_UNLOCK_LEVEL]; always one of [heroClass]'s. */
    val subclass: Subclass? = null,
    /** Skills in slot order; slot i opens at [HERO_SKILL_LEVELS][i]. */
    val skills: List<Trait> = emptyList(),
) {
    fun attr(a: HeroAttribute): Int = attributes[a] ?: STARTING_ATTRIBUTE

    /** A spell's rank: 0 if not known yet, otherwise 1..[Spells.MAX_RANK]. */
    fun spellRank(id: String): Int = if (id in knownSpells) (spellRanks[id] ?: 1).coerceIn(1, Spells.MAX_RANK) else 0

    /** Known spells at their current rank and the hero's mastery. */
    fun spells(): List<Skill> = Spells.all.filter { it.id in knownSpells }.map { Spells.ranked(it, spellRank(it.id), level, heroClass) }

    /**
     * Class strength in percent: 3% at level 5, +1.5% per level after, capped
     * at 40% (level 30). Small at first, noticeable later.
     */
    val classBonus: Double
        get() = if (heroClass == HeroClass.NONE) 0.0 else (3 + (level - CLASS_UNLOCK_LEVEL) * 1.5).coerceIn(0.0, 40.0)

    private fun classMultiplier(a: HeroAttribute): Double {
        val b = classBonus / 100
        return 1 + when (heroClass) {
            HeroClass.NONE -> 0.0
            HeroClass.WARRIOR -> when (a) {
                HeroAttribute.ATTACK, HeroAttribute.SPEED -> b
                HeroAttribute.MAGIC -> -b / 2
                else -> 0.0
            }
            HeroClass.MAGE -> when (a) {
                HeroAttribute.MAGIC, HeroAttribute.DEFENSE -> b
                HeroAttribute.ATTACK -> -b / 2
                else -> 0.0
            }
            HeroClass.ROGUE -> if (a == HeroAttribute.SPEED) b else 0.0
        }
    }

    /** The subclass, if it still belongs to the hero's class. */
    val activeSubclass: Subclass? get() = subclass?.takeIf { it.parent == heroClass }

    /** Skill slots open at this level. */
    val skillSlots: Int get() = HERO_SKILL_LEVELS.count { level >= it }

    /** Each picked skill with its rank (it grows every 10 levels after its slot opened). */
    val skillRanks: List<Pair<Trait, Int>>
        get() = skills.take(skillSlots).mapIndexed { i, t -> t to skillRank(level, HERO_SKILL_LEVELS[i]) }

    fun rankOf(t: Trait): Int = skillRanks.firstOrNull { it.first == t }?.second ?: 0

    /** Everything the subclass and skills add. */
    val perks: Perks
        get() {
            var p = activeSubclass?.perks ?: Perks()
            for ((t, r) in skillRanks) {
                p += when (t) {
                    Trait.MORALE_MASTER -> Perks(teamPct = t.teamPct(r))
                    Trait.TREASURE_HUNTER -> Perks(goldPct = t.goldPct(r))
                    Trait.SCHOLAR -> Perks(xpPct = t.xpPct(r))
                    Trait.BEAST_WHISPERER -> Perks(tamePct = t.tamePct(r))
                    Trait.EAGLE_EYE -> Perks(critPct = t.critPct(r))
                    Trait.MANA_WELL -> Perks(energyPerTurn = t.energyPerTurn(r), maxEnergy = t.maxEnergy(r))
                    else -> Perks()
                }
            }
            return p
        }

    /** Percent the subclass (and First Strike, for Speed) adds to a stat; already part of [attack] and the rest. */
    private fun bonusPct(a: HeroAttribute): Int {
        val p = activeSubclass?.perks ?: Perks()
        return when (a) {
            HeroAttribute.ATTACK -> p.attackPct
            HeroAttribute.MAGIC -> p.magicPct
            HeroAttribute.DEFENSE -> p.defensePct
            HeroAttribute.SPEED -> p.speedPct + 3 * rankOf(Trait.FIRST_STRIKE)
            HeroAttribute.ENERGY -> 0
        }
    }

    private fun stat(a: HeroAttribute) = ((attr(a) + level) * (classMultiplier(a) + bonusPct(a) / 100.0)).toInt()

    // Effective combat stats; level adds a little to everything so allocating
    // points is a boost rather than the only source of growth.
    val attack: Int get() = stat(HeroAttribute.ATTACK)
    val magic: Int get() = stat(HeroAttribute.MAGIC)
    val defense: Int get() = stat(HeroAttribute.DEFENSE)
    val speed: Int get() = stat(HeroAttribute.SPEED)

    /** Chance (0..1) to dodge an enemy attack; Rogues only. */
    val evasion: Double get() = if (heroClass == HeroClass.ROGUE) classBonus / 200 else 0.0
    val maxHp: Int get() = (40 + level * 10 + attr(HeroAttribute.DEFENSE) * 3) * (100 + (activeSubclass?.perks?.maxHpPct ?: 0)) / 100
    val maxMp: Int get() = 10 + attr(HeroAttribute.ENERGY) * 2 + perks.maxEnergy

    val xpToNext: Int get() = heroXpForNext(level)
}

/** XP needed for the next level: 2.29 made levelling 60% slower for everyone ([XP_CURVE]). */
fun heroXpForNext(level: Int): Int = ((50 + (level - 1) * 40) * XP_CURVE).toInt()

/** Levelling takes this much more XP than it used to (hero and monsters). */
const val XP_CURVE = 1.6

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

@Serializable
enum class Difficulty(val label: String, val xp: Int, val gold: Int) {
    EASY("Easy", 15, 8),
    NORMAL("Normal", 30, 15),
    HARD("Hard", 60, 30),
}

@Serializable
enum class TaskSource { MANUAL, CALENDAR }

/** How urgent a quest is (separate from [Difficulty], which sets the reward). */
@Serializable
enum class Priority(val label: String) { LOW("Low"), NORMAL("Normal"), HIGH("High") }

@Serializable
data class QuestTask(
    val id: String,
    val title: String,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val source: TaskSource = TaskSource.MANUAL,
    /** Start time (epoch ms) for calendar events. */
    val startMs: Long? = null,
    val endMs: Long? = null,
    val calendarName: String? = null,
    val completedAtMs: Long? = null,
    /** When a manual quest was added (calendar quests use [startMs]). */
    val createdAtMs: Long? = null,
    /** When a to-do is due (calendar quests are due when they start). */
    val dueMs: Long? = null,
    val priority: Priority = Priority.NORMAL,
) {
    val completed: Boolean get() = completedAtMs != null

    /** The time this quest is due by: its due date, or a calendar event's start. */
    val dueAt: Long? get() = dueMs ?: startMs
}

// ---------------------------------------------------------------------------
// Shrines
// ---------------------------------------------------------------------------

@Serializable
data class ShrineRecord(
    val lastVisitMs: Long = 0,
    /** Boss shrines: the boss must be defeated once before it can be tamed. */
    val bossDefeated: Boolean = false,
    val bossCaptured: Boolean = false,
    /** Item shrines: how many times you've collected here (it builds a bond). */
    val visits: Int = 0,
) {
    val bond: ShrineBond get() = ShrineBond.of(visits)
}

/**
 * Item shrines you keep coming back to grow attached to you: more loot,
 * shorter recharges and, once bonded, more monsters gathering there.
 */
enum class ShrineBond(val label: String, val visits: Int, val extraRolls: Int, val goldMult: Double, val cooldownMin: Int) {
    NONE("New", 0, 0, 1.0, 30),
    FAMILIAR("Familiar", 5, 1, 1.0, 30),
    TRUSTED("Trusted", 15, 1, 1.5, 20),
    BONDED("Bonded", 40, 2, 2.0, 15);

    val next: ShrineBond? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(visits: Int): ShrineBond = entries.last { visits >= it.visits }
    }
}

// ---------------------------------------------------------------------------
// Phone-free focus sessions
// ---------------------------------------------------------------------------

@Serializable
data class FocusSession(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val minutes: Int,
)

val FOCUS_DURATIONS = listOf(15, 30, 45, 60, 90, 120)

/**
 * A rare monster on its way to you (after a two-hour phone-free session or a
 * weather event): it's the next one to find you on a battery-saver walk, if
 * that happens within [GameEngine.VISITOR_TTL_MS], and it's easier to tame.
 */
@Serializable
data class Visitor(
    val id: String,
    val speciesId: String,
    val level: Int,
    val createdMs: Long,
    val shiny: Boolean = false,
) {
    val species: Species get() = Bestiary.get(speciesId)
}

// ---------------------------------------------------------------------------
// Whole save game
// ---------------------------------------------------------------------------

const val STARTING_BAG_CAPACITY = 20
const val STARTING_MONSTER_CAPACITY = 20
const val MAX_PARTY_SIZE = 5
const val MAX_ITEMS_PER_BATTLE = 3
const val MAX_HUNT_TOKENS = 10
const val GOLD_LOSS_PERCENT = 10

@Serializable
data class GameState(
    val version: Int = 12,
    val hero: Hero = Hero(),
    val gold: Int = 50,
    val tasks: List<QuestTask> = emptyList(),
    val monsters: List<Monster> = emptyList(),
    /** UIDs of monsters that join battles (max [MAX_PARTY_SIZE]). */
    val party: List<String> = emptyList(),
    /** Monsters in the swap slots (max [MAX_SWAPS]): they can be sent in mid-battle. */
    val swaps: List<String> = emptyList(),
    val inventory: Map<Item, Int> = mapOf(Item.POTION to 3, Item.BINDING_CRYSTAL to 5, Item.RESTORE_POTION to 1),
    /** Epoch day of the last free daily recovery (see GameEngine.dailyRecovery). */
    val lastRecoveryDay: Long? = null,
    /** Unequipped weapons (each takes a bag slot). */
    val gear: List<Gear> = emptyList(),
    val equippedGear: Gear? = null,
    /** Second weapon, carried with the Kleptomaniac skill. */
    val offhandGear: Gear? = null,
    /** Unequipped armor (each takes a bag slot). */
    val armor: List<Armor> = emptyList(),
    /** Body armor (plate, leather or robe). */
    val equippedArmor: Armor? = null,
    /** Hood, cloak, pants and boots, one per slot. */
    val equippedPieces: Map<ArmorSlot, Armor> = emptyMap(),
    val settings: Settings = Settings(),
    /** Players from before the new starters get to pick one for free. */
    val starterGiftPending: Boolean = false,
    /** Rare visitors on their way to you (saved under their old name, followers). */
    @kotlinx.serialization.SerialName("followers")
    val visitors: List<Visitor> = emptyList(),
    /** When rest healing was last applied (see GameEngine.regenerate). */
    val lastRegenMs: Long = 0,
    /** Last daily step total read from Health Connect, and the day it was for. */
    val healthDay: Long? = null,
    val healthSteps: Long? = null,
    /** Candy per evolution line (family name -> count). Doesn't use bag space. */
    val candy: Map<String, Int> = emptyMap(),
    /** Journal: species encountered, and species owned at some point. */
    val seen: Set<String> = emptySet(),
    val caught: Set<String> = emptySet(),
    /** Species caught in their shiny colors. */
    val shinyCaught: Set<String> = emptySet(),
    /** Epoch day -> steps walked (last ~90 days). */
    val stepHistory: Map<Long, Int> = emptyMap(),
    /** Epoch day -> quests completed that day. */
    val questDays: Map<Long, Int> = emptyMap(),
    /** Achievements whose reward has been claimed, by id. */
    val achievementsClaimed: Set<String> = emptySet(),
    /** Daily and weekly goals: the day and week they're for, the stats when each began, and what's been claimed. */
    val goalDay: Long = -1,
    val dayBaseline: Lifetime = Lifetime(),
    val goalWeek: Long = -1,
    val weekBaseline: Lifetime = Lifetime(),
    val goalsClaimed: Set<String> = emptySet(),
    /** The last day each weather event happened (by event name), so each brings one visitor a day. */
    val weatherEventDays: Map<String, Long> = emptyMap(),
    /** Eggs being walked to hatch. */
    val eggs: List<Egg> = emptyList(),
    /** First steps: the welcome was shown, steps claimed, or the checklist dismissed. */
    val tutorialIntroSeen: Boolean = false,
    val tutorialClaimed: Set<String> = emptySet(),
    val tutorialDismissed: Boolean = false,
    /** Today's quest rewards so far (see [QuestRewards]); kept on the hero, so deleting quests doesn't reset it. */
    val questLimits: QuestLimits = QuestLimits(),
    /** Daily reminders (gentle alarms on chosen days). */
    val reminders: List<Reminder> = emptyList(),
    /** Food set out to draw monsters that eat it (see [Foods]), and until when. */
    val food: Item? = null,
    val foodUntilMs: Long = 0,
    /** Weapon parts by type; [PARTS_TO_FORGE] of one type forge that weapon. */
    val parts: Map<WeaponType, Int> = emptyMap(),
    /** Armor scrap by type; [PARTS_TO_FORGE] of one type forge that armor. From thugs and villains. */
    val armorParts: Map<ArmorType, Int> = emptyMap(),
    /** Damage the hero carries between battles (healed by walking and potions). */
    val heroDamage: Int = 0,
    /** Energy the hero has spent and not yet recovered (back slowly over time, faster by walking, or with a Mana Draught). */
    val heroEnergyUsed: Int = 0,
    /** Scholar's Tomes waiting to be read: pages each, for a spell the player picks. */
    val tomes: List<Int> = emptyList(),
    /** Up to when Energy recovery over time has been counted. */
    val energyRegenMs: Long = 0,
    /** Steps not yet turned into a reward (rewards come every 100 steps). */
    val stepBank: Int = 0,
    /** The buddy (first party slot) the walk toward its next candy counts for, and how far you've gone. */
    val buddyUid: String? = null,
    val buddyMeters: Double = 0.0,
    /** Species pinned in the Journal (see [Wanted]). */
    val wanted: Set<String> = emptySet(),
    /** This week's mystery monster (see [Mystery]). */
    val mysteryWeek: Long = -1,
    val mysteryId: String? = null,
    val mysteryFound: Boolean = false,
    val mysteryClaimed: Boolean = false,
    /** Mysteries solved and claimed, oldest first: species id to the week it was the mystery. */
    val mysteriesSolved: List<Pair<String, Long>> = emptyList(),
    /** Recent store demand per item (units, when last bought) for the day [demandDay] (see [Prices]). */
    val demand: Map<Item, Pair<Double, Long>> = emptyMap(),
    val demandDay: Long = -1,
    /** The Silver or Gold shrine that rose near you today (see [RoamingShrines]). */
    val roaming: RoamingShrine? = null,
    /** Torches for dungeons (see [Torches]), steps toward the next one, and the day the morning's were given. */
    val torches: Int = 0,
    val torchSteps: Int = 0,
    val torchGiftDay: Long = -1,
    /** How rested the hero is on [restDay] (see [Rest]). */
    val restDay: Long = -1,
    val rest: Rest = Rest.NORMAL,
    /** The dungeon you're in (see [Dungeons]), dungeons cleared (id -> day), and the day a champion last raised a stat. */
    val dungeonRun: DungeonRun? = null,
    val dungeonsCleared: Map<String, Long> = emptyMap(),
    val championStatDay: Long = -1,
    /** Stat points dungeon champions have given, per attribute (capped per star rank, see [Dungeons.CHAMPION_STAT_CAP]). */
    val championGains: Map<HeroAttribute, Int> = emptyMap(),
    /** The 0-star tutorial dungeon has been finished (it's one of a kind). */
    val tutorialDungeonDone: Boolean = false,
    /** The day a pharmacy last healed you, and a hospital last woke your monsters (see [Facilities]). */
    val infirmaryDay: Long = -1,
    val hospitalDay: Long = -1,
    /** Gym sessions: the day of the last ones and how many that day (see [XpRules.TRAINING_SHARES]). */
    val trainingDay: Long = -1,
    val trainingCount: Int = 0,
    /** The last day the hero studied at an academy (a school). */
    val academyDay: Long = -1,
    /** Trader offers bought ("traderId:index" -> day) and nomads beaten (id -> day). */
    val traderBought: Map<String, Long> = emptyMap(),
    val nomadsBeaten: Map<String, Long> = emptyMap(),
    val stepsToday: Int = 0,
    /** Steps per local hour (key: [Health.hourKey]), for the Health card's chart. About a week is kept. */
    val stepsByHour: Map<Long, Int> = emptyMap(),
    /** Glasses of water logged per epoch day (Health card; no rewards). */
    val water: Map<Long, Int> = emptyMap(),
    val waterGoal: Int = Health.DEFAULT_WATER_GOAL,
    /** The water goal each day was logged against (epoch day -> glasses), so changing the goal doesn't rewrite history. */
    val waterGoalByDay: Map<Long, Int> = emptyMap(),
    /** Nights of sleep by wake-up epoch day (Health card; no rewards). */
    val sleep: Map<Long, SleepNight> = emptyMap(),
    /** What each recent day brought (levels, XP, loot, healing, tasks), for the morning recap. */
    val tallies: Map<Long, DayTally> = emptyMap(),
    /** Epoch day a full night's sleep last refilled the hero's Energy. */
    val sleepEnergyDay: Long? = null,
    /** Epoch day the morning recap was last shown. */
    val recapShownDay: Long? = null,
    /** Epoch day [stepsToday] belongs to. */
    val stepsDay: Long? = null,
    /** Villain camp id -> epoch day defeated. */
    val villainsDefeated: Map<String, Long> = emptyMap(),
    /** Dens beaten (id to day); they stay empty until the dens move the next day. */
    val densCleared: Map<String, Long> = emptyMap(),
    /** A beaten den monster waiting for taming throws. */
    val pendingDenCatch: DenCatch? = null,
    /** Monsters of the last defeated villain team; one can be restored to join you. */
    val villainSpoils: List<Monster> = emptyList(),
    // Legacy fields from older saves, converted on load.
    val weapons: List<Weapon> = emptyList(),
    val equippedWeapon: Weapon? = null,
    /** Spell-book pages collected per spell id; [Spells.PAGES_TO_LEARN] teach the spell. */
    val spellPages: Map<String, Int> = emptyMap(),
    val weaponParts: Int = 0,
    /** The phone-free session in progress, if any. */
    val focus: FocusSession? = null,
    /** Loot boxes from phone-free sessions (max [MAX_LOOT_BOXES]); opened by walking. */
    val lootBoxes: List<LootBox> = emptyList(),
    /** Monsters currently out on adventures. */
    val adventures: List<ActiveAdventure> = emptyList(),
    /** Adventure id -> epoch day it was taken, so it isn't offered again. */
    val adventureLog: Map<String, Long> = emptyMap(),
    /** Last step-counter reading, to work out steps walked since. */
    val lastStepCount: Long? = null,
    val bagCapacity: Int = STARTING_BAG_CAPACITY,
    val monsterCapacity: Int = STARTING_MONSTER_CAPACITY,
    /** Bag and monster-storage expansions bought in the shop (each one costs more). */
    val bagExpansionsBought: Int = 0,
    val storageExpansionsBought: Int = 0,
    /** Storage upgrades earned (one per [UPGRADE_LEVEL_INTERVAL] levels) but not yet spent. */
    val pendingUpgrades: Int = 0,
    /** Earned by completing tasks; spent to start a wild encounter from anywhere. */
    val huntTokens: Int = 1,
    val shrines: Map<String, ShrineRecord> = emptyMap(),
    val stats: Lifetime = Lifetime(),
    val starterChosen: Boolean = false,
) {
    /** Everything the hero has on: body armor plus the other pieces. */
    val wornArmor: List<Armor> get() = listOfNotNull(equippedArmor) + equippedPieces.values

    fun worn(slot: ArmorSlot): Armor? = if (slot == ArmorSlot.BODY) equippedArmor else equippedPieces[slot]

    val bagCount: Int get() = inventory.values.sum() + gear.size + armor.size
    val bagFull: Boolean get() = bagCount >= bagCapacity
    val storageFull: Boolean get() = monsters.size >= monsterCapacity
    val partyMonsters: List<Monster> get() = party.mapNotNull { uid -> monsters.find { it.uid == uid } }
    val swapMonsters: List<Monster> get() = swaps.mapNotNull { uid -> monsters.find { it.uid == uid } }

    /** The party plus the swap slots: everyone who comes along to fights. */
    val team: List<String> get() = party + swaps
    fun count(item: Item): Int = inventory[item] ?: 0

    /** The hero's Energy right now. */
    val heroEnergy: Int get() = (hero.maxMp - heroEnergyUsed).coerceIn(0, hero.maxMp)

    /** Records every owned monster's species as caught (and seen) in the journal. */
    fun withJournal(): GameState {
        val owned = monsters.map { it.speciesId }.toSet()
        val shinies = monsters.filter { it.shiny }.map { it.speciesId }.toSet()
        if (caught.containsAll(owned) && seen.containsAll(caught) && shinyCaught.containsAll(shinies)) return this
        val c = caught + owned
        return copy(caught = c, seen = seen + c, shinyCaught = shinyCaught + shinies)
    }


    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }
        fun fromJson(text: String): GameState = json.decodeFromString(serializer(), text).migrated()
    }

    /** Brings older saves up to date (e.g. monsters from the previous roster). */
    fun migrated(): GameState {
        var s = this
        if (s.version < 2) {
            s = s.copy(
                version = 2,
                monsters = s.monsters.map { it.copy(speciesId = Bestiary.migrateId(it.speciesId)).evolved() },
            )
        }
        if (s.version < 3) {
            // Old fixed weapons become the new weapon types; untyped parts become sword parts.
            val rng = kotlin.random.Random(s.hero.level)
            s = s.copy(
                version = 3,
                gear = s.gear + s.weapons.map { Armory.fromLegacy(it, s.hero.level, rng) },
                equippedGear = s.equippedGear ?: s.equippedWeapon?.let { Armory.fromLegacy(it, s.hero.level, rng) },
                parts = if (s.weaponParts > 0) s.parts + (WeaponType.SWORD to (s.parts[WeaponType.SWORD] ?: 0) + s.weaponParts) else s.parts,
                weapons = emptyList(),
                equippedWeapon = null,
                weaponParts = 0,
            )
        }
        if (s.version < 4) {
            val zone = java.time.ZoneId.systemDefault()
            val days = s.tasks.mapNotNull { it.completedAtMs }
                .groupingBy { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }.eachCount()
            s = s.copy(
                version = 4,
                hero = s.hero.copy(classChosen = s.hero.heroClass != HeroClass.NONE),
                questDays = days + s.questDays,
                stepHistory = s.stepsDay?.let { s.stepHistory + (it to s.stepsToday) } ?: s.stepHistory,
            )
        }
        if (s.version < 5) {
            // Monster storage now starts at 20 instead of 6.
            s = s.copy(version = 5, monsterCapacity = s.monsterCapacity + STARTING_MONSTER_CAPACITY - 6)
        }
        if (s.version < 6) {
            // The starters changed (0.5): existing players get to pick one of the new ones.
            val starterFamilies = GameEngine.starters.map { Bestiary.get(it).family }.toSet()
            val hasOne = s.monsters.any { it.species.family in starterFamilies }
            s = s.copy(version = 6, starterGiftPending = s.starterChosen && !hasOne)
        }
        if (s.version < 7) {
            // 1.0: the hero's own magic. Old spells and pages turn into their new versions.
            val known = s.hero.knownSpells.map { Spells.legacy[it] ?: it }.filter { Spells.byId(it) != null }.toSet() + Spells.starting
            val ranks = s.hero.knownSpells.filter { it in Spells.legacyRank2 }.mapNotNull { Spells.legacy[it] }.associateWith { 2 }
            val pages = s.spellPages.entries
                .mapNotNull { (id, n) -> (Spells.legacy[id] ?: id).takeIf { Spells.byId(it) != null }?.let { it to n } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            s = s.copy(version = 7, hero = s.hero.copy(knownSpells = known, spellRanks = ranks), spellPages = pages)
        }
        if (s.version < 8) {
            // 2.29: one wolf and one hawk. Removed lines become the kept one, stage for stage.
            fun id(x: String) = Bestiary.removedIds[x] ?: x
            fun ids(xs: Set<String>) = xs.map(::id).toSet()
            val candy = s.candy.entries.groupBy({ Bestiary.removedFamilies[it.key] ?: it.key }, { it.value }).mapValues { it.value.sum() }
            s = s.copy(
                version = 8,
                monsters = s.monsters.map { it.copy(speciesId = id(it.speciesId)) },
                eggs = s.eggs.map { it.copy(speciesId = id(it.speciesId)) },
                candy = candy,
                seen = ids(s.seen),
                caught = ids(s.caught),
                shinyCaught = ids(s.shinyCaught),
                wanted = ids(s.wanted),
                mysteryId = s.mysteryId?.let(::id),
                mysteriesSolved = s.mysteriesSolved.map { (sp, w) -> id(sp) to w },
                villainSpoils = s.villainSpoils.map { it.copy(speciesId = id(it.speciesId)) },
            )
        }
        if (s.version < 9) {
            // 3.7: Ward is replaced by Aegis for everyone; Ward's rank and pages carry over.
            val wardRank = s.hero.spellRanks["ward"]
            val ranks = (s.hero.spellRanks - "ward").let { r ->
                if (wardRank != null) r + (Spells.AEGIS to maxOf(wardRank, r[Spells.AEGIS] ?: 1)) else r
            }
            val wardPages = s.spellPages["ward"] ?: 0
            val pages = (s.spellPages - "ward").let { p -> if (wardPages > 0) p + (Spells.AEGIS to (p[Spells.AEGIS] ?: 0) + wardPages) else p }
            s = s.copy(
                version = 9,
                hero = s.hero.copy(knownSpells = s.hero.knownSpells - "ward" + Spells.AEGIS, spellRanks = ranks),
                spellPages = pages,
            )
        }
        if (s.version < 10) s = levelReset(s)
        if (s.version < 11) {
            // 3.10: moves, a spell and a few achievements got new ids.
            fun mon(m: Monster) = m.copy(moves = m.moves?.map { Moves.renamedIds[it] ?: it })
            fun spell(id: String) = Spells.legacy[id]?.takeIf { id == "mud_shot" } ?: id
            val achievements = mapOf("dex_25" to "journal_25", "dex_60" to "journal_60", "dex_all" to "journal_all")
            s = s.copy(
                version = 11,
                monsters = s.monsters.map(::mon),
                villainSpoils = s.villainSpoils.map(::mon),
                pendingDenCatch = s.pendingDenCatch?.let { it.copy(monster = mon(it.monster)) },
                hero = s.hero.copy(
                    knownSpells = s.hero.knownSpells.map(::spell).toSet(),
                    spellRanks = s.hero.spellRanks.mapKeys { spell(it.key) },
                ),
                spellPages = s.spellPages.mapKeys { spell(it.key) },
                achievementsClaimed = s.achievementsClaimed.map { achievements[it] ?: it }.toSet(),
            )
        }
        if (s.version < 12) {
            // 3.20: lures are retired (wild monsters no longer roam the map); any held turn into gold.
            val lures = s.inventory[Item.LURE] ?: 0
            s = s.copy(
                version = 12,
                inventory = s.inventory - Item.LURE,
                gold = s.gold + lures * RETIRED_LURE_GOLD,
            )
        }
        return s.withJournal()
    }
}

@Serializable
data class Lifetime(
    val tasksCompleted: Int = 0,
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
    val monstersTamed: Int = 0,
    val shrinesVisited: Int = 0,
    val bossesDefeated: Int = 0,
    val focusWins: Int = 0,
    val focusFails: Int = 0,
    val focusMinutes: Int = 0,
    val totalSteps: Long = 0,
    val villainsBeaten: Int = 0,
    val dungeonsCleared: Int = 0,
    /** The longest phone-free session finished, in minutes. */
    val longestFocus: Int = 0,
    /** Monsters tamed of each type (a dual type counts for both). */
    val tamedByType: Map<Element, Int> = emptyMap(),
    val eggsHatched: Int = 0,
    /** Phone-free sessions of [Bounty.LONG_FOCUS_MIN] minutes or more finished. */
    val longFocusWins: Int = 0,
) {
    /** One more monster tamed. */
    fun tamed(m: Monster): Lifetime = copy(
        monstersTamed = monstersTamed + 1,
        tamedByType = m.species.types.fold(tamedByType) { acc, t -> acc + (t to (acc[t] ?: 0) + 1) },
    )
}

/** Gold for each lure held when lures were retired (save version 12). */
const val RETIRED_LURE_GOLD = 60

/** Heroes above this level were set back once (3.7.1), to [LEVEL_RESET_TO]. */
const val LEVEL_RESET_ABOVE = 50
const val LEVEL_RESET_TO = 25

/**
 * The one-time level reset (save version 10): a hero above level
 * [LEVEL_RESET_ABOVE] goes back to [LEVEL_RESET_TO]. Their stat points are
 * handed back to spend again (the levels' points, plus any a dungeon champion
 * gave), and monsters above the new limit (hero + 5) come down to it with the
 * XP they lose saved, to pay back as the hero levels up again. Gear is kept.
 */
internal fun levelReset(state: GameState): GameState {
    val h = state.hero
    if (h.level <= LEVEL_RESET_ABOVE) return state.copy(version = 10)
    // Points spent or waiting beyond what the levels gave came from champions: keep those.
    val spent = h.attributes.values.sumOf { it - STARTING_ATTRIBUTE }
    val fromChampions = (spent + h.unspentPoints - (h.level - 1) * STAT_POINTS_PER_LEVEL).coerceAtLeast(0)
    val hero = h.copy(
        level = LEVEL_RESET_TO,
        xp = 0,
        attributes = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE },
        unspentPoints = (LEVEL_RESET_TO - 1) * STAT_POINTS_PER_LEVEL + fromChampions,
    )
    val cap = XpRules.monsterCap(LEVEL_RESET_TO)
    val monsters = state.monsters.map { m ->
        if (m.level <= cap) return@map m
        val lost = (cap until m.level).sumOf { monsterXpForNext(it) } + m.xp
        m.copy(level = cap, xp = 0, heldXp = m.heldXp + lost, damage = 0)
    }
    // Hero skill slots open at level 30: without Kleptomaniac working, the second weapon goes back in the bag.
    val offhand = state.offhandGear?.takeIf { hero.skillRanks.none { it.first == Trait.KLEPTOMANIAC } }
    return state.copy(
        version = 10,
        hero = hero,
        monsters = monsters,
        gear = if (offhand != null) state.gear + offhand else state.gear,
        offhandGear = if (offhand != null) null else state.offhandGear,
        heroDamage = 0,
    )
}
