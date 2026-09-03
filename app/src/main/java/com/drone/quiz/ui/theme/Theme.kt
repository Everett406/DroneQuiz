package com.drone.quiz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

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

@Composable
fun DroneTheme(themeMode: Int, fontLevel: Int, content: @Composable () -> Unit) {
    val ui = droneUiColors(themeMode)
    val base = LocalDensity.current
    val fontMultiplier = floatArrayOf(0.85f, 1f, 1.15f, 1.3f).getOrElse(fontLevel) { 1f }

    CompositionLocalProvider(
        LocalUi provides ui,
        LocalDensity provides Density(base.density, base.fontScale * fontMultiplier)
    ) {
        MaterialTheme(
            colorScheme = if (ui.isDark) DarkScheme else LightScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

// 通用尺寸
object Dim {
    val barHeight = 64.dp
    val screenPadding = 18.dp
}
