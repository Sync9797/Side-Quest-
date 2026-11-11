package com.monsterquest.app

import com.monsterquest.core.Battle
import com.monsterquest.core.BattleEvent
import com.monsterquest.core.BattleOutcome
import com.monsterquest.core.Combatant
import com.monsterquest.core.Element
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Status
import com.monsterquest.core.EncounterType
import com.monsterquest.core.Item
import com.monsterquest.core.Move
import com.monsterquest.core.Skill
import com.monsterquest.core.Weather

/** Immutable snapshot of a [Battle] for the UI (the engine itself is mutable). */
data class BattleView(
    val allies: List<FighterView>,
    /** The enemy your attacks aim at. */
    val enemy: FighterView,
    /** Every enemy (one, or a pack of up to 4). */
    val enemies: List<FighterView> = listOf(enemy),
    val log: List<String>,
    val current: FighterView?,
    val outcome: BattleOutcome,
    val encounter: EncounterType,
    val round: Int,
    val itemsLeft: Int,
    val spells: List<Skill>,
    /** The current monster's 4 moves (empty for the hero). */
    val moves: List<Move>,
    val weather: Weather,
    val reversedOrder: Boolean,
    val items: List<Pair<Item, Int>>,
    val orbs: List<OrbOption>,
    val captureBlocked: String?,
    val resultText: String?,
    /** Villain fights: the next monster to face after this win. */
    val nextOpponent: String? = null,
    /** Every attack, heal and miss so far, for animations. */
    val events: List<BattleEvent> = emptyList(),
    /** Fighter keys in the order they'll act, starting with the one acting now. */
    val turnOrder: List<String> = emptyList(),
    /** Fighters on your side (hero + monsters); wild enemies scale with it. */
    val teamSize: Int = 1,
    /** How many of your side can fight at once (theirs, or the whole team in a den). */
    val slots: Int = 6,
    val fullTeam: Boolean = true,
    /** Who levelled up in this fight (shown once it's over). */
    val levelUps: List<com.monsterquest.core.LevelUp> = emptyList(),
    /** Monsters in the swap slots, ready to be sent in. */
    val swaps: List<FighterView> = emptyList(),
    /** Everyone out front is down but someone on the bench can fight: send them in (no skipping). */
    val mustSwap: Boolean = false,
    /** Species you've already caught: marked next to the foe's name. */
    val caught: Set<String> = emptySet(),
    /** Berries you can feed the foe now, with how many you have. */
    val berries: List<Pair<Item, Int>> = emptyList(),
    /** The boost a berry the foe ate gives your next throw (1 = none). */
    val bait: Double = 1.0,
    /** The last Binding cast (for the shake animation) and how many there have been. */
    val castRoll: com.monsterquest.core.CastRoll? = null,
    val castId: Int = 0,
    /** Settings > Simple casting: tap a crystal instead of tracing the glyph. */
    val simpleCasting: Boolean = false,
    /** Uses left of each of the current monster's moves (PP), by move id. */
    val movePp: Map<String, Int> = emptyMap(),
    /** HP each of the current fighter's healing spells restores, by spell id. */
    /** HP each healing spell would restore (per ally it reaches), by spell id. */
    val spellHeals: Map<String, Int> = emptyMap(),
    /** Healing spells that reach every ally (Healing Circle, Sanctuary). */
    val spellHealsAll: Set<String> = emptySet(),
    /** Overflow: share of healing past full HP that becomes a shield, by spell id. */
    val spellOverflow: Map<String, Double> = emptyMap(),
    /** The foes struck first (a monster or a thug jumped you). */
    val ambush: Boolean = false,
    /** Snap Bind (first turn only): each crystal you have, how many, and its odds. Empty when it's not available. */
    val snapOdds: List<Triple<Item, Int, Double>> = emptyList(),
) {
    /** True if you've already caught a monster of [f]'s species. */
    fun alreadyCaught(f: FighterView): Boolean = f.speciesId != null && f.speciesId in caught

    fun fighter(key: String): FighterView? = enemies.find { it.key == key } ?: allies.find { it.key == key }

    val isPack: Boolean get() = enemies.size > 1

    val isBoss: Boolean get() = encounter != EncounterType.WILD

    companion object {
        fun of(
            b: Battle,
            resultText: String?,
            nextOpponent: String? = null,
            levelUps: List<com.monsterquest.core.LevelUp> = emptyList(),
        ): BattleView {
            val cur = b.current
            return BattleView(
                allies = b.allies.map { FighterView.of(it) },
                enemy = FighterView.of(b.enemy),
                enemies = b.enemies.map { FighterView.of(it) },
                log = b.log.toList(),
                current = cur?.let { FighterView.of(it) },
                outcome = b.outcome,
                encounter = b.encounter,
                round = b.round,
                itemsLeft = b.itemsRemainingThisBattle,
                spells = cur?.let { b.spellsFor(it) } ?: emptyList(),
                moves = cur?.moves ?: emptyList(),
                weather = b.weather,
                reversedOrder = b.reversedRounds > 0,
                items = b.battleItems().map { it to b.count(it) },
                // Odds shown include the 2% critical catch.
                orbs = b.orbs().map { o -> OrbOption(o, b.count(o), b.catchOdds(o), com.monsterquest.core.TraceGrade.entries.associateWith { g -> b.catchOdds(o, g) }) },
                captureBlocked = b.captureBlockedReason(),
                resultText = resultText,
                nextOpponent = nextOpponent,
                events = b.events.toList(),
                turnOrder = if (b.outcome == BattleOutcome.ONGOING) b.turnOrder() else emptyList(),
                teamSize = b.teamSize,
                slots = b.slots,
                fullTeam = b.fullTeam,
                levelUps = levelUps,
                swaps = b.swapsWaiting.map { FighterView.of(it) },
                mustSwap = b.mustSwap,
                berries = b.berries().filter { b.canFeed(it) }.map { it to b.count(it) },
                bait = b.bait,
                castRoll = b.lastCast,
                castId = b.castCount,
                movePp = cur?.let { c -> c.moves.associate { it.id to c.ppLeft(it) } }.orEmpty(),
                spellHeals = cur?.let { c -> b.spellsFor(c).mapNotNull { sp -> b.healPreview(c, sp)?.let { sp.id to it } }.toMap() }.orEmpty(),
                spellHealsAll = cur?.let { c -> b.spellsFor(c).filter { b.healsEveryone(c, it) }.map { it.id }.toSet() }.orEmpty(),
                spellOverflow = cur?.let { c -> b.spellsFor(c).mapNotNull { sp -> b.overflowShare(c, sp)?.let { sp.id to it } }.toMap() }.orEmpty(),
                ambush = b.ambush,
                snapOdds = if (b.snapAvailable) {
                    Item.entries.filter { it.kind == com.monsterquest.core.ItemKind.CAPTURE && it != Item.PERFECT_ORB && b.count(it) > 0 }
                        .map { Triple(it, b.count(it), b.snapOdds(it)) }
                } else {
                    emptyList()
                },
            )
        }
    }
}

/** A crystal you can cast Binding with: its odds for a Good trace and for each grade. */
data class OrbOption(val item: Item, val count: Int, val odds: Double, val gradeOdds: Map<com.monsterquest.core.TraceGrade, Double> = emptyMap())

data class FighterView(
    val key: String,
    val name: String,
    /** For artwork: monster species id, or the hero's class. */
    val speciesId: String?,
    val heroClass: HeroClass?,
    val element: Element,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val isHero: Boolean,
    /** Status and stat-change labels, e.g. "Asleep", "Def ↓1". */
    val tags: List<String>,
    val status: Status?,
    val cursed: Boolean = false,
    val shiny: Boolean = false,
    /** An enemy thug fighting with their monsters. */
    val thug: Boolean = false,
    /** A dual-type monster's second type. */
    val element2: Element? = null,
    /** Left the fight without being beaten: "Tamed" or "Ran off". */
    val gone: String? = null,
) {
    val tamed: Boolean get() = gone == "Tamed"
    /** One or two types. */
    val types: List<Element> get() = listOfNotNull(element, element2)
    val typeLabel: String get() = types.joinToString(" / ") { it.label }

    val alive: Boolean get() = hp > 0 && gone == null
    val hpFraction: Float get() = hp.toFloat() / maxHp

    companion object {
        fun of(c: Combatant) = FighterView(
            c.key, c.name, c.speciesId, c.heroClass, c.element, c.level, c.hp, c.maxHp, c.mp, c.maxMp, c.isHero, c.tags(), c.status, c.cursed, c.shiny, c.thug,
            element2 = c.element2,
            gone = c.gone,
        )
    }
}
