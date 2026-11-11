package com.monsterquest.core

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EncounterType {
    /** A wild monster from the map or a hunt token. */
    WILD,
    /** First fight against a shrine boss: it must be defeated before it can be tamed. */
    BOSS_CHALLENGE,
    /** The boss has been beaten before, so taming is now allowed. */
    BOSS_TAMING,
    /** Silver shrine guardian (Tue/Thu): tough and harder to tame, but tameable right away. */
    SILVER_BOSS,
    /** Boss that bursts out of a Legendary loot box; tameable right away. */
    BOX_BOSS,
    /** A villain's shadow-cursed monster: can't be tamed. */
    VILLAIN,
    /** A den's strong monster: beat it first, then you get a few taming throws. */
    DEN,
    /** A thug and their monsters jump you: an even fight, and nothing can be tamed. */
    THUG,
    /** A wandering adventurer's challenge: 3 on 3, two swaps each. Nothing can be tamed. */
    NOMAD,
    /** A dungeon's cursed guardians: an even fight, and they can't be tamed. */
    DUNGEON_GUARD,
    /** The champion at the end of a dungeon (a hero of one of the classes) and their monsters. */
    DUNGEON_BOSS;

    /** Enemy HP multiplier. */
    val hpMultiplier: Double
        get() = when (this) {
            WILD, VILLAIN, THUG, NOMAD, DUNGEON_GUARD -> 1.0
            DUNGEON_BOSS -> 1.3
            DEN -> Dens.HP_MULTIPLIER
            SILVER_BOSS -> 2.2
            else -> 2.0
        }

    /** Multiplier on taming odds. */
    val captureMultiplier: Double
        get() = when (this) {
            SILVER_BOSS -> 0.6
            else -> 1.0
        }
}

enum class BattleOutcome { ONGOING, WON, LOST, CAPTURED, FLED }

sealed interface BattleAction {
    /** Hero's basic weapon attack. */
    data object Attack : BattleAction
    data object Defend : BattleAction
    data class CastSpell(val skillId: String, val targetKey: String? = null) : BattleAction
    /** A monster uses one of its 4 moves. */
    data class UseMove(val moveId: String) : BattleAction
    data class UseItem(val item: Item, val targetKey: String) : BattleAction
    /** Send in a monster from the swap slots, in place of [outKey] (null: into a free spot). */
    data class Swap(val inUid: String, val outKey: String?) : BattleAction
    /** Cast Binding with a crystal ([orb]); [grade] is how well its glyph was traced. */
    data class Capture(val orb: Item, val grade: TraceGrade = TraceGrade.GOOD) : BattleAction
    /**
     * Snap Bind: a gamble on the very first turn of a wild fight, before anyone
     * on your side has acted. A strong chance to tame the target outright
     * (whatever its health); the rest of the pack scatters. Miss, and the pack
     * doubles down: tougher and harder to tame for the rest of the fight.
     */
    data class SnapBind(val orb: Item) : BattleAction
    data object Flee : BattleAction
    /** A fainted monster's turn passes (when the player doesn't swap it out). */
    data object Skip : BattleAction
}

class Combatant(
    /** "hero", "enemy", or the monster's uid. */
    val key: String,
    val name: String,
    val element: Element,
    val level: Int,
    val maxHp: Int,
    var hp: Int,
    val attack: Int,
    val magic: Int,
    val defense: Int,
    val speed: Int,
    val maxMp: Int,
    var mp: Int,
    val isHero: Boolean,
    /** Monster moves (empty for the hero, who uses Attack and spells). */
    val moves: List<Move>,
) {
    /** Uses left of each move this battle (PP), by move id. */
    val pp: MutableMap<String, Int> = moves.associate { it.id to it.maxPp }.toMutableMap()

    fun ppLeft(m: Move): Int = pp[m.id] ?: m.maxPp

    /** Moves with uses left; Struggle when there are none. */
    val usableMoves: List<Move> get() = moves.filter { ppLeft(it) > 0 }.ifEmpty { if (moves.isEmpty()) emptyList() else listOf(Moves.STRUGGLE) }

    /** After a turn: [used] costs one use; every other move gets one back. */
    fun spendTurn(used: Move?) {
        for (m in moves) {
            pp[m.id] = if (m.id == used?.id) ppLeft(m) - 1 else minOf(m.maxPp, ppLeft(m) + 1)
        }
    }

    /** For artwork: the monster's species, or the hero's class. */
    var speciesId: String? = null
    /** A dual-type monster's second type. */
    var element2: Element? = null
    /** One or two types, for matchups and the same-type bonus. */
    val types: List<Element> get() = listOfNotNull(element, element2)
    /** An enemy thug (a person fighting with their monsters). */
    var thug: Boolean = false
    var shiny: Boolean = false
    var heroClass: HeroClass? = null

    /** Chance to dodge incoming attacks (Rogue hero). */
    var evasion: Double = 0.0

    // Weapon effects (hero only).
    var hits: Int = 1
    var energyRegen: Int = 0
    var critBonus: Double = 0.0
    var lifesteal: Double = 0.0
    var onHitStatus: Status? = null
    var tameBonus: Double = 0.0
    var cursed: Boolean = false

    // Subclass perks (hero only).
    var spellPct: Int = 0
    var healPct: Int = 0
    var spellDrainPct: Int = 0
    var critDamagePct: Int = 0
    var ragePct: Int = 0
    var blindPct: Int = 0

    // Skills: the fighter's own, at their rank, and what they (or a teammate's) give it.
    val traits: MutableMap<Trait, Int> = mutableMapOf()
    var immuneEarth: Boolean = false
    /** Hovering only because of the hero's maxed Hover (it breaks when the hero is hit). */
    var borrowedHover: Boolean = false
    /** Knocked down by Knockdown: Earth moves hit it for the rest of the battle. */
    var grounded: Boolean = false
    /** Less damage from Earth moves, in percent (a hovering hero's team). */
    var earthResistPct: Int = 0
    var unflappable: Boolean = false
    /** Turns of sleep shaken off; [NO_SLEEP] means it can't fall asleep. */
    var sleepCut: Int = 0
    var regenPct: Int = 0
    var secondWindPct: Int = 0
    var secondWindUsed: Boolean = false
    var hidePct: Int = 0
    var touchStatus: Status? = null
    var touchPct: Int = 0
    /** Kindling: it absorbed fire, so its Fire moves hit harder. */
    var kindled: Boolean = false
    var stalwartUsed: Boolean = false
    /** Bond 5 (see [Bonds]): once a battle it survives a knockout blow with 1 HP. */
    var steadfast: Boolean = false
    var steadfastUsed: Boolean = false

    // The off-hand weapon (Kleptomaniac): a second strike after each weapon attack.
    /** Power of the off-hand strike, in percent of a normal one (0: no off hand). */
    var offhandPct: Int = 0
    var offhandElement: Element = Element.NEUTRAL
    var offhandHits: Int = 1

    /** A monster skill's rank, or 0 without it. */
    fun rankOf(t: Trait): Int = traits[t] ?: 0

    // Cursed gear (hero only).
    var weaponCurse: Curse? = null
    /** The weapon is still cursed: 25% less damage. */
    var weaponCursed: Boolean = false
    /** Curses on worn armor: they strike back at physical attackers. */
    var armorCurses: List<Curse> = emptyList()
    /** Max HP lost each turn while wearing or wielding cursed gear, in percent. */
    var curseDrainPct: Int = 0
    /** Weapon hits landed and physical hits taken (they wear the curses down). */
    var weaponUses: Int = 0
    var armorHits: Int = 0

    /** Applies a skill's effect on its owner. */
    fun gainTrait(t: Trait, rank: Int, hero: Boolean) {
        traits[t] = rank
        when (t) {
            Trait.WAKEFUL -> sleepCut = maxOf(sleepCut, if (rank >= MAX_SKILL_RANK) NO_SLEEP else t.sleepCut(rank))
            Trait.HOVER, Trait.AIRBORNE -> immuneEarth = true
            Trait.UNFLAPPABLE -> unflappable = true
            Trait.QUICK_STEP -> evasion += t.dodgePct(rank) / 100.0
            Trait.SECOND_WIND -> secondWindPct = maxOf(secondWindPct, t.secondWindPct(rank))
            Trait.REGENERATION -> regenPct += t.regenPct(rank)
            Trait.THICK_HIDE -> hidePct = t.hidePct(rank)
            Trait.SPORE_COAT -> touchPct = t.touchPct(rank)
            Trait.CRACKLE_SKIN, Trait.EMBER_HIDE, Trait.VENOM_BARBS -> {
                touchStatus = when (t) {
                    Trait.CRACKLE_SKIN -> Status.SHOCK
                    Trait.EMBER_HIDE -> Status.BURN
                    else -> Status.POISON
                }
                touchPct = t.touchPct(rank)
            }
            else -> {}
        }
    }

    /** What a hero skill at team range gives a teammate. */
    fun shareTrait(t: Trait, rank: Int) {
        when (t) {
            Trait.WAKEFUL -> sleepCut = maxOf(sleepCut, t.sleepCut(rank.coerceAtMost(MAX_SKILL_RANK - 1)))
            Trait.HOVER -> {
                earthResistPct = maxOf(earthResistPct, t.hoverTeamPct(rank))
                // Maxed: the whole team hovers too, until the hero takes a hit.
                if (rank >= MAX_SKILL_RANK && !immuneEarth) {
                    immuneEarth = true
                    borrowedHover = true
                }
            }
            Trait.UNFLAPPABLE -> unflappable = true
            Trait.QUICK_STEP -> evasion += t.dodgePct(rank) / 200.0
            Trait.SECOND_WIND -> secondWindPct = maxOf(secondWindPct, t.secondWindPct(rank))
            Trait.REGENERATION -> regenPct += t.regenPct(rank)
            else -> {}
        }
    }
    /** Type of the hero's basic Attack (from their weapon). */
    var attackElement: Element = Element.NEUTRAL
    /** Shield up (Defend): no damage until its next turn. */
    var defending: Boolean = false
    /** Defended twice in a row: the shield only halves damage this time. */
    var bracing: Boolean = false
    /** Its last turn was a Defend. */
    var defendedLast: Boolean = false

    // ---- conditions ----
    var status: Status? = null
    var statusTurns: Int = 0
    var nightTerrorDamage: Int = 0
    var scorchTurns: Int = 0
    var scorchDamage: Int = 0
    var scorchVerb: String = "is scorched"
    /** Next move has a 40% chance to miss. */
    var accuracyDown: Boolean = false
    /** Mud Sling: accuracy lowered for the rest of the battle, in percent (stacks). */
    var muddiedPct: Int = 0
    /** Next move misses. */
    var missNext: Boolean = false
    var flinch: Boolean = false
    var mustRecharge: Boolean = false
    var protecting: Boolean = false
    /** Ward: damage soaked up before HP is touched. */
    var shield: Int = 0
    var usedProtectLast: Boolean = false
    /** Mid-dive (Dive Bomb turn 1): out of reach until it strikes. */
    var diving: Move? = null
    val stages: MutableMap<BattleStat, Int> = mutableMapOf()
    /** Iron Wing stacks, in percent. */
    var powerUp: Int = 0
    /** Scared or Menaced: Attack drops to [SCARED_ATTACK] (66.7%) for the battle. Doesn't stack. */
    var scared: Boolean = false
    /** Swift (Water moves): moves first next round. */
    var swift: Boolean = false
    /** A pack member that left the fight without being beaten: "Tamed" or "Ran off". */
    var gone: String? = null
    /** Rage stacks: a stronger pack member gets one each time you tame one of its pack. */
    var rage: Int = 0

    val alive: Boolean get() = hp > 0 && gone == null
    val hpFraction: Double get() = hp.toDouble() / maxHp

    fun stageMultiplier(stat: BattleStat): Double = (1.0 + 0.15 * (stages[stat] ?: 0)).coerceIn(0.4, 2.0)
    /** Speed after stat stages; a shocked fighter moves at half speed (like paralysis). */
    val effectiveSpeed: Double get() = speed * stageMultiplier(BattleStat.SPEED) * (if (status == Status.SHOCK) SHOCK_SPEED else 1.0)

    /** Short labels for the UI, e.g. "Asleep", "Def ↓1". */
    fun tags(): List<String> = buildList {
        gone?.let { add(it) }
        if (cursed) add("Shadow-cursed")
        if (rage > 0) add("Enraged" + if (rage > 1) " x$rage" else "")
        status?.let { add(it.label) }
        if (scorchTurns > 0) add("Scorched")
        stages.forEach { (s, n) -> if (n != 0) add("${s.label.take(3)} ${if (n < 0) "↓" else "↑"}${kotlin.math.abs(n)}") }
        if (powerUp > 0) add("Power +$powerUp%")
        if (scared) add("Scared (Atk 67%)")
        if (swift) add("Moves first")
        if (immuneEarth) add("Hovering")
        if (grounded) add("Grounded")
        traits.forEach { (t, r) -> add("${t.label} ${"★".repeat(r)}") }
        if (diving != null) add("In the air")
        if (protecting) add("Guarding")
        if (defending) add("Shield up")
        if (shield > 0) add("Shield $shield")
        if (mustRecharge) add("Recharging")
        if (accuracyDown || missNext) add("Blinded")
        if (muddiedPct > 0) add("Acc ↓$muddiedPct%")
    }

    companion object {
        const val HERO_KEY = "hero"
        const val ENEMY_KEY = "enemy"
        const val NO_SLEEP = 99

        /** Keys for a pack's monsters: "enemy", "enemy2", "enemy3", "enemy4". */
        fun enemyKey(index: Int) = if (index == 0) ENEMY_KEY else "$ENEMY_KEY${index + 1}"

        fun isEnemy(key: String) = key.startsWith(ENEMY_KEY)

        fun fromHero(
            h: Hero,
            gear: Gear? = null,
            damage: Int = 0,
            armor: List<Armor> = emptyList(),
            /** Second weapon (Kleptomaniac): part of its power counts. */
            offhand: Gear? = null,
            /** Energy already spent before this battle (it doesn't come back between fights). */
            energyUsed: Int = 0,
            /** Today's rest: scales Attack, Magic, Defense and Speed. */
            restFactor: Double = 1.0,
        ): Combatant {
            val cls = h.heroClass
            val perks = h.perks
            val klepto = h.rankOf(Trait.KLEPTOMANIAC)
            val off = offhand?.takeIf { klepto > 0 }
            val share = if (off != null) Trait.KLEPTOMANIAC.offhandPct(klepto) / 100.0 else 0.0
            val fx = gear?.effects.orEmpty() + armor.flatMap { it.effects } + (if (klepto >= 3) off?.effects.orEmpty() else emptyList())
            // The class bonus (or the penalty for an unfamiliar weapon) goes on every stat.
            val classStats = (gear?.classStats(cls, perks.anyWeapon) ?: 0) + ((off?.classStats(cls, perks.anyWeapon) ?: 0) * share).roundToInt()
            // The off hand's Attack goes into its own strike (see offhandPct), not the main one.
            val offMagic = ((off?.magicBonus(cls) ?: 0) * share).roundToInt()
            val speedPct = (gear?.type?.speedPercent ?: 0) + armor.sumOf { it.type.speedPercent } + 10 * fx.count { it == GearEffect.SWIFT }
            val baseDefense = h.defense + armor.sumOf { it.defenseBonus(cls) } + classStats
            val defense = (baseDefense * (1 + 0.1 * fx.count { it == GearEffect.GUARDED })).toInt().coerceAtLeast(1)
            val attack = (h.attack + (gear?.attackBonus(cls) ?: 0) + armor.sumOf { it.attackBonus(cls) } + classStats) *
                (1 + 0.1 * fx.count { it == GearEffect.MIGHTY })
            val magic = (h.magic + (gear?.magicBonus(cls) ?: 0) + offMagic + armor.sumOf { it.magicBonus(cls) } + classStats) *
                (1 + 0.1 * fx.count { it == GearEffect.FOCUSED })
            return Combatant(
                HERO_KEY, h.name, Element.NEUTRAL, h.level, h.maxHp, (h.maxHp - damage).coerceIn(1, h.maxHp),
                (attack * restFactor).toInt().coerceAtLeast(1), (magic * restFactor).toInt().coerceAtLeast(1),
                (defense * restFactor).toInt().coerceAtLeast(1), ((h.speed + classStats) * (1 + speedPct / 100.0) * restFactor).toInt().coerceAtLeast(1), h.maxMp,
                (h.maxMp - energyUsed).coerceIn(0, h.maxMp), true, emptyList(),
            ).apply {
                attackElement = gear?.element ?: Element.NEUTRAL
                evasion = h.evasion + armor.sumOf { it.type.evasionPercent } / 100.0 + perks.dodgePct / 100.0
                heroClass = h.heroClass
                hits = gear?.type?.hits ?: 1
                if (off != null) {
                    // The off hand's own Attack bonus makes its strike a little stronger.
                    val offBonus = off.attackBonus(cls).coerceAtLeast(0)
                    offhandPct = (Trait.KLEPTOMANIAC.offhandPct(klepto) * (1 + offBonus / (h.attack + 20.0) / 2)).roundToInt()
                    offhandElement = off.element
                    offhandHits = off.type.hits
                }
                energyRegen = (gear?.type?.energyPerTurn ?: 0) + 2 * fx.count { it == GearEffect.ARCANE } + perks.energyPerTurn
                critBonus = (if (GearEffect.KEEN in fx) 0.1 else 0.0) + perks.critPct / 100.0
                lifesteal = 0.15 * fx.count { it == GearEffect.VAMPIRIC }
                onHitStatus = when {
                    GearEffect.SEARING in fx -> Status.BURN
                    GearEffect.CRACKLING in fx -> Status.SHOCK
                    GearEffect.VENOMOUS in fx -> Status.POISON
                    else -> null
                }
                tameBonus = 0.1 * fx.count { it == GearEffect.LUCKY } + perks.tamePct / 100.0
                weaponCurse = gear?.curse
                weaponCursed = gear?.cursed == true
                armorCurses = armor.mapNotNull { it.curse }
                curseDrainPct = CURSE_DRAIN_PCT * ((if (gear?.cursed == true) 1 else 0) + armor.count { it.cursed })
                spellPct = perks.spellPct
                healPct = perks.healPct
                spellDrainPct = perks.spellDrainPct
                critDamagePct = perks.critDamagePct
                ragePct = perks.ragePct
                blindPct = perks.blindPct
                regenPct = perks.regenPct
                if (perks.startWardPct > 0) shield = maxHp * perks.startWardPct / 100
                h.skillRanks.forEach { (t, r) -> gainTrait(t, r, hero = true) }
            }
        }

        /**
         * A thug: about as tough as a monster of their level, fighting their own way.
         * Brutes hit hard and take a beating but are slow; Hexers cast dark magic
         * but are frail; Cutthroats are quick, dodge, and fight dirty.
         */
        fun fromThug(t: Thug, key: String): Combatant {
            val lv = t.level
            data class Build(val hp: Int, val atk: Int, val def: Int, val spd: Int, val element: Element, val moves: List<String>, val dodge: Double)
            val b = when (t.cls) {
                HeroClass.WARRIOR -> Build(70 + lv * 12, 18 + lv * 2, 15 + lv * 2, 8 + lv * 3 / 4, Element.NEUTRAL, listOf("skull_ram", "ground_pound", "crushing_weight", "fang_snap"), 0.0)
                HeroClass.MAGE -> Build(45 + lv * 8, 18 + lv * 5 / 2, 10 + lv, 12 + lv, Element.CORRUPT, listOf("hex_barrage", "dark_wave", "night_terror", "soul_drain"), 0.0)
                else -> Build(50 + lv * 9, 16 + lv * 2, 12 + lv * 3 / 2, 14 + lv * 5 / 4, Element.NEUTRAL, listOf("sucker_punch", "dusk_slash", "frenzy_swipes", "scare"), 0.1)
            }
            return Combatant(
                key, t.title, b.element, lv, b.hp, b.hp, b.atk, 0, b.def, b.spd, 0, 0, false, b.moves.map { Moves.get(it) },
            ).apply {
                thug = true
                heroClass = t.cls
                // Brutes glare their foes down as the fight starts.
                if (t.cls == HeroClass.WARRIOR) gainTrait(Trait.MENACE, 1, hero = false)
                evasion = b.dodge
            }
        }

        fun fromMonster(
            m: Monster,
            key: String = m.uid,
            hpMultiplier: Double = 1.0,
            attackMultiplier: Double = 1.0,
            defenseMultiplier: Double = 1.0,
            speedMultiplier: Double = 1.0,
        ): Combatant {
            val s = m.stats
            val hp = (s.hp * hpMultiplier).toInt()
            val atk = (s.attack * attackMultiplier).toInt()
            return Combatant(
                key, m.displayName, m.species.element, m.level, hp, (hp - m.damage).coerceIn(1, hp),
                atk, atk, (s.defense * defenseMultiplier).toInt(), (s.speed * speedMultiplier).toInt().coerceAtLeast(1), 0, 0, false, m.activeMoves,
            ).apply {
                speciesId = m.speciesId
                element2 = m.species.secondary
                cursed = m.cursed
                shiny = m.shiny
                m.trait?.let { (t, r) -> gainTrait(t, r, hero = false) }
            }
        }
    }
}

/** How an attack is shown on screen. */
enum class StrikeStyle {
    /** Weapons and Normal-type moves: slash marks across the target. */
    SLASH,
    /** Spells and typed moves: a projectile of the move's type flies at the target. */
    BOLT,
    HEAL,
    MISS,
    /** A protective spell: Ward or Fortify on an ally. */
    WARD,
}

/** One thing that happened in battle, for the UI to animate (in [seq] order). */
data class BattleEvent(
    val seq: Int,
    val actorKey: String,
    val targetKey: String,
    val style: StrikeStyle,
    val element: Element,
    val amount: Int = 0,
    val crit: Boolean = false,
    val superEffective: Boolean = false,
)

/**
 * Enemies grow with the size of your team (the hero plus up to 5 monsters),
 * so a full team of 6 faces a real fight while a lone hero isn't overwhelmed.
 * Bosses, dens' rivals and villains are built for a team of 4: they get easier
 * against smaller teams and tougher (and pay more) against 5 or 6.
 */
object TeamScaling {
    data class Factors(val hp: Double, val attack: Double, val defense: Double)

    /**
     * Wild monsters and dens grow with your team. Bosses and villains are built
     * for a full team of 4 and scale from there; in a matched fight (where your
     * side is as big as theirs) they scale like a wild monster instead.
     */
    fun factors(teamSize: Int, encounter: EncounterType, fullTeam: Boolean = true): Factors =
        if (encounter == EncounterType.WILD || encounter == EncounterType.DEN || !fullTeam) {
            Factors(hp(teamSize), attack(teamSize), defense(teamSize))
        } else {
            Factors(hp(teamSize) / hp(4), attack(teamSize) / attack(4), defense(teamSize) / defense(4))
        }

    // A lone foe always faces the hero and a monster (2 on your side), scaled to make it a real fight (3.6).
    private val hpByTeam = listOf(0.35, 1.2, 2.6, 4.2, 5.6, 7.0)
    private val attackByTeam = listOf(0.45, 1.0, 1.6, 2.0, 2.3, 2.55)
    private val defenseByTeam = listOf(1.0, 1.0, 1.1, 1.2, 1.25, 1.3)

    private fun pick(table: List<Double>, teamSize: Int) = table[(teamSize - 1).coerceIn(0, table.size - 1)]

    fun hp(teamSize: Int) = pick(hpByTeam, teamSize)
    fun attack(teamSize: Int) = pick(attackByTeam, teamSize)
    fun defense(teamSize: Int) = pick(defenseByTeam, teamSize)
    private val rewardByTeam = listOf(0.6, 1.25, 2.0, 2.75, 3.4, 4.0)

    /** Bosses and villains pay more when they had to scale up for a team bigger than 4. */
    fun bossReward(teamSize: Int) = (reward(teamSize) / reward(4)).coerceAtLeast(1.0)

    /**
     * Rewards follow the enemy's strength: a lone hero faces a weaker monster
     * for less, a full team faces a much stronger one for much more.
     */
    fun reward(teamSize: Int) = pick(rewardByTeam, teamSize)

    // Packs: the team-scaled strength is shared out among the pack, with a
    // little extra in total (more monsters means more turns against you).
    private val packHp = listOf(1.0, 0.45, 0.32, 0.25)
    private val packAttack = listOf(1.0, 0.5, 0.36, 0.28)
    /** Packs pay more in total than a single monster. */
    private val packReward = listOf(1.0, 1.4, 1.75, 2.1)

    fun pack(f: Factors, count: Int): Factors {
        val i = (count - 1).coerceIn(0, 3)
        return Factors(f.hp * packHp[i], f.attack * packAttack[i], f.defense)
    }

    fun packReward(count: Int) = packReward[(count - 1).coerceIn(0, 3)]
}

data class BattleResult(
    val outcome: BattleOutcome,
    val encounter: EncounterType,
    val enemy: Monster,
    val heroXp: Int,
    /** Gold won (positive). Losses are applied as a percentage by [GameEngine]. */
    val goldWon: Int,
    val monsterXp: Map<String, Int>,
    val itemsUsed: Map<Item, Int>,
    val drops: Map<Item, Int>,
    val captured: Monster?,
    val shrineId: String?,
    /** Party monsters knocked out at the end of the battle; they stay down until restored. */
    val faintedUids: Set<String> = emptySet(),
    /** Missing HP per surviving ally key ("hero" or a monster uid), carried into the next battle. */
    val damage: Map<String, Int> = emptyMap(),
    val heroFell: Boolean = false,
    /** Energy the hero has spent by the end of the battle. */
    val heroEnergyUsed: Int = 0,
    /** Weapon hits landed and physical hits taken: they wear curses down. */
    val weaponUses: Int = 0,
    val armorHits: Int = 0,
    /** Species of every monster beaten (each gives candy of its line). */
    val defeated: List<String> = emptyList(),
    /** Your monsters still standing at the end (each one earns an extra taming throw at a den). */
    val standing: Int = 0,
    /** Every monster tamed in this fight ([captured] is the first). */
    val tamed: List<Monster> = listOfNotNull(captured),
    /** A Snap Bind landed: the rest of the pack scattered (and now flees from you on the map). */
    val scattered: Boolean = false,
    /** Your monsters who stepped into the fight (their bond grows). */
    val fought: Set<String> = emptySet(),
)

/** " (strong trace)" for the battle log; nothing for a Perfect Crystal, which needs no trace. */
private fun traceNote(a: BattleAction.Capture) = if (Binding.needsTrace(a.orb)) " (${a.grade.label.lowercase()} trace)" else ""

/** Every taming cast has this chance to be a critical catch: tamed, whatever the odds. */
const val CRITICAL_CATCH = 0.02

/** Snap Bind odds by rarity (for a Binding Crystal), before the crystal and level gap. */
val SNAP_BIND_ODDS = mapOf(Rarity.COMMON to 0.40, Rarity.UNCOMMON to 0.32, Rarity.RARE to 0.22, Rarity.EPIC to 0.12, Rarity.LEGENDARY to 0.05)
/** Highest Snap Bind odds, whatever the crystal. */
const val SNAP_BIND_MAX = 0.65
/** A missed Snap Bind: taming odds for the rest of the fight are multiplied by this. */
const val SNAP_FAIL_CATCH = 0.5

/** The real chance of a throw with base odds [p], counting the critical catch. */
fun withCriticalCatch(p: Double): Double = if (p >= 1.0) 1.0 else CRITICAL_CATCH + (1 - CRITICAL_CATCH) * p

/** Chance (0..1) that throwing [orb] tames the enemy right now. 0 = not allowed. */
fun captureChance(hpFraction: Double, rarity: Rarity, levelDiff: Int, orb: Item): Double {
    if (hpFraction > 0.5) return 0.0
    val base = (1.0 - hpFraction * 1.6) * 0.9
    val levelPenalty = 0.92.pow(max(0, levelDiff))
    return (base * rarity.captureMultiplier * orb.power * levelPenalty).coerceIn(0.03, 0.95)
}

/** Hero spells aimed at an ally (or the hero) instead of an enemy. */
val SUPPORT_KINDS = setOf(SkillKind.HEAL, SkillKind.SHIELD, SkillKind.AEGIS, SkillKind.FORTIFY, SkillKind.HASTE, SkillKind.EMPOWER, SkillKind.CLEANSE, SkillKind.MEND_ALL)

private val TOUCH_TRAITS = setOf(Trait.CRACKLE_SKIN, Trait.EMBER_HIDE, Trait.VENOM_BARBS, Trait.SPORE_COAT)

/** Chance (percent) a cursed Frost or Flame piece afflicts the foe. */
const val CURSE_STATUS_PCT = 35
/** A cursed Life Drain weapon heals this share of the damage it deals (percent). */
const val CURSE_DRAIN_HEAL = 30
/** Cursed Life Drain armor steals this share of the damage taken back from the attacker (percent). */
const val CURSE_ARMOR_STEAL = 10

/** Fights already built as an even match: no extra scaling, nothing tamed, and no double turns. */
val EVEN_MATCHES = setOf(EncounterType.THUG, EncounterType.NOMAD, EncounterType.DUNGEON_GUARD, EncounterType.DUNGEON_BOSS)

/** Monsters that wait in the swap slots, ready to be sent in. */
const val MAX_SWAPS = 2

/**
 * Fights where you bring your full team (hero, party and swaps). Everywhere
 * else your side matches theirs: as many fighters as they have, the hero
 * included, and the rest of the party waits on the bench to swap in.
 */
val FULL_TEAM_ENCOUNTERS = setOf(EncounterType.DEN)

/** Your side in any fight: at least the hero and one monster. */
const val MIN_SIDE = 2

/** Scare and Menace: the foe's Attack drops to 66.7% (two thirds). */
const val SCARED_ATTACK = 2.0 / 3.0

/** Pay for a thug fight, per fighter on their side (thug included). */
const val THUG_REWARD = 0.9

/** Poison: this share of max HP each turn. */
const val POISON_PCT = 8

/** Shocked fighters move at this share of their Speed. */
const val SHOCK_SPEED = 0.5

/** Burned fighters' physical attacks hit for this share. */
const val BURN_ATTACK = 0.75

/** Same-type attack bonus (STAB): a move of one of the user's own types hits 1.5x, as in the classic games. */
const val SAME_TYPE_BONUS = 1.5

/** Spread moves hit each foe at 75% power when there's more than one (as in later generations). */
const val SPREAD_POWER = 0.75

/** Highest a stat stage can go (stage multipliers are capped at 2x anyway). */
private const val MAX_STAGES = 6

/**
 * Taming one of a pack doesn't end the fight. The rest react: each one weaker
 * (lower level) than the tamed monster may run off at this chance; the
 * others fly into a rage, +1 to every stat, stacking with every tame.
 */
const val PACK_FLEE_CHANCE = 0.5

class Battle(
    hero: Hero,
    party: List<Monster>,
    val enemyMonster: Monster,
    val encounter: EncounterType,
    inventory: Map<Item, Int>,
    /** Whether the player has room to keep a tamed monster. */
    private val storageFull: Boolean,
    val shrineId: String? = null,
    private val rng: Random = Random.Default,
    gear: Gear? = null,
    heroDamage: Int = 0,
    armor: List<Armor> = emptyList(),
    /** Other wild monsters fighting alongside [enemyMonster] as a pack (up to 3). */
    extraEnemies: List<Monster> = emptyList(),
    /** The monsters struck first: they act before you in the first round. */
    val ambush: Boolean = false,
    /** Second weapon carried with Kleptomaniac. */
    offhand: Gear? = null,
    /** Monsters in the swap slots: they can be sent in during the fight. */
    swaps: List<Monster> = emptyList(),
    /** Energy the hero has already spent. */
    heroEnergyUsed: Int = 0,
    /** A thug fighting alongside the enemy monsters. */
    val thug: Thug? = null,
    /** Whether you bring the whole team; otherwise your side matches theirs (see [FULL_TEAM_ENCOUNTERS]). */
    val fullTeam: Boolean = encounter in FULL_TEAM_ENCOUNTERS,
    /** Free spaces in monster storage: how many can be tamed in this fight. */
    private val storageRoom: Int = if (storageFull) 0 else Int.MAX_VALUE,
    /** The weather the fight starts in (from the real weather: rain, or a heatwave's drought). */
    startWeather: Weather = Weather.CLEAR,
    /** Multiplier on taming odds (a rare visitor is easier). */
    private val captureBoost: Double = 1.0,
    /** A trainer's monsters waiting to step in when one of theirs falls (a nomad's swaps). */
    enemyReserve: List<Monster> = emptyList(),
    /** At most this many of your monsters wait to swap in (null: all who don't fit). */
    swapLimit: Int? = null,
    /** The trainer who commands the enemy side (a nomad), for the battle log. */
    val trainer: String? = null,
    /** Today's rest (see [Rest]): a good night makes your side 10% better, a short one 10% worse. */
    private val restFactor: Double = 1.0,
    /** The tutorial dungeon: foes hit softly and every Binding cast tames. */
    val tutorial: Boolean = false,
    /** The monster (uid) who fights beside the hero; null: the first one in the party. */
    lead: String? = null,
    /** The real weather: its favored types fight harder, on both sides (see [LiveWeather.favored]). */
    val liveWeather: LiveWeather? = null,
) {
    /**
     * How many of your side fight at once, the hero included: theirs, or the
     * whole team; never less than the hero and one monster ([MIN_SIDE]).
     */
    val slots: Int = if (fullTeam) 1 + MAX_PARTY_SIZE else maxOf(MIN_SIDE, 1 + extraEnemies.take(3).size + (if (thug != null) 1 else 0))
    private val able = (party + swaps).filter { !it.fainted }.let { all ->
        // The chosen companion steps up first (even from a swap slot); the rest keep their order.
        val pick = lead?.let { uid -> all.find { it.uid == uid } }
        val ordered = party.filter { !it.fainted }
        if (pick == null) ordered else listOf(pick) + ordered.filter { it.uid != pick.uid }
    }.take(MAX_PARTY_SIZE)
    private val fighters = able.take(slots - 1)
    // In a matched fight the party members who don't fit wait on the bench, ahead of the swap slots.
    private val swapMonsters = (able.drop(slots - 1) + swaps.filter { !it.fainted && it.uid !in able.map { a -> a.uid } }.take(MAX_SWAPS)).let { all -> swapLimit?.let { all.take(it) } ?: all }
    private val heroPerks = hero.perks
    /** Your monsters' boost from the hero (Morale Master, Warlord, Beastmaster) and the best Pack Leader among them. */
    private val teamBoost = 1 + (heroPerks.teamPct + packLeader(fighters)) / 100.0
    /** Type pairings in your team (fighters and swaps alike): every monster of yours gets their boosts. */
    val synergies: List<Synergy> = Synergy.of(fighters + swapMonsters)
    private fun weatherAttack(m: Monster) = liveWeather?.attackFactor(listOfNotNull(m.species.element, m.species.secondary)) ?: 1.0
    private fun weatherSpeed(m: Monster) = liveWeather?.speedFactor(listOfNotNull(m.species.element, m.species.secondary)) ?: 1.0
    private fun ally(m: Monster) = (teamBoost * restFactor * Bonds.statFactor(m.bond)).let { f ->
        Combatant.fromMonster(
            m,
            hpMultiplier = f,
            attackMultiplier = f * Synergy.factor(synergies, BattleStat.ATTACK) * weatherAttack(m),
            defenseMultiplier = f * Synergy.factor(synergies, BattleStat.DEFENSE),
            speedMultiplier = f * Synergy.factor(synergies, BattleStat.SPEED) * weatherSpeed(m),
        ).apply { steadfast = Bonds.steadfast(m.bond) }
    }

    /** The hero, whether fighting or stepped back to let a monster fight. */
    private val heroFighter = Combatant.fromHero(hero, gear, heroDamage, armor, offhand, heroEnergyUsed, restFactor)
    private val team: MutableList<Combatant> = (listOf(heroFighter) + fighters.map { ally(it) }).toMutableList()

    /** Your side as it stands: the hero, then the monsters fighting (swaps change who's here). */
    val allies: List<Combatant> get() = team

    /** Monsters waiting in the swap slots (and any swapped out, the hero too). */
    private val reserve: MutableList<Combatant> = swapMonsters.map { ally(it) }.toMutableList()
    val swapsWaiting: List<Combatant> get() = reserve

    /** Monsters that actually stepped into the fight (they earn full XP). */
    private val fought: MutableSet<String> = fighters.map { it.uid }.toMutableSet()

    /** Fighters on your side: the hero plus the party monsters able to fight. */
    val teamSize: Int = 1 + fighters.size
    /** The enemy side: one monster, or a pack of up to 4. */
    private val enemyMonsterList: MutableList<Monster> = (listOf(enemyMonster) + extraEnemies.take(3)).toMutableList()
    val enemyMonsters: List<Monster> get() = enemyMonsterList
    // A thug's crew and a nomad's team are an even match already: no extra scaling.
    private val scaling = if (encounter in EVEN_MATCHES) TeamScaling.Factors(1.0, 1.0, 1.0)
    else TeamScaling.pack(TeamScaling.factors(teamSize, encounter, fullTeam), enemyMonsters.size)
    private val enemyBoost = 1 + packLeader(enemyMonsters) / 100.0
    private fun foe(m: Monster, key: String): Combatant {
        // Legendaries are built stronger to own; as shrine guardians they're scaled back to their usual challenge.
        val legend = if (m.species.rarity == Rarity.LEGENDARY && encounter != EncounterType.WILD) 1 / Bestiary.LEGENDARY_POWER else 1.0
        // A wild Alpha is half again as tough.
        val alpha = if (m.alpha && encounter == EncounterType.WILD) Alphas.STAT_BOOST else 1.0
        return Combatant.fromMonster(
            if (alpha > 1.0) m.copy(nickname = "Alpha ${m.displayName}") else m,
            key,
            hpMultiplier = encounter.hpMultiplier * scaling.hp * legend * enemyBoost * alpha * (if (tutorial) 0.6 else 1.0),
            attackMultiplier = scaling.attack * legend * enemyBoost * alpha * (if (tutorial) 0.35 else 1.0) * weatherAttack(m),
            defenseMultiplier = scaling.defense * legend * enemyBoost * alpha,
            speedMultiplier = enemyBoost * weatherSpeed(m),
        )
    }
    private val enemyList: MutableList<Combatant> = (
        enemyMonsterList.mapIndexed { i, m -> foe(m, Combatant.enemyKey(i)) } +
            listOfNotNull(thug?.let { Combatant.fromThug(it, Combatant.enemyKey(enemyMonsterList.size)) })
        ).toMutableList()
    val enemies: List<Combatant> get() = enemyList

    /** The trainer's monsters still waiting to step in. */
    private val enemyBench: ArrayDeque<Monster> = ArrayDeque(enemyReserve)
    val enemyReserveLeft: Int get() = enemyBench.size
    /** Trainer monsters knocked out and replaced (they count as beaten). */
    private val retired = mutableListOf<Monster>()
    private var nextEnemyKey = enemyList.size

    /** A trainer sends in the next monster for each one of theirs that fell. */
    private fun sendEnemyReserves() {
        for (i in enemyList.indices) {
            if (enemyList[i].hp > 0 || enemyBench.isEmpty() || i >= enemyMonsterList.size) continue
            retired += enemyMonsterList[i]
            val m = enemyBench.removeFirst()
            enemyMonsterList[i] = m
            enemyList[i] = foe(m, Combatant.enemyKey(nextEnemyKey++))
            log += "${trainer ?: "They"} sends out ${m.displayName} (Lv ${m.level})!"
        }
    }

    /** Which enemy your attacks, spells and orbs aim at (the player can switch). */
    var targetKey: String = Combatant.ENEMY_KEY
        private set

    /** The enemy being targeted (the next one standing if it falls). */
    val enemy: Combatant
        get() = enemies.find { it.key == targetKey && it.alive } ?: enemies.firstOrNull { it.alive } ?: enemies.first()

    fun selectTarget(key: String) {
        if (enemies.any { it.key == key && it.alive }) targetKey = key
    }

    private fun monsterFor(key: String): Monster = enemyMonsters[enemies.indexOfFirst { it.key == key }.coerceIn(0, enemyMonsters.size - 1)]

    /** Pack members tamed so far, in order. */
    private val capturedKeys = mutableListOf<String>()
    private val heroLevel = hero.level
    private val heroSkills = hero.spells()
    /** Rank-5 spells' paths and mastery (see [SpellMastery]). */
    private val spellPaths = hero.spellPaths
    private val spellMastery = hero.spellMastery
    private val partyUids = fighters.map { it.uid } + swapMonsters.map { it.uid }

    private val bag: MutableMap<Item, Int> = inventory.toMutableMap()
    private val itemsUsed = mutableMapOf<Item, Int>()
    /** Items used that count toward the per-battle limit: taming (Binding Crystals and berries) doesn't. */
    val itemsUsedCount: Int get() = itemsUsed.filterKeys { countsTowardLimit(it) }.values.sum()

    private fun countsTowardLimit(item: Item) = item.kind != ItemKind.CAPTURE && item.kind != ItemKind.BAIT
    val itemsRemainingThisBattle: Int get() = MAX_ITEMS_PER_BATTLE - itemsUsedCount

    val log = mutableListOf<String>()
    /** Attacks, heals and misses in order, for animations. */
    val events = mutableListOf<BattleEvent>()
    var outcome: BattleOutcome = BattleOutcome.ONGOING
        private set
    var round: Int = 0
        private set
    var weather: Weather = startWeather
        private set
    /** Rounds left with reversed turn order (Wild Wind). */
    var reversedRounds: Int = 0
        private set

    private val queue = ArrayDeque<String>()

    /** The ally whose turn it is (waiting for player input), or null if the battle is over. */
    var current: Combatant? = null
        private set

    val isBoss: Boolean get() = encounter !in setOf(EncounterType.WILD, EncounterType.DEN) + EVEN_MATCHES
    val captureLocked: Boolean
        get() = encounter == EncounterType.BOSS_CHALLENGE || encounter == EncounterType.VILLAIN || encounter == EncounterType.DEN ||
            encounter in EVEN_MATCHES

    init {
        val intro = when (encounter) {
            EncounterType.WILD -> if (enemies.size > 1) {
                "A pack of ${enemies.size} wild monsters attacks together: ${enemies.joinToString { "${it.name} (Lv ${it.level})" }}!"
            } else {
                "A wild ${enemy.name} (Lv ${enemy.level}) appears!"
            }
            EncounterType.BOSS_CHALLENGE -> "BOSS: ${enemy.name} (Lv ${enemy.level}) guards this shrine! Defeat it to earn the right to tame it."
            EncounterType.BOSS_TAMING -> "BOSS: ${enemy.name} (Lv ${enemy.level}) remembers you. Weaken it and try to tame it!"
            EncounterType.SILVER_BOSS -> "SILVER GUARDIAN: ${enemy.name} (Lv ${enemy.level}) blocks your path! It's hard to tame, but it has perfect genes."
            EncounterType.BOX_BOSS -> "The loot box bursts open... ${enemy.name} (Lv ${enemy.level}) erupts out of it!"
            EncounterType.VILLAIN -> "A shadow-cursed ${enemy.name} (Lv ${enemy.level}) attacks! It can't be tamed."
            EncounterType.DEN -> "A fierce ${enemy.name} (Lv ${enemy.level}) charges out of its den! Beat it, then try to tame it."
            EncounterType.THUG -> "${thug?.title ?: "A thug"} of the ${thug?.crew ?: "streets"} jumps you with " +
                "${enemyMonsters.joinToString { "${it.displayName} (Lv ${it.level})" }}!"
            EncounterType.DUNGEON_GUARD -> "The dungeon's guardians rise: ${enemyMonsters.joinToString { "${it.displayName} (Lv ${it.level})" }}! They can't be tamed."
            EncounterType.DUNGEON_BOSS -> "${trainer ?: "The champion"} blocks the way out with ${enemyMonsters.joinToString { "${it.displayName} (Lv ${it.level})" }}!"
            EncounterType.NOMAD -> "${trainer ?: "The nomad"} steps up${if (thug != null) " to fight" else ""} with ${enemyMonsters.joinToString { "${it.displayName} (Lv ${it.level})" }}" +
                (if (enemyBench.isNotEmpty()) ", with ${enemyBench.size} more waiting!" else "!")
        }
        log += intro
        // Stormcallers and Sunbringers bring their weather with them.
        (allies + enemies).firstOrNull { it.rankOf(Trait.STORMCALLER) > 0 || it.rankOf(Trait.SUNBRINGER) > 0 }?.let { c ->
            weather = if (c.rankOf(Trait.STORMCALLER) > 0) Weather.RAIN else Weather.DROUGHT
            log += "${c.name} brings ${if (weather == Weather.RAIN) "a storm: it starts to rain" else "a scorching drought"}!"
        }
        when (weather) {
            Weather.RAIN -> log += "It's raining: Water and Electric moves are stronger, Fire weaker."
            Weather.DROUGHT -> log += "The heat is fierce: Fire and Corrupt moves are stronger, Water weaker."
            Weather.CLEAR -> {}
        }
        liveWeather?.let { w ->
            val all = (allies + enemies).filter { !it.isHero && !it.thug }
            val up = all.filter { c -> c.types.any { it in w.favored } }.map { it.name }.distinct()
            val down = all.filter { c -> c.types.none { it in w.favored } && c.types.any { it in w.hindered } }.map { it.name }.distinct()
            if (up.isNotEmpty()) log += "${w.label}: ${up.joinToString()} ${if (up.size == 1) "draws" else "draw"} strength from the weather."
            if (down.isNotEmpty()) log += "${w.label}: ${down.joinToString()} ${if (down.size == 1) "is" else "are"} held back by the weather."
        }
        if (synergies.isNotEmpty()) log += "Team synergy: " + synergies.joinToString { "${it.label} (${it.text})" } + "."
        if (ambush) log += "Ambush! The monsters strike before you're ready!"
        if (encounter == EncounterType.WILD && teamSize > 1 && enemies.size < teamSize) {
            log += (if (enemies.size > 1) "The pack grows" else "${enemy.name} grows") + " stronger against your team of $teamSize!"
        }
        openingSkills(hero)
        advance()
    }

    /** The best Pack Leader bonus in a group of monsters, in percent. */
    private fun packLeader(group: List<Monster>): Int =
        group.mapNotNull { m -> m.trait?.takeIf { it.first == Trait.PACK_LEADER }?.let { (t, r) -> t.teamPct(r) } }.maxOrNull() ?: 0

    /** Skills that act as the battle starts: team-wide hero skills, wards and Menace. */
    private fun openingSkills(hero: Hero) {
        val me = heroFighter
        for ((t, r) in hero.skillRanks) {
            if (t.teamRange(r, hero = true)) (allies + reserve).filter { !it.isHero }.forEach { it.shareTrait(t, r) }
        }
        if (heroPerks.teamPct > 0 && allies.size > 1) log += "Your monsters are fired up! (+${heroPerks.teamPct}%)"
        if (packLeader(fighters) > 0) log += "A Pack Leader rallies your team! (+${packLeader(fighters)}%)"
        if (enemyBoost > 1) log += "A Pack Leader rallies the wild pack! (+${packLeader(enemyMonsters)}%)"
        if (me.shield > 0) log += "${me.name} starts behind a holy ward (absorbs ${me.shield})."
        for (c in allies + enemies) {
            val r = c.traits[Trait.MENACE] ?: continue
            if (!c.alive) continue
            // The same Attack cut as Scare (66.7%), plus extra stages at higher ranks.
            val extra = Trait.MENACE.menaceStages(r)
            log += "${c.name}'s Menace!"
            for (foe in foesOf(c).filter { it.alive }) {
                if (foe.unflappable) {
                    log += "${foe.name} doesn't even notice."
                } else {
                    foe.scared = true
                    if (extra > 0) foe.stages[BattleStat.ATTACK] = ((foe.stages[BattleStat.ATTACK] ?: 0) - extra).coerceAtLeast(-4)
                    log += "${foe.name}'s Attack fell to 67%!"
                }
            }
        }
    }

    fun combatant(key: String): Combatant? =
        if (Combatant.isEnemy(key)) enemies.find { it.key == key } else allies.find { it.key == key } ?: reserve.find { it.key == key }

    fun count(item: Item): Int = bag[item] ?: 0

    // ---- queries for the UI -------------------------------------------------

    fun spellsFor(actor: Combatant): List<Skill> = if (actor.isHero) heroSkills else emptyList()

    fun battleItems(): List<Item> =
        Item.entries.filter { it.usableInBattle && it.kind != ItemKind.CAPTURE && it.kind != ItemKind.BAIT && count(it) > 0 }

    /** HP a healing spell restores when [caster] casts it (before capping at full). */
    fun spellHeal(caster: Combatant, skill: Skill): Int = ((skill.power + caster.magic * 1.5) * (1 + caster.healPct / 100.0)).roundToInt()

    /**
     * HP each ally [skill] restores when [caster] casts it now (before capping at
     * full), with its path and mastery; null for spells that don't heal.
     * Mend, Cleanse and their paths heal the target; Healing Circle and
     * Sanctuary heal everyone ([healsEveryone]).
     */
    fun healPreview(caster: Combatant, skill: Skill): Int? {
        val path = if (caster.isHero) spellPaths[skill.id] ?: 0 else 0
        val m = if (caster.isHero) spellMastery[skill.id] ?: 0 else 0
        val base = spellHeal(caster, skill)
        val factor = when (skill.kind) {
            SkillKind.HEAL -> if (path == 1) SpellMastery.purifyBoost(m) else 1.0
            SkillKind.CLEANSE -> when (path) {
                1 -> SpellMastery.sanctuaryShare(m)
                2 -> SpellMastery.restorationBoost(m)
                else -> 1.0
            }
            SkillKind.MEND_ALL -> when (path) {
                1 -> SpellMastery.purifyBoost(m)
                2 -> SpellMastery.radiantBoost(m)
                else -> 1.0
            }
            else -> return null
        }
        return (base * factor).roundToInt()
    }

    /** Whether [skill] heals every ally rather than just its target. */
    fun healsEveryone(caster: Combatant, skill: Skill): Boolean =
        skill.kind == SkillKind.MEND_ALL || (skill.kind == SkillKind.CLEANSE && caster.isHero && spellPaths[skill.id] == 1)

    /** Overflow (Mend's second path): the share of healing past full HP that becomes a shield, or null. */
    fun overflowShare(caster: Combatant, skill: Skill): Double? =
        if (skill.kind == SkillKind.HEAL && caster.isHero && spellPaths[skill.id] == 2) SpellMastery.overflowShare(spellMastery[skill.id] ?: 0) else null

    /** Berries you could feed the foe (on the Tame menu). */
    fun berries(): List<Item> = Item.entries.filter { it.kind == ItemKind.BAIT && count(it) > 0 }

    /** True if [berry] can be fed now: the foe can be tamed in this fight, and it hasn't eaten as good a berry. */
    fun canFeed(berry: Item): Boolean =
        berry.kind == ItemKind.BAIT && count(berry) > 0 && bait < berry.power &&
            encounter != EncounterType.VILLAIN && encounter != EncounterType.DEN && encounter != EncounterType.THUG && !captureLocked

    /** The last Binding cast's roll, for the shake animation, and how many casts so far (to spot a new one). */
    var lastCast: CastRoll? = null
        private set
    var castCount: Int = 0
        private set

    /** A berry the foe has eaten: the next throw's odds are multiplied by this (1 = none). */
    var bait: Double = 1.0
        private set

    /** Orbs you can throw now: above half HP only a Perfect Orb works. */
    fun orbs(): List<Item> = Item.entries.filter {
        it.kind == ItemKind.CAPTURE && count(it) > 0 && (enemy.hpFraction <= 0.5 || it == Item.PERFECT_ORB)
    }

    /** Null when capture is allowed, otherwise a reason to show the player. */
    fun captureBlockedReason(): String? = when {
        encounter == EncounterType.VILLAIN -> "Shadow-bound monsters can't be tamed. Defeat the whole team."
        encounter == EncounterType.DEN -> "Den monsters are too fierce. Beat it first, then you'll get your binding casts."
        encounter == EncounterType.THUG -> "A thug's monsters won't leave their master. Beat the whole crew."
        encounter == EncounterType.NOMAD -> "The nomad's monsters are loyal partners. Win the challenge instead."
        encounter == EncounterType.DUNGEON_GUARD -> "Cursed guardians can't be tamed. Fight your way through."
        encounter == EncounterType.DUNGEON_BOSS -> "The champion's monsters stay loyal. Beat them all."
        captureLocked -> "Defeat this boss first. Then it can be tamed on a later visit."
        capturedKeys.size >= storageRoom -> "Monster storage is full. Release a monster or upgrade storage."
        orbs().isEmpty() -> "No Binding Crystals left."
        enemy.hpFraction > 0.5 && count(Item.PERFECT_ORB) <= 0 -> "Too strong to tame. Get it below 50% HP."
        else -> null
    }

    /** The odds shown for [orb]: its taming chance plus the 2% critical catch. */
    fun catchOdds(orb: Item, grade: TraceGrade = TraceGrade.GOOD): Double = withCriticalCatch(captureOdds(orb, grade))

    /** Status conditions (sleep, shock, poison, burn) make taming easier. */
    fun captureOdds(orb: Item, grade: TraceGrade = TraceGrade.GOOD): Double = if (orb == Item.PERFECT_ORB || tutorial) 1.0 else
        (
            captureChance(enemy.hpFraction, monsterFor(enemy.key).species.rarity, enemy.level - heroLevel, orb) * bait * grade.multiplier *
                encounter.captureMultiplier * (enemy.status?.captureBonus ?: 1.0) * (1 + heroFighter.tameBonus) * captureBoost *
                (if (snapFailed) SNAP_FAIL_CATCH else 1.0) * (if (monsterFor(enemy.key).alpha) Alphas.CATCH_MULTIPLIER else 1.0)
            ).coerceIn(0.0, 0.95)

    // ---- Snap Bind: the first-turn gamble ------------------------------------

    /** How many turns your side has taken (Snap Bind only works before the first). */
    private var allyTurns = 0
    /** A Snap Bind missed: the pack has doubled down. */
    var snapFailed: Boolean = false
        private set
    /** A Snap Bind landed: the rest of the pack scattered. */
    var scattered: Boolean = false
        private set

    /** Snap Bind can be cast now: a wild fight's first turn, before your side has acted (not after an ambush). */
    val snapAvailable: Boolean
        get() = outcome == BattleOutcome.ONGOING && encounter == EncounterType.WILD && round == 1 && allyTurns == 0 && !ambush &&
            current != null && !Combatant.isEnemy(current!!.key) && captureBlockedReasonIgnoringHp() == null

    /** Snap Bind odds with [orb]: by rarity, better crystals help (square root of their power), less against higher levels. */
    fun snapOdds(orb: Item): Double {
        val sp = monsterFor(enemy.key).species
        val base = (SNAP_BIND_ODDS[sp.rarity] ?: 0.1) * (if (monsterFor(enemy.key).alpha) Alphas.CATCH_MULTIPLIER else 1.0)
        val levelPenalty = 0.95.pow(max(0, enemy.level - heroLevel))
        return (base * kotlin.math.sqrt(orb.power) * levelPenalty * captureBoost).coerceIn(0.01, SNAP_BIND_MAX)
    }

    /** Why taming is blocked, leaving out the "below half HP" rule (Snap Bind ignores it). */
    private fun captureBlockedReasonIgnoringHp(): String? = when {
        captureLocked || encounter != EncounterType.WILD -> "Not here."
        capturedKeys.size >= storageRoom -> "Monster storage is full."
        Item.entries.none { it.kind == ItemKind.CAPTURE && count(it) > 0 && it != Item.PERFECT_ORB } -> "No Binding Crystals left."
        else -> null
    }

    // ---- turn handling ------------------------------------------------------

    fun act(action: BattleAction) {
        val actor = current ?: return
        if (outcome != BattleOutcome.ONGOING) return
        val consumedTurn = perform(actor, action)
        if (!consumedTurn) return
        if (!Combatant.isEnemy(actor.key)) allyTurns++
        // A turn spent on anything but a move rests every move a little.
        if (action !is BattleAction.UseMove) actor.spendTurn(null)
        if (action != BattleAction.Defend) actor.defendedLast = false
        current = null
        checkEnd()
        advance()
    }

    private fun advance() {
        while (outcome == BattleOutcome.ONGOING) {
            // Everyone out front is down but someone waits on the bench: the fight waits for you to send them in.
            if (mustSwap) {
                current = team.first { !it.alive }
                return
            }
            if (queue.isEmpty()) startRound()
            val key = queue.removeFirst()
            val c = combatant(key) ?: continue
            if (!c.alive) {
                // A fallen monster's turn: the player can swap someone in or skip it.
                if (awaitsSwap(c)) {
                    current = c
                    return
                }
                continue
            }
            val canAct = beginTurn(c)
            checkEnd()
            if (!canAct || outcome != BattleOutcome.ONGOING) continue
            if (Combatant.isEnemy(key)) {
                enemyTurn(c)
                checkEnd()
            } else {
                current = c
                return
            }
        }
        current = null
    }

    /**
     * Who acts next, starting with whoever is acting now: the rest of this
     * round, then the predicted order of the next round (by Speed).
     */
    fun turnOrder(count: Int = 6): List<String> {
        val alive = { k: String -> combatant(k)?.alive == true }
        val now = listOfNotNull(current?.key) + queue.filter(alive)
        val everyone = (allies + enemies).filter { it.alive }.sortedByDescending { speedOf(it) }
            .sortedByDescending { it.swift }
        val nextReversed = reversedRounds > 1
        val next = (if (nextReversed) everyone.reversed() else everyone).map { it.key } +
            (if (isBoss && enemies.first().alive) listOf(Combatant.ENEMY_KEY) else emptyList())
        return (now + next + next).take(count)
    }

    private fun record(a: Combatant, d: Combatant, style: StrikeStyle, element: Element, amount: Int = 0, crit: Boolean = false, superEffective: Boolean = false) {
        events += BattleEvent(events.size, a.key, d.key, style, element, amount, crit, superEffective)
    }

    private fun startRound() {
        round++
        if (reversedRounds > 0) reversedRounds--
        // Fallen monsters keep their turn while a swap is waiting (to swap or skip).
        val everyone = (allies + enemies).filter { it.alive || awaitsSwap(it) }
        // Momentum: Speed creeps up round by round.
        if (round > 1) for (c in (allies + enemies).filter { it.alive && it.rankOf(Trait.MOMENTUM) > 0 }) {
            val now = c.stages[BattleStat.SPEED] ?: 0
            if (now < 4 && rng.nextInt(100) < Trait.MOMENTUM.momentumPct(c.rankOf(Trait.MOMENTUM))) {
                c.stages[BattleStat.SPEED] = now + 1
                log += "${c.name}'s Momentum: its Speed rose!"
            }
        }
        val bySpeed = everyone.sortedByDescending { speedOf(it) + rng.nextDouble() }
        val ordered = if (reversedRounds > 0) bySpeed.reversed() else bySpeed
        // An ambush: the monsters all act before you in the first round.
        var order = if (ambush && round == 1) ordered.sortedBy { !Combatant.isEnemy(it.key) } else ordered
        // First Strike: the hero (or, at team range, the whole team) opens the battle.
        val strike = heroFighter.traits[Trait.FIRST_STRIKE]
        if (round == 1 && strike != null) {
            val first = if (Trait.FIRST_STRIKE.teamRange(strike, hero = true)) order.filter { !Combatant.isEnemy(it.key) }
            else order.filter { it.key == Combatant.HERO_KEY }
            order = first + (order - first.toSet())
        }
        // Swift: whoever used a Swift move last round goes first now.
        val swifts = order.filter { it.swift }
        if (swifts.isNotEmpty()) {
            order = swifts + (order - swifts.toSet())
            swifts.forEach { it.swift = false }
        }
        queue.addAll(order.map { it.key })
        // Bosses act twice per round.
        if (isBoss) queue.addLast(Combatant.ENEMY_KEY)
    }

    /** Damage of a fixed "base" amount, scaled by level like normal attacks. */
    private fun scaled(base: Int, level: Int) = (base * (1 + level * 0.08)).roundToInt()

    private fun hurt(c: Combatant, amount: Int, text: String) {
        c.hp = max(0, c.hp - amount)
        log += "${c.name} $text ($amount)."
        if (!c.alive) log += "${c.name} falls!"
        secondWind(c)
    }

    private fun breakTeamHover(hero: Combatant) {
        val lifted = teamOf(hero).filter { it.borrowedHover } + reserve.filter { it.borrowedHover }
        if (lifted.isEmpty()) return
        lifted.forEach { it.borrowedHover = false; it.immuneEarth = false }
        log += "${hero.name} is hit! The team's hover breaks (${hero.name} keeps floating)."
    }

    /** Second Wind: once a battle, a big heal on dropping below a quarter of max HP. */
    private fun secondWind(c: Combatant) {
        if (c.secondWindPct <= 0 || c.secondWindUsed || !c.alive || c.hpFraction >= 0.25) return
        c.secondWindUsed = true
        heal(c, maxOf(1, c.maxHp * c.secondWindPct / 100), "${c.name} gets a second wind")
    }

    /** Puts [d] to sleep for [turns], less what Wakeful shakes off. Returns false if it stays awake. */
    private fun fallAsleep(d: Combatant, turns: Int): Boolean {
        if (d.sleepCut >= Combatant.NO_SLEEP) {
            log += "${d.name}'s Wakeful nature keeps it wide awake!"
            return false
        }
        d.status = Status.SLEEP
        d.statusTurns = (turns - d.sleepCut).coerceAtLeast(1)
        return true
    }

    /** Ticks conditions at the start of a fighter's turn. Returns false if it loses the turn. */
    private fun beginTurn(c: Combatant): Boolean {
        c.defending = false
        c.bracing = false
        c.protecting = false
        if (c.energyRegen > 0 && c.mp < c.maxMp) c.mp = minOf(c.maxMp, c.mp + c.energyRegen)
        if (c.regenPct > 0 && c.hp < c.maxHp) heal(c, maxOf(1, c.maxHp * c.regenPct / 100), "${c.name} regenerates")
        val drinker = c.rankOf(Trait.RAIN_DRINKER)
        if (drinker > 0 && weather == Weather.RAIN && c.hp < c.maxHp) heal(c, maxOf(1, c.maxHp * Trait.RAIN_DRINKER.rainDrinkPct(drinker) / 100), "${c.name} drinks the rain")
        if (c.scorchTurns > 0) {
            c.scorchTurns--
            hurt(c, c.scorchDamage, c.scorchVerb)
        }
        when (c.status) {
            Status.POISON -> hurt(c, max(1, c.maxHp * POISON_PCT / 100), "is hurt by poison")
            Status.BURN -> hurt(c, max(1, c.maxHp * 6 / 100), "is hurt by its burn")
            // A drought thaws frostbite out.
            Status.FROSTBITE -> if (weather != Weather.DROUGHT) hurt(c, max(1, c.maxHp * 5 / 100), "is hurt by frostbite")
            else -> {}
        }
        if (c.status == Status.POISON || c.status == Status.BURN || c.status == Status.FROSTBITE) tickStatus(c)
        // Cursed gear gnaws at its owner every turn until the curse is lifted.
        if (c.curseDrainPct > 0 && c.alive) hurt(c, max(1, c.maxHp * c.curseDrainPct / 100), "is drained by a curse")
        if (!c.alive) return false

        if (c.status == Status.SLEEP) {
            if (c.nightTerrorDamage > 0) hurt(c, c.nightTerrorDamage, "writhes in a night terror")
            if (!c.alive) return false
            tickStatus(c)
            if (c.status == Status.SLEEP) {
                log += "${c.name} is fast asleep."
                return false
            }
            log += "${c.name} woke up!"
        }
        if (c.mustRecharge) {
            c.mustRecharge = false
            log += "${c.name} must recharge."
            return false
        }
        if (c.flinch) {
            c.flinch = false
            log += "${c.name} flinched and lost its turn!"
            return false
        }
        if (c.status == Status.SHOCK) {
            tickStatus(c)
            if (rng.nextDouble() < 0.25) {
                log += "${c.name} is shocked and can't move!"
                return false
            }
        }
        val dive = c.diving
        if (dive != null) {
            c.diving = null
            val target = if (Combatant.isEnemy(c.key)) pickTarget() else enemy
            if (target != null) useMove(c, target, dive, diveStrike = true)
            return false
        }
        return true
    }

    private fun tickStatus(c: Combatant) {
        c.statusTurns--
        if (c.statusTurns <= 0) {
            val s = c.status
            c.status = null
            c.nightTerrorDamage = 0
            if (s != null && s != Status.SLEEP) log += "${c.name} is no longer ${s.label.lowercase()}."
        }
    }

    private fun checkEnd() {
        if (outcome != BattleOutcome.ONGOING) return
        sendEnemyReserves()
        if (enemies.none { it.alive }) {
            // Beat any of them and it's a win; if every one was tamed or ran off, it ends with the taming.
            if (enemies.any { it.hp <= 0 }) {
                outcome = BattleOutcome.WON
                log += if (trainer != null) "${trainer}'s whole team is defeated!" else if (enemies.size > 1) "The whole pack is defeated!" else "${enemy.name} is defeated!"
            } else {
                outcome = BattleOutcome.CAPTURED
                if (enemies.any { it.gone == "Ran off" }) log += "The rest of the pack is gone."
            }
        } else if (allies.none { it.alive } && reserve.none { it.alive }) {
            outcome = BattleOutcome.LOST
            log += "Your party has fallen..."
        }
    }

    /** Returns false if the action was invalid (turn not used). */
    private fun perform(actor: Combatant, action: BattleAction): Boolean {
        // A fallen monster can only be swapped out, or let its turn pass.
        if (!actor.alive) {
            return when (action) {
                is BattleAction.Swap -> swapIn(action.copy(outKey = actor.key))
                BattleAction.Skip -> {
                    // With nobody else standing out front, someone has to step in.
                    if (mustSwap) return false
                    log += "${actor.name} can't fight. Its turn passes."
                    true
                }
                else -> false
            }
        }
        when (action) {
            BattleAction.Skip -> return false
            BattleAction.Attack -> {
                if (actor.isHero) {
                    // Daggers strike twice.
                    repeat(actor.hits) {
                        if (enemy.alive && !floatsOver(actor, enemy, actor.attackElement) && rollHit(actor, enemy, 100)) {
                            // A cursed weapon hits 25% softer until the curse is lifted.
                            val power = if (actor.weaponCursed) 40 * (100 - CURSED_DAMAGE_CUT) / 100 else 40
                            val dmg = strike(actor, enemy, power, actor.attackElement, useMagic = false, verb = "attacks", style = StrikeStyle.SLASH)
                            afterHeroHit(actor, enemy, dmg, basic = true)
                        }
                    }
                    // Kleptomaniac: the off-hand weapon follows up.
                    if (actor.offhandPct > 0) repeat(actor.offhandHits) {
                        if (enemy.alive && !floatsOver(actor, enemy, actor.offhandElement) && rollHit(actor, enemy, 100)) {
                            val power = (40 * actor.offhandPct / 100).coerceAtLeast(1)
                            val dmg = strike(actor, enemy, power, actor.offhandElement, useMagic = false, verb = "strikes with the off hand at", style = StrikeStyle.SLASH)
                            afterHeroHit(actor, enemy, dmg, basic = true)
                        }
                    }
                } else {
                    useMove(actor, enemy, actor.moves.firstOrNull() ?: Moves.get("shoulder_rush"))
                }
            }
            BattleAction.Defend -> {
                // The shield blocks every hit until the next turn, but it can't hold twice in a row.
                if (actor.defendedLast) {
                    actor.bracing = true
                    actor.defendedLast = false
                    log += "${actor.name} braces again, but the shield can't hold twice in a row: half damage this turn."
                } else {
                    actor.defending = true
                    actor.defendedLast = true
                    log += "${actor.name} raises a shield: no damage until their next turn."
                }
            }
            is BattleAction.UseMove -> {
                val move = actor.usableMoves.find { it.id == action.moveId } ?: return false
                spendMove(actor, move)
                if (move == Moves.STRUGGLE) log += "${actor.name} is out of moves!"
                useMove(actor, enemy, move)
            }
            is BattleAction.CastSpell -> {
                val skill = spellsFor(actor).find { it.id == action.skillId } ?: return false
                if (actor.mp < skill.mpCost) return false
                // A rank-5 spell's path (0: none yet) and its mastery.
                val path = if (actor.isHero) spellPaths[skill.id] ?: 0 else 0
                val m = if (actor.isHero) spellMastery[skill.id] ?: 0 else 0
                if (skill.kind in SUPPORT_KINDS) {
                    val target = combatant(action.targetKey ?: actor.key)?.takeIf { it.alive && !Combatant.isEnemy(it.key) }
                        ?: return false
                    actor.mp -= skill.mpCost
                    if (path != 0 && castPath(actor, target, skill, path, m)) return true
                    when (skill.kind) {
                        SkillKind.SHIELD -> {
                            val amount = ((skill.power + actor.magic * 1.2) * (1 + actor.healPct / 100.0)).roundToInt()
                            target.shield = max(target.shield, amount)
                            log += "${actor.name} casts ${skill.name}: ${target.name} is shielded (absorbs ${target.shield})."
                            record(actor, target, StrikeStyle.WARD, Element.NEUTRAL, target.shield)
                        }
                        SkillKind.AEGIS -> {
                            // Worth a share of the caster's max HP: half at first, more as it ranks up.
                            val amount = (actor.maxHp * skill.power / 100.0).roundToInt().coerceAtLeast(1)
                            target.shield = max(target.shield, amount)
                            log += "${actor.name} casts ${skill.name}: ${target.name} is behind a great shield (absorbs ${target.shield})."
                            record(actor, target, StrikeStyle.WARD, Element.NEUTRAL, target.shield)
                        }
                        SkillKind.FORTIFY, SkillKind.HASTE, SkillKind.EMPOWER -> {
                            val (stat, label) = when (skill.kind) {
                                SkillKind.HASTE -> BattleStat.SPEED to "Speed"
                                SkillKind.EMPOWER -> BattleStat.ATTACK to "Attack"
                                else -> BattleStat.DEFENSE to "Defense"
                            }
                            val now = target.stages[stat] ?: 0
                            target.stages[stat] = minOf(MAX_STAGES, now + skill.power)
                            log += "${actor.name} casts ${skill.name}: ${target.name}'s $label rises!"
                            record(actor, target, StrikeStyle.WARD, Element.NEUTRAL)
                        }
                        SkillKind.CLEANSE -> {
                            val cured = target.status
                            target.status = null
                            target.statusTurns = 0
                            BattleStat.entries.forEach { st -> if ((target.stages[st] ?: 0) < 0) target.stages[st] = 0 }
                            heal(target, spellHeal(actor, skill), "${actor.name} casts ${skill.name}" + (cured?.let { " (${target.name} is no longer ${it.label.lowercase()})" } ?: ""))
                        }
                        SkillKind.MEND_ALL -> {
                            val amount = spellHeal(actor, skill)
                            log += "${actor.name} casts ${skill.name}!"
                            for (a in allies.filter { it.alive }) heal(a, amount, "  ${skill.name}")
                        }
                        else -> heal(target, spellHeal(actor, skill), "${actor.name} casts ${skill.name}")
                    }
                } else {
                    actor.mp -= skill.mpCost
                    // Storm and Mudslide: the spell splits to hit every foe.
                    val splits = path == 1
                    if (splits) {
                        val share = if (skill.id == Spells.MUD_SLING) SpellMastery.mudslideShare(m) else SpellMastery.stormShare(m)
                        log += "${actor.name}'s ${SpellMastery.path(skill.id, 1)?.name ?: skill.name} strikes every foe!"
                        for (foe in enemies.filter { it.alive }) {
                            if (floatsOver(actor, foe, skill.element) || !rollHit(actor, foe, 100)) continue
                            val d = strike(actor, foe, (skill.power * share).roundToInt().coerceAtLeast(1), skill.element, useMagic = true, verb = "hits", style = StrikeStyle.BOLT)
                            afterHeroHit(actor, foe, d)
                            if (skill.id == Spells.MUD_SLING && foe.alive) muddy(foe)
                        }
                        return true
                    }
                    if (floatsOver(actor, enemy, skill.element) || !rollHit(actor, enemy, 100)) return true
                    val target = enemy
                    // Lance: more power, and its type's effect.
                    val lance = path == 2 && skill.id != Spells.MUD_SLING
                    val power = if (lance) (skill.power * SpellMastery.lanceBoost(m)).roundToInt() else skill.power
                    val dmg = strike(actor, target, power, skill.element, useMagic = true, verb = "casts ${skill.name} on", style = StrikeStyle.BOLT)
                    afterHeroHit(actor, target, dmg)
                    if (lance && target.alive) lanceEffect(actor, target, skill.element, m)
                    // Quagmire: the mud also slows the foe.
                    if (path == 2 && skill.id == Spells.MUD_SLING && target.alive && rng.nextDouble() < luck(actor, SpellMastery.quagmireChance(m))) {
                        lowerStage(target, BattleStat.SPEED)
                    }
                    // Mud Sling: mud in the eyes, stacking.
                    if (skill.id == Spells.MUD_SLING && target.alive) muddy(target)
                    if (actor.spellDrainPct > 0 && dmg > 0 && actor.alive && actor.hp < actor.maxHp) {
                        heal(actor, maxOf(1, dmg * actor.spellDrainPct / 100), "${actor.name}'s dark magic drains life")
                    }
                }
            }
            is BattleAction.UseItem -> {
                val target = combatant(action.targetKey)?.takeIf { !Combatant.isEnemy(it.key) } ?: return false
                // A berry for the foe: only when it can be tamed, and it doesn't stack.
                if (action.item.kind == ItemKind.BAIT && !canFeed(action.item)) return false
                if (!useItem(action.item)) return false
                when (action.item.kind) {
                    ItemKind.BAIT -> {
                        bait = action.item.power
                        log += "${actor.name} feeds a ${action.item.label} to ${enemy.name}. Your next Binding cast is ${action.item.power}x as likely to tame it!"
                    }
                    ItemKind.HEAL_HP -> {
                        if (!target.alive) return refund(action.item)
                        heal(target, GameEngine.potionHeal(action.item, target.maxHp), "${actor.name} uses ${action.item.label}")
                    }
                    ItemKind.HEAL_MP -> {
                        if (!target.isHero || !target.alive) return refund(action.item)
                        val before = target.mp
                        target.mp = minOf(target.maxMp, target.mp + action.item.power.toInt())
                        log += "${actor.name} uses ${action.item.label}: ${target.name} +${target.mp - before} Energy."
                    }
                    ItemKind.REVIVE -> {
                        if (target.alive) return refund(action.item)
                        target.hp = max(1, (target.maxHp * action.item.power).roundToInt())
                        log += "${actor.name} uses ${action.item.label}: ${target.name} is back on their feet!"
                    }
                    else -> return refund(action.item)
                }
            }
            is BattleAction.Capture -> {
                if (captureBlockedReason() != null) return false
                if (action.orb.kind != ItemKind.CAPTURE || !useItem(action.orb)) return false
                val chance = captureOdds(action.orb, action.grade)
                // A berry only lasts for one throw.
                bait = 1.0
                val critical = chance < 1.0 && rng.nextDouble() < CRITICAL_CATCH
                val roll = rng.nextDouble()
                val success = critical || roll < chance
                lastCast = CastRoll(success, shakesFor(success, critical, roll, chance), critical)
                castCount++
                if (success) {
                    val tamed = enemy
                    if (critical) log += "Critical catch!"
                    log += "${actor.name} casts ${Binding.SPELL} with a ${action.orb.label}${traceNote(action)}... ${tamed.name} was tamed!"
                    capturedKeys += tamed.key
                    tamed.gone = "Tamed"
                    packReacts(tamed)
                } else {
                    log += "${actor.name} casts ${Binding.SPELL} with a ${action.orb.label}${traceNote(action)}... ${enemy.name} broke free!"
                }
            }
            is BattleAction.SnapBind -> {
                if (!snapAvailable || action.orb.kind != ItemKind.CAPTURE || action.orb == Item.PERFECT_ORB) return false
                val chance = snapOdds(action.orb)
                if (!useItem(action.orb)) return false
                val roll = rng.nextDouble()
                val success = roll < chance
                lastCast = CastRoll(success, shakesFor(success, success, roll, chance), success)
                castCount++
                val target = enemy
                if (success) {
                    log += "SNAP BIND! ${actor.name} catches ${target.name} off guard with a ${action.orb.label}: tamed!"
                    capturedKeys += target.key
                    target.gone = "Tamed"
                    // Everyone else bolts.
                    for (e in enemies.filter { it.alive }) {
                        e.gone = "Scattered"
                        log += "${e.name} scatters!"
                    }
                    scattered = true
                } else {
                    snapFailed = true
                    log += "Snap Bind! ${target.name} twists free of the ${action.orb.label}..."
                    for (e in enemies.filter { it.alive }) {
                        e.rage++
                        BattleStat.entries.forEach { st -> e.stages[st] = minOf(MAX_STAGES, (e.stages[st] ?: 0) + 1) }
                    }
                    log += (if (enemies.count { it.alive } > 1) "The pack doubles down" else "${target.name} doubles down") +
                        ": all stats up, and much harder to tame now!"
                }
            }
            is BattleAction.Swap -> if (!swapIn(action)) return false
            BattleAction.Flee -> {
                val chance = (0.5 + (actor.speed - enemy.speed) * 0.02).coerceIn(0.2, 0.95)
                if (rng.nextDouble() < chance) {
                    outcome = BattleOutcome.FLED
                    log += "You got away safely."
                } else {
                    log += "Couldn't escape!"
                }
            }
        }
        return true
    }

    /** The rest of the pack after [tamed] is tamed: the weaker may run off, the rest fly into a rage. */
    private fun packReacts(tamed: Combatant) {
        for (e in enemies.filter { it.alive }) {
            if (e.level < tamed.level) {
                // A Snare Aura on your side holds them in place.
                val snare = allies.filter { it.alive }.maxOfOrNull { it.rankOf(Trait.SNARE_AURA) } ?: 0
                val flee = PACK_FLEE_CHANCE * (1 - (if (snare > 0) Trait.SNARE_AURA.snarePct(snare) else 0) / 100.0)
                if (rng.nextDouble() < flee) {
                    e.gone = "Ran off"
                    log += "${e.name} panics and runs off!"
                } else {
                    log += "${e.name} stands its ground."
                }
            } else {
                e.rage++
                BattleStat.entries.forEach { st -> e.stages[st] = minOf(MAX_STAGES, (e.stages[st] ?: 0) + 1) }
                log += "${e.name} flies into a rage! All its stats rise" + (if (e.rage > 1) " (rage x${e.rage})!" else "!")
            }
        }
    }

    /** A fallen fighter on your side (the hero too) that someone waiting on the bench could replace. */
    fun awaitsSwap(c: Combatant): Boolean =
        !c.alive && !Combatant.isEnemy(c.key) && c in team && reserve.any { it.alive }

    /**
     * Everyone fighting on your side is down but someone on the bench can
     * still fight: it isn't over, you must send them in (no skipping).
     */
    val mustSwap: Boolean
        get() = outcome == BattleOutcome.ONGOING && team.none { it.alive } && reserve.any { it.alive }

    /**
     * Replaces a monster that has just fainted with one from the swap slots,
     * without using anyone's turn (like sending in the next one after a faint).
     */
    fun replaceFainted(inUid: String, outKey: String): Boolean {
        if (outcome != BattleOutcome.ONGOING) return false
        val out = team.find { it.key == outKey } ?: return false
        if (out.alive) return false
        val wasTurn = current?.key == outKey
        // Everyone was down: the fight was only waiting for this; it carries on in turn order.
        val forced = mustSwap
        if (!swapIn(BattleAction.Swap(inUid, outKey))) return false
        if (forced) {
            current = null
            advance()
        } else if (wasTurn) {
            // If it was the fallen one's turn, the newcomer takes it.
            current = team.find { it.key == inUid }
        }
        return true
    }

    /** Sends a monster in from the swap slots, in place of a teammate (who goes to the swap slots) or into a free spot. */
    private fun swapIn(a: BattleAction.Swap): Boolean {
        val incoming = reserve.find { it.key == a.inUid && it.alive } ?: return false
        val outKey = a.outKey
        if (outKey == null) {
            if (team.size >= slots) return false
            reserve.remove(incoming)
            team += incoming
            log += "Go, ${incoming.name}!"
        } else {
            val i = team.indexOfFirst { it.key == outKey }
            if (i < 0) return false
            val out = team[i]
            team[i] = incoming
            reserve.remove(incoming)
            reserve += out
            if (out.alive) {
                // Swapping out a fighter who's standing uses up their turn.
                queue.remove(outKey)
            } else {
                // In for someone who fell: the newcomer gets the fallen one's turn if it hasn't come yet.
                val q = queue.indexOf(outKey)
                if (q >= 0) queue[q] = incoming.key
            }
            log += when {
                !out.alive -> "${incoming.name} takes ${out.name}'s place!"
                out.isHero -> "${out.name} steps back. Go, ${incoming.name}!"
                incoming.isHero -> "${out.name}, come back! ${incoming.name} steps in!"
                else -> "${out.name}, come back! Go, ${incoming.name}!"
            }
        }
        fought += incoming.key
        record(incoming, incoming, StrikeStyle.WARD, Element.NEUTRAL)
        return true
    }

    private fun useItem(item: Item): Boolean {
        if ((countsTowardLimit(item) && itemsRemainingThisBattle <= 0) || count(item) <= 0) return false
        bag[item] = count(item) - 1
        itemsUsed[item] = (itemsUsed[item] ?: 0) + 1
        return true
    }

    private fun refund(item: Item): Boolean {
        bag[item] = count(item) + 1
        val used = (itemsUsed[item] ?: 1) - 1
        if (used <= 0) itemsUsed.remove(item) else itemsUsed[item] = used
        return false
    }

    /** Mud Sling's mud in the eyes: accuracy down, stacking to a cap. */
    private fun muddy(target: Combatant) {
        val cap = Spells.MUD_SLING_ACCURACY * Spells.MUD_SLING_STACKS
        if (target.muddiedPct >= cap) {
            log += "${target.name} can't get any muddier."
        } else {
            target.muddiedPct = minOf(cap, target.muddiedPct + Spells.MUD_SLING_ACCURACY)
            log += "${target.name}'s accuracy fell! (−${target.muddiedPct}%)"
        }
    }

    /** One stage off a foe's stat (unless it's unflappable). */
    private fun lowerStage(d: Combatant, stat: BattleStat) {
        if (d.unflappable) {
            log += "${d.name} is unflappable: its stats don't drop!"
            return
        }
        d.stages[stat] = ((d.stages[stat] ?: 0) - 1).coerceAtLeast(-4)
        log += "${d.name}'s ${stat.label} fell!"
    }

    /** A Lance bolt's effect: its type's status, or a lowered stat. */
    private fun lanceEffect(a: Combatant, d: Combatant, element: Element, m: Int) {
        val effect = SpellMastery.lanceEffect(element)
        val chance = SpellMastery.lanceChance(m) * (if (effect == SpellMastery.LanceEffect.SLEEP) 0.5 else 1.0)
        if (rng.nextDouble() >= luck(a, chance)) return
        when (effect) {
            SpellMastery.LanceEffect.SLOW -> lowerStage(d, BattleStat.SPEED)
            SpellMastery.LanceEffect.CRACK -> lowerStage(d, BattleStat.DEFENSE)
            SpellMastery.LanceEffect.SAP -> lowerStage(d, BattleStat.ATTACK)
            else -> {
                if (d.status != null) return
                val status = when (effect) {
                    SpellMastery.LanceEffect.BURN -> Status.BURN
                    SpellMastery.LanceEffect.SHOCK -> Status.SHOCK
                    SpellMastery.LanceEffect.POISON -> Status.POISON
                    else -> Status.SLEEP
                }
                if (status == Status.SLEEP) {
                    if (fallAsleep(d, 2)) log += "${d.name} fell asleep!"
                } else {
                    d.status = status
                    d.statusTurns = 3
                    log += "${d.name} is ${status.label.lowercase()}!"
                }
            }
        }
    }

    /**
     * A support spell cast along its path. Returns true if the path handled
     * the whole cast (false: the spell works as usual).
     */
    private fun castPath(actor: Combatant, target: Combatant, skill: Skill, path: Int, m: Int): Boolean {
        val pathName = SpellMastery.path(skill.id, path)?.name ?: skill.name
        val team = allies.filter { it.alive }
        fun cure(t: Combatant): Status? = t.status?.also { t.status = null; t.statusTurns = 0 }
        when (skill.kind) {
            SkillKind.HEAL -> if (path == 1) {
                val cured = cure(target)
                heal(target, (spellHeal(actor, skill) * SpellMastery.purifyBoost(m)).roundToInt(), "${actor.name} casts $pathName" + (cured?.let { " (${target.name} is no longer ${it.label.lowercase()})" } ?: ""))
            } else {
                val amount = spellHeal(actor, skill)
                val room = target.maxHp - target.hp
                heal(target, amount, "${actor.name} casts $pathName")
                val spill = ((amount - room).coerceAtLeast(0) * SpellMastery.overflowShare(m)).roundToInt()
                if (spill > 0) {
                    target.shield = max(target.shield, spill)
                    log += "The overflow shields ${target.name} (absorbs ${target.shield})."
                    record(actor, target, StrikeStyle.WARD, Element.NEUTRAL, target.shield)
                }
            }
            SkillKind.AEGIS -> {
                val single = actor.maxHp * skill.power / 100.0
                if (path == 1) {
                    val amount = (single + actor.maxHp * SpellMastery.bulwarkExtraPct(m) / 100.0).roundToInt().coerceAtLeast(1)
                    target.shield = max(target.shield, amount)
                    log += "${actor.name} casts $pathName: ${target.name} is behind a great shield (absorbs ${target.shield})."
                    record(actor, target, StrikeStyle.WARD, Element.NEUTRAL, target.shield)
                } else {
                    val amount = (single * SpellMastery.sharedShare(m)).roundToInt().coerceAtLeast(1)
                    log += "${actor.name} casts $pathName: the whole team is shielded (absorbs $amount each)."
                    for (a in team) {
                        a.shield = max(a.shield, amount)
                        record(actor, a, StrikeStyle.WARD, Element.NEUTRAL, a.shield)
                    }
                }
            }
            SkillKind.FORTIFY, SkillKind.HASTE, SkillKind.EMPOWER -> {
                val (stat, label) = when (skill.kind) {
                    SkillKind.HASTE -> BattleStat.SPEED to "Speed"
                    SkillKind.EMPOWER -> BattleStat.ATTACK to "Attack"
                    else -> BattleStat.DEFENSE to "Defense"
                }
                if (path == 1) {
                    val stages = SpellMastery.rallyStages(skill.power, m)
                    log += "${actor.name} casts $pathName: the whole team's $label rises!"
                    for (a in team) {
                        a.stages[stat] = minOf(MAX_STAGES, (a.stages[stat] ?: 0) + stages)
                        record(actor, a, StrikeStyle.WARD, Element.NEUTRAL)
                    }
                } else {
                    target.stages[stat] = minOf(MAX_STAGES, (target.stages[stat] ?: 0) + skill.power + SpellMastery.overchargeExtra(m))
                    log += "${actor.name} casts $pathName: ${target.name}'s $label surges!"
                    record(actor, target, StrikeStyle.WARD, Element.NEUTRAL)
                }
            }
            SkillKind.CLEANSE -> if (path == 1) {
                log += "${actor.name} casts $pathName!"
                val amount = (spellHeal(actor, skill) * SpellMastery.sanctuaryShare(m)).roundToInt()
                for (a in team) {
                    cure(a)
                    BattleStat.entries.forEach { st -> if ((a.stages[st] ?: 0) < 0) a.stages[st] = 0 }
                    heal(a, amount, "  $pathName")
                }
            } else {
                val cured = cure(target)
                BattleStat.entries.forEach { st -> if ((target.stages[st] ?: 0) < 0) target.stages[st] = 0 }
                heal(target, (spellHeal(actor, skill) * SpellMastery.restorationBoost(m)).roundToInt(), "${actor.name} casts $pathName" + (cured?.let { " (${target.name} is no longer ${it.label.lowercase()})" } ?: ""))
            }
            SkillKind.MEND_ALL -> {
                val amount = (spellHeal(actor, skill) * if (path == 1) SpellMastery.purifyBoost(m) else SpellMastery.radiantBoost(m)).roundToInt()
                log += "${actor.name} casts $pathName!"
                for (a in team) {
                    if (path == 1) cure(a)
                    heal(a, amount, "  $pathName")
                }
            }
            else -> return false
        }
        return true
    }

    private fun heal(target: Combatant, amount: Int, prefix: String) {
        val before = target.hp
        target.hp = minOf(target.maxHp, target.hp + amount)
        log += "$prefix: ${target.name} +${target.hp - before} HP."
        record(target, target, StrikeStyle.HEAL, Element.NEUTRAL, target.hp - before)
    }

    /** Accuracy check, including blinding effects. Logs a miss. */
    private fun rollHit(a: Combatant, d: Combatant, accuracy: Int): Boolean {
        if (a.missNext) {
            a.missNext = false
            a.accuracyDown = false
            log += "${a.name} misses!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        var chance = accuracy / 100.0 * (1 - a.muddiedPct / 100.0) *
            (1 + (a.rankOf(Trait.KEEN_SIGHT).takeIf { it > 0 }?.let { Trait.KEEN_SIGHT.keenSightPct(it) / 100.0 } ?: 0.0))
        if (a.accuracyDown) {
            chance *= 0.6
            a.accuracyDown = false
        }
        if (rng.nextDouble() >= chance) {
            log += "${a.name}'s attack misses ${d.name}!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        if (d.evasion > 0 && rng.nextDouble() < d.evasion) {
            log += "${d.name} nimbly dodges!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        return true
    }

    private fun MoveEffect.targetsFoe(): Boolean = when (this) {
        is MoveEffect.Inflict, is MoveEffect.LowerFoe, is MoveEffect.FoeAccuracyDown, is MoveEffect.FoeMissesNext,
        is MoveEffect.Flinch, is MoveEffect.Scorch, is MoveEffect.NightTerror, MoveEffect.Scare -> true
        else -> false
    }

    private fun useMove(a: Combatant, d: Combatant, move: Move, diveStrike: Boolean = false) {
        val isProtect = MoveEffect.Protect in move.effects
        if (isProtect && a.usedProtectLast) {
            a.usedProtectLast = false
            log += "${a.name} tries ${move.name}, but it fails twice in a row!"
            return
        }
        a.usedProtectLast = isProtect

        if (MoveEffect.TwoTurnDive in move.effects && !diveStrike) {
            a.diving = move
            log += "${a.name} uses ${move.name} and soars out of reach!"
            return
        }

        val hitsFoe = move.isDamaging || move.effects.any { it.targetsFoe() }
        log += "${a.name} uses ${move.name}!"
        if (!hitsFoe) {
            applyEffects(a, d, move, onFoe = true, onSelf = true)
            return
        }
        // Spread moves strike every foe (a little weaker each when there's more than one).
        val targets = if (MoveEffect.HitsAllFoes in move.effects) foesOf(a).filter { it.alive }.ifEmpty { listOf(d) } else listOf(d)
        val mult = if (targets.size > 1) SPREAD_POWER else 1.0
        var landed = false
        moveDealt = 0
        for (t in targets) {
            if (hitTarget(a, t, move, mult)) {
                applyEffects(a, t, move, onFoe = true, onSelf = false)
                landed = true
            }
        }
        if (landed) applyEffects(a, d, move, onFoe = false, onSelf = true)
        // Reckless moves: a share of the damage comes back.
        move.effects.filterIsInstance<MoveEffect.RecoilPct>().firstOrNull()?.let { fx ->
            if (a.alive && moveDealt > 0) recoil(a, maxOf(1, moveDealt * fx.percent / 100))
        }
    }

    /** Damage dealt by the move being used (for recoil). */
    private var moveDealt = 0

    /** A side effect's chance for [a]'s move: Fortune makes them likelier. */
    private fun luck(a: Combatant, chance: Double): Double {
        val r = a.rankOf(Trait.FORTUNE)
        return if (r > 0) (chance * Trait.FORTUNE.fortuneMult(r)).coerceAtMost(1.0) else chance
    }

    /** Recoil, cut by Iron Skull (none at all at rank 5). */
    private fun recoil(a: Combatant, amount: Int) {
        val skull = a.rankOf(Trait.IRON_SKULL)
        val cut = if (skull > 0) Trait.IRON_SKULL.ironSkullPct(skull) else 0
        val taken = amount * (100 - cut) / 100
        if (taken <= 0) {
            log += "${a.name}'s Iron Skull shrugs off the recoil!"
            return
        }
        hurt(a, taken, if (cut > 0) "is hurt by recoil (Iron Skull: -$cut%)" else "is hurt by recoil")
    }

    /** Speed in battle: stat stages, then Rain Runner or Sun Chaser in their weather. */
    private fun speedOf(c: Combatant): Double {
        val rain = c.rankOf(Trait.RAIN_RUNNER)
        val sun = c.rankOf(Trait.SUN_CHASER)
        val mult = when {
            weather == Weather.RAIN && rain > 0 -> Trait.RAIN_RUNNER.weatherSpeed(rain)
            weather == Weather.DROUGHT && sun > 0 -> Trait.SUN_CHASER.weatherSpeed(sun)
            else -> 1.0
        }
        return c.effectiveSpeed * mult
    }

    /** A move spent: Overbearing foes may make it cost an extra use. */
    private fun spendMove(actor: Combatant, move: Move) {
        actor.spendTurn(move)
        if (move == Moves.STRUGGLE) return
        val bully = foesOf(actor).filter { it.alive && it.rankOf(Trait.OVERBEARING) > 0 }.maxByOrNull { it.rankOf(Trait.OVERBEARING) } ?: return
        val left = actor.ppLeft(move)
        if (left > 0 && rng.nextInt(100) < Trait.OVERBEARING.overbearingPct(bully.rankOf(Trait.OVERBEARING))) {
            actor.pp[move.id] = left - 1
            log += "${bully.name} is Overbearing: ${move.name} costs ${actor.name} an extra use."
        }
    }

    /** The other side, from [a]'s point of view. */
    private fun foesOf(a: Combatant): List<Combatant> = if (Combatant.isEnemy(a.key)) allies else enemies

    /** [a]'s own side (including [a]). */
    private fun teamOf(a: Combatant): List<Combatant> = if (Combatant.isEnemy(a.key)) enemies else allies

    /** One target of a move: dodges, guards, accuracy, then damage. Returns true if it connected. */
    private fun hitTarget(a: Combatant, t: Combatant, move: Move, mult: Double): Boolean {
        val pierce = MoveEffect.Pierce in move.effects
        if (t.diving != null && move.id != "thunder_bolt") {
            log += "${t.name} is high in the air. It can't be reached!"
            return false
        }
        if (t.protecting && !pierce) {
            log += "${t.name}'s guard blocks it!"
            return false
        }
        if (t.protecting) log += "${move.name} pierces ${t.name}'s guard!"
        val smack = move.effects.filterIsInstance<MoveEffect.SmackDown>().firstOrNull()
        if (floatsOver(a, t, move.type, smackDown = smack != null)) return false
        if (MoveEffect.SureHit !in move.effects && !rollHit(a, t, move.accuracy)) return false
        if (!move.isDamaging) return true
        if (t.diving != null) log += "Sky Bolt strikes ${t.name} mid-dive!"
        val style = if (move.type == Element.NEUTRAL) StrikeStyle.SLASH else StrikeStyle.BOLT
        val crit = if (MoveEffect.HighCrit in move.effects) 0.25 else 0.0
        val multi = move.effects.filterIsInstance<MoveEffect.MultiHit>().firstOrNull()
        val hits = multi?.let { rng.nextInt(it.min, it.max + 1) } ?: 1
        // Knockdown hits a hovering foe harder and knocks it to the ground.
        val airborne = smack != null && t.immuneEarth
        // Status Bonus: hits harder when the foe is already asleep, shocked, poisoned...
        val bonus = move.effects.filterIsInstance<MoveEffect.StatusBonus>().firstOrNull()
            ?.takeIf { t.status != null }?.multiplier ?: 1.0
        // HP-scaled moves weaken as the user tires.
        val hpScale = if (MoveEffect.HpScaled in move.effects) a.hpFraction else 1.0
        val power = ((if (airborne) smack!!.groundedPower else move.power) * mult * bonus * hpScale).roundToInt().coerceAtLeast(1)
        val useDefense = MoveEffect.DefenseStrike in move.effects
        var count = 0
        var dealt = 0
        while (count < hits && t.alive) {
            dealt += strike(a, t, power, move.type, useMagic = false, verb = null, style = style, extraCrit = crit, pierce = pierce, useDefense = useDefense)
            count++
        }
        moveDealt += dealt
        if (multi != null) log += "Hit $count time${if (count == 1) "" else "s"}!"
        if (airborne && t.alive) {
            t.immuneEarth = false
            t.borrowedHover = false
            t.earthResistPct = 0
            t.grounded = true
            log += "${t.name} is knocked out of the air! Earth moves can hit it now."
        }
        move.effects.filterIsInstance<MoveEffect.LifeDrain>().firstOrNull()?.let { fx ->
            if (a.alive && dealt > 0 && a.hp < a.maxHp) heal(a, maxOf(1, dealt * fx.percent / 100), "${a.name} drains the foe")
        }
        return true
    }

    /** Hover and Airborne: Earth attacks pass beneath. Logs it and returns true if [t] is out of reach. */
    private fun floatsOver(a: Combatant, t: Combatant, type: Element, smackDown: Boolean = false): Boolean {
        if (type != Element.EARTH || !t.immuneEarth || smackDown) return false
        log += "${t.name} floats above it. Earth attacks can't reach!"
        record(a, t, StrikeStyle.MISS, Element.NEUTRAL)
        return true
    }

    /** Applies [move]'s effects: those aimed at the foe [d] ([onFoe]) and/or those on the user and its team ([onSelf]). */
    private fun applyEffects(a: Combatant, d: Combatant, move: Move, onFoe: Boolean, onSelf: Boolean) {
        for (fx in move.effects) {
            if (if (fx.targetsFoe()) !onFoe else !onSelf) continue
            when (fx) {
                is MoveEffect.Inflict -> if (d.alive && d.status == null && rng.nextDouble() < luck(a, fx.chance)) {
                    val turns = rng.nextInt(fx.minTurns, fx.maxTurns + 1)
                    if (fx.status == Status.SLEEP) {
                        if (!fallAsleep(d, turns)) continue
                    } else {
                        d.status = fx.status
                        d.statusTurns = turns
                    }
                    log += when (fx.status) {
                        Status.SLEEP -> "${d.name} fell asleep!"
                        Status.SHOCK -> "${d.name} is shocked!"
                        Status.POISON -> "${d.name} is poisoned!"
                        Status.BURN -> "${d.name} is burned!"
                        Status.FROSTBITE -> "${d.name} has frostbite!"
                    }
                } else if (move.power == 0 && d.status != null) {
                    log += "${d.name} is already ${d.status!!.label.lowercase()}."
                }
                is MoveEffect.NightTerror -> if (d.alive && d.status == null && rng.nextDouble() < luck(a, fx.chance)) {
                    if (!fallAsleep(d, rng.nextInt(3, 6))) continue
                    d.nightTerrorDamage = scaled(fx.base, a.level)
                    log += "${d.name} is gripped by a night terror!"
                } else {
                    log += "${d.name} shakes off the night terror."
                }
                is MoveEffect.LowerFoe -> if (d.alive && d.unflappable) {
                    log += "${d.name} is unflappable: its stats don't drop!"
                } else if (d.alive && rng.nextDouble() < luck(a, fx.chance)) {
                    fx.stats.forEach { d.stages[it] = ((d.stages[it] ?: 0) - 1).coerceAtLeast(-4) }
                    log += "${d.name}'s ${fx.stats.joinToString(", ") { it.label }} fell!"
                }
                is MoveEffect.SetWeather -> if (rng.nextDouble() < fx.chance && weather != fx.weather) {
                    weather = fx.weather
                    log += when (fx.weather) {
                        Weather.RAIN -> "It started to rain! Water and Electric moves are stronger."
                        Weather.DROUGHT -> "A drought sets in! Fire and Corrupt moves are stronger."
                        Weather.CLEAR -> "The weather cleared."
                    }
                }
                is MoveEffect.FoeAccuracyDown -> if (rng.nextDouble() < luck(a, fx.chance)) {
                    if (d.rankOf(Trait.KEEN_SIGHT) >= 3) {
                        log += "${d.name}'s Keen Sight keeps its aim!"
                    } else {
                        d.accuracyDown = true
                        log += "${d.name}'s accuracy fell!"
                    }
                }
                is MoveEffect.FoeMissesNext -> if (rng.nextDouble() < luck(a, fx.chance)) {
                    if (d.rankOf(Trait.UNSHAKEN) >= 3) {
                        log += "${d.name} is Unshaken!"
                    } else {
                        d.missNext = true
                        log += "${d.name} is thrown off balance!"
                    }
                }
                is MoveEffect.Flinch -> if (d.alive && d.rankOf(Trait.UNSHAKEN) == 0 && rng.nextDouble() < luck(a, fx.chance)) d.flinch = true
                is MoveEffect.Scorch -> if (d.alive) {
                    d.scorchTurns = fx.turns
                    d.scorchDamage = scaled(fx.base, a.level)
                    d.scorchVerb = fx.verb
                    log += if (fx.verb == "is scorched") "${d.name} is scorched by lingering flames!" else "${d.name} is caught in it!"
                }
                MoveEffect.Protect -> {
                    a.protecting = true
                    log += "${a.name} raises a ${move.name}!"
                }
                is MoveEffect.RaiseSelf -> if (a.alive && rng.nextDouble() < luck(a, fx.chance)) {
                    fx.stats.forEach { a.stages[it] = ((a.stages[it] ?: 0) + fx.stages).coerceAtMost(4) }
                    log += "${a.name}'s ${fx.stats.joinToString(" and ") { it.label }} ${if (fx.stages > 1) "sharply rose" else "rose"}!"
                }
                is MoveEffect.LowerSelf -> if (a.alive) {
                    fx.stats.forEach { a.stages[it] = ((a.stages[it] ?: 0) - fx.stages).coerceAtLeast(-4) }
                    log += "${a.name}'s ${fx.stats.joinToString(" and ") { it.label }} ${if (fx.stages > 1) "harshly fell" else "fell"}!"
                }
                MoveEffect.CureSelf -> if (a.status != null || a.scorchTurns > 0) {
                    a.status = null
                    a.statusTurns = 0
                    a.nightTerrorDamage = 0
                    a.scorchTurns = 0
                    log += "${a.name} shakes off its ailments!"
                }
                is MoveEffect.TeamHeal -> teamOf(a).filter { it.alive && it.hp < it.maxHp }.forEach {
                    heal(it, maxOf(1, it.maxHp * fx.percent / 100), "${a.name}'s ${move.name}")
                }
                is MoveEffect.TeamRaise -> {
                    teamOf(a).filter { it.alive }.forEach { c ->
                        fx.stats.forEach { c.stages[it] = ((c.stages[it] ?: 0) + fx.stages).coerceAtMost(4) }
                    }
                    log += "The whole team's ${fx.stats.joinToString(" and ") { it.label }} rose!"
                }
                is MoveEffect.MultiHit, MoveEffect.HighCrit, MoveEffect.SureHit, MoveEffect.HitsAllFoes, MoveEffect.Pierce, is MoveEffect.LifeDrain,
                is MoveEffect.SmackDown, is MoveEffect.StatusBonus, MoveEffect.DefenseStrike, is MoveEffect.RecoilPct, MoveEffect.HpScaled -> {}
                MoveEffect.Swift -> if (a.alive) {
                    a.swift = true
                    log += "${a.name} will move first next round!"
                }
                MoveEffect.Recharge -> a.mustRecharge = true
                is MoveEffect.HealSelf -> if (a.alive && a.hp < a.maxHp) {
                    heal(a, maxOf(1, a.maxHp * fx.percent / 100), "${a.name} restores itself")
                }
                is MoveEffect.Recoil -> if (rng.nextDouble() < fx.chance) recoil(a, scaled(fx.base, a.level))
                is MoveEffect.PowerUp -> {
                    a.powerUp += fx.percent
                    log += "${a.name}'s power rises! (+${a.powerUp}%)"
                }
                MoveEffect.Scare -> when {
                    !d.alive -> {}
                    d.unflappable -> log += "${d.name} is too unflappable to be scared."
                    d.scared -> log += "${d.name} is already shaken."
                    else -> {
                        d.scared = true
                        log += "${d.name} is scared! Its Attack drops to 67%."
                    }
                }
                MoveEffect.ReverseOrder -> {
                    reversedRounds = 4
                    log += "Wild winds swirl! The slowest now move first."
                }
                MoveEffect.TwoTurnDive -> {}
            }
        }
    }

    /** Weapon effects after the hero lands a hit: lifesteal and on-hit statuses (and Trickster feints on [basic] attacks). */
    private fun afterHeroHit(a: Combatant, d: Combatant, dmg: Int, basic: Boolean = false) {
        // A cursed weapon's power: frostbite, life drain or flame. Every hit wears the curse down.
        if (basic && dmg > 0) {
            a.weaponUses++
            when (a.weaponCurse) {
                Curse.FROST -> if (d.alive && d.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    d.status = Status.FROSTBITE
                    d.statusTurns = 4
                    log += "${d.name} has frostbite!"
                }
                Curse.FLAME -> if (d.alive && d.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    d.status = Status.BURN
                    d.statusTurns = 4
                    log += "${d.name} is set on fire!"
                }
                Curse.LIFE_DRAIN -> if (a.alive && a.hp < a.maxHp) heal(a, maxOf(1, dmg * CURSE_DRAIN_HEAL / 100), "${a.name}'s weapon drinks life")
                null -> {}
            }
        }
        if (basic && a.blindPct > 0 && d.alive && !d.accuracyDown && rng.nextInt(100) < a.blindPct) {
            d.accuracyDown = true
            log += "${a.name} feints! ${d.name} is thrown off balance."
        }
        if (a.lifesteal > 0 && dmg > 0 && a.alive) {
            heal(a, maxOf(1, (dmg * a.lifesteal).roundToInt()), "${a.name}'s weapon drains life")
        }
        val s = a.onHitStatus
        if (s != null && d.alive && d.status == null && rng.nextDouble() < 0.15) {
            d.status = s
            d.statusTurns = 4
            log += "${d.name} is ${s.label.lowercase()}!"
        }
    }

    private fun strike(
        a: Combatant, d: Combatant, power: Int, type: Element, useMagic: Boolean, verb: String?, style: StrikeStyle,
        extraCrit: Double = 0.0,
        /** Ignores the target's raised Defense and its brace. */
        pierce: Boolean = false,
        /** Defense Strike: the user's Defense does the hitting. */
        useDefense: Boolean = false,
    ): Int {
        // Lightning Eater, Spring Soak and Kindling: that type heals instead of hurting.
        val absorbTrait = d.traits.keys.firstOrNull { it.absorbs != null && it.absorbs == type }
        if (absorbTrait != null) {
            val r = d.rankOf(absorbTrait)
            log += "${d.name}'s ${absorbTrait.label} absorbs the ${type.label.lowercase()}!"
            if (d.hp < d.maxHp) heal(d, maxOf(1, d.maxHp * absorbTrait.absorbHealPct(r) / 100), absorbTrait.label)
            if (absorbTrait == Trait.KINDLING && !d.kindled) {
                d.kindled = true
                log += "${d.name}'s Fire moves grow stronger!"
            }
            record(a, d, StrikeStyle.MISS, type)
            return 0
        }
        // A raised shield (Defend) blocks the hit entirely; piercing attacks go through.
        if (d.defending && !pierce) {
            log += "${d.name}'s shield blocks it!"
            record(a, d, StrikeStyle.MISS, type)
            return 0
        }
        // Last Stand, Grit and Kindling power up the attacker.
        val lastStand = a.rankOf(Trait.LAST_STAND).takeIf { it > 0 && type in a.types && a.hpFraction <= 1.0 / 3 }
            ?.let { 1 + Trait.LAST_STAND.lastStandPct(it) / 100.0 } ?: 1.0
        val grit = a.rankOf(Trait.GRIT).takeIf { it > 0 && a.status != null && !useMagic }
            ?.let { 1 + Trait.GRIT.gritPct(it) / 100.0 } ?: 1.0
        // A burn weakens physical attacks (Grit shrugs that off and hits harder instead).
        val burned = if (a.status == Status.BURN && !useMagic && a.rankOf(Trait.GRIT) == 0) BURN_ATTACK else 1.0
        val kindle = if (a.kindled && type == Element.FIRE) 1 + Trait.KINDLING.kindlePct(a.rankOf(Trait.KINDLING)) / 100.0 else 1.0
        val atk = when {
            useMagic -> a.magic.toDouble()
            useDefense -> a.defense * a.stageMultiplier(BattleStat.DEFENSE)
            else -> a.attack * a.stageMultiplier(BattleStat.ATTACK) * (if (a.scared) SCARED_ATTACK else 1.0)
        }
        val defStage = d.stageMultiplier(BattleStat.DEFENSE).let { if (pierce) minOf(it, 1.0) else it }
        // Hard Scales: tougher while it has a status.
        val scales = d.rankOf(Trait.HARD_SCALES).takeIf { it > 0 && d.status != null }?.let { 1 + Trait.HARD_SCALES.hardScalesPct(it) / 100.0 } ?: 1.0
        val def = d.defense * defStage * scales
        val typeMult = type.multiplierAgainst(d.types)
        val sameType = if (type != Element.NEUTRAL && type in a.types) SAME_TYPE_BONUS else 1.0
        // Plated hides can't be critically hit.
        val crit = d.rankOf(Trait.PLATED) == 0 && rng.nextDouble() < 0.06 + a.critBonus + extraCrit
        val raw = power * atk / (atk + def) *
            (1 + a.level * 0.08) *
            typeMult * sameType * weather.multiplier(type) *
            (1 + a.powerUp / 100.0) *
            (0.85 + rng.nextDouble() * 0.15) *
            (if (crit) 1.5 + a.critDamagePct / 100.0 else 1.0) *
            (if (d.bracing && !pierce) 0.5 else 1.0) *
            // Subclass perks and skills.
            (if (useMagic) 1 + a.spellPct / 100.0 else 1.0) *
            (1 + a.ragePct / 100.0 * (1 - a.hpFraction)) *
            (1 - d.hidePct / 100.0) *
            (if (type == Element.EARTH) 1 - d.earthResistPct / 100.0 else 1.0) *
            lastStand * grit * kindle * burned *
            // Insulated takes less Fire and Electric; Plated less from super-effective hits.
            (if ((type == Element.FIRE || type == Element.ELECTRIC) && d.rankOf(Trait.INSULATED) > 0) 1 - Trait.INSULATED.insulatedPct(d.rankOf(Trait.INSULATED)) / 100.0 else 1.0) *
            (if (typeMult > 1.0 && d.rankOf(Trait.PLATED) > 0) 1 - Trait.PLATED.platedPct(d.rankOf(Trait.PLATED)) / 100.0 else 1.0)
        var dmg = max(1, raw.roundToInt())
        // Stalwart: once a battle, a knockout blow at (nearly) full HP leaves it at 1 HP.
        val stalwart = d.rankOf(Trait.STALWART)
        if (stalwart > 0 && !d.stalwartUsed && dmg >= d.hp + d.shield &&
            d.hp * 100 >= d.maxHp * Trait.STALWART.stalwartMinPct(stalwart)
        ) {
            d.stalwartUsed = true
            dmg = d.hp - 1 + d.shield
            log += "${d.name} is Stalwart: it holds on with 1 HP!"
        }
        if (d.steadfast && !d.steadfastUsed && dmg >= d.hp + d.shield && d.hp > 1) {
            d.steadfastUsed = true
            dmg = d.hp - 1 + d.shield
            log += "${d.name} won't let you down: Steadfast, it holds on with 1 HP!"
        }
        val absorbed = minOf(d.shield, dmg)
        d.shield -= absorbed
        d.hp = max(0, d.hp - (dmg - absorbed))
        record(a, d, style, type, dmg, crit, typeMult > 1.0)
        val notes = buildList {
            if (absorbed > 0) add(if (d.shield > 0) "The ward absorbs $absorbed." else "The ward absorbs $absorbed and shatters!")
            if (crit) add("Critical!")
            if (typeMult > 1.0) add("Super effective!")
            if (typeMult < 1.0) add("Not very effective...")
        }.joinToString(" ")
        log += (if (verb != null) "${a.name} $verb ${d.name} for $dmg damage. $notes" else "It hits ${d.name} for $dmg damage. $notes").trim()
        if (!d.alive) log += "${d.name} falls!"
        secondWind(d)
        // The team's borrowed hover breaks when the hero is hit (the hero keeps hovering).
        if (d.isHero && dmg > 0) breakTeamHover(d)
        // Cursed armor strikes back at physical attackers (and every hit wears the curse down).
        if (!useMagic && d.isHero && dmg > 0) {
            d.armorHits++
            for (c in d.armorCurses) when (c) {
                Curse.FROST -> if (a.alive && a.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    a.status = Status.FROSTBITE
                    a.statusTurns = 4
                    log += "${d.name}'s frozen armor gives ${a.name} frostbite!"
                }
                Curse.FLAME -> if (a.alive && a.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    a.status = Status.BURN
                    a.statusTurns = 4
                    log += "${d.name}'s burning armor sets ${a.name} alight!"
                }
                Curse.LIFE_DRAIN -> if (a.alive && d.alive) {
                    val stolen = maxOf(1, dmg * CURSE_ARMOR_STEAL / 100).coerceAtMost(a.hp)
                    a.hp -= stolen
                    d.hp = minOf(d.maxHp, d.hp + stolen)
                    log += "${d.name}'s armor steals $stolen HP from ${a.name}."
                    if (!a.alive) log += "${a.name} falls!"
                }
            }
        }
        // Barbed Hide cuts whoever strikes it.
        val barbs = d.rankOf(Trait.BARBED_HIDE)
        if (barbs > 0 && a.alive && a !== d && dmg > 0) {
            hurt(a, maxOf(1, a.maxHp * Trait.BARBED_HIDE.barbedPct(barbs) / 100), "is cut by ${d.name}'s Barbed Hide")
        }
        // Crackle Skin, Ember Hide and Venom Barbs punish whoever strikes in close.
        // Spore Coat: sleep, poison or shock, whichever the spores bring.
        val touch = d.touchStatus ?: if (d.rankOf(Trait.SPORE_COAT) > 0) listOf(Status.SLEEP, Status.POISON, Status.SHOCK).random(rng) else null
        if (touch != null && !useMagic && a.alive && a.status == null && rng.nextInt(100) < d.touchPct) {
            a.status = touch
            a.statusTurns = 3
            log += "${d.name}'s ${d.traits.keys.first { it in TOUCH_TRAITS }.label}: ${a.name} is ${touch.label.lowercase()}!"
        }
        return dmg
    }

    private fun pickTarget(): Combatant? {
        val targets = allies.filter { it.alive }
        if (targets.isEmpty()) return null
        val reachable = targets.filter { it.diving == null }.ifEmpty { targets }
        return if (rng.nextDouble() < 0.35) reachable.minBy { it.hpFraction } else reachable.random(rng)
    }

    /** Enemy AI: usually its best damaging move, sometimes a useful status move. */
    private fun enemyTurn(enemy: Combatant) {
        val target = pickTarget() ?: return
        val usable = enemy.usableMoves.filter { m ->
            when {
                MoveEffect.Protect in m.effects -> !enemy.usedProtectLast && enemy.hpFraction < 0.5
                m.isDamaging -> true
                m.effects.any { it is MoveEffect.Inflict || it is MoveEffect.NightTerror } -> target.status == null
                m.effects.any { it is MoveEffect.SetWeather } -> m.effects.filterIsInstance<MoveEffect.SetWeather>().none { it.weather == weather }
                m.effects.contains(MoveEffect.ReverseOrder) -> reversedRounds == 0
                m.effects.contains(MoveEffect.CureSelf) && m.effects.none { it is MoveEffect.RaiseSelf } -> enemy.status != null
                m.effects.any { it is MoveEffect.HealSelf } && !m.isDamaging -> enemy.hpFraction < 0.6
                m.effects.any { it is MoveEffect.TeamHeal } -> enemies.any { it.alive && it.hpFraction < 0.6 }
                m.effects.any { it is MoveEffect.RaiseSelf } -> m.effects.filterIsInstance<MoveEffect.RaiseSelf>().any { r -> r.stats.any { (enemy.stages[it] ?: 0) < 2 } }
                else -> true
            }
        }.ifEmpty { enemy.usableMoves.ifEmpty { listOf(Moves.STRUGGLE) } }
        val move = if (rng.nextDouble() < 0.7) {
            usable.maxBy { m ->
                val smack = m.effects.filterIsInstance<MoveEffect.SmackDown>().firstOrNull()
                when {
                    !m.isDamaging -> 25.0
                    // Earth moves pass under a hovering target, except Knockdown, which hits harder.
                    m.type == Element.EARTH && target.immuneEarth -> if (smack != null) smack.groundedPower * m.type.multiplierAgainst(target.types) else 0.0
                    else -> m.expectedPower * m.type.multiplierAgainst(target.types)
                }
            }
        } else {
            usable.random(rng)
        }
        spendMove(enemy, move)
        useMove(enemy, target, move)
    }

    // ---- result -------------------------------------------------------------

    fun result(): BattleResult {
        check(outcome != BattleOutcome.ONGOING) { "Battle still in progress" }
        val lvl = enemies.maxOf { it.level }
        val bossMult = if (isBoss) 3 else 1
        val rewardMult = when (encounter) {
            // Matched fights are short, so a lone hero is paid like a team of 2 (x1.25, not x0.6).
            EncounterType.WILD -> TeamScaling.reward(if (fullTeam) teamSize else maxOf(teamSize, 2)) * TeamScaling.packReward(enemies.size)
            EncounterType.DEN -> TeamScaling.reward(teamSize) * Dens.REWARD_MULTIPLIER
            // A thug's crew grows with your team, so the pay follows its size.
            EncounterType.THUG, EncounterType.DUNGEON_GUARD -> enemies.size * THUG_REWARD
            // Paid by the nomad instead (see Wanderers.nomadReward); this is the usual win.
            EncounterType.NOMAD -> (enemies.size + retired.size) * THUG_REWARD
            else -> TeamScaling.bossReward(teamSize)
        }
        // Wayfarer, Scholar and Treasure Hunter add to the spoils.
        val xpMult = 1 + heroPerks.xpPct / 100.0
        val tamedMonsters = capturedKeys.map { monsterFor(it) }
        // Win XP for the fight, plus taming XP for each one tamed (kept even if you flee or fall after).
        val heroXp = ((if (outcome == BattleOutcome.WON) lvl * 3 * bossMult else 0) +
            tamedMonsters.sumOf { GameEngine.captureXp(it.level) * bossMult }).let {
            // Foes far below you are worth little.
            (it * rewardMult * xpMult * XpRules.gapFactor(heroLevel, lvl)).roundToInt()
        }
        val gold = if (outcome == BattleOutcome.WON) {
            ((lvl * 3 + rng.nextInt(lvl * 2 + 1)) * (if (isBoss) 4 else 1) * rewardMult * (1 + heroPerks.goldPct / 100.0)).roundToInt()
        } else {
            0
        }
        val monsterXp = if (outcome == BattleOutcome.WON || outcome == BattleOutcome.CAPTURED || tamedMonsters.isNotEmpty()) {
            partyUids.associateWith { uid ->
                val gap = XpRules.gapFactor(combatant(uid)?.level ?: lvl, lvl)
                val base = (lvl * 12 * (if (isBoss) 2 else 1) * rewardMult * xpMult * gap).roundToInt()
                // Swap-slot monsters that never stepped in get half, like those who fell.
                if (uid in fought && combatant(uid)?.alive == true) base else base / 2
            }
        } else {
            emptyMap()
        }
        val drops = mutableMapOf<Item, Int>()
        if (outcome == BattleOutcome.WON) {
            if (encounter == EncounterType.SILVER_BOSS) {
                drops[Item.RESONANT_CRYSTAL] = 2
                drops[Item.GREATER_POTION] = 1
            } else if (isBoss) {
                drops[Item.SOVEREIGN_CRYSTAL] = 1
                drops[Item.GREATER_POTION] = 2
            } else {
                // Stronger (team-scaled) monsters drop more, and better.
                if (rng.nextDouble() < (0.25 * rewardMult).coerceAtMost(0.9)) {
                    drops[listOf(Item.POTION, Item.BINDING_CRYSTAL, Item.MANA_DRAUGHT, Item.SWEET_BERRY).random(rng)] = 1
                    // Now and then a wild monster drops a rare Golden Berry.
                    if (rng.nextDouble() < 0.03) drops[Item.GOLDEN_BERRY] = 1
                }
                if (teamSize >= 3 && rng.nextDouble() < (0.2 * (teamSize - 2)).coerceAtMost(0.75)) {
                    val bonus = if (rng.nextDouble() < 0.04 * (teamSize - 1)) Item.RESTORE_POTION
                    else listOf(Item.GREATER_POTION, Item.RESONANT_CRYSTAL, Item.PHOENIX_FEATHER).random(rng)
                    drops[bonus] = (drops[bonus] ?: 0) + 1
                }
            }
        }
        // Scavengers on your side sometimes turn something up.
        if (outcome == BattleOutcome.WON || outcome == BattleOutcome.CAPTURED) {
            for (uid in partyUids) {
                val r = combatant(uid)?.rankOf(Trait.SCAVENGER) ?: 0
                if (r > 0 && rng.nextInt(100) < Trait.SCAVENGER.scavengePct(r)) {
                    val found = listOf(Item.POTION, Item.BINDING_CRYSTAL, Item.SWEET_BERRY, Item.MANA_DRAUGHT).random(rng)
                    drops[found] = (drops[found] ?: 0) + 1
                    log += "${combatant(uid)?.name} scavenged a ${found.label}!"
                }
            }
        }
        val captured = tamedMonsters.map { it.copy(uid = newId(rng), xp = 0) }
        val fainted = partyUids.filter { combatant(it)?.alive == false }.toSet()
        // Damage carries over: remember how hurt everyone still standing is.
        val damage = (allies + reserve).filter { it.alive }.associate { it.key to (it.maxHp - it.hp) }
        val heroFell = !heroFighter.alive
        return BattleResult(
            outcome, encounter, captured.firstOrNull() ?: enemyMonster, heroXp, gold, monsterXp, itemsUsed.toMap(), drops, captured.firstOrNull(), shrineId, fainted,
            damage, heroFell, heroFighter.maxMp - heroFighter.mp,
            heroFighter.weaponUses, heroFighter.armorHits,
            // Only the ones knocked out count as beaten (not the tamed or the ones that ran).
            if (outcome == BattleOutcome.WON) (retired + enemyMonsters.filterIndexed { i, _ -> enemies[i].hp <= 0 }).map { it.speciesId } else emptyList(),
            standing = allies.count { !it.isHero && it.alive },
            tamed = captured,
            scattered = scattered,
            fought = fought.filter { it in partyUids }.toSet(),
        )
    }
}
