package com.monsterquest.core

/**
 * One-time tips: a short card the first time you meet something, so the
 * game's depth comes a piece at a time instead of all at once in Help.
 */
enum class Tip(val title: String, val text: String) {
    DUNGEON(
        "Your first dungeon",
        "Each room offers two doors: pick one. Fights, traps, chests and fountains wait behind them, and a champion at the end. " +
            "Torches help inside: light one to pass a trap safely, to see behind a \"?\" door, to light a dark hall or to open a vault's rich chest.",
    ),
    DARK(
        "Pitch dark",
        "The deep half of a hard dungeon is dark: every door shows \"?\". One torch lights the way for 3 rooms. Walking in blind is a gamble.",
    ),
    EGGS(
        "Eggs hatch by walking",
        "Walking warms your first 3 eggs. When one is ready it glows: tap Hatch. An Incubator from the Store halves an egg's distance.",
    ),
    ADVENTURES(
        "Adventures",
        "Send monsters you aren't using on an adventure: they come back with loot (and sometimes an egg). " +
            "Monsters away can't fight, and the button wiggles when a team is home.",
    ),
    BONDS(
        "Bonds",
        "Every battle a monster fights and wins, and every 1,000 steps in your team, builds its bond. Each bond level makes it stronger, " +
            "and at 5 hearts it survives one knockout a battle.",
    ),
    SYNERGY(
        "Team synergy",
        "Some types fight better together: with both in your party or swaps, every monster gets a boost. Your synergies show above the party.",
    ),
    SPELL_PATH(
        "A spell at rank 5",
        "A rank-5 spell can follow one of two paths, for example a bolt that splits to hit every foe or one that hits harder with an effect. " +
            "Pick one on the Hero tab; each path has 5 mastery ranks of its own.",
    ),
    BOUNTY(
        "The weekly bounty",
        "The same three goals every week: walk 40,000 steps, two phone-free sessions of 60+ minutes, and 10 quests. Clear all three for a Rare or Epic piece of gear.",
    ),
    ;

    val id: String get() = name.lowercase()
}

object Tips {
    fun seen(state: GameState, tip: Tip): Boolean = tip.id in state.tipsSeen

    fun markSeen(state: GameState, tip: Tip): GameState =
        if (seen(state, tip)) state else state.copy(tipsSeen = state.tipsSeen + tip.id)
}

/** Release notes (a release's description) as a short list of points for the "What's new" card. */
object ReleaseNotes {
    /**
     * The "- " points of [body], with wrapped lines joined; when there are
     * none, its plain paragraphs. The first line (the release's title) and
     * attribution lines are left out.
     */
    fun points(body: String): List<String> {
        val lines = body.lines().map { it.trimEnd() }
            .filterNot { it.startsWith("Co-Authored-By:") || it.startsWith("Claude-Session:") || it.startsWith("🤖") }
        val bullets = mutableListOf<String>()
        var current: StringBuilder? = null
        for (l in lines) {
            val t = l.trim()
            when {
                t.startsWith("- ") || t.startsWith("* ") -> {
                    current?.let { bullets += it.toString() }
                    current = StringBuilder(t.drop(2).trim())
                }
                t.isEmpty() -> { current?.let { bullets += it.toString() }; current = null }
                current != null && l.startsWith(" ") -> current.append(' ').append(t)
                else -> { current?.let { bullets += it.toString() }; current = null }
            }
        }
        current?.let { bullets += it.toString() }
        if (bullets.isNotEmpty()) return bullets
        // No bullet points: the paragraphs after the title.
        return body.split(Regex("\\n\\s*\\n")).drop(1).map { it.lines().joinToString(" ") { s -> s.trim() }.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Co-Authored-By:") && !it.startsWith("Claude-Session:") }
    }
}
