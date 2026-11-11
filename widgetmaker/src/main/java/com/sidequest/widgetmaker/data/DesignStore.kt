package com.sidequest.widgetmaker.data

import android.content.Context
import com.sidequest.widgetmaker.spec.DesignParser
import com.sidequest.widgetmaker.spec.SavedDesign
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Saved designs (a JSON file), which design each placed widget shows, and the
 * API key. Everything stays on the phone.
 */
class DesignStore(context: Context) {
    private val file = File(context.filesDir, "designs.json")
    private val prefs = context.getSharedPreferences("widget_maker", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(SavedDesign.serializer())

    /** Newest first. */
    fun all(): List<SavedDesign> = synchronized(LOCK) {
        if (!file.exists()) return emptyList()
        runCatching { DesignParser.json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }

    fun get(id: String): SavedDesign? = all().firstOrNull { it.id == id }

    fun save(design: SavedDesign) = synchronized(LOCK) {
        val list = all().filterNot { it.id == design.id } + design
        writeAtomically(DesignParser.json.encodeToString(serializer, list))
    }

    fun delete(id: String) = synchronized(LOCK) {
        writeAtomically(DesignParser.json.encodeToString(serializer, all().filterNot { it.id == id }))
    }

    /** The design a placed widget shows: the one picked for it, else the newest. */
    fun designFor(appWidgetId: Int): SavedDesign? {
        val picked = prefs.getString(widgetKey(appWidgetId), null)?.let { get(it) }
        return picked ?: all().firstOrNull()
    }

    fun assign(appWidgetId: Int, designId: String) {
        prefs.edit().putString(widgetKey(appWidgetId), designId).apply()
    }

    fun forget(appWidgetId: Int) {
        prefs.edit().remove(widgetKey(appWidgetId)).apply()
    }

    var apiKey: String
        get() = prefs.getString("api_key", "").orEmpty()
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    private fun writeAtomically(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    private fun widgetKey(appWidgetId: Int) = "widget_$appWidgetId"

    private companion object {
        val LOCK = Any()
    }
}
