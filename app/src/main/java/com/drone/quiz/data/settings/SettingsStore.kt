package com.drone.quiz.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    val searchHistory: List<String> = emptyList() // 搜索历史（最新在前，最多 8 条）
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
        val searchHistory = stringPreferencesKey("search_history")
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
            searchHistory = p[K.searchHistory]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            } ?: emptyList()
        )
    }

    /** 当前刷题会话（null = 无未完成会话） */
    val practiceSession: Flow<PracticeSession?> = context.dataStore.data.map { p ->
        p[K.practiceSession]?.let { raw ->
            runCatching { json.decodeFromString<PracticeSession>(raw) }.getOrNull()
                ?.takeIf { it.ids.isNotEmpty() }
        }
    }

    suspend fun currentPracticeSession(): PracticeSession? = practiceSession.first()

    suspend fun setPracticeSession(s: PracticeSession?) {
        context.dataStore.edit { p ->
            if (s == null) p.remove(K.practiceSession)
            else p[K.practiceSession] = json.encodeToString(s.copy(savedAt = System.currentTimeMillis()))
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
    suspend fun setNickname(v: String) = context.dataStore.edit { it[K.nickname] = v.trim().take(12) }

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
