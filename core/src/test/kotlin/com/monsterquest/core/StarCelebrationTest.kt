package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarCelebrationTest {
    @Test fun theFirstStarIsCelebratedOnce() {
        assertFalse(Stars.due(GameState(hero = Hero(level = 50))))
        val s = GameState(hero = Hero(level = 51))
        assertTrue(Stars.due(s))
        val seen = Stars.celebrate(s)
        assertFalse(Stars.due(seen))
        assertEquals(1, seen.starsCelebrated)
        // The next star brings it back.
        assertTrue(Stars.due(seen.copy(hero = seen.hero.copy(level = 101))))
        // And it's kept in the save.
        assertEquals(1, GameState.fromJson(seen.toJson()).starsCelebrated)
    }

    @Test fun perksDescribeTheStar() {
        val p = Stars.perks(1, 51)
        assertTrue(p.first().contains("★1 · Lv 1"))
        assertTrue(p.any { "20" in it && "champions" in it.lowercase() })
        assertTrue(p.any { "Every 50 levels" in it })

    }
}
