package com.monsterquest.app.ui

import androidx.compose.ui.graphics.asImageBitmap
import com.monsterquest.app.ui.art.ArtQuality
import com.monsterquest.app.Haptics
import com.monsterquest.app.ui.art.BeaconState
import com.monsterquest.app.ui.art.drawPlayerOn
import com.monsterquest.app.ui.art.drawShrineOn
import com.monsterquest.app.ui.art.drawVillainOn
import com.monsterquest.app.ui.art.drawTraderOn
import com.monsterquest.app.ui.art.drawNomadOn
import com.monsterquest.app.ui.art.drawDungeonOn
import com.monsterquest.app.ui.art.drawFacilityOn
import com.monsterquest.app.ui.art.spriteDrawable
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.ThugArt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.monsterquest.app.ui.art.drawDenOn
import com.monsterquest.core.Den
import com.monsterquest.core.Dens
import com.monsterquest.app.ui.art.drawFocusPin
import com.monsterquest.app.data.StreetLookup
import com.monsterquest.app.Navigation
import androidx.compose.runtime.produceState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Bestiary
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Schedule
import com.monsterquest.core.Shrine
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.Item
import com.monsterquest.core.VillainCamp
import com.monsterquest.core.VillainTier
import com.monsterquest.core.Villains
import com.monsterquest.core.World
import kotlinx.coroutines.delay

/** Nothing farther than this (10 miles) shows in the Nearby list. */
private const val MAX_NEARBY_M = 16_093.0

/** A spot picked from the Nearby list: the map pans there and a card offers directions. */
private class MapFocus(
    val lat: Double,
    val lon: Double,
    val title: String,
    val sub: String,
    /** Drop a temporary pin (monsters, which may not be drawn on the map yet). */
    val pin: Boolean,
    val details: () -> Unit,
)

private sealed interface MapPick {
    data class ShrinePick(val shrine: Shrine) : MapPick
    data class VillainPick(val camp: VillainCamp) : MapPick
    data class DenPick(val den: Den) : MapPick
    data class TraderPick(val trader: com.monsterquest.core.Trader) : MapPick
    data class NomadPick(val nomad: com.monsterquest.core.Nomad) : MapPick
    data class DungeonPick(val dungeon: com.monsterquest.core.Dungeon) : MapPick
    data class FacilityPick(val venue: com.monsterquest.core.Venue) : MapPick
}

internal fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MapScreen(vm: GameViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GIcon(GameIcon.MAP, Modifier.size(64.dp), Gold)
            Text(
                "Explore the real world! Item shrines at cafes, restaurants and parks hold weapons, " +
                    "spell pages, Binding Crystals and potions. Silver shrines (Tue/Thu) and Gold shrines (weekends) hold guardians " +
                    "that unlock when you keep up with your quests. " +
                    "Monsters roam nearby too.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
        return
    }

    TrackLocation(context, vm.state.settings.batterySaverGps || vm.batterySaver, vm::onLocation)
    // Secret walking encounters only happen while the map is open.
    DisposableEffect(Unit) {
        vm.mapVisible = true
        onDispose { vm.mapVisible = false }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            // Monsters creep closer over time, so refresh often.
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val loc = vm.location
    // Today's risen Silver or Gold shrine joins the rest.
    val risen = com.monsterquest.core.RoamingShrines.current(vm.state, now)
    val shrines = vm.shrines + listOfNotNull(risen?.shrine)
    val villains = vm.villainsNear(now)
    val dens = vm.densNear(now)
    val traders = vm.tradersNear(now)
    val nomads = vm.nomadsNear(now)
    val dungeons = vm.dungeonsNear(now)
    val facilities = vm.facilitiesNear()
    var pick by remember { mutableStateOf<MapPick?>(null) }
    var showWeather by remember { mutableStateOf(false) }
    if (showWeather) WeatherDialog(vm, now) { showWeather = false }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    // The pin and card are temporary: they clear themselves after a few minutes.
    LaunchedEffect(focus) {
        if (focus != null) {
            delay(5 * 60_000L)
            focus = null
        }
    }

    // Battery saver: turn the phone upside down (e.g. hanging at your side or in a
    // pocket) and the screen goes dark; turn it back upright to return.
    if (vm.state.settings.flipBatterySaver) UpsideDownSaver(vm)

    // The map follows the hero; looking at something from the Nearby list leaves it there briefly.
    var following by remember { mutableStateOf(true) }

    val darkMap = when (vm.state.settings.mapStyle) {
        com.monsterquest.core.MapStyle.AUTO -> isDarkTheme(vm.state.settings.themeMode)
        com.monsterquest.core.MapStyle.LIGHT -> false
        com.monsterquest.core.MapStyle.DARK -> true
    }
    val gameMap = rememberGameMap(darkMap)
    // Walking somewhere: close what's open, and work out the route along the streets in the loaded map,
    // again every ~20 m you walk. Arriving ends it.
    val route = vm.walkRoute
    LaunchedEffect(route?.lat, route?.lon) { if (route != null) { pick = null; focus = null } }
    LaunchedEffect(route?.lat, route?.lon, gameMap.styleReady, loc?.let { (it.first * 5_000).toInt() to (it.second * 5_000).toInt() }) {
        val r = vm.walkRoute ?: return@LaunchedEffect
        val here = vm.location ?: return@LaunchedEffect
        if (World.distanceMeters(here.first, here.second, r.lat, r.lon) < com.monsterquest.core.Routing.ARRIVED_M) {
            vm.walkRoute = null
            vm.toast("You've arrived at ${r.label}!")
            return@LaunchedEffect
        }
        if (r.path == null) kotlinx.coroutines.delay(300)
        // The streets there may still be loading: try a few times before giving up.
        var path: List<Pair<Double, Double>>? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) kotlinx.coroutines.delay(2_500)
            val lines = gameMap.readWalkways()
            path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.monsterquest.core.Routing.route(com.monsterquest.core.Routing.buildWays(lines), here, r.lat to r.lon)
            }
            if (path != null) break
        }
        if (vm.walkRoute?.lat == r.lat && vm.walkRoute?.lon == r.lon) {
            // No street route: show none rather than a line across buildings.
            vm.walkRoute = r.copy(path = path ?: emptyList(), straight = path == null)
        }
    }
    val icons = remember { HashMap<String, androidx.compose.ui.graphics.ImageBitmap>() }
    fun sprite(key: String, wDp: Int, hDp: Int, draw: (android.graphics.Canvas, Float, Float) -> Unit) =
        icons.getOrPut("$key:${ArtQuality.reduced}") { spriteDrawable(context, wDp, hDp, draw).bitmap.asImageBitmap() }

    // The hero (and the camera) glide to each new GPS fix instead of jumping.
    val glide = rememberGlide(loc)
    // Keep the map on the hero, within a mile of them.
    LaunchedEffect(loc, gameMap.map, gameMap.tick > 0) {
        val l = loc ?: return@LaunchedEffect
        gameMap.limitAround(l.first, l.second)
    }
    val followingNow = androidx.compose.runtime.rememberUpdatedState(following)
    LaunchedEffect(gameMap.map) {
        androidx.compose.runtime.snapshotFlow { glide.value.takeIf { followingNow.value } }.collect { p ->
            if (p != null) gameMap.centerOn(p.first, p.second)
        }
    }
    // The map can turn with the phone, so what's in front of you is up the screen.
    val followHeading = vm.state.settings.followHeading
    HeadingFollower(gameMap, followHeading)
    val onCompass: () -> Unit = {
        when {
            followHeading -> { vm.updateSettings { it.copy(followHeading = false) }; gameMap.resetNorth() }
            kotlin.math.abs(gameMap.bearing) > 1 -> gameMap.resetNorth()
            else -> vm.updateSettings { it.copy(followHeading = true) }
        }
    }
    // Turning the map by hand stops it following the phone.
    val onUserTurn: () -> Unit = { if (vm.state.settings.followHeading) vm.updateSettings { it.copy(followHeading = false) } }
    // Read cafés, doctors, groceries and streets out of the map's own tiles once it settles.
    LaunchedEffect(gameMap.styleReady, loc?.let { (it.first * 500).toInt() to (it.second * 500).toInt() }) {
        if (!gameMap.styleReady) return@LaunchedEffect
        // Give the tiles a moment to arrive.
        kotlinx.coroutines.delay(2_500)
        gameMap.readPlaces()?.let { vm.addMapPlaces(it.venues, it.streets, it.tiles, it.shrines) }
    }

    // What's around, as small pills that wrap cleanly instead of one long line.
    val placeCounts: @Composable (Modifier) -> Unit = { mod ->
        if (vm.loadingPlaces) {
            Text("Scanning for shrines…", fontSize = 13.sp, modifier = mod)
        } else {
            androidx.compose.foundation.layout.FlowRow(
                mod,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MapCount("${shrines.count { it.tier == ShrineTier.ITEM }} item · ${shrines.count { it.tier == ShrineTier.SILVER }} silver · ${shrines.count { it.tier == ShrineTier.GOLD }} gold")
                MapCount("${dens.size} dens")
                if (dungeons.isNotEmpty()) MapCount("${dungeons.size} dungeons")
                if (villains.isNotEmpty()) MapCount("${villains.size} foes")
                if (traders.isNotEmpty()) MapCount("${traders.size} traders")
                if (nomads.isNotEmpty()) MapCount("${nomads.size} nomads")
            }
        }
    }
    val weatherBadge: @Composable (Modifier) -> Unit = { mod ->
        vm.weather?.takeIf { vm.state.settings.liveWeather }?.let { w ->
            WeatherBadge(w, vm.state.settings.imperialUnits, mod) { showWeather = true }
        }
    }
    // Food set out: its icon and time left (tap for who it draws).
    val foodBadge: @Composable (Modifier) -> Unit = { mod ->
        com.monsterquest.core.Foods.active(vm.state, now)?.let { food ->
            Row(
                mod
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable { vm.toast("${food.label} is out: monsters that eat it are far likelier to find you on battery-saver walks. ${foodEatersText(vm.state, food)}") }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GIcon(food.icon(), Modifier.size(18.dp), food.color())
                Spacer(Modifier.width(4.dp))
                Text(formatDuration(vm.state.foodUntilMs - now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    // Phones show the place counts over the map; big screens show them under the Nearby list.
    val mapPane: @Composable (Boolean) -> Unit = { showTracker ->
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val sprites = buildList {
                    shrines.forEach { sh ->
                        val cooling = GameEngine.shrineCooldownLeft(vm.state, sh, now) > 0
                        val open = Schedule.access(vm.state, sh, now).open
                        val beacon = when {
                            sh.isBoss && !open -> BeaconState.LOCKED
                            cooling -> BeaconState.COOLING
                            else -> BeaconState.OPEN
                        }
                        // Boss shrines stand taller so they're easy to spot from afar.
                        val (bw, bh) = if (sh.isBoss) 60 to 110 else 48 to 88
                        add(MapSprite("sh:${sh.id}", sh.lat, sh.lon, sprite("shrine_${sh.tier}_$beacon", bw, bh) { c, w, h -> drawShrineOn(c, w, h, sh.tier, beacon) }, bw, bh, anchorY = 0.945f) { pick = MapPick.ShrinePick(sh) })
                    }
                    dens.forEach { den ->
                        add(MapSprite("dn:${den.id}", den.lat, den.lon, sprite("den_${den.species.element}", 56, 48) { c, w, h -> drawDenOn(c, w, h, den.species.element) }, 56, 48, anchorY = 0.93f) { pick = MapPick.DenPick(den) })
                    }
                    villains.forEach { v ->
                        val isVillain = v.tier == VillainTier.VILLAIN
                        add(MapSprite("vl:${v.id}", v.lat, v.lon, sprite("villain_$isVillain", 46, 69) { c, w, h -> drawVillainOn(c, w, h, isVillain) }, 46, 69, anchorY = 0.955f) { pick = MapPick.VillainPick(v) })
                    }
                    traders.forEach { t ->
                        add(MapSprite("tr:${t.id}", t.lat, t.lon, sprite("trader", 52, 52) { c, w, h -> drawTraderOn(c, w, h) }, 52, 52, anchorY = 0.9f) { pick = MapPick.TraderPick(t) })
                    }
                    facilities.forEach { f ->
                        val locked = !com.monsterquest.core.Facilities.academyOpen(f, now)
                        add(MapSprite("fc:${f.id}", f.lat, f.lon, sprite("facility_${f.role}_$locked", 34, 34) { c, w, h -> drawFacilityOn(c, w, h, f.role, locked) }, 34, 34, anchorY = 0.5f) { pick = MapPick.FacilityPick(f) })
                    }
                    dungeons.forEach { dg ->
                        add(MapSprite("dg:${dg.id}", dg.lat, dg.lon, sprite("dungeon_${dg.stars}", 60, 60) { c, w, h -> drawDungeonOn(c, w, h, dg.stars) }, 60, 60, anchorY = 0.95f) { pick = MapPick.DungeonPick(dg) })
                    }
                    nomads.forEach { n ->
                        add(MapSprite("nm:${n.id}", n.lat, n.lon, sprite("nomad", 52, 52) { c, w, h -> drawNomadOn(c, w, h) }, 52, 52, anchorY = 0.9f) { pick = MapPick.NomadPick(n) })
                    }
                    if (loc != null) {
                        val outfit = HeroOutfit.of(vm.state, hoodDown = true)
                        val stars = com.monsterquest.core.Stars.of(vm.state.hero.level)
                        add(MapSprite("hero", loc.first, loc.second, at = { glide.value }, image = sprite("player_${vm.state.hero.heroClass}_${outfit}_${stars}_${com.monsterquest.core.Titles.CENTURION in vm.state.titles}", 60, 60) { c, w, h -> drawPlayerOn(c, w, h, vm.state.hero.heroClass, outfit, stars, centurion = com.monsterquest.core.Titles.CENTURION in vm.state.titles) }, wDp = 60, hDp = 60, anchorY = 0.86f) {})
                    }
                    focus?.takeIf { it.pin }?.let { f ->
                        add(MapSprite("focus", f.lat, f.lon, sprite("focus_pin", 30, 40) { c, w, h -> drawFocusPin(c, w, h) }, 30, 40, anchorY = 1f) { f.details() })
                    }
                }
                GameMapView(gameMap, Modifier.fillMaxSize(), onUserTurn = onUserTurn) {
                    // Reach circle for shrines.
                    if (loc != null) MapCircle(gameMap, { glide.value }, World.SHRINE_RANGE_M, Color(0x228B5CF6), Color(0x888B5CF6))
                    vm.walkRoute?.path?.takeIf { it.size >= 2 }?.let { MapRoute(gameMap, it) }
                    risen?.let { RisenShrineFx(gameMap, it) }
                    MapSprites(gameMap, sprites)
                }
                // On a phone the compass (with any food below it) and the weather sit on the map;
                // on a foldable they move beside it, above Nearby.
                if (showTracker) {
                    Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapCompass(gameMap, followHeading, onCompass)
                        foodBadge(Modifier)
                    }
                    weatherBadge(Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
                focus?.let { f -> FocusCard(vm, f, Modifier.align(Alignment.BottomCenter)) { focus = null } }
                vm.walkRoute?.let { r -> RouteCard(vm, r, Modifier.align(Alignment.TopStart).padding(end = 64.dp)) }
                if (loc == null) {
                    Text(
                        "Finding your location…",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTracker) placeCounts(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (!following) {
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(onClick = {
                            following = true
                            loc?.let { gameMap.animateTo(it.first, it.second) }
                        }) { IconText(GameIcon.PIN, "Back to me") }
                    }
                }
            }
            Text(
                "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
    }

    fun focusOn(f: MapFocus) {
        focus = f
        following = false
        gameMap.animateTo(f.lat, f.lon)
    }
    // After a look at something in the Nearby list, the map glides back to the hero.
    LaunchedEffect(following, focus) {
        if (!following) {
            kotlinx.coroutines.delay(LOOK_AWAY_MS)
            following = true
            loc?.let { gameMap.animateTo(it.first, it.second) }
        }
    }
    val imperial = vm.state.settings.imperialUnits
    AdaptivePanes(
        // The map takes about two thirds; the side pane lines up with the Bag button below.
        firstWeight = 0.68f,
        compact = { mapPane(true) },
        first = { mapPane(false) },
        second = {
          Column(Modifier.fillMaxSize()) {
            // Beside the map on a foldable: the weather, compass and food above Nearby, and what's around below it.
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapCompass(gameMap, followHeading, onCompass)
                foodBadge(Modifier)
                Spacer(Modifier.weight(1f))
                weatherBadge(Modifier)
            }
            Box(Modifier.weight(1f)) {
            NearbyList(
                vm = vm,
                shrines = shrines,
                villains = villains,
                dens = dens,
                dungeons = dungeons,
                traders = traders,
                nomads = nomads,
                facilities = facilities,
                onOther = { pick = it },
                onDen = { den ->
                    focusOn(MapFocus(den.lat, den.lon, den.name, "Lv ${den.monster.level} ${den.species.name} • strong", pin = false) { pick = MapPick.DenPick(den) })
                },
                onShrine = { sh ->
                    focusOn(MapFocus(sh.lat, sh.lon, sh.name, sh.subtitle ?: sh.tier.label, pin = false) { pick = MapPick.ShrinePick(sh) })
                },
                onVillain = { v ->
                    focusOn(MapFocus(v.lat, v.lon, v.leader, "${v.tier.label} of the ${v.crew}", pin = false) { pick = MapPick.VillainPick(v) })
                },
            )
            }
            placeCounts(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 8.dp))
          }
        },
    )

    when (val p = pick) {
        is MapPick.ShrinePick -> ShrineDialog(vm, p.shrine, now) { pick = null }
        is MapPick.VillainPick -> VillainDialog(vm, p.camp) { pick = null }
        is MapPick.DenPick -> DenDialog(vm, p.den) { pick = null }
        is MapPick.TraderPick -> TraderDialog(vm, p.trader) { pick = null }
        is MapPick.NomadPick -> NomadDialog(vm, p.nomad) { pick = null }
        is MapPick.DungeonPick -> DungeonDialog(vm, p.dungeon, vm.distanceTo(p.dungeon.lat, p.dungeon.lon)) { pick = null }
        is MapPick.FacilityPick -> FacilityDialog(vm, p.venue) { pick = null }
        null -> {}
    }
}

/** How long the map stays on something picked from the Nearby list before returning to the hero. */
private const val LOOK_AWAY_MS = 10_000L

/** A small rounded count under the map ("8 near", "3 dens"...). */
/** The weather's icon. */
fun com.monsterquest.core.LiveWeather.icon(): GameIcon = when (this) {
    com.monsterquest.core.LiveWeather.CLEAR, com.monsterquest.core.LiveWeather.HOT -> GameIcon.SUN
    com.monsterquest.core.LiveWeather.CLOUDY -> GameIcon.CLOUD
    com.monsterquest.core.LiveWeather.FOG -> GameIcon.FOG
    com.monsterquest.core.LiveWeather.RAIN -> GameIcon.RAIN
    com.monsterquest.core.LiveWeather.STORM -> GameIcon.STORM
    com.monsterquest.core.LiveWeather.SNOW -> GameIcon.SNOW
    com.monsterquest.core.LiveWeather.WIND -> GameIcon.WIND
}

private fun temperature(c: Double, imperial: Boolean) = if (imperial) "${Math.round(c * 9 / 5 + 32)}°F" else "${Math.round(c)}°C"

/** The real weather, floating on the map: tap it to see what it brings out. */
@Composable
private fun WeatherBadge(w: com.monsterquest.core.WeatherReading, imperial: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClickLabel = "Weather effects", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(w.kind.icon(), Modifier.size(20.dp), Gold)
        Spacer(Modifier.width(6.dp))
        val event = com.monsterquest.core.WeatherEvent.of(w.kind)
        Text(
            (event?.label ?: w.kind.label) + " ${temperature(w.tempC, imperial)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (event != null) Gold else MaterialTheme.colorScheme.onSurface,
        )
        // The types it favors, as small icons.
        w.kind.more.take(2).forEach { el ->
            Spacer(Modifier.width(4.dp))
            GIcon(el.icon(), Modifier.size(16.dp), el.color())
        }
    }
}

/** What the weather and the time of day are doing to the monsters out right now. */
@Composable
private fun WeatherDialog(vm: GameViewModel, now: Long, onDismiss: () -> Unit) {
    val w = vm.weather
    val imperial = vm.state.settings.imperialUnits
    val time = com.monsterquest.core.TimeOfDay.of(java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { w?.let { GIcon(it.kind.icon(), Modifier.size(32.dp), Gold) } },
        title = { Text(w?.let { "${it.kind.label}, ${temperature(it.tempC, imperial)}" } ?: "Weather") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w == null) {
                    Text("The weather hasn't been looked up yet.")
                } else {
                    com.monsterquest.core.WeatherEvent.of(w.kind)?.let { e ->
                        Text("${e.label}! ${e.text}", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Its type surges (x${com.monsterquest.core.WeatherEvent.SURGE} more), and once a day it sends a rare visitor to find you on a battery-saver walk.", fontSize = 12.sp)
                    }
                    val wanted = com.monsterquest.core.Wanted.boostedBy(vm.state, w.kind)
                    if (wanted.isNotEmpty()) {
                        Text("Wanted lines more likely now: ${wanted.joinToString { it.name }}", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (w.kind.more.isNotEmpty()) {
                        Text("More likely now", fontWeight = FontWeight.SemiBold)
                        w.kind.more.forEach { el -> WeatherTypeLine(el, "x${"%.1f".format(w.kind.boosts.getValue(el))}") }
                    }
                    if (w.kind.fewer.isNotEmpty()) {
                        Text("Less likely", fontWeight = FontWeight.SemiBold)
                        w.kind.fewer.forEach { el -> WeatherTypeLine(el, "x${"%.1f".format(w.kind.boosts.getValue(el))}") }
                    }
                    when (w.kind.battle) {
                        com.monsterquest.core.Weather.RAIN -> Text("Battles start in Rain: Water and Electric moves +25%, Fire −15%.", fontSize = 13.sp)
                        com.monsterquest.core.Weather.DROUGHT -> Text("Battles start in a Drought: Fire and Corrupt moves +25%, Water −15%.", fontSize = 13.sp)
                        com.monsterquest.core.Weather.CLEAR -> {}
                    }
                }
                Text(
                    "${time.label}: ${time.favored.joinToString(" and ") { it.label }} types are out more (x3).",
                    fontSize = 13.sp,
                )
                // Lakes, rivers and coast bring out Water types; far from water they're rare.
                when (com.monsterquest.core.World.waterFactor(vm.waterDistance())) {
                    com.monsterquest.core.World.NEAR_WATER_FACTOR -> Text("Near water (a lake, river or coast): Water types x${com.monsterquest.core.World.NEAR_WATER_FACTOR}.", fontSize = 13.sp)
                    com.monsterquest.core.World.FAR_FROM_WATER_FACTOR -> Text("No water nearby: Water types are rarer (x${com.monsterquest.core.World.FAR_FROM_WATER_FACTOR}).", fontSize = 13.sp)
                    else -> {}
                }
                Text(
                    "New monsters follow the weather with the next wave (every 15 minutes). Live weather from Open-Meteo; turn it off in Settings.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun WeatherTypeLine(el: com.monsterquest.core.Element, factor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(el.icon(), Modifier.size(18.dp), el.color())
        Spacer(Modifier.width(8.dp))
        Text(el.label, modifier = Modifier.weight(1f))
        Text(factor, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapCount(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@SuppressLint("MissingPermission")
@Composable
internal fun TrackLocation(context: Context, batterySaver: Boolean = false, onLocation: (Double, Double, Float?) -> Unit) {
    DisposableEffect(batterySaver) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lastGpsMs = 0L
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (location.provider == LocationManager.GPS_PROVIDER) {
                    lastGpsMs = now
                } else if (now - lastGpsMs < 10_000) {
                    // A fresh GPS fix beats the rougher network one: don't let the hero jump back and forth.
                    return
                }
                onLocation(location.latitude, location.longitude, if (location.hasSpeed()) location.speed else null)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        try {
            providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { onLocation(it.latitude, it.longitude, null) }
            // Every second while you play; slow and cheap with the battery saver.
            val (interval, distance) = if (batterySaver) 20_000L to 25f else 1_000L to 0f
            providers.forEach { lm.requestLocationUpdates(it, interval, distance, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            // Permission revoked while running; the screen will ask again.
        }
        onDispose { lm.removeUpdates(listener) }
    }
}


/** A monster leapt out while you walked: a long buzz, "Ambushed!", then the fight starts on its own. */
@Composable
internal fun SecretAmbushPopup(vm: GameViewModel, group: List<com.monsterquest.core.Monster>) {
    val context = LocalContext.current
    LaunchedEffect(group) {
        if (vm.state.settings.vibrateOnSpawn) Haptics.tripleBuzz(context)
        delay(2_200)
        vm.startSecretAmbush()
    }
    val sp = group.first().species
    val shake = rememberInfiniteTransition(label = "ambush").animateFloat(
        -4f, 4f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "shake",
    )
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "AMBUSHED!",
                color = HpRed,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                modifier = Modifier.fillMaxWidth().offset(x = shake.value.dp),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val thug = vm.secretThug
                if (thug != null) {
                    // A thug and their crew: one monster for each of yours.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                        ThugArt(thug.cls, Modifier.size(96.dp))
                        group.forEach { m -> MonsterArt(m.species, Modifier.size(64.dp), shiny = m.shiny) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${thug.title} of the ${thug.crew} jumps you!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "Lv ${thug.level} ${thug.kind.lowercase()} • ${group.size} monster${if (group.size == 1) "" else "s"}, one for each of yours • they fight together",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                    group.forEach { m -> MonsterArt(m.species, Modifier.size(if (group.size == 1) 120.dp else 90.dp), shiny = m.shiny) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (group.size == 1) "A wild ${sp.name} leaps out in front of you!"
                    else "${group.size} wild ${sp.name}s leap out in front of you!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Lv ${group.first().level} • ${sp.typeLabel}" + if (group.size > 1) " • each one weaker as a pack" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                }
                if (group.any { it.shiny }) Text("Shiny!", color = Gold, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { Button(onClick = vm::startSecretAmbush) { Text("Fight!") } },
    )
}

/** The street at a point: the map data's own when it has one, otherwise asked of the phone's geocoder. */
@Composable
private fun rememberStreet(lat: Double, lon: Double, known: String? = null): String? {
    val context = LocalContext.current
    val street by produceState(known ?: StreetLookup.cached(lat, lon), lat, lon, known) {
        if (known == null) value = StreetLookup.streetAt(context, lat, lon)
    }
    return street
}

/** A dialog title with the real-world place underneath, e.g. "Barnes & Noble on Wards Road". */
@Composable
private fun PlaceTitle(icon: GameIcon, title: String, color: Color, subtitle: String?) {
    Column {
        IconText(icon, title, tint = color, size = 20.dp, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
        }
    }
}

/** Draws the walking route to a point on the game's own map (the phone's map app is a tap away on the route card). */
@Composable
private fun WalkHereButton(vm: GameViewModel, lat: Double, lon: Double, label: String) {
    OutlinedButton(onClick = { vm.walkTo(lat, lon, label) }, modifier = Modifier.padding(top = 8.dp)) {
        GIcon(GameIcon.WALK, Modifier.size(16.dp), Gold)
        Spacer(Modifier.width(6.dp))
        Text("Walk here")
    }
}

/** The route you're walking: how far and how long, the phone's map app if you want turn-by-turn, and End. */
@Composable
private fun RouteCard(vm: GameViewModel, r: GameViewModel.WalkRoute, modifier: Modifier) {
    val context = LocalContext.current
    val imperial = vm.state.settings.imperialUnits
    val meters = r.path?.takeIf { it.size >= 2 }?.let { com.monsterquest.core.Routing.length(it) }
    Column(
        modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, Gold, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        IconText(GameIcon.WALK, "Walking to ${r.label}", tint = Gold, bold = true)
        Text(
            when {
                r.straight -> "No street route here yet: the streets between you aren't loaded. Zoom out or walk a little, or use Phone maps."
                meters == null -> "Finding a route along the streets…"
                else -> "${formatDistance(meters, imperial)} along the streets  •  about ${com.monsterquest.core.Routing.minutes(meters)} min"
            },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { Navigation.walkTo(context, r.lat, r.lon, r.label) }) { Text("Phone maps") }
            TextButton(onClick = { vm.walkRoute = null }) { Text("End") }
        }
    }
}

/** Where a Nearby pick is: the map has panned there; walk there or see more. */
@Composable
private fun FocusCard(vm: GameViewModel, f: MapFocus, modifier: Modifier, onClose: () -> Unit) {
    val dist = vm.distanceTo(f.lat, f.lon)
    val here = vm.location
    val dir = here?.let { World.compass(World.bearing(it.first, it.second, f.lat, f.lon)) }
    Column(
        modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, Gold, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dist != null && dir != null) {
                    Text("${formatDistance(dist, vm.state.settings.imperialUnits)} to the $dir", fontSize = 12.sp, color = Gold)
                }
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WalkHereButton(vm, f.lat, f.lon, f.title)
            OutlinedButton(onClick = f.details, modifier = Modifier.padding(top = 8.dp)) { Text("Details") }
        }
    }
}

/** "At Barnes & Noble" / "Near Central Park (120 ft from it)". */
private fun landmarkText(place: Shrine, gap: Double, imperial: Boolean): String {
    val where = place.place ?: place.name
    return if (gap < 40) "At $where" else "Near $where (${formatDistance(gap, imperial)} from it)"
}

private fun distanceText(m: Double?, imperial: Boolean): String =
    if (m == null) "unknown distance" else "${formatDistance(m, imperial)} away"

@Composable
private fun ShrineDialog(vm: GameViewModel, shrine: Shrine, now: Long, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(shrine.lat, shrine.lon)
    val inRange = dist != null && dist <= World.SHRINE_RANGE_M
    val rec = vm.state.shrines[shrine.id]
    val cooldown = GameEngine.shrineCooldownLeft(vm.state, shrine, now)
    val guardian = shrine.bossSpeciesId?.let { Bestiary.get(it) }
    val access = Schedule.access(vm.state, shrine, now)
    val street = rememberStreet(shrine.lat, shrine.lon, shrine.street)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(shrine.tier.icon(), shrine.name, shrine.tier.color(), shrine.copy(street = street).subtitle) },
        text = {
            Column {
                Text("${shrine.tier.label} • ${distanceText(dist, vm.state.settings.imperialUnits)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when (shrine.tier) {
                    ShrineTier.ITEM -> Column {
                        Text("Weapons, spell-book pages, Binding Crystals, berries and potions.")
                        // Its bond with you: more loot and faster recharges the more you come back.
                        val rec = vm.state.shrines[shrine.id] ?: com.monsterquest.core.ShrineRecord()
                        val bond = rec.bond
                        Text(
                            "Bond: ${bond.label} (${rec.visits} visit${if (rec.visits == 1) "" else "s"})" +
                                (bond.next?.let { " • ${it.label} at ${it.visits}" } ?: ""),
                            color = if (bond != com.monsterquest.core.ShrineBond.NONE) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (bond != com.monsterquest.core.ShrineBond.NONE) {
                            Text(
                                listOfNotNull(
                                    "+${bond.extraRolls} loot",
                                    if (bond.goldMult > 1.0) "x${bond.goldMult} gold" else null,
                                    "recharges in ${bond.cooldownMin} min",
                                    if (bond == com.monsterquest.core.ShrineBond.BONDED) "more monsters gather here" else null,
                                ).joinToString(" • "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ShrineTier.SILVER -> if (guardian != null) {
                        GuardianPreview(guardian)
                        Text("Level ${GameEngine.silverLevel(vm.state.hero.level)} • 1.5× HP • Acts twice per round")
                        Text("At least 3 perfect stats • Harder to tame", color = SilverColor)
                    }
                    ShrineTier.GOLD -> if (guardian != null) {
                        GuardianPreview(guardian)
                        Text("Level ${GameEngine.bossLevel(vm.state.hero.level)} • Double HP • Acts twice per round")
                        Text(
                            if (rec?.bossDefeated == true) "Defeated. You may now try to tame it!"
                            else "Defeat the guardian once to unlock taming.",
                        )
                    }
                }
                if (shrine.isBoss) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        if (!access.open) {
                            GIcon(GameIcon.LOCK, Modifier.size(14.dp).padding(top = 2.dp), HpYellow)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(access.reason, color = if (access.open) HpGreen else HpYellow, fontSize = 13.sp)
                    }
                }
                if (cooldown > 0) Text("Recharging: ${formatDuration(cooldown)}", color = HpYellow)
                if (!inRange) {
                    Text(
                        "Walk within ${formatDistance(World.SHRINE_RANGE_M, vm.state.settings.imperialUnits)} to interact.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    WalkHereButton(vm, shrine.lat, shrine.lon, shrine.place ?: shrine.name)
                    FarwispUse(vm, enabled = cooldown == 0L && (!shrine.isBoss || access.open)) {
                        if (shrine.isBoss) vm.challengeBoss(shrine, remote = true) else vm.visitShrine(shrine, remote = true)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            if (shrine.isBoss) {
                TextButton(onClick = { vm.challengeBoss(shrine); onDismiss() }, enabled = inRange && cooldown == 0L && access.open) {
                    Text(
                        when {
                            shrine.tier == ShrineTier.SILVER -> "Challenge"
                            rec?.bossDefeated == true -> "Tame boss"
                            else -> "Challenge"
                        },
                    )
                }
            } else {
                TextButton(onClick = { vm.visitShrine(shrine); onDismiss() }, enabled = inRange && cooldown == 0L) {
                    Text("Collect")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A den: a strong monster to beat, then 5-10 throws to tame it. */
@Composable
private fun DenDialog(vm: GameViewModel, den: Den, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(den.lat, den.lon)
    val inRange = dist != null && dist <= Dens.RANGE_M
    val imperial = vm.state.settings.imperialUnits
    val landmark = vm.landmarkNear(den.lat, den.lon)
    val street = rememberStreet(den.lat, den.lon)
    val where = landmark?.let { (place, gap) -> landmarkText(place, gap, imperial) } ?: street?.let { "On $it" }
    val m = den.monster
    val seen = m.speciesId in vm.state.seen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.PAW, den.name, den.species.element.color(), where) },
        text = {
            Column {
                MonsterArt(den.species, Modifier.size(110.dp).align(Alignment.CenterHorizontally), shiny = m.shiny, silhouette = !seen)
                Text(
                    (if (seen) m.species.name else "Something big") + " • Lv ${m.level} • ${den.species.typeLabel}",
                    fontWeight = FontWeight.SemiBold,
                    color = rarityColor(den.species.rarity),
                )
                Text(distanceText(dist, imperial), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Stronger than wild monsters: more HP, great stats (${GameEngine.perfectStatCount(m)} perfect). No loot here, just the monster.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "It can't be tamed mid-fight. Beat it, and you get ${Dens.BASE_THROWS} Binding casts to tame it, plus 1 for each of your monsters still standing.",
                    fontSize = 13.sp,
                    color = HpYellow,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!inRange) {
                    Text("Walk within ${formatDistance(Dens.RANGE_M, imperial)} to fight.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(vm, den.lat, den.lon, den.name)
                    FarwispUse(vm) { vm.challengeDen(den, remote = true); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.challengeDen(den); onDismiss() }, enabled = inRange) { Text("Fight!") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun VillainDialog(vm: GameViewModel, camp: VillainCamp, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(camp.lat, camp.lon)
    val inRange = dist != null && dist <= Villains.RANGE_M
    val landmark = vm.landmarkNear(camp.lat, camp.lon)
    val street = rememberStreet(camp.lat, camp.lon)
    val where = landmark?.let { (place, gap) -> landmarkText(place, gap, vm.state.settings.imperialUnits) }?.let { w -> street?.let { "$w on $it" } ?: w }
        ?: street?.let { "Camped on $it" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.SKULL, camp.leader, Color(0xFFF28B9B), where) },
        text = {
            Column {
                Text("${camp.tier.label} of the ${camp.crew} • ${distanceText(dist, vm.state.settings.imperialUnits)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Their monsters carry the shadow curse (Defense -50%, Attack +25%) and can't be tamed. " +
                        "Beat the whole team in a row for gold and loot, then use a Restore Potion to try to free one.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    camp.team.forEach { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MonsterArt(m.species, Modifier.size(52.dp))
                            Text("Lv ${m.level}", fontSize = 11.sp)
                        }
                    }
                }
                if (!inRange) {
                    Text("Walk within ${formatDistance(Villains.RANGE_M, vm.state.settings.imperialUnits)} to fight.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(vm, camp.lat, camp.lon, camp.leader)
                    FarwispUse(vm) { vm.challengeVillain(camp, remote = true); onDismiss() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.challengeVillain(camp); onDismiss() }, enabled = inRange) { Text("Fight!") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private class Entry(
    val dist: Double,
    val art: @Composable () -> Unit,
    val title: String,
    val sub: String,
    val inRange: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun GuardianPreview(sp: com.monsterquest.core.Species) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonsterArt(sp, Modifier.size(72.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Guardian: ${sp.name}", fontWeight = FontWeight.SemiBold)
            Text("${sp.typeLabel} type", fontSize = 12.sp, color = sp.element.color())
        }
    }
}

/** Nearby places sorted by distance, shown beside the map on big screens. */
@Composable
private fun NearbyList(
    vm: GameViewModel,
    shrines: List<Shrine>,
    villains: List<VillainCamp>,
    dens: List<Den>,
    onDen: (Den) -> Unit,
    onShrine: (Shrine) -> Unit,
    onVillain: (VillainCamp) -> Unit,
    dungeons: List<com.monsterquest.core.Dungeon> = emptyList(),
    traders: List<com.monsterquest.core.Trader> = emptyList(),
    nomads: List<com.monsterquest.core.Nomad> = emptyList(),
    facilities: List<com.monsterquest.core.Venue> = emptyList(),
    onOther: (MapPick) -> Unit = {},
) {
    fun far(lat: Double, lon: Double) = vm.distanceTo(lat, lon) ?: Double.MAX_VALUE
    fun away(d: Double) = distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits)
    val entries = shrines.map { sh ->
        val d = vm.distanceTo(sh.lat, sh.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { GIcon(sh.tier.icon(), Modifier.size(28.dp), sh.tier.color()) },
            sh.name,
            (sh.subtitle ?: sh.tier.label) + " • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= World.SHRINE_RANGE_M,
        ) { onShrine(sh) }
    } + dens.map { den ->
        val d = vm.distanceTo(den.lat, den.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { MonsterArt(den.species, Modifier.size(40.dp), silhouette = den.monster.speciesId !in vm.state.seen) },
            den.name,
            "Den • Lv ${den.monster.level} • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= Dens.RANGE_M,
        ) { onDen(den) }
    } + villains.map { v ->
        val d = vm.distanceTo(v.lat, v.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { GIcon(GameIcon.SKULL, Modifier.size(28.dp), Color(0xFFF28B9B)) },
            v.leader,
            "${v.tier.label} • ${v.team.size} cursed monsters • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= Villains.RANGE_M,
        ) { onVillain(v) }
    } + dungeons.map { dg ->
        val d = far(dg.lat, dg.lon)
        Entry(d, { GIcon(GameIcon.FIGHT, Modifier.size(28.dp), Gold) }, dg.name,
            "Dungeon ${starsText(dg.stars)} • Lv ${dg.level}" + (dg.place?.let { " • $it" } ?: "") + " • " + away(d),
            d <= com.monsterquest.core.Dungeons.RANGE_M) { onOther(MapPick.DungeonPick(dg)) }
    } + traders.map { t ->
        val d = far(t.lat, t.lon)
        Entry(d, { GIcon(GameIcon.STORE, Modifier.size(28.dp), Color(0xFF6FCF8A)) }, t.name, "Trader • ${t.offers.size} offers • " + away(d),
            d <= com.monsterquest.core.Wanderers.RANGE_M) { onOther(MapPick.TraderPick(t)) }
    } + nomads.map { n ->
        val d = far(n.lat, n.lon)
        Entry(d, { GIcon(GameIcon.SWORD, Modifier.size(28.dp), Color(0xFF6FA8E8)) }, n.name, "Nomad • Lv ${n.level} • " + away(d),
            d <= com.monsterquest.core.Wanderers.RANGE_M) { onOther(MapPick.NomadPick(n)) }
    } + facilities.map { f ->
        val d = far(f.lat, f.lon)
        Entry(d, {
            when (f.role) {
                com.monsterquest.core.VenueRole.GYM -> GIcon(GameIcon.FIST, Modifier.size(28.dp), HpGreen)
                com.monsterquest.core.VenueRole.SCHOOL -> GIcon(GameIcon.BOOK, Modifier.size(28.dp), Color(0xFF9B7FE8))
                else -> GIcon(GameIcon.HEART, Modifier.size(28.dp), if (f.role == com.monsterquest.core.VenueRole.HOSPITAL) HpRed else HpGreen)
            }
        },
            com.monsterquest.core.Facilities.label(f.role), listOfNotNull(f.name ?: f.kind, away(d)).joinToString(" • "),
            d <= com.monsterquest.core.Facilities.RANGE_M) { onOther(MapPick.FacilityPick(f)) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp)) {
        item { SectionTitle("Nearby") }
        if (entries.none { it.dist <= MAX_NEARBY_M }) {
            item { Text("Nothing found nearby yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(entries.filter { it.dist <= MAX_NEARBY_M }.sortedBy { it.dist }.take(40)) { e ->
            GameCard(Modifier.padding(vertical = 3.dp).clickable(onClick = e.onClick), border = if (e.inRange) Gold else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    e.art()
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.inRange) Text("In range", fontSize = 12.sp, color = Gold)
                }
            }
        }
    }
}

@Composable
private fun TraderDialog(vm: GameViewModel, trader: com.monsterquest.core.Trader, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(trader.lat, trader.lon)
    val inRange = dist != null && dist <= com.monsterquest.core.Wanderers.RANGE_M
    // Fresh stock from the current state (sold offers drop off).
    val now = System.currentTimeMillis()
    val live = vm.tradersNear(now).find { it.id == trader.id } ?: trader.copy(offers = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.STORE, trader.name, Color(0xFF6FCF8A), "Wandering trader • ${distanceText(dist, vm.state.settings.imperialUnits)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Gear from the road and supplies below shop prices. Each offer sells once; the trader moves on tomorrow.", fontSize = 13.sp)
                Text("You have ${vm.state.gold} gold", color = Gold, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                if (live.offers.isEmpty()) Text("Sold out. Come back tomorrow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                live.offers.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            val sub = when {
                                o.item != null -> "${o.discountPct}% off (shop: ${o.item!!.price * o.qty})"
                                o.gear != null -> "Lv ${o.gear!!.level} ${o.gear!!.type.label}"
                                o.armor != null -> "Lv ${o.armor!!.level} armor"
                                else -> ""
                            }
                            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { vm.buyFromTrader(live, o.index) }, enabled = inRange && vm.state.gold >= o.price) { Text("${o.price}g") }
                    }
                }
                if (!inRange) {
                    Text("Walk within ${formatDistance(com.monsterquest.core.Wanderers.RANGE_M, vm.state.settings.imperialUnits)} to trade.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(vm, trader.lat, trader.lon, trader.name)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun NomadDialog(vm: GameViewModel, nomad: com.monsterquest.core.Nomad, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(nomad.lat, nomad.lon)
    val inRange = dist != null && dist <= com.monsterquest.core.Wanderers.RANGE_M
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.SWORD, nomad.name, Color(0xFF6FA8E8), "Nomad adventurer, Lv ${nomad.level} • ${distanceText(dist, vm.state.settings.imperialUnits)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "\"A fair fight? Me and two of mine against you and two of yours, two swaps each.\" " +
                        "${nomad.name} fights too: ${nomad.style}. " +
                        "Win for ${nomad.level * com.monsterquest.core.Wanderers.NOMAD_GOLD_PER_LEVEL} gold, 2 Greater Potions and an egg from one of their monsters' lines " +
                        "(${com.monsterquest.core.Wanderers.NOMAD_EGG_PERFECT} perfect stats). Their monsters can't be tamed.",
                    fontSize = 13.sp,
                )
                Text("Fighting beside ${nomad.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                NomadTeamRow(vm, nomad.active)
                Text("Swaps", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                NomadTeamRow(vm, nomad.reserve)
                if (!inRange) {
                    Text("Walk within ${formatDistance(com.monsterquest.core.Wanderers.RANGE_M, vm.state.settings.imperialUnits)} to accept.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(vm, nomad.lat, nomad.lon, nomad.name)
                    FarwispUse(vm) { vm.challengeNomad(nomad, remote = true); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.challengeNomad(nomad); onDismiss() }, enabled = inRange) { Text("Accept!") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun NomadTeamRow(vm: GameViewModel, team: List<com.monsterquest.core.Monster>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        team.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterArt(m.species, Modifier.size(48.dp))
                Text("Lv ${m.level}", fontSize = 11.sp)
                Text(com.monsterquest.core.Scouting.starText(com.monsterquest.core.Scouting.stars(m.species)), fontSize = 9.sp, color = Gold)
            }
        }
    }
}

/**
 * The glow around a risen Silver or Gold shrine: soft rings that pulse
 * outward, and when it has just risen, a pillar of light and a burst. Drawn
 * over the map at the shrine's spot; it follows the map as you pan and zoom
 * and doesn't block touches.
 */
@Composable
private fun RisenShrineFx(gameMap: GameMap, risen: com.monsterquest.core.RoamingShrine) {
    val gold = risen.shrine.tier == ShrineTier.GOLD
    val color = if (gold) Color(0xFFFFD34D) else Color(0xFFCFE3F5)
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "risen")
    val pulse by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Restart),
        label = "pulse",
    )
    // It rose just now: play the arrival once.
    val rise = remember(risen.shrine.id) { androidx.compose.animation.core.Animatable(if (System.currentTimeMillis() - risen.bornMs < 60_000) 0f else 1f) }
    LaunchedEffect(risen.shrine.id) { rise.animateTo(1f, androidx.compose.animation.core.tween(1800)) }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val read = gameMap.tick
        val c = gameMap.toScreen(risen.shrine.lat, risen.shrine.lon) ?: return@Canvas
        val base = 34.dp.toPx()
        // Pulsing rings.
        for (k in 0..1) {
            val ph = (pulse + k * 0.5f) % 1f
            drawCircle(color.copy(alpha = (1f - ph) * 0.55f), radius = base * (0.6f + ph * 1.4f), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = base * 1.3f),
            radius = base * 1.3f,
            center = c,
        )
        // Arrival: a pillar of light shooting up, and a burst ring.
        val r = rise.value
        if (r < 1f) {
            val beamH = size.height * 0.5f * (1f - (1f - r) * (1f - r))
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.8f * (1f - r))), startY = c.y - beamH, endY = c.y),
                topLeft = Offset(c.x - base * 0.35f, c.y - beamH),
                size = androidx.compose.ui.geometry.Size(base * 0.7f, beamH),
            )
            drawCircle(color.copy(alpha = 1f - r), radius = base * (0.3f + r * 3f), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx()))
        }
    }
}

/** A pharmacy, gym or hospital: what it does, and a button to use it once you're there. */
@Composable
private fun FacilityDialog(vm: GameViewModel, v: com.monsterquest.core.Venue, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(v.lat, v.lon)
    val inRange = dist != null && dist <= com.monsterquest.core.Facilities.RANGE_M
    val now = System.currentTimeMillis()
    val used = com.monsterquest.core.Facilities.usedToday(vm.state, v.role, now)
    val closed = !com.monsterquest.core.Facilities.academyOpen(v, now)
    val (what, action) = when (v.role) {
        com.monsterquest.core.VenueRole.PHARMACY -> "A free full heal for your hero and every monster that's still standing, once a day." to "Get patched up"
        com.monsterquest.core.VenueRole.HOSPITAL -> "Wakes every fainted monster at full health, once a day." to "Wake my monsters"
        com.monsterquest.core.VenueRole.SCHOOL -> "Study magic once a day: you come away with a Scholar's Tome of ${com.monsterquest.core.Facilities.STUDY_PAGES} pages for any spell you choose (on the Hero tab), to learn a new one or rank one up." to "Study magic"
        else -> com.monsterquest.core.Facilities.nextTrainingShare(vm.state, now).let { share ->
            if (share == null) "Your team has trained ${com.monsterquest.core.XpRules.TRAINING_SHARES.size} times today. Come back tomorrow." to "Train"
            else "Spend ${com.monsterquest.core.Facilities.TRAINING_TORCHES} torch to train: every party monster gains ${(share * 100).toInt()}% of a level " +
                "(session ${com.monsterquest.core.Facilities.trainingsToday(vm.state, now) + 1} of ${com.monsterquest.core.XpRules.TRAINING_SHARES.size} today; you have ${vm.state.torches} torches). " +
                "Monsters can't get more than ${com.monsterquest.core.XpRules.MONSTER_LEAD} levels above your hero." to "Train (${com.monsterquest.core.Facilities.TRAINING_TORCHES} torch)"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(com.monsterquest.core.Facilities.label(v.role))
                Text(listOfNotNull(v.name, v.kind).joinToString(" • "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column {
                Text(what)
                if (closed) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(GameIcon.LOCK, Modifier.size(16.dp), HpYellow)
                        Text(" By a school: opens at ${com.monsterquest.core.Facilities.SCHOOL_DAY_END_HOUR} PM on school days (weekends all day).", color = HpYellow)
                    }
                }
                if (used) Text("Already used today. Come back tomorrow.", color = HpYellow, modifier = Modifier.padding(top = 6.dp))
                if (!inRange) {
                    Text("Walk within ${formatDistance(com.monsterquest.core.Facilities.RANGE_M, vm.state.settings.imperialUnits)} to use it (${distanceText(dist, vm.state.settings.imperialUnits)}).", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(vm, v.lat, v.lon, v.name ?: v.kind ?: "Here")
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.useFacility(v); onDismiss() }, enabled = inRange && !used && !closed) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
