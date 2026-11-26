package com.monsterquest.core

import kotlin.random.Random

/** A state change plus a message to show the player. */
/** A game action's result: the new state, a message, and (for loot) what was received. */
data class Update(
    val state: GameState,
    val message: String,
    val rewards: List<Reward> = emptyList(),
    /** The heading over [rewards] when they're shown (e.g. "Treasury", a dungeon's name). */
    val rewardTitle: String? = null,
    /** Who levelled up (shown as banners and a pop-up after battle, not in [message]). */
    val levelUps: List<LevelUp> = emptyList(),
)

/** One stat before and after a level-up. */
data class StatLine(val label: String, val before: Int, val after: Int) {
    val gain: Int get() = after - before
}

/** A fighter that levelled up: new level, stat changes, moves learned and evolution. */
data class LevelUp(
    /** The monster's uid, or [Combatant.HERO_KEY]. */
    val key: String,
    val name: String,
    /** The species after levelling (null for the hero). */
    val speciesId: String?,
    val fromLevel: Int,
    val toLevel: Int,
    val stats: List<StatLine>,
    val learned: List<String> = emptyList(),
    /** Moves it could learn but its 4 slots are full (swap on its stat page). */
    val canLearn: List<String> = emptyList(),
    /** The same moves, by id: offered right away at the end of the battle. */
    val canLearnIds: List<String> = emptyList(),
    /** Its 4 moves after levelling, by id (to compare against). */
    val loadout: List<String> = emptyList(),
    /** The species name it evolved from, if it evolved. */
    val evolvedFrom: String? = null,
    /** Anything else worth saying (e.g. attribute points to spend). */
    val notes: List<String> = emptyList(),
)

/** One thing received, for showing loot with icons. */
sealed interface Reward {
    data class Gold(val amount: Int) : Reward
    data class Items(val item: Item, val count: Int) : Reward
    data class WeaponDrop(val gear: Gear) : Reward
    data class ArmorDrop(val armor: Armor) : Reward
    data class Pages(val spellId: String, val count: Int, val learned: Boolean) : Reward
    data class Parts(val type: WeaponType, val count: Int) : Reward
    data class ArmorParts(val type: ArmorType, val count: Int) : Reward
    /** A spell learned (or ranked up) at once from a spell book. */
    data class SpellBook(val spellId: String, val rank: Int) : Reward
    data class Torches(val count: Int) : Reward
    data class Xp(val amount: Int) : Reward
    /** A hero attribute raised for good (a dungeon champion). */
    data class Stat(val attribute: HeroAttribute, val amount: Int) : Reward
    /** An egg of a line, to hatch by walking. */
    data class Egg(val line: String) : Reward
    /** A Scholar's Tome: pages for any spell you choose. */
    data class Tome(val pages: Int) : Reward
}

/** An event read from the phone's calendar. */
data class CalendarEvent(
    /** Unique per occurrence, e.g. "eventId:beginMs". */
    val key: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarName: String?,
)

object GameEngine {

    /** The starters: #1 Voltfawn, #4 Cindrake, #7 Drizzlet, #10 Mosscub. */
    val starters = listOf("voltfawn", "cindrake", "drizzlet", "mosscub")

    fun chooseStarter(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        if (state.starterChosen) return Update(state, "Starter already chosen.")
        val m = Monster.create(Bestiary.get(speciesId), 3, rng)
        return Update(
            state.copy(monsters = state.monsters + m, party = listOf(m.uid), starterChosen = true).withJournal(),
            "${m.species.name} joins you! Personality: ${m.personality.label} (${m.personality.description}).",
        )
    }

    /** Level a gifted starter joins at: near the hero's level, but young enough to evolve on screen. */
    fun giftStarterLevel(heroLevel: Int) = heroLevel.coerceIn(5, 15)

    /**
     * For players who started before the new starters: pick one, free. It joins
     * even if monster storage is full, and goes into the party if there's room.
     */
    fun claimStarterGift(state: GameState, speciesId: String, rng: Random = Random.Default): Update {
        if (!state.starterGiftPending) return Update(state, "You've already claimed your new starter.")
        if (speciesId !in starters) return Update(state, "Pick one of the new starters.")
        val m = Monster.create(Bestiary.get(speciesId), giftStarterLevel(state.hero.level), rng)
        val party = if (state.party.size < MAX_PARTY_SIZE) state.party + m.uid else state.party
        val s = addCandy(
            state.copy(monsters = state.monsters + m, party = party, starterGiftPending = false),
            m.species,
            TAME_CANDY,
        ).withJournal()
        return Update(s, "${m.species.name} joins you! It evolves at level 16 and again at 36.")
    }

    // ---- hero progression --------------------------------------------------

    data class XpGain(
        val state: GameState,
        val levelsGained: Int,
        val upgradesGained: Int,
        /** Items from the level-up boxes, already in the bag. */
        val boxItems: Map<Item, Int> = emptyMap(),
        /** Star reward boxes opened (already in the bag), one per new star. */
        val starBoxes: List<Stars.Box> = emptyList(),
    )

    /**
     * The reward box for reaching [level]: a couple of items that get better
     * as you level (orbs follow the shop's level locks), with a little extra
     * every 5 and 10 levels, and a Perfect Orb at level 100.
     */
    fun levelBox(level: Int): Map<Item, Int> {
        val box = mutableMapOf<Item, Int>()
        fun add(item: Item, n: Int = 1) { box[item] = (box[item] ?: 0) + n }
        add(
            when {
                level < 10 -> Item.BINDING_CRYSTAL
                level < 25 -> Item.RESONANT_CRYSTAL
                level < 50 -> Item.RADIANT_CRYSTAL
                else -> Item.PRISMATIC_CRYSTAL
            },
        )
        add(if (level < 15) Item.POTION else Item.GREATER_POTION)
        if (level % 5 == 0) {
            add(
                when {
                    level < 25 -> Item.MANA_DRAUGHT
                    level % 10 == 0 -> Item.RESTORE_POTION
                    else -> Item.PHOENIX_FEATHER
                },
            )
        }
        if (level % 10 == 0) add(Item.TREAT)
        if (level == MAX_HERO_REWARD_LEVEL) add(Item.PERFECT_ORB)
        return box
    }

    /**
     * The hero as they fight: attributes and class plus the weapon, off-hand,
     * armor and their effects, worked out exactly as a battle does (without
     * today's rest, which changes day to day).
     */
    fun heroInBattle(state: GameState): Combatant =
        Combatant.fromHero(state.hero, state.equippedGear, 0, state.wornArmor, state.offhandGear)

    /** Reaching this level gives a Perfect Orb. */
    const val MAX_HERO_REWARD_LEVEL = 100

    fun addHeroXp(state: GameState, amount: Int): XpGain {
        var hero = state.hero
        var xp = hero.xp + amount
        var levels = 0
        var upgrades = 0
        val boxes = mutableMapOf<Item, Int>()
        while (xp >= heroXpForNext(hero.level)) {
            xp -= heroXpForNext(hero.level)
            hero = hero.copy(level = hero.level + 1, unspentPoints = hero.unspentPoints + STAT_POINTS_PER_LEVEL)
            levels++
            if (hero.level % UPGRADE_LEVEL_INTERVAL == 0) upgrades++
            levelBox(hero.level).forEach { (item, n) -> boxes[item] = (boxes[item] ?: 0) + n }
        }
        hero = hero.copy(xp = xp)
        // Level-up boxes always arrive in full, even past the bag's size (you're never
        // punished for levelling); an over-full bag just can't take anything else until you make room.
        // Every 50 levels: a new star, and its reward box.
        val starBoxes = (Stars.of(state.hero.level) + 1..Stars.of(hero.level)).map { Stars.box(it, hero.level, hero.name) }
        starBoxes.forEach { b -> b.items.forEach { (item, n) -> boxes[item] = (boxes[item] ?: 0) + n } }
        val inv = state.inventory.toMutableMap()
        boxes.forEach { (item, n) -> inv[item] = (inv[item] ?: 0) + n }
        // The hero levelled: monsters at the limit get the XP they were holding.
        val monsters = if (levels == 0) state.monsters else state.monsters.map { m ->
            if (m.heldXp <= 0) m else growDetailed(m.copy(heldXp = 0), m.heldXp, XpRules.monsterCap(hero.level)).first
        }
        return XpGain(
            state.copy(
                hero = hero, pendingUpgrades = state.pendingUpgrades + upgrades, inventory = inv, monsters = monsters,
                gear = state.gear + starBoxes.map { it.weapon },
                armor = state.armor + starBoxes.map { it.armor },
            ),
            levels,
            upgrades,
            boxes,
            starBoxes,
        )
    }

    fun levelUpText(gain: XpGain): String = buildString {
        if (gain.levelsGained > 0) append(" LEVEL UP! Now ${Stars.label(gain.state.hero.level)}.")
        gain.starBoxes.forEachIndexed { i, b ->
            val star = Stars.of(gain.state.hero.level) - gain.starBoxes.size + 1 + i
            append(" ★ STAR $star! Your star reward box holds ${b.weapon.name} (${b.weapon.rarity.label}), ${b.armor.name} (${b.armor.rarity.label}) and ${boxText(b.items)}.")
        }
        if (gain.boxItems.isNotEmpty()) append(" Level-up box: ${boxText(gain.boxItems)}.")
        if (gain.upgradesGained > 0) append(" Storage upgrade unlocked!")
        val h = gain.state.hero
        if (gain.levelsGained > 0 && !h.classChosen && h.level >= CLASS_UNLOCK_LEVEL) {
            append(" Choose your class on the Hero tab to claim a Rare weapon!")
        }
    }

    fun boxText(items: Map<Item, Int>): String = items.entries.joinToString { (i, n) -> if (n > 1) "${i.label} x$n" else i.label }

    /** Pick a class at level 5+. The first choice is free; switching costs gold. */
    fun chooseClass(state: GameState, cls: HeroClass, rng: Random = Random.Default): Update {
        val h = state.hero
        if (h.level < CLASS_UNLOCK_LEVEL) return Update(state, "Classes unlock at level $CLASS_UNLOCK_LEVEL.")
        if (h.classChosen && h.heroClass == cls) return Update(state, "You're already a ${cls.label}.")
        val cost = if (!h.classChosen) 0 else CLASS_CHANGE_COST
        if (state.gold < cost) return Update(state, "Changing class costs $cost gold.")
        // A new class means a new subclass: the old one belonged to the old class.
        var s = state.copy(hero = h.copy(heroClass = cls, classChosen = true, subclass = null), gold = state.gold - cost)
        var gift = ""
        if (!h.classChosen) {
            // Picking a path at level 5 comes with a Rare weapon suited to it.
            val type = when (cls) {
                HeroClass.WARRIOR -> WeaponType.BROADSWORD
                HeroClass.MAGE -> WeaponType.STAFF
                HeroClass.ROGUE -> WeaponType.DAGGER
                HeroClass.NONE -> WeaponType.SWORD
            }
            val w = Armory.roll(maxOf(h.level, CLASS_UNLOCK_LEVEL), rng, type, minimum = GearRarity.RARE).let {
                if (it.rarity == GearRarity.RARE) it else it.copy(rarity = GearRarity.RARE, effects = it.effects.take(1))
            }
            s = s.copy(gear = s.gear + w)
            gift = " You received a Rare ${w.name}! Equip it from the Bag."
        }
        val what = if (cls == HeroClass.NONE) "You stay an Adventurer: no strengths, no weaknesses." else "You are now a ${cls.label}!"
        val sub = if (h.subclass != null && h.level >= SUBCLASS_UNLOCK_LEVEL) " Pick a new subclass on the Hero tab." else ""
        return Update(s, what + (if (cost > 0) " (−$cost gold)" else "") + gift + sub)
    }

    /** Pick a subclass of your class at level 15. The first pick is free; switching costs gold. */
    fun chooseSubclass(state: GameState, sub: Subclass): Update {
        val h = state.hero
        if (h.level < SUBCLASS_UNLOCK_LEVEL) return Update(state, "Subclasses unlock at level $SUBCLASS_UNLOCK_LEVEL.")
        if (!h.classChosen) return Update(state, "Choose a class first.")
        if (sub.parent != h.heroClass) return Update(state, "${sub.label} is a ${sub.parent.label} subclass.")
        if (h.activeSubclass == sub) return Update(state, "You're already a ${sub.label}.")
        val cost = if (h.activeSubclass == null) 0 else SUBCLASS_CHANGE_COST
        if (state.gold < cost) return Update(state, "Switching subclass costs $cost gold.")
        return Update(
            state.copy(hero = h.copy(subclass = sub), gold = state.gold - cost),
            "You are now a ${sub.label}!" + if (cost > 0) " (−$cost gold)" else "",
        )
    }

    /**
     * Puts a skill in slot [slot] (slots open at levels 30, 45 and 60, shown as ★1 · Lv 10). Filling
     * an empty slot is free; swapping a skill out costs gold. A slot's rank
     * comes from your level, so a new skill there starts at the same rank.
     */
    fun learnSkill(state: GameState, slot: Int, skill: Trait): Update {
        val h = state.hero
        if (slot !in 0 until h.skillSlots) {
            val at = HERO_SKILL_LEVELS.getOrNull(slot) ?: return Update(state, "There's no such skill slot.")
            return Update(state, "This skill slot opens at ${Stars.levelText(at)}.")
        }
        if (!skill.forHero) return Update(state, "Only monsters can have ${skill.label}.")
        val current = h.skills.getOrNull(slot)
        if (current == skill) return Update(state, "You already have ${skill.label}.")
        if (skill in h.skills) return Update(state, "${skill.label} is already in another slot.")
        val cost = if (current == null) 0 else SKILL_CHANGE_COST
        if (state.gold < cost) return Update(state, "Swapping a skill costs $cost gold.")
        val skills = h.skills.toMutableList()
        if (current == null) skills += skill else skills[slot] = skill
        var s = state.copy(hero = h.copy(skills = skills), gold = state.gold - cost)
        var extra = ""
        // Without Kleptomaniac there's no hand free for the second weapon.
        if (current == Trait.KLEPTOMANIAC && s.offhandGear != null) {
            extra = " Your ${s.offhandGear!!.name} went back in your bag."
            s = s.copy(gear = s.gear + s.offhandGear!!, offhandGear = null)
        }
        return Update(s, "You learned ${skill.label}!" + (if (cost > 0) " (−$cost gold)" else "") + extra)
    }

    fun allocatePoint(state: GameState, attr: HeroAttribute): Update {
        val h = state.hero
        if (h.unspentPoints <= 0) return Update(state, "No points to spend.")
        val newAttrs = h.attributes + (attr to h.attr(attr) + 1)
        return Update(
            state.copy(hero = h.copy(attributes = newAttrs, unspentPoints = h.unspentPoints - 1)),
            "${attr.label} increased to ${h.attr(attr) + 1}.",
        )
    }

    /** Claim a level upgrade: the bag and monster storage both grow. */
    fun applyUpgrade(state: GameState): Update {
        if (state.pendingUpgrades <= 0) return Update(state, "No upgrades available. One is earned every $UPGRADE_LEVEL_INTERVAL levels.")
        val s = state.copy(
            pendingUpgrades = state.pendingUpgrades - 1,
            bagCapacity = state.bagCapacity + BAG_UPGRADE_AMOUNT,
            monsterCapacity = state.monsterCapacity + STORAGE_UPGRADE_AMOUNT,
        )
        return Update(s, "Bag expanded to ${s.bagCapacity} slots and monster storage to ${s.monsterCapacity}.")
    }

    /**
     * The next bought expansion's price: it starts high and grows by
     * [PAID_UPGRADE_GROWTH] each time, rounded to 50 gold, so storage grows slowly.
     */
    fun expansionCost(state: GameState, bag: Boolean): Int {
        val (base, bought) = if (bag) BAG_EXPANSION_COST to state.bagExpansionsBought else STORAGE_EXPANSION_COST to state.storageExpansionsBought
        val raw = base * Math.pow(PAID_UPGRADE_GROWTH, bought.toDouble())
        return (Math.round(raw / 50).toInt() * 50).coerceAtMost(1_000_000)
    }

    /** Buy [PAID_UPGRADE_AMOUNT] more bag slots or monster storage in the shop. */
    fun buyStorage(state: GameState, bag: Boolean): Update {
        val cost = expansionCost(state, bag)
        if (state.gold < cost) return Update(state, "This expansion costs ${"%,d".format(cost)} gold.")
        val s = state.copy(gold = state.gold - cost)
        return if (bag) {
            Update(
                s.copy(bagCapacity = s.bagCapacity + PAID_UPGRADE_AMOUNT, bagExpansionsBought = s.bagExpansionsBought + 1),
                "Bag expanded to ${s.bagCapacity + PAID_UPGRADE_AMOUNT} slots (−${"%,d".format(cost)} gold).",
            )
        } else {
            Update(
                s.copy(monsterCapacity = s.monsterCapacity + PAID_UPGRADE_AMOUNT, storageExpansionsBought = s.storageExpansionsBought + 1),
                "Monster storage expanded to ${s.monsterCapacity + PAID_UPGRADE_AMOUNT} (−${"%,d".format(cost)} gold).",
            )
        }
    }

    // ---- tasks -------------------------------------------------------------

    fun addTask(
        state: GameState,
        title: String,
        difficulty: Difficulty,
        rng: Random = Random.Default,
        nowMs: Long = System.currentTimeMillis(),
        dueMs: Long? = null,
        priority: Priority = Priority.NORMAL,
    ): Update {
        val t = title.trim()
        if (t.isEmpty()) return Update(state, "Quest needs a name.")
        val task = QuestTask(id = newId(rng), title = t, difficulty = difficulty, createdAtMs = nowMs, dueMs = dueMs, priority = priority)
        return Update(state.copy(tasks = state.tasks + task), "Quest added: $t. Its reward unlocks in ${QuestRewards.MIN_AGE_MS / 60_000} minutes.")
    }

    /** Changes a to-do's name, difficulty, due date and priority (calendar quests: priority only). */
    fun editTask(state: GameState, id: String, title: String, difficulty: Difficulty, dueMs: Long?, priority: Priority): Update {
        val task = state.tasks.find { it.id == id } ?: return Update(state, "Quest not found.")
        val t = title.trim()
        if (t.isEmpty()) return Update(state, "Quest needs a name.")
        val updated = if (task.source == TaskSource.CALENDAR) task.copy(priority = priority)
        else task.copy(title = t, difficulty = difficulty, dueMs = dueMs, priority = priority)
        return Update(state.copy(tasks = state.tasks.map { if (it.id == id) updated else it }), "Quest updated.")
    }

    /**
     * Closes a quest you won't get done: no reward, no penalty. A to-do is
     * removed; a calendar event leaves your quests for good (it stays in your
     * calendar, and syncs leave it out).
     */
    fun deleteTask(state: GameState, id: String, nowMs: Long = System.currentTimeMillis()): Update {
        val task = state.tasks.find { it.id == id } ?: return Update(state, "Quest not found.")
        val tasks = state.tasks.filterNot { it.id == id }
        if (task.source != TaskSource.CALENDAR) return Update(state.copy(tasks = tasks), "Quest removed.")
        // Remember it (the last couple of months are plenty: older events don't come back in a sync).
        val keep = state.dismissedQuests.filter { key -> (eventStartMs(key) ?: nowMs) > nowMs - DISMISSED_KEEP_MS }.toSet()
        return Update(state.copy(tasks = tasks, dismissedQuests = keep + id), "Closed \"${task.title}\". It stays in your calendar.")
    }

    /** Calendar events that have ended without being marked done (your own to-dos aren't included). */
    fun missedEvents(state: GameState, nowMs: Long): List<QuestTask> =
        state.tasks.filter { it.source == TaskSource.CALENDAR && Planner.isOverdue(it, nowMs) }

    /** Closes every missed calendar event at once (see [deleteTask]): hidden in the game, kept in your calendar. */
    fun closeMissed(state: GameState, nowMs: Long = System.currentTimeMillis()): Update {
        val missed = missedEvents(state, nowMs)
        if (missed.isEmpty()) return Update(state, "No missed events.")
        var s = state
        missed.forEach { s = deleteTask(s, it.id, nowMs).state }
        return Update(s, "Cleared ${missed.size} missed event${if (missed.size == 1) "" else "s"}. They stay in your calendar.")
    }

    /** How long closed calendar events are remembered. */
    private const val DISMISSED_KEEP_MS = 60L * 24 * 60 * 60_000

    /** A calendar quest's start, from its key ("eventId:beginMs"). */
    private fun eventStartMs(key: String): Long? = key.substringAfterLast(':', "").toLongOrNull()

    fun completeTask(state: GameState, id: String, nowMs: Long): Update {
        val task = state.tasks.find { it.id == id } ?: return Update(state, "Quest not found.")
        if (task.completed) return Update(state, "Already completed.")
        // Limits against fake quests: too new, too many today, too many Hard, or a repeat.
        val p = QuestRewards.payout(state, task, nowMs)
        var s = state.copy(
            tasks = state.tasks.map { if (it.id == id) it.copy(completedAtMs = nowMs) else it },
            questDays = state.questDays.let { it + (epochDay(nowMs) to (it[epochDay(nowMs)] ?: 0) + 1) },
            gold = state.gold + p.gold,
            huntTokens = if (p.token) minOf(MAX_HUNT_TOKENS, state.huntTokens + 1) else state.huntTokens,
            stats = state.stats.copy(tasksCompleted = state.stats.tasksCompleted + 1),
            questLimits = p.limits,
        )
        val gain = addHeroXp(s, p.xp)
        s = gain.state
        val notes = p.notes.joinToString("") { " $it" }
        val text = if (p.share > 0) "Quest complete! +${p.xp} XP, +${p.gold} gold, +1 hunt token.$notes" else "Quest done!$notes"
        return Update(s, text + levelUpText(gain))
    }

    fun difficultyFor(event: CalendarEvent): Difficulty {
        if (event.allDay) return Difficulty.NORMAL
        val minutes = (event.endMs - event.startMs) / 60_000
        return when {
            minutes <= 30 -> Difficulty.EASY
            minutes >= 120 -> Difficulty.HARD
            else -> Difficulty.NORMAL
        }
    }

    /**
     * Replace not-yet-completed calendar quests with [events]. Completed quests
     * are kept (their rewards were already paid) and never re-added.
     */
    fun syncCalendar(state: GameState, events: List<CalendarEvent>): Update {
        val completedKeys = state.tasks.filter { it.source == TaskSource.CALENDAR && it.completed }.map { it.id }.toSet()
        val kept = state.tasks.filter { it.source == TaskSource.MANUAL || it.completed }
        // Priorities set on calendar quests survive a re-sync.
        val priorities = state.tasks.filter { it.source == TaskSource.CALENDAR }.associate { it.id to it.priority }
        val fresh = events.filter { it.key !in completedKeys && it.key !in state.dismissedQuests }.map {
            QuestTask(
                id = it.key,
                title = it.title.ifBlank { "(Untitled event)" },
                difficulty = difficultyFor(it),
                source = TaskSource.CALENDAR,
                startMs = it.startMs,
                endMs = it.endMs,
                calendarName = it.calendarName,
                priority = priorities[it.key] ?: Priority.NORMAL,
            )
        }
        return Update(state.copy(tasks = kept + fresh), "Synced ${fresh.size} calendar quest(s).")
    }

    // ---- items -------------------------------------------------------------

    /** Adds items while respecting bag capacity. Returns the new state and what didn't fit. */
    /**
     * Adds items while respecting bag capacity, unless [overfill]: then they
     * all go in even past the bag's size (what a dungeon run or an adventure
     * that set out with room brought back). Once over, nothing else fits.
     */
    /**
     * Adds [items] to the Bag. Earned items always fit, even past the bag's
     * size (an over-full bag only stops buying, forging and taking gear
     * off); pass [overfill] false only where the caller has already checked
     * for room. Returns the state and how many didn't fit (0 when overfilling).
     */
    fun addItems(state: GameState, items: Map<Item, Int>, overfill: Boolean = true): Pair<GameState, Int> {
        var inv = state.inventory
        var space = state.bagCapacity - state.bagCount
        var lost = 0
        for ((item, qty) in items) {
            val fit = if (overfill) qty else minOf(qty, space.coerceAtLeast(0))
            if (fit > 0) inv = inv + (item to (inv[item] ?: 0) + fit)
            space -= fit
            lost += qty - fit
        }
        return state.copy(inventory = inv) to lost
    }

    internal fun removeItems(inv: Map<Item, Int>, used: Map<Item, Int>): Map<Item, Int> {
        val out = inv.toMutableMap()
        for ((item, n) in used) {
            val left = (out[item] ?: 0) - n
            if (left <= 0) out.remove(item) else out[item] = left
        }
        return out
    }

    fun buy(state: GameState, item: Item, qty: Int = 1, nowMs: Long = System.currentTimeMillis()): Update {
        if (item.price <= 0) return Update(state, "${item.label} can't be bought.")
        if (state.hero.level < item.minLevel) return Update(state, "${item.label}s unlock at ${Stars.levelText(item.minLevel)}.")
        // Buying the same thing again and again marks it up for a while (see Prices).
        val cost = Prices.price(state, item, qty, nowMs)
        if (state.gold < cost) return Update(state, "Not enough gold.")
        if (state.bagCount + qty > state.bagCapacity) return Update(state, "Bag is full (${state.bagCount}/${state.bagCapacity}).")
        val (s, _) = addItems(Prices.record(state.copy(gold = state.gold - cost), item, qty, nowMs), mapOf(item to qty))
        val markup = Prices.multiplier(s, item, nowMs)
        return Update(s, "Bought ${item.label} for $cost gold." + if (markup > 1.0) " It's selling fast: the next one is ${Prices.unitPrice(s, item, nowMs)}." else "")
    }

    fun discard(state: GameState, item: Item): Update {
        if (state.count(item) <= 0) return Update(state, "None to discard.")
        return Update(state.copy(inventory = removeItems(state.inventory, mapOf(item to 1))), "Discarded ${item.label}.")
    }

    // ---- monsters ----------------------------------------------------------

    fun toggleParty(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        return if (uid !in state.party && uid in Adventures.awayUids(state)) {
            Update(state, "${m.displayName} is out on an adventure.")
        } else if (uid in state.swaps) {
            Update(state.copy(swaps = state.swaps - uid), "${m.displayName} left the swap slots.")
        } else if (uid in state.party) {
            Update(state.copy(party = state.party - uid), "${m.displayName} left the battle party.")
        } else if (state.party.size >= MAX_PARTY_SIZE) {
            Update(state, "Battle party is full ($MAX_PARTY_SIZE). Remove a monster first.")
        } else {
            Update(state.copy(party = state.party + uid), "${m.displayName} joined the battle party.")
        }
    }

    /**
     * Puts a monster into battle-party slot [index] (drag and drop). A bench
     * monster replaces whoever is there (they go back to the bench); a party
     * monster swaps places.
     */
    fun setPartySlot(state: GameState, uid: String, index: Int): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (uid !in state.party && uid in Adventures.awayUids(state)) return Update(state, "${m.displayName} is out on an adventure.")
        val party = state.party.filter { p -> state.monsters.any { it.uid == p } }.toMutableList()
        val slot = index.coerceIn(0, MAX_PARTY_SIZE - 1)
        val from = party.indexOf(uid)
        // From a swap slot: trade places with whoever is in the party slot.
        val swapAt = state.swaps.indexOf(uid)
        if (swapAt >= 0) {
            val swaps = state.swaps.toMutableList()
            val out = party.getOrNull(slot)
            if (out != null) {
                party[slot] = uid
                swaps[swapAt] = out
            } else {
                party += uid
                swaps.removeAt(swapAt)
            }
            return Update(state.copy(party = party, swaps = swaps), "${m.displayName} joined the battle party.")
        }
        return if (from >= 0) {
            if (slot >= party.size || slot == from) return Update(state.copy(party = party), "")
            val other = party[slot]
            party[slot] = uid
            party[from] = other
            val otherName = state.monsters.find { it.uid == other }?.displayName ?: "them"
            Update(state.copy(party = party), "${m.displayName} swapped places with $otherName.")
        } else if (slot < party.size) {
            val out = party[slot]
            party[slot] = uid
            val outName = state.monsters.find { it.uid == out }?.displayName ?: "Your monster"
            Update(state.copy(party = party), "${m.displayName} joined the battle party; $outName went to the bench.")
        } else {
            party += uid
            Update(state.copy(party = party), "${m.displayName} joined the battle party.")
        }
    }

    /**
     * Puts a monster into swap slot [index] (drag and drop): it comes along to
     * fights and can be sent in mid-battle. A party monster dropped here trades
     * places with whoever is in the slot.
     */
    fun setSwapSlot(state: GameState, uid: String, index: Int): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (uid !in state.team && uid in Adventures.awayUids(state)) return Update(state, "${m.displayName} is out on an adventure.")
        val swaps = state.swaps.filter { p -> state.monsters.any { it.uid == p } }.toMutableList()
        val party = state.party.toMutableList()
        val slot = index.coerceIn(0, MAX_SWAPS - 1)
        val out = swaps.getOrNull(slot)
        val from = swaps.indexOf(uid)
        if (from >= 0) {
            if (out == null || from == slot) return Update(state.copy(swaps = swaps), "")
            swaps[slot] = uid
            swaps[from] = out
            return Update(state.copy(swaps = swaps), "")
        }
        val partyAt = party.indexOf(uid)
        if (out != null) swaps[slot] = uid else swaps += uid
        if (partyAt >= 0) {
            if (out != null) party[partyAt] = out else party.removeAt(partyAt)
        }
        val note = if (out != null && partyAt < 0) " ${state.monsters.find { it.uid == out }?.displayName ?: "Your monster"} went back to the collection." else ""
        return Update(state.copy(party = party, swaps = swaps), "${m.displayName} is in a swap slot.$note")
    }

    fun release(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (state.monsters.size <= 1) return Update(state, "You can't release your last monster.")
        if (uid in Adventures.awayUids(state)) return Update(state, "${m.displayName} is out on an adventure.")
        val gained = releaseCandy(m)
        val family = m.species.family
        return Update(
            state.copy(
                monsters = state.monsters.filterNot { it.uid == uid },
                party = state.party - uid,
                swaps = state.swaps - uid,
                candy = state.candy + (family to (state.candy[family] ?: 0) + gained),
            ),
            "${m.displayName} was released into the wild. +$gained ${candyName(m.species)}.",
        )
    }

    /**
     * Releases several monsters at once (from the collection). Monsters on
     * adventures are skipped, and at least one monster always stays.
     */
    fun releaseMany(state: GameState, uids: Collection<String>): Update {
        val away = Adventures.awayUids(state)
        var s = state
        var released = 0
        var candy = 0
        for (uid in uids.distinct()) {
            val m = s.monsters.find { it.uid == uid } ?: continue
            if (uid in away || s.monsters.size <= 1) continue
            val gained = releaseCandy(m)
            val family = m.species.family
            s = s.copy(
                monsters = s.monsters.filterNot { it.uid == uid },
                party = s.party - uid,
                swaps = s.swaps - uid,
                candy = s.candy + (family to (s.candy[family] ?: 0) + gained),
            )
            released++
            candy += gained
        }
        if (released == 0) return Update(state, "Nobody was released.")
        return Update(s, "Released $released monster${if (released == 1) "" else "s"} into the wild. +$candy candy.")
    }

    // ---- candy ----------------------------------------------------------------

    /** Every evolution line has its own candy, named after its first form. */
    fun candyName(sp: Species): String = "${Bestiary.lineOf(sp).first().name} candy"

    fun candyOf(state: GameState, sp: Species): Int = state.candy[sp.family] ?: 0

    /** Candy for taming a monster. */
    const val TAME_CANDY = 3
    /** Taming a line's first form gives more candy, so catching the small ones pays. */
    const val FIRST_FORM_CANDY = 5

    /** Candy for taming [species]. */
    fun tameCandy(species: Species): Int = if (species.stage == 1 && species.stages > 1) FIRST_FORM_CANDY else TAME_CANDY

    fun addCandy(state: GameState, sp: Species, n: Int): GameState =
        state.copy(candy = state.candy + (sp.family to candyOf(state, sp) + n))

    private val BOSS_ENCOUNTERS = setOf(
        EncounterType.BOSS_CHALLENGE, EncounterType.BOSS_TAMING, EncounterType.SILVER_BOSS, EncounterType.BOX_BOSS, EncounterType.DEN,
    )

    /** Candy for defeating a wild (or thug's) monster; bosses give more. */
    const val DEFEAT_CANDY = 1
    const val BOSS_DEFEAT_CANDY = 3

    /** Candy to evolve from each stage: 1 to 2, 2 to 3, 3 to 4. */
    val EVOLVE_CANDY = listOf(10, 25, 50)

    fun evolveCost(m: Monster): Int = EVOLVE_CANDY[(m.species.stage - 1).coerceIn(0, EVOLVE_CANDY.size - 1)]

    /**
     * Evolves a monster that has reached its evolution level, for candy of its
     * line (from taming, beating and releasing monsters of that line).
     */
    fun evolve(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val into = m.species.evolvesTo?.let { Bestiary.get(it) } ?: return Update(state, "${m.displayName} doesn't evolve.")
        val at = m.species.evolveLevel ?: return Update(state, "${m.displayName} doesn't evolve.")
        if (m.level < at) return Update(state, "${m.displayName} can evolve at level $at.")
        val cost = evolveCost(m)
        val have = candyOf(state, m.species)
        if (have < cost) return Update(state, "Evolving needs $cost ${candyName(m.species)} (you have $have). Tame or beat more of its kind.")
        val before = m.learnedMoves.map { it.id }.toSet()
        var evolved = m.copy(speciesId = into.id)
        // New moves fill empty slots of a custom loadout.
        val fresh = evolved.learnedMoves.filter { it.id !in before }
        val custom = evolved.moves
        if (custom != null) evolved = evolved.copy(moves = (custom + fresh.map { it.id }).take(Moves.MAX_MOVES))
        val family = m.species.family
        val s = state.copy(
            monsters = state.monsters.map { if (it.uid == uid) evolved else it },
            candy = state.candy + (family to have - cost),
        ).withJournal()
        val learned = if (fresh.isNotEmpty()) " It learned ${fresh.joinToString { it.name }}!" else ""
        return Update(s, "${m.displayName} evolved into ${into.name}! (−$cost candy)$learned")
    }

    /** Releasing gives more candy for evolved and higher-level monsters. */
    fun releaseCandy(m: Monster): Int = m.species.stage + m.level / 15

    /** Candy to level up from [level]; it climbs as the monster grows. */
    fun candyCost(level: Int): Int = 1 + level / 5

    /**
     * Spend candy to raise a monster one level (it may evolve or learn moves).
     * Its stat points (IVs) never change: better ones come from taming more monsters.
     */
    fun candyLevelUp(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (m.level >= MAX_MONSTER_LEVEL) return Update(state, "${m.displayName} is already max level.")
        if (m.level >= XpRules.monsterCap(state)) return Update(state, "${m.displayName} is ${XpRules.MONSTER_LEAD} levels ahead of you: level up your hero first.")
        val cost = candyCost(m.level)
        val have = candyOf(state, m.species)
        if (have < cost) return Update(state, "Need $cost ${candyName(m.species)} (you have $have). Release ${m.species.family.removeSuffix(" line")}-line monsters to get more.")
        val (grown, notes) = grow(m, (m.xpToNext - m.xp).coerceAtLeast(1))
        val family = m.species.family
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) grown else it },
                candy = state.candy + (family to have - cost),
            ),
            "${m.displayName} grew to Lv ${grown.level}! (-$cost candy)" + notes.joinToString("") { " $it" },
        )
    }

    fun rename(state: GameState, uid: String, name: String): Update {
        val n = name.trim().take(16).ifEmpty { null }
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(nickname = n) else it }),
            "Renamed.",
        )
    }


    /**
     * Adds XP, then reports evolutions and newly learned moves. New moves fill
     * empty slots of a custom loadout; automatic loadouts update themselves.
     */
    /** Teaches [uid] the move [newId] in place of [replaceId]. */
    fun learnMove(state: GameState, uid: String, newId: String, replaceId: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "")
        val ids = m.activeMoves.map { it.id }
        if (replaceId !in ids || newId in ids) return Update(state, "")
        val u = setMoves(state, uid, ids.map { if (it == replaceId) newId else it })
        return u.copy(message = "${m.displayName} forgot ${Moves.get(replaceId).name} and learned ${Moves.get(newId).name}!")
    }

    /** Said when a monster's XP is held because it's at the hero's lead. */
    fun heldNote(m: Monster): String =
        "${m.displayName} is ${XpRules.MONSTER_LEAD} levels ahead of you: its XP is saved until your hero levels up."

    fun grow(m: Monster, xp: Int, cap: Int = MAX_MONSTER_LEVEL): Pair<Monster, List<String>> {
        val (after, up) = growDetailed(m, xp, cap)
        val notes = mutableListOf<String>()
        if (after.heldXp > m.heldXp) notes += heldNote(after)
        if (up?.evolvedFrom != null) notes += "${up.evolvedFrom} evolved into ${after.species.name}!"
        up?.learned?.forEach { notes += "${after.displayName} learned $it!" }
        up?.canLearn?.forEach { notes += "${after.displayName} can learn $it. Swap it in from its stat page." }
        return after to notes
    }

    private fun monsterStats(m: Monster) = m.stats.let { listOf(it.hp, it.attack, it.magic, it.defense, it.speed) }

    /** Like [grow], but returns what changed as a [LevelUp] (null if it didn't level). */
    fun growDetailed(m: Monster, xp: Int, cap: Int = MAX_MONSTER_LEVEL): Pair<Monster, LevelUp?> {
        val before = m.learnedMoves.map { it.id }.toSet()
        var after = m.gainXp(xp, cap)
        if (after.level == m.level) return after to null
        val learned = mutableListOf<String>()
        val canLearn = mutableListOf<String>()
        val canLearnIds = mutableListOf<String>()
        for (mv in after.learnedMoves.filter { it.id !in before }) {
            val custom = after.moves
            if (custom != null && custom.size < Moves.MAX_MOVES) {
                after = after.copy(moves = custom + mv.id)
                learned += mv.name
            } else if (custom != null) {
                canLearn += mv.name
                canLearnIds += mv.id
            } else {
                learned += mv.name
            }
        }
        val labels = listOf("HP", "Attack", "Magic", "Defense", "Speed")
        val b = monsterStats(m)
        val a = monsterStats(after)
        return after to LevelUp(
            key = m.uid,
            name = after.displayName,
            speciesId = after.speciesId,
            fromLevel = m.level,
            toLevel = after.level,
            stats = labels.indices.map { StatLine(labels[it], b[it], a[it]) },
            learned = learned,
            canLearn = canLearn,
            canLearnIds = canLearnIds,
            loadout = after.activeMoves.map { it.id },
            evolvedFrom = if (after.speciesId != m.speciesId) m.species.name else null,
            notes = traitNotes(m, after) + listOfNotNull(
                after.canEvolveTo?.takeIf { m.canEvolveTo == null }?.let {
                    "Ready to evolve into ${it.name}! Evolve it on its page (${evolveCost(after)} candy)."
                },
            ),
        )
    }

    /** A monster's skill: gained at level 30, stronger every 10 levels after. */
    private fun traitNotes(before: Monster, after: Monster): List<String> {
        val (t, r) = after.trait ?: return emptyList()
        val was = before.trait?.second ?: 0
        return when {
            was == 0 -> listOf("Gained the skill ${t.label}! ${t.describe(r, hero = false)}.")
            r > was -> listOf("${t.label} ranked up to $r: ${t.describe(r, hero = false)}.")
            else -> emptyList()
        }
    }

    /** The hero's level-up as a [LevelUp], or null if no level was gained. */
    fun heroLevelUp(before: Hero, gain: XpGain): LevelUp? {
        if (gain.levelsGained <= 0) return null
        val h = gain.state.hero
        val notes = buildList {
            add("+${gain.levelsGained * STAT_POINTS_PER_LEVEL} attribute points to spend on the Hero tab.")
            if (gain.upgradesGained > 0) add("Storage upgrade unlocked!")
            if (!h.classChosen && h.level >= CLASS_UNLOCK_LEVEL) add("Choose your class on the Hero tab to claim a Rare weapon!")
            if (gain.boxItems.isNotEmpty()) add("Level-up box: ${boxText(gain.boxItems)}.")
            if (gain.state.bagCount > gain.state.bagCapacity) add("Your bag is over full: use, sell or drop items before picking up more.")
            Item.entries.filter { it.price > 0 && it.minLevel in (before.level + 1)..h.level }.forEach { add("${it.label}s are now in the shop!") }
            Armory.MATERIAL_TIERS.filter { it.fromLevel in (before.level + 1)..h.level }.forEach { add("Better gear: ${it.note.lowercase()} from now on.") }
            if (before.level < SUBCLASS_UNLOCK_LEVEL && h.level >= SUBCLASS_UNLOCK_LEVEL) add("Subclasses unlocked! Pick one on the Hero tab.")
            HERO_SKILL_LEVELS.forEachIndexed { i, at ->
                if (before.level < at && h.level >= at) add("Skill slot ${i + 1} opened! Pick a skill on the Hero tab.")
            }
            for ((t, r) in h.skillRanks) if (before.rankOf(t) in 1 until r) add("${t.label} ranked up to $r: ${t.describe(r)}.")
        }
        return LevelUp(
            key = Combatant.HERO_KEY,
            name = h.name,
            speciesId = null,
            fromLevel = before.level,
            toLevel = h.level,
            stats = listOf(
                StatLine("HP", before.maxHp, h.maxHp),
                StatLine("Energy", before.maxMp, h.maxMp),
                StatLine("Attack", before.attack, h.attack),
                StatLine("Magic", before.magic, h.magic),
                StatLine("Defense", before.defense, h.defense),
                StatLine("Speed", before.speed, h.speed),
            ),
            notes = notes,
        )
    }

    /** Sets a monster's 4 move slots (must be moves it has learned). */
    fun setMoves(state: GameState, uid: String, moveIds: List<String>): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val learned = m.learnedMoves.map { it.id }.toSet()
        val valid = moveIds.distinct().filter { it in learned }.take(Moves.MAX_MOVES)
        if (valid.isEmpty()) return Update(state, "A monster needs at least one move.")
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(moves = valid) else it }),
            "${m.displayName}'s moves updated.",
        )
    }

    fun restore(state: GameState, uid: String): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (!m.fainted) return Update(state, "${m.displayName} isn't hurt.")
        if (state.count(Item.RESTORE_POTION) <= 0) return Update(state, "You need a Restore Potion (Shop or loot).")
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) it.copy(fainted = false, damage = 0) else it },
                inventory = removeItems(state.inventory, mapOf(Item.RESTORE_POTION to 1)),
            ),
            "${m.displayName} is back on its feet!",
        )
    }

    // ---- rest: everyone slowly heals over time --------------------------------

    /** Share of max HP healed each hour, for the hero and every monster. */
    const val REGEN_PERCENT_PER_HOUR = 5
    /** A knocked-out monster wakes up once it's back to this share of max HP. */
    const val WAKE_PERCENT = 10
    private const val HOUR_MS = 3_600_000L

    private fun perHour(maxHp: Int) = maxOf(1, (maxHp * REGEN_PERCENT_PER_HOUR + 99) / 100)
    private fun wakeHp(maxHp: Int) = maxOf(1, (maxHp * WAKE_PERCENT + 99) / 100)

    /** A fainted monster's missing HP (older saves stored 0 for knocked out, meaning empty). */
    private fun faintedDamage(m: Monster): Int = if (m.damage <= 0) m.stats.hp else m.damage

    /** Whole hours until a knocked-out monster wakes up by resting (0 if it isn't knocked out). */
    fun hoursUntilAwake(m: Monster): Int {
        if (!m.fainted) return 0
        val max = m.stats.hp
        val missing = wakeHp(max) - (max - faintedDamage(m))
        return if (missing <= 0) 0 else (missing + perHour(max) - 1) / perHour(max)
    }

    /**
     * Rest: every full hour since the last check heals the hero and every
     * monster [REGEN_PERCENT_PER_HOUR]% of max HP. Knocked-out monsters
     * recover too and wake up at [WAKE_PERCENT]% (a Restore Potion is instant).
     */
    /** Energy fully recovers over this long at rest (walking is faster). */
    const val ENERGY_FULL_MS = 6 * HOUR_MS
    /** Energy back for every [STEPS_PER_REWARD] steps walked. */
    const val ENERGY_PER_REWARD = 2

    /** Energy comes back a point at a time: all of it over [ENERGY_FULL_MS]. */
    fun regenEnergy(state: GameState, nowMs: Long): GameState {
        val max = state.hero.maxMp.coerceAtLeast(1)
        if (state.heroEnergyUsed <= 0 || state.energyRegenMs <= 0L || nowMs < state.energyRegenMs) return state.copy(energyRegenMs = nowMs)
        val perPoint = ENERGY_FULL_MS / max
        val points = ((nowMs - state.energyRegenMs) / perPoint).toInt()
        if (points <= 0) return state
        return state.copy(
            heroEnergyUsed = (state.heroEnergyUsed - points).coerceAtLeast(0),
            energyRegenMs = state.energyRegenMs + points * perPoint,
        )
    }

    fun regenerate(state0: GameState, nowMs: Long): Update {
        val state = regenEnergy(state0, nowMs)
        if (state.lastRegenMs <= 0L || nowMs < state.lastRegenMs) return Update(state.copy(lastRegenMs = nowMs), "")
        val hours = ((nowMs - state.lastRegenMs) / HOUR_MS).toInt()
        if (hours <= 0) return Update(state, "")
        val woke = mutableListOf<String>()
        val heroHeal = perHour(state.hero.maxHp) * hours
        val monsters = state.monsters.map { m ->
            val max = m.stats.hp
            val heal = perHour(max) * hours
            if (m.fainted) {
                val damage = (faintedDamage(m) - heal).coerceAtLeast(0)
                if (max - damage >= wakeHp(max)) {
                    woke += m.displayName
                    m.copy(fainted = false, damage = damage)
                } else {
                    m.copy(damage = damage)
                }
            } else {
                m.copy(damage = (m.damage - heal).coerceAtLeast(0))
            }
        }
        val s = state.copy(
            heroDamage = (state.heroDamage - heroHeal).coerceAtLeast(0),
            monsters = monsters,
            lastRegenMs = state.lastRegenMs + hours * HOUR_MS,
        )
        val msg = when (woke.size) {
            0 -> ""
            1 -> "${woke[0]} rested and is back on its feet!"
            else -> "${woke.joinToString()} rested and are back on their feet!"
        }
        return Update(s, msg)
    }

    /**
     * Safety net: if every monster you own has fainted, your strongest one
     * recovers on its own once per day so you're never stuck.
     */
    fun dailyRecovery(state: GameState, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Update? {
        if (state.monsters.isEmpty() || state.monsters.any { !it.fainted }) return null
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        if (state.lastRecoveryDay == today) return null
        val best = state.monsters.maxBy { it.level }
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == best.uid) it.copy(fainted = false, damage = 0) else it },
                lastRecoveryDay = today,
            ),
            "${best.displayName} recovered overnight. Restore Potions heal the rest.",
        )
    }

    /** The shadow curse lifted: perfect one stat of the player's choice. */
    fun perfectStat(state: GameState, uid: String, stat: Stat): Update {
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (!m.cureReady) return Update(state, "${m.displayName} has no stat to perfect.")
        if (stat !in MONSTER_STATS) return Update(state, "Choose HP, Attack, Defense or Speed.")
        val iv = m.ivs
        val perfected = when (stat) {
            Stat.HP -> iv.copy(hp = MAX_IV)
            Stat.ATTACK -> iv.copy(attack = MAX_IV, magic = MAX_IV)
            Stat.DEFENSE -> iv.copy(defense = MAX_IV)
            Stat.SPEED -> iv.copy(speed = MAX_IV)
            Stat.MAGIC -> iv
        }
        return Update(
            state.copy(monsters = state.monsters.map { if (it.uid == uid) it.copy(ivs = perfected, cureReady = false) else it }),
            "${m.displayName}'s ${stat.label} is now perfect!",
        )
    }

    /** Heal the hero ([Combatant.HERO_KEY]) or a monster with a potion outside battle. */
    /** How much a healing potion restores for someone with [maxHp] (before capping at full). */
    fun potionHeal(item: Item, maxHp: Int): Int = maxOf(50, (maxHp * item.power).toInt())

    fun usePotion(state: GameState, item: Item, target: String): Update {
        if (state.count(item) <= 0) return Update(state, "No ${item.label} left.")
        val spent = state.copy(inventory = removeItems(state.inventory, mapOf(item to 1)))
        // A Mana Draught outside battle restores the hero's Energy.
        if (item.kind == ItemKind.HEAL_MP) {
            if (state.heroEnergyUsed <= 0) return Update(state, "Your Energy is already full.")
            val back = minOf(state.heroEnergyUsed, item.power.toInt())
            return Update(spent.copy(heroEnergyUsed = state.heroEnergyUsed - back), "${item.label}: you recover $back Energy.")
        }
        if (item.kind != ItemKind.HEAL_HP) return Update(state, "${item.label} can't be used here.")
        if (target == Combatant.HERO_KEY) {
            if (state.heroDamage <= 0) return Update(state, "You're already at full health.")
            val heal = potionHeal(item, state.hero.maxHp)
            return Update(spent.copy(heroDamage = (state.heroDamage - heal).coerceAtLeast(0)), "${item.label}: you recover $heal HP.")
        }
        val m = state.monsters.find { it.uid == target } ?: return Update(state, "Monster not found.")
        if (m.fainted) return Update(state, "${m.displayName} has fainted. Only a Restore Potion can help.")
        if (m.damage <= 0) return Update(state, "${m.displayName} is already at full health.")
        val heal = potionHeal(item, m.stat(Stat.HP))
        return Update(
            spent.copy(monsters = state.monsters.map { if (it.uid == target) it.copy(damage = (it.damage - heal).coerceAtLeast(0)) else it }),
            "${item.label}: ${m.displayName} recovers $heal HP.",
        )
    }

    fun treatXp(m: Monster): Int = maxOf(Item.TREAT.power.toInt(), m.xpToNext * 2 / 5)

    /** A Mood Crystal: [uid] gets a new personality, different from its old one. */
    fun rerollPersonality(state: GameState, uid: String, rng: Random = Random.Default): Update {
        if (state.count(Item.MOOD_CRYSTAL) <= 0) return Update(state, "No Mood Crystals. Earn them from phone-free sessions of an hour or more.")
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        val next = Personality.entries.filter { it != m.personality }.random(rng)
        val changed = m.copy(personality = next)
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) changed else it },
                inventory = removeItems(state.inventory, mapOf(Item.MOOD_CRYSTAL to 1)),
            ),
            "The Mood Crystal glows... ${m.displayName} is now ${next.label} (was ${m.personality.label}).",
        )
    }

    fun feedTreat(state: GameState, uid: String): Update {
        if (state.count(Item.TREAT) <= 0) return Update(state, "No treats. Buy some in the shop.")
        val m = state.monsters.find { it.uid == uid } ?: return Update(state, "Monster not found.")
        if (m.level >= MAX_MONSTER_LEVEL) return Update(state, "${m.displayName} is already max level.")
        if (m.level >= XpRules.monsterCap(state)) return Update(state, "${m.displayName} is ${XpRules.MONSTER_LEAD} levels ahead of you: level up your hero first.")
        val xp = treatXp(m)
        val (fed, notes) = grow(m, xp, XpRules.monsterCap(state))
        val lvlMsg = (if (fed.level > m.level) " Grew to Lv ${fed.level}!" else "") + notes.joinToString("") { " $it" }
        return Update(
            state.copy(
                monsters = state.monsters.map { if (it.uid == uid) fed else it },
                inventory = removeItems(state.inventory, mapOf(Item.TREAT to 1)),
            ),
            "${m.displayName} gobbles the treat. +$xp XP.$lvlMsg",
        )
    }

    // ---- encounters --------------------------------------------------------

    /** Hero XP for taming a monster: more than beating it, so catching always pays. */
    fun captureXp(level: Int): Int = level * 4

    fun wildLevel(heroLevel: Int, rng: Random): Int = (heroLevel + rng.nextInt(-2, 3)).coerceAtLeast(1)

    fun bossLevel(heroLevel: Int): Int = maxOf(8, heroLevel + 5)

    /** Spends a hunt token for a wild encounter anywhere. */
    fun startHunt(state: GameState, rng: Random = Random.Default): Pair<GameState, Monster?> {
        if (state.huntTokens <= 0) return state to null
        val level = wildLevel(state.hero.level, rng)
        val enemy = Monster.create(Bestiary.randomWild(rng, level), level, rng).let { it.copy(shiny = rollShiny(rng)) }.withWildMoves(rng)
        return state.copy(huntTokens = state.huntTokens - 1) to enemy
    }

    fun newBattle(
        state: GameState,
        enemy: Monster,
        encounter: EncounterType,
        shrineId: String? = null,
        rng: Random = Random.Default,
        pack: List<Monster> = emptyList(),
        ambush: Boolean = false,
        thug: Thug? = null,
        fullTeam: Boolean = encounter in FULL_TEAM_ENCOUNTERS,
        /** The real weather where the fight happens. */
        weather: LiveWeather? = null,
        /** Better taming odds (a rare visitor). */
        captureBoost: Double = 1.0,
        /** A trainer's monsters waiting to step in, a cap on your swaps, and the trainer's name (a nomad). */
        enemyReserve: List<Monster> = emptyList(),
        swapLimit: Int? = null,
        trainer: String? = null,
        /** The tutorial dungeon's gentle fights. */
        tutorial: Boolean = false,
        /** The monster who fights beside the hero against a lone foe (see [leadOptions]). */
        lead: String? = null,
    ) = Battle(
        state.hero,
        // Monsters out on adventures can't fight.
        state.partyMonsters.filter { it.uid !in Adventures.awayUids(state) },
        enemy, encounter, state.inventory, state.storageFull, shrineId, rng, state.equippedGear, state.heroDamage, state.wornArmor,
        storageRoom = (state.monsterCapacity - state.monsters.size).coerceAtLeast(0),
        extraEnemies = pack,
        ambush = ambush,
        offhand = state.offhandGear,
        swaps = state.swapMonsters.filter { it.uid !in Adventures.awayUids(state) },
        heroEnergyUsed = state.heroEnergyUsed,
        thug = thug,
        fullTeam = fullTeam,
        startWeather = weather?.battle ?: Weather.CLEAR,
        captureBoost = captureBoost,
        enemyReserve = enemyReserve,
        swapLimit = swapLimit,
        trainer = trainer,
        restFactor = Rest.today(state, System.currentTimeMillis()).statFactor,
        tutorial = tutorial,
        lead = lead,
        liveWeather = weather,
    )

    /** Whether a fight is against a lone monster: the hero and one monster of your choice face it. */
    fun isOneOnOne(pack: List<Monster>, thug: Thug?, fullTeam: Boolean): Boolean = pack.isEmpty() && thug == null && !fullTeam

    /**
     * The monsters who could fight beside the hero against a lone foe: the
     * ones the fight would bring (standing, not away on an adventure, up to
     * [swapLimit]).
     */
    fun leadOptions(state: GameState, swapLimit: Int? = null): List<Monster> {
        val away = Adventures.awayUids(state)
        val party = state.partyMonsters.filter { it.uid !in away && !it.fainted }.take(MAX_PARTY_SIZE)
        val swaps = state.swapMonsters.filter { it.uid !in away && !it.fainted }.take(MAX_SWAPS)
        val all = party + swaps
        return swapLimit?.let { all.take(it) } ?: all
    }

    /** Party monsters able to fight right now (a thug brings as many). */
    fun fightingCount(state: GameState): Int {
        val away = Adventures.awayUids(state)
        return state.partyMonsters.count { !it.fainted && it.uid !in away }
    }

    fun applyBattleResult(state: GameState, r: BattleResult, nowMs: Long): Update {
        var s = state.copy(inventory = removeItems(state.inventory, r.itemsUsed))
        val msg = StringBuilder()
        val levelUps = mutableListOf<LevelUp>()
        when (r.outcome) {
            BattleOutcome.WON, BattleOutcome.CAPTURED -> {
                if (r.outcome == BattleOutcome.WON) {
                    msg.append("Victory! +${r.heroXp} XP, +${r.goldWon} gold.")
                    s = s.copy(gold = s.gold + r.goldWon, stats = s.stats.copy(battlesWon = s.stats.battlesWon + 1))
                    // Beating monsters gives candy of their lines (bosses give more).
                    val perMonster = if (r.encounter in BOSS_ENCOUNTERS) BOSS_DEFEAT_CANDY else DEFEAT_CANDY
                    val won = r.defeated.map { Bestiary.get(it) }.groupBy { it.family }
                    won.forEach { (_, list) -> s = addCandy(s, list.first(), perMonster * list.size) }
                    if (won.isNotEmpty()) {
                        msg.append(" ").append(won.values.joinToString { list -> "+${perMonster * list.size} ${candyName(list.first())}" }).append(".")
                    }
                } else {
                    val names = r.tamed.map { it.displayName }
                    msg.append(
                        if (names.size > 1) "${names.dropLast(1).joinToString()} and ${names.last()} were tamed! +${r.heroXp} XP."
                        else "${r.enemy.displayName} was tamed! +${r.heroXp} XP.",
                    )
                }
                // Monster XP: level-ups are shown as banners and a pop-up, not in the message.
                s = s.copy(
                    monsters = s.monsters.map { m ->
                        val xp = r.monsterXp[m.uid] ?: return@map m
                        val (after, up) = growDetailed(m, xp, XpRules.monsterCap(s))
                        up?.let { levelUps += it }
                        after
                    },
                )
                // Fighting side by side builds the bond.
                val (bonded, grew) = Bonds.add(s, r.fought, Bonds.PER_BATTLE)
                s = bonded
                msg.append(Bonds.grewText(grew))
                // Drops
                if (r.drops.isNotEmpty()) {
                    // Everything a battle drops comes home, even past the bag's size.
                    val (withDrops, lost) = addItems(s, r.drops)
                    s = withDrops
                    msg.append(" Found: ").append(r.drops.entries.joinToString { "${it.key.label} x${it.value}" }).append(".")
                    if (lost > 0) msg.append(" (Bag full: $lost item(s) left behind.)")
                }
                val heroBefore = s.hero
                val gain = addHeroXp(s, r.heroXp)
                s = gain.state
                heroLevelUp(heroBefore, gain)?.let { levelUps.add(0, it) }
                // Boss shrine progress
                if (r.shrineId != null && r.encounter == EncounterType.SILVER_BOSS) {
                    val rec = s.shrines[r.shrineId] ?: ShrineRecord()
                    s = s.copy(shrines = s.shrines + (r.shrineId to rec.copy(lastVisitMs = nowMs)))
                } else if (r.shrineId != null && r.encounter == EncounterType.DEN) {
                    // A den is beaten, not tamed: now come the taming throws.
                    if (r.outcome == BattleOutcome.WON) {
                        s = Dens.defeated(s, r.shrineId, r.enemy, nowMs, r.standing)
                        s.pendingDenCatch?.let {
                            val bonus = it.throwsTotal - Dens.BASE_THROWS
                            msg.append(" ${r.enemy.displayName} is worn out! You have ${it.throwsTotal} binding casts to tame it" +
                                (if (bonus > 0) " (+$bonus for your monsters still standing)." else "."))
                        }
                    }
                } else if (r.shrineId != null && r.encounter != EncounterType.WILD) {
                    val rec = s.shrines[r.shrineId] ?: ShrineRecord()
                    val firstDefeat = !rec.bossDefeated && r.outcome == BattleOutcome.WON
                    val updated = rec.copy(
                        lastVisitMs = nowMs,
                        bossDefeated = true,
                        bossCaptured = rec.bossCaptured || r.outcome == BattleOutcome.CAPTURED,
                    )
                    s = s.copy(shrines = s.shrines + (r.shrineId to updated))
                    if (firstDefeat) {
                        s = s.copy(stats = s.stats.copy(bossesDefeated = s.stats.bossesDefeated + 1))
                        msg.append(" The shrine's boss can now be tamed on your next visit!")
                    }
                }
            }
            BattleOutcome.LOST -> {
                val loss = s.gold * GOLD_LOSS_PERCENT / 100
                s = s.copy(gold = s.gold - loss, stats = s.stats.copy(battlesLost = s.stats.battlesLost + 1))
                msg.append("Defeated... You dropped $loss gold while fleeing.")
            }
            BattleOutcome.FLED -> msg.append("You escaped.")
            BattleOutcome.ONGOING -> {}
        }
        // Every monster tamed in the fight is yours, even if you fled or fell after.
        if (r.outcome == BattleOutcome.WON || r.outcome == BattleOutcome.FLED || r.outcome == BattleOutcome.LOST) {
            if (r.tamed.isNotEmpty()) msg.append(" Tamed: ${r.tamed.joinToString { it.displayName }}.")
        }
        for (c in r.tamed) {
            if (s.storageFull) break
            val party = if (s.party.size < MAX_PARTY_SIZE) s.party + c.uid else s.party
            s = s.copy(
                monsters = s.monsters + c,
                party = party,
                stats = s.stats.tamed(c),
            )
            s = addCandy(s, c.species, tameCandy(c.species))
            msg.append(" ${if (r.tamed.size > 1) "${c.displayName}: " else ""}Personality: ${c.personality.label} (${c.personality.description}). +${tameCandy(c.species)} ${candyName(c.species)}." +
                if (c.alpha) " An Alpha, with ${Alphas.PERFECT_STATS} perfect stats!" else "")
        }
        // Fighting with a weapon makes you better with it (only unfamiliar ones need it).
        if (r.outcome != BattleOutcome.FLED) s = practiceWeapons(s, msg)
        // Using cursed gear wears its curse down.
        s = wearCurses(s, r.weaponUses, r.armorHits, msg)
        // Thugs drop weapon parts and armor scrap.
        val rewards = mutableListOf<Reward>()
        // What the fight paid, shown with icons.
        if (r.outcome == BattleOutcome.WON || r.outcome == BattleOutcome.CAPTURED) {
            if (r.outcome == BattleOutcome.WON && r.goldWon > 0) rewards += Reward.Gold(r.goldWon)
            if (r.heroXp > 0) rewards += Reward.Xp(r.heroXp)
            r.drops.forEach { (item, n) -> rewards += Reward.Items(item, n) }
        }
        if (r.encounter == EncounterType.THUG && r.outcome == BattleOutcome.WON) {
            val rng = Random(nowMs)
            val loot = Loot(parts = rollParts(1 + rng.nextInt(2), rng, state.hero.level), armorParts = rollArmorParts(1 + rng.nextInt(2), rng))
            val u = grantLoot(s, loot)
            s = u.state
            rewards += u.rewards
            msg.append(" The thug dropped ${u.message}.")
        }
        // The weekend Gold shrine: a guardian beaten pays out big.
        if ((r.encounter == EncounterType.BOSS_CHALLENGE || r.encounter == EncounterType.BOSS_TAMING) &&
            (r.outcome == BattleOutcome.WON || r.outcome == BattleOutcome.CAPTURED)
        ) {
            val u = weekendReward(s, Random(nowMs xor 0x60D))
            s = u.state
            rewards += u.rewards
            msg.append(" ${u.message}")
        }
        // Damage carries over to the next battle. A fallen hero limps home at 10% HP.
        val heroMax = s.hero.maxHp
        s = s.copy(
            heroDamage = if (r.heroFell) heroMax - maxOf(1, heroMax / 10) else (r.damage[Combatant.HERO_KEY] ?: s.heroDamage),
            // Energy doesn't come back after a fight; it recovers over time and by walking.
            heroEnergyUsed = r.heroEnergyUsed.coerceIn(0, s.hero.maxMp),
            monsters = s.monsters.map { m -> r.damage[m.uid]?.let { m.copy(damage = it) } ?: m },
        )
        // Monsters knocked out stay down until a Restore Potion is used.
        if (r.faintedUids.isNotEmpty()) {
            val names = s.monsters.filter { it.uid in r.faintedUids }.map { it.displayName }
            s = s.copy(monsters = s.monsters.map { if (it.uid in r.faintedUids) it.copy(fainted = true, damage = it.stats.hp) else it })
            msg.append(" ${names.joinToString()} fainted and need${if (names.size == 1) "s" else ""} a Restore Potion.")
        }
        // Fighting slowly lifts the shadow curse.
        if (r.outcome == BattleOutcome.WON || r.outcome == BattleOutcome.CAPTURED) {
            val lifted = mutableListOf<String>()
            s = s.copy(
                monsters = s.monsters.map { m ->
                    if (!m.cursed || m.uid !in r.monsterXp) return@map m
                    val progress = m.cureProgress + 1
                    if (progress >= m.cureNeeded) {
                        lifted += m.displayName
                        m.copy(cursed = false, cureProgress = 0, cureReady = true)
                    } else {
                        m.copy(cureProgress = progress)
                    }
                },
            )
            lifted.forEach { msg.append(" $it's shadow curse is lifted! Choose a stat to perfect on its stat page.") }
        }
        val title = when (r.outcome) {
            BattleOutcome.WON -> "Victory!"
            BattleOutcome.CAPTURED -> "Tamed!"
            else -> null
        }
        return Update(s, msg.toString(), rewards = rewards, rewardTitle = title, levelUps = levelUps)
    }

    /**
     * Each weapon hit or physical hit taken lifts [CURSE_PER_USE]% of a curse.
     * Once it's gone the gear stays Legendary, keeps its power and gains new effects.
     */
    fun wearCurses(state: GameState, weaponUses: Int, armorHits: Int, msg: StringBuilder, rng: Random = Random(state.gold.toLong() + weaponUses)): GameState {
        var s = state
        val w = s.equippedGear
        if (w != null && w.cursed && weaponUses > 0) {
            val left = (w.cursePct - weaponUses * CURSE_PER_USE).coerceAtLeast(0)
            var lifted = w.copy(cursePct = left)
            if (left == 0) {
                lifted = lifted.copy(effects = lifted.effects + (GearEffect.entries - lifted.effects.toSet()).shuffled(rng).take(2))
                msg.append(" The curse on your ${w.name} is lifted! It keeps its ${w.curse!!.label} power and gains new effects.")
            }
            s = s.copy(equippedGear = lifted)
        }
        if (armorHits > 0) {
            val pieces = s.wornArmor.filter { it.cursed }
            for (a in pieces) {
                val left = (a.cursePct - armorHits * CURSE_PER_USE).coerceAtLeast(0)
                var lifted = a.copy(cursePct = left)
                if (left == 0) {
                    lifted = lifted.copy(effects = lifted.effects + (ARMOR_EFFECTS - lifted.effects.toSet()).shuffled(rng).take(2))
                    msg.append(" The curse on your ${a.name} is lifted! It keeps its ${a.curse!!.label} power and gains new effects.")
                }
                s = if (a.type.slot == ArmorSlot.BODY) s.copy(equippedArmor = lifted)
                else s.copy(equippedPieces = s.equippedPieces + (a.type.slot to lifted))
            }
        }
        return s
    }

    /** Chances at the weekend Gold shrine: a spell book, and a piece of cursed gear. */
    const val SPELL_BOOK_CHANCE = 0.75
    const val CURSED_GEAR_CHANCE = 0.40

    /**
     * What beating a weekend Gold shrine guardian pays: gold, spell pages, a
     * 75% chance of a spell book (a whole spell learned at once, or a rank up)
     * and a 40% chance of cursed Legendary gear (usually a weapon).
     */
    fun weekendReward(state: GameState, rng: Random): Update {
        val lvl = state.hero.level
        val gold = 40 * lvl + 200
        var s = state.copy(gold = state.gold + gold)
        val rewards = mutableListOf<Reward>(Reward.Gold(gold))
        val parts = mutableListOf("+$gold gold")
        // Pages for spells you're still learning or ranking up.
        val candidates = pageCandidates(s)
        val pages = if (candidates.isEmpty()) emptyMap() else candidates.shuffled(rng).take(2).associate { it.id to 2 + rng.nextInt(3) }
        if (pages.isNotEmpty()) {
            val u = grantLoot(s, Loot(pages = pages))
            s = u.state
            rewards += u.rewards
            parts += u.message
        }
        if (rng.nextDouble() < SPELL_BOOK_CHANCE) {
            val h = s.hero
            val unknown = Spells.all.filter { it.id !in h.knownSpells }
            val book = unknown.randomOrNull(rng)
            if (book != null) {
                s = s.copy(hero = h.copy(knownSpells = h.knownSpells + book.id))
                rewards += Reward.SpellBook(book.id, 1)
                parts += "a spell book: you learned ${book.name}!"
            } else {
                // Only a rank the hero's level allows (rank 2 at 10, 3 at 20...).
                Spells.all.filter { h.spellRank(it.id) < Spells.MAX_RANK && h.level >= SpellMastery.levelForRank(h.spellRank(it.id) + 1) }.randomOrNull(rng)?.let { sp ->
                    val rank = h.spellRank(sp.id) + 1
                    s = s.copy(hero = h.copy(spellRanks = h.spellRanks + (sp.id to rank)))
                    rewards += Reward.SpellBook(sp.id, rank)
                    parts += "a spell book: ${sp.name} is now rank $rank!"
                }
            }
        }
        if (rng.nextDouble() < CURSED_GEAR_CHANCE) {
            // Cursed gear doesn't need bag space checks to be kept: it's the prize.
            if (rng.nextDouble() < 0.7) {
                val w = Armory.cursedWeapon(lvl, rng)
                s = s.copy(gear = s.gear + w)
                rewards += Reward.WeaponDrop(w)
                parts += "a ${w.name} (Legendary)! Use it to lift the curse"
            } else {
                val a = Armory.cursedArmor(lvl, rng)
                s = s.copy(armor = s.armor + a)
                rewards += Reward.ArmorDrop(a)
                parts += "a ${a.name} (Legendary)! Wear it to lift the curse"
            }
        }
        return Update(s, "Weekend guardian's hoard: ${parts.joinToString("; ")}.", rewards)
    }

    // ---- shrines -----------------------------------------------------------

    fun shrineCooldownLeft(state: GameState, shrine: Shrine, nowMs: Long): Long {
        val rec = state.shrines[shrine.id] ?: return 0
        val cd = when (shrine.tier) {
            // A bonded item shrine recharges faster.
            ShrineTier.ITEM -> rec.bond.cooldownMin * 60_000L
            ShrineTier.SILVER -> World.SILVER_COOLDOWN_MS
            ShrineTier.GOLD -> World.BOSS_COOLDOWN_MS
        }
        return (rec.lastVisitMs + cd - nowMs).coerceAtLeast(0)
    }

    /** What an item shrine (or a phone-free session) hands out. */
    data class Loot(
        val items: Map<Item, Int> = emptyMap(),
        val weapons: List<Gear> = emptyList(),
        val armor: List<Armor> = emptyList(),
        /** Spell id -> pages. */
        val pages: Map<String, Int> = emptyMap(),
        val parts: Map<WeaponType, Int> = emptyMap(),
        val armorParts: Map<ArmorType, Int> = emptyMap(),
    )

    /**
     * Rolls item-shrine loot: weapons, spell-book pages, capture orbs and potions.
     * Pages are for spells the hero doesn't know yet or can still rank up.
     */
    fun rollLoot(state: GameState, rolls: Int, rng: Random): Loot {
        val items = mutableMapOf<Item, Int>()
        val weapons = mutableListOf<Gear>()
        val armor = mutableListOf<Armor>()
        val pages = mutableMapOf<String, Int>()
        val unknownSpells = pageCandidates(state)
        repeat(rolls) {
            val r = rng.nextDouble()
            when {
                r < 0.12 -> weapons += Armory.roll(state.hero.level, rng)
                r < 0.19 -> armor += Armory.rollArmor(state.hero.level, rng)
                // Spell pages turn up often, two at a time now and then.
                r < 0.46 && unknownSpells.isNotEmpty() -> {
                    val sp = unknownSpells.random(rng)
                    pages[sp.id] = (pages[sp.id] ?: 0) + 1 + (if (rng.nextDouble() < 0.4) 1 else 0)
                }
                r < 0.60 -> {
                    val orb = if (rng.nextDouble() < 0.75) Item.BINDING_CRYSTAL else Item.RESONANT_CRYSTAL
                    items[orb] = (items[orb] ?: 0) + 1
                }
                // Berries: only found out in the world (shrines and wild monsters), never in the shop.
                r < 0.71 -> {
                    val berry = if (rng.nextDouble() < 0.15) Item.GOLDEN_BERRY else Item.SWEET_BERRY
                    items[berry] = (items[berry] ?: 0) + 1
                }
                else -> {
                    // Restore Potions are deliberately rare.
                    val potion = if (rng.nextDouble() < 0.06) Item.RESTORE_POTION
                    else listOf(Item.POTION, Item.POTION, Item.POTION, Item.GREATER_POTION, Item.MANA_DRAUGHT, Item.PHOENIX_FEATHER).random(rng)
                    items[potion] = (items[potion] ?: 0) + 1
                }
            }
        }
        return Loot(items, weapons, armor, pages)
    }

    /**
     * Spells worth a page: unknown spells count double, known ones only while
     * they can still be ranked up.
     */
    fun pageCandidates(state: GameState): List<Skill> = Spells.all.flatMap { sp ->
        when (state.hero.spellRank(sp.id)) {
            0 -> listOf(sp, sp)
            // At rank 5, pages still help while its path has mastery to gain.
            Spells.MAX_RANK -> if (sp.id in state.hero.spellPaths && (state.hero.spellMastery[sp.id] ?: 0) < SpellMastery.MAX) listOf(sp) else emptyList()
            else -> listOf(sp)
        }
    }

    /** Ranks up a known spell with pages and gold. */
    fun upgradeSpell(state: GameState, spellId: String): Update {
        val spell = Spells.byId(spellId) ?: return Update(state, "Unknown spell.")
        val rank = state.hero.spellRank(spellId)
        if (rank == 0) return Update(state, "Learn ${spell.name} first.")
        if (rank >= Spells.MAX_RANK) return Update(state, "${spell.name} is already at max rank.")
        val needLevel = SpellMastery.levelForRank(rank + 1)
        if (state.hero.level < needLevel) return Update(state, "Rank ${rank + 1} opens at hero ${Stars.levelText(needLevel)}.")
        val pages = state.spellPages[spellId] ?: 0
        val needPages = Spells.pagesToUpgrade(rank)
        val needGold = Spells.goldToUpgrade(rank)
        if (pages < needPages) return Update(state, "Need $needPages ${spell.name} pages (you have $pages).")
        if (state.gold < needGold) return Update(state, "Need $needGold gold.")
        val left = pages - needPages
        val s = state.copy(
            gold = state.gold - needGold,
            spellPages = if (left > 0) state.spellPages + (spellId to left) else state.spellPages - spellId,
            hero = state.hero.copy(spellRanks = state.hero.spellRanks + (spellId to rank + 1)),
        )
        return Update(s, "${spell.name} is now rank ${rank + 1}!")
    }

    /** Some parts of one random weapon type. */
    fun rollParts(count: Int, rng: Random, heroLevel: Int = 1): Map<WeaponType, Int> =
        if (count <= 0) emptyMap() else mapOf(Armory.randomPartType(rng, heroLevel) to count)

    /** Some armor scrap of one random armor type (from thugs and villains). */
    fun rollArmorParts(count: Int, rng: Random): Map<ArmorType, Int> =
        if (count <= 0) emptyMap() else mapOf(Armory.randomArmorPartType(rng) to count)

    /** Adds loot to the save (respecting bag space) and describes it. */
    /** Adds earned loot: everything is kept, even past the bag's size (see [addItems]). */
    fun grantLoot(state: GameState, loot: Loot, overfill: Boolean = true): Update {
        var s = state
        val parts = mutableListOf<String>()
        val rewards = mutableListOf<Reward>()
        loot.items.forEach { (item, n) -> rewards += Reward.Items(item, n) }
        val (withItems, lostItems) = addItems(s, loot.items, overfill)
        s = withItems
        loot.items.forEach { (item, n) -> parts += "${item.label} x$n" }
        var lostWeapons = 0
        for (w in loot.weapons) {
            if (overfill || s.bagCount < s.bagCapacity) {
                s = s.copy(gear = s.gear + w)
                parts += "${w.rarity.label} ${w.name}"
                rewards += Reward.WeaponDrop(w)
            } else {
                lostWeapons++
            }
        }
        for (a in loot.armor) {
            if (overfill || s.bagCount < s.bagCapacity) {
                s = s.copy(armor = s.armor + a)
                parts += "${a.rarity.label} ${a.name}"
                rewards += Reward.ArmorDrop(a)
            } else {
                lostWeapons++
            }
        }
        for ((type, n) in loot.parts) {
            s = s.copy(parts = s.parts + (type to (s.parts[type] ?: 0) + n))
            parts += "${type.label} part x$n"
            rewards += Reward.Parts(type, n)
        }
        for ((type, n) in loot.armorParts) {
            s = s.copy(armorParts = s.armorParts + (type to (s.armorParts[type] ?: 0) + n))
            parts += "${type.label} scrap x$n"
            rewards += Reward.ArmorParts(type, n)
        }
        val learned = mutableListOf<String>()
        for ((spellId, n) in loot.pages) {
            val spell = Spells.byId(spellId) ?: continue
            val total = (s.spellPages[spellId] ?: 0) + n
            parts += "${spell.name} page"
            if (total >= Spells.PAGES_TO_LEARN && spellId !in s.hero.knownSpells) {
                val left = total - Spells.PAGES_TO_LEARN
                s = s.copy(
                    hero = s.hero.copy(knownSpells = s.hero.knownSpells + spellId),
                    spellPages = if (left > 0) s.spellPages + (spellId to left) else s.spellPages - spellId,
                )
                learned += spell.name
                rewards += Reward.Pages(spellId, n, learned = true)
            } else {
                s = s.copy(spellPages = s.spellPages + (spellId to total))
                rewards += Reward.Pages(spellId, n, learned = false)
            }
        }
        val msg = buildString {
            append(parts.joinToString(", ").ifEmpty { "nothing" })
            val lost = lostItems + lostWeapons
            if (lost > 0) append(" (Bag full: $lost left behind.)")
            learned.forEach { append(" You learned $it!") }
        }
        return Update(s, msg, rewards)
    }

    /** Collect loot from an item shrine. */
    fun visitShrine(state: GameState, shrine: Shrine, nowMs: Long, rng: Random = Random.Default): Update {
        if (shrine.isBoss) return Update(state, "This is a boss shrine. Challenge the guardian!")
        if (shrineCooldownLeft(state, shrine, nowMs) > 0) return Update(state, "This shrine is recharging.")
        val before = state.shrines[shrine.id] ?: ShrineRecord()
        val bond = before.bond
        val gold = (rng.nextInt(5, 16) * bond.goldMult).toInt()
        val after = before.copy(lastVisitMs = nowMs, visits = before.visits + 1)
        var s = state.copy(
            gold = state.gold + gold,
            shrines = state.shrines + (shrine.id to after),
            stats = state.stats.copy(shrinesVisited = state.stats.shrinesVisited + 1),
        )
        val rolled = rollLoot(s, rng.nextInt(2, 4) + bond.extraRolls, rng)
        // Libraries and bookstores are full of spell pages.
        val bookish = shrine.kind != null && (shrine.kind.contains("Library", true) || shrine.kind.contains("Book", true))
        val extra = if (bookish) pageCandidates(s).randomOrNull(rng)?.let { sp -> mapOf(sp.id to 1 + rng.nextInt(2)) }.orEmpty() else emptyMap()
        val pages = (rolled.pages.keys + extra.keys).associateWith { (rolled.pages[it] ?: 0) + (extra[it] ?: 0) }
        val loot = grantLoot(s, rolled.copy(pages = pages))
        s = loot.state
        val bondUp = if (after.bond != bond) " This shrine is now ${after.bond.label} with you!" else ""
        return Update(s, "${shrine.name}: ${loot.message}, +$gold gold.$bondUp", listOf(Reward.Gold(gold)) + loot.rewards)
    }

    // ---- weapons -----------------------------------------------------------


    /** Turns [PARTS_TO_FORGE] parts of one type into a weapon of that type (better odds than shrines). */
    fun forge(state: GameState, type: WeaponType, rng: Random = Random.Default): Update {
        val have = state.parts[type] ?: 0
        if (have < PARTS_TO_FORGE) return Update(state, "Need $PARTS_TO_FORGE ${type.label.lowercase()} parts to forge.")
        if (state.bagFull) return Update(state, "Bag is full.")
        val w = Armory.roll(state.hero.level, rng, type, luck = 10)
        return Update(
            state.copy(parts = state.parts + (type to have - PARTS_TO_FORGE), gear = state.gear + w),
            "You forged a ${w.rarity.label} ${w.name} (Lv ${w.level})!",
        )
    }

    /** Turns [PARTS_TO_FORGE] armor scrap of one type into a piece of that armor. */
    fun forgeArmor(state: GameState, type: ArmorType, rng: Random = Random.Default): Update {
        val have = state.armorParts[type] ?: 0
        if (have < PARTS_TO_FORGE) return Update(state, "Need $PARTS_TO_FORGE ${type.label.lowercase()} scrap to forge.")
        if (state.bagFull) return Update(state, "Bag is full.")
        val a = Armory.rollArmor(state.hero.level, rng, type, luck = 10)
        return Update(
            state.copy(armorParts = state.armorParts + (type to have - PARTS_TO_FORGE), armor = state.armor + a),
            "You forged a ${a.rarity.label} ${a.name} (Lv ${a.level})!",
        )
    }

    /** Melts armor down into scrap of its type (more for rarer pieces), for [SMELT_COST] gold. */
    fun smeltArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found. Take it off first.")
        if (state.gold < SMELT_COST) return Update(state, "Smelting costs $SMELT_COST gold.")
        val n = a.rarity.smeltParts
        return Update(
            state.copy(
                armor = state.armor.filterNot { it.id == id },
                gold = state.gold - SMELT_COST,
                armorParts = state.armorParts + (a.type to (state.armorParts[a.type] ?: 0) + n),
            ),
            "Smelted ${a.name} into $n ${a.type.label.lowercase()} scrap (−$SMELT_COST gold).",
            listOf(Reward.ArmorParts(a.type, n)),
        )
    }

    /** What a weapon's class bonus or penalty means for this hero, for messages. */
    fun weaponFit(w: Gear, h: Hero): String {
        val n = w.classStats(h.heroClass, h.perks.anyWeapon)
        return when {
            n > 0 -> " Class bonus: +$n to all stats!"
            n < 0 -> " Unfamiliar weapon: $n to all stats until you've fought ${w.type.masteryBattles - w.proficiency} more battle(s) with it."
            else -> ""
        }
    }

    /** Adds a battle of practice to the weapons in hand; reports any newly mastered. */
    private fun practiceWeapons(state: GameState, msg: StringBuilder): GameState {
        val cls = state.hero.heroClass
        fun practice(w: Gear?): Gear? {
            if (w == null || !w.foreign(cls) || w.mastered) return w
            val p = w.practiced()
            if (p.mastered) msg.append(" You've mastered your ${w.name}: no more penalty!")
            return p
        }
        return state.copy(equippedGear = practice(state.equippedGear), offhandGear = practice(state.offhandGear))
    }

    fun equip(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found.")
        val rest = state.gear.filterNot { it.id == id }
        // The previously equipped weapon goes back in the bag (equipping frees a slot, so it fits).
        val back = state.equippedGear?.let { rest + it } ?: rest
        return Update(state.copy(gear = back, equippedGear = w), "Equipped ${w.name}.${weaponFit(w, state.hero)}")
    }

    fun unequip(state: GameState): Update {
        val w = state.equippedGear ?: return Update(state, "Nothing equipped.")
        if (state.bagFull) return Update(state, "Bag is full.")
        return Update(state.copy(gear = state.gear + w, equippedGear = null), "Unequipped ${w.name}.")
    }

    /** Kleptomaniac: carry a second weapon in the other hand. */
    fun equipOffhand(state: GameState, id: String): Update {
        val r = state.hero.rankOf(Trait.KLEPTOMANIAC)
        if (r <= 0) return Update(state, "You need the Kleptomaniac skill to carry two weapons.")
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found.")
        val rest = state.gear.filterNot { it.id == id }
        val back = state.offhandGear?.let { rest + it } ?: rest
        return Update(
            state.copy(gear = back, offhandGear = w),
            "${w.name} in your off hand: it strikes after each attack at ${Trait.KLEPTOMANIAC.offhandPct(r)}% power.${weaponFit(w, state.hero)}",
        )
    }

    fun unequipOffhand(state: GameState): Update {
        val w = state.offhandGear ?: return Update(state, "Nothing in your off hand.")
        if (state.bagFull) return Update(state, "Bag is full.")
        return Update(state.copy(gear = state.gear + w, offhandGear = null), "Put away ${w.name}.")
    }

    // ---- selling -------------------------------------------------------------

    fun sellItem(state: GameState, item: Item, qty: Int = 1): Update {
        val n = minOf(qty, state.count(item))
        if (n <= 0) return Update(state, "You don't have any ${item.label}.")
        val gold = item.sellPrice * n
        return Update(
            state.copy(inventory = removeItems(state.inventory, mapOf(item to n)), gold = state.gold + gold),
            "Sold ${item.label}${if (n > 1) " x$n" else ""} for $gold gold.",
            listOf(Reward.Gold(gold)),
        )
    }

    fun sellWeapon(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found. Take it off first.")
        return Update(
            state.copy(gear = state.gear.filterNot { it.id == id }, gold = state.gold + w.sellPrice),
            "Sold ${w.rarity.label} ${w.name} for ${w.sellPrice} gold.",
            listOf(Reward.Gold(w.sellPrice)),
        )
    }

    fun sellArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found. Take it off first.")
        return Update(
            state.copy(armor = state.armor.filterNot { it.id == id }, gold = state.gold + a.sellPrice),
            "Sold ${a.rarity.label} ${a.name} for ${a.sellPrice} gold.",
            listOf(Reward.Gold(a.sellPrice)),
        )
    }

    /** Melts a weapon down into parts of its type (more for rarer weapons), for [SMELT_COST] gold. */
    fun smelt(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found. Take it off first.")
        if (state.gold < SMELT_COST) return Update(state, "Smelting costs $SMELT_COST gold.")
        val n = w.rarity.smeltParts
        return Update(
            state.copy(
                gear = state.gear.filterNot { it.id == id },
                gold = state.gold - SMELT_COST,
                parts = state.parts + (w.type to (state.parts[w.type] ?: 0) + n),
            ),
            "Smelted ${w.name} into $n ${w.type.label.lowercase()} part${if (n > 1) "s" else ""} (−$SMELT_COST gold).",
            listOf(Reward.Parts(w.type, n)),
        )
    }

    fun equipArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found.")
        val rest = state.armor.filterNot { it.id == id }
        val slot = a.type.slot
        val back = state.worn(slot)?.let { rest + it } ?: rest
        val fit = if (a.matches(state.hero.heroClass)) " Class bonus +${(CLASS_MATCH_BONUS * 100).toInt()}%!" else ""
        val s = if (slot == ArmorSlot.BODY) state.copy(armor = back, equippedArmor = a)
        else state.copy(armor = back, equippedPieces = state.equippedPieces + (slot to a))
        return Update(s, "Put on ${a.name}.$fit")
    }

    fun unequipArmor(state: GameState, slot: ArmorSlot = ArmorSlot.BODY): Update {
        val a = state.worn(slot) ?: return Update(state, "Nothing worn there.")
        if (state.bagFull) return Update(state, "Bag is full.")
        val s = if (slot == ArmorSlot.BODY) state.copy(armor = state.armor + a, equippedArmor = null)
        else state.copy(armor = state.armor + a, equippedPieces = state.equippedPieces - slot)
        return Update(s, "Took off ${a.name}.")
    }

    fun dropArmor(state: GameState, id: String): Update {
        val a = state.armor.find { it.id == id } ?: return Update(state, "Armor not found.")
        return Update(state.copy(armor = state.armor.filterNot { it.id == id }), "Dropped ${a.name}.")
    }

    fun dropWeapon(state: GameState, id: String): Update {
        val w = state.gear.find { it.id == id } ?: return Update(state, "Weapon not found.")
        return Update(state.copy(gear = state.gear.filterNot { it.id == id }), "Dropped ${w.name}.")
    }

    // ---- rare visitors --------------------------------------------------------

    /** A rare visitor waits this long for your next battery-saver walk. */
    const val VISITOR_TTL_MS = 2 * 60 * 60_000L

    /** Taming odds against a rare visitor are this much better. */
    const val VISITOR_CATCH_BOOST = 1.5

    fun activeVisitors(state: GameState, nowMs: Long): List<Visitor> =
        state.visitors.filter { nowMs - it.createdMs in 0 until VISITOR_TTL_MS }

    /** An Epic (or rarer) wild monster, [type] if given, a little above your usual wild level. */
    fun rareVisitor(state: GameState, nowMs: Long, rng: Random, type: Element? = null): Visitor? {
        val level = wildLevel(state.hero.level, rng) + 2
        val fits = Bestiary.wild.filter { (type == null || type in it.types) && it.minWildLevel <= level }
        val pool = fits.filter { it.rarity >= Rarity.EPIC }.ifEmpty { fits.filter { it.rarity >= Rarity.RARE } }.ifEmpty { fits }
        if (pool.isEmpty()) return null
        return Visitor(newId(rng), pool.random(rng).id, level, nowMs, rollShiny(rng))
    }

    /** Adds a visitor to the queue (expired ones are dropped). */
    fun addVisitor(state: GameState, v: Visitor, nowMs: Long): GameState =
        state.copy(visitors = activeVisitors(state, nowMs) + v)

    /** The next visitor to meet on a walk, as a battle-ready monster, and the state without it. */
    fun takeVisitor(state: GameState, nowMs: Long, rng: Random): Pair<GameState, Monster>? {
        val v = activeVisitors(state, nowMs).firstOrNull() ?: return null
        // The very monster announced: no evolving on the way in.
        val m = Monster.create(v.species, v.level, rng, shiny = v.shiny, evolve = false).withWildMoves(rng)
        return state.copy(visitors = activeVisitors(state, nowMs).drop(1)) to m
    }

    // ---- journal -----------------------------------------------------------

    fun markSeen(state: GameState, speciesId: String): GameState =
        if (speciesId in state.seen) state else state.copy(seen = state.seen + speciesId)

    fun epochDay(nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    const val STEPS_PER_REWARD = 100
    const val XP_PER_REWARD = 10
    const val HP_PER_REWARD = 10

    /**
     * A new reading from the phone's step counter (total steps since boot).
     * Steps since the previous reading count as walking; a lower reading means
     * the phone rebooted, so the new total counts from zero.
     */
    fun onStepCount(state: GameState, totalSteps: Long, nowMs: Long = System.currentTimeMillis()): Update {
        val last = state.lastStepCount
        val delta = when {
            last == null -> 0L
            totalSteps >= last -> totalSteps - last
            else -> totalSteps
        }
        val u = walk(state, delta.toInt(), nowMs)
        return Update(u.state.copy(lastStepCount = totalSteps), u.message)
    }

    /**
     * Switch where steps come from. Baselines reset so steps aren't counted
     * twice (or counted from before the switch).
     */
    fun setStepSource(state: GameState, source: StepSource): GameState =
        state.copy(settings = state.settings.copy(stepSource = source), lastStepCount = null, healthDay = null, healthSteps = null)

    /**
     * A new daily step total (from Health Connect). Steps since the previous
     * reading count as walking. The first reading after switching sources only
     * sets the baseline; on a new day the whole total counts.
     */
    fun onDailyStepTotal(state: GameState, day: Long, total: Long, nowMs: Long = System.currentTimeMillis()): Update {
        val last = state.healthSteps
        val delta = when {
            state.healthDay == null || last == null -> 0L
            state.healthDay == day -> (total - last).coerceAtLeast(0)
            else -> total
        }
        val u = walk(state, delta.toInt(), nowMs)
        return Update(u.state.copy(healthDay = day, healthSteps = maxOf(total, if (state.healthDay == day) last ?: 0 else 0)), u.message)
    }

    /** Drop calendar quests that haven't been completed (e.g. after turning calendar sync off). */
    fun clearCalendarQuests(state: GameState): Update {
        val kept = state.tasks.filterNot { it.source == TaskSource.CALENDAR && !it.completed }
        return Update(state.copy(tasks = kept), "Removed ${state.tasks.size - kept.size} calendar quest(s).")
    }

    /**
     * Walking [steps]: unlocking loot boxes advance, and every
     * [STEPS_PER_REWARD] steps give the hero and each battle-party monster
     * [XP_PER_REWARD] XP and heal [HP_PER_REWARD] HP. The message is empty
     * unless something notable happened (a level-up, evolution or new move).
     */
    fun walk(state: GameState, steps: Int, nowMs: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Update {
        if (steps <= 0) return Update(state, "")
        var s = LootBoxes.addWalk(state, steps * METERS_PER_MILE / STEPS_PER_MILE)
        s = Eggs.addWalk(s, steps * METERS_PER_MILE / STEPS_PER_MILE)
        val (withBuddy, buddyText) = Buddy.addWalk(s, steps * METERS_PER_MILE / STEPS_PER_MILE)
        s = withBuddy
        val day = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        val today = if (s.stepsDay == day) s.stepsToday + steps else steps
        s = s.copy(
            stepsToday = today,
            stepsDay = day,
            stepHistory = s.stepHistory.filterKeys { it > day - 90 } + (day to today),
            stats = s.stats.copy(totalSteps = s.stats.totalSteps + steps),
        )
        s = Health.recordSteps(s, steps, nowMs, zone)
        // 2.29: walking earns torches for dungeons (no XP), and still heals the team.
        val (lit, earned) = Torches.addSteps(s, steps)
        s = lit
        // Every thousand steps together builds the team's bond.
        val bondPoints = (s.stats.totalSteps / Bonds.STEPS_PER_POINT - (s.stats.totalSteps - steps) / Bonds.STEPS_PER_POINT).toInt()
        val away = Adventures.awayUids(s)
        val (bonded, grew) = Bonds.add(s, s.team.filter { it !in away }, bondPoints)
        s = bonded
        val walkText = listOfNotNull(
            buddyText.takeIf { it.isNotEmpty() },
            Bonds.grewText(grew).trim().takeIf { it.isNotEmpty() },
            if (earned > 0) "Walking: +$earned torch${if (earned == 1) "" else "es"} (${s.torches} in all)." else null,
        ).joinToString(" ")
        val bank = s.stepBank + steps
        val rewards = bank / STEPS_PER_REWARD
        s = s.copy(stepBank = bank % STEPS_PER_REWARD)
        if (rewards == 0) return Update(s, walkText)

        val hp = rewards * HP_PER_REWARD
        s = s.copy(
            heroDamage = (s.heroDamage - hp).coerceAtLeast(0),
            heroEnergyUsed = (s.heroEnergyUsed - rewards * ENERGY_PER_REWARD).coerceAtLeast(0),
        )
        s = s.copy(
            monsters = s.monsters.map { m ->
                if (m.uid !in s.team || m.fainted || m.uid in away) m else m.copy(damage = (m.damage - hp).coerceAtLeast(0))
            },
        )
        return Update(s, walkText)
    }

    // ---- phone-free focus sessions ------------------------------------------

    fun startFocus(state: GameState, minutes: Int, nowMs: Long, rng: Random = Random.Default): Update {
        if (state.focus != null) return Update(state, "A phone-free session is already running.")
        if (state.lootBoxes.size >= MAX_LOOT_BOXES) {
            return Update(state, "You're holding $MAX_LOOT_BOXES loot boxes. Open or discard one first.")
        }
        val session = FocusSession(newId(rng), nowMs, nowMs + minutes * 60_000L, minutes)
        return Update(state.copy(focus = session), "Phone-free for $minutes minutes. Put your phone down and stay in the game.")
    }

    /** When a finished session's visitor sets out: as the session ends. */
    private fun nowMsForVisitor(f: FocusSession) = f.endMs

    /** Torches for a two-hour phone-free session. */
    const val FOCUS_JACKPOT_TORCHES = 5

    /** Boxes from sessions this long need half the walk to open. */
    const val HALF_WALK_MINUTES = 90

    /** What a long phone-free session pays on top of its box. */
    data class FocusBonus(val tomePages: Int, val levelShare: Double, val energyRefill: Int, val moodCrystalPct: Int = 0)

    fun focusBonus(minutes: Int): FocusBonus = when {
        minutes >= 120 -> FocusBonus(tomePages = 5, levelShare = 0.5, energyRefill = 100, moodCrystalPct = 100)
        minutes >= 90 -> FocusBonus(tomePages = 3, levelShare = 0.25, energyRefill = 50, moodCrystalPct = 50)
        minutes >= 60 -> FocusBonus(tomePages = 0, levelShare = 0.1, energyRefill = 0, moodCrystalPct = 25)
        else -> FocusBonus(0, 0.0, 0)
    }

    /** Spells a Scholar's Tome can go to: ones not yet learned or not yet maxed. */
    fun tomeChoices(state: GameState): List<Skill> = Spells.all.filter { state.hero.spellRank(it.id) < Spells.MAX_RANK }

    /** Reads the Scholar's Tome at [index]: its pages go to [spellId]. */
    fun readTome(state: GameState, index: Int, spellId: String): Update {
        val pages = state.tomes.getOrNull(index) ?: return Update(state, "No tome to read.")
        if (tomeChoices(state).none { it.id == spellId }) return Update(state, "That spell is already mastered.")
        val s = state.copy(tomes = state.tomes.filterIndexed { i, _ -> i != index })
        val u = grantLoot(s, Loot(pages = mapOf(spellId to pages)))
        return Update(u.state, "Scholar's Tome: ${u.message}.", u.rewards)
    }

    fun focusPenalty(state: GameState): Int = maxOf(10, state.gold * GOLD_LOSS_PERCENT / 100).coerceAtMost(state.gold)

    /**
     * Ends the current session. Success awards a loot box (rarer for longer
     * sessions) that opens after walking; failure costs gold and a hunt token.
     */
    fun resolveFocus(state: GameState, success: Boolean, rng: Random = Random.Default): Update {
        val f = state.focus ?: return Update(state, "")
        var s = state.copy(focus = null)
        return if (success) {
            s = s.copy(
                stats = s.stats.copy(
                    focusWins = s.stats.focusWins + 1,
                    focusMinutes = s.stats.focusMinutes + f.minutes,
                    longestFocus = maxOf(s.stats.longestFocus, f.minutes),
                    longFocusWins = s.stats.longFocusWins + if (f.minutes >= Bounty.LONG_FOCUS_MIN) 1 else 0,
                ),
            )
            if (s.lootBoxes.size >= MAX_LOOT_BOXES) {
                // Shouldn't happen (starting is blocked when full), but don't lose the reward entirely.
                val gold = f.minutes
                return Update(s.copy(gold = s.gold + gold), "Phone-free for ${f.minutes} min! Box storage full, so you got $gold gold instead.")
            }
            val rarity = LootBoxes.roll(f.minutes, rng)
            val box = LootBox(newId(rng), rarity, f.endMs, halfWalk = f.minutes >= HALF_WALK_MINUTES)
            s = s.copy(lootBoxes = s.lootBoxes + box)
            val msg = StringBuilder("Phone-free for ${f.minutes} min! You earned a Level ${rarity.level} ${rarity.label} loot box. Walk ${box.milesLabel} mile(s) to open it")
            msg.append(if (box.halfWalk) " (half the usual walk)." else ".")
            // Long sessions pay more on top: a Scholar's Tome, training for the party, rest for the hero.
            val bonus = focusBonus(f.minutes)
            if (bonus.tomePages > 0) {
                s = s.copy(tomes = s.tomes + bonus.tomePages)
                msg.append(" Plus a Scholar's Tome: choose a spell for ${bonus.tomePages} pages (on the Hero tab).")
            }
            val levelUps = mutableListOf<LevelUp>()
            if (bonus.levelShare > 0) {
                s = s.copy(
                    monsters = s.monsters.map { m ->
                        if (m.uid !in s.party || m.fainted) return@map m
                        val (after, up) = growDetailed(m, (monsterXpForNext(m.level) * bonus.levelShare).toInt().coerceAtLeast(1), XpRules.monsterCap(s))
                        up?.let { levelUps += it }
                        after
                    },
                )
                msg.append(" Your party trained while you were away (+${(bonus.levelShare * 100).toInt()}% of a level each).")
            }
            // Two hours off the phone: a bundle of torches for the dungeons.
            if (f.minutes >= 120) {
                val spare = Torches.spareText(s, FOCUS_JACKPOT_TORCHES)
                s = Torches.add(s, FOCUS_JACKPOT_TORCHES, reward = true)
                msg.append(" +$FOCUS_JACKPOT_TORCHES torches for the dungeons$spare.")
                // A rare monster heads your way: it finds you on your next battery-saver walk.
                rareVisitor(s, nowMsForVisitor(f), rng)?.let { v ->
                    s = addVisitor(s, v, nowMsForVisitor(f))
                    msg.append(" A rare ${v.species.name} (Lv ${v.level}) is out looking for you: turn on the battery saver and walk within ${VISITOR_TTL_MS / 3_600_000} hours to meet it.")
                }
            }
            // Mood Crystals (personality rerolls) only come from hour-plus sessions.
            if (bonus.moodCrystalPct > 0 && rng.nextInt(100) < bonus.moodCrystalPct) {
                s = s.copy(inventory = s.inventory + (Item.MOOD_CRYSTAL to s.count(Item.MOOD_CRYSTAL) + 1))
                msg.append(" You found a Mood Crystal (a new personality for a monster).")
            }
            if (bonus.energyRefill > 0 && s.heroEnergyUsed > 0) {
                s = s.copy(heroEnergyUsed = (s.heroEnergyUsed - s.hero.maxMp * bonus.energyRefill / 100).coerceAtLeast(0))
                msg.append(if (bonus.energyRefill >= 100) " Your Energy is full." else " Energy +${bonus.energyRefill}%.")
            }
            Update(s, msg.toString(), levelUps = levelUps)
        } else {
            val loss = focusPenalty(s)
            s = s.copy(
                gold = s.gold - loss,
                huntTokens = (s.huntTokens - 1).coerceAtLeast(0),
                stats = s.stats.copy(focusFails = s.stats.focusFails + 1),
            )
            Update(s, "You picked up your phone early. Lost $loss gold and a hunt token.")
        }
    }

    fun silverLevel(heroLevel: Int): Int = maxOf(6, heroLevel + 3)

    /** Sets at least [count] random stats to a perfect IV. */
    fun withPerfectStats(m: Monster, count: Int, rng: Random): Monster {
        val perfect = MONSTER_STATS.shuffled(rng).take(count).toSet()
        fun iv(stat: Stat) = if (stat in perfect) MAX_IV else m.ivs[stat]
        return m.copy(ivs = StatBlock(iv(Stat.HP), iv(Stat.ATTACK), iv(Stat.MAGIC), iv(Stat.DEFENSE), iv(Stat.SPEED)))
    }

    /** Number of perfect (maximum) IVs, shown as stars in the UI. */
    fun perfectStatCount(m: Monster): Int = MONSTER_STATS.count { m.ivs[it] == MAX_IV }

    /**
     * Build the guardian for a silver or gold shrine, or explain why it can't
     * be fought (closed today, quest goal not met, or resting).
     */
    fun bossEncounter(
        state: GameState,
        shrine: Shrine,
        nowMs: Long,
        rng: Random = Random.Default,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Pair<Monster?, String> {
        if (!shrine.isBoss) return null to "Not a boss shrine."
        if (state.monsters.isEmpty()) return null to "You need at least one monster."
        val access = Schedule.access(state, shrine, nowMs, zone)
        if (!access.open) return null to access.reason
        val left = shrineCooldownLeft(state, shrine, nowMs)
        if (left > 0) return null to "The guardian is resting. Back in ${left / 3_600_000}h ${(left / 60_000) % 60}m."
        val species = Bestiary.get(shrine.bossSpeciesId!!)
        // Seeded so the same shrine always has the same guardian personality.
        val seeded = Random(shrine.id.hashCode())
        if (shrine.tier == ShrineTier.SILVER) {
            val guardian = Monster.create(species, silverLevel(state.hero.level), seeded).copy(uid = newId(rng))
            return withPerfectStats(guardian, 3, seeded) to "silver"
        }
        val boss = Monster.create(species, bossLevel(state.hero.level), seeded).copy(uid = newId(rng))
        val defeated = state.shrines[shrine.id]?.bossDefeated == true
        return boss to (if (defeated) "taming" else "challenge")
    }
}
