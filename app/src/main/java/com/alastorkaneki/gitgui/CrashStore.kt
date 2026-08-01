package com.alastorkaneki.gitgui

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

object CrashStore {
    private const val FILE_NAME = "last_startup_crash.txt"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            save(appContext, thread, error)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    fun save(context: Context, thread: Thread, error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val report = buildString {
            appendLine("GIT GUI uncaught crash")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine("Exception: ${error.javaClass.name}: ${error.message.orEmpty()}")
            appendLine()
            append(writer.toString())
        }
        runCatching {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(report)
                it.flush()
            }
        }
        DownloadLog.appendBlock(context, "UNCAUGHT EXCEPTION", report)
        return report
    }

    fun read(context: Context): String? = runCatching {
        context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        context.deleteFile(FILE_NAME)
    }
}
