package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

/** Where a dungeon is: its look, the type of monsters inside, and its names. */
@Serializable
enum class DungeonTheme(val label: String, val element: Element, val names: List<String>) {
    CAVE("Cave", Element.EARTH, listOf("Mossy Cave", "Hollow Burrow", "Echo Cavern")),
    CRYPT("Crypt", Element.CORRUPT, listOf("Forgotten Crypt", "Bone Vault", "Silent Catacomb")),
    FORGE("Forge", Element.FIRE, listOf("Ember Forge", "Ashen Furnace", "Cinder Halls")),
    GROTTO("Grotto", Element.WATER, listOf("Sunken Grotto", "Tide Caves", "Drowned Temple")),
    SPIRE("Spire", Element.ELECTRIC, listOf("Storm Spire", "Static Tower", "Thunder Keep")),
    ROOST("Roost", Element.FLIGHT, listOf("Sky Roost", "Wind Ruins", "Cloud Aerie")),
    MIRE("Mire", Element.GAS, listOf("Fume Mire", "Spore Hollow", "Rotting Marsh")),
}

/** What waits behind a door. */
@Serializable
enum class RoomKind(val label: String, val hint: String) {
    MONSTERS("Monster lair", "Monsters to fight, and to tame"),
    ELITE("Alpha's den", "One big Alpha with perfect genes"),
    COMBAT("Guarded hall", "Cursed guardians (can't be tamed)"),
    TRAP("Trap", "A risky trap: you'll see the odds first, or light a torch to pass safely"),
    TREASURY("Treasury", "A chest of loot"),
    FOUNTAIN("Fairy fountain", "Restores you and your monsters"),
    EMPTY("Quiet room", "Nothing at all"),
    BOSS("Champion", "The dungeon's champion"),
    /** Leads nowhere: always looks unknown until lit. Light a torch to find the way on, or turn back. */
    DEAD_END("Dead end", "Leads nowhere, unless a torch finds the way"),
    /** The tutorial's first room: choose your starter. */
    STARTER("Starter's chamber", "Choose your first monster"),
    ;

    val isFight: Boolean get() = this in setOf(MONSTERS, ELITE, COMBAT, BOSS)
}

/** A dungeon on the map. Everything inside is drawn from [seed], so it's the same each time you look. */
@Serializable
data class Dungeon(
    val id: String,
    val name: String,
    val theme: DungeonTheme,
    /** 1 (easiest) to 5 (hardest). */
    val stars: Int,
    /** The level of what's inside (and the recommended level). */
    val level: Int,
    val lat: Double,
    val lon: Double,
    val seed: Long,
    /** The 0-star tutorial: one of a kind, every room shown once, and it can't be lost. */
    val tutorial: Boolean = false,
    /** The real place it's in, e.g. "Blue Bottle Coffee" (null for scattered ones). */
    val place: String? = null,
) {
    /** Rooms (each a choice of two doors) before the champion: more the harder it is. */
    val rooms: Int get() = if (tutorial) Dungeons.TUTORIAL_ROOMS.size else 2 + stars * 2
    /** Torches to enter: 2 for a 1-star dungeon, up to 6 for a 5-star one. */
    val torchCost: Int get() = if (tutorial) 0 else stars + 1
    /** The champion's class (the boss is a hero, with monsters of the dungeon's type). */
    val bossClass: HeroClass get() = listOf(HeroClass.WARRIOR, HeroClass.MAGE, HeroClass.ROGUE)[Math.floorMod(seed, 3L).toInt()]
    /** The stat the champion's defeat raises for good. */
    val bossStat: HeroAttribute get() = Dungeons.statFor(bossClass)
}

/** A dungeon in progress: how far in, and what's been found so far (banked when you leave or win). */
@Serializable
data class DungeonRun(
    val dungeon: Dungeon,
    /** Rooms passed (0 = at the first pair of doors). */
    val step: Int = 0,
    /** The room being fought now (the battle decides what happens next). */
    val fighting: RoomKind? = null,
    val gold: Int = 0,
    val items: Map<Item, Int> = emptyMap(),
    val torches: Int = 0,
    val pages: Map<String, Int> = emptyMap(),
    val gear: List<Gear> = emptyList(),
    val armor: List<Armor> = emptyList(),
    /** What happened in the last room, to show. */
    val last: String = "",
    /** The champion is beaten. */
    val cleared: Boolean = false,
    /**
     * The bag was already full when you went in: what you find only fits if
     * there's room. Otherwise everything comes home, even past the bag's size.
     */
    val bagFullAtStart: Boolean = false,
    /** Hidden doors you've lit a torch to look behind ([Dungeons.doorKey]). */
    val scouted: Set<Int> = emptySet(),
    /** A torch burns until this step: dark rooms before it show their doors. */
    val litUntil: Int = 0,
    /** Times you turned back from a dead end: each gives the room behind you fresh doors. */
    val detours: Int = 0,
    /** Waiting on a choice: a dead end behind this door (true = left). */
    val deadEnd: Boolean? = null,
    /** Waiting on a choice: a dark vault behind this door (true = left). */
    val vault: Boolean? = null,
) {
    val atBoss: Boolean get() = step >= dungeon.rooms
}

/** One reward in a champion's pool, with its chance (percent). */
data class BossReward(
    val label: String,
    val pct: Int,
    /** What it is, for its icon. */
    val kind: Kind = Kind.ITEM,
    val item: Item? = null,
    val stat: HeroAttribute? = null,
) {
    enum class Kind { STAT, GOLD, WEAPON, ARMOR, PAGES, TORCHES, ITEM }
}

/**
 * Dungeons: entered with torches, a few rooms deep (more the harder it is),
 * each room a choice of two doors drawn at random for the difficulty. At the
 * end waits a champion (a hero of one of the classes); beating them raises a
 * stat for good and rolls the rare reward pool. Leave any time and keep what
 * you found; fall, and half of it is lost.
 */
object Dungeons {
    const val RANGE_M = 100.0

    /** The tutorial's rooms in order: one of each kind, then its champion. */
    val TUTORIAL_ROOMS = listOf(RoomKind.STARTER, RoomKind.TREASURY, RoomKind.MONSTERS, RoomKind.TRAP, RoomKind.COMBAT, RoomKind.FOUNTAIN)

    /** The tutorial dungeon (0 stars): a Warrior champion, a cave, level 1. */
    val tutorialDungeon = Dungeon("tutorial", "Hollow of Beginnings", DungeonTheme.CAVE, 0, 1, 0.0, 0.0, seed = 3L, tutorial = true)

    /** Starts the tutorial dungeon for a new player (once). */
    fun startTutorial(state: GameState): GameState =
        if (state.tutorialDungeonDone || state.dungeonRun != null) state
        else state.copy(
            dungeonRun = DungeonRun(tutorialDungeon, last = "Your adventure begins in the Hollow of Beginnings. Every room here teaches you something, and nothing here can beat you."),
            tutorialIntroSeen = true,
        )

    /** The tutorial's first room: your starter joins you. */
    fun pickStarter(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        if (!run.dungeon.tutorial || run.step != 0) return Update(state, "")
        val u = GameEngine.chooseStarter(state, speciesId, rng)
        return advance(u.state, run, u.message + " Together you head deeper in.")
    }
    private const val CELL_DEG = 0.007 // ~780 m
    private const val CELL_RADIUS = 2
    const val CHANCE = 0.45

    fun statFor(cls: HeroClass): HeroAttribute = when (cls) {
        HeroClass.WARRIOR -> HeroAttribute.ATTACK
        HeroClass.MAGE -> HeroAttribute.MAGIC
        HeroClass.ROGUE -> HeroAttribute.SPEED
        HeroClass.NONE -> HeroAttribute.DEFENSE
    }

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(vararg parts: Long): Long {
        var h = 1469598103934665603L
        for (p in parts) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 29)
        }
        return h
    }

    /** Today's dungeons around a location, every difficulty in reach (ones you cleared today excluded). */
    fun near(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Dungeon> {
        val day = epochDay(nowMs, zone)
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<Dungeon>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val s = seed(x, y, day, 0xD06E)
            val r = Random(s)
            if (r.nextDouble() > CHANCE) continue
            val id = "dg:$day:$x:$y"
            if (id in state.dungeonsCleared) continue
            val theme = DungeonTheme.entries.random(r)
            val stars = Venues.starDeck(state.hero.level).random(r)
            val level = (state.hero.level + (stars - 2) * 3 + r.nextInt(-1, 2)).coerceAtLeast(2)
            out += Dungeon(
                id, theme.names.random(r), theme, stars, level,
                (x + 0.2 + r.nextDouble() * 0.6) * CELL_DEG, (y + 0.2 + r.nextDouble() * 0.6) * CELL_DEG, s,
            )
        }
        return out
    }

    // ---- what's inside -------------------------------------------------------

    /** Monsters you may meet inside (for the preview): the theme's first forms around its level. */
    fun residents(d: Dungeon): List<Species> {
        val r = Random(d.seed xor 0x5EED)
        return (1..40).map { Bestiary.randomWild(r, d.level, setOf(d.theme.element)) }
            .filter { d.theme.element in it.types }.distinctBy { it.family }.take(4)
            .ifEmpty { listOf(Bestiary.randomWild(r, d.level, setOf(d.theme.element))) }
    }

    /** Door odds for a difficulty: harder dungeons hold more fights and traps, fewer gifts. */
    private fun roomWeights(stars: Int): Map<RoomKind, Int> = mapOf(
        RoomKind.MONSTERS to 30,
        RoomKind.ELITE to 2 * stars,
        RoomKind.COMBAT to 8 + 5 * stars,
        RoomKind.TRAP to 8 + 4 * stars,
        RoomKind.TREASURY to 18,
        RoomKind.FOUNTAIN to 13 - 2 * stars,
        RoomKind.EMPTY to 16 - 2 * stars,
        RoomKind.DEAD_END to if (stars >= 2) 1 + stars else 0,
    )

    private fun rng(run: DungeonRun, salt: Int) = Random(run.dungeon.seed * 31 + run.step * 1_009L + run.detours * 7_919L + salt)

    /** The two doors at this step (left, right): drawn at random for the difficulty, never both the same. */
    fun doors(run: DungeonRun): Pair<RoomKind, RoomKind> {
        if (run.atBoss) return RoomKind.BOSS to RoomKind.BOSS
        // The tutorial shows every kind of room once, in order.
        if (run.dungeon.tutorial) TUTORIAL_ROOMS[run.step].let { return it to it }
        val r = rng(run, 1)
        val w = roomWeights(run.dungeon.stars).filterValues { it > 0 }
        fun draw(except: RoomKind?): RoomKind {
            val pool = w.filterKeys { it != except }
            var roll = r.nextInt(pool.values.sum())
            for ((k, v) in pool) { roll -= v; if (roll < 0) return k }
            return pool.keys.last()
        }
        val left = draw(null)
        return left to draw(left)
    }

    /** A door's key for [DungeonRun.scouted]: the step, the side and the detour (a turned-back room has new doors). */
    fun doorKey(step: Int, left: Boolean, detours: Int = 0): Int = detours * 1_000 + step * 2 + if (left) 0 else 1

    /** A torch lights this many rooms of a dark dungeon. */
    const val TORCH_ROOMS = 3

    /**
     * The deep half of a 4- or 5-star dungeon is pitch dark: every door shows
     * "?" unless a torch is burning ([lightWay]).
     */
    fun dark(run: DungeonRun): Boolean =
        !run.atBoss && !run.dungeon.tutorial && run.dungeon.stars >= 4 && run.step >= run.dungeon.rooms / 2

    /** A torch is burning here (lit for [TORCH_ROOMS] rooms). */
    fun lit(run: DungeonRun): Boolean = run.step < run.litUntil

    /**
     * Whether a door's sign is hidden ("?"): more often in harder dungeons
     * (18% to 50%), every door in the dark ([dark]), and always a dead end;
     * a torch shows what's behind.
     */
    fun hidden(run: DungeonRun, left: Boolean): Boolean {
        if (run.atBoss || run.dungeon.tutorial || doorKey(run.step, left, run.detours) in run.scouted) return false
        val room = doors(run).let { if (left) it.first else it.second }
        if (lit(run)) return false
        if (room == RoomKind.DEAD_END || dark(run)) return true
        return rng(run, if (left) 2 else 3).nextDouble() < 0.1 + 0.08 * run.dungeon.stars
    }

    /** Lights a torch that burns for [TORCH_ROOMS] rooms: every door in them shows what's behind. */
    fun lightWay(state: GameState): Update {
        val run = state.dungeonRun ?: return Update(state, "You're not in a dungeon.")
        if (lit(run)) return Update(state, "Your torch is still burning.")
        val (s, r) = lightTorch(state, run) ?: return Update(state, "You have no torches to light.")
        val lit = r.copy(litUntil = run.step + TORCH_ROOMS, last = "You light a torch. It will burn for $TORCH_ROOMS rooms.")
        return Update(s.copy(dungeonRun = lit), lit.last)
    }

    /** Torches you can light inside: the ones you carry plus any found in this dungeon. */
    fun torchesAtHand(state: GameState): Int = state.torches + (state.dungeonRun?.torches ?: 0)

    /** Uses one torch: one found in this dungeon first, then one you brought. Null if there are none. */
    private fun lightTorch(state: GameState, run: DungeonRun): Pair<GameState, DungeonRun>? = when {
        run.torches > 0 -> state to run.copy(torches = run.torches - 1)
        state.torches > 0 -> state.copy(torches = state.torches - 1) to run
        else -> null
    }

    /** Lights a torch to see behind a hidden door. */
    fun scout(state: GameState, left: Boolean): Update {
        val run = state.dungeonRun ?: return Update(state, "You're not in a dungeon.")
        if (!hidden(run, left)) return Update(state, "You can already see what's behind that door.")
        val (s, r) = lightTorch(state, run) ?: return Update(state, "You have no torches to light.")
        val room = doors(run).let { if (left) it.first else it.second }
        val seen = r.copy(scouted = r.scouted + doorKey(run.step, left, run.detours), last = "Your torch shows the way: behind the door is ${if (room == RoomKind.TRAP) "a ${trapAt(run, left).label.lowercase()}" else "a ${room.label.lowercase()}"}.")
        return Update(s.copy(dungeonRun = seen), seen.last)
    }

    /** Who fights in a room (the champion's monsters for the boss). */
    fun monstersFor(run: DungeonRun, room: RoomKind): List<Monster> {
        val d = run.dungeon
        val r = rng(run, 4 + room.ordinal)
        if (d.tutorial) {
            // Gentle first fights: one small monster each.
            val id = when (room) { RoomKind.COMBAT -> "gloomwisp"; RoomKind.BOSS -> "pebblit"; else -> "zapmouse" }
            val m = Monster.create(Bestiary.get(id), 2, r, evolve = false).withWildMoves(r)
            return listOf(if (room == RoomKind.COMBAT) m.copy(cursed = true) else m)
        }
        fun lvl() = (d.level + r.nextInt(-1, 2)).coerceAtLeast(1)
        return when (room) {
            RoomKind.MONSTERS -> {
                // Easy dungeons send at most three at once.
                val most = if (d.stars <= 2) 3 else 4
                val n = 1 + r.nextInt(most)
                val species = List(n) { Bestiary.randomWild(r, d.level, setOf(d.theme.element)) }
                species.map { Monster.create(it, lvl(), r, shiny = rollShiny(r), evolve = false).withWildMoves(r) }
            }
            RoomKind.ELITE -> {
                val sp = Bestiary.lineOf(Bestiary.randomWild(r, d.level, setOf(d.theme.element))).first()
                val m = Monster.create(sp, d.level + 1, r, evolve = false).withWildMoves(r)
                listOf(GameEngine.withPerfectStats(m, Alphas.PERFECT_STATS, r).copy(alpha = true))
            }
            RoomKind.COMBAT -> {
                // Cursed guardians: evolved monsters of the dark and of the dungeon's type.
                val n = (1 + r.nextInt(1 + (d.stars + 1) / 2)).coerceAtMost(3)
                List(n) {
                    val favored = setOf(Element.CORRUPT, d.theme.element)
                    val lv = lvl()
                    Monster.create(Bestiary.evolvedFor(Bestiary.randomWild(r, lv, favored), lv), lv, r).withWildMoves(r).copy(cursed = true)
                }
            }
            RoomKind.BOSS -> List(2) {
                val lv = d.level + 2
                Monster.create(Bestiary.evolvedFor(Bestiary.randomWild(r, lv, setOf(d.theme.element)), lv), lv, r).withWildMoves(r)
            }
            else -> emptyList()
        }
    }

    private val championNames = listOf("Varric", "Seraphine", "Doran", "Ysolde", "Kael", "Mirela", "Thorne", "Isolde")

    /** The champion at the end: a hero of the dungeon's boss class. */
    fun champion(d: Dungeon): Thug {
        if (d.tutorial) return Thug("Brannoc", d.name, 2, d.bossClass, fullTitle = "Brannoc the Gatekeeper")
        val name = championNames[Math.floorMod(d.seed shr 3, championNames.size.toLong()).toInt()]
        val title = when (d.bossClass) {
            HeroClass.WARRIOR -> "$name the Warlord"
            HeroClass.MAGE -> "$name the Archmage"
            else -> "$name the Shadowblade"
        }
        return Thug(name, d.name, d.level + 3, d.bossClass, fullTitle = title)
    }

    // ---- entering, doors and rooms --------------------------------------------

    fun enter(state: GameState, d: Dungeon): Update {
        if (state.dungeonRun != null) return Update(state, "You're already in a dungeon.")
        if (d.tutorial) return Update(startTutorial(state), "")
        if (state.torches < d.torchCost) return Update(state, "You need ${d.torchCost} torch${if (d.torchCost == 1) "" else "es"} to enter (you have ${state.torches}). Walk to light more.")
        if (state.monsters.none { it.uid in state.party && !it.fainted }) return Update(state, "Bring at least one monster that can fight.")
        val full = state.bagFull
        val run = DungeonRun(
            d,
            last = "You light a torch and step into the ${d.name}." + if (full) " Your bag is full: you can't carry out anything you find." else "",
            bagFullAtStart = full,
        )
        return Update(state.copy(torches = state.torches - d.torchCost, dungeonRun = run), run.last)
    }

    /** The trap behind a door: its kind is fixed for the run, so it's the same each time you look. */
    fun trapAt(run: DungeonRun, left: Boolean): TrapKind {
        val r = rng(run, if (left) 20 else 21)
        // Harder dungeons lean toward the nastier traps.
        val pool = TrapKind.entries.filter { it.minStars <= run.dungeon.stars }
        return pool.random(r)
    }

    /**
     * A trap's odds of getting through unhurt: its kind, the dungeon's
     * difficulty and a bit of luck (each trap is different), better for a
     * quick hero (Rogues most of all). Never certain: light a torch for that.
     */
    fun trapChance(state: GameState, run: DungeonRun, left: Boolean): Double {
        val d = run.dungeon
        if (d.tutorial) return 1.0
        val kind = trapAt(run, left)
        val luck = rng(run, if (left) 22 else 23).nextDouble(-0.12, 0.12)
        val base = 0.72 - 0.07 * (d.stars - 1) + kind.oddsShift + luck
        val rogue = if (state.hero.heroClass == HeroClass.ROGUE) 0.08 else 0.0
        val quick = ((state.hero.attr(HeroAttribute.SPEED) - STARTING_ATTRIBUTE) * 0.005).coerceIn(0.0, 0.1)
        return (base + rogue + quick).coerceIn(MIN_TRAP_CHANCE, MAX_TRAP_CHANCE)
    }

    const val MIN_TRAP_CHANCE = 0.15
    const val MAX_TRAP_CHANCE = 0.85

    /** Share of max HP a sprung trap takes from everyone: more in harder dungeons and from nastier traps. */
    fun trapDamagePct(d: Dungeon, kind: TrapKind): Double = (0.15 + 0.06 * d.stars) * kind.harm

    /**
     * Walks into a trap room: [trapChance] to get through unhurt, otherwise
     * everyone takes damage (a monster with too little HP left is knocked
     * out; the hero is left hanging on). Either way you go on.
     */
    fun springTrap(state: GameState, left: Boolean, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val kind = if (run.dungeon.tutorial) TrapKind.DARTS else trapAt(run, left)
        val ok = rng.nextDouble() < trapChance(state, run, left)
        if (ok) return advance(state, run, kind.pass)
        // Each hit varies a little.
        val pct = trapDamagePct(run.dungeon, kind) * rng.nextDouble(0.85, 1.15)
        val heroHit = (state.hero.maxHp * pct).toInt()
        val knocked = mutableListOf<String>()
        val s = state.copy(
            heroDamage = (state.heroDamage + heroHit).coerceAtMost(state.hero.maxHp - 1),
            monsters = state.monsters.map { m ->
                if (m.uid !in state.party || m.fainted) return@map m
                val dmg = m.damage + (m.stats.hp * pct).toInt()
                if (dmg >= m.stats.hp) {
                    knocked += m.displayName
                    m.copy(damage = 0, fainted = true)
                } else {
                    m.copy(damage = dmg)
                }
            },
        )
        val out = if (knocked.isEmpty()) "" else " ${knocked.joinToString()} ${if (knocked.size == 1) "is" else "are"} knocked out!"
        return advance(s, run, "${kind.fail} Everyone loses ${(pct * 100).toInt()}% of their health.$out")
    }

    /** Lights a torch at a trap: you see every plate and wire, and pass through unhurt. */
    fun torchPastTrap(state: GameState, left: Boolean): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val (s, r) = lightTorch(state, run) ?: return Update(state, "You have no torches to light.")
        return advance(s, r, "You light a torch: the ${trapAt(run, left).label.lowercase()} lies bare in its glow, and you step around it unhurt.")
    }

    /**
     * Walks through a door without looking: a hidden trap springs at once
     * (no odds, no torch). Anything else plays out as usual.
     */
    fun openBlind(state: GameState, left: Boolean, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "You're not in a dungeon.")
        val room = doors(run).let { if (left) it.first else it.second }
        return if (room == RoomKind.TRAP) springTrap(state, left, rng) else open(state, left, rng)
    }

    /** Opens the door on the [left] or right. Fights hand over to a battle; the rest happen right here. */
    fun open(state: GameState, left: Boolean, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "You're not in a dungeon.")
        if (run.fighting != null || run.cleared) return Update(state, "")
        val (l, r) = doors(run)
        val room = if (left) l else r
        return when {
            room.isFight -> Update(state.copy(dungeonRun = run.copy(fighting = room)), "")
            room == RoomKind.TRAP -> Update(state, "") // the player decides first (see [springTrap])
            // A dead end or a dark vault: the player decides what to do (see [deadEnd] and [openVault]).
            room == RoomKind.DEAD_END -> Update(state.copy(dungeonRun = run.copy(deadEnd = left)), "A dead end! The passage stops at a blank wall.")
            room == RoomKind.TREASURY && isVault(run, left) -> Update(state.copy(dungeonRun = run.copy(vault = left)), "A vault, too dark to see into.")
            room == RoomKind.STARTER -> Update(state, "") // chosen on its own screen (see [pickStarter])
            else -> resolve(state, run, room, rng)
        }
    }

    /**
     * At a dead end: light a torch to find the hidden way on, or turn back.
     * Turning back costs a room: you're one room further from the champion,
     * facing new doors.
     */
    fun deadEnd(state: GameState, useTorch: Boolean): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        if (run.deadEnd == null) return Update(state, "")
        if (useTorch) {
            val (s, r) = lightTorch(state, run) ?: return Update(state, "You have no torches to light.")
            return advance(s, r.copy(deadEnd = null), "Your torch flickers in a draft: a hidden passage behind the wall leads on.")
        }
        val back = run.copy(deadEnd = null, step = (run.step - 1).coerceAtLeast(0), detours = run.detours + 1, litUntil = 0,
            last = "You turn back the way you came. The halls seem to have shifted: new doors wait.")
        return Update(state.copy(dungeonRun = back), back.last)
    }

    /** Whether a treasury behind a door is a dark vault (more often in harder dungeons). */
    fun isVault(run: DungeonRun, left: Boolean): Boolean =
        !run.dungeon.tutorial && rng(run, if (left) 30 else 31).nextDouble() < 0.25 + 0.05 * run.dungeon.stars

    /** At a dark vault: light a torch for the rich chest, or grope for the plain one. */
    fun openVault(state: GameState, useTorch: Boolean, rng: Random = Random.Default): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        if (run.vault == null) return Update(state, "")
        var s = state
        var r = run.copy(vault = null)
        if (useTorch) {
            val lit = lightTorch(state, r) ?: return Update(state, "You have no torches to light.")
            s = lit.first
            r = lit.second
        }
        val (r2, text, loot) = treasure(s, r, rng, rich = useTorch)
        val intro = if (useTorch) "Torchlight fills the vault: a rich chest! " else "You feel your way to a chest in the dark. "
        return advance(s, r2, intro + text.removePrefix("A treasury! ")).copy(rewards = loot, rewardTitle = if (useTorch) "Torchlit vault" else "Treasury")
    }

    private fun resolve(state: GameState, run: DungeonRun, room: RoomKind, rng: Random): Update = when (room) {
        RoomKind.FOUNTAIN -> advance(
            state.copy(
                heroDamage = 0,
                heroEnergyUsed = 0,
                monsters = state.monsters.map { if (it.uid in state.team) it.copy(damage = 0, fainted = false) else it },
            ),
            run,
            "A fairy fountain! Its water restores you and your monsters to full strength.",
        )
        RoomKind.TREASURY -> {
            val (r2, text, loot) = treasure(state, run, rng)
            advance(state, r2, text).copy(rewards = loot, rewardTitle = "Treasury")
        }
        else -> advance(state, run, "An empty room. Dust, cobwebs and silence.")
    }

    /** A chest: gold, and often potions, crystals, spell pages, torches, now and then gear. Better in harder dungeons. */
    /** A chest's loot; a [rich] one (a torchlit vault) holds twice the gold, more of everything and better gear. */
    private fun treasure(state: GameState, run: DungeonRun, rng: Random, rich: Boolean = false): Triple<DungeonRun, String, List<Reward>> {
        val d = run.dungeon
        if (d.tutorial) {
            // Everything you need for the rooms ahead.
            val r = run.copy(gold = run.gold + 100, torches = run.torches + 3, items = run.items + (Item.BINDING_CRYSTAL to 5) + (Item.POTION to 3))
            return Triple(
                r,
                "A treasury! The chest holds 100 gold, 3 torches, 5 Binding Crystals and 3 Potions. Chests in other dungeons are random, and better the harder the dungeon.",
                listOf(Reward.Gold(100), Reward.Torches(3), Reward.Items(Item.BINDING_CRYSTAL, 5), Reward.Items(Item.POTION, 3)),
            )
        }
        var r = run
        val found = mutableListOf<String>()
        val loot = mutableListOf<Reward>()
        val gold = (d.level * (4 + d.stars * 2) + rng.nextInt(20)) * (if (rich) 2 else 1)
        r = r.copy(gold = r.gold + gold)
        found += "$gold gold"
        loot += Reward.Gold(gold)
        fun item(i: Item, n: Int) {
            r = r.copy(items = r.items + (i to (r.items[i] ?: 0) + n)); found += "${i.label}${if (n > 1) " x$n" else ""}"; loot += Reward.Items(i, n)
        }
        if (rich || rng.nextDouble() < 0.5) item(listOf(Item.POTION, Item.GREATER_POTION, Item.MANA_DRAUGHT).random(rng), 1 + rng.nextInt(2) + (if (rich) 1 else 0))
        if (rich || rng.nextDouble() < 0.35) item(if (d.stars >= 3 || rich) Item.RESONANT_CRYSTAL else Item.BINDING_CRYSTAL, 1 + rng.nextInt(3))
        if (rng.nextDouble() < 0.3 + 0.05 * d.stars) {
            val n = 1 + (if (rng.nextDouble() < d.stars / 6.0) 1 else 0)
            r = r.copy(torches = r.torches + n)
            found += "$n torch${if (n == 1) "" else "es"}"
            loot += Reward.Torches(n)
        }
        val spells = GameEngine.pageCandidates(state)
        if (spells.isNotEmpty() && rng.nextDouble() < (if (rich) 0.7 else 0.35)) {
            val sp = spells.random(rng)
            val n = 1 + rng.nextInt(2)
            r = r.copy(pages = r.pages + (sp.id to (r.pages[sp.id] ?: 0) + n))
            found += "$n ${sp.name} page${if (n == 1) "" else "s"}"
            loot += Reward.Pages(sp.id, n, learned = false)
        }
        if (rng.nextDouble() < 0.06 * d.stars * (if (rich) 2.5 else 1.0)) {
            val g = Armory.roll(d.level, rng, luck = 10 * d.stars + (if (rich) 20 else 0))
            r = r.copy(gear = r.gear + g)
            found += "${g.rarity.label} ${g.name}"
            loot += Reward.WeaponDrop(g)
        }
        return Triple(r, "A treasury! The chest holds ${found.joinToString()}.", loot)
    }

    private fun advance(state: GameState, run: DungeonRun, text: String): Update {
        val next = run.copy(step = run.step + 1, fighting = null, last = text)
        return Update(state.copy(dungeonRun = next), text)
    }

    /**
     * A room's fight is over. Won (or tamed): on to the next room, or, for the
     * champion, the dungeon is cleared. Lost or fled: the dungeon ends, and
     * half of what you found is lost (fleeing keeps it all).
     */
    fun afterFight(state: GameState, outcome: BattleOutcome, nowMs: Long, rng: Random = Random.Default, zone: ZoneId = ZoneId.systemDefault()): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val room = run.fighting ?: return Update(state, "")
        // The tutorial can't be lost: a fall just means your team picks itself up and goes on.
        if (run.dungeon.tutorial && outcome != BattleOutcome.WON && outcome != BattleOutcome.CAPTURED) {
            val healed = state.copy(heroDamage = 0, monsters = state.monsters.map { it.copy(fainted = false, damage = 0) })
            return if (room == RoomKind.BOSS) clear(healed, run, nowMs, rng, zone)
            else advance(healed, run, "Your team picks itself up and pushes on. (In real dungeons, falling ends the run and loses half your finds.)")
        }
        return when (outcome) {
            BattleOutcome.WON, BattleOutcome.CAPTURED -> if (room == RoomKind.BOSS) clear(state, run, nowMs, rng, zone)
            else advance(state, run, "The way ahead is clear.")
            BattleOutcome.FLED -> leave(state, "You fled the fight and made it back out with everything you found.")
            else -> leave(state, "Your team fell in the ${run.dungeon.name}. You dragged yourselves out, losing half of what you found.", half = true)
        }
    }

    /** The champion's reward pool, as shown before you enter (chances in percent). */
    /** Stat points champions can give per attribute, for each star rank (10 before the first star, 20 at the first...). */
    const val CHAMPION_STAT_CAP = 10

    /** The most of one stat champions can have given at real hero level [level]. */
    fun championCap(level: Int): Int = CHAMPION_STAT_CAP * (Stars.of(level) + 1)

    fun bossRewards(d: Dungeon): List<BossReward> = listOf(
        BossReward("+1 ${statFor(d.bossClass).label} for good (once a day, up to $CHAMPION_STAT_CAP per star rank)", 100, BossReward.Kind.STAT, stat = statFor(d.bossClass)),
        BossReward("${d.level * 30} gold", 100, BossReward.Kind.GOLD),
        BossReward("Epic or better weapon", 10 + 6 * d.stars, BossReward.Kind.WEAPON),
        BossReward("Rare or better armor", 15 + 5 * d.stars, BossReward.Kind.ARMOR),
        BossReward("3 spell pages", 50, BossReward.Kind.PAGES),
        BossReward("3 Resonant Crystals", 40, item = Item.RESONANT_CRYSTAL),
        BossReward("2 torches", 30, BossReward.Kind.TORCHES),
        BossReward("Incubator", 6 + 2 * d.stars, item = Item.INCUBATOR),
        BossReward("Golden Berry", 20, item = Item.GOLDEN_BERRY),
        BossReward("Mood Crystal", 3 + d.stars, item = Item.MOOD_CRYSTAL),
    )

    private fun clear(state: GameState, run: DungeonRun, nowMs: Long, rng: Random, zone: ZoneId): Update {
        val d = run.dungeon
        val day = epochDay(nowMs, zone)
        if (d.tutorial) {
            // Your first champion: +1 to their class's stat, and the tutorial is done.
            val h = state.hero
            val s = state.copy(hero = h.copy(attributes = h.attributes + (d.bossStat to h.attr(d.bossStat) + 1)), tutorialDungeonDone = true)
            val banked = bank(s, run.copy(gold = run.gold + 150), half = false)
            return Update(
                banked.state.copy(dungeonRun = null),
                "Brannoc the Gatekeeper is defeated! +1 ${d.bossStat.label} for good. Real dungeons wait on your map: walk to light torches and explore them. ${banked.message}".trim(),
                rewards = listOf(Reward.Stat(d.bossStat, 1), Reward.Gold(150)),
                rewardTitle = "${d.name} cleared",
            )
        }
        var r = run.copy(gold = run.gold + d.level * 30)
        val got = mutableListOf<String>()
        val loot = mutableListOf<Reward>()
        var s = state
        // The champion's stat, once a day.
        val stat = d.bossStat
        if (s.championStatDay != day) {
            val h = s.hero
            val gained = s.championGains[stat] ?: 0
            if (gained < championCap(h.level)) {
                s = s.copy(
                    hero = h.copy(attributes = h.attributes + (stat to h.attr(stat) + 1)),
                    championStatDay = day,
                    championGains = s.championGains + (stat to gained + 1),
                )
                got += "+1 ${stat.label} for good"
                loot += Reward.Stat(stat, 1)
            } else {
                got += "no more ${stat.label} from champions until your next star (${championCap(h.level)} reached)"
            }
        }
        got += "${d.level * 30} gold"
        loot += Reward.Gold(d.level * 30)
        val pool = bossRewards(d)
        fun hit(i: Int) = rng.nextInt(100) < pool[i].pct
        fun item(i: Item, n: Int) { r = r.copy(items = r.items + (i to (r.items[i] ?: 0) + n)); loot += Reward.Items(i, n) }
        if (hit(2)) Armory.roll(d.level, rng, luck = 30 + 10 * d.stars, minimum = GearRarity.EPIC).let { r = r.copy(gear = r.gear + it); got += "${it.rarity.label} ${it.name}"; loot += Reward.WeaponDrop(it) }
        if (hit(3)) Armory.rollArmor(d.level, rng, luck = 20 + 10 * d.stars, minimum = GearRarity.RARE).let { r = r.copy(armor = r.armor + it); got += "${it.rarity.label} ${it.name}"; loot += Reward.ArmorDrop(it) }
        if (hit(4)) GameEngine.pageCandidates(s).randomOrNull(rng)?.let { r = r.copy(pages = r.pages + (it.id to (r.pages[it.id] ?: 0) + 3)); got += "3 ${it.name} pages"; loot += Reward.Pages(it.id, 3, learned = false) }
        if (hit(5)) { item(Item.RESONANT_CRYSTAL, 3); got += "3 Resonant Crystals" }
        if (hit(6)) { r = r.copy(torches = r.torches + 2); got += "2 torches"; loot += Reward.Torches(2) }
        if (hit(7)) { item(Item.INCUBATOR, 1); got += "an Incubator" }
        if (hit(8)) { item(Item.GOLDEN_BERRY, 1); got += "a Golden Berry" }
        if (hit(9)) { item(Item.MOOD_CRYSTAL, 1); got += "a Mood Crystal" }
        s = s.copy(dungeonsCleared = s.dungeonsCleared.filterValues { it >= day - 1 } + (d.id to day))
        val banked = bank(s, r, half = false)
        return Update(
            banked.state.copy(dungeonRun = null, stats = banked.state.stats.copy(dungeonsCleared = banked.state.stats.dungeonsCleared + 1)),
            "${champion(d).title} is defeated! The ${d.name} is cleared: ${got.joinToString()}. ${banked.message}".trim(),
            rewards = loot,
            rewardTitle = "${d.name} cleared",
        )
    }

    /** Leaves the dungeon, banking what was found (half of it if your team fell). */
    fun leave(state: GameState, text: String = "You leave the dungeon with everything you found.", half: Boolean = false): Update {
        val run = state.dungeonRun ?: return Update(state, "")
        val banked = bank(state, run, half)
        return Update(banked.state.copy(dungeonRun = null), "$text ${banked.message}".trim())
    }

    private fun bank(state: GameState, run: DungeonRun, half: Boolean): Update {
        fun cut(n: Int) = if (half) n / 2 else n
        var s = state.copy(gold = state.gold + cut(run.gold))
        s = Torches.add(s, cut(run.torches), reward = true)
        val loot = GameEngine.Loot(
            items = run.items.mapValues { cut(it.value) }.filterValues { it > 0 },
            weapons = if (half) run.gear.take(run.gear.size / 2) else run.gear,
            armor = if (half) run.armor.take(run.armor.size / 2) else run.armor,
            pages = run.pages.mapValues { cut(it.value) }.filterValues { it > 0 },
        )
        val granted = GameEngine.grantLoot(s, loot, overfill = !run.bagFullAtStart)
        val total = listOfNotNull(
            cut(run.gold).takeIf { it > 0 }?.let { "$it gold" },
            cut(run.torches).takeIf { it > 0 }?.let { "$it torch${if (it == 1) "" else "es"}" },
            granted.message.takeIf { it.isNotBlank() && it != "nothing" },
        )
        return Update(granted.state, if (total.isEmpty()) "" else "Banked: ${total.joinToString()}.")
    }
}

/** The kinds of trap a dungeon hides: each with its own odds and bite. */
enum class TrapKind(
    val label: String,
    /** Added to the odds of getting through unhurt. */
    val oddsShift: Double,
    /** How hard it hits, against an ordinary trap. */
    val harm: Double,
    /** Only in dungeons of at least this many stars. */
    val minStars: Int,
    val fail: String,
    val pass: String,
) {
    DARTS("Dart wall", 0.1, 0.8, 1, "You stepped on a pressure plate and darts flew at you!", "You spotted the tripwire and stepped over it."),
    PIT("Spike pit", 0.0, 1.0, 1, "The floor gave way and you tumbled into a pit of spikes!", "You tested the floor and found the hollow stones. Safe!"),
    GAS("Poison gas", 0.05, 0.9, 2, "Poison gas hissed from the cracks in the stones!", "You held your breath and dashed through the gas."),
    BLADES("Swinging blades", -0.05, 1.15, 2, "A swinging blade sliced out of the wall!", "You timed the swinging blades and slipped through."),
    BOULDER("Rolling boulder", -0.1, 1.3, 3, "A boulder thundered down the corridor and crashed into your team!", "You heard the rumble and flattened yourselves in an alcove as the boulder rolled past."),
    FLAMES("Flame vents", -0.15, 1.5, 4, "Flames roared up from the vents in the floor!", "You read the scorch marks and crossed between the bursts of flame."),
}
