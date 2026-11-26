package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** High-priority to-dos get a heads-up before they're due. */
class PriorityAlertsTest {
    private val now = 1_800_000_000_000L
    private fun state(vararg t: QuestTask) = GameState(tasks = t.toList())

    @Test fun aHighPriorityTodoNotifiesBeforeItsDue() {
        val due = now + 3 * 3_600_000L
        val s = state(
            QuestTask("h", "Report", dueMs = due, priority = Priority.HIGH),
            QuestTask("n", "Laundry", dueMs = due, priority = Priority.NORMAL),
            QuestTask("d", "Done", dueMs = due, priority = Priority.HIGH, completedAtMs = now),
        )
        val n = GameNudges.taskNudges(s, now)
        assertEquals(listOf("task:h:$due"), n.map { it.id })
        assertEquals(due - GameNudges.TASK_WARNING_MS, n.single().atMs)
        assertTrue(n.single().id in GameNudges.upcoming(s, now).map { it.id })
        assertTrue(GameNudges.stillDue(s, n.single().id, due - 1000))
        // Done by then, or past due: no notification.
        val done = s.copy(tasks = s.tasks.map { it.copy(completedAtMs = now) })
        assertFalse(GameNudges.stillDue(done, n.single().id, due - 1000))
        assertFalse(GameNudges.stillDue(s, n.single().id, due + 1000))
    }

    @Test fun dueSoonStillGetsOneAndItCanBeTurnedOff() {
        val due = now + 10 * 60_000L
        val s = state(QuestTask("h", "Call", dueMs = due, priority = Priority.HIGH))
        assertEquals(now + 60_000L, GameNudges.taskNudges(s, now).single().atMs)
        // Separate from game updates, and off when switched off.
        assertEquals(1, GameNudges.upcoming(s.copy(settings = s.settings.copy(gameNudges = false)), now).size)
        assertTrue(GameNudges.upcoming(s.copy(settings = s.settings.copy(priorityAlerts = false)), now).isEmpty())
    }
}
