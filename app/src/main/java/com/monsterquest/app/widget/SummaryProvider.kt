package com.monsterquest.app.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.monsterquest.app.data.SaveRepository

/**
 * Shares [WidgetSummary] with AI Widget Maker (and nothing else), read-only,
 * so its Monster Quest widgets can show your eggs, adventures, steps and sleep.
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        // Only the widget app may read it.
        if (callingPackage !in READERS) return null
        val context = context ?: return null
        val json = WidgetSummary.of(SaveRepository(context).load()).toString()
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val WIDGET_MAKER = "com.sidequest.widgetmaker"
        private val READERS = setOf(WIDGET_MAKER)
        private const val CHANGED = "com.sidequest.widgetmaker.GAME_CHANGED"
        @Volatile private var lastPing = 0L

        /** Tells the widget app the game changed (at most every 15 seconds), so its widgets update. */
        fun ping(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastPing < 15_000) return
            lastPing = now
            runCatching { context.sendBroadcast(Intent(CHANGED).setPackage(WIDGET_MAKER)) }
        }
    }
}
