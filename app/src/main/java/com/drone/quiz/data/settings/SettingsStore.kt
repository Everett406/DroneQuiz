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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val type: String = "all",             // all | single | judge | multi | blank | short
    val cat: String = "all",
    val ids: List<Long> = emptyList(),    // 题目顺序快照
    val answers: Map<String, Int> = emptyMap(), // qid -> picked（multi 为位掩码，blank/short 为 1 已答标记）
    val details: Map<String, String> = emptyMap(), // qid -> UserAnswer JSON（填空文本/简答草稿与自评）
    val index: Int = 0,                   // 当前进度（0-based）
    val bankId: String = "",             // 所属题库（恢复时校验，切库后旧会话作废）
    val savedAt: Long = 0,
    // v2.8.6 顺序循环补漏：本轮周期内已覆盖题目的累计集合（不含当前会话的 answers）。
    // 会话刷到末题即视为本轮结束，下轮只挑「总题单 - covered - answers」的题；全刷完则开完整新一轮。
    // 新字段带默认值：老版本快照 JSON 无此键也能照常反序列化（ignoreUnknownKeys）。
    val covered: List<Long> = emptyList()
)

/**
 * 快速模考配置快照（v2.10.0）：每次在模考配置页点「开始考试」时落盘，
 * 长按图标/小组件的「快速模考」直接按它组卷开考，跳过配置页。
 */
@Serializable
data class ExamQuickConfig(
    val counts: Map<String, Int> = emptyMap(), // 题型 → 题数
    val durationMin: Int = 60,                 // 考试时长（分钟）
    val typeOrder: List<String> = emptyList()  // 题型顺序（空 = 规范序）
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
    val supportRefused: Boolean = false, // 用户拒绝支持：永不再弹
    val currentBank: String = "drone",   // 当前使用题库
    val examIncludeShort: Boolean = false, // 模考高级选项：含简答题（默认关）
    val examTypeOrder: List<String> = emptyList(), // 模考题型顺序（空 = 单选→多选→填空→判断→简答）
    val examAutoMix: Boolean = true, // 模考题型构成：自动按题库各题型占比配比（关 = 手动拖比例，v2.8.3）
    val eyeCareReminder: Boolean = false, // v2.8.6 护眼提醒：连续刷题 20 分钟弹窗提醒休息（考试不受影响）
    val onboardingDone: Boolean = false, // v2.9.0 首启功能引导已完成/已跳过（跳过即不再自动弹）
    // v2.10.0 桌面小组件 / 成绩分享卡
    val dailyGoal: Int = 30,             // 每日目标题数（统计卡进度环 +「还差 N 题」）
    val wrongLastType: String = "all",   // 错题特训上次筛选（快捷方式直落沿用）
    val wrongLastCat: String = "all",
    val shareCardTheme: String = "sunset", // 成绩分享卡主题预设 id
    val shareCardName: String = "",      // 成绩卡署名（空 = 沿用个人昵称）
    val shareCardSlogan: String = "",    // 成绩卡自定义标语（可选）
    val shareCardAccent: String = ""     // 成绩卡强调色 hex（空 = 主题默认）
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
        // 多题库（v2.8.0）
        val currentBank = stringPreferencesKey("current_bank")
        val deletedBanks = stringPreferencesKey("deleted_banks") // JSON 数组：被删除的内置题库墓碑，清空记录后重生
        // 模考高级选项
        val examIncludeShort = booleanPreferencesKey("exam_include_short")
        val examTypeOrder = stringPreferencesKey("exam_type_order")
        val examAutoMix = booleanPreferencesKey("exam_auto_mix")
        // v2.8.6 护眼提醒
        val eyeCareReminder = booleanPreferencesKey("eye_care_reminder")
        // v2.9.0 首启功能引导
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        // v2.10.0 小组件 / 成绩分享卡
        val dailyGoal = intPreferencesKey("daily_goal")
        val wrongLastType = stringPreferencesKey("wrong_last_type")
        val wrongLastCat = stringPreferencesKey("wrong_last_cat")
        val examQuickConfig = stringPreferencesKey("exam_quick_config")
        val shareCardTheme = stringPreferencesKey("share_card_theme")
        val shareCardName = stringPreferencesKey("share_card_name")
        val shareCardSlogan = stringPreferencesKey("share_card_slogan")
        val shareCardAccent = stringPreferencesKey("share_card_accent")

        /** currentBank 缺省值（与 AppSettings.currentBank 默认一致） */
        const val BANK_DEFAULT = "drone"
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
            supportRefused = p[K.supportRefused] ?: false,
            currentBank = p[K.currentBank] ?: "drone",
            examIncludeShort = p[K.examIncludeShort] ?: false,
            examTypeOrder = p[K.examTypeOrder]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            } ?: emptyList(),
            examAutoMix = p[K.examAutoMix] ?: true,
            eyeCareReminder = p[K.eyeCareReminder] ?: false,
            onboardingDone = p[K.onboardingDone] ?: false,
            dailyGoal = p[K.dailyGoal] ?: 30,
            wrongLastType = p[K.wrongLastType] ?: "all",
            wrongLastCat = p[K.wrongLastCat] ?: "all",
            shareCardTheme = p[K.shareCardTheme] ?: "sunset",
            shareCardName = p[K.shareCardName] ?: "",
            shareCardSlogan = p[K.shareCardSlogan] ?: "",
            shareCardAccent = p[K.shareCardAccent] ?: ""
        )
    }

    /**
     * 指定模式（0 顺序 / 1 随机）的刷题会话（null = 无未完成会话）。双槽互不影响。
     *
     * v2.8.8 切库隔离堵漏（用户实测：导入新题库后「接续上次进度」仍显示旧题库的
     * 「523/2000」，切随机又变内置题库 20 道）：快照的 bankId 必须与当前题库一致，
     * 否则视同无会话。在唯一读口过滤，所有消费方（配置页续刷提示/刷题页恢复/
     * 自动接续/补漏轮）一次性生效；切回旧题库时进度仍在，不丢数据。
     */
    fun practiceSession(order: Int): Flow<PracticeSession?> = context.dataStore.data.map { p ->
        val raw = if (order == 1) p[K.practiceSessionRandom] else p[K.practiceSession]
        val bank = p[K.currentBank] ?: K.BANK_DEFAULT
        raw?.let {
            runCatching { json.decodeFromString<PracticeSession>(it) }.getOrNull()
                ?.takeIf { s -> s.ids.isNotEmpty() && s.bankId == bank }
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

    /** v2.9.0 首启引导完成/跳过：跳过即不再自动弹（设置页重看不受影响）。 */
    suspend fun completeOnboarding() = context.dataStore.edit { it[K.onboardingDone] = true }

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

    // ---- 多题库（v2.8.0） ----

    suspend fun setCurrentBank(id: String) = context.dataStore.edit { it[K.currentBank] = id }

    /** 删除内置题库时记墓碑（避免下次启动被重新播种）；清空全部数据时清空墓碑即可恢复内置题库。 */
    suspend fun addDeletedBank(id: String) {
        context.dataStore.edit { p ->
            val cur = p[K.deletedBanks]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            } ?: emptyList()
            if (id !in cur) p[K.deletedBanks] = json.encodeToString(cur + id)
        }
    }

    suspend fun clearDeletedBanks() = context.dataStore.edit { it.remove(K.deletedBanks) }

    suspend fun deletedBanks(): List<String> {
        val p = context.dataStore.data.first()
        return p[K.deletedBanks]?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun setExamIncludeShort(v: Boolean) = context.dataStore.edit { it[K.examIncludeShort] = v }

    /** 护眼提醒开关（v2.8.6）：连续刷题 20 分钟弹窗提醒休息，考试不受影响 */
    suspend fun setEyeCareReminder(v: Boolean) = context.dataStore.edit { it[K.eyeCareReminder] = v }

    /** 模考题型构成：自动配比开关（v2.8.3） */
    suspend fun setExamAutoMix(v: Boolean) = context.dataStore.edit { it[K.examAutoMix] = v }

    suspend fun setExamTypeOrder(list: List<String>) {
        context.dataStore.edit { p ->
            if (list.isEmpty()) p.remove(K.examTypeOrder)
            else p[K.examTypeOrder] = json.encodeToString(list)
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

    // ---- v2.10.0 小组件 / 成绩分享卡 ----

    /** 每日目标题数（学习统计卡进度环 +「距目标还差 N 题」） */
    suspend fun setDailyGoal(v: Int) = context.dataStore.edit { it[K.dailyGoal] = v.coerceIn(5, 500) }

    /** 错题特训筛选记忆（长按图标「错题特训」直落沿用） */
    suspend fun setWrongLastFilter(type: String, cat: String) = context.dataStore.edit {
        it[K.wrongLastType] = type.ifEmpty { "all" }
        it[K.wrongLastCat] = cat.ifEmpty { "all" }
    }

    /** 快速模考配置快照（模考配置页每次开考时写入） */
    suspend fun setExamQuickConfig(c: ExamQuickConfig?) = context.dataStore.edit {
        if (c == null) it.remove(K.examQuickConfig)
        else it[K.examQuickConfig] = json.encodeToString(c)
    }

    suspend fun examQuickConfig(): ExamQuickConfig? {
        val p = context.dataStore.data.first()
        return p[K.examQuickConfig]?.let { raw ->
            runCatching { json.decodeFromString<ExamQuickConfig>(raw) }.getOrNull()
        }?.takeIf { it.counts.isNotEmpty() }
    }

    suspend fun setShareCardTheme(v: String) = context.dataStore.edit { it[K.shareCardTheme] = v }
    suspend fun setShareCardName(v: String) = context.dataStore.edit { it[K.shareCardName] = v.trim().take(8) }
    suspend fun setShareCardSlogan(v: String) = context.dataStore.edit { it[K.shareCardSlogan] = v.trim().take(30) }
    suspend fun setShareCardAccent(v: String) = context.dataStore.edit { it[K.shareCardAccent] = v }

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

/**
 * 根组合真正响应的字段子集（v2.8.6 性能收敛）。
 *
 * 背景：DataStore 对任意 key 的写入（刷题会话快照每次翻页/作答都会写）都会让
 * `settings` flow 重发一个新的 AppSettings 实例。此前 MainActivity 根组合直接收集
 * 完整 settings，导致刷题时的每次快照落盘都触发「根 → AppRoot → 整个 NavHost」
 * 的连锁重组，叠加液态玻璃渲染造成可感知的卡顿（大题库导入后尤为明显）。
 *
 * v2.8.8 追加剔除 usageMs/supportPrompted/supportRefused：前台计时每分钟 +60s
 * 都会重发 settings flow，此前它们挂在 RootSettings 里，导致「根 → AppRoot」
 * 每分钟全量重组一次（滚动中偶发掉帧）。打赏门槛判定下沉到 SupportPromptHost
 * 内部自行收集，根组合只在主题/字体/特效/壁纸真正变化时才重组。
 */
data class RootSettings(
    val themeMode: Int = 0,
    val fontLevel: Int = 1,
    val readingFont: String = "system",
    val effects: Boolean = true,
    val wallpaper: String = "",
    val wallpaperBlur: Boolean = false
)

/** AppSettings flow → RootSettings flow（结构相等去重）。 */
fun Flow<AppSettings>.conflateForRoot(): Flow<RootSettings> = map {
    RootSettings(
        themeMode = it.themeMode,
        fontLevel = it.fontLevel,
        readingFont = it.readingFont,
        effects = it.effects,
        wallpaper = it.wallpaper,
        wallpaperBlur = it.wallpaperBlur
    )
}.distinctUntilChanged()
