package com.monsterquest.core

import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenuesTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private val day = 1_800_000_000_000L / 86_400_000L
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    private val cafes = (0 until 40).map { Venue("node$it", "Cafe $it", "Coffee shop", 40.0 + it * 0.001, -74.0, VenueRole.DUNGEON) }
    private val groceries = (0 until 40).map { Venue("way$it", "Market $it", "Supermarket", 40.0 + it * 0.001, -74.01, VenueRole.MARKET) }

    @Test
    fun venuesHostDungeonsTradersAndDens() {
        val s = start()
        val all = cafes + groceries
        val plan = Venues.plan(s, all, now, utc)
        assertTrue(plan.dungeons.isNotEmpty() && plan.traders.isNotEmpty())
        // Nothing doubles up: each venue is at most one thing today.
        val used = plan.dungeons.map { it.lat to it.lon } + plan.dens.map { it.lat to it.lon }
        assertEquals(used.size, used.toSet().size)
        // Dungeons sit in the coffee shops (forges), at the very spot.
        val d = plan.dungeons.first()
        val cafe = cafes.first { "dgv:${it.id}:" in d.id }
        assertEquals(cafe.lat to cafe.lon, d.lat to d.lon)
        assertEquals(DungeonTheme.FORGE, d.theme)
        assertEquals(cafe.name, d.place)
        // Tomorrow a different set of cafés is open.
        val tomorrow = Venues.plan(s, all, now + 86_400_000L, utc)
        assertTrue(tomorrow.dungeons.map { it.lat to it.lon }.toSet() != plan.dungeons.map { it.lat to it.lon }.toSet())
        // A cleared dungeon stays cleared for the day, and its spot isn't refilled.
        val cleared = s.copy(dungeonsCleared = mapOf(d.id to day))
        val after = Venues.plan(cleared, all, now, utc)
        assertTrue(after.dungeons.none { it.id == d.id })
        assertTrue((d.lat to d.lon) in after.dungeonSpots)
    }

    /** A dense downtown and a quiet suburb end up with a similar number of dungeons within a quarter mile. */
    @Test
    fun citiesAreBalancedAgainstSuburbs() {
        val s = start()
        val center = 40.75 to -73.99
        val rnd = Random(9)
        fun around(n: Int, radiusM: Double, role: VenueRole, tag: String) = (0 until n).map {
            val a = rnd.nextDouble() * 2 * Math.PI
            val r = radiusM * kotlin.math.sqrt(rnd.nextDouble())
            Venue("$tag$it", "$tag $it", if (role == VenueRole.DUNGEON) "Coffee shop" else "Supermarket",
                center.first + r * kotlin.math.sin(a) / 111_320.0,
                center.second + r * kotlin.math.cos(a) / (111_320.0 * kotlin.math.cos(Math.toRadians(center.first))), role)
        }
        val quarterMile = 402.0
        fun within(lat: Double, lon: Double) = World.distanceMeters(center.first, center.second, lat, lon) <= quarterMile
        val scattered = Dungeons.near(s, center.first, center.second, now, utc)
        val scatteredDens = Dens.near(s, center.first, center.second, now, utc)
        val scatteredTraders = Wanderers.tradersNear(s, center.first, center.second, now, utc)
        val days = 0 until 20
        fun avg(venues: List<Venue>): Triple<Double, Double, Double> {
            var dg = 0; var dn = 0; var tr = 0
            for (k in days) {
                val t = now + k * 86_400_000L
                val p = Venues.topUp(
                    Venues.plan(s, venues, t, utc),
                    Dungeons.near(s, center.first, center.second, t, utc),
                    Dens.near(s, center.first, center.second, t, utc),
                    Wanderers.tradersNear(s, center.first, center.second, t, utc),
                )
                dg += p.dungeons.count { within(it.lat, it.lon) }
                dn += p.dens.count { within(it.lat, it.lon) }
                tr += p.traders.count { within(it.lat, it.lon) }
                // Nothing stands on top of anything else.
                val spots = p.dungeons.map { it.lat to it.lon } + p.traders.map { it.lat to it.lon } + p.dens.map { it.lat to it.lon }
                for (i in spots.indices) for (j in i + 1 until spots.size) {
                    assertTrue(World.distanceMeters(spots[i].first, spots[i].second, spots[j].first, spots[j].second) >= World.MIN_SPACING_M - 1)
                }
            }
            return Triple(dg / days.count().toDouble(), dn / days.count().toDouble(), tr / days.count().toDouble())
        }
        val downtown = avg(around(200, 900.0, VenueRole.DUNGEON, "cafe") + around(40, 900.0, VenueRole.MARKET, "mart"))
        val suburb = avg(around(10, 900.0, VenueRole.DUNGEON, "cafe") + around(2, 900.0, VenueRole.MARKET, "mart"))
        val none = avg(emptyList())
        println("within a quarter mile (dungeons, dens, traders): downtown $downtown, suburb $suburb, no places $none")
        // Downtown: a handful of dungeons, not dozens (the old rule put ~50 here).
        assertTrue(downtown.first in 3.0..9.0, "downtown dungeons ${downtown.first}")
        assertTrue(downtown.third <= 4.0, "downtown traders ${downtown.third}")
        // The suburb and the countryside still get some.
        assertTrue(suburb.first >= 1.0, "suburb dungeons ${suburb.first}")
        assertTrue(none.first + none.second >= 0.5, "countryside ${none}")
        assertTrue(downtown.first <= suburb.first * 6, "downtown ${downtown.first} vs suburb ${suburb.first}")
        assertTrue(scattered.isNotEmpty() || scatteredDens.isNotEmpty() || scatteredTraders.isNotEmpty())
    }

    @Test
    fun everyNeighbourhoodHasEasyDungeonsAndFewHardOnes() {
        // A full 3x3 block deals each difficulty from the deck for your level.
        for (level in listOf(5, 20, 35, 60)) for (d in 0L until 10L) for (br in -2L..2L) for (bc in -2L..2L) {
            val stars = (0 until 3).flatMap { r -> (0 until 3).map { c -> Venues.starsFor(br * 3 + r to bc * 3 + c, d, level) } }
            assertEquals(Venues.starDeck(level).sorted(), stars.sorted())
        }
    }

    @Test
    fun thugsStandOnStreetsSpreadApart() {
        val street = (0 until 2000).map { 40.0 + it * 0.0005 to -74.0 }
        val camps = Venues.streetCamps(start(), street, now, utc)
        assertTrue(camps.isNotEmpty() && camps.size <= Venues.MAX_STREET_CAMPS)
        assertTrue(camps.all { c -> street.any { it.first == c.lat && it.second == c.lon } })
        for (a in camps) for (b in camps) if (a !== b) assertTrue(World.distanceMeters(a.lat, a.lon, b.lat, b.lon) >= Venues.CAMP_SPACING_M)
    }

    @Test
    fun mapTilePlacesGetRoles() {
        assertEquals(VenueRole.DUNGEON, Venues.roleForPoi("cafe", "cafe", "a"))
        assertEquals(VenueRole.DUNGEON, Venues.roleForPoi("doctors", "hospital", "a"))
        assertEquals(VenueRole.MARKET, Venues.roleForPoi("supermarket", "grocery", "a"))
        assertEquals(VenueRole.PHARMACY, Venues.roleForPoi("pharmacy", "pharmacy", "a"))
        assertEquals(VenueRole.HOSPITAL, Venues.roleForPoi("hospital", "hospital", "a"))
        assertEquals(VenueRole.GYM, Venues.roleForPoi("fitness_centre", "fitness", "a"))
        assertEquals(VenueRole.NIGHTLIFE, Venues.roleForPoi("bar", "bar", "a"))
        assertEquals(VenueRole.NATURE, Venues.roleForPoi(null, "park", "a"))
        assertEquals(null, Venues.roleForPoi("bank", "bank", "a"))
        // Most restaurants hold a dungeon, and the same one always does.
        val picks = (0 until 1000).map { Venues.roleForPoi("restaurant", "restaurant", "r$it") }
        val share = picks.count { it == VenueRole.DUNGEON }
        assertTrue(share in 600..800, "$share")
        assertEquals(picks[5], Venues.roleForPoi("restaurant", "restaurant", "r5"))
        assertEquals("Doctor's office", Venues.kindLabel("doctors"))
    }

    @Test
    fun mergeFillsInWithoutDuplicates() {
        val a = Venue("n1", "Cafe", "Coffee shop", 40.0, -74.0, VenueRole.DUNGEON)
        val sameSpot = Venue("m1", "Cafe", "Coffee shop", 40.0001, -74.0, VenueRole.DUNGEON)
        val other = Venue("m2", "Market", "Supermarket", 40.0001, -74.0, VenueRole.MARKET)
        val far = Venue("m3", "Cafe 2", "Coffee shop", 40.01, -74.0, VenueRole.DUNGEON)
        val merged = Venues.merge(listOf(a), listOf(sameSpot, other, far, far))
        assertEquals(listOf("n1", "m2", "m3"), merged.map { it.id })
    }

    @Test
    fun mapLandmarksBecomeShrines() {
        assertEquals(ShrineTier.ITEM to World.PlaceCategory.KNOWLEDGE, Venues.shrineForPoi("library", "library"))
        assertEquals(ShrineTier.GOLD, Venues.shrineForPoi("museum", "museum")?.first)
        assertEquals(ShrineTier.SILVER, Venues.shrineForPoi("cinema", "cinema")?.first)
        assertEquals(World.PlaceCategory.NATURE, Venues.shrineForPoi(null, "park")?.second)
        // Cafés host dungeons, not shrines; ordinary shops and banks are neither.
        assertEquals(null, Venues.shrineForPoi("cafe", "cafe"))
        assertEquals(null, Venues.shrineForPoi("bank", "bank"))
    }
}
