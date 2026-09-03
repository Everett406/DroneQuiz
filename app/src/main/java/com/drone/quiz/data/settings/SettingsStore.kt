package com.drone.quiz.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 普通刷题会话快照（持久化到 DataStore）。
 * ids 为题目顺序快照：恢复时按快照取题，保证与上次完全一致（不受筛选/随机影响）。
 */
@Serializable
data class PracticeSession(
    val src: String = "all",              // all | wrong
    val type: String = "all",             // all | single | judge
    val cat: String = "all",
    val ids: List<Long> = emptyList(),    // 题目顺序快照
    val answers: Map<String, Int> = emptyMap(), // qid -> 选项
    val index: Int = 0,                   // 当前进度（0-based）
    val savedAt: Long = 0
)

data class AppSettings(
    val themeMode: Int = 0,      // 0 跟随系统 1 浅色 2 深色
    val fontLevel: Int = 1,      // 0..3 -> 0.85 / 1.0 / 1.15 / 1.3
    val autoNext: Boolean = true,
    val passScore: Int = 60,     // 50..95 step 5
    val removeThreshold: Int = 2, // 错题移除：连续答对 N 次
    val dailyNotify: Boolean = false,
    val practiceOrder: Int = 0,  // 0 顺序 1 随机
    val effects: Boolean = true, // 画面特效（液态玻璃）；异常退出后自动降级时为 false 效果
    val bankVersion: Int = 0,    // 已加载题库的版本（与 assets questions.json 的 version 比对）
    val glassBlur: Int = 1,      // 底栏玻璃模糊档位：0 低 / 1 中 / 2 高
    val wallpaper: String = "",  // 全局壁纸文件路径（空 = 默认渐变）
    val wallpaperBlur: Boolean = false, // 壁纸是否模糊化（作玻璃背景纹路）
    val nickname: String = "",   // 用户昵称（空 = 首页只按时间问候，不带称呼）
    val readingFont: String = "system", // 阅读字体：system | sans | serif | kai
    val searchHistory: List<String> = emptyList(), // 搜索历史（最新在前，最多 8 条）
    val usageMs: Long = 0L,      // 累计前台使用毫秒（打赏弹窗门槛）
    val supportPrompted: Boolean = false, // 打赏弹窗已自动弹过（只弹一次）
    val supportRefused: Boolean = false  // 用户拒绝支持：永不再弹
)

class SettingsStore(private val context: Context) {

    private object K {
        val theme = intPreferencesKey("theme")
        val font = intPreferencesKey("font_level")
        val autoNext = booleanPreferencesKey("auto_next")
        val passScore = intPreferencesKey("pass_score")
        val removeThreshold = intPreferencesKey("remove_threshold")
        val dailyNotify = booleanPreferencesKey("daily_notify")
        val practiceOrder = intPreferencesKey("practice_order")
        val effects = booleanPreferencesKey("glass_effects")
        val bankVersion = intPreferencesKey("bank_version")
        val practiceSession = stringPreferencesKey("practice_session")
        val glassBlur = intPreferencesKey("glass_blur_level")
        val wallpaper = stringPreferencesKey("wallpaper_path")
        val wallpaperBlur = booleanPreferencesKey("wallpaper_blur")
        val nickname = stringPreferencesKey("nickname")
        val readingFont = stringPreferencesKey("reading_font")
        val searchHistory = stringPreferencesKey("search_history")
        val usageMs = longPreferencesKey("usage_ms")
        val supportPrompted = booleanPreferencesKey("support_prompted")
        val supportRefused = booleanPreferencesKey("support_refused")
        // 顺序槽沿用旧 key practice_session（老版本数据无损迁移：历史上只有顺序会话）；
        // 随机槽独立，两模式各自记各自的进度，互不覆盖
        val practiceSessionRandom = stringPreferencesKey("practice_session_random")
        // 模考记录删除限额（每周 2 次）
        val examDelWeek = stringPreferencesKey("exam_del_week")
        val examDelCount = intPreferencesKey("exam_del_count")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[K.theme] ?: 0,
            fontLevel = p[K.font] ?: 1,
            autoNext = p[K.autoNext] ?: true,
            passScore = p[K.passScore] ?: 60,
            removeThreshold = p[K.removeThreshold] ?: 2,
            dailyNotify = p[K.dailyNotify] ?: false,
            practiceOrder = p[K.practiceOrder] ?: 0,
            effects = p[K.effects] ?: true,
            bankVersion = p[K.bankVersion] ?: 0,
            glassBlur = p[K.glassBlur] ?: 1,
            wallpaper = p[K.wallpaper] ?: "",
            wallpaperBlur = p[K.wallpaperBlur] ?: false,
            nickname = p[K.nickname] ?: "",
            readingFont = p[K.readingFont] ?: "system",
            searchHistory = p[K.searchHistory]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            } ?: emptyList(),
            usageMs = p[K.usageMs] ?: 0L,
            supportPrompted = p[K.supportPrompted] ?: false,
            supportRefused = p[K.supportRefused] ?: false
        )
    }

    /** 指定模式（0 顺序 / 1 随机）的刷题会话（null = 无未完成会话）。双槽互不影响。 */
    fun practiceSession(order: Int): Flow<PracticeSession?> = context.dataStore.data.map { p ->
        val raw = if (order == 1) p[K.practiceSessionRandom] else p[K.practiceSession]
        raw?.let {
            runCatching { json.decodeFromString<PracticeSession>(it) }.getOrNull()
                ?.takeIf { s -> s.ids.isNotEmpty() }
        }
    }

    suspend fun currentPracticeSession(order: Int): PracticeSession? = practiceSession(order).first()

    suspend fun setPracticeSession(s: PracticeSession?, order: Int) {
        context.dataStore.edit { p ->
            val key = if (order == 1) K.practiceSessionRandom else K.practiceSession
            if (s == null) p.remove(key)
            else p[key] = json.encodeToString(s.copy(savedAt = System.currentTimeMillis()))
        }
    }

    /** 累计前台使用时长（打赏弹窗门槛）；挂调用方协程。 */
    suspend fun addUsageMs(delta: Long) {
        context.dataStore.edit { p ->
            p[K.usageMs] = (p[K.usageMs] ?: 0L) + delta
        }
    }

    suspend fun setSupportPrompted() = context.dataStore.edit { it[K.supportPrompted] = true }
    suspend fun setSupportRefused() = context.dataStore.edit { p ->
        p[K.supportPrompted] = true
        p[K.supportRefused] = true
    }

    /** 本周（ISO 周，周一为一周开始）键，如 "2026-W36" */
    private fun currentWeekKey(): String {
        val d = java.time.LocalDate.now()
        return "%04d-W%02d".format(
            d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR),
            d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        )
    }

    /**
     * 模考记录删除配额：每周最多 2 次，跨周自动重置。
     * 返回 (本周已用, 剩余)；不写入——写入用 recordExamDeletion。
     */
    suspend fun examDeleteQuota(): Pair<Int, Int> {
        val limit = 2
        val p = context.dataStore.data.first()
        val used = if (p[K.examDelWeek] == currentWeekKey()) p[K.examDelCount] ?: 0 else 0
        return used to (limit - used).coerceAtLeast(0)
    }

    /** 记录一次删除（本周计数 +1，跨周自动重置） */
    suspend fun recordExamDeletion() {
        context.dataStore.edit { p ->
            val wk = currentWeekKey()
            val used = if (p[K.examDelWeek] == wk) p[K.examDelCount] ?: 0 else 0
            p[K.examDelWeek] = wk
            p[K.examDelCount] = used + 1
        }
    }

    suspend fun setThemeMode(v: Int) = context.dataStore.edit { it[K.theme] = v }
    suspend fun setFontLevel(v: Int) = context.dataStore.edit { it[K.font] = v }
    suspend fun setAutoNext(v: Boolean) = context.dataStore.edit { it[K.autoNext] = v }
    suspend fun setPassScore(v: Int) = context.dataStore.edit { it[K.passScore] = v }
    suspend fun setRemoveThreshold(v: Int) = context.dataStore.edit { it[K.removeThreshold] = v }
    suspend fun setDailyNotify(v: Boolean) = context.dataStore.edit { it[K.dailyNotify] = v }
    suspend fun setPracticeOrder(v: Int) = context.dataStore.edit { it[K.practiceOrder] = v }
    suspend fun setEffects(v: Boolean) = context.dataStore.edit { it[K.effects] = v }
    suspend fun setBankVersion(v: Int) = context.dataStore.edit { it[K.bankVersion] = v }
    suspend fun setGlassBlur(v: Int) = context.dataStore.edit { it[K.glassBlur] = v.coerceIn(0, 2) }
    suspend fun setWallpaper(path: String) = context.dataStore.edit { it[K.wallpaper] = path }
    suspend fun setWallpaperBlur(v: Boolean) = context.dataStore.edit { it[K.wallpaperBlur] = v }
    suspend fun setNickname(v: String) = context.dataStore.edit { it[K.nickname] = v.trim().take(5) }
    suspend fun setReadingFont(v: String) = context.dataStore.edit { it[K.readingFont] = v }

    /** 记录搜索词：去重置顶，最多保留 8 条。 */
    suspend fun addSearchHistory(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        context.dataStore.edit { p ->
            val cur = p[K.searchHistory]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            } ?: emptyList()
            p[K.searchHistory] = json.encodeToString((listOf(t) + cur.filter { it != t }).take(8))
        }
    }

    suspend fun clearSearchHistory() = context.dataStore.edit { it.remove(K.searchHistory) }
}
