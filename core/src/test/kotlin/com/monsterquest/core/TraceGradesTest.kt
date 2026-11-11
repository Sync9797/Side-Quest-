package com.monsterquest.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Perfect takes a steady, quick hand; fair attempts still tame. */
class TraceGradesTest {
    companion object {
        /** The glyph drawn by a hand that wanders up to [wobble] off the line (smooth drift plus a little jitter). */
        fun humanTrace(g: Glyph, wobble: Float, rng: Random): List<GlyphPoint> {
            val pts = mutableListOf<GlyphPoint>()
            for ((a, b) in g.points.zipWithNext()) {
                val n = (hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) / 0.01).toInt().coerceAtLeast(1)
                for (i in 0 until n) pts += GlyphPoint(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n)
            }
            pts += g.points.last()
            val p1 = rng.nextDouble() * 6.28; val p2 = rng.nextDouble() * 6.28; val p3 = rng.nextDouble() * 6.28
            val f1 = 2 + rng.nextDouble() * 3; val f2 = 3 + rng.nextDouble() * 4
            return pts.mapIndexed { i, p ->
                val t = i.toDouble() / pts.size
                val dx = wobble * (0.7 * sin(f1 * 6.28 * t + p1) + 0.3 * sin(f2 * 6.28 * t + p3)) + wobble * 0.15 * (rng.nextDouble() - 0.5)
                val dy = wobble * (0.7 * cos(f1 * 6.28 * t + p2) + 0.3 * sin(f2 * 6.28 * t + p1)) + wobble * 0.15 * (rng.nextDouble() - 0.5)
                GlyphPoint((p.x + dx).toFloat(), (p.y + dy).toFloat())
            }
        }

        /** Share of traces (all species, a few hands each) at each grade, for [wobble] and [timeFraction] of the limit. */
        fun shares(wobble: Float, timeFraction: Double): Map<TraceGrade, Double> {
            val counts = IntArray(TraceGrade.entries.size)
            for (sp in Bestiary.all) for (k in 0 until 4) {
                val g = Binding.glyphFor(sp)
                val tr = humanTrace(g, wobble, Random(sp.id.hashCode() * 7 + k))
                counts[Binding.grade(g, tr, (g.timeLimitMs * timeFraction).toLong()).ordinal]++
            }
            return TraceGrade.entries.associateWith { counts[it.ordinal] / counts.sum().toDouble() }
        }
    }

    private fun Map<TraceGrade, Double>.atLeast(g: TraceGrade) = filterKeys { it >= g }.values.sum()

    @Test
    fun perfectIsHardButDoable() {
        // A steady, quick hand: Perfect most of the time.
        assertTrue(shares(0.01f, 0.35).getValue(TraceGrade.PERFECT) > 0.8)
        // A small wobble is Strong, not Perfect.
        assertTrue(shares(0.03f, 0.6).getValue(TraceGrade.PERFECT) < 0.05)
        assertTrue(shares(0.03f, 0.6).atLeast(TraceGrade.STRONG) > 0.8)
        // Sloppy (5% of the glyph off the line) never Perfect; slow drawing costs too.
        assertTrue(shares(0.05f, 0.35).getValue(TraceGrade.PERFECT) < 0.01)
        assertEquals(0.0, shares(0.0f, 0.85).getValue(TraceGrade.PERFECT))
        // Shaky still tames sometimes: mostly Faint or Good, rarely a fizzle.
        val shaky = shares(0.1f, 0.6)
        assertTrue(shaky.atLeast(TraceGrade.STRONG) < 0.01)
        assertTrue(shaky.getValue(TraceGrade.FIZZLE) < 0.2)
    }
}
