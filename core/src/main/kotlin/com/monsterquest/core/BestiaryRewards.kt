package com.monsterquest.core

/**
 * Journal rewards for completing a page:
 * - A type's page (every listed species of that type caught, either of its
 *   types): [TYPE_GOLD] gold, a Farwisp and a title, e.g. "Fire Warden".
 * - A whole evolution line: [LINE_GOLD] gold, a Resonant Crystal and
 *   [LINE_CANDY] of the line's candy.
 * Each is claimed once, from the Journal. The hidden legendary never counts.
 */
object BestiaryRewards {
    const val TYPE_GOLD = 500
    const val LINE_GOLD = 150
    const val LINE_CANDY = 10

    data class Goal(val id: String, val name: String, val members: List<Species>, val type: Element?) {
        val reward: String
            get() = if (type != null) "$TYPE_GOLD gold, a Farwisp and the title ${typeTitle(type)}"
            else "$LINE_GOLD gold, a Resonant Crystal and $LINE_CANDY ${GameEngine.candyName(members.first())}"
    }

    fun typeTitle(e: Element): String = "${e.label} Warden"

    val typeGoals: List<Goal> = Element.typed.map { e ->
        Goal("type:${e.name}", "${e.label} page", Bestiary.all.filter { e in it.types }, e)
    }

    /** Every listed line of two or more forms. */
    val lineGoals: List<Goal> = Bestiary.all.filter { it.stages > 1 }.groupBy { it.family }.map { (family, members) ->
        Goal("line:$family", family, members.sortedBy { it.stage }, null)
    }

    val all: List<Goal> get() = typeGoals + lineGoals

    fun progress(state: GameState, g: Goal): Pair<Int, Int> = g.members.count { it.id in state.caught } to g.members.size

    fun complete(state: GameState, g: Goal): Boolean = g.members.all { it.id in state.caught }

    /** Complete and not yet claimed. */
    fun claimable(state: GameState): List<Goal> = all.filter { it.id !in state.bestiaryClaimed && complete(state, it) }

    fun claim(state: GameState, id: String): Update {
        val g = all.find { it.id == id } ?: return Update(state, "No such reward.")
        if (id in state.bestiaryClaimed) return Update(state, "Already claimed.")
        if (!complete(state, g)) return Update(state, "Catch every monster on the ${g.name} first.")
        var s = state.copy(bestiaryClaimed = state.bestiaryClaimed + id)
        val rewards = mutableListOf<Reward>()
        val type = g.type
        if (type != null) {
            s = s.copy(gold = s.gold + TYPE_GOLD, inventory = s.inventory + (Item.FARWISP to s.count(Item.FARWISP) + 1))
            s = Titles.grant(s, typeTitle(type))
            rewards += Reward.Gold(TYPE_GOLD)
            rewards += Reward.Items(Item.FARWISP, 1)
        } else {
            s = s.copy(gold = s.gold + LINE_GOLD)
            s = GameEngine.addItems(s, mapOf(Item.RESONANT_CRYSTAL to 1)).first
            s = GameEngine.addCandy(s, g.members.first(), LINE_CANDY)
            rewards += Reward.Gold(LINE_GOLD)
            rewards += Reward.Items(Item.RESONANT_CRYSTAL, 1)
        }
        val title = if (type != null) " You're now a ${typeTitle(type)}!" else " Plus $LINE_CANDY ${GameEngine.candyName(g.members.first())}."
        return Update(s, "${g.name} complete!$title", rewards, rewardTitle = "Journal reward")
    }
}
