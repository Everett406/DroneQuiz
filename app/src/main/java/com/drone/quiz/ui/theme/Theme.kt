package com.drone.quiz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.drone.quiz.R

@Immutable
data class UiColors(
    val bg: Color,
    val bgGradient: Brush,
    val surface: Color,        // 玻璃表面色
    val surfaceStrong: Color,  // 卡片底（非玻璃）
    val ink: Color,            // 主按钮/选中（墨黑）
    val onInk: Color,
    val text: Color,
    val textSub: Color,
    val line: Color,
    val accent: Color,         // 点缀（火焰/进度/选中 tint），克制使用
    val correct: Color,
    val wrong: Color,
    val isDark: Boolean
)

val LocalUi = staticCompositionLocalOf {
    UiColors(
        bg = Color(0xFFF6F1E9),
        bgGradient = Brush.verticalGradient(listOf(Color(0xFFFAF6EF), Color(0xFFEFE8DB))),
        surface = Color.White.copy(alpha = 0.46f),
        surfaceStrong = Color.White.copy(alpha = 0.72f),
        ink = Color(0xFF1B1811),
        onInk = Color(0xFFF8F4EC),
        text = Color(0xFF26211A),
        textSub = Color(0xFF8D8474),
        line = Color(0xFFE4DCCD),
        accent = Color(0xFFE07830),
        correct = Color(0xFF3E9B4F),
        wrong = Color(0xFFD64545),
        isDark = false
    )
}

/**
 * 内置阅读字体（v2.7.2）：系统默认 + 三款适合长文阅读的内嵌字体。
 * 字体文件经子集化（GB2312 + 题库全字符集），单款仅数 MB；
 * Bold 位：宋体用真 Bold，文楷用 Medium（该家族最重字重），黑体用真 Bold。
 */
data class ReadingFontOption(
    val id: String,
    val label: String,
    val desc: String,
    val family: FontFamily?
)

val ReadingFontOptions: List<ReadingFontOption> = listOf(
    ReadingFontOption("system", "系统", "手机自带", null),
    ReadingFontOption(
        "sans", "黑体", "清晰耐读",
        FontFamily(
            Font(R.font.notosans_sc_regular, FontWeight.Normal),
            Font(R.font.notosans_sc_bold, FontWeight.Bold)
        )
    ),
    ReadingFontOption(
        "serif", "宋体", "书卷气息",
        FontFamily(
            Font(R.font.notoserif_sc_regular, FontWeight.Normal),
            Font(R.font.notoserif_sc_bold, FontWeight.Bold)
        )
    ),
    ReadingFontOption(
        "kai", "文楷", "温润手写",
        FontFamily(
            Font(R.font.lxgwwenkai_regular, FontWeight.Normal),
            Font(R.font.lxgwwenkai_medium, FontWeight.Bold)
        )
    )
)

fun readingFontOption(id: String): ReadingFontOption =
    ReadingFontOptions.firstOrNull { it.id == id } ?: ReadingFontOptions.first()

/** 当前全局阅读字体（默认 FontFamily.Default；DroneTheme 注入）。 */
val LocalReadingFont: androidx.compose.runtime.ProvidableCompositionLocal<FontFamily> =
    staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1B1811),
    onPrimary = Color(0xFFF8F4EC),
    background = Color(0xFFF6F1E9),
    onBackground = Color(0xFF26211A),
    surface = Color.White,
    onSurface = Color(0xFF26211A),
    surfaceVariant = Color(0xFFEFE8DB),
    onSurfaceVariant = Color(0xFF8D8474),
    outline = Color(0xFFE4DCCD)
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2EBDD),
    onPrimary = Color(0xFF1B1811),
    background = Color(0xFF171412),
    onBackground = Color(0xFFEFE9DC),
    surface = Color(0xFF221E19),
    onSurface = Color(0xFFEFE9DC),
    surfaceVariant = Color(0xFF2A251F),
    onSurfaceVariant = Color(0xFF9A907F),
    outline = Color(0xFF3A342B)
)

@Composable
fun droneUiColors(themeMode: Int): UiColors {
    val sysDark = isSystemInDarkTheme()
    val isDark = themeMode == 2 || (themeMode == 0 && sysDark)
    return if (isDark) {
        UiColors(
            bg = Color(0xFF171412),
            bgGradient = Brush.verticalGradient(listOf(Color(0xFF1C1815), Color(0xFF141110))),
            surface = Color(0xFF2B261F).copy(alpha = 0.5f),
            surfaceStrong = Color(0xFF2B261F).copy(alpha = 0.78f),
            ink = Color(0xFFF2EBDD),
            onInk = Color(0xFF1B1811),
            text = Color(0xFFEFE9DC),
            textSub = Color(0xFF9A907F),
            line = Color(0xFF332D25),
            accent = Color(0xFFE8934A),
            correct = Color(0xFF5DB56B),
            wrong = Color(0xFFE06666),
            isDark = true
        )
    } else {
        LocalUi.current
    }
}

/** 全部 15 个排版样式统一替换 fontFamily（未显式指定 fontFamily 的 Text 全部继承）。 */
private fun typographyWithFamily(family: FontFamily): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = family),
        displayMedium = t.displayMedium.copy(fontFamily = family),
        displaySmall = t.displaySmall.copy(fontFamily = family),
        headlineLarge = t.headlineLarge.copy(fontFamily = family),
        headlineMedium = t.headlineMedium.copy(fontFamily = family),
        headlineSmall = t.headlineSmall.copy(fontFamily = family),
        titleLarge = t.titleLarge.copy(fontFamily = family),
        titleMedium = t.titleMedium.copy(fontFamily = family),
        titleSmall = t.titleSmall.copy(fontFamily = family),
        bodyLarge = t.bodyLarge.copy(fontFamily = family),
        bodyMedium = t.bodyMedium.copy(fontFamily = family),
        bodySmall = t.bodySmall.copy(fontFamily = family),
        labelLarge = t.labelLarge.copy(fontFamily = family),
        labelMedium = t.labelMedium.copy(fontFamily = family),
        labelSmall = t.labelSmall.copy(fontFamily = family)
    )
}

@Composable
fun DroneTheme(
    themeMode: Int,
    fontLevel: Int,
    readingFont: String = "system",
    content: @Composable () -> Unit
) {
    val ui = droneUiColors(themeMode)
    // 主题明暗镜像同步：glass() modifier 的果冻分支（非 Composable 工厂）经
    // GlassRuntime.isDark 取色，无法读 CompositionLocal（v2.9.3 果冻模式）
    com.drone.quiz.ui.glass.GlassRuntime.isDark = ui.isDark
    val base = LocalDensity.current
    val fontMultiplier = floatArrayOf(0.85f, 1f, 1.15f, 1.3f).getOrElse(fontLevel) { 1f }
    val fontOption = readingFontOption(readingFont)
    // null = 系统默认：不注 typography（保持平台默认），Local 给 Default
    val typography = fontOption.family?.let { typographyWithFamily(it) }

    CompositionLocalProvider(
        LocalUi provides ui,
        LocalReadingFont provides (fontOption.family ?: FontFamily.Default),
        LocalDensity provides Density(base.density, base.fontScale * fontMultiplier)
    ) {
        MaterialTheme(
            colorScheme = if (ui.isDark) DarkScheme else LightScheme,
            typography = typography ?: MaterialTheme.typography,
            content = content
        )
    }
}

// 通用尺寸
object Dim {
    val barHeight = 64.dp
    val screenPadding = 18.dp
}

// ==================== 背景亮度自适应（v2.8.4） ====================

/** 自定义壁纸的平均亮度（0..1，Rec.709 加权）；null = 未设置壁纸（默认渐变与主题同向）。 */
val LocalWallpaperLuminance = staticCompositionLocalOf<Float?> { null }

/**
 * 实际背景（壁纸 × 主题纱合成后）是否偏暗——按钮等前景元素据此自适应配色。
 * 无壁纸 → 与主题同向（isDark）；有壁纸 → 壁纸原始亮度按主题纱 alpha 合成估算
 * （纱参数与 AppRoot 的 wallScrim 一致：浅色主题米白纱≈0.93 亮度、深色主题墨纱≈0.08，
 * alpha 取模糊开/关档位的中位）。用于修复：亮色主题 + 深色壁纸时墨色按钮融进背景（用户反馈）。
 */
@Composable
fun backdropIsDark(): Boolean {
    val ui = LocalUi.current
    val wall = LocalWallpaperLuminance.current ?: return ui.isDark
    val scrimAlpha = if (ui.isDark) 0.45f else 0.42f
    val scrimLuminance = if (ui.isDark) 0.08f else 0.93f
    val effective = wall * (1f - scrimAlpha) + scrimLuminance * scrimAlpha
    return effective < 0.5f
}

/**
 * 页面级辅助小字的自适应色（v2.8.7）。
 *
 * 适用对象：直接坐在壁纸上的低层级文字——分区标题（SectionLabel）、页面副标题
 * （ScreenTitle.subtitle）、页脚提示（续刷进度/错题本脚注/每日精进入口）。
 * 用户实测：浅色主题 + 深色壁纸时 textSub（中灰）对比度不足，圈出多处"看不清"。
 *
 * 规则：壁纸明暗与主题**同向** → 保持设计原色（不动默认观感）；
 * **翻暗** → 提亮为暖沙色；**翻亮** → 压深为暖灰。判定复用 backdropIsDark()（含主题纱合成）。
 */
@Composable
fun readableSubColor(): Color {
    val ui = LocalUi.current
    val dark = backdropIsDark()
    return when {
        dark == ui.isDark -> ui.textSub
        dark -> Color(0xFFC7BDA6)
        else -> Color(0xFF6E6555)
    }
}
