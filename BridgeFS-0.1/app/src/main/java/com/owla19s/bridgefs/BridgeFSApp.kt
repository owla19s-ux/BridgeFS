package com.owla19s.bridgefs

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BridgeFSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val text = buildString {
                    appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    appendLine("thread: ${t.name}")
                    appendLine("version: ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
                    appendLine(Log.getStackTraceString(e))
                }
                val dir = File("/sdcard/BridgeFS/logs")
                if (dir.mkdirs() || dir.isDirectory) {
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    File(dir, "crash_$ts.log").writeText(text)
                    File(dir, "latest.log").writeText(text)
                } else {
                    val fb = File(getExternalFilesDir(null), "logs").apply { mkdirs() }
                    File(fb, "latest.log").writeText(text)
                }
            }.onFailure { Log.e("BridgeFS", "crash log failed", it) }
            default?.uncaughtException(t, e)
        }
    }
}
