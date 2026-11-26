package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Species(
    val id: String,
    val name: String,
    val element: Element,
    val rarity: Rarity,
    val base: StatBlock,
    val special: Skill,
    /** Boss species only appear at boss shrines. */
    val bossOnly: Boolean = false,
    /** Evolution line this species belongs to (e.g. "Emberkit line"). */
    val family: String,
    /** Breed name for species that share an archetype across types, e.g. "Ember" hound. */
    val breed: String? = null,
    /** 1-based position in the evolution line, and the line's length. */
    val stage: Int = 1,
    val stages: Int = 1,
    val evolvesTo: String? = null,
    val evolveLevel: Int? = null,
    /** Lowest level this species can be found at in the wild. */
    val minWildLevel: Int = 1,
    /** A second type (mostly final evolutions): both count for matchups, same-type bonus and moves. */
    val secondary: Element? = null,
) {
    /** One or two types, main type first. */
    val types: List<Element> get() = listOfNotNull(element, secondary)

    /** "Electric" or "Electric / Flight". */
    val typeLabel: String get() = types.joinToString(" / ") { it.label }

    /** Types its attacks are strong against (either of its types). */
    val strongAgainst: List<Element> get() = types.flatMap { it.beats }.distinct()

    /** Attack types that hit it harder than normal, counting both its types. */
    val weakTo: List<Element> get() = Element.typed.filter { it.multiplierAgainst(types) > 1.0 }

    /** One of the rare one-stage mythics. */
    val mythic: Boolean get() = id in Bestiary.MYTHIC_IDS

    /** Legendaries and mythics have no gender, and can't make eggs. */
    val genderless: Boolean get() = rarity == Rarity.LEGENDARY || mythic
}

/**
 * All 119 monsters, numbered in this order (mythics and legendaries last):
 * - 4 starter lines (#1, #4, #7, #10): pseudo-legendary three-stage lines, the
 *   strongest monsters short of the legendaries. Very rare in the wild.
 * - 6 four-stage lines (3 evolutions), one per type.
 * - 1 three-stage hound line (Shadow Hound).
 * - 4 more three-stage lines: slime, rock golem, shell turtle and imp.
 * - 1.33: five more per type: a three-stage and a two-stage line each.
 * - 2 rare one-stage mythics.
 * - 6 legendary bosses, one per type, found only at boss shrines.
 */
object Bestiary {
    // Each evolution is a whole new, much stronger creature.
    private val fourStageMultipliers = listOf(1.0, 1.4, 1.85, 2.35)
    private val threeStageMultipliers = listOf(1.0, 1.5, 2.0)
    private val fourStageRarity = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC)
    private val threeStageRarity = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE)
    private val fourStageLevels = listOf(14, 28, 42)
    private val threeStageLevels = listOf(16, 32)
    // Two-stage lines: one big evolution.
    private val twoStageMultipliers = listOf(1.0, 1.75)
    private val twoStageRarity = listOf(Rarity.COMMON, Rarity.RARE)
    private val twoStageLevels = listOf(24)

    private data class Member(val id: String, val name: String, val special: String)

    private fun scale(b: StatBlock, m: Double) = StatBlock(
        (b.hp * m).toInt(), (b.attack * m).toInt(), (b.magic * m).toInt(), (b.defense * m).toInt(), (b.speed * m).toInt(),
    )

    // Starters: three stages, strong growth, always rare.
    private val starterMultipliers = listOf(1.0, 1.5, 2.05)
    private val starterRarity = listOf(Rarity.EPIC, Rarity.EPIC, Rarity.EPIC)
    private val starterLevels = listOf(16, 36)

    private fun line(
        family: String,
        element: Element,
        base: StatBlock,
        kind: SkillKind,
        members: List<Member>,
        breed: String? = null,
        starter: Boolean = false,
    ): List<Species> {
        val four = members.size == 4
        val two = members.size == 2
        val mults = if (starter) starterMultipliers else if (four) fourStageMultipliers else if (two) twoStageMultipliers else threeStageMultipliers
        val rarities = if (starter) starterRarity else if (four) fourStageRarity else if (two) twoStageRarity else threeStageRarity
        val levels = if (starter) starterLevels else if (four) fourStageLevels else if (two) twoStageLevels else threeStageLevels
        return members.mapIndexed { i, m ->
            Species(
                id = m.id,
                name = m.name,
                element = element,
                rarity = rarities[i],
                base = scale(base, mults[i]),
                special = Skill("${m.id}_special", m.special, element, kind, 30 + i * 9),
                family = family,
                breed = breed,
                stage = i + 1,
                stages = members.size,
                evolvesTo = members.getOrNull(i + 1)?.id,
                evolveLevel = levels.getOrNull(i),
                minWildLevel = if (i == 0) 1 else levels[i - 1],
            )
        }
    }

    private fun hounds(breed: String, element: Element, names: List<String>, specials: List<String>): List<Species> {
        val prefix = breed.lowercase()
        val ids = listOf("${prefix}_pup", "${names[1].lowercase().replace(' ', '_')}", "${names[2].lowercase().replace(' ', '_')}")
        return line(
            family = "$breed Hound line",
            element = element,
            base = StatBlock(40, 15, 8, 11, 14),
            kind = SkillKind.PHYSICAL,
            members = listOf(
                Member(ids[0], names[0], specials[0]),
                Member(ids[1], names[1], specials[1]),
                Member(ids[2], names[2], specials[2]),
            ),
            breed = breed,
        )
    }

    /**
     * Legendaries are the strongest monsters you can own: their HP, Attack,
     * Magic and Defense are boosted by this much. Boss fights scale them back
     * down (see [Battle]), so Gold shrines stay as hard as they were.
     */
    const val LEGENDARY_POWER = 1.6

    /**
     * Mythics are rare one-stage monsters as strong as a final evolution
     * (but below the starters and legendaries): their stats are boosted by this.
     */
    const val MYTHIC_POWER = 1.45

    val MYTHIC_IDS = setOf("mimic", "prism_fairy")

    private fun mythic(b: StatBlock) = StatBlock(
        (b.hp * MYTHIC_POWER).toInt(), (b.attack * MYTHIC_POWER).toInt(), (b.magic * MYTHIC_POWER).toInt(),
        (b.defense * MYTHIC_POWER).toInt(), (b.speed * MYTHIC_POWER).toInt(),
    )

    private fun single(
        id: String, name: String, element: Element, rarity: Rarity,
        base: StatBlock, special: String, kind: SkillKind, power: Int, bossOnly: Boolean, minWildLevel: Int = 1,
    ): Species {
        val b = if (rarity == Rarity.LEGENDARY) {
            StatBlock(
                (base.hp * LEGENDARY_POWER).toInt(), (base.attack * LEGENDARY_POWER).toInt(), (base.magic * LEGENDARY_POWER).toInt(),
                (base.defense * LEGENDARY_POWER).toInt(), base.speed,
            )
        } else {
            base
        }
        return Species(
            id, name, element, rarity, b, Skill("${id}_special", special, element, kind, power),
            bossOnly = bossOnly, family = name, minWildLevel = minWildLevel,
        )
    }

    /**
     * Names for evolved forms that became entirely new creatures. Species ids
     * never change (saves refer to them), only the names shown.
     */
    private val evolvedNames = mapOf(
        "blazecat" to "Cinderfang", "flarepanther" to "Pyremane", "solar_tiger" to "Sun Manticore",
        "storm_kirin" to "Storm Behemoth", "skylark" to "Razorwing", "tidefin" to "Fangfin",
        "basilisk" to "Plague Mantis", "gloombat" to "Gloomshade", "nightstalker" to "Nightwraith",
        "slime_sovereign" to "Ooze Hydra",
        "shadow_pup" to "Shadow Wolfcub", "dusk_hound" to "Dusk Warg",
    )

    /**
     * Dual types: the starters' final forms and some of the strongest final
     * evolutions gain a second type that suits them.
     */
    private val dualTypes: Map<String, Element> = mapOf(
        // Starters
        "thunderhorn" to Element.EARTH,           // a storm stag that shakes the ground
        "infernodrake" to Element.FLIGHT,         // a winged fire dragon
        "maelstrom_serpent" to Element.FLIGHT,    // a sea dragon that rides the storm
        "terraursa" to Element.FIRE,              // a volcanic elder bear
        // Strong final evolutions
        "magma_colossus" to Element.EARTH,        // molten rock
        "bastion_tortoise" to Element.EARTH,      // a fortress of a shell
        "archfiend" to Element.FIRE,              // a demon
        "leviathan" to Element.CORRUPT,           // a monster of the deep
        "basilisk" to Element.FLIGHT,             // a winged mantis (Plague Mantis)
        "torrentoad" to Element.GAS,              // a poisonous giant toad
        "elder_strix" to Element.CORRUPT,         // an owl of the night
        "widowmaw" to Element.GAS,                // a venomous widow
    )

    val all: List<Species> = buildList<Species> {
        // ---- Starters (#1, #4, #7): pseudo-legendary three-stage lines ----
        addAll(line("Voltfawn line", Element.ELECTRIC, StatBlock(44, 15, 17, 13, 18), SkillKind.MAGICAL, listOf(
            Member("voltfawn", "Voltfawn", "Volt Kick"),
            Member("stormstag", "Stormstag", "Storm Charge"),
            Member("thunderhorn", "Thunderhorn", "Thunder Crown"),
        ), starter = true))
        addAll(line("Cindrake line", Element.FIRE, StatBlock(46, 18, 14, 14, 15), SkillKind.PHYSICAL, listOf(
            Member("cindrake", "Cindrake", "Cinder Breath"),
            Member("blazewyrm", "Blazewyrm", "Blaze Wing"),
            Member("infernodrake", "Infernodrake", "Infernal Roar"),
        ), starter = true))
        addAll(line("Drizzlet line", Element.WATER, StatBlock(50, 14, 16, 16, 13), SkillKind.MAGICAL, listOf(
            Member("drizzlet", "Drizzlet", "Drizzle Jet"),
            Member("tidewyrm", "Tidewyrm", "Tide Coil"),
            Member("maelstrom_serpent", "Maelstrom Serpent", "Maelstrom Fang"),
        ), starter = true))
        addAll(line("Mosscub line", Element.EARTH, StatBlock(52, 17, 12, 17, 11), SkillKind.PHYSICAL, listOf(
            Member("mosscub", "Mosscub", "Moss Swipe"),
            Member("grovebear", "Grovebear", "Grove Maul"),
            Member("terraursa", "Terraursa", "Tectonic Roar"),
        ), starter = true))

        // ---- Four-stage lines (3 evolutions) ----
        addAll(line("Emberkit line", Element.FIRE, StatBlock(40, 15, 10, 9, 15), SkillKind.PHYSICAL, listOf(
            Member("emberkit", "Emberkit", "Ember Swipe"),
            Member("blazecat", "Blazecat", "Blaze Pounce"),
            Member("flarepanther", "Flarepanther", "Flare Rush"),
            Member("solar_tiger", "Solar Tiger", "Solar Fang"),
        )))
        addAll(line("Zapmouse line", Element.ELECTRIC, StatBlock(36, 9, 16, 9, 17), SkillKind.MAGICAL, listOf(
            Member("zapmouse", "Zapmouse", "Static Nip"),
            Member("voltrat", "Voltrat", "Spark Bolt"),
            Member("thunderhog", "Thunderhog", "Thunder Quills"),
            Member("storm_kirin", "Storm Kirin", "Heaven's Bolt"),
        )))
        addAll(line("Fluffchick line", Element.FLIGHT, StatBlock(36, 13, 11, 9, 18), SkillKind.PHYSICAL, listOf(
            Member("fluffchick", "Fluffchick", "Peck Flurry"),
            Member("skylark", "Skylark", "Wing Slice"),
            Member("galehawk", "Galehawk", "Gale Dive"),
            Member("tempest_roc", "Tempest Roc", "Tempest Wings"),
        )))
        addAll(line("Bubblefin line", Element.WATER, StatBlock(44, 12, 13, 12, 10), SkillKind.MAGICAL, listOf(
            Member("bubblefin", "Bubblefin", "Bubble Jet"),
            Member("tidefin", "Tidefin", "Tide Spout"),
            Member("reefjaw", "Reefjaw", "Riptide"),
            Member("leviathan", "Leviathan", "Abyssal Wave"),
        )))
        addAll(line("Toxbug line", Element.GAS, StatBlock(42, 12, 13, 13, 10), SkillKind.MAGICAL, listOf(
            Member("toxbug", "Toxbug", "Toxic Spit"),
            Member("mirepede", "Mirepede", "Acid Bite"),
            Member("stingscor", "Stingscor", "Venom Sting"),
            Member("basilisk", "Basilisk", "Scythe Rend"),
        )))
        addAll(line("Gloomwisp line", Element.CORRUPT, StatBlock(38, 11, 16, 10, 15), SkillKind.MAGICAL, listOf(
            Member("gloomwisp", "Gloomwisp", "Hexing Gaze"),
            Member("gloombat", "Gloombat", "Night Shriek"),
            Member("nightstalker", "Nightstalker", "Shadow Grasp"),
            Member("abyss_lord", "Abyss Lord", "Oblivion"),
        )))

        // ---- Hound breeds: three-stage lines (2 evolutions) ----
        addAll(hounds("Shadow", Element.CORRUPT, listOf("Shadow Pup", "Dusk Hound", "Void Fenrir"), listOf("Shadow Bite", "Dusk Fang", "Void Howl")))

        // ---- More three-stage lines ----
        addAll(line("Goopling line", Element.GAS, StatBlock(46, 10, 13, 13, 8), SkillKind.MAGICAL, listOf(
            Member("goopling", "Goopling", "Goo Toss"),
            Member("sludgeon", "Sludgeon", "Sludge Tide"),
            Member("slime_sovereign", "Slime Sovereign", "Royal Ooze"),
        )))
        addAll(line("Pebblit line", Element.FIRE, StatBlock(50, 16, 6, 17, 5), SkillKind.PHYSICAL, listOf(
            Member("pebblit", "Pebblit", "Rock Toss"),
            Member("boulderon", "Boulderon", "Boulder Slam"),
            Member("magma_colossus", "Magma Colossus", "Molten Quake"),
        )))
        addAll(line("Shellsnap line", Element.WATER, StatBlock(48, 13, 10, 17, 6), SkillKind.PHYSICAL, listOf(
            Member("shellsnap", "Shellsnap", "Shell Ram"),
            Member("coralback", "Coralback", "Coral Crush"),
            Member("bastion_tortoise", "Bastion Tortoise", "Bastion Surge"),
        )))
        addAll(line("Twiglet line", Element.EARTH, StatBlock(50, 15, 12, 17, 6), SkillKind.PHYSICAL, listOf(
            Member("twiglet", "Twiglet", "Twig Jab"),
            Member("barkwarden", "Barkwarden", "Bark Slam"),
            Member("ancient_treant", "Ancient Treant", "Root Quake"),
        )))
        addAll(line("Impling line", Element.CORRUPT, StatBlock(36, 12, 16, 9, 16), SkillKind.MAGICAL, listOf(
            Member("impling", "Impling", "Imp Claw"),
            Member("hexfiend", "Hexfiend", "Hex Bolt"),
            Member("archfiend", "Archfiend", "Infernal Pact"),
        )))

        // ---- 1.33: five more per type (a three-stage and a two-stage line each) ----
        // Fire
        addAll(line("Cinderskink line", Element.FIRE, StatBlock(38, 15, 12, 9, 14), SkillKind.PHYSICAL, listOf(
            Member("cinderskink", "Cinderskink", "Tail Flare"),
            Member("magmander", "Magmander", "Magma Gills"),
            Member("pyrosaur", "Pyrosaur", "Frill Inferno"),
        )))
        addAll(line("Cinderlamb line", Element.FIRE, StatBlock(48, 13, 15, 13, 11), SkillKind.MAGICAL, listOf(
            Member("cinderlamb", "Cinderlamb", "Smoulder Wool"),
            Member("blazeram", "Blazeram", "Blazing Horns"),
        )))
        // Electric
        addAll(line("Joltlet line", Element.ELECTRIC, StatBlock(39, 14, 14, 9, 19), SkillKind.PHYSICAL, listOf(
            Member("joltlet", "Joltlet", "Tail Zap"),
            Member("voltape", "Voltape", "Charged Knuckles"),
            Member("thunderkong", "Thunderkong", "Thunder Pound"),
        )))
        addAll(line("Zaplume line", Element.ELECTRIC, StatBlock(44, 9, 19, 13, 15), SkillKind.MAGICAL, listOf(
            Member("zaplume", "Zaplume", "Static Sting"),
            Member("tempest_jelly", "Tempest Jelly", "Storm Tendrils"),
        )))
        // Water
        addAll(line("Puddlet line", Element.WATER, StatBlock(42, 12, 13, 11, 13), SkillKind.MAGICAL, listOf(
            Member("puddlet", "Puddlet", "Puddle Splash"),
            Member("croaktide", "Croaktide", "Tide Croak"),
            Member("torrentoad", "Torrentoad", "Torrent Spout"),
        )))
        addAll(line("Pincerling line", Element.WATER, StatBlock(44, 14, 8, 18, 7), SkillKind.PHYSICAL, listOf(
            Member("pincerling", "Pincerling", "Shell Pinch"),
            Member("cragclaw", "Cragclaw", "Crushing Claws"),
        )))
        // Flight
        addAll(line("Hootling line", Element.FLIGHT, StatBlock(38, 10, 16, 10, 15), SkillKind.MAGICAL, listOf(
            Member("hootling", "Hootling", "Hoot"),
            Member("strixwind", "Strixwind", "Night Gust"),
            Member("elder_strix", "Elder Strix", "Rune Gale"),
        )))
        addAll(line("Pterix line", Element.FLIGHT, StatBlock(44, 17, 11, 13, 19), SkillKind.PHYSICAL, listOf(
            Member("pterix", "Pterix", "Beak Jab"),
            Member("skyreaver", "Skyreaver", "Reaving Dive"),
        )))
        // Gas
        addAll(line("Sporelet line", Element.GAS, StatBlock(44, 10, 15, 13, 8), SkillKind.MAGICAL, listOf(
            Member("sporelet", "Sporelet", "Spore Puff"),
            Member("fungloom", "Fungloom", "Gloom Spores"),
            Member("mycolord", "Mycolord", "Mycelium Storm"),
        )))
        addAll(line("Stinkit line", Element.GAS, StatBlock(40, 12, 14, 11, 13), SkillKind.MAGICAL, listOf(
            Member("stinkit", "Stinkit", "Stink Spray"),
            Member("reekmane", "Reekmane", "Reek Cloud"),
        )))
        // Corrupt
        addAll(line("Hexling line", Element.CORRUPT, StatBlock(36, 14, 14, 9, 16), SkillKind.PHYSICAL, listOf(
            Member("hexling", "Hexling", "Hex Bite"),
            Member("webstalker", "Webstalker", "Shadow Web"),
            Member("widowmaw", "Widowmaw", "Widow's Kiss"),
        )))
        addAll(line("Nightling line", Element.CORRUPT, StatBlock(42, 13, 17, 12, 18), SkillKind.MAGICAL, listOf(
            Member("nightling", "Nightling", "Night Nip"),
            Member("vampyrex", "Vampyrex", "Blood Drain"),
        )))
        // Earth
        addAll(line("Rollpip line", Element.EARTH, StatBlock(46, 13, 8, 18, 8), SkillKind.PHYSICAL, listOf(
            Member("rollpip", "Rollpip", "Rolling Bash"),
            Member("pangolurk", "Pangolurk", "Scale Slash"),
            Member("ironscale", "Ironscale", "Iron Rampage"),
        )))
        addAll(line("Stubhorn line", Element.EARTH, StatBlock(50, 16, 6, 16, 7), SkillKind.PHYSICAL, listOf(
            Member("stubhorn", "Stubhorn", "Horn Bump"),
            Member("rockrhino", "Rockrhino", "Boulder Charge"),
        )))

        // ---- Mythics: rare single-stage monsters, as strong as a final evolution ----
        add(single("mimic", "Mimic", Element.CORRUPT, Rarity.EPIC, mythic(StatBlock(66, 24, 24, 24, 10)), "Treasure Trap", SkillKind.PHYSICAL, 52, false, minWildLevel = 15))
        add(single("prism_fairy", "Prism Fairy", Element.FLIGHT, Rarity.EPIC, mythic(StatBlock(54, 10, 30, 15, 27)), "Prism Beam", SkillKind.MAGICAL, 52, false, minWildLevel = 15))

        // ---- Legendary bosses ----
        add(single("magma_behemoth", "Magma Behemoth", Element.FIRE, Rarity.LEGENDARY, StatBlock(84, 27, 18, 20, 9), "Magma Surge", SkillKind.PHYSICAL, 60, true))
        add(single("thunder_titan", "Thunder Titan", Element.ELECTRIC, Rarity.LEGENDARY, StatBlock(78, 20, 27, 18, 16), "Judgement Bolt", SkillKind.MAGICAL, 60, true))
        add(single("sky_serpent", "Sky Serpent", Element.FLIGHT, Rarity.LEGENDARY, StatBlock(72, 22, 24, 16, 22), "Cyclone Coil", SkillKind.MAGICAL, 58, true))
        add(single("abyss_kraken", "Abyss Kraken", Element.WATER, Rarity.LEGENDARY, StatBlock(80, 24, 22, 18, 12), "Maelstrom", SkillKind.MAGICAL, 58, true))
        add(single("miasma_hydra", "Miasma Hydra", Element.GAS, Rarity.LEGENDARY, StatBlock(86, 23, 22, 21, 10), "Nine Venoms", SkillKind.MAGICAL, 58, true))
        add(single("dread_lich", "Dread Lich", Element.CORRUPT, Rarity.LEGENDARY, StatBlock(70, 14, 28, 18, 16), "Death Knell", SkillKind.MAGICAL, 62, true))
        add(single("worldroot_colossus", "Worldroot Colossus", Element.EARTH, Rarity.LEGENDARY, StatBlock(88, 26, 16, 24, 8), "Worldsplitter", SkillKind.PHYSICAL, 60, true))
    }.map { sp -> evolvedNames[sp.id]?.let { sp.copy(name = it) } ?: sp }
        .map { sp -> dualTypes[sp.id]?.let { sp.copy(secondary = it) } ?: sp }

    /**
     * The hidden legendary's line (see [Mythic]): kept out of [all], so it's
     * not in the Journal, the wild, shrines or any count. It evolves only by
     * level, at 30, 60 and 100. Stage 1 is the weakest monster in the game;
     * stage 4 the strongest.
     */
    val hidden: List<Species> = listOf(
        Triple(Mythic.MURKLET, "Murklet", StatBlock(30, 9, 9, 8, 8)),
        Triple(Mythic.CINDERWRITHE, "Cinderwrithe", StatBlock(120, 40, 34, 30, 17)),
        Triple(Mythic.FUMEWARDEN, "Fumewarden", StatBlock(140, 50, 48, 40, 32)),
        Triple(Mythic.TURUMARTH, "Turumarth", StatBlock(170, 62, 62, 50, 45)),
    ).mapIndexed { i, (id, name, base) ->
        val element = listOf(Element.WATER, Element.FIRE, Element.FIRE, Element.MYTHICAL)[i]
        Species(
            id = id, name = name, element = element, rarity = Rarity.LEGENDARY, base = base,
            special = Skill("${id}_special", Mythic.SIGNATURES[i].second, element, SkillKind.MAGICAL, 40 + i * 40),
            bossOnly = true, family = "Murklet line", stage = i + 1, stages = 4,
            evolvesTo = Mythic.LINE.getOrNull(i + 1), evolveLevel = Mythic.EVOLVE_LEVELS.getOrNull(i),
            secondary = if (i == 2) Element.WATER else null,
        )
    }

    /** The listed roster plus the hidden line: for looking species up, never for showing or counting them. */
    val everything: List<Species> = all + hidden

    /** Shown in the Journal and counted (not the hidden line). */
    fun isListed(id: String): Boolean = id in byId && id !in Mythic.LINE

    private val byId = everything.associateBy { it.id }

    /** Journal number: the order of [all], so mythics and then legendaries come last. */
    fun number(sp: Species): Int = all.indexOfFirst { it.id == sp.id } + 1

    /**
     * 2.29: one wolf and one hawk. The other hound breeds became Shadow Hounds
     * and the Zipwing line became Fluffchicks, stage for stage.
     */
    val removedIds: Map<String, String> =
        listOf("ember", "crackle_jolt", "gale", "tide", "venom", "moss").associate { "${it}_pup" to "shadow_pup" } +
            listOf("cinder_hound", "volt_hound", "sky_hound", "surf_hound", "toxic_hound", "thorn_hound").associateWith { "dusk_hound" } +
            listOf("inferno_fenrir", "thunder_fenrir", "tempest_fenrir", "tsunami_fenrir", "plague_fenrir", "gaia_fenrir").associateWith { "void_fenrir" } +
            mapOf("zipwing" to "fluffchick", "stormtalon" to "skylark", "thunderbird" to "galehawk")

    /** Evolution lines that were folded into another (old family name to the new one), for candy. */
    val removedFamilies: Map<String, String> =
        listOf("Ember", "Spark", "Gale", "Tide", "Venom", "Moss").associate { "$it Hound line" to "Shadow Hound line" } +
            mapOf("Zipwing line" to "Fluffchick line")

    /** Species from older saves, mapped onto the current roster. */
    private val legacyIds = mapOf(
        "emberpup" to "shadow_pup", "sproutling" to "shadow_pup", "pebblet" to "zapmouse",
        "breezling" to "fluffchick", "greywolf" to "shadow_pup", "squeaker" to "zapmouse",
        "glowmoth" to "prism_fairy", "shadecat" to "gloomwisp", "thornboar" to "toxbug",
        "tidecrab" to "tidefin", "cinderfox" to "emberkit", "dustmole" to "voltrat",
        "stormhawk" to "galehawk", "frostbear" to "reefjaw", "ironshell" to "stingscor",
        "wraith" to "gloombat", "sunlion" to "flarepanther", "wyrmling" to "void_fenrir",
        "elder_treant" to "miasma_hydra", "titan_golem" to "thunder_titan", "seraph" to "prism_fairy",
    ) + removedIds


    fun get(id: String): Species = byId[id] ?: byId.getValue(migrateId(id))

    fun migrateId(id: String): String = when {
        id in byId -> id
        else -> legacyIds[id] ?: "shadow_pup"
    }

    val wild: List<Species> = all.filter { !it.bossOnly }

    /**
     * Silver shrine guardians: strong evolved monsters but never legendary:
     * third stages of the four-stage lines and the final forms of the three-stage lines.
     */
    val silverGuardians: List<Species> = all.filter { !it.bossOnly && it.stage == 3 }

    /** Gold shrine guardians: the legendaries plus the final forms of the four-stage lines. */
    val bosses: List<Species> = all.filter { it.bossOnly || (it.stages == 4 && it.stage == 4) }

    /** Random wild species that can appear at [level], weighted by rarity. */
    /**
     * A random wild species for [level]. [favored] types are three times as
     * likely (time of day); [rareBoost] multiplies the odds of uncommon and
     * rarer monsters (moving around, lures).
     */
    fun randomWild(
        rng: Random,
        level: Int = MAX_MONSTER_LEVEL,
        favored: Set<Element> = emptySet(),
        rareBoost: Double = 1.0,
        /** Extra multipliers by type (the real weather); a dual type takes its better one. */
        boosts: Map<Element, Double> = emptyMap(),
        /** Extra multipliers by species id (the mystery of the week). */
        speciesBoosts: Map<String, Double> = emptyMap(),
        /** Keep evolved forms rare, so first forms fill the wild (off for dens, which want strong monsters). */
        favorFirstForms: Boolean = true,
    ): Species {
        val pool = wild.filter { it.minWildLevel <= level }
        fun weight(s: Species): Int {
            var w = s.rarity.spawnWeight.toDouble()
            if (s.element in favored) w *= 3
            if (boosts.isNotEmpty()) w *= s.types.maxOf { boosts[it] ?: 1.0 }
            if (s.rarity.ordinal > 0) w *= rareBoost
            speciesBoosts[s.id]?.let { w *= it }
            if (favorFirstForms) w *= STAGE_PULL.getOrElse(s.stage - 1) { STAGE_PULL.last() }
            return w.toInt().coerceAtLeast(1)
        }
        var roll = rng.nextInt(pool.sumOf { weight(it) })
        for (s in pool) {
            roll -= weight(s)
            if (roll < 0) return s
        }
        return pool.last()
    }

    /** Wild spawn weight by evolution stage: evolved forms are rare finds (catch a small one and evolve it). */
    val STAGE_PULL = listOf(1.0, 0.3, 0.12, 0.06)

    /** [species]' line evolved as far as a trained monster of [level] would be. */
    fun evolvedFor(species: Species, level: Int): Species {
        var s = lineOf(species).first()
        while (true) {
            val next = s.evolvesTo ?: return s
            val at = s.evolveLevel ?: return s
            if (level < at) return s
            s = get(next)
        }
    }

    /** A trainer's monster: a random line, evolved to fit [level] (as a trained team would be). */
    fun trained(rng: Random, level: Int): Species = evolvedFor(randomWild(rng, level), level)

    /** The full evolution line containing [species]. */
    fun lineOf(species: Species): List<Species> = everything.filter { it.family == species.family }.sortedBy { it.stage }
}
