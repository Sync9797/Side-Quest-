package com.monsterquest.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ShrineTier(val label: String) {
    /** Loot: weapons, spell pages, orbs, potions. Always open. */
    ITEM("Item shrine"),
    /** Tough non-legendary guardians; open Tue/Thu when the quest goal is met. */
    SILVER("Silver shrine"),
    /** Legendary-class guardians; open Sat/Sun when the weekly quest goal is met. */
    GOLD("Gold shrine"),
}

/** One map tile as kept on the phone: when it was looked up, its shrines, and points along its water. */
@kotlinx.serialization.Serializable
data class ShrineTile(
    val fetchedAt: Long,
    val shrines: List<Shrine>,
    val water: List<List<Double>> = emptyList(),
    /** Places that host dungeons and traders (see [Venues]). */
    val venues: List<Venue> = emptyList(),
    /** Points along the tile's streets, for thugs and villains. */
    val streets: List<List<Double>> = emptyList(),
    /** What the tile's lookup included: older tiles are looked up again ([TILE_VERSION]). */
    val v: Int = 0,
)

/** Bump to make every cached tile look itself up again (2: venues and streets). */
const val TILE_VERSION = 3

@kotlinx.serialization.Serializable
data class Shrine(
    val id: String,
    /** The in-game name, e.g. "Supply Cache". */
    val name: String,
    val lat: Double,
    val lon: Double,
    val tier: ShrineTier,
    val bossSpeciesId: String? = null,
    /** The real place it stands at, e.g. "Barnes & Noble" (null for generated waystones). */
    val place: String? = null,
    /** What kind of place that is, e.g. "Bookstore". */
    val kind: String? = null,
    /** The street it's on, when the map data knows it. */
    val street: String? = null,
) {
    val isBoss: Boolean get() = tier != ShrineTier.ITEM

    /** The second line under the name: "Barnes & Noble on Wards Road", "Bookstore", ... */
    val subtitle: String?
        get() {
            val where = place ?: kind ?: return street?.let { "on $it" }
            return if (street != null) "$where on $street" else where
        }
}

/** Times of day change which monster types are out, like a day/night cycle. */
enum class TimeOfDay(val label: String, val hours: IntRange, val favored: Set<Element>) {
    DAWN("Dawn", 5..9, setOf(Element.FLIGHT, Element.EARTH)),
    DAY("Day", 10..16, setOf(Element.FIRE, Element.ELECTRIC)),
    DUSK("Dusk", 17..20, setOf(Element.GAS, Element.WATER)),
    NIGHT("Night", 21..28, setOf(Element.CORRUPT, Element.GAS));

    companion object {
        fun of(hour: Int): TimeOfDay = entries.firstOrNull { hour in it.hours || hour + 24 in it.hours } ?: NIGHT
    }
}

/**
 * Alphas: big, tough first forms waiting in dungeons' Alpha dens (1.5x
 * stats in battle, a stronger extra move, a little harder to tame) with
 * [PERFECT_STATS] perfect stats, so the small forms stay worth hunting.
 */
object Alphas {
    const val STAT_BOOST = 1.5
    const val CATCH_MULTIPLIER = 0.8
    const val PERFECT_STATS = 3
}

/** Location-based content: shrines and places on the map, built from real places. */
object World {
    /** Water types by a lake, river or coast: this much more common within [NEAR_WATER_M]. */
    const val NEAR_WATER_FACTOR = 2.5
    /** ...and this much rarer with no water within [FAR_FROM_WATER_M]. */
    const val FAR_FROM_WATER_FACTOR = 0.4
    const val NEAR_WATER_M = 250.0
    const val FAR_FROM_WATER_M = 1_000.0

    /** How water nearby changes Water-type spawns; null when unknown or in between. */
    fun waterFactor(distanceM: Double?): Double? = when {
        distanceM == null -> null
        distanceM <= NEAR_WATER_M -> NEAR_WATER_FACTOR
        distanceM >= FAR_FROM_WATER_M -> FAR_FROM_WATER_FACTOR
        else -> null
    }

    // ---- fixed map tiles (shrines are looked up once per tile and kept) ----

    /** Tile size in degrees: about 1.1 km north-south. */
    const val TILE_DEG = 0.01

    /** A tile's key, e.g. "4075:-7399". */
    fun tileKey(lat: Double, lon: Double): String = "${floor(lat / TILE_DEG).toLong()}:${floor(lon / TILE_DEG).toLong()}"

    /** South, west, north, east edges of a tile. */
    fun tileBounds(key: String): DoubleArray {
        val (y, x) = key.split(":").map { it.toLong() }
        return doubleArrayOf(y * TILE_DEG, x * TILE_DEG, (y + 1) * TILE_DEG, (x + 1) * TILE_DEG)
    }

    /** Tiles covering [radiusM] around a point. */
    fun tilesAround(lat: Double, lon: Double, radiusM: Double): List<String> {
        val dy = kotlin.math.ceil(radiusM / 111_320.0 / TILE_DEG).toLong()
        val dx = kotlin.math.ceil(radiusM / (111_320.0 * kotlin.math.cos(Math.toRadians(lat))) / TILE_DEG).toLong()
        val cy = floor(lat / TILE_DEG).toLong()
        val cx = floor(lon / TILE_DEG).toLong()
        return (-dy..dy).flatMap { y -> (-dx..dx).map { x -> "${cy + y}:${cx + x}" } }
    }

    /** Most shrines of each tier kept per tile. */
    val TILE_CAPS = mapOf(ShrineTier.ITEM to 24, ShrineTier.SILVER to 5, ShrineTier.GOLD to 4)

    /** Nothing on the map stands closer than this to a shrine, so more of them never feels crowded. */
    const val MIN_SPACING_M = 90.0

    /**
     * Which of a tile's real places become shrines: the same ones every time
     * (picked by a stable hash of the place, not by where you are), spaced at
     * least [MIN_SPACING_M] apart, up to [TILE_CAPS] of each tier.
     */
    fun selectForTile(candidates: List<Shrine>): List<Shrine> {
        val kept = mutableListOf<Shrine>()
        for (sh in candidates.sortedBy { stableHash(it.id) * 31L + it.id.length }) {
            if (kept.count { it.tier == sh.tier } >= (TILE_CAPS[sh.tier] ?: 0)) continue
            if (kept.any { distanceMeters(it.lat, it.lon, sh.lat, sh.lon) < MIN_SPACING_M }) continue
            kept += sh
        }
        return kept
    }
    /** How close (meters) you must be to use a shrine. */
    const val SHRINE_RANGE_M = 60.0

    /** About how far a place's own ring reaches out around it on the map. */
    const val PLACE_RING_M = 15.0

    /**
     * In reach: the place's ring touches your [rangeM] ring (its centre is
     * within [rangeM] plus [PLACE_RING_M]). Null distance (no location) is never in reach.
     */
    fun inReach(distanceM: Double?, rangeM: Double): Boolean = distanceM != null && distanceM <= rangeM + PLACE_RING_M
    const val BOSS_COOLDOWN_MS = 4 * 60 * 60_000L
    /** Each silver shrine can be fought once per open day. */
    const val SILVER_COOLDOWN_MS = 20 * 60 * 60_000L

    private const val SHRINE_CELL_DEG = 0.0028 // ~310 m
    private const val SHRINE_CELL_RADIUS = 3

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    private fun seed(vararg parts: Long): Long {
        var h = 1125899906842597L
        for (p in parts) {
            h = 31 * h + p
            h = h xor (h ushr 29)
            h *= -0x40a7b892e31b1a47L
        }
        return h
    }

    private fun stableHash(s: String): Int = abs(s.hashCode() % 1_000_000)

    /** Gold guardians before the Earth legendary arrived; their shrines keep the same guardian. */
    private const val ORIGINAL_GOLD_GUARDIANS = 12

    internal fun guardianFor(id: String, tier: ShrineTier): String? {
        val h = stableHash(id)
        return when (tier) {
            ShrineTier.ITEM -> null
            ShrineTier.SILVER -> Bestiary.silverGuardians.let { it[h % it.size].id }
            ShrineTier.GOLD -> {
                // Newer guardians take over a fair share of shrines; every other shrine
                // keeps the guardian it always had (so past boss wins still count).
                val pool = Bestiary.bosses
                val slot = h % pool.size
                val original = pool.filter { it.id != "worldroot_colossus" }
                if (slot >= ORIGINAL_GOLD_GUARDIANS) pool.first { it.id == "worldroot_colossus" }.id
                else original[h % ORIGINAL_GOLD_GUARDIANS].id
            }
        }
    }

    /**
     * Turn a real-world place (from OpenStreetMap) into a shrine. The app
     * picks the tier from the kind of place: everyday spots are item shrines,
     * entertainment venues silver, and major destinations gold.
     */
    fun shrineFromPlace(
        placeId: String,
        place: String?,
        lat: Double,
        lon: Double,
        tier: ShrineTier,
        kind: String? = null,
        street: String? = null,
        category: PlaceCategory = PlaceCategory.OTHER,
    ): Shrine {
        val id = "osm:$placeId"
        return Shrine(id, shrineTitle(tier, category), lat, lon, tier, guardianFor(id, tier), place = place, kind = kind, street = street)
    }

    /** What kind of real place a shrine stands at; picks its in-game name. */
    enum class PlaceCategory { FOOD, SHOP, KNOWLEDGE, NATURE, ART, WATER, CIVIC, OTHER }

    /** In-game shrine names, by tier and the kind of place. */
    fun shrineTitle(tier: ShrineTier, category: PlaceCategory): String = when (tier) {
        ShrineTier.GOLD -> "Ancient Altar"
        ShrineTier.SILVER -> "Silver Obelisk"
        ShrineTier.ITEM -> when (category) {
            PlaceCategory.FOOD -> "Provision Shrine"
            PlaceCategory.SHOP -> "Supply Cache"
            PlaceCategory.KNOWLEDGE -> "Lore Shrine"
            PlaceCategory.NATURE -> "Grove Shrine"
            PlaceCategory.ART -> "Muse Stone"
            PlaceCategory.WATER -> "Spring Shrine"
            PlaceCategory.CIVIC -> "Sanctum"
            PlaceCategory.OTHER -> "Wayshrine"
        }
    }

    /** Fallback shrines for areas with no mapped places (e.g. rural areas or offline). */
    fun proceduralShrines(lat: Double, lon: Double): List<Shrine> {
        val cx = floor(lat / SHRINE_CELL_DEG).toLong()
        val cy = floor(lon / SHRINE_CELL_DEG).toLong()
        val out = mutableListOf<Shrine>()
        for (dx in -SHRINE_CELL_RADIUS..SHRINE_CELL_RADIUS) for (dy in -SHRINE_CELL_RADIUS..SHRINE_CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, 7))
            if (r.nextDouble() > 0.6) continue
            val id = "gen:$x:$y"
            val roll = r.nextDouble()
            val tier = when {
                roll < 0.1 -> ShrineTier.GOLD
                roll < 0.25 -> ShrineTier.SILVER
                else -> ShrineTier.ITEM
            }
            val sLat = (x + r.nextDouble()) * SHRINE_CELL_DEG
            val sLon = (y + r.nextDouble()) * SHRINE_CELL_DEG
            val name = when (tier) {
                ShrineTier.GOLD -> "Ancient Altar"
                ShrineTier.SILVER -> "Silver Obelisk"
                ShrineTier.ITEM -> listOf("Wayshrine", "Supply Cache", "Stone Circle", "Spirit Well").random(r)
            }
            out += Shrine(id, name, sLat, sLon, tier, guardianFor(id, tier))
        }
        return out
    }

    /** Compass bearing in degrees (0 = north, 90 = east) from the first point to the second. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    /** "N", "NE", "E", ... for a bearing. */
    fun compass(bearing: Double): String =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((bearing + 22.5) % 360) / 45).toInt()]

    /**
     * Extra item stops ("waystones") filling the gaps between real places, so
     * there's always something to collect within a short walk.
     */
    fun waystones(lat: Double, lon: Double, existing: List<Shrine>): List<Shrine> {
        val cell = 0.0025 // ~280 m
        val cx = floor(lat / cell).toLong()
        val cy = floor(lon / cell).toLong()
        val out = mutableListOf<Shrine>()
        for (dx in -5..5) for (dy in -5..5) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, 23))
            if (r.nextDouble() > 0.55) continue
            val sLat = (x + r.nextDouble()) * cell
            val sLon = (y + r.nextDouble()) * cell
            if ((existing + out).any { distanceMeters(it.lat, it.lon, sLat, sLon) < 120 }) continue
            val name = listOf("Waystone", "Supply Cache", "Stone Circle", "Spirit Well", "Old Signpost", "Traveler's Rest").random(r)
            out += Shrine("way:$x:$y", name, sLat, sLon, ShrineTier.ITEM)
        }
        return out
    }

}
