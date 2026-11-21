package com.monsterquest.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Subclasses: at level 15 every class picks one of three specialties.
// ---------------------------------------------------------------------------

const val SUBCLASS_UNLOCK_LEVEL = 15
const val SUBCLASS_CHANGE_COST = 300

/**
 * What a subclass, or a hero skill at its rank, adds. Everything is a plain
 * number so the battle code can add the sources together.
 */
data class Perks(
    /** Percent bonuses to the hero's own stats. */
    val attackPct: Int = 0,
    val magicPct: Int = 0,
    val defensePct: Int = 0,
    val speedPct: Int = 0,
    val maxHpPct: Int = 0,
    /** Extra spell damage, in percent. */
    val spellPct: Int = 0,
    /** Extra healing and ward strength, in percent. */
    val healPct: Int = 0,
    /** Spells heal the hero for this share of the damage they deal, in percent. */
    val spellDrainPct: Int = 0,
    /** Energy restored every turn, and extra max Energy. */
    val energyPerTurn: Int = 0,
    val maxEnergy: Int = 0,
    /** Extra critical-hit chance, in percent, and extra critical damage (1.5x becomes 1.5 + this / 100). */
    val critPct: Int = 0,
    val critDamagePct: Int = 0,
    /** Extra dodge chance, in percent. */
    val dodgePct: Int = 0,
    /** Chance (percent) that a basic attack throws the foe off (its next move is likely to miss). */
    val blindPct: Int = 0,
    /** Extra taming odds, in percent. */
    val tamePct: Int = 0,
    /** Your monsters' HP, Attack, Defense and Speed, in percent. */
    val teamPct: Int = 0,
    /** Extra gold and XP from battles, in percent. */
    val goldPct: Int = 0,
    val xpPct: Int = 0,
    /** Damage grows as HP drops, up to this percent near 0 HP. */
    val ragePct: Int = 0,
    /** Start every battle behind a ward worth this share of max HP. */
    val startWardPct: Int = 0,
    /** HP healed at the start of each of the hero's turns, in percent of max HP. */
    val regenPct: Int = 0,
    /** Every weapon counts as one of your class's. */
    val anyWeapon: Boolean = false,
) {
    operator fun plus(o: Perks) = Perks(
        attackPct + o.attackPct, magicPct + o.magicPct, defensePct + o.defensePct, speedPct + o.speedPct, maxHpPct + o.maxHpPct,
        spellPct + o.spellPct, healPct + o.healPct, spellDrainPct + o.spellDrainPct,
        energyPerTurn + o.energyPerTurn, maxEnergy + o.maxEnergy,
        critPct + o.critPct, critDamagePct + o.critDamagePct, dodgePct + o.dodgePct, blindPct + o.blindPct, tamePct + o.tamePct,
        teamPct + o.teamPct, goldPct + o.goldPct, xpPct + o.xpPct, ragePct + o.ragePct, startWardPct + o.startWardPct,
        regenPct + o.regenPct, anyWeapon || o.anyWeapon,
    )
}

@Serializable
enum class Subclass(val parent: HeroClass, val label: String, val summary: String, val perks: Perks) {
    // Warrior
    BERSERKER(
        HeroClass.WARRIOR, "Berserker", "+15% Attack and hits harder as your HP drops (up to +40%), but −10% Defense",
        Perks(attackPct = 15, defensePct = -10, ragePct = 40),
    ),
    PALADIN(
        HeroClass.WARRIOR, "Paladin", "+15% Defense, healing +30%, and every battle starts behind a ward (15% of max HP)",
        Perks(defensePct = 15, healPct = 30, startWardPct = 15),
    ),
    WARLORD(
        HeroClass.WARRIOR, "Warlord", "Leads from the front: your monsters get +8% HP, Attack, Defense and Speed",
        Perks(teamPct = 8),
    ),

    // Mage
    ELEMENTALIST(
        HeroClass.MAGE, "Elementalist", "Spells deal +25% damage",
        Perks(spellPct = 25),
    ),
    CLERIC(
        HeroClass.MAGE, "Cleric", "Healing and wards +50%, +10 max Energy",
        Perks(healPct = 50, maxEnergy = 10),
    ),
    WARLOCK(
        HeroClass.MAGE, "Warlock", "Spells heal you for 20% of the damage they deal, +10% Magic",
        Perks(spellDrainPct = 20, magicPct = 10),
    ),

    // Rogue
    ASSASSIN(
        HeroClass.ROGUE, "Assassin", "+12% critical-hit chance and critical hits deal 2x instead of 1.5x",
        Perks(critPct = 12, critDamagePct = 50),
    ),
    TRICKSTER(
        HeroClass.ROGUE, "Trickster", "+8% dodge, and your attacks have a 25% chance to throw the foe off balance",
        Perks(dodgePct = 8, blindPct = 25),
    ),
    BEASTMASTER(
        HeroClass.ROGUE, "Beastmaster", "+20% taming odds and your monsters get +5% HP, Attack, Defense and Speed",
        Perks(tamePct = 20, teamPct = 5),
    ),

    // Adventurer
    JACK_OF_ALL_TRADES(
        HeroClass.NONE, "Jack of All Trades", "Every weapon counts as your own: the class bonus with any weapon, and +5% to all stats",
        Perks(anyWeapon = true, attackPct = 5, magicPct = 5, defensePct = 5, speedPct = 5),
    ),
    SURVIVOR(
        HeroClass.NONE, "Survivor", "+20% max HP and heal 4% of your HP every turn",
        Perks(maxHpPct = 20, regenPct = 4),
    ),
    WAYFARER(
        HeroClass.NONE, "Wayfarer", "+20% gold and XP from battles",
        Perks(goldPct = 20, xpPct = 20),
    );

    companion object {
        fun of(cls: HeroClass): List<Subclass> = entries.filter { it.parent == cls }
    }
}

// ---------------------------------------------------------------------------
// Skills: passive abilities that grow every 10 levels. Heroes pick theirs
// (one at level 30, a second at 45 and a third at 60, which reads ★1 · Lv 10); a monster gets one
// at level 30 that fits its type and kind.
// ---------------------------------------------------------------------------

const val MAX_SKILL_RANK = 5
/** Levels between skill ranks. */
const val SKILL_RANK_LEVELS = 10
/** Hero levels that open the 1st, 2nd and 3rd skill slot (60 reads ★1 · Lv 10). */
val HERO_SKILL_LEVELS = listOf(30, 45, 60)
const val MONSTER_SKILL_LEVEL = 30
const val SKILL_CHANGE_COST = 500
/** From this rank, skills that only helped their owner cover the whole team. */
const val TEAM_RANGE_RANK = 3

/** A skill's rank once its owner is [level], when it was gained at [from]. 0 = not yet. */
fun skillRank(level: Int, from: Int): Int =
    if (level < from) 0 else (1 + (level - from) / SKILL_RANK_LEVELS).coerceAtMost(MAX_SKILL_RANK)

@Serializable
enum class Trait(val label: String, val forHero: Boolean, val forMonster: Boolean) {
    MORALE_MASTER("Morale Master", true, false),
    PACK_LEADER("Pack Leader", false, true),
    KLEPTOMANIAC("Kleptomaniac", true, false),
    @SerialName("INSOMNIA") WAKEFUL("Wakeful", true, true),
    @SerialName("INTIMIDATE") MENACE("Menace", true, true),
    HOVER("Hover", true, false),
    @SerialName("LEVITATE") AIRBORNE("Airborne", false, true),
    @SerialName("OBLIVIOUS") UNFLAPPABLE("Unflappable", true, true),
    QUICK_STEP("Quick Step", true, true),
    TREASURE_HUNTER("Treasure Hunter", true, false),
    SCHOLAR("Scholar", true, false),
    SECOND_WIND("Second Wind", true, true),
    FIRST_STRIKE("First Strike", true, false),
    BEAST_WHISPERER("Beast Whisperer", true, false),
    REGENERATION("Regeneration", true, true),
    EAGLE_EYE("Eagle Eye", true, false),
    MANA_WELL("Mana Well", true, false),
    THICK_HIDE("Thick Hide", false, true),
    @SerialName("STATIC") CRACKLE_SKIN("Crackle Skin", false, true),
    @SerialName("FLAME_BODY") EMBER_HIDE("Ember Hide", false, true),
    @SerialName("POISON_POINT") VENOM_BARBS("Venom Barbs", false, true),

    // Monster traits inspired by early-generation abilities (see docs/trait-ideas.md for more to come).
    LAST_STAND("Last Stand", false, true),
    RAIN_RUNNER("Rain Runner", false, true),
    SUN_CHASER("Sun Chaser", false, true),
    IRON_SKULL("Iron Skull", false, true),
    OVERBEARING("Overbearing", false, true),
    MOMENTUM("Momentum", false, true),
    GRIT("Grit", false, true),
    KEEN_SIGHT("Keen Sight", false, true),
    UNSHAKEN("Unshaken", false, true),
    STALWART("Stalwart", false, true),
    LIGHTNING_EATER("Lightning Eater", false, true),
    SPRING_SOAK("Spring Soak", false, true),
    KINDLING("Kindling", false, true),
    BARBED_HIDE("Barbed Hide", false, true),
    SCAVENGER("Scavenger", false, true),
    SNARE_AURA("Snare Aura", false, true),
    PLATED("Plated", false, true),
    FORTUNE("Fortune", false, true),
    SPORE_COAT("Spore Coat", false, true),
    HARD_SCALES("Hard Scales", false, true),
    RAIN_DRINKER("Rain Drinker", false, true),
    INSULATED("Insulated", false, true),
    // Signature skills of legendaries: they bring their weather with them.
    STORMCALLER("Stormcaller", false, true),
    SUNBRINGER("Sunbringer", false, true);

    /** Floats above the ground: Earth moves can't touch it. */
    val hovers: Boolean get() = this == HOVER || this == AIRBORNE

    // ---- numbers per rank (1..5) ----

    /** Morale Master / Pack Leader: team stats, in percent. */
    fun teamPct(rank: Int) = 2 * rank
    /** Kleptomaniac: the off-hand strike's power (and the share of its other stats that counts), in percent. */
    fun offhandPct(rank: Int) = 40 + 15 * (rank - 1)
    /** Wakeful: turns of sleep shaken off (rank 5: can't be put to sleep). */
    fun sleepCut(rank: Int) = (rank + 1) / 2
    /** Menace: Attack stages taken from every foe on top of the 66.7% cut (one more at ranks 3 and 5). */
    fun menaceStages(rank: Int) = (rank - 1) / 2
    /** Quick Step: dodge chance, in percent. */
    fun dodgePct(rank: Int) = 4 + 3 * rank
    fun goldPct(rank: Int) = 10 * rank
    fun xpPct(rank: Int) = 6 * rank
    /** Second Wind: heal this share of max HP once, on dropping below 25%. */
    fun secondWindPct(rank: Int) = 15 + 5 * rank
    fun tamePct(rank: Int) = 5 * rank
    fun regenPct(rank: Int) = (rank + 3) / 2
    fun critPct(rank: Int) = 3 * rank
    fun energyPerTurn(rank: Int) = rank
    fun maxEnergy(rank: Int) = 2 * rank
    /** Thick Hide: less damage taken, in percent. */
    fun hidePct(rank: Int) = 5 + 2 * rank
    /** Crackle Skin, Ember Hide, Venom Barbs: chance to afflict an attacker, in percent. */
    fun touchPct(rank: Int) = 8 + 3 * rank
    /**
     * Hover at team range: less Earth damage for the rest of the team, in
     * percent. At rank 5 the whole team hovers until the hero takes a hit,
     * then falls back to this.
     */
    fun hoverTeamPct(rank: Int) = if (rank >= 4) 50 else 25

    /** Last Stand: own-type moves hit harder at or below a third of its HP, in percent. */
    fun lastStandPct(rank: Int) = 30 + 5 * rank
    /** Rain Runner / Sun Chaser: Speed multiplier in Rain / Drought. */
    fun weatherSpeed(rank: Int) = 1.5 + 0.1 * rank
    /** Iron Skull: recoil cut, in percent (none at all at rank 5). */
    fun ironSkullPct(rank: Int) = 20 * rank
    /** Overbearing: chance a foe's move costs an extra use, in percent. */
    fun overbearingPct(rank: Int) = 20 * rank
    /** Momentum: chance each round to gain +1 Speed, in percent. */
    fun momentumPct(rank: Int) = 20 * rank
    /** Grit: more Attack while it has a status, in percent. */
    fun gritPct(rank: Int) = 30 + 5 * rank
    /** Keen Sight: extra accuracy, in percent. */
    fun keenSightPct(rank: Int) = 4 + 4 * rank
    /** Stalwart: survives a knockout blow with 1 HP when at least this much HP (in percent) is left. */
    fun stalwartMinPct(rank: Int) = 100 - 5 * (rank - 1)
    /** Lightning Eater / Spring Soak / Kindling: HP healed on absorbing a hit, in percent of max HP. */
    fun absorbHealPct(rank: Int) = 10 + 3 * rank
    /** Kindling: Fire moves stronger after absorbing fire, in percent. */
    fun kindlePct(rank: Int) = 30 + 5 * rank
    /** Barbed Hide: attackers lose this share of their max HP, in percent. */
    fun barbedPct(rank: Int) = 3 + rank
    /** Scavenger: chance to find an item after a won battle, in percent. */
    fun scavengePct(rank: Int) = 5 + 5 * rank
    /** Snare Aura: cut to a pack member's chance of running off, in percent. */
    fun snarePct(rank: Int) = 20 * rank

    /** Fortune: side-effect chances of its moves are multiplied by this. */
    fun fortuneMult(rank: Int) = 1.0 + 0.2 * rank
    /** Hard Scales: more Defense while it has a status, in percent. */
    fun hardScalesPct(rank: Int) = 30 + 5 * rank
    /** Rain Drinker: HP healed each turn in Rain, in percent. */
    fun rainDrinkPct(rank: Int) = 3 + rank / 2
    /** Insulated: less Fire and Electric damage, in percent. */
    fun insulatedPct(rank: Int) = 20 + 6 * rank
    /** Plated: less damage from super-effective hits, in percent (it can't be critically hit at all). */
    fun platedPct(rank: Int) = 2 * rank

    /** The type a Lightning Eater, Spring Soak or Kindling monster absorbs. */
    val absorbs: Element?
        get() = when (this) {
            LIGHTNING_EATER -> Element.ELECTRIC
            SPRING_SOAK -> Element.WATER
            KINDLING -> Element.FIRE
            else -> null
        }

    /** Whether the effect covers the owner's whole side at this rank (hero skills only). */
    fun teamRange(rank: Int, hero: Boolean) = hero && rank >= TEAM_RANGE_RANK && this in RANGED

    /** What the skill does at [rank], for the owner described ("you" or "it"). */
    fun describe(rank: Int, hero: Boolean = true): String {
        val r = rank.coerceIn(1, MAX_SKILL_RANK)
        val team = teamRange(r, hero)
        val who = if (hero) "you" else "it"
        val whoCap = who.replaceFirstChar { it.uppercase() }
        return when (this) {
            MORALE_MASTER -> "Your monsters get +${teamPct(r)}% HP, Attack, Defense and Speed"
            PACK_LEADER -> "Its team gets +${teamPct(r)}% HP, Attack, Defense and Speed"
            KLEPTOMANIAC -> "Carry a second weapon: it strikes again after each attack at ${offhandPct(r)}% power" +
                if (r >= 3) ", and its effects work too" else ""
            WAKEFUL -> if (r >= MAX_SKILL_RANK) "Can't be put to sleep" + (if (team) ", and your team wakes ${sleepCut(r - 1)} turns sooner" else "")
            else "Wakes ${sleepCut(r)} turn${if (sleepCut(r) > 1) "s" else ""} sooner from sleep" + if (team) " (whole team)" else ""
            MENACE -> "Every foe's Attack drops to 67% as the battle starts" +
                (if (menaceStages(r) > 0) ", then ${menaceStages(r)} stage${if (menaceStages(r) > 1) "s" else ""} more" else "")
            HOVER -> "Earth moves can't touch you" + when {
                r >= MAX_SKILL_RANK -> "; your whole team hovers too, until you take a hit (then ${hoverTeamPct(r)}% less Earth damage)"
                team -> "; your team takes ${hoverTeamPct(r)}% less Earth damage"
                else -> ""
            }
            AIRBORNE -> "Floats: Earth moves can't touch it"
            UNFLAPPABLE -> "$whoCap can't have stats lowered or be scared" + if (team) " (whole team)" else ""
            QUICK_STEP -> "${dodgePct(r)}% chance to dodge attacks" + if (team) " (your monsters: ${dodgePct(r) / 2}%)" else ""
            TREASURE_HUNTER -> "+${goldPct(r)}% gold from battles"
            SCHOLAR -> "+${xpPct(r)}% XP from battles, for you and your monsters"
            SECOND_WIND -> "Once a battle, heals ${secondWindPct(r)}% HP on dropping below 25%" + if (team) " (whole team)" else ""
            FIRST_STRIKE -> (if (team) "Your whole team acts" else "You act") + " first in the opening round, and +${3 * r}% Speed"
            BEAST_WHISPERER -> "+${tamePct(r)}% taming odds"
            REGENERATION -> "Heals ${regenPct(r)}% HP every turn" + if (team) " (whole team)" else ""
            EAGLE_EYE -> "+${critPct(r)}% critical-hit chance"
            MANA_WELL -> "+${energyPerTurn(r)} Energy every turn and +${maxEnergy(r)} max Energy"
            THICK_HIDE -> "Takes ${hidePct(r)}% less damage"
            CRACKLE_SKIN -> "${touchPct(r)}% chance to shock anything that hits it"
            EMBER_HIDE -> "${touchPct(r)}% chance to burn anything that hits it"
            VENOM_BARBS -> "${touchPct(r)}% chance to poison anything that hits it"
            LAST_STAND -> "Its own-type moves hit ${lastStandPct(r)}% harder at a third of its HP or less"
            RAIN_RUNNER -> "Speed x${"%.1f".format(weatherSpeed(r))} in Rain"
            SUN_CHASER -> "Speed x${"%.1f".format(weatherSpeed(r))} in a Drought"
            IRON_SKULL -> if (r >= MAX_SKILL_RANK) "Takes no recoil damage" else "Takes ${ironSkullPct(r)}% less recoil damage"
            OVERBEARING -> "${overbearingPct(r)}% chance a foe's move costs an extra use"
            MOMENTUM -> if (r >= MAX_SKILL_RANK) "Speed rises every round" else "${momentumPct(r)}% chance each round for its Speed to rise"
            GRIT -> "${gritPct(r)}% more Attack while it has a status"
            KEEN_SIGHT -> "+${keenSightPct(r)}% accuracy" + if (r >= 3) ", and it can't be blinded" else ""
            UNSHAKEN -> "Never flinches" + if (r >= 3) ", and it can't be thrown off balance" else ""
            STALWART -> "Once a battle, survives a knockout blow with 1 HP" +
                if (stalwartMinPct(r) >= 100) " (at full HP)" else " (at ${stalwartMinPct(r)}% HP or more)"
            LIGHTNING_EATER -> "Electric attacks heal it ${absorbHealPct(r)}% HP instead of hurting"
            SPRING_SOAK -> "Water attacks heal it ${absorbHealPct(r)}% HP instead of hurting"
            KINDLING -> "Fire attacks can't hurt it: they heal ${absorbHealPct(r)}% HP and its Fire moves hit ${kindlePct(r)}% harder"
            BARBED_HIDE -> "Anything that hits it loses ${barbedPct(r)}% of its max HP"
            SCAVENGER -> "${scavengePct(r)}% chance to find an item after a win"
            SNARE_AURA -> if (r >= MAX_SKILL_RANK) "Pack members never run off when one is tamed"
            else "Pack members are ${snarePct(r)}% less likely to run off when one is tamed"
            PLATED -> "Can't be critically hit, and takes ${platedPct(r)}% less from super-effective hits"
            FORTUNE -> "Its moves' side effects are x${"%.1f".format(fortuneMult(r))} as likely"
            SPORE_COAT -> "${touchPct(r)}% chance to put to sleep, poison or shock anything that hits it"
            HARD_SCALES -> "${hardScalesPct(r)}% more Defense while it has a status"
            RAIN_DRINKER -> "Heals ${rainDrinkPct(r)}% HP every turn in Rain"
            INSULATED -> "Takes ${insulatedPct(r)}% less Fire and Electric damage"
            STORMCALLER -> "Brings Rain to every battle it joins"
            SUNBRINGER -> "Brings a Drought to every battle it joins"
        }
    }

    companion object {
        /** Hero skills that spread to the team at [TEAM_RANGE_RANK]. */
        val RANGED = setOf(WAKEFUL, HOVER, UNFLAPPABLE, QUICK_STEP, SECOND_WIND, FIRST_STRIKE, REGENERATION)
        val heroSkills: List<Trait> = entries.filter { it.forHero }
    }
}

/** Which skill each monster gets at level 30: it follows the creature's kind and type. */
object MonsterTraits {
    private val byFamily: Map<String, Trait> = mapOf(
        // Starters
        "Voltfawn line" to Trait.CRACKLE_SKIN,          // a charged stag
        "Cindrake line" to Trait.MENACE,      // dragons
        "Drizzlet line" to Trait.REGENERATION,    // sea serpents shed and heal
        "Mosscub line" to Trait.THICK_HIDE,       // bears
        // Four-stage lines
        "Emberkit line" to Trait.QUICK_STEP,      // cats
        "Zapmouse line" to Trait.CRACKLE_SKIN,          // static fur
        "Fluffchick line" to Trait.AIRBORNE,      // birds
        "Bubblefin line" to Trait.UNFLAPPABLE,      // fish don't care
        "Toxbug line" to Trait.VENOM_BARBS,      // stingers
        "Gloomwisp line" to Trait.AIRBORNE,       // wisps and bats float
        // More three-stage lines
        "Goopling line" to Trait.REGENERATION,    // slimes reform
        "Pebblit line" to Trait.EMBER_HIDE,       // molten rock
        "Shellsnap line" to Trait.THICK_HIDE,     // shells
        "Twiglet line" to Trait.REGENERATION,     // trees regrow
        "Impling line" to Trait.WAKEFUL,         // fiends never sleep
        // 1.33 lines
        "Cinderskink line" to Trait.EMBER_HIDE,   // a burning tail
        "Cinderlamb line" to Trait.THICK_HIDE,    // thick wool
        "Joltlet line" to Trait.QUICK_STEP,       // monkeys dodge
        "Zaplume line" to Trait.CRACKLE_SKIN,           // stinging jelly
        "Puddlet line" to Trait.REGENERATION,     // frogs regrow
        "Pincerling line" to Trait.THICK_HIDE,    // shells
        "Hootling line" to Trait.WAKEFUL,        // owls stay up all night
        "Pterix line" to Trait.AIRBORNE,          // fliers
        "Sporelet line" to Trait.VENOM_BARBS,    // poisonous spores
        "Stinkit line" to Trait.MENACE,       // nobody wants to get close
        "Hexling line" to Trait.VENOM_BARBS,     // venom fangs
        "Nightling line" to Trait.AIRBORNE,       // bats fly
        "Rollpip line" to Trait.THICK_HIDE,       // armored scales
        "Stubhorn line" to Trait.MENACE,      // a charging rhino
        // Mythics
        "Mimic" to Trait.THICK_HIDE,              // it's a chest
        "Prism Fairy" to Trait.AIRBORNE,
        // Legendaries
        "Magma Behemoth" to Trait.EMBER_HIDE,
        "Thunder Titan" to Trait.CRACKLE_SKIN,
        "Sky Serpent" to Trait.AIRBORNE,
        "Abyss Kraken" to Trait.MENACE,
        "Miasma Hydra" to Trait.REGENERATION,
        "Dread Lich" to Trait.WAKEFUL,           // the undead don't sleep
        "Worldroot Colossus" to Trait.THICK_HIDE,
    )

    /** The skill this species had before skills were rolled (monsters from older saves keep it). Hounds and wolves lead packs. */
    fun of(species: Species): Trait =
        byFamily[species.family] ?: if (species.breed != null) Trait.PACK_LEADER else Trait.SECOND_WIND

    /**
     * The skills each line can roll, with weights, picked to suit how it
     * looks and what type it is. Lines with Flight float about 60% of the
     * time (Airborne) but can roll something else.
     */
    private val pools: Map<String, List<Pair<Trait, Int>>> = mapOf(
        // Starters
        "Voltfawn line" to listOf(Trait.CRACKLE_SKIN to 4, Trait.LIGHTNING_EATER to 3, Trait.MOMENTUM to 2, Trait.LAST_STAND to 1),   // a charged stag
        "Cindrake line" to listOf(Trait.AIRBORNE to 6, Trait.MENACE to 2, Trait.KINDLING to 1, Trait.LAST_STAND to 1),     // winged dragons
        "Drizzlet line" to listOf(Trait.AIRBORNE to 6, Trait.REGENERATION to 1, Trait.SPRING_SOAK to 1, Trait.RAIN_RUNNER to 1, Trait.RAIN_DRINKER to 1, Trait.HARD_SCALES to 1), // sky serpents
        "Mosscub line" to listOf(Trait.THICK_HIDE to 4, Trait.GRIT to 2, Trait.STALWART to 2, Trait.LAST_STAND to 2, Trait.INSULATED to 2),          // bears
        // Four-stage lines
        "Emberkit line" to listOf(Trait.QUICK_STEP to 4, Trait.SUN_CHASER to 3, Trait.KINDLING to 2, Trait.LAST_STAND to 1),  // fire cats
        "Zapmouse line" to listOf(Trait.CRACKLE_SKIN to 5, Trait.LIGHTNING_EATER to 3, Trait.MOMENTUM to 1, Trait.SCAVENGER to 1),  // rodents
        "Fluffchick line" to listOf(Trait.AIRBORNE to 6, Trait.KEEN_SIGHT to 2, Trait.MOMENTUM to 2),                      // birds of prey
        "Bubblefin line" to listOf(Trait.BARBED_HIDE to 3, Trait.RAIN_RUNNER to 3, Trait.UNFLAPPABLE to 2, Trait.SPRING_SOAK to 2), // sharks: rough skin
        "Toxbug line" to listOf(Trait.AIRBORNE to 6, Trait.VENOM_BARBS to 2, Trait.KEEN_SIGHT to 1, Trait.MOMENTUM to 1),     // winged bugs
        "Gloomwisp line" to listOf(Trait.AIRBORNE to 6, Trait.OVERBEARING to 2, Trait.WAKEFUL to 2),                      // wisps and wraiths
        // Hounds lead packs, with a touch of their element
        "Shadow Hound line" to listOf(Trait.PACK_LEADER to 5, Trait.MENACE to 2, Trait.OVERBEARING to 1),
        // Three-stage lines
        "Goopling line" to listOf(Trait.REGENERATION to 5, Trait.STALWART to 2, Trait.VENOM_BARBS to 2),                 // slimes reform
        "Pebblit line" to listOf(Trait.EMBER_HIDE to 4, Trait.STALWART to 3, Trait.IRON_SKULL to 2),                      // molten rock
        "Shellsnap line" to listOf(Trait.THICK_HIDE to 3, Trait.STALWART to 3, Trait.PLATED to 3, Trait.SPRING_SOAK to 2),                   // tortoises
        "Twiglet line" to listOf(Trait.REGENERATION to 4, Trait.SUN_CHASER to 3, Trait.THICK_HIDE to 2, Trait.RAIN_DRINKER to 2),                  // trees love the sun
        "Impling line" to listOf(Trait.WAKEFUL to 3, Trait.KINDLING to 3, Trait.GRIT to 2, Trait.OVERBEARING to 1),          // fiends
        "Cinderskink line" to listOf(Trait.EMBER_HIDE to 4, Trait.KINDLING to 3, Trait.LAST_STAND to 2, Trait.SUN_CHASER to 1, Trait.HARD_SCALES to 2), // salamanders
        "Cinderlamb line" to listOf(Trait.THICK_HIDE to 3, Trait.IRON_SKULL to 3, Trait.INSULATED to 3, Trait.GRIT to 1),                       // rams: wool and horns
        "Joltlet line" to listOf(Trait.QUICK_STEP to 4, Trait.MOMENTUM to 3, Trait.LIGHTNING_EATER to 1),                 // monkeys
        "Zaplume line" to listOf(Trait.CRACKLE_SKIN to 4, Trait.LIGHTNING_EATER to 3, Trait.SPRING_SOAK to 2, Trait.AIRBORNE to 1), // drifting jellies
        "Puddlet line" to listOf(Trait.RAIN_RUNNER to 3, Trait.REGENERATION to 2, Trait.SPRING_SOAK to 2, Trait.RAIN_DRINKER to 2, Trait.VENOM_BARBS to 1), // frogs
        "Pincerling line" to listOf(Trait.PLATED to 3, Trait.THICK_HIDE to 2, Trait.STALWART to 2, Trait.UNFLAPPABLE to 2, Trait.SCAVENGER to 1, Trait.RAIN_RUNNER to 1), // crabs
        "Hootling line" to listOf(Trait.AIRBORNE to 6, Trait.WAKEFUL to 3, Trait.KEEN_SIGHT to 2),                      // owls
        "Pterix line" to listOf(Trait.AIRBORNE to 6, Trait.IRON_SKULL to 2, Trait.MENACE to 1, Trait.KEEN_SIGHT to 1),   // pterosaurs
        "Sporelet line" to listOf(Trait.SPORE_COAT to 4, Trait.VENOM_BARBS to 2, Trait.REGENERATION to 2, Trait.STALWART to 1),               // mushrooms
        "Stinkit line" to listOf(Trait.MENACE to 4, Trait.VENOM_BARBS to 2, Trait.GRIT to 1),                      // skunks
        "Hexling line" to listOf(Trait.VENOM_BARBS to 4, Trait.SNARE_AURA to 3, Trait.WAKEFUL to 1),                  // spiders spin webs
        "Nightling line" to listOf(Trait.AIRBORNE to 5, Trait.WAKEFUL to 2, Trait.OVERBEARING to 1),                   // bats
        "Rollpip line" to listOf(Trait.PLATED to 4, Trait.THICK_HIDE to 3, Trait.STALWART to 2, Trait.IRON_SKULL to 1),                    // armored pangolins
        "Stubhorn line" to listOf(Trait.IRON_SKULL to 4, Trait.MENACE to 3, Trait.GRIT to 2, Trait.STALWART to 1),      // charging rhinos
        // Mythics
        "Mimic" to listOf(Trait.SNARE_AURA to 3, Trait.PLATED to 2, Trait.THICK_HIDE to 2, Trait.SCAVENGER to 2),                          // a treasure chest that bites
        "Prism Fairy" to listOf(Trait.AIRBORNE to 6, Trait.FORTUNE to 2, Trait.KEEN_SIGHT to 1, Trait.SCAVENGER to 1),
        // Legendaries
        "Magma Behemoth" to listOf(Trait.SUNBRINGER to 3, Trait.EMBER_HIDE to 1, Trait.KINDLING to 1),
        "Thunder Titan" to listOf(Trait.STORMCALLER to 3, Trait.CRACKLE_SKIN to 1, Trait.LIGHTNING_EATER to 1),
        "Sky Serpent" to listOf(Trait.AIRBORNE to 8, Trait.OVERBEARING to 2),
        "Abyss Kraken" to listOf(Trait.STORMCALLER to 3, Trait.SNARE_AURA to 1, Trait.MENACE to 1),                 // tentacles hold on
        "Miasma Hydra" to listOf(Trait.SPORE_COAT to 3, Trait.REGENERATION to 2, Trait.OVERBEARING to 1),
        "Dread Lich" to listOf(Trait.OVERBEARING to 3, Trait.WAKEFUL to 3),
        "Worldroot Colossus" to listOf(Trait.SUNBRINGER to 3, Trait.STALWART to 1, Trait.THICK_HIDE to 1),
    )

    /** What [species]'s line can roll, with weights (a line with no pool keeps its fixed skill). */
    fun pool(species: Species): List<Pair<Trait, Int>> = pools[species.family] ?: listOf(of(species) to 1)

    /** Rolls a new monster's skill from its line's pool. */
    fun roll(species: Species, rng: kotlin.random.Random): Trait {
        val pool = pool(species)
        var r = rng.nextInt(pool.sumOf { it.second })
        for ((t, w) in pool) {
            r -= w
            if (r < 0) return t
        }
        return pool.last().first
    }
}

/** A monster's skill and its rank, or null below level 30. */
val Monster.trait: Pair<Trait, Int>?
    get() = skillRank(level, MONSTER_SKILL_LEVEL).takeIf { it > 0 }?.let { (skill ?: MonsterTraits.of(species)) to it }

// ---------------------------------------------------------------------------
// Weapon class bonus and proficiency
// ---------------------------------------------------------------------------

/** Battles with an unfamiliar weapon before its penalty is gone. */
const val PROFICIENCY_BATTLES = 10

/** Flat bonus to all stats for a weapon of your class: 2, 4, 6 or 8 by rarity. */
val GearRarity.classStatBonus: Int get() = 2 * (ordinal + 1)
