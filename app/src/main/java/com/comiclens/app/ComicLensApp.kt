package com.comiclens.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class ComicLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(filesDir, "logs").apply { mkdirs() }
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(dir, "crash_${System.currentTimeMillis()}.txt")
                    .writeText("Thread: ${thread.name}\n$sw")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
