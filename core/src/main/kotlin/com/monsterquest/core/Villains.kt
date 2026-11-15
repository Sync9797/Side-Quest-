package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

enum class VillainTier(val label: String, val teamSize: IntRange, val levelBonus: IntRange, val rewardMultiplier: Int) {
    THUGS("Thug crew", 2..2, -1..1, 1),
    VILLAIN("Villain", 3..4, 2..4, 2),
}

/** A band of thugs or a villain on the map, with a team of shadow-cursed monsters. */
data class VillainCamp(
    val id: String,
    val leader: String,
    val crew: String,
    val tier: VillainTier,
    val lat: Double,
    val lon: Double,
    val level: Int,
    val team: List<Monster>,
)

/**
 * A thug who jumps you while you walk and fights alongside their monsters
 * (as many as you have fighting, so it's an even match). Their monsters
 * won't leave them, so they can't be tamed.
 */
data class Thug(
    val name: String,
    val crew: String,
    val level: Int,
    val cls: HeroClass = HeroClass.ROGUE,
    /** A full name to show instead of "Name the Kind" (a nomad). */
    val fullTitle: String? = null,
) {
    /** What kind of thug: a Brute (warrior), a Hexer (mage) or a Cutthroat (rogue). */
    val kind: String
        get() = when (cls) {
            HeroClass.WARRIOR -> "Brute"
            HeroClass.MAGE -> "Hexer"
            else -> "Cutthroat"
        }

    /** e.g. "Brick the Brute". */
    val title: String get() = fullTitle ?: "$name the $kind"
}

/**
 * Thugs and villains roam the map, changing daily. Their monsters carry the
 * shadow curse and can't be tamed; beat the whole team to win their loot and
 * a chance to redeem one of their monsters.
 */
object Villains {
    const val RANGE_M = 100.0
    const val REDEEM_CHANCE = 0.75

    private const val CELL_DEG = 0.006 // ~650 m
    /** Share of map cells with a camp today. */
    const val CAMP_CHANCE = 0.32
    private const val CELL_RADIUS = 3

    private val thugLeaders = listOf("Brick", "Snitch", "Grub", "Rook", "Moss", "Knuckles", "Vex", "Sly")
    private val thugCrews = listOf("Alley Rats", "Rust Knives", "Dockside Gang", "Gutter Dogs", "Ash Crows")
    private val villainLeaders = listOf("Malgrave the Hollow", "Vexa Nightshade", "Lord Cinder", "The Gray Warden", "Mother Mire", "Sable the Unbound")
    private val villainCrews = listOf("Shadow Covenant", "Black Lantern", "Hollow Court")

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(x: Long, y: Long, day: Long): Long {
        var h = 1469598103934665603L
        for (p in longArrayOf(x, y, day, 0x5ade)) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 31)
        }
        return h
    }

    /** A thug for a surprise attack, around the hero's level, with [monsters] monsters of their own. */
    fun ambusher(heroLevel: Int, monsters: Int, rng: Random): Pair<Thug, List<Monster>> {
        val level = (heroLevel + rng.nextInt(-1, 2)).coerceAtLeast(2)
        val team = List(monsters.coerceIn(1, MAX_PARTY_SIZE)) {
            val lvl = GameEngine.wildLevel(heroLevel, rng)
            Monster.create(Bestiary.trained(rng, lvl), lvl, rng)
        }
        val cls = listOf(HeroClass.WARRIOR, HeroClass.MAGE, HeroClass.ROGUE).random(rng)
        return Thug(thugLeaders.random(rng), thugCrews.random(rng), level, cls) to team
    }

    /** Today's thugs and villains around a location (already-beaten ones excluded). */
    fun near(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<VillainCamp> {
        val day = epochDay(nowMs, zone)
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<VillainCamp>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, day))
            if (r.nextDouble() > CAMP_CHANCE) continue
            val id = "v:$day:$x:$y"
            if (id in state.villainsDefeated) continue
            out += makeCamp(state, id, (x + r.nextDouble()) * CELL_DEG, (y + r.nextDouble()) * CELL_DEG, r)
        }
        return out
    }

    /** A thug crew (or, a quarter of the time, a villain) at [lat], [lon], with a team of shadow-cursed monsters. */
    fun makeCamp(state: GameState, id: String, lat: Double, lon: Double, r: Random): VillainCamp {
        val tier = if (r.nextDouble() < 0.25) VillainTier.VILLAIN else VillainTier.THUGS
        val level = (state.hero.level + r.nextInt(tier.levelBonus.first, tier.levelBonus.last + 1)).coerceAtLeast(2)
        val size = r.nextInt(tier.teamSize.first, tier.teamSize.last + 1)
        val team = (1..size).map {
            val lvl = (level + r.nextInt(-1, 2)).coerceAtLeast(1)
            Monster.create(Bestiary.trained(r, lvl), lvl, r).copy(cursed = true)
        }
        return VillainCamp(
            id = id,
            leader = if (tier == VillainTier.VILLAIN) villainLeaders.random(r) else thugLeaders.random(r),
            crew = if (tier == VillainTier.VILLAIN) villainCrews.random(r) else thugCrews.random(r),
            tier = tier,
            lat = lat,
            lon = lon,
            level = level,
            team = team,
        )
    }

    /** The whole team is beaten: pay out and offer their monsters for redemption. */
    fun reward(state: GameState, camp: VillainCamp, nowMs: Long, rng: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Update {
        val day = epochDay(nowMs, zone)
        val mult = camp.tier.rewardMultiplier
        val gold = camp.level * 15 * camp.team.size * mult
        var s = state.copy(
            gold = state.gold + gold,
            villainsDefeated = state.villainsDefeated.filterValues { it >= day - 2 } + (camp.id to day),
            villainSpoils = camp.team,
            stats = state.stats.copy(villainsBeaten = state.stats.villainsBeaten + 1),
        )
        val rolled = GameEngine.rollLoot(s, camp.team.size + 1, rng)
        val bonusGear = if (camp.tier == VillainTier.VILLAIN) listOf(Armory.roll(camp.level, rng, luck = 15)) else emptyList()
        val loot = rolled.copy(
            weapons = rolled.weapons + bonusGear,
            parts = GameEngine.rollParts(camp.team.size, rng, state.hero.level),
            // Thugs and villains also drop armor scrap.
            armorParts = GameEngine.rollArmorParts(camp.team.size, rng),
        )
        val granted = GameEngine.grantLoot(s, loot)
        s = granted.state
        return Update(
            s, "${camp.leader}'s ${camp.crew} is defeated! +$gold gold, ${granted.message}.",
            rewards = listOf(Reward.Gold(gold)) + granted.rewards,
            rewardTitle = "${camp.crew} defeated",
        )
    }

    /**
     * Use a Restore Potion on one of the beaten team's monsters. It joins you
     * ([REDEEM_CHANCE]) still carrying the shadow curse, or flees. One try per team.
     */
    fun redeem(state: GameState, index: Int, rng: Random = Random.Default): Update {
        val m = state.villainSpoils.getOrNull(index) ?: return Update(state, "Nothing to redeem.")
        if (state.count(Item.RESTORE_POTION) <= 0) return Update(state, "You need a Restore Potion.")
        if (state.storageFull) return Update(state, "Monster storage is full.")
        val inv = state.inventory.toMutableMap().apply {
            val left = (this[Item.RESTORE_POTION] ?: 0) - 1
            if (left <= 0) remove(Item.RESTORE_POTION) else put(Item.RESTORE_POTION, left)
        }
        val s = state.copy(inventory = inv, villainSpoils = emptyList())
        return if (rng.nextDouble() < REDEEM_CHANCE) {
            val joined = m.copy(uid = newId(rng), xp = 0, damage = 0, fainted = false, cursed = true, cureProgress = 0)
            Update(
                GameEngine.addCandy(
                    s.copy(monsters = s.monsters + joined, stats = s.stats.tamed(joined)),
                    m.species,
                    GameEngine.TAME_CANDY,
                ),
                "${m.displayName} accepts your help and joins you! +${GameEngine.TAME_CANDY} ${GameEngine.candyName(m.species)}. " +
                    "It still carries the shadow curse; battle together to lift it.",
            )
        } else {
            Update(s, "${m.displayName} recovers, but flees into the shadows.")
        }
    }

    fun dismissSpoils(state: GameState): GameState = state.copy(villainSpoils = emptyList())
}
