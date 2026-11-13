package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.floor
import kotlin.random.Random

/** What a real place hosts on the map. */
@Serializable
enum class VenueRole {
    /** Coffee shops, doctor's offices and most restaurants: a dungeon. */
    DUNGEON,
    /** Grocery stores: a trader (beside the store's Supply Cache). */
    MARKET,
    /** Pharmacies: an infirmary, a free full heal once a day. */
    PHARMACY,
    /** Gyms: a training ground, a torch for party XP. */
    GYM,
    /** Hospitals: wakes every fainted monster, once a day. */
    HOSPITAL,
    /** Parks and gardens: dens here hold Earth, Flight and Gas monsters (the park keeps its shrine). */
    NATURE,
    /** Fountains and waterfronts: dens here hold Water monsters. */
    WATERSIDE,
    /** Bars, pubs and nightclubs: thugs gather here at night, and keep away by day. */
    NIGHTLIFE,
    /** Schools, colleges and universities: an academy where the hero studies magic once a day. */
    SCHOOL,
}

/** A real place (from OpenStreetMap) that hosts a dungeon or a trader. */
@Serializable
data class Venue(
    val id: String,
    val name: String?,
    /** "Coffee shop", "Doctor's office"... */
    val kind: String?,
    val lat: Double,
    val lon: Double,
    val role: VenueRole,
)

/**
 * Today's dungeons, traders and dens at real places ([Venues.plan]); the
 * spots lists include ones already cleared today, so nothing fills in there.
 */
data class VenuePlan(
    val dungeons: List<Dungeon>,
    val traders: List<Trader>,
    val dens: List<Den>,
    val dungeonSpots: List<Pair<Double, Double>> = emptyList(),
    val traderSpots: List<Pair<Double, Double>> = emptyList(),
    val denSpots: List<Pair<Double, Double>> = emptyList(),
) {
    companion object {
        val EMPTY = VenuePlan(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Puts the day's dungeons, traders, dens and thugs at real places: a dungeon
 * in every coffee shop, doctor's office and most restaurants, a trader at
 * every grocery store, and each day some of those spots are taken over by a
 * den (at that exact spot). Thugs and villains turn up along real streets.
 * Where there's no map data (offline, or very rural), the old scattered
 * placement fills in.
 */
object Venues {
    /** Share of venues a den takes over each day. */
    const val DEN_TAKEOVER = 0.15
    /** Chance each street point has a thug camp today, and the closest two camps may be. */
    const val STREET_CAMP_CHANCE = 0.03
    const val CAMP_SPACING_M = 250.0
    const val MAX_STREET_CAMPS = 8
    /** Chance a bar has a crew outside it at night, and how far from bars they keep by day. */
    const val NIGHT_CAMP_CHANCE = 0.5
    const val BAR_CLEAR_M = 120.0

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun rng(id: String, day: Long, salt: Int) = Random(id.hashCode().toLong() * 1_000_003 + day * 7_919 + salt)

    /** Places dens can take over, and how often: parks and waterside spots twice as often. */
    private fun takeover(role: VenueRole): Double = when (role) {
        VenueRole.DUNGEON, VenueRole.MARKET -> DEN_TAKEOVER
        VenueRole.NATURE, VenueRole.WATERSIDE -> DEN_TAKEOVER * 2
        else -> 0.0
    }

    /** The types a den at [v] favors: parks bring out Earth, Flight and Gas; waterside spots Water. */
    private fun favored(v: Venue): Set<Element> = when (v.role) {
        VenueRole.NATURE -> setOf(Element.EARTH, Element.FLIGHT, Element.GAS)
        VenueRole.WATERSIDE -> setOf(Element.WATER)
        else -> emptySet()
    }

    /** Night is from 8 PM to 4 AM: thugs gather at bars then. */
    fun isNight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(nowMs).atZone(zone).hour.let { it >= 20 || it < 4 }

    /** Pharmacies, gyms and hospitals near you (they're the same every day). */
    fun facilities(venues: List<Venue>, streets: List<Pair<Double, Double>> = emptyList()): List<Venue> =
        venues.filter { it.role in FACILITIES && it.role != VenueRole.SCHOOL } + academies(venues.filter { it.role == VenueRole.SCHOOL }, streets)

    /** Academy spots on a university campus, and on a college's. */
    const val UNIVERSITY_ACADEMIES = 3
    const val COLLEGE_ACADEMIES = 2
    /** How far from a campus's middle its extra academy spots stand. */
    const val CAMPUS_SPREAD_M = 110.0
    /** A school's academy stands on a street at least this far from the school (never on its grounds)... */
    const val OFF_CAMPUS_MIN_M = 150.0
    /** ...and no further than this; with no street in between, that school has none. */
    const val OFF_CAMPUS_MAX_M = 400.0

    private fun isHigherEd(v: Venue) = v.kind?.let { it.contains("University", true) || it.contains("College", true) } == true

    /**
     * Where the hero can study: a few spots across each university and
     * college campus, and for schools (primary and high schools), a spot on
     * a nearby street off the grounds, so nobody needs to go onto a school's
     * campus.
     */
    fun academies(schools: List<Venue>, streets: List<Pair<Double, Double>>): List<Venue> = schools.flatMap { v ->
        if (isHigherEd(v)) {
            val n = if (v.kind?.contains("University", true) == true) UNIVERSITY_ACADEMIES else COLLEGE_ACADEMIES
            val turn = Math.floorMod(v.id.hashCode(), 360).toDouble()
            (0 until n).map { k ->
                if (k == 0) {
                    v.copy(id = "${v.id}#0")
                } else {
                    val a = Math.toRadians(turn + 360.0 * (k - 1) / (n - 1))
                    v.copy(
                        id = "${v.id}#$k",
                        lat = v.lat + CAMPUS_SPREAD_M * kotlin.math.sin(a) / 111_320.0,
                        lon = v.lon + CAMPUS_SPREAD_M * cos(a) / (111_320.0 * cos(Math.toRadians(v.lat))),
                    )
                }
            }
        } else {
            val spot = streets
                .map { it to World.distanceMeters(v.lat, v.lon, it.first, it.second) }
                .filter { (_, d) -> d >= OFF_CAMPUS_MIN_M && d <= OFF_CAMPUS_MAX_M }
                .minByOrNull { it.second }?.first
            if (spot == null) emptyList()
            else listOf(Venue("${v.id}#st", v.name?.let { "Across from $it" } ?: "Near a school", "Study spot", spot.first, spot.second, VenueRole.SCHOOL))
        }
    }

    /** Share of restaurants that hold a dungeon. */
    const val RESTAURANT_DUNGEON_PCT = 70
    /** Two places of the same role closer than this are the same place. */
    const val SAME_PLACE_M = 30.0

    /**
     * What a place from the map's own vector tiles hosts, from its OpenStreetMap
     * value (the "subclass": cafe, doctors, supermarket...), or null for none.
     */
    fun roleForPoi(subclass: String?, cls: String?, id: String): VenueRole? {
        val v = subclass ?: cls ?: return null
        return when (v) {
            "pharmacy", "chemist" -> VenueRole.PHARMACY
            "hospital" -> VenueRole.HOSPITAL
            "fitness_centre", "gym" -> VenueRole.GYM
            "cafe", "coffee", "doctors", "doctor", "clinic", "dentist" -> VenueRole.DUNGEON
            "restaurant", "fast_food" -> if (Math.floorMod(id.hashCode(), 100) < RESTAURANT_DUNGEON_PCT) VenueRole.DUNGEON else null
            "supermarket", "convenience", "grocery", "greengrocer" -> VenueRole.MARKET
            "park", "garden" -> VenueRole.NATURE
            "fountain" -> VenueRole.WATERSIDE
            "bar", "pub", "nightclub", "biergarten" -> VenueRole.NIGHTLIFE
            "school", "college", "university" -> VenueRole.SCHOOL
            else -> null
        }
    }

    /**
     * Whether a landmark from the map's own tiles is a shrine, and which tier
     * and kind: libraries, churches, parks, museums, monuments, art... (the
     * same places the OpenStreetMap lookup turns into shrines). Null: not one.
     */
    fun shrineForPoi(subclass: String?, cls: String?): Pair<ShrineTier, World.PlaceCategory>? {
        val v = subclass ?: cls ?: return null
        val tier = when (v) {
            "theatre", "arts_centre", "museum", "theme_park", "zoo", "aquarium", "attraction",
            "castle", "monument", "fort", "ruins", "stadium", "water_park" -> ShrineTier.GOLD
            "cinema", "casino", "bowling_alley", "amusement_arcade", "sports_centre", "escape_game", "miniature_golf" -> ShrineTier.SILVER
            "library", "place_of_worship", "post_office", "townhall", "community_centre", "marketplace", "fountain",
            "park", "garden", "playground", "artwork", "viewpoint", "picnic_site", "ice_cream", "food_court",
            "books", "gift", "toys", "music", "art", "florist", "bakery" -> ShrineTier.ITEM
            else -> return null
        }
        val category = when (v) {
            "library", "museum", "books" -> World.PlaceCategory.KNOWLEDGE
            "park", "garden", "playground", "viewpoint", "picnic_site" -> World.PlaceCategory.NATURE
            "fountain" -> World.PlaceCategory.WATER
            "artwork", "theatre", "arts_centre", "art" -> World.PlaceCategory.ART
            "place_of_worship", "post_office", "townhall", "community_centre", "castle", "monument", "fort", "ruins" -> World.PlaceCategory.CIVIC
            "ice_cream", "food_court", "marketplace", "bakery" -> World.PlaceCategory.FOOD
            "gift", "toys", "music", "florist" -> World.PlaceCategory.SHOP
            else -> World.PlaceCategory.OTHER
        }
        return tier to category
    }

    /** A friendly label for a place's OpenStreetMap value ("Coffee shop", "Doctor's office"...). */
    fun kindLabel(raw: String?): String? {
        raw ?: return null
        val special = mapOf(
            "cafe" to "Coffee shop", "doctors" to "Doctor's office", "doctor" to "Doctor's office", "clinic" to "Clinic",
            "dentist" to "Dentist", "pharmacy" to "Pharmacy", "chemist" to "Pharmacy", "hospital" to "Hospital",
            "fitness_centre" to "Gym", "supermarket" to "Supermarket", "convenience" to "Convenience store",
            "grocery" to "Grocery store", "greengrocer" to "Greengrocer", "fast_food" to "Fast food", "pub" to "Pub",
            "nightclub" to "Nightclub", "biergarten" to "Beer garden",
            "school" to "School", "college" to "College", "university" to "University",
        )
        return special[raw] ?: raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * [primary] plus every place in [extra] that isn't already there (no place
     * of the same role within [SAME_PLACE_M]). Used to fill in what one map
     * source missed with the other.
     */
    fun merge(primary: List<Venue>, extra: List<Venue>): List<Venue> {
        if (extra.isEmpty()) return primary
        val out = primary.toMutableList()
        val ids = primary.mapTo(HashSet()) { it.id }
        for (v in extra) {
            if (v.id in ids) continue
            if (out.any { it.role == v.role && World.distanceMeters(it.lat, it.lon, v.lat, v.lon) < SAME_PLACE_M }) continue
            out += v
            ids += v.id
        }
        return out
    }
    val FACILITIES = setOf(VenueRole.PHARMACY, VenueRole.GYM, VenueRole.HOSPITAL, VenueRole.SCHOOL)

    /** A dungeon's look, from the kind of place: coffee roasters are forges, doctor's offices crypts, the rest random. */
    private fun themeFor(v: Venue, r: Random): DungeonTheme = when {
        v.kind?.contains("Coffee", ignoreCase = true) == true -> DungeonTheme.FORGE
        v.kind?.contains("Doctor", ignoreCase = true) == true || v.kind?.contains("Clinic", ignoreCase = true) == true ||
            v.kind?.contains("Dentist", ignoreCase = true) == true -> DungeonTheme.CRYPT
        else -> DungeonTheme.entries.random(r)
    }

    /** Each block of about this size (meters) holds at most one venue dungeon a day; the other spots there rest. */
    const val DUNGEON_CELL_M = 300.0
    /** And at most one trader per this size. */
    const val TRADER_CELL_M = 400.0
    /** Chance a cell's runner-up spot holds a den today instead of resting. */
    const val LEFTOVER_DEN_CHANCE = 0.35
    /** Scattered ones fill in wherever no real-place dungeon, den or trader is this close. */
    const val FLOOR_DUNGEON_M = 450.0
    const val FLOOR_DEN_M = 300.0
    const val FLOOR_TRADER_M = 500.0
    /**
     * Every 3x3 block of dungeon cells (about 900 m across) shares a deck of
     * difficulties, so each neighbourhood has easier and harder ones. The deck
     * grows tougher as you level: new heroes see mostly 1 to 3 stars, veterans
     * mostly 4 and 5 (with an easier one or two still around).
     */
    fun starDeck(heroLevel: Int): List<Int> = when {
        heroLevel < 15 -> listOf(1, 1, 1, 2, 2, 2, 3, 3, 4)
        heroLevel < 30 -> listOf(1, 1, 2, 2, 3, 3, 4, 4, 5)
        heroLevel < 45 -> listOf(1, 2, 2, 3, 3, 4, 4, 5, 5)
        else -> listOf(2, 3, 3, 4, 4, 4, 5, 5, 5)
    }

    /** The cell of about [sizeM] meters a spot falls in. */
    fun cellOf(lat: Double, lon: Double, sizeM: Double): Pair<Long, Long> {
        val dLat = sizeM / 111_320.0
        val row = floor(lat / dLat).toLong()
        val rowLat = (row + 0.5) * dLat
        val dLon = sizeM / (111_320.0 * cos(Math.toRadians(rowLat)))
        return row to floor(lon / dLon).toLong()
    }

    /** A venue dungeon's difficulty: its cell's card from its block's deck (for a hero of [heroLevel]). */
    fun starsFor(cell: Pair<Long, Long>, day: Long, heroLevel: Int): Int {
        val br = Math.floorDiv(cell.first, 3L)
        val bc = Math.floorDiv(cell.second, 3L)
        val deck = starDeck(heroLevel).shuffled(rng("stars:$br:$bc", day, 5))
        return deck[(Math.floorMod(cell.first, 3L) * 3 + Math.floorMod(cell.second, 3L)).toInt()]
    }

    /**
     * Today's dungeons, traders and dens at real places, balanced so a busy
     * downtown isn't wall-to-wall dungeons:
     * - one dungeon per ~300 m block, at a spot picked fresh each day (the
     *   other cafés and restaurants there rest until another day);
     * - the block's runner-up spot sometimes holds a den instead;
     * - one trader per ~400 m block of grocery stores;
     * - parks and fountains sometimes hold a den;
     * - nothing stands within [World.MIN_SPACING_M] of anything else;
     * - difficulty comes from a shared deck, so every neighbourhood has easy ones.
     * Some spots are taken over by a den for the day ([DEN_TAKEOVER]).
     */
    fun plan(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): VenuePlan {
        val day = epochDay(nowMs, zone)
        val kept = ArrayList<Pair<Double, Double>>()
        fun clear(lat: Double, lon: Double) = kept.none { World.distanceMeters(it.first, it.second, lat, lon) < World.MIN_SPACING_M }
        val dungeonAt = ArrayList<Pair<Venue, Int>>()
        val denAt = ArrayList<Venue>()
        val traderAt = ArrayList<Venue>()
        fun denSpot(v: Venue): Pair<Double, Double> =
            // On a park or fountain, a step off its shrine so both can be tapped.
            if (v.role == VenueRole.NATURE || v.role == VenueRole.WATERSIDE) v.lat - 0.00018 to v.lon + 0.00018 else v.lat to v.lon
        // A step off the store's Supply Cache, so both can be tapped.
        fun traderSpot(v: Venue): Pair<Double, Double> = v.lat + 0.00018 to v.lon + 0.00018
        fun cells(role: Set<VenueRole>, sizeM: Double) = venues.filter { it.role in role }
            .groupBy { cellOf(it.lat, it.lon, sizeM) }
            .toSortedMap(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
            .map { (k, list) -> k to list.sortedBy { it.id }.shuffled(rng("cell:${k.first}:${k.second}:${role.first()}", day, 6)) }

        for ((cell, cands) in cells(setOf(VenueRole.DUNGEON), DUNGEON_CELL_M)) {
            val open = cands.filter { clear(it.lat, it.lon) }.toMutableList()
            val first = open.removeFirstOrNull() ?: continue
            if (rng(first.id, day, 1).nextDouble() < DEN_TAKEOVER) denAt += first else dungeonAt += first to starsFor(cell, day, state.hero.level)
            kept += first.lat to first.lon
            val second = open.firstOrNull { clear(it.lat, it.lon) } ?: continue
            if (rng(second.id, day, 7).nextDouble() < LEFTOVER_DEN_CHANCE) {
                denAt += second
                kept += second.lat to second.lon
            }
        }
        for ((_, cands) in cells(setOf(VenueRole.MARKET), TRADER_CELL_M)) {
            val first = cands.firstOrNull { v -> clear(v.lat, v.lon) && traderSpot(v).let { clear(it.first, it.second) } } ?: continue
            if (rng(first.id, day, 1).nextDouble() < DEN_TAKEOVER) {
                denAt += first
                kept += first.lat to first.lon
            } else {
                traderAt += first
                kept += traderSpot(first)
            }
        }
        for ((_, cands) in cells(setOf(VenueRole.NATURE, VenueRole.WATERSIDE), DUNGEON_CELL_M)) {
            val first = cands.firstOrNull { v -> denSpot(v).let { clear(it.first, it.second) } } ?: continue
            if (rng(first.id, day, 1).nextDouble() >= takeover(first.role)) continue
            denAt += first
            kept += denSpot(first)
        }

        val dungeons = dungeonAt.mapNotNull { (v, stars) ->
            val id = "dgv:${v.id}:$day"
            if (id in state.dungeonsCleared) return@mapNotNull null
            val r = rng(v.id, day, 2)
            val theme = themeFor(v, r)
            val level = (state.hero.level + (stars - 2) * 3 + r.nextInt(-1, 2)).coerceAtLeast(2)
            Dungeon(id, theme.names.random(r), theme, stars, level, v.lat, v.lon, r.nextLong(), place = v.name ?: v.kind)
        }
        val traders = traderAt.map { v ->
            val (lat, lon) = traderSpot(v)
            Wanderers.makeTrader(state, "tv:${v.id}:$day", lat, lon, rng(v.id, day, 3))
        }
        val dens = denAt.mapNotNull { v ->
            val id = "denv:${v.id}:$day"
            val (lat, lon) = denSpot(v)
            if (id in state.densCleared) null else Dens.make(state, id, lat, lon, rng(v.id, day, 4), favored(v))
        }
        return VenuePlan(
            dungeons, traders, dens,
            dungeonSpots = dungeonAt.map { it.first.lat to it.first.lon },
            traderSpots = traderAt.map { traderSpot(it) },
            denSpots = denAt.map { denSpot(it) },
        )
    }

    /**
     * Adds the scattered dungeons, dens and traders wherever real places left
     * a gap (quiet streets, the countryside, or places that didn't load), kept
     * clear of everything else and of [avoid] (shrines).
     */
    fun topUp(
        plan: VenuePlan,
        dungeons: List<Dungeon>,
        dens: List<Den>,
        traders: List<Trader>,
        avoid: List<Pair<Double, Double>> = emptyList(),
    ): VenuePlan {
        val kept = ArrayList<Pair<Double, Double>>().apply { addAll(plan.dungeonSpots); addAll(plan.denSpots); addAll(plan.traderSpots); addAll(avoid) }
        fun fits(lat: Double, lon: Double, same: List<Pair<Double, Double>>, floorM: Double) =
            same.none { World.distanceMeters(it.first, it.second, lat, lon) < floorM } &&
                kept.none { World.distanceMeters(it.first, it.second, lat, lon) < World.MIN_SPACING_M }
        val dg = dungeons.filter { d -> fits(d.lat, d.lon, plan.dungeonSpots, FLOOR_DUNGEON_M).also { if (it) kept += d.lat to d.lon } }
        val dn = dens.filter { d -> fits(d.lat, d.lon, plan.denSpots, FLOOR_DEN_M).also { if (it) kept += d.lat to d.lon } }
        val tr = traders.filter { t -> fits(t.lat, t.lon, plan.traderSpots, FLOOR_TRADER_M).also { if (it) kept += t.lat to t.lon } }
        return plan.copy(dungeons = plan.dungeons + dg, dens = plan.dens + dn, traders = plan.traders + tr)
    }

    fun dungeons(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Dungeon> = plan(state, venues, nowMs, zone).dungeons
    fun traders(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Trader> = plan(state, venues, nowMs, zone).traders
    fun dens(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Den> = plan(state, venues, nowMs, zone).dens

    /**
     * Thug crews and villains standing on real streets today, spread apart. At
     * night they also gather outside bars ([nightlife]); by day they keep away
     * from them.
     */
    fun streetCamps(
        state: GameState,
        streets: List<Pair<Double, Double>>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nightlife: List<Venue> = emptyList(),
    ): List<VillainCamp> {
        val day = epochDay(nowMs, zone)
        val night = isNight(nowMs, zone)
        val out = mutableListOf<VillainCamp>()
        if (night) {
            for (v in nightlife) {
                val r = rng(v.id, day, 6)
                if (r.nextDouble() >= NIGHT_CAMP_CHANCE) continue
                val id = "vn:${v.id}:$day"
                if (id in state.villainsDefeated) continue
                out += Villains.makeCamp(state, id, v.lat + 0.00022, v.lon - 0.00012, r)
            }
        }
        for ((lat, lon) in streets) {
            if (!night && nightlife.any { World.distanceMeters(it.lat, it.lon, lat, lon) < BAR_CLEAR_M }) continue
            if (out.size >= MAX_STREET_CAMPS) break
            val key = "%.5f,%.5f".format(java.util.Locale.US, lat, lon)
            val r = rng(key, day, 5)
            if (r.nextDouble() >= STREET_CAMP_CHANCE) continue
            if (out.any { World.distanceMeters(it.lat, it.lon, lat, lon) < CAMP_SPACING_M }) continue
            val id = "vs:$key:$day"
            if (id in state.villainsDefeated) continue
            out += Villains.makeCamp(state, id, lat, lon, r)
        }
        return out
    }
}
