package com.drone.quiz

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局崩溃捕获：崩溃堆栈写入私有文件，下次启动时在屏幕上完整展示，
 * 便于远程排查（用户截图即可反馈）。
 * 注意：仅能捕获 Java/Kotlin 层异常；native 崩溃由 BootGuard 的心跳机制兜底定位。
 */
object CrashGuard {

    var liveCrash: String? by mutableStateOf(null)
        private set

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val text = buildString {
                appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("线程: ${thread.name}")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
            }
            runCatching {
                appContext.openFileOutput("last_crash.txt", Context.MODE_PRIVATE).use {
                    it.write(text.toByteArray())
                }
            }
            runCatching {
                BootGuard.log(appContext, "crash", "未捕获异常: ${throwable.javaClass.name}: ${throwable.message}")
            }
            liveCrash = text
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLast(context: Context): String? = runCatching {
        context.openFileInput("last_crash.txt").use { String(it.readBytes()) }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { context.deleteFile("last_crash.txt") }
        liveCrash = null
    }
}
