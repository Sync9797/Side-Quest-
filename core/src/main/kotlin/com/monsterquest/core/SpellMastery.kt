package com.monsterquest.core

import kotlin.math.roundToInt

/** One of a spell's two finishing paths (chosen at rank 5). */
data class SpellPath(
    /** 1 or 2. */
    val id: Int,
    val name: String,
    /** What it does at [mastery] (0 = just chosen). */
    val text: (mastery: Int) -> String,
)

/**
 * The long road for a hero's magic.
 *
 * Ranks need levels: rank 2 at level 10, 3 at 20, 4 at 30 and 5 at 40, so no
 * one maxes their spells in a few weeks. At rank 5 a spell can follow one of
 * two paths (a choice of what it becomes, not just a bigger number), and
 * each path has five mastery ranks of its own, opening from level 45 to 65
 * (into the first star). Switching paths later costs gold and keeps mastery.
 */
object SpellMastery {
    const val MAX = 5
    const val SWITCH_COST = 1_000

    /** The hero level a spell rank needs (rank 1 has none). */
    fun levelForRank(rank: Int): Int = 10 * (rank - 1).coerceAtLeast(0)

    /** The hero level mastery rank [m] (1..5) needs. */
    fun levelForMastery(m: Int): Int = 40 + 5 * m

    /** Pages and gold for mastery rank [m]: they carry on from the spell's own rank costs. */
    fun pagesFor(m: Int): Int = Spells.pagesToUpgrade(Spells.MAX_RANK + m - 1)
    fun goldFor(m: Int): Int = Spells.goldToUpgrade(Spells.MAX_RANK + m - 1)

    // ---- what the paths do ----------------------------------------------------

    /** Storm (bolts): splits to hit every foe at this share of its power. */
    fun stormShare(m: Int) = 0.5 + 0.06 * m
    /** Lance (bolts): power multiplier, and the chance of its type's effect. */
    fun lanceBoost(m: Int) = 1 + 0.1 * m
    fun lanceChance(m: Int) = 0.10 + 0.06 * m
    /** Mudslide: Mud Sling hits every foe at this share. */
    fun mudslideShare(m: Int) = 0.7 + 0.06 * m
    /** Quagmire: chance Mud Sling also lowers the foe's Speed. */
    fun quagmireChance(m: Int) = 0.4 + 0.12 * m
    /** Purifying paths: extra healing. */
    fun purifyBoost(m: Int) = 1 + 0.05 * m
    /** Overflow (Mend): share of healing past full HP that becomes a shield. */
    fun overflowShare(m: Int) = 0.5 + 0.1 * m
    /** Bulwark (Aegis): extra share of max HP. */
    fun bulwarkExtraPct(m: Int) = 5 * m
    /** Shared Aegis: each ally's shield as a share of a single Aegis. */
    fun sharedShare(m: Int) = 0.5 + 0.06 * m
    /** Rally (buffs): stages each ally gets. Overcharge: extra stages on one. */
    fun rallyStages(power: Int, m: Int) = (power - 1 + (if (m >= 3) 1 else 0)).coerceAtLeast(1)
    fun overchargeExtra(m: Int) = if (m >= 5) 2 else 1
    /** Sanctuary (Cleanse): healing share when it cleanses everyone. */
    fun sanctuaryShare(m: Int) = 0.5 + 0.08 * m
    /** Power multipliers for the "bigger" paths. */
    fun restorationBoost(m: Int) = 1 + 0.2 * m
    fun radiantBoost(m: Int) = 1 + 0.15 * m

    /** What a Lance bolt of [element] does on a hit. */
    enum class LanceEffect(val label: String) { BURN("burn"), SHOCK("shock"), POISON("poison"), SLEEP("put to sleep"), SLOW("lower Speed"), CRACK("lower Defense"), SAP("lower Attack") }

    fun lanceEffect(element: Element): LanceEffect = when (element) {
        Element.FIRE -> LanceEffect.BURN
        Element.ELECTRIC -> LanceEffect.SHOCK
        Element.GAS -> LanceEffect.POISON
        Element.CORRUPT -> LanceEffect.SLEEP
        Element.WATER -> LanceEffect.SLOW
        Element.EARTH -> LanceEffect.CRACK
        else -> LanceEffect.SAP
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    private val stormNames = mapOf(
        Element.FIRE to "Firestorm", Element.ELECTRIC to "Chain Lightning", Element.WATER to "Downpour",
        Element.FLIGHT to "Gale Sweep", Element.GAS to "Toxic Cloud", Element.CORRUPT to "Shadow Rain", Element.EARTH to "Rockfall",
    )
    private val lanceNames = mapOf(
        Element.FIRE to "Searing Lance", Element.ELECTRIC to "Stunning Bolt", Element.WATER to "Crushing Torrent",
        Element.FLIGHT to "Piercing Gust", Element.GAS to "Venom Lance", Element.CORRUPT to "Dread Lance", Element.EARTH to "Shatter Lance",
    )

    /** The two paths for [spell]. */
    fun paths(spell: Skill): List<SpellPath> = when {
        spell.id == Spells.MUD_SLING -> listOf(
            SpellPath(1, "Mudslide") { m -> "Hits every foe at ${pct(mudslideShare(m))} power, muddying each" },
            SpellPath(2, "Quagmire") { m -> "${pct(quagmireChance(m).coerceAtMost(1.0))} chance to also lower the foe's Speed" },
        )
        spell.kind == SkillKind.MAGICAL -> listOf(
            SpellPath(1, stormNames[spell.element] ?: "Storm") { m -> "Splits to hit every foe at ${pct(stormShare(m))} power" },
            SpellPath(2, lanceNames[spell.element] ?: "Lance") { m ->
                "${pct(lanceBoost(m))} power, and a ${pct(lanceChance(m) * (if (spell.element == Element.CORRUPT) 0.5 else 1.0))} chance to ${lanceEffect(spell.element).label}"
            },
        )
        spell.kind == SkillKind.HEAL -> listOf(
            SpellPath(1, "Purifying Mend") { m -> "Also cures the target's status; ${pct(purifyBoost(m))} healing" },
            SpellPath(2, "Overflow") { m -> "Healing past full HP becomes a shield (${pct(overflowShare(m))} of it)" },
        )
        spell.kind == SkillKind.AEGIS -> listOf(
            SpellPath(1, "Bulwark") { m -> "+${bulwarkExtraPct(m)}% of your max HP on top of the shield" },
            SpellPath(2, "Shared Aegis") { m -> "Shields every ally, each at ${pct(sharedShare(m))} of a single Aegis" },
        )
        spell.kind in Spells.STAGE_SPELLS -> listOf(
            SpellPath(1, "Rally") { m -> "Every ally gets the boost (one stage less until mastery 3)" },
            SpellPath(2, "Overcharge") { m -> "+${overchargeExtra(m)} extra stage${if (overchargeExtra(m) > 1) "s" else ""} on the target" },
        )
        spell.kind == SkillKind.CLEANSE -> listOf(
            SpellPath(1, "Sanctuary") { m -> "Cleanses every ally, healing each at ${pct(sanctuaryShare(m))}" },
            SpellPath(2, "Restoration") { m -> "${pct(restorationBoost(m))} healing" },
        )
        spell.kind == SkillKind.MEND_ALL -> listOf(
            SpellPath(1, "Purifying Circle") { m -> "Also cures every ally's status; ${pct(purifyBoost(m))} healing" },
            SpellPath(2, "Radiant Circle") { m -> "${pct(radiantBoost(m))} healing" },
        )
        else -> emptyList()
    }

    fun path(spellId: String, pathId: Int): SpellPath? = Spells.byId(spellId)?.let { s -> paths(s).find { it.id == pathId } }

    // ---- choosing and mastering ---------------------------------------------

    /** Chooses (or switches, for [SWITCH_COST] gold) a rank-5 spell's path. Mastery carries over. */
    fun choose(state: GameState, spellId: String, pathId: Int): Update {
        val spell = Spells.byId(spellId) ?: return Update(state, "Unknown spell.")
        val h = state.hero
        if (h.spellRank(spellId) < Spells.MAX_RANK) return Update(state, "Rank ${spell.name} up to ${Spells.MAX_RANK} to choose its path.")
        val p = paths(spell).find { it.id == pathId } ?: return Update(state, "No such path.")
        val current = h.spellPaths[spellId]
        if (current == pathId) return Update(state, "${spell.name} already follows ${p.name}.")
        val cost = if (current == null) 0 else SWITCH_COST
        if (state.gold < cost) return Update(state, "Switching paths costs $cost gold.")
        return Update(
            state.copy(gold = state.gold - cost, hero = h.copy(spellPaths = h.spellPaths + (spellId to pathId))),
            "${spell.name} follows the path of ${p.name}!" + if (cost > 0) " (−$cost gold)" else "",
        )
    }

    /** Raises a path's mastery by one, with pages and gold, once the hero's level allows. */
    fun master(state: GameState, spellId: String): Update {
        val spell = Spells.byId(spellId) ?: return Update(state, "Unknown spell.")
        val h = state.hero
        val pathId = h.spellPaths[spellId] ?: return Update(state, "Choose a path for ${spell.name} first.")
        val m = h.spellMastery[spellId] ?: 0
        if (m >= MAX) return Update(state, "${spell.name} is fully mastered.")
        val next = m + 1
        if (h.level < levelForMastery(next)) return Update(state, "Mastery $next opens at ${Stars.levelText(levelForMastery(next))}.")
        val pages = state.spellPages[spellId] ?: 0
        val needPages = pagesFor(next)
        val needGold = goldFor(next)
        if (pages < needPages) return Update(state, "Need $needPages ${spell.name} pages (you have $pages).")
        if (state.gold < needGold) return Update(state, "Need $needGold gold.")
        val left = pages - needPages
        val s = state.copy(
            gold = state.gold - needGold,
            spellPages = if (left > 0) state.spellPages + (spellId to left) else state.spellPages - spellId,
            hero = h.copy(spellMastery = h.spellMastery + (spellId to next)),
        )
        return Update(s, "${path(spellId, pathId)?.name ?: spell.name} reaches mastery $next!")
    }
}
