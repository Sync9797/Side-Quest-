package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

const val MAX_LOOT_BOXES = 6
const val MAX_UNLOCKING_BOXES = 2
const val METERS_PER_MILE = 1609.344
/** Rough average; used to turn the phone's step counter into distance. */
const val STEPS_PER_MILE = 2000

/**
 * Box levels and what they hold:
 * 1. gold and potions
 * 2. + weapon parts
 * 3. + spell sheets (pages)
 * 4. + a boss encounter (20% legendary); only from sessions of an hour or more
 */
@Serializable
enum class BoxRarity(
    val label: String,
    /** Distance to walk before the box opens. */
    val miles: Int,
) {
    COMMON("Common", 1),
    RARE("Rare", 3),
    EPIC("Epic", 5),
    LEGENDARY("Legendary", 10);

    val level: Int get() = ordinal + 1
    val meters: Double get() = miles * METERS_PER_MILE

    val contents: String
        get() = when (this) {
            COMMON -> "Gold and potions"
            RARE -> "Gold, potions and weapon parts"
            EPIC -> "Gold, potions, weapon parts and spell sheets"
            LEGENDARY -> "Everything from Level 3, plus a boss encounter (20% legendary)"
        }
}

/** Result of opening a box: the loot, and a boss to fight for Level 4 boxes. */
data class BoxOpening(val update: Update, val boss: Monster? = null)

@Serializable
data class LootBox(
    val id: String,
    val rarity: BoxRarity,
    val earnedMs: Long,
    /** Only unlocking boxes collect walking distance. */
    val unlocking: Boolean = false,
    val walkedMeters: Double = 0.0,
    /** Earned in a long (90+ minute) phone-free session: half the walk to open. */
    val halfWalk: Boolean = false,
) {
    /** Distance to walk to open it. */
    val meters: Double get() = rarity.meters * if (halfWalk) 0.5 else 1.0
    val miles: Double get() = rarity.miles * if (halfWalk) 0.5 else 1.0
    /** "3", "2.5": the walk in miles, for messages. */
    val milesLabel: String get() = if (miles % 1.0 == 0.0) miles.toInt().toString() else "%.1f".format(miles)
    val ready: Boolean get() = walkedMeters >= meters
    val progress: Double get() = (walkedMeters / meters).coerceIn(0.0, 1.0)
}

/** Loot boxes: earned from phone-free sessions, opened by walking. */
object LootBoxes {

    /** Odds (percent) of each box rarity for a session of [minutes]. Longer sessions, better boxes. */
    fun odds(minutes: Int): Map<BoxRarity, Int> = when {
        // Two hours off the phone is the jackpot: always Epic or Legendary.
        minutes >= 120 -> mapOf(BoxRarity.EPIC to 50, BoxRarity.LEGENDARY to 50)
        minutes >= 90 -> mapOf(BoxRarity.RARE to 45, BoxRarity.EPIC to 40, BoxRarity.LEGENDARY to 15)
        minutes >= 60 -> mapOf(BoxRarity.COMMON to 25, BoxRarity.RARE to 45, BoxRarity.EPIC to 25, BoxRarity.LEGENDARY to 5)
        // Level 4 boxes need at least an hour off the phone.
        minutes >= 45 -> mapOf(BoxRarity.COMMON to 40, BoxRarity.RARE to 47, BoxRarity.EPIC to 13)
        minutes >= 30 -> mapOf(BoxRarity.COMMON to 60, BoxRarity.RARE to 35, BoxRarity.EPIC to 5)
        else -> mapOf(BoxRarity.COMMON to 85, BoxRarity.RARE to 15)
    }

    fun roll(minutes: Int, rng: Random): BoxRarity {
        var r = rng.nextInt(100)
        for ((rarity, pct) in odds(minutes)) {
            r -= pct
            if (r < 0) return rarity
        }
        return BoxRarity.COMMON
    }

    fun startUnlocking(state: GameState, id: String): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        if (box.unlocking) return Update(state, "Already unlocking.")
        if (state.lootBoxes.count { it.unlocking } >= MAX_UNLOCKING_BOXES) {
            return Update(state, "You can only unlock $MAX_UNLOCKING_BOXES boxes at a time.")
        }
        return Update(
            state.copy(lootBoxes = state.lootBoxes.map { if (it.id == id) it.copy(unlocking = true) else it }),
            "${box.rarity.label} box is unlocking. Walk ${box.milesLabel} mile(s) to open it!",
        )
    }

    /** Credits walked distance to every unlocking box. */
    fun addWalk(state: GameState, meters: Double): GameState {
        if (meters <= 0 || state.lootBoxes.none { it.unlocking && !it.ready }) return state
        return state.copy(
            lootBoxes = state.lootBoxes.map {
                if (it.unlocking && !it.ready) it.copy(walkedMeters = minOf(it.meters, it.walkedMeters + meters)) else it
            },
        )
    }

    /** A new reading from the phone's step counter (total steps since boot). See [GameEngine.onStepCount]. */
    fun onStepCount(state: GameState, totalSteps: Long): GameState = GameEngine.onStepCount(state, totalSteps).state

    private val potions = listOf(Item.POTION, Item.POTION, Item.POTION, Item.GREATER_POTION, Item.MANA_DRAUGHT, Item.PHOENIX_FEATHER)

    /** Chance that a Level 4 box's boss is a legendary. */
    const val LEGENDARY_BOSS_CHANCE = 0.2

    fun open(state: GameState, id: String, rng: Random = Random.Default): BoxOpening {
        val box = state.lootBoxes.find { it.id == id } ?: return BoxOpening(Update(state, "Box not found."))
        if (!box.ready) {
            return BoxOpening(Update(state, "Keep walking! %.1f of %s miles.".format(box.walkedMeters / METERS_PER_MILE, box.milesLabel)))
        }
        val level = box.rarity.level
        val gold = when (level) {
            1 -> rng.nextInt(30, 61)
            2 -> rng.nextInt(60, 121)
            3 -> rng.nextInt(120, 201)
            else -> rng.nextInt(200, 301)
        }
        val items = mutableMapOf<Item, Int>()
        repeat(level + 1) {
            val p = if (rng.nextDouble() < 0.08) Item.RESTORE_POTION else potions.random(rng)
            items[p] = (items[p] ?: 0) + 1
        }
        // Rarely, a Farwisp: likelier in better boxes.
        if (rng.nextDouble() < Farwisp.boxChance(level)) items[Item.FARWISP] = (items[Item.FARWISP] ?: 0) + 1
        val parts = when (level) {
            1 -> 0
            2 -> rng.nextInt(2, 4)
            3 -> rng.nextInt(3, 5)
            else -> rng.nextInt(4, 6)
        }
        val pages = mutableMapOf<String, Int>()
        val unknown = GameEngine.pageCandidates(state)
        if (level >= 3 && unknown.isNotEmpty()) {
            repeat(if (level == 3) rng.nextInt(2, 4) else rng.nextInt(3, 5)) {
                val sp = unknown.random(rng)
                pages[sp.id] = (pages[sp.id] ?: 0) + 1
            }
        }
        var s = state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }, gold = state.gold + gold)
        val granted = GameEngine.grantLoot(s, GameEngine.Loot(items = items, pages = pages, parts = GameEngine.rollParts(parts, rng, state.hero.level)))
        s = granted.state
        val text = "Level $level box: +$gold gold, ${granted.message}."
        if (level < 4) return BoxOpening(Update(s, text))

        val boss = boxBoss(state.hero.level, rng)
        val kind = if (boss.species.rarity == Rarity.LEGENDARY) "a LEGENDARY" else "a"
        return BoxOpening(Update(s, "$text Something stirs inside... $kind ${boss.species.name} appears!"), boss)
    }

    /** The boss inside a Level 4 box: 20% a legendary, otherwise a top-stage monster. */
    fun boxBoss(heroLevel: Int, rng: Random): Monster {
        val legendary = rng.nextDouble() < LEGENDARY_BOSS_CHANCE
        val pool = if (legendary) Bestiary.all.filter { it.bossOnly } else Bestiary.bosses.filter { !it.bossOnly }
        val m = Monster.create(pool.random(rng), heroLevel + 4, rng)
        return GameEngine.withPerfectStats(m, 2, rng)
    }

    fun discard(state: GameState, id: String): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        return Update(state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }), "Discarded a ${box.rarity.label} box.")
    }
}
