package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Relics: named Legendary weapons of old legend, found only late in the
 * game (from level [MIN_LEVEL]): from champions of 5-star and Mythic
 * dungeons, and now and then in a star reward box. Each has a fixed element,
 * fixed powers and a line of lore.
 */
@Serializable
enum class Relic(val title: String, val type: WeaponType, val element: Element, val effects: List<GearEffect>, val lore: String) {
    KINGSFLAME(
        "Kingsflame, the Blade Reforged", WeaponType.SWORD, Element.FIRE, listOf(GearEffect.MIGHTY, GearEffect.KEEN, GearEffect.GUARDED),
        "Broken in the last great war and forged anew for a king who came home.",
    ),
    GLOWFANG(
        "Glowfang", WeaponType.DAGGER, Element.ELECTRIC, listOf(GearEffect.KEEN, GearEffect.SWIFT, GearEffect.LUCKY),
        "An elven knife that glows blue whenever foes draw near.",
    ),
    WHITESTAFF(
        "The Wanderer's Whitestaff", WeaponType.STAFF, Element.NEUTRAL, listOf(GearEffect.FOCUSED, GearEffect.ARCANE, GearEffect.GUARDED),
        "Carried into the deep dark by a grey-cloaked wanderer, and out again in white.",
    ),
    OATHKEEPER(
        "Oathkeeper Greatsword", WeaponType.BROADSWORD, Element.NEUTRAL, listOf(GearEffect.MIGHTY, GearEffect.GUARDED, GearEffect.VAMPIRIC),
        "Sworn by the riders of the plains to answer whenever the beacons burn.",
    ),
    LANTERNLIGHT(
        "Lanternlight Wand", WeaponType.WAND, Element.WATER, listOf(GearEffect.ARCANE, GearEffect.FOCUSED, GearEffect.SWIFT),
        "A star's light caught in still water: a light in dark places when all others go out.",
    ),
    DEEPDELVER(
        "Deepdelver's Axe", WeaponType.AXE, Element.EARTH, listOf(GearEffect.MIGHTY, GearEffect.GUARDED, GearEffect.KEEN),
        "Forged under the mountain by a stubborn smith-king, for halls that were lost.",
    ),
    SILVERLEAF(
        "Silverleaf Longbow", WeaponType.BOW, Element.FLIGHT, listOf(GearEffect.SWIFT, GearEffect.KEEN, GearEffect.LUCKY),
        "Strung by the archers of a golden wood; its arrows sing as they fly.",
    ),
    WRAITHREAPER(
        "Wraithreaper", WeaponType.SCYTHE, Element.CORRUPT, listOf(GearEffect.VAMPIRIC, GearEffect.FOCUSED, GearEffect.MIGHTY),
        "Taken from a ring-bound king of shadows. Sometimes, it whispers.",
    ),
    ;

    companion object {
        const val MIN_LEVEL = 50

        /** [relic] as a weapon at [level]: Legendary, diamond, with its own powers. */
        fun forge(relic: Relic, level: Int, rng: Random): Gear = Gear(
            id = newId(rng), type = relic.type, rarity = GearRarity.LEGENDARY, level = level, element = relic.element,
            effects = relic.effects, material = Material.DIAMOND, relic = relic,
        )

        /** With [chance], a random relic for a hero of [level] (none below [MIN_LEVEL]). */
        fun roll(level: Int, rng: Random, chance: Double): Gear? =
            if (level < MIN_LEVEL || rng.nextDouble() >= chance) null else forge(entries.random(rng), level, rng)
    }
}

/**
 * Level 50: exotic weapons join the world, and the hero picks one of three
 * mystery chests, each holding a different one (an Epic axe, bow or scythe).
 */
object ExoticChests {
    const val LEVEL = EXOTIC_LEVEL
    val TYPES = listOf(WeaponType.AXE, WeaponType.BOW, WeaponType.SCYTHE)

    fun due(state: GameState): Boolean = state.hero.level >= LEVEL && !state.exoticChestOpened

    /** What's in the three chests (the same each time you look). */
    fun chests(state: GameState): List<Gear> {
        val rng = Random(state.hero.name.hashCode() * 31L + LEVEL)
        return TYPES.shuffled(rng).map { Armory.roll(maxOf(state.hero.level, LEVEL), rng, type = it, luck = 20, minimum = GearRarity.EPIC) }
    }

    fun open(state: GameState, index: Int): Update {
        if (!due(state)) return Update(state, "")
        val w = chests(state).getOrNull(index) ?: return Update(state, "")
        val s = state.copy(gear = state.gear + w, exoticChestOpened = true)
        return Update(s, "You open the chest: a ${w.rarity.label} ${w.name}! ${w.type.description}.", rewards = listOf(Reward.WeaponDrop(w)), rewardTitle = "A new weapon")
    }
}
