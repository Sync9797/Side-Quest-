package com.monsterquest.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on the phone (nothing is sent anywhere), so it can be
 * shared from Settings to help fix it.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Writes any crash to a file, then lets Android handle it as usual. */
    fun install(context: Context, version: String) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                file(app).writeText(
                    buildString {
                        appendLine("Side Quest $version crashed at ${java.time.ZonedDateTime.now()}")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace.take(60_000))
                    },
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The last crash report, or null. */
    fun last(context: Context): String? = file(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    fun clear(context: Context) {
        file(context).delete()
    }
}
