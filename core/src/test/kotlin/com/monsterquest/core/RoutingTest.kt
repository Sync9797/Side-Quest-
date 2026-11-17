package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingTest {
    // A small grid of streets about 110 m apart: two east-west, two north-south.
    private val d = 0.001
    private val lines = listOf(
        listOf(0.0 to 0.0, 0.0 to d, 0.0 to 2 * d),
        listOf(d to 0.0, d to d, d to 2 * d),
        listOf(0.0 to 0.0, d to 0.0),
        listOf(0.0 to 2 * d, d to 2 * d),
    )

    @Test fun followsTheStreetsAroundTheBlock() {
        val g = Routing.build(lines)
        val from = 0.0 to 0.0
        val to = d to 2 * d
        val path = assertNotNull(Routing.route(g, from, to))
        assertEquals(from, path.first())
        assertEquals(to, path.last())
        // Along the streets it's an L, longer than the straight line.
        val walk = Routing.length(path)
        val straight = Routing.length(Routing.straight(from, to))
        assertTrue(walk > straight * 1.2, "$walk vs $straight")
        assertTrue(walk < straight * 1.6, "$walk vs $straight")
    }

    @Test fun joinsLinesThatShareAPoint() {
        // Two separate lines meeting at the middle corner are one network.
        val g = Routing.build(listOf(listOf(0.0 to 0.0, 0.0 to d), listOf(0.0 to d, d to d)))
        assertNotNull(Routing.route(g, 0.0 to 0.0, d to d))
    }

    @Test fun noRouteFarFromAnyStreetOrAcrossAGap() {
        val g = Routing.build(lines)
        assertNull(Routing.route(g, 0.0 to 0.0, 0.05 to 0.05))
        val split = Routing.build(listOf(listOf(0.0 to 0.0, 0.0 to d), listOf(0.01 to 0.0, 0.01 to d)))
        assertNull(Routing.route(split, 0.0 to 0.0, 0.01 to d))
        assertEquals(1, Routing.minutes(10.0))
        assertEquals(5, Routing.minutes(400.0))
    }
}
