package com.comiclens.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.Date

@HiltAndroidApp
class ComicLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                CrashLog.file(this).appendText("${Date()} [${thread.name}]\n${error.stackTraceToString()}\n\n")
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    fun file(ctx: Context) = File(ctx.filesDir, "crash.log")
    fun read(ctx: Context): String = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
}
