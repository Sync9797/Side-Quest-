package com.monsterquest.core

/** A short piece of lore for each species, shown in the Monster Journal. */
object Lore {
    private val entries: Map<String, String> = mapOf(
        // Voltfawn line
        "voltfawn" to "Born during spring storms, its spotted coat crackles when it's startled. Herds of them leave scorched hoofprints in wet meadows.",
        "stormstag" to "Its antlers draw lightning down from passing clouds. It charges rivals at full gallop, trailing sparks behind it.",
        "thunderhorn" to "Said to call the first storm of every season. When it stamps, the ground splits and the sky answers with thunder.",
        // Cindrake line
        "cindrake" to "A hatchling dragon that sneezes embers when it's excited. It sleeps curled around warm stones.",
        "blazewyrm" to "Its wings are still too small to fly, but a single flap fans its flames into a roaring blaze.",
        "infernodrake" to "It soars above volcanoes, riding the heat. Its roar can be heard for miles, and nothing nearby stays cold for long.",
        // Drizzlet line
        "drizzlet" to "A tiny serpent that lives in rain puddles and leaps between them during storms.",
        "tidewyrm" to "It coils through rivers and estuaries, steering the current with its tail. Fishers say it guides lost boats home.",
        "maelstrom_serpent" to "When it rises from the sea, the clouds rise with it. It swims through the sky as easily as through the deep.",
        // Mosscub line
        "mosscub" to "Moss grows on its back as it naps in the forest shade. It loves berries and sleeps through the coldest months.",
        "grovebear" to "Saplings take root along its spine. Where it lies down to rest, a grove grows by spring.",
        "terraursa" to "An ancient bear with a living forest on its back and magma in its veins. Mountains are said to stir when it wakes.",
        // Emberkit line
        "emberkit" to "A playful kitten whose tail glows like a coal. It follows campfires and naps in warm ashes.",
        "blazecat" to "Its fangs heat up when it hunts. It stalks through tall grass, leaving faint wisps of smoke.",
        "flarepanther" to "Its mane burns brighter the angrier it gets. Few rivals stay to see it at full flame.",
        "solar_tiger" to "It basks at noon and stores the sun's heat in its wings. At dusk it glows like a second sunset.",
        // Zapmouse line
        "zapmouse" to "Its fur stands on end with static. It nests inside old electronics and chews through cables for fun.",
        "voltrat" to "It stores charge in its cheeks and fires sparks at anything that gets too close to its stash.",
        "thunderhog" to "Its quills crackle with stored lightning. When threatened it curls up and lets the sparks fly.",
        "storm_kirin" to "Its stomps echo like thunder. Storms are said to follow it from valley to valley.",
        // Fluffchick line
        "fluffchick" to "A ball of fluff that peeps loudly at sunrise. It can barely flutter, but it never stops trying.",
        "skylark" to "Its flight feathers grow sharp as blades. It slices through the air in daring loops.",
        "galehawk" to "It hunts from high above, then dives faster than the eye can follow, riding the gusts it creates.",
        "tempest_roc" to "Its wingbeats stir up storms. Sailors take its shadow overhead as a warning to head for shore.",
        // Bubblefin line
        "bubblefin" to "A little fish that blows bubbles to confuse predators. Schools of them shimmer in shallow reefs.",
        "tidefin" to "Its teeth come in early and it isn't shy about using them. It patrols reefs looking for trouble.",
        "reefjaw" to "A reef shark with skin rough as coral. It rides the riptide to strike from where it's least expected.",
        "leviathan" to "A shadow beneath the waves as long as a ship. Old charts mark its waters with a single dark warning.",
        // Toxbug line
        "toxbug" to "A small beetle that sprays foul mist when bothered. Gardeners find them hiding under damp leaves.",
        "mirepede" to "It wriggles through swamp mud on dozens of legs. Its bite leaves a burning, acidic sting.",
        "stingscor" to "Its tail drips venom that glows faintly in the dark. It waits motionless for hours before striking.",
        "basilisk" to "A mantis with scythe-like arms and poisoned wings. Swarms of lesser bugs scatter when it takes flight.",
        // Gloomwisp line
        "gloomwisp" to "A flicker of dark light that drifts through graveyards at night. Touching it leaves a chill that lingers.",
        "gloombat" to "It hangs in abandoned towers and shrieks to unsettle intruders. Its wings are made of shadow.",
        "nightstalker" to "A wraith that follows travelers on moonless nights. Look back and it's gone; look ahead and it's closer.",
        "abyss_lord" to "It rules the darkness between stars. Where it passes, light itself seems to fade.",
        // Hounds
        "ember_pup" to "A wolf pup with a coat of glowing embers. Its playful nips leave little scorch marks.",
        "cinder_hound" to "It leads its pack through burnt forests, where the ground is still warm from the fire.",
        "inferno_fenrir" to "A three-headed beast wreathed in black fire. Each head watches a different path.",
        "spark_pup" to "Its fur crackles when it wags its tail. Packs of them light up the night like fireflies.",
        "volt_hound" to "It runs so fast that sparks trail from its paws. Its howl makes radios crackle.",
        "thunder_fenrir" to "The leader of the storm wolves. When it howls, lightning answers from the clouds.",
        "gale_pup" to "A light-footed pup that chases leaves on the wind and sometimes floats off after them.",
        "sky_hound" to "It runs along the wind itself, bounding over treetops without touching a branch.",
        "tempest_fenrir" to "Its pack races across the sky with the storm front. Its howl is the roar of the gale.",
        "tide_pup" to "It loves to splash in waves and shakes seawater on anyone nearby.",
        "surf_hound" to "It rides breaking waves on its belly, hunting fish in the surf with its pack.",
        "tsunami_fenrir" to "A wolf of the open sea. Its howl pulls the tide in, and its charge brings it crashing down.",
        "venom_pup" to "Its breath smells faintly of marsh gas. It's friendly, but its bite stings for days.",
        "toxic_hound" to "It prowls fog-covered swamps, its fangs slick with a numbing poison.",
        "plague_fenrir" to "A sickly green mist trails the pack wherever this wolf leads it.",
        "shadow_pup" to "It hides in shadows and pounces on its own pack for fun. Its eyes glow at night.",
        "dusk_hound" to "It hunts in the hour between day and night, when shapes are hardest to see.",
        "void_fenrir" to "A wolf of pure darkness. Its pack is only seen when the stars go out behind it.",
        "moss_pup" to "Clover sprouts from its paw prints. It rolls in meadows until it's covered in petals.",
        "thorn_hound" to "Thorny vines grow along its back. It guards old forests from anyone who would harm them.",
        "gaia_fenrir" to "Ancient trees bow as it passes. Its pack is said to be the forest's oldest guardians.",
        // Goopling line
        "goopling" to "A wobbly blob of slime that oozes through cracks. Cut it in half and it just becomes two.",
        "sludgeon" to "It gathers in sewers and drainage ditches, growing bigger with everything it absorbs.",
        "slime_sovereign" to "A many-headed mass of ooze. It splits and reforms faster than anything can harm it.",
        // Pebblit line
        "pebblit" to "A pebble with a warm, glowing core. It rolls downhill whenever it wants to go somewhere.",
        "boulderon" to "It shoulder-charges cliffs to break off new rock for itself. Its core glows red through the cracks.",
        "magma_colossus" to "A walking mountain of molten stone. The ground cracks and smokes beneath each step.",
        // Zipwing line
        "zipwing" to "A tiny bird that zips between power lines, recharging as it perches.",
        "stormtalon" to "It dives through thunderclouds to sharpen its static-charged talons.",
        "thunderbird" to "Its wingbeats roll like thunder, and lightning flashes from its feathers as it flies.",
        // Shellsnap line
        "shellsnap" to "A small turtle with a snapping beak. It hides in its shell at the first sign of trouble.",
        "coralback" to "Coral grows on its shell, and little fish make their homes in its branches.",
        "bastion_tortoise" to "Its shell is a fortress. Whole reefs have grown on the backs of the oldest ones.",
        // Twiglet line
        "twiglet" to "A walking sapling that soaks up morning sun. It stands perfectly still to hide among other plants.",
        "barkwarden" to "Thick bark armors its trunk. It guards young trees from anything that would trample them.",
        "ancient_treant" to "Older than any village. Its roots run deep beneath the forest and remember everything.",
        // Impling line
        "impling" to "A mischievous little fiend that steals shiny things and laughs from the shadows.",
        "hexfiend" to "It scrawls cursed runes on walls and doors. Bad luck follows anyone who reads them.",
        "archfiend" to "It bargains with fire and shadow. Its pacts always cost more than they seem to.",
        // Cinderskink line
        "cinderskink" to "A lizard whose tail glows like a lantern. It basks on sun-warmed rocks all day.",
        "magmander" to "It swims through rivers of lava, breathing through glowing gills.",
        "pyrosaur" to "Its fiery frill flares open when it's angry. Rivals usually back away at the first glow.",
        // Cinderlamb line
        "cinderlamb" to "Its wool smoulders but never burns. Shepherds in cold villages keep them for warmth.",
        "blazeram" to "Its curled horns glow red-hot. It settles every argument with a headbutt.",
        // Joltlet line
        "joltlet" to "A little monkey with a charged tail. It swings from branch to branch on bursts of static.",
        "voltape" to "Its knuckles spark when it pounds the ground. It challenges anything that looks strong.",
        "thunderkong" to "When it beats its chest, thunder rolls through the jungle. It rules the canopy.",
        // Zaplume line
        "zaplume" to "A drifting jellyfish that glows in the dark. Its tendrils give a sharp static sting.",
        "tempest_jelly" to "It floats on storm winds with trailing tendrils of lightning. Swarms of them light up the sky.",
        // Puddlet line
        "puddlet" to "A tadpole that lives in rain puddles. It sings softly after storms.",
        "croaktide" to "Its croak can be heard across the whole pond. It loves the rain more than anything.",
        "torrentoad" to "It sprays water and toxic mist from its back. Swamps fall silent when it croaks.",
        // Pincerling line
        "pincerling" to "A tiny crab that collects shells and pebbles. It pinches anything that tries to take them.",
        "cragclaw" to "Its claws can crack boulders. It scavenges the shoreline for whatever the tide brings in.",
        // Hootling line
        "hootling" to "A round little owl that hoots through the night. It never seems to sleep.",
        "strixwind" to "It glides silently on night winds. Its keen eyes can spot a mouse from far above.",
        "elder_strix" to "Glowing runes cover its feathers. It's said to remember every secret spoken under the moon.",
        // Pterix line
        "pterix" to "A hatchling from a long-lost age, with a beak hard as stone. It squawks at anything that moves.",
        "skyreaver" to "An ancient flier with a rock-hard skull. It dives on prey from the clifftops.",
        // Sporelet line
        "sporelet" to "A tiny mushroom that puffs spores when it's poked. It grows in damp, shady places.",
        "fungloom" to "Its spores make anything nearby drowsy. It glows faintly in dark forests.",
        "mycolord" to "Its mycelium spreads under the whole forest floor. Every mushroom there answers to it.",
        // Stinkit line
        "stinkit" to "A skunk-like critter with a fearsome spray. Most predators learn to leave it alone.",
        "reekmane" to "Its mane traps a foul cloud. Even the boldest monsters keep their distance.",
        // Hexling line
        "hexling" to "A small spider with glowing markings. Its bite carries a weak curse.",
        "webstalker" to "It spins webs of shadow between trees and waits for prey to wander in.",
        "widowmaw" to "Its webs are strong enough to hold any monster, and its kiss is deadly poison.",
        // Nightling line
        "nightling" to "A little bat that nibbles fruit at night and hides in rafters during the day.",
        "vampyrex" to "It drains the strength of its prey. Its eyes glow red in the dark.",
        // Rollpip line
        "rollpip" to "A shy armadillo-like critter that rolls into a ball at the first sign of danger.",
        "pangolurk" to "Its scales are sharp as blades. It digs through hillsides looking for insects.",
        "ironscale" to "Its scales have hardened to iron. It rolls through walls without slowing down.",
        // Stubhorn line
        "stubhorn" to "A young rhino with a stubby horn. It headbutts everything to practice.",
        "rockrhino" to "Its rock-hard horn can knock down a tree. Once it starts charging, it doesn't stop.",
        // Mythics
        "mimic" to "It looks like a treasure chest until someone tries to open it. Then it bites.",
        "prism_fairy" to "A fairy made of light. Rainbows appear wherever it has been.",
        // Legendaries
        "magma_behemoth" to "It sleeps deep in a volcano. When it wakes, the mountain erupts.",
        "thunder_titan" to "A giant of living lightning. Its footsteps are thunder and its gaze is a flash.",
        "sky_serpent" to "It circles the world above the clouds. Few have seen it and fewer have caught it.",
        "abyss_kraken" to "It rules the deepest trenches of the sea. Its tentacles can drag ships beneath the waves.",
        "miasma_hydra" to "A many-headed serpent of poison mist. Cut off one head and two more grow back.",
        "dread_lich" to "An ancient sorcerer who cheated death. Its very presence drains the will to fight.",
        "worldroot_colossus" to "Its roots hold the world together. Legends say forests grow wherever it has walked.",
        // The hidden line
        "murklet" to "A blob of living water. Look closely and something small is curled up inside it.",
        "cinderwrithe" to "Molten slime with a young dragon coiled inside, its eyes glowing through the goo.",
        "fumewarden" to "It burst out of its slime as a sea-dragon of fire and water, wreathed in steam.",
        "turumarth" to "Six heads and a body of living fire. Old stories say it guards the deepest waters of the world.",
    )

    /** The lore for [species] (every species has some). */
    fun of(species: Species): String = entries[species.id] ?: "Little is known about ${species.name} yet."

    /** For tests: species without lore. */
    internal fun missing(): List<String> = Bestiary.all.map { it.id }.filter { it !in entries }
}

/**
 * A species' ceiling: its stats at level 100 with perfect genes and a
 * Balanced personality, to judge how good it can get.
 */
object MaxStats {
    fun of(species: Species): Map<Stat, Int> {
        val m = Monster(
            uid = "max:${species.id}",
            speciesId = species.id,
            personality = Personality.BALANCED,
            ivs = StatBlock(MAX_IV, MAX_IV, MAX_IV, MAX_IV, MAX_IV),
            level = MAX_MONSTER_LEVEL,
        )
        return MONSTER_STATS.associateWith { m.stat(it) }
    }

    fun total(species: Species): Int = of(species).values.sum()

    private val ranking: List<String> by lazy { Bestiary.everything.sortedByDescending { total(it) }.map { it.id } }

    /** 1 = the highest stat total of every species. */
    fun rank(species: Species): Int = ranking.indexOf(species.id) + 1

    /** The highest max of [stat] among all species (to draw bars against). */
    fun best(stat: Stat): Int = Bestiary.everything.maxOf { of(it).getValue(stat) }
}
