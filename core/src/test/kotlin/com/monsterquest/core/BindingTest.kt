package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingTest {
    private val common = Bestiary.get("zapmouse")

    @Test
    fun glyphsFollowTypeAndGetHarderWithRarity() {
        val g = Binding.glyphFor(common)
        assertTrue(g.name in setOf("Bolt", "Zigzag", "Fork"), g.name)
        // The same species always gets the same pattern.
        assertEquals(g.points, Binding.glyphFor(common).points)
        assertTrue(g.points.all { it.x in 0f..1f && it.y in 0f..1f })
        // Rarer monsters: a more intricate pattern, with extra time for it.
        val epic = Bestiary.all.first { it.rarity == Rarity.EPIC && it.element == Element.ELECTRIC }
        assertEquals(2, Binding.complexity(epic, Item.BINDING_CRYSTAL))
        assertEquals(0, Binding.complexity(common, Item.BINDING_CRYSTAL))
        val hard = Binding.glyphFor(epic)
        val plain = Binding.glyphFor(epic, Item.PRISMATIC_CRYSTAL)
        // Better crystals make the pattern simpler: Resonant one step, Prismatic two.
        assertEquals(1, Binding.complexity(epic, Item.RESONANT_CRYSTAL))
        assertEquals(0, Binding.complexity(epic, Item.PRISMATIC_CRYSTAL))
        assertTrue(Binding.length(plain.points) < Binding.length(hard.points))
        assertEquals(hard.name, plain.name)
        // Better crystals give more time to trace.
        assertTrue(Binding.glyphFor(common, Item.PRISMATIC_CRYSTAL).timeLimitMs > g.timeLimitMs)
        assertFalse(Binding.needsTrace(Item.PERFECT_ORB))
        // Every type has its own shapes: no pattern name is shared between types.
        val byType = Bestiary.all.groupBy { it.element }.mapValues { (_, sps) -> sps.map { Binding.glyphFor(it).name }.toSet() }
        val names = byType.values.flatten()
        assertEquals(names.size, names.toSet().size)
        // There's more than one pattern per type across the bestiary.
        assertTrue(byType.values.count { it.size > 1 } >= 6, "$byType")
    }

    @Test
    fun speedAndAccuracyMakeTheGrade() {
        val g = Binding.glyphFor(common)
        // Traced exactly and quickly: Perfect.
        assertEquals(TraceGrade.PERFECT, Binding.grade(g, g.points, g.timeLimitMs / 3))
        // Exact but slow: lower.
        val slow = Binding.grade(g, g.points, g.timeLimitMs * 9 / 10)
        assertTrue(slow < TraceGrade.PERFECT && slow >= TraceGrade.GOOD)
        // A wobbly but close trace still does fine.
        val r = Random(3)
        val wobbly = g.points.map { GlyphPoint(it.x + (r.nextFloat() - 0.5f) * 0.06f, it.y + (r.nextFloat() - 0.5f) * 0.06f) }
        assertTrue(Binding.grade(g, wobbly, g.timeLimitMs / 2) >= TraceGrade.STRONG)
        // A mark elsewhere fizzles; half the glyph is weak.
        assertEquals(TraceGrade.FIZZLE, Binding.grade(g, listOf(GlyphPoint(0.9f, 0.9f), GlyphPoint(0.95f, 0.95f)), 500))
        assertTrue(Binding.grade(g, g.points.take(g.points.size / 2), g.timeLimitMs / 3) <= TraceGrade.FAINT)
    }

    @Test
    fun aBetterTraceRaisesTheOdds() {
        val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.copy(inventory = mapOf(Item.BINDING_CRYSTAL to 9))
        val b = GameEngine.newBattle(s, Monster.create(common, 6, Random(2)), EncounterType.WILD, rng = Random(3))
        b.enemy.hp = b.enemy.maxHp / 3
        val good = b.captureOdds(Item.BINDING_CRYSTAL)
        assertTrue(b.captureOdds(Item.BINDING_CRYSTAL, TraceGrade.PERFECT) > good)
        assertTrue(b.captureOdds(Item.BINDING_CRYSTAL, TraceGrade.FAINT) < good)
        assertEquals(1.0, b.captureOdds(Item.PERFECT_ORB, TraceGrade.FAINT))
        assertEquals("Binding Crystal", Item.BINDING_CRYSTAL.label)
        // Dens too.
        val m = Monster.create(common, 6, Random(4))
        assertTrue(Dens.throwOdds(m, grade = TraceGrade.PERFECT) > Dens.throwOdds(m))
    }

    @Test
    fun castsShakeBeforeTheySealOrBreak() {
        assertEquals(3, shakesFor(success = true, critical = false, roll = 0.1, chance = 0.3))
        assertEquals(1, shakesFor(success = true, critical = true, roll = 0.9, chance = 0.3))
        assertEquals(2, shakesFor(success = false, critical = false, roll = 0.35, chance = 0.3)) // a near miss
        assertEquals(1, shakesFor(success = false, critical = false, roll = 0.6, chance = 0.3))
        assertEquals(0, shakesFor(success = false, critical = false, roll = 0.95, chance = 0.3))
        // Battles and dens record each cast's roll for the animation.
        val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.copy(inventory = mapOf(Item.BINDING_CRYSTAL to 9))
        val b = GameEngine.newBattle(s, Monster.create(common, 6, Random(2)), EncounterType.WILD, rng = Random(3))
        b.enemy.hp = b.enemy.maxHp / 3
        var guard = 0
        while (b.castCount == 0 && b.outcome == BattleOutcome.ONGOING && guard++ < 20) {
            if (b.current?.let { !Combatant.isEnemy(it.key) } == true) b.step(BattleAction.Capture(Item.BINDING_CRYSTAL, TraceGrade.STRONG)) else break
        }
        assertEquals(1, b.castCount)
        val roll = b.lastCast!!
        assertEquals(roll.success, b.outcome == BattleOutcome.CAPTURED)
        if (roll.success) assertTrue(roll.shakes >= 1) else assertTrue(roll.shakes in 0..2)
    }
}
