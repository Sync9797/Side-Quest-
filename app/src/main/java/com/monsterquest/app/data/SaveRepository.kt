package com.monsterquest.app.data

import android.content.Context
import android.util.Log
import com.monsterquest.core.GameState
import java.io.File

/** Saves the whole game as one JSON file in app-private storage. */
class SaveRepository(context: Context) {
    private val app = context.applicationContext
    private val file = File(context.filesDir, "save.json")
    private val tmp = File(context.filesDir, "save.json.tmp")

    fun load(): GameState = try {
        if (file.exists()) GameState.fromJson(file.readText()) else GameState()
    } catch (e: Exception) {
        Log.e("SideQuest", "Failed to load save, backing it up and starting fresh", e)
        file.copyTo(File(file.parentFile, "save.broken.json"), overwrite = true)
        GameState()
    }

    fun save(state: GameState) {
        // Write to a temp file first so a crash mid-write can't corrupt the save.
        tmp.writeText(state.toJson())
        tmp.renameTo(file)
        keepDailyBackup()
        // AI Widget Maker's Monster Quest widgets update right away.
        com.monsterquest.app.widget.SummaryProvider.ping(app)
    }

    /**
     * Once a day, a copy of the save is kept next to it; the last
     * [DAILY_BACKUPS] are kept, so a bad day can be undone.
     */
    private fun keepDailyBackup() {
        val day = java.time.LocalDate.now().toString()
        val today = File(file.parentFile, "save.backup-$day.json")
        if (today.exists() || !file.exists()) return
        runCatching {
            file.copyTo(today)
            backups().drop(DAILY_BACKUPS).forEach { it.delete() }
        }
    }

    /** Daily backups, newest first. */
    fun backups(): List<File> =
        file.parentFile?.listFiles { f -> f.name.startsWith("save.backup-") }?.sortedByDescending { it.name }.orEmpty()

    /** Before an import replaces the game, the current one is kept here. */
    fun keepBeforeImport() {
        if (file.exists()) runCatching { file.copyTo(File(file.parentFile, "save.before-import.json"), overwrite = true) }
    }

    companion object {
        const val DAILY_BACKUPS = 7
    }
}
