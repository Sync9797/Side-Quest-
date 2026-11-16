package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppearanceTest {
    @Test fun newAndOldSavesAskForALook() {
        assertTrue(Looks.due(GameState()))
        // A save from before customization has no appearance field at all.
        val old = GameState().toJson().replace(Regex(",?\"appearance\":null"), "")
        assertNull(GameState.fromJson(old).appearance)
        assertTrue(Looks.due(GameState.fromJson(old)))
    }

    @Test fun savingALookStoresItAndTheName() {
        val look = Appearance(body = BodyType.FEMALE, eyes = EyeStyle.BRIGHT, hair = HairStyle.BRAID, hoodAlwaysUp = true)
        val s = Looks.save(GameState(), look, "  Wren of the Long Road  ")
        assertFalse(Looks.due(s))
        assertEquals(look, s.appearance)
        assertEquals("Wren of the Long", s.hero.name)
        // It survives a save and load.
        assertEquals(look, GameState.fromJson(s.toJson()).appearance)
    }

    @Test fun aBlankNameKeepsTheOldOne() {
        val s = Looks.save(GameState(hero = Hero(name = "Aren")), Appearance(), "   ")
        assertEquals("Aren", s.hero.name)
        assertNotNull(s.appearance)
    }

    @Test fun randomLooksAndNamesAreValid() {
        val rng = Random(5)
        repeat(50) {
            val a = Appearance.random(rng)
            assertFalse(a.hoodAlwaysUp)
            val n = Appearance.randomName(a.body, rng)
            assertTrue(n.isNotBlank() && n.length <= Appearance.MAX_NAME)
        }
    }
}
