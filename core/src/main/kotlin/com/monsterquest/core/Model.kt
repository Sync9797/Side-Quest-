package com.monsterquest.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Types: a balanced wheel of seven, like rock-paper-scissors with seven
// throws. Every type is strong against exactly two others, weak to exactly
// two, and neutral to the remaining two:
//   Water    > Fire, Gas          rain douses fire and washes out gas
//   Fire     > Earth, Electric    burns grass, melts wiring
//   Corrupt  > Fire, Flight       darkness smothers flame and downs fliers
//   Gas      > Corrupt, Electric  toxic fumes choke the dark and short out sparks
//   Earth    > Water, Corrupt     soaks up water, buries the dark
//   Flight   > Earth, Gas         rises above the ground, blows gas away
//   Electric > Water, Flight      lightning conducts through water and strikes fliers
// ---------------------------------------------------------------------------

@Serializable
enum class Element(val label: String) {
    /** "Normal": an attack type only; no monster is Normal type. */
    NEUTRAL("Normal"),
    FIRE("Fire"),
    ELECTRIC("Electric"),
    FLIGHT("Flight"),
    WATER("Water"),
    GAS("Gas"),
    CORRUPT("Corrupt"),
    /** Earth and grass: stone, roots and leaves. */
    EARTH("Earth");

    /** The two types this type deals extra damage to. */
    val beats: List<Element>
        get() = when (this) {
            WATER -> listOf(FIRE, GAS)
            FIRE -> listOf(EARTH, ELECTRIC)
            CORRUPT -> listOf(FIRE, FLIGHT)
            GAS -> listOf(CORRUPT, ELECTRIC)
            EARTH -> listOf(WATER, CORRUPT)
            FLIGHT -> listOf(EARTH, GAS)
            ELECTRIC -> listOf(WATER, FLIGHT)
            NEUTRAL -> emptyList()
        }

    /** The two types that beat this type. */
    val weakTo: List<Element>
        get() = entries.filter { this in it.beats }

    /** Damage multiplier when an attack of this type hits [defender]. */
    fun multiplierAgainst(defender: Element): Double = when {
        defender in beats -> 1.5
        this in defender.beats -> 0.67
        else -> 1.0
    }

    /** Against a dual-type monster both matchups count: two weaknesses 2.25x, two resistances about 0.45x. */
    fun multiplierAgainst(defender: List<Element>): Double = defender.fold(1.0) { m, t -> m * multiplierAgainst(t) }

    companion object {
        val typed: List<Element> = entries.filter { it != NEUTRAL }
    }
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

@Serializable
enum class Stat(val label: String) {
    HP("HP"), ATTACK("Attack"), MAGIC("Magic"), DEFENSE("Defense"), SPEED("Speed")
}

const val SHADOW_DEFENSE = 0.5
const val SHADOW_ATTACK = 1.25

/** The stats monsters use (Magic is hero-only). */
val MONSTER_STATS: List<Stat> = listOf(Stat.HP, Stat.ATTACK, Stat.DEFENSE, Stat.SPEED)

@Serializable
data class StatBlock(
    val hp: Int = 0,
    val attack: Int = 0,
    val magic: Int = 0,
    val defense: Int = 0,
    val speed: Int = 0,
) {
    operator fun get(stat: Stat): Int = when (stat) {
        Stat.HP -> hp
        Stat.ATTACK -> attack
        Stat.MAGIC -> magic
        Stat.DEFENSE -> defense
        Stat.SPEED -> speed
    }

    companion object {
        fun random(rng: Random, max: Int) = StatBlock(
            hp = rng.nextInt(max + 1),
            attack = rng.nextInt(max + 1),
            magic = rng.nextInt(max + 1),
            defense = rng.nextInt(max + 1),
            speed = rng.nextInt(max + 1),
        )
    }
}

// ---------------------------------------------------------------------------
// Personalities: every monster rolls one, so two Ember Wolfcubs can play very
// differently (e.g. a Ferocious one hits hard but folds quickly, a Stalwart
// one is a slow wall).
// ---------------------------------------------------------------------------

@Serializable
enum class Personality(
    val label: String,
    private val mods: Map<Stat, Double>,
) {
    BALANCED("Balanced", emptyMap()),
    BRAVE("Brave", mapOf(Stat.ATTACK to 1.2, Stat.SPEED to 0.85)),
    FEROCIOUS("Ferocious", mapOf(Stat.ATTACK to 1.25, Stat.DEFENSE to 0.85)),
    RECKLESS("Reckless", mapOf(Stat.ATTACK to 1.2, Stat.SPEED to 1.1, Stat.DEFENSE to 0.8, Stat.HP to 0.9)),
    STALWART("Stalwart", mapOf(Stat.DEFENSE to 1.25, Stat.SPEED to 0.85)),
    STURDY("Sturdy", mapOf(Stat.HP to 1.15, Stat.DEFENSE to 1.1, Stat.ATTACK to 0.85)),
    HARDY("Hardy", mapOf(Stat.HP to 1.25, Stat.SPEED to 0.85)),
    NIMBLE("Nimble", mapOf(Stat.SPEED to 1.25, Stat.DEFENSE to 0.85)),
    HASTY("Hasty", mapOf(Stat.SPEED to 1.2, Stat.HP to 0.9)),
    CUNNING("Cunning", mapOf(Stat.MAGIC to 1.25, Stat.ATTACK to 0.85)),
    MYSTIC("Mystic", mapOf(Stat.MAGIC to 1.2, Stat.DEFENSE to 0.9)),
    LAZY("Lazy", mapOf(Stat.HP to 1.2, Stat.DEFENSE to 1.05, Stat.SPEED to 0.8));

    fun modifier(stat: Stat): Double = mods[stat] ?: 1.0

    /** e.g. "High Attack, low Speed". */
    val description: String
        get() {
            if (mods.isEmpty()) return "No strengths or weaknesses"
            val ups = mods.filterValues { it > 1.0 }.keys.map { it.label }
            val downs = mods.filterValues { it < 1.0 }.keys.map { it.label }
            val parts = ups.map { "high $it" } + downs.map { "low $it" }
            return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
        }
}

// ---------------------------------------------------------------------------
// Skills
// ---------------------------------------------------------------------------

@Serializable
enum class SkillKind {
    PHYSICAL, MAGICAL, HEAL,
    /** Hero spell: a barrier on one ally (or the hero) that soaks up damage. */
    SHIELD,
    /** Hero spell: a great barrier that soaks up a share (its power, in percent) of the hero's max HP. */
    AEGIS,
    /** Hero spell: raises one ally's Defense. */
    FORTIFY,
    /** Hero spell: raises one ally's Speed. */
    HASTE,
    /** Hero spell: raises one ally's Attack. */
    EMPOWER,
    /** Hero spell: clears one ally's status and lowered stats, and heals a little. */
    CLEANSE,
    /** Hero spell: heals every ally at once (less than Mend on one). */
    MEND_ALL,
}

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val element: Element,
    val kind: SkillKind,
    val power: Int,
    /** Energy (MP) cost for hero spells. Monster specials use a cooldown instead. */
    val mpCost: Int = 0,
    /** Hero spells: extra power for each rank above 1. */
    val powerPerRank: Int = 0,
    val description: String = "",
)

/**
 * Hero magic. Humans can't breathe fire or call lightning; the hero's spells
 * are scholars' imitations of monster magic. They're simpler and a little
 * weaker than real monster moves, and get stronger two ways: the hero's
 * mastery grows with level (full strength at [MASTERY_LEVEL]), and each spell
 * can be ranked up to [MAX_RANK] with spell-book pages and gold.
 *
 * Ember Bolt, Mend and Aegis are known from the start; the rest are learned by
 * collecting [PAGES_TO_LEARN] pages from item shrines.
 */
object Spells {
    const val PAGES_TO_LEARN = 3
    const val MAX_RANK = 5
    const val MASTERY_LEVEL = 25

    /** Mud Sling: Earth magic that lowers the foe's accuracy by [MUD_SLING_ACCURACY]% per hit, up to [MUD_SLING_STACKS] times. */
    const val MUD_SLING = "mud_sling"
    const val MUD_SLING_ACCURACY = 10
    const val MUD_SLING_STACKS = 5

    /** Aegis: a shield worth half your max HP, more at higher ranks (not lowered by mastery). */
    const val AEGIS = "aegis"
    const val AEGIS_BASE_PCT = 50
    const val AEGIS_PCT_PER_RANK = 10

    private fun bolt(id: String, name: String, element: Element, flavor: String) =
        Skill(id, name, element, SkillKind.MAGICAL, 24, mpCost = 10, powerPerRank = 3, description = flavor)

    val all = listOf(
        bolt("ember_bolt", "Ember Bolt", Element.FIRE, "A spark copied from a fire-drake's breath."),
        bolt("spark_bolt", "Spark Bolt", Element.ELECTRIC, "Static gathered the way a Voltfawn does."),
        bolt("tide_bolt", "Tide Bolt", Element.WATER, "A jet of water, pulled from the air like a Drizzlet."),
        bolt("gust_bolt", "Gust Bolt", Element.FLIGHT, "A blade of wind learned from watching wings."),
        bolt("venom_bolt", "Venom Bolt", Element.GAS, "A puff of fumes, a weak copy of a Toxbug's cloud."),
        bolt("shadow_bolt", "Shadow Bolt", Element.CORRUPT, "A sliver of the dark that gloomwisps carry."),
        bolt("stone_bolt", "Stone Bolt", Element.EARTH, "A thrown shard, shaped like a Mosscub's rock."),
        Skill(
            MUD_SLING, "Mud Sling", Element.EARTH, SkillKind.MAGICAL, 12, mpCost = 7, powerPerRank = 2,
            description = "A splat of mud in the foe's eyes: little damage, but its accuracy drops $MUD_SLING_ACCURACY% " +
                "(stacks up to ${MUD_SLING_ACCURACY * MUD_SLING_STACKS}%).",
        ),
        Skill(
            "mend", "Mend", Element.NEUTRAL, SkillKind.HEAL, 24, mpCost = 5, powerPerRank = 10,
            description = "Heals one ally or yourself.",
        ),
        Skill(
            "fortify", "Fortify", Element.NEUTRAL, SkillKind.FORTIFY, 1, mpCost = 4,
            description = "Raises one ally's Defense for the rest of the battle (more at higher ranks).",
        ),
        Skill(
            "haste", "Haste", Element.NEUTRAL, SkillKind.HASTE, 1, mpCost = 4,
            description = "Quickens one ally: its Speed rises for the rest of the battle (more at higher ranks).",
        ),
        Skill(
            "empower", "Empower", Element.NEUTRAL, SkillKind.EMPOWER, 1, mpCost = 5,
            description = "Strengthens one ally: its Attack rises for the rest of the battle (more at higher ranks).",
        ),
        Skill(
            "cleanse", "Cleanse", Element.NEUTRAL, SkillKind.CLEANSE, 10, mpCost = 4, powerPerRank = 6,
            description = "Washes one ally clean: cures sleep, shock, poison, burn and frostbite, undoes lowered stats, and heals a little.",
        ),
        Skill(
            AEGIS, "Aegis", Element.NEUTRAL, SkillKind.AEGIS, AEGIS_BASE_PCT, mpCost = 8, powerPerRank = AEGIS_PCT_PER_RANK,
            description = "A great shield on one ally or yourself that absorbs damage equal to $AEGIS_BASE_PCT% of your max HP " +
                "before it breaks (+$AEGIS_PCT_PER_RANK% per rank, up to ${AEGIS_BASE_PCT + AEGIS_PCT_PER_RANK * (MAX_RANK - 1)}%).",
        ),
        Skill(
            "healing_circle", "Healing Circle", Element.NEUTRAL, SkillKind.MEND_ALL, 14, mpCost = 9, powerPerRank = 6,
            description = "A ring of light under your whole team: heals every ally at once.",
        ),
    )

    val starting: Set<String> = setOf("ember_bolt", "mend", AEGIS)

    fun byId(id: String): Skill? = all.find { it.id == id }

    /** How well the hero has copied monster magic: 75% at level 1, 100% at [MASTERY_LEVEL]. */
    fun mastery(heroLevel: Int): Double =
        (0.75 + 0.25 * (heroLevel - 1) / (MASTERY_LEVEL - 1).toDouble()).coerceIn(0.75, 1.0)

    /** Spells whose power is a number of stat stages. */
    val STAGE_SPELLS = setOf(SkillKind.FORTIFY, SkillKind.HASTE, SkillKind.EMPOWER)

    /** Stages Fortify, Haste and Empower give: +1, +2 at rank 3, +3 at rank 5. */
    fun fortifyStages(rank: Int): Int = 1 + (rank - 1) / 2

    /**
     * Attack spells grow with the hero, the way monsters' moves get stronger
     * as they level: a Mage who puts most points into Magic hits a little
     * harder than a first-form monster of their level with a rank-3 bolt, and
     * about as hard as a fully evolved one at rank 5. Tuned against monsters'
     * best moves at each level (monster moves jump in power around level 30
     * and 40, so the scale does too).
     */
    fun spellScale(heroLevel: Int): Double {
        val l = heroLevel.coerceAtLeast(1).toDouble()
        val points = SPELL_SCALE
        val i = points.indexOfFirst { it.first >= l }
        if (i <= 0) return if (i == 0) points.first().second else points.last().second
        val (l0, s0) = points[i - 1]
        val (l1, s1) = points[i]
        return s0 + (s1 - s0) * (l - l0) / (l1 - l0)
    }

    /** Hero level -> attack spell multiplier (straight lines between the points). */
    private val SPELL_SCALE = listOf(1.0 to 3.9, 25.0 to 3.9, 30.0 to 4.6, 35.0 to 4.7, 40.0 to 5.8)

    /** Mages pay this much less Energy for attack spells, in percent. */
    const val MAGE_ATTACK_DISCOUNT_PCT = 25

    /** Energy [skill] costs a hero of [heroClass]: Mages cast attack spells for less. */
    fun energyCost(skill: Skill, heroClass: HeroClass): Int =
        if (heroClass == HeroClass.MAGE && skill.kind == SkillKind.MAGICAL) {
            (skill.mpCost * (100 - MAGE_ATTACK_DISCOUNT_PCT) / 100.0).roundToInt().coerceAtLeast(1)
        } else {
            skill.mpCost
        }

    /**
     * The spell as it works for this hero right now: power from rank and
     * mastery (and, for attack spells, level), and its Energy cost for
     * [heroClass].
     */
    fun ranked(skill: Skill, rank: Int, heroLevel: Int, heroClass: HeroClass = HeroClass.NONE): Skill {
        val r = rank.coerceIn(1, MAX_RANK)
        val base = skill.power + skill.powerPerRank * (r - 1)
        val power = when {
            skill.kind in STAGE_SPELLS -> fortifyStages(r)
            skill.kind == SkillKind.AEGIS -> base
            skill.kind == SkillKind.MAGICAL -> (base * mastery(heroLevel) * spellScale(heroLevel)).roundToInt()
            else -> (base * mastery(heroLevel)).roundToInt()
        }
        return skill.copy(power = power, mpCost = energyCost(skill, heroClass))
    }

    /** Pages needed to go from [rank] to the next rank. */
    fun pagesToUpgrade(rank: Int): Int = 3 * rank

    /** Gold needed to go from [rank] to the next rank. */
    fun goldToUpgrade(rank: Int): Int = 150 * rank

    /** Old (pre-1.0) spell ids and the new spell each became. */
    val legacy: Map<String, String> = mapOf(
        "fire_bolt" to "ember_bolt", "inferno" to "ember_bolt",
        "heal" to "mend", "mega_heal" to "mend",
        "thunderclap" to "spark_bolt", "tidal_lance" to "tide_bolt", "gale" to "gust_bolt",
        "venom_cloud" to "venom_bolt", "shadow_rend" to "shadow_bolt", "judgement" to "fortify",
        // 3.7: Ward became Aegis.
        "ward" to AEGIS,
        // 3.10: Mud Shot became Mud Sling.
        "mud_shot" to MUD_SLING,
    )

    /** The old spells that were the strong versions; they carry over as rank 2. */
    val legacyRank2: Set<String> = setOf("inferno", "mega_heal")
}

// ---------------------------------------------------------------------------
// Legacy weapons from older saves, converted to Gear on load. They added to the hero's
// Attack/Magic and gives their basic attack its type.
// ---------------------------------------------------------------------------

@Serializable
enum class Weapon(
    val label: String,
    val attack: Int,
    val magic: Int,
    val element: Element,
    val rarity: Rarity,
) {
    WOODEN_SWORD("Wooden Sword", 3, 0, Element.NEUTRAL, Rarity.COMMON),
    APPRENTICE_STAFF("Apprentice Staff", 0, 4, Element.NEUTRAL, Rarity.COMMON),
    IRON_SWORD("Iron Sword", 6, 0, Element.NEUTRAL, Rarity.UNCOMMON),
    FLAME_BLADE("Flame Blade", 8, 2, Element.FIRE, Rarity.RARE),
    STORM_SPEAR("Storm Spear", 8, 2, Element.ELECTRIC, Rarity.RARE),
    WIND_BOW("Wind Bow", 7, 3, Element.FLIGHT, Rarity.RARE),
    TIDE_TRIDENT("Tide Trident", 8, 2, Element.WATER, Rarity.RARE),
    VENOM_DAGGER("Venom Dagger", 7, 3, Element.GAS, Rarity.RARE),
    SHADOW_SCYTHE("Shadow Scythe", 10, 3, Element.CORRUPT, Rarity.EPIC),
    ARCANE_STAFF("Arcane Staff", 2, 10, Element.NEUTRAL, Rarity.EPIC);

    val description: String
        get() = buildList {
            if (attack > 0) add("+$attack Attack")
            if (magic > 0) add("+$magic Magic")
            if (element != Element.NEUTRAL) add("${element.label} attacks")
        }.joinToString(" • ")
}

@Serializable
enum class Gender(val label: String, val symbol: String) {
    MALE("Male", "\u2642"),
    FEMALE("Female", "\u2640");

    val opposite: Gender get() = if (this == MALE) FEMALE else MALE

    companion object {
        /** Even odds, decided by the monster's id. */
        fun of(uid: String): Gender = if (Math.floorMod(uid.hashCode() * 31 + 7, 2) == 0) MALE else FEMALE
    }
}

enum class Rarity(val label: String, val spawnWeight: Int, val captureMultiplier: Double) {
    COMMON("Common", 60, 1.0),
    UNCOMMON("Uncommon", 25, 0.85),
    RARE("Rare", 11, 0.65),
    EPIC("Epic", 4, 0.45),
    LEGENDARY("Legendary", 0, 0.3),
}

// ---------------------------------------------------------------------------
// Monster instances
// ---------------------------------------------------------------------------

const val MAX_IV = 15
const val MAX_MONSTER_LEVEL = 100

@Serializable
data class Monster(
    val uid: String,
    val speciesId: String,
    val personality: Personality,
    /** Individual values, 0..15 per stat: hidden genes that make each monster unique. */
    val ivs: StatBlock,
    val level: Int = 1,
    val xp: Int = 0,
    /** XP earned while [XpRules.MONSTER_LEAD] levels above the hero: it pays out as the hero levels up. */
    val heldXp: Int = 0,
    val nickname: String? = null,
    /** Move ids chosen by the player for the 4 slots; null = automatic best loadout. */
    val moves: List<String>? = null,
    /** Knocked out in battle; only a Restore Potion brings it back. */
    val fainted: Boolean = false,
    /** Damage carried between battles (healed by walking and potions). */
    val damage: Int = 0,
    /** Shadow curse (villain monsters): Defense halved, Attack +25%, until cured by battling. */
    val cursed: Boolean = false,
    val cureProgress: Int = 0,
    /** The curse just lifted: the player picks one stat to perfect. */
    val cureReady: Boolean = false,
    /** A rare color variant (about 1 in [SHINY_ODDS]). */
    val shiny: Boolean = false,
    /** The skill it gets at level 30, rolled from its line's pool when it was born (null: older saves keep their line's fixed skill). */
    val skill: Trait? = null,
    /** When it last made an egg (it rests a day after). */
    val bredAtMs: Long = 0,
    /** Born an Alpha in the wild: big, with perfect genes (see [Alphas]). */
    val alpha: Boolean = false,
    /** Bond points with the hero (see [Bonds]): from battles fought and walks taken together. */
    val bond: Int = 0,
) {
    val species: Species get() = Bestiary.get(speciesId)
    val bondLevel: Int get() = Bonds.level(bond)
    val displayName: String get() = nickname ?: species.name

    /**
     * Male or female, fixed from birth (it follows the monster's id, so older
     * saves and evolutions keep it); null for legendaries and mythics.
     */
    val gender: Gender? get() = if (species.genderless) null else Gender.of(uid)

    /**
     * Monster stats run well above the hero's: they're the real fighters.
     * Monsters use HP, Attack, Defense and Speed (Magic is a hero-only stat).
     */
    fun stat(stat: Stat): Int {
        // Monsters fight with one Attack stat; caster-style species use their Magic base for it.
        val base = if (stat == Stat.ATTACK) maxOf(species.base.attack, species.base.magic) else species.base[stat]
        val raw = base + ivs[stat]
        val value = if (stat == Stat.HP) {
            raw * level / 3.0 + level * 3 + 40
        } else {
            raw * level / 8.0 + 15
        }
        val curse = when {
            !cursed -> 1.0
            stat == Stat.DEFENSE -> SHADOW_DEFENSE
            stat == Stat.ATTACK -> SHADOW_ATTACK
            else -> 1.0
        }
        return floor(value * personality.modifier(stat) * curse).toInt().coerceAtLeast(1)
    }

    val currentHp: Int get() = if (fainted) 0 else (stat(Stat.HP) - damage).coerceAtLeast(1)

    /** Battles needed to lift the shadow curse: more for higher-level, rarer monsters. */
    val cureNeeded: Int get() = 3 + level / 8 + species.rarity.ordinal

    /** Every move this monster knows at its level. */
    val learnedMoves: List<Move> get() = Moves.learned(species, level)

    /** A wild one: fights with 4 random moves it knows (see [Moves.wildLoadout]), and keeps them if tamed. */
    fun withWildMoves(rng: kotlin.random.Random): Monster = copy(moves = Moves.wildLoadout(species, level, rng).map { it.id })

    /** The (up to) 4 moves it fights with. */
    val activeMoves: List<Move>
        get() {
            val learned = learnedMoves
            val chosen = moves?.mapNotNull { id -> learned.find { it.id == id } }?.take(Moves.MAX_MOVES)
            return if (chosen.isNullOrEmpty()) Moves.autoLoadout(species, level) else chosen
        }

    val stats: StatBlock
        get() = StatBlock(
            stat(Stat.HP), stat(Stat.ATTACK), stat(Stat.MAGIC), stat(Stat.DEFENSE), stat(Stat.SPEED),
        )

    /** Average IV as a 0-100% "potential" rating shown in the UI. */
    val potential: Int
        get() = (MONSTER_STATS.sumOf { ivs[it] } * 100) / (MAX_IV * MONSTER_STATS.size)

    val xpToNext: Int get() = monsterXpForNext(level)

    companion object {
        /** A new monster; it evolves straight away if [level] is past its evolution levels. */
        /** A new monster; [evolve] grows it into the form its level allows (off for a wild one already shown on the map). */
        fun create(species: Species, level: Int, rng: Random, shiny: Boolean = false, evolve: Boolean = true): Monster = Monster(
            uid = newId(rng),
            speciesId = species.id,
            personality = Personality.entries[rng.nextInt(Personality.entries.size)],
            ivs = StatBlock.random(rng, MAX_IV),
            level = level.coerceIn(1, MAX_MONSTER_LEVEL),
            shiny = shiny,
        ).let { if (evolve) it.evolved() else it }.let { m -> m.copy(skill = MonsterTraits.roll(m.species, Random(m.uid.hashCode().toLong()))) }
    }
}

/** About 1 in this many wild monsters is shiny. */
const val SHINY_ODDS = 200

fun rollShiny(rng: Random): Boolean = rng.nextInt(SHINY_ODDS) == 0

fun monsterXpForNext(level: Int): Int = ((20 + level * level * 5) * XP_CURVE).toInt()

/**
 * Adds XP to a monster, levelling it up as needed, but no higher than [cap]
 * (the hero's lead, see [XpRules]): XP past that is held for later.
 */
fun Monster.gainXp(amount: Int, cap: Int = MAX_MONSTER_LEVEL): Monster {
    val top = cap.coerceAtMost(MAX_MONSTER_LEVEL)
    if (level >= top && top < MAX_MONSTER_LEVEL) return copy(heldXp = heldXp + amount)
    var lvl = level
    var x = xp + amount
    while (lvl < top && x >= monsterXpForNext(lvl)) {
        x -= monsterXpForNext(lvl)
        lvl++
    }
    if (lvl >= MAX_MONSTER_LEVEL) return copy(level = lvl, xp = 0)
    if (lvl >= top) {
        // At the limit: the bar fills to just short of the next level; the rest waits.
        val room = monsterXpForNext(lvl) - 1
        if (x > room) return copy(level = lvl, xp = room, heldXp = heldXp + (x - room))
    }
    // Levelling never evolves a monster on its own: the player chooses to, for candy (see GameEngine.evolve).
    return copy(level = lvl, xp = x)
}

/** The species this monster can evolve into right now (its level is high enough), or null. */
val Monster.canEvolveTo: Species?
    get() {
        val next = species.evolvesTo ?: return null
        val at = species.evolveLevel ?: return null
        return if (level >= at) Bestiary.get(next) else null
    }

/** Applies every evolution this monster's level qualifies for. */
fun Monster.evolved(): Monster {
    var m = this
    while (true) {
        val next = m.species.evolvesTo ?: break
        val at = m.species.evolveLevel ?: break
        if (m.level < at) break
        m = m.copy(speciesId = next)
    }
    return m
}

fun newId(rng: Random = Random.Default): String =
    (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rng.nextInt(36)] }.joinToString("")

// ---------------------------------------------------------------------------
// Items
// ---------------------------------------------------------------------------

const val MASTER_ORB_SELL_PRICE = 200

@Serializable
enum class ItemKind { HEAL_HP, HEAL_MP, REVIVE, CAPTURE, TREAT, RESTORE, LURE, BAIT, PERSONALITY, FOOD, INCUBATOR }

@Serializable
enum class Item(
    val label: String,
    val kind: ItemKind,
    val power: Double,
    /** Shop price in gold; 0 = not sold. */
    val price: Int,
    val description: String,
    /** Hero level needed to buy it. */
    val minLevel: Int = 0,
) {
    POTION("Potion", ItemKind.HEAL_HP, 0.35, 25, "Restores 35% HP to one ally in battle."),
    @SerialName("HI_POTION") GREATER_POTION("Greater Potion", ItemKind.HEAL_HP, 0.8, 70, "Restores 80% HP to one ally in battle."),
    @SerialName("ETHER") MANA_DRAUGHT("Mana Draught", ItemKind.HEAL_MP, 20.0, 40, "Restores 20 Energy to the hero."),
    @SerialName("PHOENIX_DOWN") PHOENIX_FEATHER("Phoenix Feather", ItemKind.REVIVE, 0.5, 90, "Revives an ally that fell during this battle, at 50% HP."),
    RESTORE_POTION("Restore Potion", ItemKind.RESTORE, 1.0, 150, "Heals a knocked-out monster after battle. The only cure for fainting."),
    // Binding Crystals power the Binding Tome's capture spell: one per cast.
    // (Ids stay as they were so saves keep their crystals.)
    @SerialName("TAMING_ORB") BINDING_CRYSTAL("Binding Crystal", ItemKind.CAPTURE, 1.0, 30, "Powers one Binding cast to tame a weakened monster."),
    @SerialName("GREAT_ORB") RESONANT_CRYSTAL("Resonant Crystal", ItemKind.CAPTURE, 1.5, 80, "Better taming odds, and a little more time to trace.", minLevel = 10),
    @SerialName("MEGA_ORB") RADIANT_CRYSTAL("Radiant Crystal", ItemKind.CAPTURE, 2.0, 160, "Strong taming odds, and more time to trace.", minLevel = 25),
    @SerialName("ULTRA_ORB") PRISMATIC_CRYSTAL("Prismatic Crystal", ItemKind.CAPTURE, 2.6, 320, "Very strong taming odds, and even more time to trace.", minLevel = 50),
    @SerialName("MASTER_ORB") SOVEREIGN_CRYSTAL("Sovereign Crystal", ItemKind.CAPTURE, 3.0, 0, "Excellent taming odds. Found at boss shrines."),
    PERFECT_ORB(
        "Perfect Crystal", ItemKind.CAPTURE, 100.0, 2_000,
        "Always tames, even at full health, with no glyph to trace (not bosses you haven't beaten). Never found in the wild; one is the reward for reaching level 100.",
        minLevel = 75,
    ),
    TREAT("Monster Treat", ItemKind.TREAT, 100.0, 50, "Feed to a monster outside battle for a big XP boost."),
    /** Only from long phone-free sessions (60 to 120 minutes). */
    MOOD_CRYSTAL("Mood Crystal", ItemKind.PERSONALITY, 1.0, 0, "Gives a monster a new personality, at random. Earned from long phone-free sessions."),
    /** Retired in 3.20 (kept so older saves load; save version 12 turns any held into gold). */
    LURE("Lure", ItemKind.LURE, 30.0, 0, "Retired: wild monsters no longer roam the map."),
    INCUBATOR("Incubator", ItemKind.INCUBATOR, 0.5, 800, "Put one egg in it to halve the distance it needs to hatch. One use per incubator, one per egg."),
    // Foods: set one out and, for 30 minutes, monsters that eat it turn up far more often (see Foods). Power is the tier.
    SEED_MIX("Seed Mix", ItemKind.FOOD, 1.0, 60, "Grain and seeds for grazers, small birds and rodents."),
    ORCHARD_FRUIT("Orchard Fruit", ItemKind.FOOD, 1.0, 80, "Ripe fruit and honey for apes, bears and other omnivores."),
    WRIGGLE_GRUBS("Wriggle Grubs", ItemKind.FOOD, 1.0, 80, "A jar of grubs for bugs, spiders, frogs and insect-eaters."),
    FRESH_FISH("Fresh Fish", ItemKind.FOOD, 1.0, 100, "The day's catch for fish, turtles, crabs and seabirds."),
    RAW_MEAT("Raw Meat", ItemKind.FOOD, 1.0, 120, "Red meat for hunters: cats, hounds, owls and lizards."),
    RICH_COMPOST("Rich Compost", ItemKind.FOOD, 1.0, 80, "Leaf mold and rot for trees, fungi and slimes."),
    MINERAL_SALTS("Mineral Salts", ItemKind.FOOD, 1.0, 120, "Salt licks and crushed ore for rock eaters.", minLevel = 5),
    SPIRIT_INCENSE("Spirit Incense", ItemKind.FOOD, 1.0, 150, "Smoke that spirits and imps can't resist.", minLevel = 10),
    DRAGON_FEAST("Dragon's Feast", ItemKind.FOOD, 1.0, 400, "A roast fit for a dragon. Nothing less will do.", minLevel = 15),
    // Better tiers of each food, unlocked as you level up.
    SEED_MIX_FINE("Fine Seed Mix", ItemKind.FOOD, 2.0, 150, "Draws the same monsters as Seed Mix, harder, with rarer ones much more likely.", minLevel = 20),
    SEED_MIX_GOURMET("Gourmet Seed Mix", ItemKind.FOOD, 3.0, 360, "The best Seed Mix: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    ORCHARD_FRUIT_FINE("Fine Orchard Fruit", ItemKind.FOOD, 2.0, 200, "Draws the same monsters as Orchard Fruit, harder, with rarer ones much more likely.", minLevel = 20),
    ORCHARD_FRUIT_GOURMET("Gourmet Orchard Fruit", ItemKind.FOOD, 3.0, 480, "The best Orchard Fruit: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    WRIGGLE_GRUBS_FINE("Fine Wriggle Grubs", ItemKind.FOOD, 2.0, 200, "Draws the same monsters as Wriggle Grubs, harder, with rarer ones much more likely.", minLevel = 20),
    WRIGGLE_GRUBS_GOURMET("Gourmet Wriggle Grubs", ItemKind.FOOD, 3.0, 480, "The best Wriggle Grubs: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    FRESH_FISH_FINE("Fine Fresh Fish", ItemKind.FOOD, 2.0, 250, "Draws the same monsters as Fresh Fish, harder, with rarer ones much more likely.", minLevel = 20),
    FRESH_FISH_GOURMET("Gourmet Fresh Fish", ItemKind.FOOD, 3.0, 600, "The best Fresh Fish: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    RAW_MEAT_FINE("Fine Raw Meat", ItemKind.FOOD, 2.0, 300, "Draws the same monsters as Raw Meat, harder, with rarer ones much more likely.", minLevel = 20),
    RAW_MEAT_GOURMET("Gourmet Raw Meat", ItemKind.FOOD, 3.0, 720, "The best Raw Meat: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    RICH_COMPOST_FINE("Fine Rich Compost", ItemKind.FOOD, 2.0, 200, "Draws the same monsters as Rich Compost, harder, with rarer ones much more likely.", minLevel = 20),
    RICH_COMPOST_GOURMET("Gourmet Rich Compost", ItemKind.FOOD, 3.0, 480, "The best Rich Compost: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 40),
    MINERAL_SALTS_FINE("Fine Mineral Salts", ItemKind.FOOD, 2.0, 300, "Draws the same monsters as Mineral Salts, harder, with rarer ones much more likely.", minLevel = 25),
    MINERAL_SALTS_GOURMET("Gourmet Mineral Salts", ItemKind.FOOD, 3.0, 720, "The best Mineral Salts: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 45),
    SPIRIT_INCENSE_FINE("Fine Spirit Incense", ItemKind.FOOD, 2.0, 375, "Draws the same monsters as Spirit Incense, harder, with rarer ones much more likely.", minLevel = 30),
    SPIRIT_INCENSE_GOURMET("Gourmet Spirit Incense", ItemKind.FOOD, 3.0, 900, "The best Spirit Incense: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 50),
    DRAGON_FEAST_FINE("Fine Dragon's Feast", ItemKind.FOOD, 2.0, 1000, "Draws the same monsters as Dragon's Feast, harder, with rarer ones much more likely.", minLevel = 35),
    DRAGON_FEAST_GOURMET("Gourmet Dragon's Feast", ItemKind.FOOD, 3.0, 2400, "The best Dragon's Feast: a strong pull, and the rarest of its eaters are the likeliest to come.", minLevel = 55),
    // Berries: fed to a monster before a throw, like a Razz Berry. Only found in the wild.
    SWEET_BERRY(
        "Sweet Berry", ItemKind.BAIT, 1.5, 0,
        "Feed it to a monster before you cast Binding: your next cast is 1.5x as likely to tame it. Found in the wild.",
    ),
    GOLDEN_BERRY(
        "Golden Berry", ItemKind.BAIT, 2.5, 0,
        "A rare berry: your next Binding cast is 2.5x as likely to tame the monster. Found in the wild.",
    );

    val usableInBattle: Boolean get() = kind in setOf(ItemKind.HEAL_HP, ItemKind.HEAL_MP, ItemKind.REVIVE, ItemKind.CAPTURE, ItemKind.BAIT)

    /** What the shop pays: half the price (Sovereign Crystals, which aren't sold, fetch a fixed sum; berries a little). */
    val sellPrice: Int get() = when {
        price > 0 -> price / 2
        this == SWEET_BERRY -> 10
        this == GOLDEN_BERRY -> 40
        else -> MASTER_ORB_SELL_PRICE
    }
}
