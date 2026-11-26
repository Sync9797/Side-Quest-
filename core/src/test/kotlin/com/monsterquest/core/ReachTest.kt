package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A place opens once its ring touches yours, not only once its centre is inside your ring. */
class ReachTest {
    @Test fun ringsTouching() {
        val r = World.SHRINE_RANGE_M
        assertTrue(World.inReach(r - 1, r))
        assertTrue(World.inReach(r + World.PLACE_RING_M, r), "rings just touching")
        assertFalse(World.inReach(r + World.PLACE_RING_M + 1, r))
        assertFalse(World.inReach(null, r))
    }
}
