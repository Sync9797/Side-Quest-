package com.monsterquest.core

import kotlin.math.abs
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

    /** On one of the grid's streets (to a few centimeters). */
    private fun onStreet(p: Pair<Double, Double>): Boolean {
        val e = 1e-7
        val (lat, lon) = p
        val alongEastWest = (abs(lat) < e || abs(lat - d) < e) && lon in -e..(2 * d + e)
        val alongNorthSouth = (abs(lon) < e || abs(lon - 2 * d) < e) && lat in -e..(d + e)
        return alongEastWest || alongNorthSouth
    }

    @Test fun followsTheStreetsAroundTheBlock() {
        val g = Routing.build(lines)
        val path = assertNotNull(Routing.route(g, 0.0 to 0.0, d to 2 * d))
        assertTrue(path.all(::onStreet), "$path")
        // Along the streets it's an L, longer than the straight line.
        val walk = Routing.length(path)
        val straight = World.distanceMeters(0.0, 0.0, d, 2 * d)
        assertTrue(walk > straight * 1.2, "$walk vs $straight")
        assertTrue(walk < straight * 1.6, "$walk vs $straight")
    }

    @Test fun startAndEndOffTheStreetSnapOntoIt() {
        val g = Routing.build(lines)
        // Standing in the middle of the block, heading for a spot set back from the far street.
        val from = 0.0003 to 0.0009
        val to = 0.0011 to 0.0015
        val path = assertNotNull(Routing.route(g, from, to))
        assertTrue(path.all(::onStreet), "$path")
        // It starts on the street nearest you (the south one, about 33 m away), not at a corner.
        assertEquals(0.0, path.first().first, 1e-9)
        assertEquals(0.0009, path.first().second, 1e-9)
        assertEquals(d, path.last().first, 1e-9)
        assertEquals(0.0015, path.last().second, 1e-9)
    }

    @Test fun samePieceOfStreetIsAStraightWalkAlongIt() {
        val g = Routing.build(lines)
        val path = assertNotNull(Routing.route(g, 0.0001 to 0.0002, 0.0001 to 0.0008))
        assertEquals(listOf(0.0 to 0.0002, 0.0 to 0.0008), path)
    }

    @Test fun takesTheNearTurnWhereStreetsCrossWithoutASharedPoint() {
        // A long north-south street, and an east-west street drawn straight across it with no point at the crossing,
        // plus a far loop that does connect. The route must take the near crossing, not the loop.
        val main = listOf(0.0 to 0.0, 0.004 to 0.0)
        val cross = listOf(0.001 to -0.001, 0.001 to 0.002)
        val east = listOf(0.004 to 0.002, 0.0 to 0.002)
        val top = listOf(0.004 to 0.0, 0.004 to 0.002)
        val g = Routing.build(listOf(main, cross, east, top))
        val from = 0.0 to 0.0
        val to = 0.0 to 0.002
        val path = assertNotNull(Routing.route(g, from, to))
        // Up 110 m, right 220 m, down 110 m: about 440 m, not the 1,100 m loop by the top.
        assertTrue(Routing.length(path) < 500, "${Routing.length(path)}")
    }

    @Test fun aSideStreetThatStopsJustShortStillJoins() {
        // The side street ends 2 m before the main street.
        val main = listOf(0.0 to 0.0, 0.002 to 0.0)
        val side = listOf(0.001 to 0.002, 0.001 to 0.00002)
        val g = Routing.build(listOf(main, side))
        assertNotNull(Routing.route(g, 0.0 to 0.0, 0.001 to 0.002))
    }

    @Test fun aBridgeDoesNotJoinTheStreetBelow() {
        val below = Routing.Way(listOf(0.001 to -0.001, 0.001 to 0.001))
        val bridge = Routing.Way(listOf(0.0 to 0.0, 0.002 to 0.0), level = 1)
        val g = Routing.buildWays(listOf(below, bridge))
        assertNull(Routing.route(g, 0.0 to 0.0, 0.001 to 0.001))
    }

    @Test fun joinsLinesThatShareAPoint() {
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
