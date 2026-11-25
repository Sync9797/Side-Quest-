package com.monsterquest.app.ui

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monsterquest.core.Venue
import com.monsterquest.core.VenueRole
import com.monsterquest.core.Venues
import com.monsterquest.core.World
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.VectorSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The world map, drawn by MapLibre from OpenFreeMap's vector tiles. It
 * stays locked on the hero: pinch or double-tap to zoom, drag
 * round the hero to turn it, and the camera tilts toward the horizon as you
 * zoom in. Everything on it (shrines, dens, dungeons...) is drawn by Compose
 * on top, placed through the map's projection.
 *
 * Robolectric (screenshot tests) can't run MapLibre's native code, so there
 * the map is a plain backdrop with a flat projection.
 */
class GameMap internal constructor(val fake: Boolean) {
    var map: MapLibreMap? by mutableStateOf(null)
        internal set
    var view: MapView? = null
        internal set
    /** Bumps whenever the camera moves, so markers re-place themselves. */
    var tick by mutableIntStateOf(0)
        internal set
    /** The map's style has loaded (its tiles can be read). */
    var styleReady by mutableStateOf(false)
        internal set

    // The flat stand-in's camera (Robolectric).
    private var fakeCenter = LatLng(0.0, 0.0)
    private var fakeZoom = START_ZOOM
    internal var widthPx = 0
    internal var heightPx = 0
    internal var density = 1f

    /** Where a spot is on screen, in pixels from the map's top-left, or null before the map is ready. */
    fun toScreen(lat: Double, lon: Double): Offset? {
        val m = map
        if (m != null) {
            val p = m.projection.toScreenLocation(LatLng(lat, lon))
            return Offset(p.x, p.y)
        }
        if (!fake || widthPx == 0) return null
        val world = 512.0 * 2.0.pow(fakeZoom) * density
        val (cx, cy) = mercator(fakeCenter.latitude, fakeCenter.longitude, world)
        val (x, y) = mercator(lat, lon, world)
        return Offset((x - cx + widthPx / 2.0).toFloat(), (y - cy + heightPx / 2.0).toFloat())
    }

    val zoom: Double get() = map?.cameraPosition?.zoom ?: fakeZoom

    /** Which way the map is turned, in degrees clockwise from north (0 = north up). */
    val bearing: Double get() = map?.cameraPosition?.bearing ?: 0.0

    /** Turns the map to face [deg] (clockwise from north) at once: following the phone's heading. */
    fun setBearing(deg: Double) {
        val m = map ?: return
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(deg).build()))
    }

    /** Turns the map back to north up. */
    fun resetNorth() {
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).build()), 400)
    }

    /** Zooms (and tilts to match) around the hero. */
    fun zoomTo(z: Double) {
        val m = map
        if (m == null) {
            fakeZoom = z.coerceIn(MIN_ZOOM, MAX_ZOOM); tick++; return
        }
        val zz = z.coerceIn(m.minZoomLevel, m.maxZoomLevel)
        m.moveCamera(CameraUpdateFactory.newCameraPosition(framed(CameraPosition.Builder(m.cameraPosition), zz).build()))
    }

    fun zoomBy(levels: Double) = zoomTo(zoom + levels)

    /**
     * Sets [zoom], the tilt to go with it, and how far down the screen the hero
     * stands: lower as you zoom in, so you see more of what's ahead, like
     * looking down the road (padding at the top moves the camera's focus down).
     */
    private fun framed(b: CameraPosition.Builder, zoom: Double): CameraPosition.Builder {
        val h = (view?.height ?: heightPx).toDouble()
        return b.zoom(zoom).tilt(tiltFor(zoom)).padding(0.0, h * lookAhead(zoom), 0.0, 0.0)
    }

    /** One level in, smoothly (a double-tap). */
    fun zoomIn() {
        val m = map ?: return zoomBy(1.0)
        val z = (m.cameraPosition.zoom + 1).coerceAtMost(m.maxZoomLevel)
        m.animateCamera(CameraUpdateFactory.newCameraPosition(framed(CameraPosition.Builder(m.cameraPosition), z).build()), 250)
    }

    /** Turns the map [deg] degrees clockwise. */
    fun rotateBy(deg: Float) {
        val m = map ?: return
        if (deg == 0f) return
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(m.cameraPosition.bearing - deg).build()))
    }

    /** Puts a spot in the middle at once (following the hero). */
    fun centerOn(lat: Double, lon: Double) {
        val m = map
        if (m == null) {
            fakeCenter = LatLng(lat, lon); tick++; return
        }
        if (!placed) {
            // First fix: jump straight to the hero, zoomed in and tilted.
            placed = true
            m.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    framed(CameraPosition.Builder().target(LatLng(lat, lon)).bearing(0.0), START_ZOOM).build(),
                ),
            )
            return
        }
        val t = m.cameraPosition.target
        if (t != null && World.distanceMeters(t.latitude, t.longitude, lat, lon) < 0.5) return
        m.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
    }

    /** Glides to a spot. */
    fun animateTo(lat: Double, lon: Double) {
        val m = map
        if (m == null) {
            centerOn(lat, lon); return
        }
        m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)), 600)
    }

    private var placed = false
    private var boundsAt: Pair<Double, Double>? = null
    private var floorFor = 0

    /**
     * Keeps the map within about a mile of the hero, and no further out than
     * the closest screen edge being [ZOOM_REACH_M] away.
     */
    fun limitAround(lat: Double, lon: Double) {
        val m = map ?: return
        val last = boundsAt
        if (last == null || World.distanceMeters(last.first, last.second, lat, lon) > 25) {
            boundsAt = lat to lon
            val dLat = MAP_REACH_M / 111_320.0
            val dLon = MAP_REACH_M / (111_320.0 * cos(Math.toRadians(lat)))
            m.setLatLngBoundsForCameraTarget(LatLngBounds.from(lat + dLat, lon + dLon, lat - dLat, lon - dLon))
        }
        val v = view ?: return
        val halfDp = minOf(v.width, v.height) / 2.0 / density
        if (halfDp <= 0) return
        val key = (halfDp * 1000).toInt() + (lat * 10).toInt()
        if (key == floorFor) return
        floorFor = key
        // MapLibre's tiles are 512 points wide: meters per point at zoom z is 78,271.5 x cos(latitude) / 2^z.
        val z = ln(78_271.517 * cos(Math.toRadians(lat)) * halfDp / ZOOM_REACH_M) / ln(2.0)
        m.setMinZoomPreference(z.coerceIn(12.0, MAX_ZOOM - 1))
    }

    /**
     * Reads the places (cafés, doctors, groceries, pharmacies, gyms, parks,
     * bars...) and streets out of the map tiles that are loaded. [tiles] are
     * the game's place tiles those map tiles fully cover.
     */
    fun readPlaces(): MapPlaces? {
        val m = map ?: return null
        val style = m.style ?: return null
        if (!style.isFullyLoaded) return null
        val src = style.getSourceAs<VectorSource>(SOURCE_ID) ?: return null
        val region = m.projection.visibleRegion.latLngBounds
        // Vector tiles stop at zoom 14 and are overzoomed past it; the z14 tiles under the screen are loaded whole.
        val n = 1 shl SOURCE_ZOOM
        val x0 = tileX(region.getLonWest(), n); val x1 = tileX(region.getLonEast(), n)
        val y0 = tileY(region.getLatNorth(), n); val y1 = tileY(region.getLatSouth(), n)
        val north = tileLat(y0, n); val south = tileLat(y1 + 1, n)
        val west = x0.toDouble() / n * 360 - 180; val east = (x1 + 1).toDouble() / n * 360 - 180
        val tiles = HashSet<String>()
        var y = floor(south / World.TILE_DEG).toLong()
        while ((y + 1) * World.TILE_DEG <= north) {
            if (y * World.TILE_DEG >= south) {
                var x = floor(west / World.TILE_DEG).toLong()
                while ((x + 1) * World.TILE_DEG <= east) {
                    if (x * World.TILE_DEG >= west) tiles += "$y:$x"
                    x++
                }
            }
            y++
        }
        val venues = ArrayList<Venue>()
        val shrines = ArrayList<com.monsterquest.core.Shrine>()
        val seen = HashSet<String>()
        for (f in runCatching { src.querySourceFeatures(arrayOf("poi"), null) }.getOrDefault(emptyList())) {
            val pt = f.geometry() as? org.maplibre.geojson.Point ?: continue
            val sub = f.prop("subclass")
            val cls = f.prop("class")
            val id = "mt" + (f.id() ?: "${sub ?: cls}@${"%.5f".format(java.util.Locale.US, pt.latitude())},${"%.5f".format(java.util.Locale.US, pt.longitude())}")
            if (!seen.add(id)) continue
            val role = Venues.roleForPoi(sub, cls, id)
            if (role != null) venues += Venue(id, f.prop("name"), Venues.kindLabel(sub ?: cls), pt.latitude(), pt.longitude(), role)
            // Landmarks are shrines too (dungeons and services take their place over, as with the place lookup).
            if (role == VenueRole.DUNGEON || role in Venues.FACILITIES) continue
            Venues.shrineForPoi(sub, cls)?.let { (tier, category) ->
                shrines += com.monsterquest.core.World.shrineFromPlace(
                    id, f.prop("name"), pt.latitude(), pt.longitude(), tier, kind = Venues.kindLabel(sub ?: cls), category = category,
                )
            }
        }
        val streets = ArrayList<Pair<Double, Double>>()
        val streetKeys = HashSet<Long>()
        fun addLine(pts: List<org.maplibre.geojson.Point>) {
            var carry = 0.0
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]
                val len = World.distanceMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude())
                var d = STREET_STEP_M - carry
                while (d <= len) {
                    val t = d / len
                    val la = a.latitude() + (b.latitude() - a.latitude()) * t
                    val lo = a.longitude() + (b.longitude() - a.longitude()) * t
                    // Lines are cut at tile edges and repeated; one point per ~30 m cell.
                    if (streetKeys.add((floor(la / 0.0003).toLong() shl 32) xor floor(lo / 0.0003).toLong())) streets += la to lo
                    d += STREET_STEP_M
                }
                carry = (carry + len) % STREET_STEP_M
            }
        }
        for (f in runCatching { src.querySourceFeatures(arrayOf("transportation"), null) }.getOrDefault(emptyList())) {
            if (f.prop("class") !in STREET_CLASSES) continue
            when (val g = f.geometry()) {
                is org.maplibre.geojson.LineString -> addLine(g.coordinates())
                is org.maplibre.geojson.MultiLineString -> g.coordinates().forEach { addLine(it) }
                else -> {}
            }
        }
        return MapPlaces(venues, streets, tiles, shrines)
    }

    /** The streets you can walk along in the loaded map tiles, each marked as a bridge, a tunnel or on the ground. */
    fun readWalkways(): List<com.monsterquest.core.Routing.Way> {
        val m = map ?: return emptyList()
        val style = m.style ?: return emptyList()
        if (!style.isFullyLoaded) return emptyList()
        val src = style.getSourceAs<VectorSource>(SOURCE_ID) ?: return emptyList()
        val lines = ArrayList<com.monsterquest.core.Routing.Way>()
        for (f in runCatching { src.querySourceFeatures(arrayOf("transportation"), null) }.getOrDefault(emptyList())) {
            if (f.prop("class") !in WALK_CLASSES) continue
            val level = when (f.prop("brunnel")) { "bridge" -> 1; "tunnel" -> -1; else -> 0 }
            when (val g = f.geometry()) {
                is org.maplibre.geojson.LineString -> lines += com.monsterquest.core.Routing.Way(g.coordinates().map { it.latitude() to it.longitude() }, level)
                is org.maplibre.geojson.MultiLineString -> g.coordinates().forEach { l -> lines += com.monsterquest.core.Routing.Way(l.map { it.latitude() to it.longitude() }, level) }
                else -> {}
            }
        }
        return lines
    }

    companion object {
        const val START_ZOOM = 16.0
        const val MIN_ZOOM = 14.0
        // Closest zoom: just past where the tilt tops out (17.5). Any closer and the near ground fills the
        // screen, so the view reads as flat again; this keeps the 3D look at every zoom.
        const val MAX_ZOOM = 18.0
        /** How far (meters) the map can move away from you: about a mile. */
        const val MAP_REACH_M = 1_609.0
        /** How far out you can zoom: the nearest screen edge stays within about a quarter mile of you. */
        const val ZOOM_REACH_M = 402.0
        const val SOURCE_ID = "openmaptiles"
        private const val SOURCE_ZOOM = 14
        private const val STREET_STEP_M = 70.0
        private val STREET_CLASSES = setOf("primary", "secondary", "tertiary", "minor")
        /** Streets a walking route may use: real streets only (no motorways, driveways, parking aisles, tracks or footpaths). */
        private val WALK_CLASSES = setOf("primary", "secondary", "tertiary", "minor")

        /**
         * Share of the screen's height kept clear above the hero's focus: 0 when
         * zoomed out (hero in the middle), up to 0.4 zoomed in (hero about 70% of
         * the way down, with the road ahead filling the screen).
         */
        fun lookAhead(zoom: Double): Double = when {
            zoom <= 15.5 -> 0.0
            zoom >= 17.5 -> 0.4
            else -> (zoom - 15.5) / 2.0 * 0.4
        }

        /** Looking straight down when zoomed out, tipping toward the horizon as you zoom in. */
        fun tiltFor(zoom: Double): Double = when {
            zoom <= 15.0 -> 20.0
            zoom >= 17.5 -> 60.0
            else -> 20.0 + (zoom - 15.0) / 2.5 * 40.0
        }

        private fun mercator(lat: Double, lon: Double, world: Double): Pair<Double, Double> {
            val r = Math.toRadians(lat)
            return (lon + 180) / 360 * world to (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * world
        }
        private fun tileX(lon: Double, n: Int) = floor((lon + 180) / 360 * n).toInt()
        private fun tileY(lat: Double, n: Int): Int {
            val r = Math.toRadians(lat)
            return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt()
        }
        private fun tileLat(y: Int, n: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / n))))

        private fun org.maplibre.geojson.Feature.prop(key: String): String? =
            if (hasProperty(key)) runCatching { getStringProperty(key) }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }
}

/** Places and streets read from the map's own tiles. */
class MapPlaces(val venues: List<Venue>, val streets: List<Pair<Double, Double>>, val tiles: Set<String>, val shrines: List<com.monsterquest.core.Shrine> = emptyList())

/** OpenFreeMap's styles (free, no key): a bright one, and a dark one for night. */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private val isRobolectric = Build.FINGERPRINT == "robolectric"

/** The map, kept for as long as the screen is (it moves between layouts when the phone folds). */
@Composable
fun rememberGameMap(dark: Boolean): GameMap {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val gm = remember { GameMap(fake = isRobolectric).also { it.density = density } }
    if (gm.fake) return gm
    val mapView = remember { createMapView(context, gm) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            gm.map = null
            gm.view = null
        }
    }
    // Light or dark: the dark style falls back to the light one if it can't load.
    DisposableEffect(dark, gm.map) {
        val m = gm.map
        if (m != null) {
            gm.styleReady = false
            m.setStyle(if (dark) STYLE_DARK else STYLE_LIGHT) { gm.styleReady = true; gm.tick++ }
        }
        onDispose {}
    }
    return gm
}

private fun createMapView(context: Context, gm: GameMap): MapView {
    MapLibre.getInstance(context)
    val opts = MapLibreMapOptions.createFromAttributes(context)
        // A TextureView sits in Compose's layers properly (clipping, folding, overlays).
        .textureMode(true)
        .minZoomPreference(GameMap.MIN_ZOOM)
        .maxZoomPreference(GameMap.MAX_ZOOM)
        .maxPitchPreference(60.0)
    return MapView(context, opts).apply {
        gm.view = this
        onCreate(null)
        var failedOnce = false
        addOnDidFailLoadingMapListener {
            // The dark style didn't load: use the light one rather than nothing.
            val m = gm.map ?: return@addOnDidFailLoadingMapListener
            if (!failedOnce && m.style?.uri == STYLE_DARK) {
                failedOnce = true
                m.setStyle(STYLE_LIGHT) { gm.styleReady = true; gm.tick++ }
            }
        }
        getMapAsync { m ->
            // Locked on the hero: the game handles zoom and turning itself.
            m.uiSettings.apply {
                setAllGesturesEnabled(false)
                // The game draws its own compass (it can sit beside the map on a foldable).
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
            }
            m.addOnCameraMoveListener { gm.tick++ }
            m.addOnCameraIdleListener { gm.tick++ }
            gm.map = m
            gm.tick++
        }
    }
}

/**
 * The map itself with its gestures: pinch to zoom, double-tap to zoom in,
 * double-tap-hold-and-slide to zoom, drag (one finger round the hero, or
 * two fingers twisting) to turn it. No panning: it stays on the hero.
 */
@Composable
fun GameMapView(gm: GameMap, modifier: Modifier = Modifier, onUserTurn: () -> Unit = {}, content: @Composable () -> Unit) {
    val turned = androidx.compose.runtime.rememberUpdatedState(onUserTurn)
    val density = LocalDensity.current
    val slop = with(density) { 8.dp.toPx() }
    val pxPerLevel = with(density) { 110.dp.toPx() }
    var lastTapAt by remember { mutableStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .background(Color(0xFFE8E4DC))
            .pointerInput(gm) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val second = down.uptimeMillis - lastTapAt < 320 && (down.position - lastTapPos).getDistance() < slop * 6
                    // 0 tap, 1 double-tap pending, 2 slide-zoom, 3 turn, 4 pinch
                    var mode = if (second) 1 else 0
                    val startZoom = gm.zoom
                    var prev = down.position
                    // Turning pivots around the hero, who stands lower on screen when zoomed in.
                    val center = Offset(size.width / 2f, size.height * (0.5f + GameMap.lookAhead(gm.zoom).toFloat() / 2f))
                    if (second) down.consume()
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            if (mode == 1) {
                                gm.zoomIn()
                                ev.changes.forEach { it.consume() }
                                lastTapAt = 0L
                            } else if (mode == 0) {
                                lastTapAt = ev.changes.first().uptimeMillis
                                lastTapPos = ev.changes.first().position
                            } else {
                                ev.changes.forEach { it.consume() }
                                lastTapAt = 0L
                            }
                            break
                        }
                        if (pressed.size >= 2) {
                            mode = 4
                            val z = ev.calculateZoom()
                            if (z > 0f && z != 1f) gm.zoomBy(ln(z.toDouble()) / ln(2.0))
                            val twist = ev.calculateRotation()
                            // A real twist, not the slight turn every pinch has.
                            if (abs(twist) > 2f) turned.value()
                            gm.rotateBy(twist)
                            ev.changes.forEach { it.consume() }
                            prev = ev.calculateCentroid()
                            continue
                        }
                        val c = pressed.first()
                        when (mode) {
                            1, 2 -> {
                                val dy = c.position.y - down.position.y
                                if (abs(dy) > slop) mode = 2
                                // Down zooms in, up zooms out.
                                if (mode == 2) gm.zoomTo(startZoom + dy / pxPerLevel)
                                c.consume()
                            }
                            0 -> if ((c.position - down.position).getDistance() > slop) {
                                mode = 3
                                prev = c.position
                                c.consume()
                            }
                            3 -> {
                                val a0 = atan2(prev.y - center.y, prev.x - center.x)
                                val a1 = atan2(c.position.y - center.y, c.position.x - center.x)
                                var d = Math.toDegrees((a1 - a0).toDouble()).toFloat()
                                if (d > 180) d -= 360
                                if (d < -180) d += 360
                                // Near the middle a small move would be a big turn; ignore it there.
                                if ((c.position - center).getDistance() > slop * 4) {
                                    if (abs(d) > 0.5f) turned.value()
                                    gm.rotateBy(d)
                                }
                                prev = c.position
                                c.consume()
                            }
                        }
                    }
                }
            },
    ) {
        if (!gm.fake) {
            val view = gm.view
            if (view != null) {
                AndroidView(
                    factory = {
                        // The same MapView moves between layouts when the phone folds or unfolds.
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().then(
                Modifier.layoutSize { w, h ->
                    gm.widthPx = w; gm.heightPx = h
                },
            ),
        ) { content() }
    }
}

private fun Modifier.layoutSize(onSize: (Int, Int) -> Unit) = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        onSize(p.width, p.height)
        layout(p.width, p.height) { p.place(0, 0) }
    },
)

/** Something standing on the map: a picture whose [anchorX]/[anchorY] point sits on the spot. */
class MapSprite(
    val key: String,
    val lat: Double,
    val lon: Double,
    val image: ImageBitmap,
    val wDp: Int,
    val hDp: Int,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 1f,
    val alpha: Float = 1f,
    /** Where it stands right now, when it moves smoothly (the hero gliding between GPS fixes). */
    val at: (() -> Pair<Double, Double>?)? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * Draws [sprites] where they stand. They re-place themselves as the camera
 * moves without recomposing, and nearer ones (lower on screen) draw on top.
 */
@Composable
fun MapSprites(gm: GameMap, sprites: List<MapSprite>) {
    Layout(
        content = {
            sprites.forEach { s ->
                val click = s.onClick
                Image(
                    s.image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(s.wDp.dp, s.hDp.dp)
                        .graphicsLayer { alpha = s.alpha }
                        .then(
                            if (click != null) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = click,
                            ) else Modifier,
                        ),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            @Suppress("UNUSED_VARIABLE") val t = gm.tick
            placeables.forEachIndexed { i, p ->
                val s = sprites[i]
                val (lat, lon) = s.at?.invoke() ?: (s.lat to s.lon)
                val at = gm.toScreen(lat, lon) ?: return@forEachIndexed
                val x = at.x - p.width * s.anchorX
                val y = at.y - p.height * s.anchorY
                // Off screen: skip.
                if (x > constraints.maxWidth || y > constraints.maxHeight || x + p.width < 0 || y + p.height < 0) return@forEachIndexed
                p.place(x.toInt(), y.toInt(), zIndex = at.y)
            }
        }
    }
}

/** A walking route on the ground: a dark edge under a dashed gold line, along the streets to the destination. */
@Composable
fun MapRoute(gm: GameMap, path: List<Pair<Double, Double>>) {
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val t = gm.tick
        val p = Path()
        path.forEachIndexed { i, (lat, lon) ->
            val o = gm.toScreen(lat, lon) ?: return@Canvas
            if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
        }
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        val join = androidx.compose.ui.graphics.StrokeJoin.Round
        drawPath(p, Color(0xAA140D0A), style = Stroke(width = 12f, cap = round, join = join))
        drawPath(
            p, Color(0xFFEBC678),
            style = Stroke(
                width = 6f, cap = round, join = join,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 10f)),
            ),
        )
    }
}

/**
 * Food set out: its scent around the hero. Rings ripple outward on the ground
 * in the food's colour (leaning with the map's tilt), and wisps of scent drift
 * up off the hero and fade. Fine and Gourmet food ripple further, and Gourmet
 * food adds gold sparkles. With reduced effects, one still ring.
 */
@Composable
fun MapScentAura(gm: GameMap, center: () -> Pair<Double, Double>?, color: Color, tier: Int) {
    val clock = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val still = com.monsterquest.app.ui.art.ArtQuality.reduced
    if (!still) androidx.compose.runtime.LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameMillis { it }
        while (true) androidx.compose.runtime.withFrameMillis { clock.longValue = it - start }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val tick = gm.tick
        val t = clock.longValue / 1000f
        val (lat, lon) = center() ?: return@Canvas
        val hero = gm.toScreen(lat, lon) ?: return@Canvas
        val reach = 26.0 + 8.0 * tier
        fun ring(radiusM: Double): Path? {
            val path = Path()
            val dLat = radiusM / 111_320.0
            val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
            for (i in 0..40) {
                val a = 2 * PI * i / 40
                val p = gm.toScreen(lat + dLat * kotlin.math.sin(a), lon + dLon * cos(a)) ?: return null
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            return path
        }
        if (still) {
            ring(reach * 0.7)?.let { drawPath(it, color.copy(alpha = 0.5f), style = Stroke(width = 4f)) }
            return@Canvas
        }
        // Rings rippling out from the hero, fading as they spread.
        for (k in 0 until 3) {
            val p = ((t / 2.6f) + k / 3f) % 1f
            ring(6.0 + reach * p)?.let {
                drawPath(it, color.copy(alpha = 0.16f * (1 - p)))
                drawPath(it, color.copy(alpha = 0.75f * (1 - p)), style = Stroke(width = 5f * (1 - p) + 1.5f))
            }
        }
        // Wisps of scent drifting up off the hero.
        val unit = size.minDimension / 100f
        for (k in 0 until 5) {
            val p = ((t / 2.2f) + k / 5f) % 1f
            val x = hero.x + kotlin.math.sin((p * 5f + k * 1.3f).toDouble()).toFloat() * unit * 4f + (k - 2) * unit * 2.2f
            val y = hero.y - unit * 4f - p * unit * 22f
            val fade = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
            drawCircle(color.copy(alpha = 0.55f * fade), unit * (1.1f + 1.4f * p), Offset(x, y))
        }
        // Gourmet food: gold sparkles circling the hero.
        if (tier >= 3) for (k in 0 until 3) {
            val a = t * 1.4f + k * 2.09f
            val fade = (kotlin.math.sin((t * 2.5f + k).toDouble()).toFloat() + 1) / 2
            drawCircle(Color(0xFFEBC678).copy(alpha = fade), unit * 0.9f, Offset(hero.x + kotlin.math.cos(a) * unit * 9f, hero.y - unit * 8f + kotlin.math.sin(a) * unit * 3f))
        }
    }
}

/** A circle on the ground ([radiusM] around a spot); it leans with the map's tilt. */
@Composable
fun MapCircle(gm: GameMap, center: () -> Pair<Double, Double>?, radiusM: Double, fill: Color, stroke: Color) {
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val t = gm.tick
        val (lat, lon) = center() ?: return@Canvas
        val path = Path()
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
        for (i in 0..48) {
            val a = 2 * PI * i / 48
            val p = gm.toScreen(lat + dLat * kotlin.math.sin(a), lon + dLon * cos(a)) ?: return@Canvas
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(width = 3f))
    }
}

/**
 * The map's compass: the red needle points north. Tap it to have the map turn
 * with the phone (a gold ring shows it's following), and again for north up.
 */
@Composable
fun MapCompass(gm: GameMap, following: Boolean = false, onClick: () -> Unit = { gm.resetNorth() }, modifier: Modifier = Modifier) {
    // Follows the app's light or dark theme.
    val face = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val tail = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .size(40.dp)
            .background(face, androidx.compose.foundation.shape.CircleShape)
            .then(if (following) Modifier.border(2.dp, Color(0xFFEBC678), androidx.compose.foundation.shape.CircleShape) else Modifier)
            .clickable(onClickLabel = if (following) "North up" else "Turn with the phone") { onClick() }
            .semanticsLabel(if (following) "Compass, turning with the phone" else "Compass"),
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { @Suppress("UNUSED_VARIABLE") val t = gm.tick; rotationZ = (-gm.bearing).toFloat() }) {
            val c = Offset(size.width / 2, size.height / 2)
            val len = size.minDimension * 0.36f
            val half = size.minDimension * 0.11f
            val north = Path().apply { moveTo(c.x, c.y - len); lineTo(c.x - half, c.y); lineTo(c.x + half, c.y); close() }
            val south = Path().apply { moveTo(c.x, c.y + len); lineTo(c.x - half, c.y); lineTo(c.x + half, c.y); close() }
            drawPath(north, Color(0xFFE0574A))
            drawPath(south, tail)
        }
    }
}

private fun Modifier.semanticsLabel(label: String) =
    this.then(androidx.compose.ui.Modifier.semantics { contentDescription = label })

/**
 * Glides from one GPS fix to the next over about a second, so the hero walks
 * instead of jumping. Big jumps (a fix after a tunnel, or the first one) snap.
 * Read the returned state in layout or drawing, not in composition, so the
 * glide only moves things on screen.
 */
@Composable
fun rememberGlide(target: Pair<Double, Double>?): androidx.compose.runtime.State<Pair<Double, Double>?> {
    val shown = remember { mutableStateOf(target) }
    androidx.compose.runtime.LaunchedEffect(target) {
        val to = target
        val from = shown.value
        if (to == null || from == null || World.distanceMeters(from.first, from.second, to.first, to.second) > 150) {
            shown.value = to
            return@LaunchedEffect
        }
        val start = androidx.compose.runtime.withFrameNanos { it }
        val duration = 900_000_000.0
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val t = ((now - start) / duration).coerceIn(0.0, 1.0)
            // Ease out: quick to start, settling gently.
            val e = 1 - (1 - t) * (1 - t)
            shown.value = (from.first + (to.first - from.first) * e) to (from.second + (to.second - from.second) * e)
            if (t >= 1.0) break
        }
    }
    return shown
}

/**
 * While [enabled], turns the map to face the way the phone points, so what's
 * in front of you is up the screen. Works with the phone flat or held up: it
 * uses the screen's top edge and the back camera's direction together.
 */
@Composable
fun HeadingFollower(gm: GameMap, enabled: Boolean) {
    if (!enabled || gm.fake) return
    val context = LocalContext.current
    DisposableEffect(gm, enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        val r = FloatArray(9)
        // Smoothed heading as a direction vector (averaging angles directly breaks at north).
        var sx = 0.0
        var sy = 0.0
        var lastMs = 0L
        var lastSet = Double.NaN
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                android.hardware.SensorManager.getRotationMatrixFromVector(r, e.values)
                @Suppress("DEPRECATION")
                val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                // The screen's "up" axis in device terms, for how the screen is turned.
                val (ux, uy) = when (rotation) {
                    android.view.Surface.ROTATION_90 -> -1f to 0f
                    android.view.Surface.ROTATION_180 -> 0f to -1f
                    android.view.Surface.ROTATION_270 -> 1f to 0f
                    else -> 0f to 1f
                }
                // World (east, north) of screen-up plus the back camera's direction (-Z).
                val east = r[0] * ux + r[1] * uy - r[2]
                val north = r[3] * ux + r[4] * uy - r[5]
                val len = kotlin.math.hypot(east.toDouble(), north.toDouble())
                if (len < 1e-3) return
                val a = if (sx == 0.0 && sy == 0.0) 1.0 else 0.15
                sx = sx * (1 - a) + east / len * a
                sy = sy * (1 - a) + north / len * a
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastMs < 33) return
                val deg = (Math.toDegrees(atan2(sx, sy)) + 360) % 360
                if (!lastSet.isNaN()) {
                    var diff = abs(deg - lastSet) % 360
                    if (diff > 180) diff = 360 - diff
                    if (diff < 0.7) return
                }
                lastMs = now
                lastSet = deg
                gm.setBearing(deg)
            }

            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
}
