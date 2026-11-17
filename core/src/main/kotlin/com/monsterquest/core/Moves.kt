package com.monsterquest.core

/** Status conditions. Each one also makes the monster easier to tame. */
enum class Status(val label: String, val captureBonus: Double) {
    SLEEP("Asleep", 1.5),
    SHOCK("Shocked", 1.3),
    POISON("Poisoned", 1.2),
    BURN("Burned", 1.2),
    /** Cursed Frost gear: 5% of max HP each turn (none during a drought). */
    FROSTBITE("Frostbitten", 1.2),
}

enum class Weather(val label: String) {
    CLEAR("Clear"),
    /** Water and Electric moves +25%, Fire −15%. */
    RAIN("Rain"),
    /** Fire and Corrupt moves +25%, Water −15%. */
    DROUGHT("Drought");

    fun multiplier(type: Element): Double = when (this) {
        CLEAR -> 1.0
        RAIN -> when (type) {
            Element.WATER, Element.ELECTRIC -> 1.25
            Element.FIRE -> 0.85
            else -> 1.0
        }
        DROUGHT -> when (type) {
            Element.FIRE, Element.CORRUPT -> 1.25
            Element.WATER -> 0.85
            else -> 1.0
        }
    }
}

enum class BattleStat(val label: String) { ATTACK("Attack"), DEFENSE("Defense"), SPEED("Speed") }

/** What a move does besides (or instead of) damage. Chances are 0..1. */
sealed interface MoveEffect {
    /** Inflict a status for a random number of turns in [minTurns]..[maxTurns]. */
    data class Inflict(val status: Status, val chance: Double, val minTurns: Int, val maxTurns: Int) : MoveEffect
    data class LowerFoe(val stats: List<BattleStat>, val chance: Double) : MoveEffect
    data class SetWeather(val weather: Weather, val chance: Double) : MoveEffect
    /** The foe's next move has a 40% chance to miss. */
    data class FoeAccuracyDown(val chance: Double) : MoveEffect
    /** The foe's next move misses outright. */
    data class FoeMissesNext(val chance: Double) : MoveEffect
    /** The foe loses its next turn. */
    data class Flinch(val chance: Double) : MoveEffect
    /** Damage over time: [base] (scaled by the user's level) each turn for [turns] turns. */
    data class Scorch(val base: Int, val turns: Int, val verb: String = "is scorched") : MoveEffect
    /** Puts the foe to sleep; it also takes [base] (scaled) damage every turn it sleeps. */
    data class NightTerror(val chance: Double, val base: Int) : MoveEffect
    /** Blocks all damage until the user's next turn. Can't be used twice in a row. */
    data object Protect : MoveEffect
    /** The user must rest on its next turn. */
    data object Recharge : MoveEffect
    /** [chance] to take [base] (scaled) damage. */
    data class Recoil(val base: Int, val chance: Double) : MoveEffect
    /** The user's damage rises by [percent] for the rest of the battle (stacks). */
    data class PowerUp(val percent: Int) : MoveEffect
    /** The foe's Attack drops to 66.7% (×2/3) for the rest of the battle. Doesn't stack with Menace. */
    data object Scare : MoveEffect
    /** Slowest fighters move first for 3 rounds. */
    data object ReverseOrder : MoveEffect
    /** Turn 1: fly out of reach (only Sky Bolt can hit). Turn 2: strike. */
    data object TwoTurnDive : MoveEffect
    /** The user heals [percent]% of its max HP. */
    data class HealSelf(val percent: Int) : MoveEffect
    /** The user's own [stats] rise by [stages] (up to +4), at [chance]. */
    data class RaiseSelf(val stats: List<BattleStat>, val stages: Int, val chance: Double = 1.0) : MoveEffect
    /** The cost of a huge hit: the user's own [stats] fall by [stages] (down to −4). */
    data class LowerSelf(val stats: List<BattleStat>, val stages: Int) : MoveEffect
    /** The user takes [percent]% of the damage it dealt as recoil. */
    data class RecoilPct(val percent: Int) : MoveEffect
    /** Power falls with the user's HP: full power at full HP. */
    data object HpScaled : MoveEffect
    /** Strikes [min]..[max] times in a row (power is per hit). */
    data class MultiHit(val min: Int, val max: Int) : MoveEffect
    /** +25% chance of a critical hit. */
    data object HighCrit : MoveEffect
    /** Never misses, and can't be dodged. */
    data object SureHit : MoveEffect
    /** Cures the user's own status and lingering burns. */
    data object CureSelf : MoveEffect
    /** Strikes every foe at once (at 75% power each when there's more than one). */
    data object HitsAllFoes : MoveEffect
    /** Heals the user for [percent]% of the damage it deals. */
    data class LifeDrain(val percent: Int) : MoveEffect
    /** Goes through guards, Wave Guard and the foe's raised Defense. */
    data object Pierce : MoveEffect
    /**
     * Knockdown: an Earth move that reaches fliers anyway. Against a hovering
     * foe it hits at [groundedPower] and knocks it down for good, so Earth moves
     * hit it for the rest of the battle.
     */
    data class SmackDown(val groundedPower: Int) : MoveEffect
    /** Every ally (the user included) heals [percent]% of its max HP. */
    data class TeamHeal(val percent: Int) : MoveEffect
    /** Every ally's [stats] rise by [stages]. */
    data class TeamRaise(val stats: List<BattleStat>, val stages: Int) : MoveEffect
    /** Water's speed: the user moves first next round. */
    data object Swift : MoveEffect
    /** Power ×[multiplier] against a foe that already has a status (asleep, shocked, poisoned, burned...). */
    data class StatusBonus(val multiplier: Double) : MoveEffect
    /** Earth's weight: damage comes from the user's Defense (and its raised stages) instead of its Attack. */
    data object DefenseStrike : MoveEffect
}

/**
 * What a move is for, at a glance: groups the move list and sets how many
 * times it can be used in a battle ([pp]).
 */
enum class MoveRole(val label: String, val description: String, val pp: Int) {
    BASIC("Basic", "Light attacks, nothing special.", 12),
    RELIABLE("Reliable", "Solid damage with no drawback, or never misses.", 8),
    HIGH_RISK("High risk", "Huge hits with a cost: inaccurate, recoil, a turn of rest, or the user's own stats fall.", 4),
    STATUS("Status & traps", "Sleep, shock, poison, burn, damage over time and stat drops on the foe.", 3),
    SUPPORT("Support", "Guards, heals, weather and boosts for the user or the team.", 4),
}

data class Move(
    val id: String,
    val name: String,
    val type: Element,
    /** Base damage, before the user's Attack and the target's Defense. 0 = status move. */
    val power: Int,
    val accuracy: Int = 100,
    val effects: List<MoveEffect> = emptyList(),
    val description: String,
) {
    val isDamaging: Boolean get() = power > 0

    val role: MoveRole
        get() {
            val e = effects
            val toFoe = e.any {
                it is MoveEffect.Inflict || it is MoveEffect.NightTerror || it is MoveEffect.Scorch ||
                    it is MoveEffect.LowerFoe || it is MoveEffect.FoeAccuracyDown || it == MoveEffect.ReverseOrder
            }
            if (!isDamaging) return if (toFoe) MoveRole.STATUS else MoveRole.SUPPORT
            val risky = accuracy <= 85 || e.any {
                it == MoveEffect.Recharge || it is MoveEffect.RecoilPct || it is MoveEffect.Recoil ||
                    it is MoveEffect.LowerSelf || it == MoveEffect.HpScaled || it == MoveEffect.TwoTurnDive
            }
            // A weak hit that always afflicts or traps the foe is really a status move.
            val trap = e.any { (it is MoveEffect.Inflict && it.chance >= 1.0) || it is MoveEffect.Scorch || it is MoveEffect.NightTerror }
            return when {
                risky -> MoveRole.HIGH_RISK
                trap && power < 70 -> MoveRole.STATUS
                power < 60 -> MoveRole.BASIC
                else -> MoveRole.RELIABLE
            }
        }

    /** Uses per battle (PP). Each turn a fighter doesn't use it, it gets one back. */
    val maxPp: Int get() = role.pp

    /** Average damage power per turn, counting hits, crits and the move's costs (for loadouts and AI). */
    val expectedPower: Double
        get() {
            val hits = effects.filterIsInstance<MoveEffect.MultiHit>().firstOrNull()?.let { (it.min + it.max) / 2.0 } ?: 1.0
            val crit = if (MoveEffect.HighCrit in effects) 1.12 else 1.0
            val spread = if (MoveEffect.HitsAllFoes in effects) 1.15 else 1.0
            // What the move costs: a turn of rest or charging, recoil, the user's own stats falling.
            val turns = if (MoveEffect.Recharge in effects || MoveEffect.TwoTurnDive in effects) 0.6 else 1.0
            val recoil = 1 - (effects.filterIsInstance<MoveEffect.RecoilPct>().firstOrNull()?.percent ?: 0) / 200.0
            val drop = if (effects.any { it is MoveEffect.LowerSelf }) 0.85 else 1.0
            return power * hits * crit * spread * accuracy / 100.0 * turns * recoil * drop
        }
}

/**
 * Every move, and the level at which each type learns them. Every monster
 * learns its own type's moves plus the Normal moves. (No monster is Normal
 * type; Normal is only an attack type.)
 */
object Moves {
    /** Move ids renamed in 3.10 (save version 11), old to new; old saves are converted on load. */
    val renamedIds: Map<String, String> = mapOf(
        "tackle" to "shoulder_rush",
        "bite" to "fang_snap",
        "headbutt" to "skull_ram",
        "fury_swipes" to "frenzy_swipes",
        "slam" to "crushing_weight",
        "fire_fang" to "ember_fang",
        "flame_charge" to "blazing_dash",
        "lava_plume" to "lava_spray",
        "spark" to "crackle_jolt",
        "thunder_shock" to "jolt_strike",
        "thunder_wave" to "static_wave",
        "charge" to "storm_gather",
        "zap_cannon" to "arc_cannon",
        "splash" to "spray_blind",
        "aqua_jet" to "jet_dash",
        "soak" to "drench",
        "peck" to "beak_strike",
        "gust" to "gale_puff",
        "steel_wing" to "iron_wing",
        "aerial_ace" to "swoop_strike",
        "poison_gas" to "noxious_cloud",
        "gunk_shot" to "filth_shot",
        "purify" to "cleansing_breath",
        "snarl" to "feral_growl",
        "nightmare" to "night_terror",
        "shadow_claw" to "umbral_claw",
        "night_slash" to "dusk_slash",
        "rock_throw" to "stone_hurl",
        "vine_whip" to "thorn_lash",
        "earthquake" to "ground_rupture",
        "harden" to "stoneskin",
        "stone_edge" to "jagged_edge",
        "flare_blitz" to "wildfire_charge",
        "surf" to "tidal_crash",
        "sky_uppercut" to "rising_strike",
        "air_slash" to "wind_slash",
        "hurricane" to "cyclone_fury",
        "venoshock" to "venom_surge",
        "sludge_bomb" to "sludge_shot",
        "mud_slap" to "mud_fling",
        "acid_spray" to "caustic_mist",
    )

    private fun m(id: String, name: String, type: Element, power: Int, acc: Int = 100, desc: String, vararg fx: MoveEffect) =
        Move(id, name, type, power, acc, fx.toList(), desc)


    val all: List<Move> = listOf(
        // ---- Normal ----
        m("shoulder_rush", "Shoulder Rush", Element.NEUTRAL, 40, desc = "A full-body charge."),
        m("fang_snap", "Fang Snap", Element.NEUTRAL, 60, desc = "30% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.3))),
        m("scare", "Scare", Element.NEUTRAL, 50, desc = "A terrifying lunge: the foe's Attack drops to 66.7% for the battle (doesn't stack with Menace).", fx = *arrayOf(MoveEffect.Scare)),
        m("skull_ram", "Skull Ram", Element.NEUTRAL, 70, desc = "30% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.3))),
        m("sleep_wave", "Sleep Wave", Element.NEUTRAL, 0, 60, "A hypnotic wave: if it lands, the foe falls asleep.", MoveEffect.Inflict(Status.SLEEP, 1.0, 2, 4)),
        m("quick_strike", "Quick Strike", Element.NEUTRAL, 40, desc = "Never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("frenzy_swipes", "Frenzy Swipes", Element.NEUTRAL, 18, 80, "Swipes 2 to 5 times.", MoveEffect.MultiHit(2, 5)),
        m("focus", "Focus", Element.NEUTRAL, 0, desc = "Sharply raises Attack.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 2))),
        m("crushing_weight", "Crushing Weight", Element.NEUTRAL, 85, desc = "A crushing full-weight blow: 30% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),

        // ---- Fire ----
        m("spit_fire", "Spit Fire", Element.FIRE, 40, desc = "A gob of flame. 10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.1, 4, 4))),
        m("ember_fang", "Ember Fang", Element.FIRE, 65, 95, "10% chance to burn, 10% chance the foe loses its next turn.", MoveEffect.Inflict(Status.BURN, 0.1, 4, 4), MoveEffect.Flinch(0.1)),
        m("fire_wave", "Fire Wave", Element.FIRE, 60, desc = "Scorches for 5 damage a turn for 5 turns.", fx = *arrayOf(MoveEffect.Scorch(5, 5))),
        m("solar_flare", "Solar Flare", Element.FIRE, 0, desc = "Blinding light: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("drought", "Drought", Element.FIRE, 0, desc = "Dries up rain. Fire and Corrupt +25%, Water −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.DROUGHT, 1.0))),
        m("fire_storm", "Fire Storm", Element.FIRE, 100, 90, "A raging firestorm that sweeps every foe. 10% chance to burn each.", MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.BURN, 0.1, 4, 4)),
        m("blast_burn", "Inferno Blast", Element.FIRE, 150, 90, "Huge damage, but the user must rest next turn.", MoveEffect.Recharge),
        m("blazing_dash", "Blazing Dash", Element.FIRE, 50, desc = "Cloaked in flame: raises the user's Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 1))),
        m("ember_barrage", "Ember Barrage", Element.FIRE, 20, 85, "Spits 2 to 5 embers.", MoveEffect.MultiHit(2, 5)),
        m("heat_claws", "Heat Claws", Element.FIRE, 70, desc = "Red-hot claws: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("kindle", "Kindle", Element.FIRE, 0, desc = "Stokes its inner fire: cures its status and raises Attack.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 1))),
        m("lava_spray", "Lava Spray", Element.FIRE, 80, desc = "Lava sprays over every foe. 30% chance to burn each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.BURN, 0.3, 4, 4))),

        // ---- Electric ----
        m("crackle_jolt", "Crackle Jolt", Element.ELECTRIC, 40, desc = "A crackling jolt. 10% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.1, 4, 4))),
        m("shock", "Shock", Element.ELECTRIC, 25, desc = "A weak jolt that always shocks: 25% chance each turn the foe can't act.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4))),
        m("jolt_strike", "Jolt Strike", Element.ELECTRIC, 65, desc = "30% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),
        m("static_wave", "Static Wave", Element.ELECTRIC, 60, desc = "A wave of current through every foe. 15% chance to shock each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.SHOCK, 0.15, 4, 4))),
        m("thunder_bolt", "Sky Bolt", Element.ELECTRIC, 95, desc = "10% chance to shock. Hits fliers mid-dive.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.1, 4, 4))),
        m("volt_crash", "Volt Crash", Element.ELECTRIC, 120, desc = "A reckless charge. Takes a third of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.RecoilPct(33))),
        m("quick_bolt", "Quick Bolt", Element.ELECTRIC, 60, desc = "Lightning-fast: never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("spark_storm", "Spark Storm", Element.ELECTRIC, 20, 85, "Zaps 2 to 5 times.", MoveEffect.MultiHit(2, 5)),
        m("storm_gather", "Storm Gather", Element.ELECTRIC, 0, desc = "Builds up power: raises Attack and Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK, BattleStat.SPEED), 1))),
        m("volt_fang", "Volt Fang", Element.ELECTRIC, 65, 95, "10% chance to shock, 10% chance the foe loses its turn.", MoveEffect.Inflict(Status.SHOCK, 0.1, 4, 4), MoveEffect.Flinch(0.1)),
        m("arc_cannon", "Arc Cannon", Element.ELECTRIC, 100, 50, "Wildly inaccurate, but always shocks.", MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4)),

        // ---- Water ----
        m("spray_blind", "Spray Blind", Element.WATER, 20, desc = "Spray in the eyes: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("water_shot", "Water Shot", Element.WATER, 60, desc = "A pressurized jet that cuts through guards and raised Defense.", fx = *arrayOf(MoveEffect.Pierce)),
        m("wave_guard", "Wave Guard", Element.WATER, 0, desc = "Blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        // Every type has its own guard move, like Water's Wave Guard.
        m("flame_shield", "Flame Shield", Element.FIRE, 0, desc = "A wall of fire: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("static_barrier", "Static Barrier", Element.ELECTRIC, 0, desc = "A crackling barrier: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("wind_wall", "Wind Wall", Element.FLIGHT, 0, desc = "A spinning wall of wind: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("toxic_veil", "Toxic Veil", Element.GAS, 0, desc = "A thick veil of fumes: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("shadow_veil", "Shadow Veil", Element.CORRUPT, 0, desc = "Melts into the shadows: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("stone_wall", "Stone Wall", Element.EARTH, 0, desc = "Raises a wall of stone: blocks all damage until your next turn.", fx = *arrayOf(MoveEffect.Protect)),
        m("rain", "Rain", Element.WATER, 0, desc = "Summons rain. Water and Electric +25%, Fire −15%.", fx = *arrayOf(MoveEffect.SetWeather(Weather.RAIN, 1.0))),
        m("hydro_blast", "Hydro Blast", Element.WATER, 120, 80, "A crushing torrent. Huge, but it often misses."),
        m("hydro_surprise", "Hydro Surprise", Element.WATER, 90, 85, "Bursts up from below: high critical-hit chance.", MoveEffect.HighCrit),
        m("jet_dash", "Jet Dash", Element.WATER, 40, desc = "A darting jet that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("bubble_barrage", "Bubble Barrage", Element.WATER, 20, 85, "Fires 2 to 5 bubbles.", MoveEffect.MultiHit(2, 5)),
        m("drench", "Drench", Element.WATER, 0, desc = "Drenches the foe: its Attack and Speed fall.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.ATTACK, BattleStat.SPEED), 1.0))),
        m("tidal_slam", "Tidal Slam", Element.WATER, 80, desc = "Crashes down like a wave: 20% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.2))),
        m("cleansing_wave", "Cleansing Wave", Element.WATER, 0, desc = "Washes away its status and heals 15% of its HP.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.HealSelf(15))),

        // ---- Flight ----
        m("beak_strike", "Beak Strike", Element.FLIGHT, 40, desc = "A sharp jab."),
        m("gale_puff", "Gale Puff", Element.FLIGHT, 40, desc = "15% chance the foe misses its next attack.", fx = *arrayOf(MoveEffect.FoeMissesNext(0.15))),
        m("iron_wing", "Iron Wing", Element.FLIGHT, 40, desc = "Your damage rises 10% for the rest of the battle.", fx = *arrayOf(MoveEffect.PowerUp(10))),
        m("lullaby", "Lullaby", Element.FLIGHT, 0, 70, "Sings the foe to sleep for 3–5 turns.", MoveEffect.Inflict(Status.SLEEP, 1.0, 3, 5)),
        m("wind_tunnel", "Wind Tunnel", Element.FLIGHT, 55, 95, "Slicing winds hit every foe: high critical-hit chance.", MoveEffect.HitsAllFoes, MoveEffect.HighCrit),
        m("flying_kick", "Flying Kick", Element.FLIGHT, 90, 95, "A soaring kick. Takes a quarter of the damage dealt as recoil.", MoveEffect.RecoilPct(25)),
        m("wild_wind", "Wild Wind", Element.FLIGHT, 0, desc = "Slowest fighters move first for 3 rounds.", fx = *arrayOf(MoveEffect.ReverseOrder)),
        m("dive_bomb", "Dive Bomb", Element.FLIGHT, 90, 95, "Flies out of reach for a turn, then strikes. Sky Bolt can hit it mid-air.", MoveEffect.TwoTurnDive),
        m("swoop_strike", "Swoop Strike", Element.FLIGHT, 60, desc = "A swooping strike that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("feather_flurry", "Feather Flurry", Element.FLIGHT, 18, 85, "Flings 2 to 5 razor feathers.", MoveEffect.MultiHit(2, 5)),
        m("tailwind", "Tailwind", Element.FLIGHT, 0, desc = "Rides the wind: sharply raises Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 2))),
        m("razor_gale", "Razor Gale", Element.FLIGHT, 70, desc = "Cutting winds: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("roost", "Roost", Element.FLIGHT, 0, desc = "Lands to rest and heals 30% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(30))),

        // ---- Gas ----
        m("puff", "Puff", Element.GAS, 40, desc = "A burst of foul air: 20% chance the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(0.2))),
        m("noxious_cloud", "Noxious Cloud", Element.GAS, 0, 90, "Poisons the foe: 8% of its HP each turn.", MoveEffect.Inflict(Status.POISON, 1.0, 5, 5)),
        m("filth_shot", "Filth Shot", Element.GAS, 120, 70, "A heavy glob of filth: huge but inaccurate. 30% chance to poison.", MoveEffect.Inflict(Status.POISON, 0.3, 5, 5)),
        m("toxic_wave", "Toxic Wave", Element.GAS, 60, desc = "Fumes roll over every foe. 15% chance to poison each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.POISON, 0.15, 5, 5))),
        m("smog_screen", "Smog Screen", Element.GAS, 0, desc = "Thick smog: the foe's next move may miss and its Speed drops.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0), MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 1.0))),
        m("acid_rain", "Acid Rain", Element.GAS, 90, desc = "20% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.2))),
        m("miasma_burst", "Miasma Burst", Element.GAS, 110, 85, "An eruption of toxic gas. 20% chance to poison.", MoveEffect.Inflict(Status.POISON, 0.2, 5, 5)),
        m("toxic_barrage", "Toxic Barrage", Element.GAS, 18, 85, "Spits 2 to 5 globs of venom.", MoveEffect.MultiHit(2, 5)),
        m("venom_fang", "Venom Fang", Element.GAS, 50, desc = "30% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("spore_cloud", "Spore Cloud", Element.GAS, 0, 80, "Drowsy spores: puts the foe to sleep.", MoveEffect.Inflict(Status.SLEEP, 1.0, 2, 4)),
        m("corrode", "Corrode", Element.GAS, 75, 95, "Eats away at armor: 50% chance to lower Defense.", MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.5)),
        m("cleansing_breath", "Cleansing Breath", Element.GAS, 0, desc = "Filters out toxins: cures its status and raises Defense.", fx = *arrayOf(MoveEffect.CureSelf, MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1))),

        // ---- Corrupt ----
        m("feral_growl", "Feral Growl", Element.CORRUPT, 40, desc = "Lowers the foe's Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 1.0))),
        m("dark_wave", "Dark Wave", Element.CORRUPT, 60, desc = "Darkness washes over every foe. 20% chance to lower Speed.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.2))),
        m("corruptive_bite", "Corruptive Bite", Element.CORRUPT, 60, desc = "15% chance to lower Speed, Defense and Attack.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED, BattleStat.DEFENSE, BattleStat.ATTACK), 0.15))),
        m("night_terror", "Night Terror", Element.CORRUPT, 0, desc = "80% chance of sleep; 20 damage every turn asleep.", fx = *arrayOf(MoveEffect.NightTerror(0.8, 20))),
        m("umbral_claw", "Umbral Claw", Element.CORRUPT, 70, desc = "Claws from the dark that slip past guards and raised Defense.", fx = *arrayOf(MoveEffect.Pierce)),
        m("void_pulse", "Void Pulse", Element.CORRUPT, 110, 85, "A pulse from the void."),
        m("sucker_punch", "Cheap Shot", Element.CORRUPT, 60, desc = "A dirty blow that never misses.", fx = *arrayOf(MoveEffect.SureHit)),
        m("hex_barrage", "Hex Barrage", Element.CORRUPT, 20, 85, "Casts 2 to 5 small hexes.", MoveEffect.MultiHit(2, 5)),
        m("dusk_slash", "Dusk Slash", Element.CORRUPT, 70, desc = "A slash from the shadows: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("dark_pact", "Dark Pact", Element.CORRUPT, 0, desc = "Trades life for power: sharply raises Attack, costs HP.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 2), MoveEffect.Recoil(15, 1.0))),
        m("soul_drain", "Soul Drain", Element.CORRUPT, 70, desc = "Drains the foe's spirit and heals 15% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(15))),

        // ---- Earth ----
        m("seed_bomb", "Seed Burst", Element.EARTH, 40, desc = "A burst of hard seeds."),
        m("mulch", "Mulch", Element.EARTH, 0, desc = "Burrows into rich soil and heals 10% of its HP.", fx = *arrayOf(MoveEffect.HealSelf(10))),
        m("sap", "Sap", Element.EARTH, 40, desc = "Drains the foe and heals half the damage dealt.", fx = *arrayOf(MoveEffect.LifeDrain(50))),
        m("stone_hurl", "Stone Hurl", Element.EARTH, 50, 90, "Hurls a heavy stone: 30% chance to lower Defense.", MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.3)),
        m("mega_sap", "Mega Sap", Element.EARTH, 60, desc = "Drains the foe and heals half the damage dealt.", fx = *arrayOf(MoveEffect.LifeDrain(50))),
        m("thorn_lash", "Thorn Lash", Element.EARTH, 45, desc = "Lashes with thorny vines: 25% chance to lower Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.25))),
        m("ground_rupture", "Ground Rupture", Element.EARTH, 100, desc = "The ground splits beneath every foe.", fx = *arrayOf(MoveEffect.HitsAllFoes)),
        m("leaf_storm", "Leaf Tempest", Element.EARTH, 130, 90, "A storm of razor leaves: the user's Attack harshly falls.", MoveEffect.LowerSelf(listOf(BattleStat.ATTACK), 2)),
        m("rock_blast", "Stone Volley", Element.EARTH, 25, 80, "Hurls 2 to 5 stones.", MoveEffect.MultiHit(2, 5)),
        m("stoneskin", "Stoneskin", Element.EARTH, 0, desc = "Turns its hide to stone: sharply raises Defense.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 2))),
        m("thorn_trap", "Thorn Trap", Element.EARTH, 0, desc = "Thorns that cut 6 damage a turn for 5 turns.", fx = *arrayOf(MoveEffect.Scorch(6, 5))),
        m("ground_pound", "Ground Pound", Element.EARTH, 65, desc = "20% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.2))),
        m("jagged_edge", "Jagged Edge", Element.EARTH, 100, 80, "Jagged stones: high critical-hit chance.", MoveEffect.HighCrit),
        m("smack_down", "Knockdown", Element.EARTH, 65, desc = "Hurls a rock that reaches fliers. Against a hovering foe: 85 power, and it's knocked down so Earth moves hit it for the rest of the battle.", fx = *arrayOf(MoveEffect.SmackDown(85))),

        // ---- More attacks: 8 per type, each type with its own strength ----
        // Fire: raw damage, often at a cost.
        m("cinder_jab", "Cinder Jab", Element.FIRE, 60, desc = "A searing jab. 10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.1, 4, 4))),
        m("flare_rush", "Flare Rush", Element.FIRE, 80, desc = "A burning tackle. Takes a quarter of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.RecoilPct(25))),
        m("searing_blade", "Searing Blade", Element.FIRE, 70, desc = "A white-hot edge that cuts through guards and raised Defense.", fx = *arrayOf(MoveEffect.Pierce)),
        m("magma_fist", "Magma Fist", Element.FIRE, 75, desc = "A fist of molten rock. 10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.1, 4, 4))),
        m("wildfire_charge", "Wildfire Charge", Element.FIRE, 120, desc = "An all-out blazing charge. Takes a third of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.RecoilPct(33))),
        m("meteor_flame", "Meteor Flame", Element.FIRE, 120, 85, "Burning rocks crash down: huge, but it can miss. 10% chance to burn.", MoveEffect.Inflict(Status.BURN, 0.1, 4, 4)),
        m("sunfire", "Sunfire", Element.FIRE, 100, 95, "A beam of sunlight. 50% chance of a drought.", MoveEffect.SetWeather(Weather.DROUGHT, 0.5)),
        m("supernova", "Supernova", Element.FIRE, 150, desc = "Explodes over every foe. Weaker as the user's HP falls.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.HpScaled)),

        // Electric: status first. Shocks, and punishes foes that are already hurting.
        m("static_jolt", "Static Jolt", Element.ELECTRIC, 35, desc = "30% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),
        m("galvanize", "Galvanize", Element.ELECTRIC, 50, desc = "Double power against a foe that has a status.", fx = *arrayOf(MoveEffect.StatusBonus(2.0))),
        m("charge_net", "Charge Net", Element.ELECTRIC, 45, 85, "A crackling net that always shocks.", MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4)),
        m("ion_storm", "Ion Storm", Element.ELECTRIC, 80, desc = "Charged air around every foe. 30% chance to shock each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),
        m("volt_snare", "Volt Snare", Element.ELECTRIC, 75, desc = "30% chance to shock, 20% chance to lower Speed.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4), MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.2))),
        m("plasma_lance", "Plasma Lance", Element.ELECTRIC, 80, desc = "Pierces guards and raised Defense. 10% chance to shock.", fx = *arrayOf(MoveEffect.Pierce, MoveEffect.Inflict(Status.SHOCK, 0.1, 4, 4))),
        m("thunderclap", "Sky Crack", Element.ELECTRIC, 120, 70, "A deafening thunderclap: huge but inaccurate. 30% chance to shock.", MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4)),
        m("judgment_bolt", "Judgment Bolt", Element.ELECTRIC, 100, 90, "1.5x power against a foe with a status. 30% chance to shock.", MoveEffect.StatusBonus(1.5), MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4)),

        // Water: balanced and fast. Many move first next round.
        m("ripple_dart", "Ripple Dart", Element.WATER, 40, desc = "A quick dart of water: the user moves first next round.", fx = *arrayOf(MoveEffect.Swift)),
        m("rapid_current", "Rapid Current", Element.WATER, 50, desc = "Rides the current: raises the user's Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 1))),
        m("tidal_crash", "Tidal Crash", Element.WATER, 90, desc = "A wave that hits every foe.", fx = *arrayOf(MoveEffect.HitsAllFoes)),
        m("riptide", "Riptide", Element.WATER, 60, desc = "Never misses, and the user moves first next round.", fx = *arrayOf(MoveEffect.SureHit, MoveEffect.Swift)),
        m("undertow", "Undertow", Element.WATER, 75, desc = "Drags the foe under: 30% chance to lower its Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.3))),
        m("torrent_rush", "Torrent Rush", Element.WATER, 80, desc = "A rushing torrent: the user moves first next round.", fx = *arrayOf(MoveEffect.Swift)),
        m("tidal_wave", "Tidal Wave", Element.WATER, 95, 85, "A muddy wave over every foe. 30% chance each one's next move may miss.", MoveEffect.HitsAllFoes, MoveEffect.FoeAccuracyDown(0.3)),
        m("tsunami", "Tsunami", Element.WATER, 150, desc = "The sea itself strikes every foe. Weaker as the user's HP falls.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.HpScaled)),

        // Flight: balanced attacks that power the user up.
        m("wing_flick", "Wing Flick", Element.FLIGHT, 40, desc = "A light flick of the wings: raises the user's Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.SPEED), 1))),
        m("rising_strike", "Rising Strike", Element.FLIGHT, 60, desc = "A rising blow: 50% chance to raise the user's Attack.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK), 1, 0.5))),
        m("updraft", "Updraft", Element.FLIGHT, 70, 90, "Strikes and rides the updraft: 50% chance to raise the user's Defense.", MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1, 0.5)),
        m("wind_slash", "Wind Slash", Element.FLIGHT, 75, 95, "A blade of air. 30% chance the foe loses its next turn.", MoveEffect.Flinch(0.3)),
        m("soaring_strike", "Soaring Strike", Element.FLIGHT, 75, desc = "The user's damage rises 10% for the rest of the battle.", fx = *arrayOf(MoveEffect.PowerUp(10))),
        m("ascent", "Ascent", Element.FLIGHT, 80, desc = "Climbs high and dives: 40% chance to raise the user's Attack and Speed.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.ATTACK, BattleStat.SPEED), 1, 0.4))),
        m("cyclone_fury", "Cyclone Fury", Element.FLIGHT, 110, 70, "A violent storm: often misses. 30% chance the foe misses its next attack.", MoveEffect.FoeMissesNext(0.3)),
        m("talon_storm", "Talon Storm", Element.FLIGHT, 100, 95, "A flurry of talons: high critical-hit chance.", MoveEffect.HighCrit),

        // Gas: status first. Poison and sleep, and punishes foes that are already hurting.
        m("noxious_spit", "Noxious Spit", Element.GAS, 35, desc = "30% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("venom_surge", "Venom Surge", Element.GAS, 55, desc = "Double power against a foe that has a status.", fx = *arrayOf(MoveEffect.StatusBonus(2.0))),
        m("drowsy_mist", "Drowsy Mist", Element.GAS, 45, 85, "Sleepy fumes. 40% chance the foe falls asleep.", MoveEffect.Inflict(Status.SLEEP, 0.4, 2, 3)),
        m("toxic_cloud", "Toxic Cloud", Element.GAS, 65, desc = "A cloud over every foe. 30% chance to poison each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("sludge_shot", "Sludge Shot", Element.GAS, 90, desc = "30% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("plague_fang", "Plague Fang", Element.GAS, 75, desc = "30% chance to poison, and heals a quarter of the damage dealt.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5), MoveEffect.LifeDrain(25))),
        m("caustic_burst", "Caustic Burst", Element.GAS, 85, desc = "1.5x power against a foe with a status. 30% chance to lower Defense.", fx = *arrayOf(MoveEffect.StatusBonus(1.5), MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.3))),
        m("pandemic", "Pandemic", Element.GAS, 95, 90, "A plague over every foe. 30% chance to poison each.", MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.POISON, 0.3, 5, 5)),

        // Corrupt: dirty fighting that tears down the foe's stats.
        m("low_blow", "Low Blow", Element.CORRUPT, 40, desc = "A dirty kick that always lowers Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 1.0))),
        m("spiteful_jab", "Spiteful Jab", Element.CORRUPT, 50, desc = "50% chance to lower Attack.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.ATTACK), 0.5))),
        m("eye_gouge", "Eye Gouge", Element.CORRUPT, 55, desc = "Goes for the eyes: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("backstab", "Backstab", Element.CORRUPT, 60, desc = "Strikes from behind: pierces guards, high critical-hit chance.", fx = *arrayOf(MoveEffect.Pierce, MoveEffect.HighCrit)),
        m("rend", "Rend", Element.CORRUPT, 70, desc = "Tears at armor: always lowers Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 1.0))),
        m("dread_howl", "Dread Howl", Element.CORRUPT, 65, desc = "A howl that chills every foe. 50% chance to lower each one's Attack.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.LowerFoe(listOf(BattleStat.ATTACK), 0.5))),
        m("hollowing_strike", "Hollowing Strike", Element.CORRUPT, 80, desc = "30% chance to lower Attack and Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.ATTACK, BattleStat.DEFENSE), 0.3))),
        // Corrupt is dark power too, not only dirty tricks.
        m("life_absorb", "Life Absorb", Element.CORRUPT, 80, desc = "Dark power pulls the life out of the foe: heals the user for 25% of the damage dealt.", fx = *arrayOf(MoveEffect.LifeDrain(25))),
        m("doomfall", "Doomfall", Element.CORRUPT, 110, 85, "Darkness falls: huge, but it can miss. 35% chance to lower Attack, Defense and Speed.", MoveEffect.LowerFoe(listOf(BattleStat.ATTACK, BattleStat.DEFENSE, BattleStat.SPEED), 0.35)),

        // Earth: steady damage built on Defense.
        m("stone_knuckle", "Stone Knuckle", Element.EARTH, 45, desc = "A rocky punch: raises the user's Defense.", fx = *arrayOf(MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1))),
        m("bedrock_press", "Bedrock Press", Element.EARTH, 60, desc = "Presses down with its whole weight: damage comes from the user's Defense, not Attack.", fx = *arrayOf(MoveEffect.DefenseStrike)),
        m("mud_fling", "Mud Fling", Element.EARTH, 30, desc = "Mud in the eyes: the foe's next move may miss.", fx = *arrayOf(MoveEffect.FoeAccuracyDown(1.0))),
        m("shield_bash", "Shield Bash", Element.EARTH, 65, desc = "Uses the user's Defense for damage, then raises it.", fx = *arrayOf(MoveEffect.DefenseStrike, MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1))),
        m("rockslide", "Landslide", Element.EARTH, 75, 90, "Rocks tumble onto every foe. 30% chance each loses its turn.", MoveEffect.HitsAllFoes, MoveEffect.Flinch(0.3)),
        m("iron_root", "Iron Root", Element.EARTH, 70, desc = "Roots dig in: heals 10% of its HP and raises Defense.", fx = *arrayOf(MoveEffect.HealSelf(10), MoveEffect.RaiseSelf(listOf(BattleStat.DEFENSE), 1))),
        m("mountain_crush", "Mountain Crush", Element.EARTH, 100, 90, "Brings a mountain down: damage comes from the user's Defense.", MoveEffect.DefenseStrike),
        m("titan_slam", "Titan Slam", Element.EARTH, 120, desc = "A titanic slam: the user's Attack and Defense fall.", fx = *arrayOf(MoveEffect.LowerSelf(listOf(BattleStat.ATTACK, BattleStat.DEFENSE), 1))),

        // ---- Starter signature moves: only the four starter lines learn these ----
        m("storm_antlers", "Storm Antlers", Element.ELECTRIC, 85, desc = "Lightning leaps from its antlers to every foe. 30% chance to shock each.", fx = *arrayOf(MoveEffect.HitsAllFoes, MoveEffect.Inflict(Status.SHOCK, 0.3, 4, 4))),
        m("heavens_charge", "Heaven's Charge", Element.ELECTRIC, 120, desc = "A divine charge that pierces guards and raised Defense. Takes a third of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.Pierce, MoveEffect.RecoilPct(33))),
        m("wyrmfire", "Wyrmfire", Element.FIRE, 100, 95, "Sacred dragonfire. 50% chance to burn.", MoveEffect.Inflict(Status.BURN, 0.5, 4, 4)),
        m("inferno_wings", "Inferno Wings", Element.FIRE, 140, 90, "Soars up on burning wings, then dives. 30% chance the foe loses its turn.", MoveEffect.TwoTurnDive, MoveEffect.Flinch(0.3)),
        m("tidal_embrace", "Tidal Embrace", Element.WATER, 70, desc = "Wraps the foe in the tide and heals half the damage dealt.", fx = *arrayOf(MoveEffect.LifeDrain(50))),
        m("maelstrom_vortex", "Maelstrom Vortex", Element.WATER, 150, 90, "A crushing whirlpool that brings rain. The user must rest next turn.", MoveEffect.SetWeather(Weather.RAIN, 1.0), MoveEffect.Recharge),
        m("grove_guardian", "Grove Guardian", Element.EARTH, 0, desc = "The ancient grove shelters the team: every ally heals 20% and gains Defense.", fx = *arrayOf(MoveEffect.TeamHeal(20), MoveEffect.TeamRaise(listOf(BattleStat.DEFENSE), 1))),
        m("tectonic_fury", "Tectonic Fury", Element.EARTH, 150, 90, "The earth erupts under the foe. The user must rest next turn.", MoveEffect.Recharge),
        // ---- Inspired by the classics: more moves on the same power-and-cost scale ----
        m("star_volley", "Star Volley", Element.NEUTRAL, 60, desc = "Star-shaped rays that never miss.", fx = *arrayOf(MoveEffect.SureHit)),
        m("claw_slash", "Claw Slash", Element.NEUTRAL, 70, desc = "A raking slash: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
        m("reckless_charge", "Reckless Charge", Element.NEUTRAL, 90, 85, "A wild charge. Takes a quarter of the damage dealt as recoil.", MoveEffect.RecoilPct(25)),
        m("crushing_fang", "Crushing Fang", Element.NEUTRAL, 80, 90, "Huge fangs clamp down. 10% chance the foe loses its next turn.", MoveEffect.Flinch(0.1)),
        m("all_out_charge", "All-Out Charge", Element.NEUTRAL, 120, desc = "Everything into one hit. Takes a third of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.RecoilPct(33))),
        m("overdrive_beam", "Overdrive Beam", Element.NEUTRAL, 150, 90, "A blinding beam of raw power. The user must rest next turn.", MoveEffect.Recharge),
        m("wisp_flame", "Wisp Flame", Element.FIRE, 0, 75, "Ghostly flames that burn the foe.", MoveEffect.Inflict(Status.BURN, 1.0, 4, 4)),
        m("flame_torrent", "Flame Torrent", Element.FIRE, 95, desc = "A steady torrent of flame. 10% chance to burn.", fx = *arrayOf(MoveEffect.Inflict(Status.BURN, 0.1, 4, 4))),
        m("meltdown", "Meltdown", Element.FIRE, 140, 90, "Burns everything at once: the user's Attack harshly falls.", MoveEffect.LowerSelf(listOf(BattleStat.ATTACK), 2)),
        m("static_field", "Static Field", Element.ELECTRIC, 0, desc = "A field of static that shocks the foe.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4))),
        m("volt_punch", "Volt Punch", Element.ELECTRIC, 75, desc = "A crackling punch. 10% chance to shock.", fx = *arrayOf(MoveEffect.Inflict(Status.SHOCK, 0.1, 4, 4))),
        m("bubble_jet", "Bubble Jet", Element.WATER, 65, desc = "A jet of bubbles. 10% chance to lower Speed.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 0.1))),
        m("pulse_ring", "Pulse Ring", Element.WATER, 60, desc = "A pulsing ring of water. 20% chance the foe misses its next attack.", fx = *arrayOf(MoveEffect.FoeMissesNext(0.2))),
        m("wing_strike", "Wing Strike", Element.FLIGHT, 60, desc = "A strong beat of the wings."),
        m("reckless_dive", "Reckless Dive", Element.FLIGHT, 120, desc = "A headlong dive. Takes a third of the damage dealt as recoil.", fx = *arrayOf(MoveEffect.RecoilPct(33))),
        m("sky_strike", "Sky Strike", Element.FLIGHT, 140, 90, "Climbs out of reach, then strikes. 30% chance the foe loses its turn.", MoveEffect.TwoTurnDive, MoveEffect.Flinch(0.3)),
        m("caustic_mist", "Caustic Mist", Element.GAS, 40, desc = "10% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.1))),
        m("numbing_spores", "Numbing Spores", Element.GAS, 0, 75, "Spores that shock the foe.", MoveEffect.Inflict(Status.SHOCK, 1.0, 4, 4)),
        m("ooze_shot", "Ooze Shot", Element.GAS, 65, desc = "30% chance to poison.", fx = *arrayOf(MoveEffect.Inflict(Status.POISON, 0.3, 5, 5))),
        m("gloom_orb", "Gloom Orb", Element.CORRUPT, 80, desc = "An orb of gloom. 20% chance to lower Defense.", fx = *arrayOf(MoveEffect.LowerFoe(listOf(BattleStat.DEFENSE), 0.2))),
        m("shadow_pulse", "Shadow Pulse", Element.CORRUPT, 80, desc = "A pulse of dark energy. 20% chance the foe loses its next turn.", fx = *arrayOf(MoveEffect.Flinch(0.2))),
        m("razor_leaves", "Razor Leaves", Element.EARTH, 55, 95, "Leaves slice every foe: high critical-hit chance.", MoveEffect.HitsAllFoes, MoveEffect.HighCrit),
        m("boulder_trap", "Boulder Trap", Element.EARTH, 50, 80, "Boulders pin the foe down: always lowers its Speed.", MoveEffect.LowerFoe(listOf(BattleStat.SPEED), 1.0)),
        m("verdant_blade", "Verdant Blade", Element.EARTH, 90, desc = "A leaf sharp as a blade: high critical-hit chance.", fx = *arrayOf(MoveEffect.HighCrit)),
    )

    /** When every move is out of uses: a weak, desperate hit with recoil. */
    val STRUGGLE = Move("struggle", "Struggle", Element.NEUTRAL, 50, 100, listOf(MoveEffect.SureHit, MoveEffect.RecoilPct(25)), "Out of moves: a desperate struggle that hurts the user too.")

    private val byId = all.associateBy { it.id }

    fun get(id: String): Move = byId.getValue(id)
    fun find(id: String): Move? = byId[id]

    /** (level, move id) pairs, per attack type. */
    val learnsets: Map<Element, List<Pair<Int, String>>> = mapOf(
        Element.NEUTRAL to listOf(1 to "shoulder_rush", 3 to "quick_strike", 5 to "fang_snap", 9 to "scare", 12 to "frenzy_swipes", 20 to "focus", 24 to "skull_ram", 29 to "crushing_weight", 34 to "sleep_wave", 14 to "star_volley", 16 to "claw_slash", 18 to "reckless_charge", 22 to "crushing_fang", 38 to "all_out_charge", 45 to "overdrive_beam"),
        Element.FIRE to listOf(1 to "spit_fire", 4 to "blazing_dash", 7 to "ember_fang", 11 to "ember_barrage", 12 to "flame_shield", 13 to "fire_wave", 16 to "heat_claws", 18 to "solar_flare", 22 to "drought", 25 to "kindle", 30 to "fire_storm", 34 to "lava_spray", 40 to "blast_burn", 6 to "cinder_jab", 14 to "flare_rush", 21 to "searing_blade", 29 to "magma_fist", 36 to "wildfire_charge", 44 to "meteor_flame", 52 to "sunfire", 60 to "supernova", 18 to "wisp_flame", 32 to "flame_torrent", 48 to "meltdown"),
        Element.ELECTRIC to listOf(1 to "crackle_jolt", 3 to "quick_bolt", 5 to "shock", 9 to "jolt_strike", 12 to "spark_storm", 12 to "static_barrier", 16 to "static_wave", 20 to "storm_gather", 22 to "volt_fang", 26 to "thunder_bolt", 38 to "volt_crash", 44 to "arc_cannon", 6 to "static_jolt", 14 to "galvanize", 21 to "charge_net", 29 to "ion_storm", 36 to "volt_snare", 44 to "plasma_lance", 52 to "thunderclap", 60 to "judgment_bolt", 10 to "static_field", 18 to "volt_punch"),
        Element.WATER to listOf(1 to "spray_blind", 4 to "jet_dash", 8 to "water_shot", 10 to "bubble_barrage", 12 to "wave_guard", 15 to "drench", 18 to "rain", 24 to "tidal_slam", 28 to "hydro_blast", 32 to "cleansing_wave", 36 to "hydro_surprise", 6 to "ripple_dart", 14 to "rapid_current", 21 to "tidal_crash", 29 to "riptide", 36 to "undertow", 44 to "torrent_rush", 52 to "tidal_wave", 60 to "tsunami", 12 to "bubble_jet", 16 to "pulse_ring"),
        Element.FLIGHT to listOf(1 to "beak_strike", 4 to "swoop_strike", 7 to "gale_puff", 10 to "feather_flurry", 12 to "iron_wing", 12 to "wind_wall", 15 to "tailwind", 17 to "lullaby", 22 to "wind_tunnel", 25 to "razor_gale", 27 to "flying_kick", 30 to "roost", 32 to "wild_wind", 38 to "dive_bomb", 6 to "wing_flick", 14 to "rising_strike", 21 to "updraft", 29 to "wind_slash", 36 to "soaring_strike", 44 to "ascent", 52 to "cyclone_fury", 60 to "talon_storm", 9 to "wing_strike", 48 to "reckless_dive", 56 to "sky_strike"),
        Element.GAS to listOf(1 to "puff", 3 to "toxic_barrage", 6 to "noxious_cloud", 9 to "venom_fang", 12 to "toxic_veil", 14 to "spore_cloud", 17 to "toxic_wave", 20 to "corrode", 23 to "smog_screen", 25 to "filth_shot", 27 to "cleansing_breath", 30 to "acid_rain", 38 to "miasma_burst", 6 to "noxious_spit", 14 to "venom_surge", 21 to "drowsy_mist", 29 to "toxic_cloud", 36 to "sludge_shot", 44 to "plague_fang", 52 to "caustic_burst", 60 to "pandemic", 4 to "caustic_mist", 11 to "numbing_spores", 16 to "ooze_shot"),
        Element.CORRUPT to listOf(1 to "feral_growl", 4 to "sucker_punch", 8 to "hex_barrage", 10 to "dark_wave", 12 to "shadow_veil", 16 to "corruptive_bite", 19 to "dusk_slash", 24 to "night_terror", 27 to "dark_pact", 30 to "umbral_claw", 33 to "soul_drain", 38 to "void_pulse", 6 to "low_blow", 14 to "spiteful_jab", 21 to "eye_gouge", 29 to "backstab", 36 to "rend", 44 to "dread_howl", 48 to "life_absorb", 52 to "hollowing_strike", 60 to "doomfall", 26 to "gloom_orb", 32 to "shadow_pulse"),
        Element.EARTH to listOf(1 to "seed_bomb", 3 to "rock_blast", 6 to "mulch", 10 to "sap", 12 to "stoneskin", 12 to "stone_wall", 15 to "stone_hurl", 18 to "thorn_trap", 22 to "mega_sap", 25 to "ground_pound", 8 to "thorn_lash", 34 to "ground_rupture", 38 to "jagged_edge", 40 to "leaf_storm", 6 to "stone_knuckle", 14 to "bedrock_press", 21 to "mud_fling", 29 to "shield_bash", 36 to "rockslide", 44 to "iron_root", 52 to "mountain_crush", 60 to "titan_slam", 17 to "razor_leaves", 19 to "boulder_trap", 30 to "verdant_blade"),
    )

    /**
     * Signature moves: each evolution line also learns a few moves of other
     * types that suit the creature (a drake's wings, a shark's bite, a
     * hound's elemental fangs), so lines of the same type fight differently.
     */
    val familyMoves: Map<String, List<Pair<Int, String>>> = mapOf(
        "Voltfawn" to listOf(18 to "gale_puff", 22 to "storm_antlers", 30 to "ground_pound", 40 to "heavens_charge"),
        "Cindrake" to listOf(18 to "iron_wing", 22 to "wyrmfire", 30 to "razor_gale", 40 to "inferno_wings"),
        "Drizzlet" to listOf(18 to "venom_fang", 22 to "tidal_embrace", 34 to "thunder_bolt", 40 to "maelstrom_vortex"),
        "Mosscub" to listOf(20 to "dusk_slash", 22 to "grove_guardian", 32 to "tidal_slam", 40 to "tectonic_fury"),
        "Emberkit" to listOf(18 to "dusk_slash", 40 to "razor_gale"),
        "Zapmouse" to listOf(12 to "sucker_punch", 24 to "ground_pound"),
        "Fluffchick" to listOf(20 to "dusk_slash", 36 to "thunder_bolt"),
        "Bubblefin" to listOf(18 to "dusk_slash", 30 to "volt_fang"),
        "Toxbug" to listOf(20 to "dusk_slash", 30 to "razor_gale"),
        "Gloomwisp" to listOf(14 to "tailwind", 22 to "fire_wave"),
        "Hound" to listOf(14 to "ember_fang", 22 to "volt_fang", 30 to "venom_fang"),
        "Goopling" to listOf(12 to "bubble_barrage", 26 to "soul_drain"),
        "Pebblit" to listOf(8 to "rock_blast", 34 to "jagged_edge"),
        "Shellsnap" to listOf(10 to "stoneskin", 24 to "ground_pound"),
        "Twiglet" to listOf(12 to "spore_cloud", 26 to "cleansing_wave"),
        "Impling" to listOf(10 to "ember_barrage", 30 to "lava_spray"),
        "Mimic" to listOf(8 to "frenzy_swipes", 20 to "jagged_edge"),
        "Prism Fairy" to listOf(8 to "cleansing_wave", 20 to "quick_bolt"),
    )

    /** Knockdown: Earth monsters learn it at 20, almost everyone else at 24. */
    const val SMACK_DOWN_EARTH_LEVEL = 20
    const val SMACK_DOWN_LEVEL = 24

    /**
     * Who can't learn Knockdown: creatures with nothing to throw a rock
     * with, or that live in the water or the air: fish, slimes, wisps and
     * the fairy, the Sky Serpent and the jellyfish.
     */
    private val noSmackDown = listOf("Bubblefin", "Goopling", "Gloomwisp", "Prism Fairy", "Sky Serpent", "Zaplume")

    fun canLearnSmackDown(species: Species): Boolean = noSmackDown.none { species.family.startsWith(it) }

    private fun smackDownFor(species: Species): List<Pair<Int, String>> = when {
        !canLearnSmackDown(species) -> emptyList()
        species.element == Element.EARTH -> listOf(SMACK_DOWN_EARTH_LEVEL to "smack_down")
        else -> listOf(SMACK_DOWN_LEVEL to "smack_down")
    }

    fun familyMovesFor(species: Species): List<Pair<Int, String>> =
        familyMoves.entries.firstOrNull { (key, _) -> species.family.startsWith(key) || (key == "Hound" && species.family.contains("Hound")) }
            ?.value.orEmpty()

    /** Moves a monster of [species] knows at [level], with the level each was learned. */
    fun learnable(species: Species): List<Pair<Int, Move>> =
        // Dual types also learn their second type's moves.
        (learnsets.getValue(species.element) + (species.secondary?.let { learnsets.getValue(it) } ?: emptyList()) +
            learnsets.getValue(Element.NEUTRAL) + familyMovesFor(species) + smackDownFor(species))
            .distinctBy { it.second }
            .map { (lvl, id) -> lvl to get(id) }
            .sortedBy { it.first }

    fun learned(species: Species, level: Int): List<Move> =
        learnable(species).filter { it.first <= level }.map { it.second }

    /**
     * Default loadout: the three strongest attacks (own type first on ties)
     * plus the newest status move, so monsters get better moves as they grow.
     */
    fun autoLoadout(species: Species, level: Int, known: List<Move> = learned(species, level)): List<Move> {
        val attacks = known.filter { it.isDamaging }
            .sortedWith(compareByDescending<Move> { it.expectedPower }.thenByDescending { it.type == species.element })
        val status = known.filter { !it.isDamaging }.lastOrNull()
        val picks = attacks.take(if (status != null) 3 else 4) + listOfNotNull(status)
        return (picks + known.filter { it !in picks }).take(MAX_MOVES)
    }

    /**
     * A wild monster's moves: 4 picked at random from everything it knows at
     * its level (the same pool your own monsters learn from), leaning toward
     * the ones it learned most recently, with at least 2 attacks.
     */
    fun wildLoadout(species: Species, level: Int, rng: kotlin.random.Random): List<Move> {
        val known = learnable(species).filter { it.first <= level }.toMutableList()
        val picks = mutableListOf<Move>()
        fun draw(from: List<Pair<Int, Move>>): Pair<Int, Move>? {
            if (from.isEmpty()) return null
            val w = from.map { 1.0 + 3.0 * it.first / level.coerceAtLeast(1) }
            var roll = rng.nextDouble() * w.sum()
            from.forEachIndexed { i, p -> roll -= w[i]; if (roll < 0) return p }
            return from.last()
        }
        repeat(2) { draw(known.filter { it.second.isDamaging })?.let { known.remove(it); picks += it.second } }
        while (picks.size < MAX_MOVES) { val p = draw(known) ?: break; known.remove(p); picks += p.second }
        return picks
    }

    const val MAX_MOVES = 4
}
