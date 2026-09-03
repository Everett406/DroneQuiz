package com.drone.quiz.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: Int = 0,      // 0 跟随系统 1 浅色 2 深色
    val fontLevel: Int = 1,      // 0..3 -> 0.85 / 1.0 / 1.15 / 1.3
    val autoNext: Boolean = true,
    val passScore: Int = 60,     // 50..95 step 5
    val removeThreshold: Int = 2, // 错题移除：连续答对 N 次
    val dailyNotify: Boolean = false,
    val practiceOrder: Int = 0,  // 0 顺序 1 随机
    val effects: Boolean = true, // 画面特效（液态玻璃）；异常退出后自动降级时为 false 效果
    val bankVersion: Int = 0     // 已加载题库的版本（与 assets questions.json 的 version 比对）
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
    }

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
            bankVersion = p[K.bankVersion] ?: 0
        )
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
}
