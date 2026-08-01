package com.alastorkaneki.gitgui

import android.app.ActivityManager
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DownloadLog {
    const val FILE_NAME = "GIT-GUI-crash-log.txt"

    private const val PREFS_NAME = "git_gui_download_log"
    private const val URI_KEY = "download_uri"
    private const val MAX_TRACE_BYTES = 262144
    private val lock = Any()

    @Volatile
    private var activeUri: Uri? = null

    fun beginSession(context: Context) {
        appendRaw(
            context,
            buildString {
                appendLine()
                appendLine()
                appendLine("========== GIT GUI STARTUP SESSION ==========")
                appendLine("Time: ${timestamp()}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Build: ${Build.DISPLAY}")
                appendLine("Fingerprint: ${Build.FINGERPRINT}")
                appendLine("Package: ${context.packageName}")
                appendLine("Version: ${versionName(context)}")
                appendLine("PID: ${Process.myPid()}")
                appendLine("Process: ${processName()}")
                appendLine("=============================================")
            }
        )
    }

    fun append(context: Context, message: String) {
        appendRaw(context, "${timestamp()} | $message\n")
    }

    fun appendBlock(context: Context, title: String, body: String) {
        appendRaw(
            context,
            buildString {
                appendLine()
                appendLine("========== $title ==========")
                appendLine(body)
                appendLine("========== END $title ==========")
            }
        )
    }

    fun appendPreviousExitInfo(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            append(context, "Previous-process exit details are unavailable below Android 11")
            return
        }
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            if (exits.isEmpty()) {
                append(context, "Android reported no previous process exits")
                return@runCatching
            }
            exits.take(3).forEachIndexed { index, exit ->
                val summary = buildString {
                    appendLine("Record: ${index + 1}")
                    appendLine("Timestamp: ${formatDate(exit.timestamp)}")
                    appendLine("Process: ${exit.processName}")
                    appendLine("Reason: ${reasonName(exit.reason)} (${exit.reason})")
                    appendLine("Status: ${exit.status}")
                    appendLine("Importance: ${exit.importance}")
                    appendLine("PSS: ${exit.pss}")
                    appendLine("RSS: ${exit.rss}")
                    appendLine("Description: ${exit.description.orEmpty()}")
                }
                appendBlock(context, "PREVIOUS PROCESS EXIT", summary)
                val trace = runCatching {
                    exit.traceInputStream?.use { readLimited(it, MAX_TRACE_BYTES) }
                }.getOrNull()
                if (!trace.isNullOrBlank()) appendBlock(context, "ANDROID EXIT TRACE", trace)
            }
        }.onFailure {
            append(context, "Unable to read previous-process exit details: ${it.javaClass.name}: ${it.message.orEmpty()}")
        }
    }

    private fun appendRaw(context: Context, text: String) {
        runCatching {
            synchronized(lock) {
                val appContext = context.applicationContext
                val existing = activeUri ?: savedUri(appContext) ?: findExistingUri(appContext)
                if (existing != null && writeToUri(appContext, existing, text)) {
                    rememberUri(appContext, existing)
                    return@synchronized
                }
                val created = createUri(appContext)
                if (created != null && writeToUri(appContext, created, text)) {
                    rememberUri(appContext, created)
                    return@synchronized
                }
                writeFallback(appContext, text)
            }
        }
    }

    private fun savedUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(URI_KEY, null)
        return value?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    private fun rememberUri(context: Context, uri: Uri) {
        activeUri = uri
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(URI_KEY, uri.toString()).commit()
    }

    private fun findExistingUri(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            val arguments = arrayOf(FILE_NAME, "${Environment.DIRECTORY_DOWNLOADS}/")
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arguments,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
        }.getOrNull()
    }

    private fun createUri(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    private fun writeToUri(context: Context, uri: Uri, text: String): Boolean {
        return runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wa") ?: return@runCatching false
            stream.bufferedWriter().use {
                it.write(text)
                it.flush()
            }
            true
        }.getOrDefault(false)
    }

    private fun writeFallback(context: Context, text: String) {
        runCatching {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            File(directory, FILE_NAME).appendText(text)
        }
    }

    private fun versionName(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName.orEmpty()
        }.getOrDefault("unknown")
    }

    private fun processName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            "unknown"
        }
    }

    private fun timestamp(): String = formatDate(System.currentTimeMillis())

    private fun formatDate(time: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(time))
    }

    private fun reasonName(reason: Int): String {
        return when (reason) {
            0 -> "UNKNOWN"
            1 -> "EXIT_SELF"
            2 -> "SIGNALED"
            3 -> "LOW_MEMORY"
            4 -> "CRASH"
            5 -> "CRASH_NATIVE"
            6 -> "ANR"
            7 -> "INITIALIZATION_FAILURE"
            8 -> "PERMISSION_CHANGE"
            9 -> "EXCESSIVE_RESOURCE_USAGE"
            10 -> "USER_REQUESTED"
            11 -> "USER_STOPPED"
            12 -> "DEPENDENCY_DIED"
            13 -> "OTHER"
            14 -> "FREEZER"
            15 -> "PACKAGE_STATE_CHANGE"
            16 -> "PACKAGE_UPDATED"
            else -> "UNRECOGNIZED"
        }
    }

    private fun readLimited(input: InputStream, limit: Int): String {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream(minOf(limit, 32768))
        var remaining = limit
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
