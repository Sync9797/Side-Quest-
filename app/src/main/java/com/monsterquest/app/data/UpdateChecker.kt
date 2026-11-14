package com.monsterquest.app.data

import com.monsterquest.core.Versions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** A newer version of the game, from the project's GitHub releases. */
data class AvailableUpdate(
    /** e.g. "3.16.0". */
    val version: String,
    /** What changed (the release's notes). */
    val notes: String,
    /** The APK to download, or the release page if there isn't one. */
    val downloadUrl: String,
    val pageUrl: String,
)

/** Looks for a newer release on GitHub (the repository is public, so no sign-in is needed). */
object UpdateChecker {
    private const val RELEASES = "https://api.github.com/repos/Sync9797/Side-Quest-/releases?per_page=15"

    /** The notes of the release for [version] (e.g. "3.25.0"), or null (offline, or no such release). */
    fun notesFor(version: String, userAgent: String): String? = runCatching {
        val conn = URL("https://api.github.com/repos/Sync9797/Side-Quest-/releases/tags/v$version").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Json.parseToJsonElement(body).jsonObject["body"]?.jsonPrimitive?.contentOrNull
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** The newest release later than [current], or null (up to date, offline, or GitHub didn't answer). */
    fun check(current: String, userAgent: String): AvailableUpdate? = runCatching {
        val conn = URL(RELEASES).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
            val tags = releases.mapNotNull { it["tag_name"]?.jsonPrimitive?.contentOrNull }
            val newest = Versions.newest(tags, current) ?: return@runCatching null
            val r = releases.first { it["tag_name"]?.jsonPrimitive?.contentOrNull == newest }
            val page = r["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/Sync9797/Side-Quest-/releases"
            val apk = r["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { (it["name"]?.jsonPrimitive?.contentOrNull ?: "").endsWith(".apk") }
                ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            AvailableUpdate(newest.removePrefix("v"), r["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(), apk ?: page, page)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
