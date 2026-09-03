package com.drone.quiz

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动守护：解决"闪退但拿不到堆栈"的远程排障问题。
 *
 * 机制：
 * 1. 面包屑日志（boot_log.txt）：记录启动过程关键节点，任何形式的进程死亡（含 native 崩溃）
 *    后重开即可看到"死前走到了哪一步"。
 * 2. 启动心跳（boot_state.txt）：启动时写入 ok=false，首帧渲染成功且稳定后改写 ok=true。
 *    若上次启动从未标记 ok，判定为"早期异常死亡"（兼容 native 崩溃/系统杀进程，无需 Java 堆栈）。
 * 3. 连续早期死亡计数（fails）：>=1 时自动进入安全模式（关闭画面特效保证可用）；
 *    >=2 时启动即弹出诊断屏，面包屑 + 崩溃栈一目了然，截图即可反馈。
 */
object BootGuard {

    data class Snapshot(
        val firstBoot: Boolean,   // 安装后首次启动（无历史记录）
        val lastDied: Boolean,    // 上次启动未标记健康即死亡
        val fails: Int            // 连续异常死亡次数
    ) {
        val autoSafeMode: Boolean get() = lastDied && fails >= 1
        val showDiagnostics: Boolean get() = lastDied && fails >= 2
    }

    private const val STATE_FILE = "boot_state.txt"
    private const val LOG_FILE = "boot_log.txt"
    private const val MAX_LOG_LINES = 150

    private val lock = Any()
    private var bootTs: Long = 0L
    private var markedHealthy: Boolean = false

    private fun stateFile(ctx: Context) = File(ctx.filesDir, STATE_FILE)
    private fun logFile(ctx: Context) = File(ctx.filesDir, LOG_FILE)

    /** Application.onCreate 调用：越早越好。 */
    fun beginBoot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        bootTs = now
        markedHealthy = false
        var firstBoot = true
        var lastDied = false
        var fails = 0
        synchronized(lock) {
            runCatching {
                val f = stateFile(context)
                if (f.exists()) {
                    firstBoot = false
                    val parts = f.readText().trim().split('|')
                    val prevOk = parts.getOrNull(1) == "1"
                    val prevFails = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    if (prevOk) {
                        fails = 0
                    } else {
                        // 上一次启动没能标记健康就死了（含 native 崩溃）
                        lastDied = true
                        fails = prevFails + 1
                    }
                }
                f.writeText("$now|0|$fails")
            }
            logInternal(context, "boot", "开始启动 (fails=$fails, lastDied=$lastDied, firstBoot=$firstBoot)")
        }
        return Snapshot(firstBoot, lastDied, fails)
    }

    /** 首帧渲染成功后调用（玻璃帧已成功绘制），以及 onStop 兜底。幂等。 */
    fun markHealthy(context: Context) {
        synchronized(lock) {
            if (markedHealthy) return
            markedHealthy = true
            runCatching {
                stateFile(context).writeText("$bootTs|1|0")
            }
            logInternal(context, "boot", "启动健康确认（界面渲染稳定）")
        }
    }

    fun log(context: Context, tag: String, msg: String) {
        synchronized(lock) { runCatching { logInternal(context, tag, msg) } }
    }

    private fun logInternal(context: Context, tag: String, msg: String) {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date())
        val line = "$ts [$tag] $msg"
        val f = logFile(context)
        val existing = if (f.exists()) f.readLines() else emptyList()
        val kept = (existing + line).takeLast(MAX_LOG_LINES)
        f.writeText(kept.joinToString("\n"))
    }

    fun readLog(context: Context): String =
        runCatching { logFile(context).readText() }.getOrDefault("（无面包屑日志）")

    fun clearLog(context: Context) {
        synchronized(lock) { runCatching { logFile(context).delete() } }
    }
}
