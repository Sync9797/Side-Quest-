package com.monsterquest.core

import kotlin.random.Random

/**
 * Star ranks: every 50 hero levels earns a star. The level shown starts over
 * (level 51 shows as ★1 · Lv 1, level 101 as ★2 · Lv 1), but the hero's real
 * level keeps counting underneath, so nothing drops: enemies, the monster
 * level limit and XP all go on following the real level. Each new star brings
 * a star reward box.
 */
object Stars {
    const val LEVELS_PER_STAR = 50

    /** Stars earned at real level [level]. */
    fun of(level: Int): Int = ((level - 1) / LEVELS_PER_STAR).coerceAtLeast(0)

    /** The level shown within the current star (1..50). */
    fun shownLevel(level: Int): Int = (level - 1).coerceAtLeast(0) % LEVELS_PER_STAR + 1

    /** "Lv 37", or "★2 · Lv 4". */
    fun label(level: Int): String = if (of(level) == 0) "Lv $level" else "★${of(level)} · Lv ${shownLevel(level)}"

    /** A star box's chance of holding a relic instead of its Epic weapon. */
    const val STAR_RELIC_CHANCE = 0.25

    /** What a new star's reward box holds: supplies, plus an Epic weapon and Epic armor rolled at your level. */
    val BOX_ITEMS: Map<Item, Int> = mapOf(Item.PRISMATIC_CRYSTAL to 3, Item.RESTORE_POTION to 2, Item.PHOENIX_FEATHER to 2)

    /** A star's reward box, at the hero's real [level]. */
    data class Box(val items: Map<Item, Int>, val weapon: Gear, val armor: Armor)

    fun box(star: Int, level: Int, heroName: String): Box {
        val r = Random(heroName.hashCode().toLong() * 31 + star)
        return Box(
            BOX_ITEMS,
            // A star box sometimes holds a relic instead of an Epic weapon.
            Relic.roll(level, r, STAR_RELIC_CHANCE) ?: Armory.roll(level, r, luck = 20, minimum = GearRarity.EPIC),
            Armory.rollArmor(level, r, luck = 20, minimum = GearRarity.EPIC),
        )
    }
}
