package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The hero's build: the art barely changes between the two (a narrower waist, a softer jaw, lashes). */
@Serializable
enum class BodyType(val label: String) { MALE("Male"), FEMALE("Female") }

@Serializable
enum class SkinTone(val label: String) {
    PORCELAIN("Porcelain"), FAIR("Fair"), SAND("Sand"), OLIVE("Olive"),
    BRONZE("Bronze"), UMBER("Umber"), DEEP("Deep"), EBONY("Ebony"),
}

@Serializable
enum class FaceShape(val label: String) { OVAL("Oval"), ROUND("Round"), ANGULAR("Angular"), LONG("Long"), HEART("Heart") }

@Serializable
enum class EyeStyle(val label: String) {
    GENTLE("Gentle"), STERN("Stern"), KEEN("Keen"), DOT("Dot"), DASH("Dash"), BRIGHT("Bright"),
}

@Serializable
enum class HairStyle(val label: String) {
    WINDSWEPT("Windswept"), WAYFARER("Wayfarer crop"), SHAGGY("Shaggy"), SWEEP("Side sweep"), LONG("Long"),
    PONYTAIL("Ponytail"), BRAID("Braid"), TOPKNOT("Topknot"), LOCS("Locs"), SHORN("Shorn"),
}

@Serializable
enum class HairColor(val label: String) {
    RAVEN("Raven"), CHESTNUT("Chestnut"), OAK("Oak"), EMBER("Ember"),
    WHEAT("Wheat"), ASH("Ash"), FROST("Frost"), DUSK("Dusk"),
}

@Serializable
enum class EarShape(val label: String) { ROUNDED("Rounded"), POINTED("Pointed") }

/** The colour of the hero's own traveller's clothes (cloak, hood, tunic); worn gear still replaces them. */
@Serializable
enum class CloakColor(val label: String) {
    FOREST("Forest"), CRIMSON("Crimson"), MIDNIGHT("Midnight"), ASH("Ash"), OCHRE("Ochre"), PLUM("Plum"),
}

/** How the hero looks, chosen on the setup screen and changed for free at the Mirror. */
@Serializable
data class Appearance(
    val body: BodyType = BodyType.MALE,
    val skin: SkinTone = SkinTone.SAND,
    val face: FaceShape = FaceShape.OVAL,
    val eyes: EyeStyle = EyeStyle.GENTLE,
    val hair: HairStyle = HairStyle.WINDSWEPT,
    val hairColor: HairColor = HairColor.CHESTNUT,
    val ears: EarShape = EarShape.POINTED,
    val cloak: CloakColor = CloakColor.FOREST,
    /** Keep the hood up everywhere, not only in battles and dungeons. */
    val hoodAlwaysUp: Boolean = false,
) {
    companion object {
        const val MAX_NAME = 16

        /** A random look, for the dice button. */
        fun random(rng: Random = Random.Default): Appearance = Appearance(
            body = BodyType.entries.random(rng),
            skin = SkinTone.entries.random(rng),
            face = FaceShape.entries.random(rng),
            eyes = EyeStyle.entries.random(rng),
            hair = HairStyle.entries.random(rng),
            hairColor = HairColor.entries.random(rng),
            ears = EarShape.entries.random(rng),
            cloak = CloakColor.entries.random(rng),
        )

        /** A random traveller's name for [body]. */
        fun randomName(body: BodyType, rng: Random = Random.Default): String =
            (if (body == BodyType.FEMALE) FEMALE_NAMES else MALE_NAMES).random(rng)

        private val MALE_NAMES = listOf("Aren", "Bram", "Corin", "Dace", "Edric", "Fenn", "Garrow", "Hale", "Ivo", "Joren", "Kestrel", "Lorne", "Marek", "Orrin", "Rowan", "Tobin")
        private val FEMALE_NAMES = listOf("Wren", "Anwen", "Briar", "Calla", "Daria", "Elowen", "Fern", "Ilse", "Kaia", "Linnea", "Maren", "Nessa", "Oriel", "Saoirse", "Tamsyn", "Yara")
    }
}

object Looks {
    /** The setup screen is due: a new player before the tutorial, or anyone from before customization existed. */
    fun due(state: GameState): Boolean = state.appearance == null

    /** Saves [look] and, when given and not blank, a new hero [name] (trimmed to [Appearance.MAX_NAME]). */
    fun save(state: GameState, look: Appearance, name: String? = null): GameState {
        val n = name?.trim()?.take(Appearance.MAX_NAME)?.takeIf { it.isNotEmpty() }
        return state.copy(appearance = look, hero = if (n != null) state.hero.copy(name = n) else state.hero)
    }
}
