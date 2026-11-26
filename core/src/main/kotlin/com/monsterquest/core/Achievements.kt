package com.monsterquest.core

/** A milestone: progress toward a goal, and a reward to claim once it's reached. */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val goal: Int,
    val gold: Int,
    val items: Map<Item, Int> = emptyMap(),
    val progress: (GameState) -> Int,
) {
    fun done(state: GameState): Boolean = progress(state) >= goal
}

/** Milestones across the whole game, built on stats it already tracks. */
object Achievements {
    /** The longest run of days in a row with a completed quest. */
    fun longestQuestStreak(state: GameState): Int {
        val days = state.questDays.filterValues { it > 0 }.keys.sorted()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    private fun caughtTypes(state: GameState): Int =
        state.caught.mapNotNull { id -> runCatching { Bestiary.get(id) }.getOrNull() }.flatMap { it.types }.toSet().count { it in Element.typed }

    private fun a(id: String, name: String, desc: String, goal: Int, gold: Int, items: Map<Item, Int> = emptyMap(), progress: (GameState) -> Int) =
        Achievement(id, name, desc, goal, gold, items, progress)

    val all: List<Achievement> = listOf(
        // Taming and collecting
        a("tame_1", "First Bond", "Tame your first wild monster.", 1, 100) { it.stats.monstersTamed },
        a("tame_25", "Tamer", "Tame 25 monsters.", 25, 400, mapOf(Item.TREAT to 2)) { it.stats.monstersTamed },
        a("tame_100", "Master Tamer", "Tame 100 monsters.", 100, 1500, mapOf(Item.PRISMATIC_CRYSTAL to 2)) { it.stats.monstersTamed },
        a("journal_25", "Field Notes", "Catch 25 different species.", 25, 500) { it.caught.size },
        a("journal_60", "Naturalist", "Catch 60 different species.", 60, 1500, mapOf(Item.TREAT to 3)) { it.caught.size },
        a("journal_all", "Living Journal", "Catch every species.", Bestiary.all.size, 5000, mapOf(Item.PERFECT_ORB to 1)) { s -> s.caught.count { Bestiary.isListed(it) } },
        a("all_types", "Seven Colors", "Catch at least one monster of every type.", Element.typed.size, 600) { caughtTypes(it) },
        a("shiny_1", "Something Shiny", "Catch a shiny monster.", 1, 500) { it.shinyCaught.size },
        a("legend_1", "Legend", "Catch a legendary monster.", 1, 2000) { s -> s.caught.count { id -> runCatching { Bestiary.get(id).rarity == Rarity.LEGENDARY }.getOrDefault(false) } },
        // Battles
        a("win_50", "Scrapper", "Win 50 battles.", 50, 300) { it.stats.battlesWon },
        a("win_250", "Veteran", "Win 250 battles.", 250, 1000, mapOf(Item.GREATER_POTION to 5)) { it.stats.battlesWon },
        a("win_1000", "Champion", "Win 1,000 battles.", 1000, 3000, mapOf(Item.RESTORE_POTION to 3)) { it.stats.battlesWon },
        a("boss_10", "Boss Hunter", "Defeat 10 shrine bosses.", 10, 1000) { it.stats.bossesDefeated },
        a("villain_5", "Shadow Breaker", "Beat 5 villain camps.", 5, 1000) { it.stats.villainsBeaten },
        // Real life
        a("quests_25", "Getting Things Done", "Complete 25 quests.", 25, 300) { it.stats.tasksCompleted },
        a("quests_250", "Quest Master", "Complete 250 quests.", 250, 1500, mapOf(Item.TREAT to 3)) { it.stats.tasksCompleted },
        a("streak_7", "On a Roll", "Complete a quest 7 days in a row.", 7, 500) { longestQuestStreak(it) },
        a("streak_30", "Unstoppable", "Complete a quest 30 days in a row.", 30, 2500, mapOf(Item.TREAT to 5)) { longestQuestStreak(it) },
        a("steps_100k", "Wanderer", "Walk 100,000 steps.", 100_000, 500) { it.stats.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
        a("steps_1m", "Explorer", "Walk 1,000,000 steps.", 1_000_000, 3000, mapOf(Item.GOLDEN_BERRY to 3)) { it.stats.totalSteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
        a("shrines_100", "Pilgrim", "Visit 100 shrines.", 100, 800) { it.stats.shrinesVisited },
        a("focus_10", "Unplugged", "Finish 10 phone-free sessions.", 10, 500) { it.stats.focusWins },
        a("focus_120", "Deep Focus", "Finish a two-hour phone-free session.", 120, 1000) { it.stats.longestFocus },
        a("focus_1000", "Digital Detox", "Spend 1,000 minutes phone-free.", 1000, 2000, mapOf(Item.GOLDEN_BERRY to 3)) { it.stats.focusMinutes },
        // Growth
        a("level_25", "Adventurer", "Reach hero level 25.", 25, 500) { it.hero.level },
        a("level_50", "Hero", "Reach hero level 50.", 50, 1500) { it.hero.level },
        a("level_100", "Living Legend", "Reach hero level 100.", 100, 5000, mapOf(Item.PERFECT_ORB to 1)) { it.hero.level },
    )

    fun get(id: String): Achievement? = all.find { it.id == id }

    /** Reached but not yet claimed. */
    fun claimable(state: GameState): List<Achievement> = all.filter { it.done(state) && it.id !in state.achievementsClaimed }

    /** Collects an achievement's reward. */
    fun claim(state: GameState, id: String): Update {
        val ach = get(id) ?: return Update(state, "Unknown achievement.")
        if (id in state.achievementsClaimed) return Update(state, "Already claimed.")
        if (!ach.done(state)) return Update(state, "Not done yet: ${ach.progress(state).coerceAtMost(ach.goal)}/${ach.goal}.")
        var s = state.copy(gold = state.gold + ach.gold, achievementsClaimed = state.achievementsClaimed + id)
        var extra = ""
        if (ach.items.isNotEmpty()) {
            val (withItems, lost) = GameEngine.addItems(s, ach.items)
            s = withItems
            extra = " and " + ach.items.entries.joinToString { "${it.key.label} x${it.value}" } + if (lost > 0) " (bag full: $lost left behind)" else ""
        }
        val loot = listOf(Reward.Gold(ach.gold)) + ach.items.map { (item, n) -> Reward.Items(item, n) }
        return Update(s, "Achievement: ${ach.name}! +${ach.gold} gold$extra.", rewards = loot, rewardTitle = ach.name)
    }
}
