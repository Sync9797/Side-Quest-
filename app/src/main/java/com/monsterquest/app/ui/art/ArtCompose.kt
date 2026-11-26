package com.monsterquest.app.ui.art

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.monsterquest.core.BoxRarity
import com.monsterquest.core.Element
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Item
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.Species
import com.monsterquest.core.Status
import com.monsterquest.core.Weather

/**
 * Rendered art, reused across frames and screens. Vector art (paths, glows,
 * engraving) is costly to draw, so each picture is drawn once per size into a
 * bitmap; scrolling and animations then just blit it, keeping 120 fps smooth.
 */
private object ArtCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(key: String, w: Int, h: Int, draw: (android.graphics.Canvas, Float, Float) -> Unit): Bitmap {
        val k = "$key@${w}x$h:${ArtQuality.reduced}"
        cache.get(k)?.let { return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(android.graphics.Canvas(bmp), w.toFloat(), h.toFloat())
        cache.put(k, bmp)
        return bmp
    }
}

/** Draws cached art identified by [key]; the same key must always draw the same picture. */
@Composable
private fun ArtCanvas(modifier: Modifier, key: String, whiteness: Float = 0f, draw: (android.graphics.Canvas, Float, Float) -> Unit) {
    // Turned white (an evolution's silhouette): the tint covers only the picture's own pixels.
    val filter = if (whiteness > 0f) androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = whiteness.coerceIn(0f, 1f)), androidx.compose.ui.graphics.BlendMode.SrcAtop) else null
    // Clip so glows and halos never paint over neighboring UI.
    Spacer(
        modifier.clipToBounds().drawWithCache {
            val w = size.width.toInt()
            val h = size.height.toInt()
            val image = if (w > 0 && h > 0) ArtCache.get(key, w, h, draw).asImageBitmap() else null
            onDrawBehind { image?.let { drawImage(it, colorFilter = filter) } }
        },
    )
}

/** The hero wearing [outfit] (by default, the class's typical look). [portrait] adds the halo and sparkles. */
@Composable
fun HeroArt(cls: HeroClass, modifier: Modifier, portrait: Boolean = true, outfit: HeroOutfit = HeroOutfit.classic(cls)) {
    ArtCanvas(modifier, "hero:$cls:$portrait:$outfit") { c, w, h -> drawHero(c, w, h, cls, portrait, outfit) }
}

/** The hero as they stand on the map, with their stars. */
@Composable
fun PlayerMarkerArt(cls: HeroClass, outfit: HeroOutfit, stars: Int, modifier: Modifier) {
    ArtCanvas(modifier, "marker:$cls:$outfit:$stars") { c, w, h -> drawPlayerOn(c, w, h, cls, outfit, stars) }
}

/** Just the hero's head and shoulders, for the look editor's thumbnails. */
@Composable
fun HeroHeadArt(cls: HeroClass, outfit: HeroOutfit, modifier: Modifier) {
    ArtCanvas(modifier, "herohead:$cls:$outfit") { c, w, h ->
        // Show the 86-point square around the head (from 108, 40) of the hero's 300x280 art.
        val k = minOf(w, h) / 86f
        c.save()
        c.translate(-108f * k + (w - 86f * k) / 2, -40f * k + (h - 86f * k) / 2)
        drawHero(c, 300f * k, 280f * k, cls, false, outfit)
        c.restore()
    }
}

/** An evil thug; the look depends on their class (Brute, Hexer or Cutthroat). */
@Composable
fun ThugArt(cls: HeroClass, modifier: Modifier) {
    ArtCanvas(modifier, "thug:$cls") { c, w, h -> drawThug(c, w, h, cls) }
}

/**
 * A monster's artwork. [dim] grays it out (fainted); [silhouette] shows only
 * its shadow (not yet seen). [idle] gives it a little life (see [idleMotion]):
 * on a monster's own page and out on the map, not in lists like the Journal.
 */
@Composable
fun MonsterArt(
    species: Species, modifier: Modifier, dim: Boolean = false, silhouette: Boolean = false, shiny: Boolean = false, idle: Boolean = false,
    /** 0 to 1: how white it glows (an evolution's flickering silhouette). */
    whiteness: Float = 0f,
) {
    ArtCanvas(modifier.idleMotion(species, idle && !dim && !silhouette), "monster:${species.id}:$dim:$silhouette:$shiny", whiteness) { c, w, h ->
        if (silhouette) {
            // Draw normally into a layer, then flood everything drawn with one dark color.
            val layer = c.saveLayer(0f, 0f, w, h, null)
            drawMonster(c, w, h, species, dim = true)
            c.drawRect(0f, 0f, w, h, silhouettePaint)
            c.restoreToCount(layer)
        } else {
            drawMonster(c, w, h, species, dim, shiny)
        }
    }
}

/** How a monster moves at rest: each kind its own way. */
private enum class Idle { BREATHE, FLOAT, SQUISH, FLICKER }

private fun idleOf(sp: Species): Idle = when {
    sp.element == Element.FLIGHT || sp.secondary == Element.FLIGHT || sp.element == Element.ELECTRIC -> Idle.FLOAT
    sp.element == Element.WATER || sp.element == Element.GAS -> Idle.SQUISH
    sp.element == Element.FIRE || sp.element == Element.MYTHICAL -> Idle.FLICKER
    else -> Idle.BREATHE
}

/**
 * Subtle life for a monster at rest: earth and shadow creatures breathe,
 * fliers and sparks float, water and gas wobble like jelly, fire flickers.
 * Each species is out of step with the others, and only the drawing layer
 * moves (the art itself is a cached picture), so it costs next to nothing.
 */
private fun Modifier.idleMotion(sp: Species, on: Boolean): Modifier = if (!on || ArtQuality.reduced || ArtQuality.stillForTests) this else composed {
    val clock = remember { mutableLongStateOf(0L) }
    // An endless animation: tests (and the system's "remove animations" setting) can pause it.
    LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { clock.longValue = it - start }
    }
    val seed = (sp.id.hashCode() and 0xFFFF) / 65535f * 2f * PI.toFloat()
    val style = idleOf(sp)
    graphicsLayer {
        val t = clock.longValue / 1000f
        fun wave(period: Float) = sin(t * 2f * PI.toFloat() / period + seed)
        transformOrigin = TransformOrigin(0.5f, 1f)
        when (style) {
            Idle.BREATHE -> { val b = wave(3.2f); scaleY = 1f + 0.022f * b; scaleX = 1f - 0.008f * b }
            Idle.FLOAT -> { translationY = wave(2.6f) * size.height * 0.025f; rotationZ = wave(5.2f) * 1.2f }
            Idle.SQUISH -> { val b = wave(2.2f); scaleY = 1f - 0.035f * b; scaleX = 1f + 0.03f * b }
            Idle.FLICKER -> { val b = wave(2.8f) * 0.018f + sin(t * 11f + seed) * 0.006f; scaleY = 1f + b; scaleX = 1f - b * 0.4f }
        }
    }
}

private val silhouettePaint = android.graphics.Paint().apply {
    color = 0xFF2A3A3E.toInt()
    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
}

/** A small game icon. */
@Composable
fun GIcon(icon: GameIcon, modifier: Modifier = Modifier.size(18.dp), tint: Color = LocalContentColor.current) {
    val cut = MaterialTheme.colorScheme.background.toArgb()
    val t = tint.toArgb()
    ArtCanvas(modifier, "icon:$icon:$t:$cut") { c, w, h -> drawIcon(c, w, h, icon, t, cut) }
}

/** Icon followed by text, e.g. a coin and "1234". */
@Composable
fun IconText(
    icon: GameIcon,
    text: String,
    tint: Color = LocalContentColor.current,
    textColor: Color = Color.Unspecified,
    size: Dp = 16.dp,
    fontSize: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    style: TextStyle = TextStyle.Default,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GIcon(icon, Modifier.size(size), tint)
        Text(
            text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else null,
            style = style,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// What icon and color each game concept uses.
// ---------------------------------------------------------------------------

fun Element.icon(): GameIcon = when (this) {
    Element.FIRE -> GameIcon.FLAME
    Element.ELECTRIC -> GameIcon.BOLT
    Element.FLIGHT -> GameIcon.WING
    Element.WATER -> GameIcon.DROP
    Element.GAS -> GameIcon.GAS
    Element.CORRUPT -> GameIcon.MOON
    Element.EARTH -> GameIcon.LEAF
    Element.NEUTRAL -> GameIcon.RING
    Element.MYTHICAL -> GameIcon.STAR
}

fun Element.color(): Color = Color(palette(this).light)

fun Item.icon(): GameIcon = when (this) {
    Item.POTION -> GameIcon.POTION
    Item.GREATER_POTION -> GameIcon.GREATER_POTION
    Item.MANA_DRAUGHT -> GameIcon.POTION
    Item.PHOENIX_FEATHER -> GameIcon.FEATHER
    Item.RESTORE_POTION -> GameIcon.RESTORE
    // Binding Crystals.
    Item.BINDING_CRYSTAL, Item.RESONANT_CRYSTAL, Item.RADIANT_CRYSTAL, Item.PRISMATIC_CRYSTAL, Item.SOVEREIGN_CRYSTAL, Item.PERFECT_ORB -> GameIcon.CRYSTAL
    Item.TREAT -> GameIcon.TREAT
    Item.MOOD_CRYSTAL -> GameIcon.SPARK
    Item.LURE -> GameIcon.LURE
    Item.INCUBATOR -> GameIcon.HEART
    Item.SWEET_BERRY, Item.GOLDEN_BERRY -> GameIcon.BERRY
    Item.SEED_MIX, Item.SEED_MIX_FINE, Item.SEED_MIX_GOURMET, Item.RICH_COMPOST, Item.RICH_COMPOST_FINE, Item.RICH_COMPOST_GOURMET -> GameIcon.LEAF
    Item.ORCHARD_FRUIT, Item.ORCHARD_FRUIT_FINE, Item.ORCHARD_FRUIT_GOURMET -> GameIcon.BERRY
    Item.WRIGGLE_GRUBS, Item.WRIGGLE_GRUBS_FINE, Item.WRIGGLE_GRUBS_GOURMET -> GameIcon.TREAT
    Item.FRESH_FISH, Item.FRESH_FISH_FINE, Item.FRESH_FISH_GOURMET -> GameIcon.DROP
    Item.RAW_MEAT, Item.RAW_MEAT_FINE, Item.RAW_MEAT_GOURMET -> GameIcon.PAW
    Item.MINERAL_SALTS, Item.MINERAL_SALTS_FINE, Item.MINERAL_SALTS_GOURMET -> GameIcon.CRYSTAL
    Item.SPIRIT_INCENSE, Item.SPIRIT_INCENSE_FINE, Item.SPIRIT_INCENSE_GOURMET -> GameIcon.MOON
    Item.DRAGON_FEAST, Item.DRAGON_FEAST_FINE, Item.DRAGON_FEAST_GOURMET -> GameIcon.FLAME
    Item.FARWISP -> GameIcon.ORB
}

fun Item.color(): Color = when (this) {
    Item.POTION, Item.GREATER_POTION -> Color(0xFF9CD2BD)
    Item.MANA_DRAUGHT -> Color(0xFF9CBFE4)
    Item.PHOENIX_FEATHER -> Color(0xFFF2A198)
    Item.RESTORE_POTION -> Color(0xFFF28B9B)
    Item.BINDING_CRYSTAL -> Color(0xFF9CBFE4)
    Item.RESONANT_CRYSTAL -> Color(0xFFC3A6F0)
    Item.RADIANT_CRYSTAL -> Color(0xFF7FD6C2)
    Item.PRISMATIC_CRYSTAL -> Color(0xFFF2A65A)
    Item.SOVEREIGN_CRYSTAL -> Color(0xFFEBC678)
    Item.PERFECT_ORB -> Color(0xFFFF7BD5)
    Item.TREAT -> Color(0xFFD9A07A)
    Item.MOOD_CRYSTAL -> Color(0xFFB59AF0)
    Item.LURE -> Color(0xFFF2A198)
    Item.INCUBATOR -> Color(0xFFF2C27A)
    Item.SWEET_BERRY -> Color(0xFFE5739A)
    Item.GOLDEN_BERRY -> Color(0xFFEBC678)
    Item.SEED_MIX, Item.SEED_MIX_FINE, Item.SEED_MIX_GOURMET -> Color(0xFFD9C27A)
    Item.ORCHARD_FRUIT, Item.ORCHARD_FRUIT_FINE, Item.ORCHARD_FRUIT_GOURMET -> Color(0xFFF2A65A)
    Item.WRIGGLE_GRUBS, Item.WRIGGLE_GRUBS_FINE, Item.WRIGGLE_GRUBS_GOURMET -> Color(0xFFC9A27E)
    Item.FRESH_FISH, Item.FRESH_FISH_FINE, Item.FRESH_FISH_GOURMET -> Color(0xFF7FB8E6)
    Item.RAW_MEAT, Item.RAW_MEAT_FINE, Item.RAW_MEAT_GOURMET -> Color(0xFFE07A6E)
    Item.RICH_COMPOST, Item.RICH_COMPOST_FINE, Item.RICH_COMPOST_GOURMET -> Color(0xFF8FB86A)
    Item.MINERAL_SALTS, Item.MINERAL_SALTS_FINE, Item.MINERAL_SALTS_GOURMET -> Color(0xFFBFC7D1)
    Item.SPIRIT_INCENSE, Item.SPIRIT_INCENSE_FINE, Item.SPIRIT_INCENSE_GOURMET -> Color(0xFFB59AF0)
    Item.DRAGON_FEAST, Item.DRAGON_FEAST_FINE, Item.DRAGON_FEAST_GOURMET -> Color(0xFFF26B4A)
    Item.FARWISP -> Color(0xFF6FC7B8)
}

fun com.monsterquest.core.WeaponType.icon(): GameIcon = when (this) {
    com.monsterquest.core.WeaponType.SWORD -> GameIcon.SWORD
    com.monsterquest.core.WeaponType.BROADSWORD -> GameIcon.BROADSWORD
    com.monsterquest.core.WeaponType.DAGGER -> GameIcon.DAGGER
    com.monsterquest.core.WeaponType.STAFF -> GameIcon.STAFF
    com.monsterquest.core.WeaponType.WAND -> GameIcon.WAND
    com.monsterquest.core.WeaponType.AXE -> GameIcon.AXE
    com.monsterquest.core.WeaponType.BOW -> GameIcon.BOW
    com.monsterquest.core.WeaponType.SCYTHE -> GameIcon.SCYTHE
}

fun com.monsterquest.core.GearRarity.color(): Color = when (this) {
    com.monsterquest.core.GearRarity.NORMAL -> Color(0xFFD7DDD9)
    com.monsterquest.core.GearRarity.RARE -> Color(0xFF9CBFE4)
    com.monsterquest.core.GearRarity.EPIC -> Color(0xFFC3A6F0)
    com.monsterquest.core.GearRarity.LEGENDARY -> Color(0xFFEBC678)
}

fun com.monsterquest.core.ArmorType.icon(): GameIcon = when (this) {
    com.monsterquest.core.ArmorType.PLATE -> GameIcon.PLATE
    com.monsterquest.core.ArmorType.LEATHER -> GameIcon.LEATHER
    com.monsterquest.core.ArmorType.ROBE -> GameIcon.ROBE
    com.monsterquest.core.ArmorType.HOOD -> GameIcon.HOOD
    com.monsterquest.core.ArmorType.CLOAK -> GameIcon.CLOAK
    com.monsterquest.core.ArmorType.PANTS -> GameIcon.PANTS
    com.monsterquest.core.ArmorType.BOOTS -> GameIcon.BOOTS
    com.monsterquest.core.ArmorType.HELM -> GameIcon.HELM
    com.monsterquest.core.ArmorType.MANTLE -> GameIcon.CLOAK
    com.monsterquest.core.ArmorType.LEGGINGS -> GameIcon.PANTS
    com.monsterquest.core.ArmorType.SANDALS -> GameIcon.SANDALS
    com.monsterquest.core.ArmorType.FURCLOAK -> GameIcon.FURCLOAK
}

fun BoxRarity.color(): Color = when (this) {
    BoxRarity.COMMON -> Color(0xFFB8C1BE)
    BoxRarity.RARE -> Color(0xFF9CBFE4)
    BoxRarity.EPIC -> Color(0xFFC3A6F0)
    BoxRarity.LEGENDARY -> Color(0xFFEBC678)
}

fun ShrineTier.icon(): GameIcon = when (this) {
    ShrineTier.ITEM -> GameIcon.ITEM_SHRINE
    ShrineTier.SILVER -> GameIcon.SILVER_SHRINE
    ShrineTier.GOLD -> GameIcon.GOLD_SHRINE
}

fun ShrineTier.color(): Color = when (this) {
    ShrineTier.ITEM -> Color(0xFF9CD2BD)
    ShrineTier.SILVER -> Color(0xFFC0C7D1)
    ShrineTier.GOLD -> Color(0xFFEBC678)
}

fun Status.icon(): GameIcon = when (this) {
    Status.SLEEP -> GameIcon.SLEEP
    Status.SHOCK -> GameIcon.BOLT
    Status.POISON -> GameIcon.SKULL
    Status.BURN -> GameIcon.FLAME
    Status.FROSTBITE -> GameIcon.CRYSTAL
}

fun Weather.icon(): GameIcon? = when (this) {
    Weather.RAIN -> GameIcon.RAIN
    Weather.DROUGHT -> GameIcon.SUN
    Weather.CLEAR -> null
}

fun com.monsterquest.core.HeroAttribute.icon(): GameIcon = when (this) {
    com.monsterquest.core.HeroAttribute.ATTACK -> GameIcon.SWORD
    com.monsterquest.core.HeroAttribute.ENERGY -> GameIcon.BOLT
    com.monsterquest.core.HeroAttribute.MAGIC -> GameIcon.SPARK
    com.monsterquest.core.HeroAttribute.DEFENSE -> GameIcon.SHIELD
    com.monsterquest.core.HeroAttribute.SPEED -> GameIcon.WING
}

fun HeroClass.icon(): GameIcon = when (this) {
    HeroClass.NONE -> GameIcon.HERO
    HeroClass.WARRIOR -> GameIcon.SWORD
    HeroClass.MAGE -> GameIcon.STAFF
    HeroClass.ROGUE -> GameIcon.DAGGER
}
