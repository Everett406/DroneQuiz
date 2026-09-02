package com.drone.quiz.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun icon(
    name: String,
    size: Dp = 24.dp,
    viewport: Float = 24f,
    block: PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = size,
    defaultHeight = size,
    viewportWidth = viewport,
    viewportHeight = viewport
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color(0xFF000000)),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) { block() }
}.build()

// ---------- 底栏图标（描边风格，几何绘制） ----------

object AppIcons {

    val Home: ImageVector by lazy {
        icon("AppHome") {
            // 屋顶 + 房体
            moveTo(4f, 11f)
            lineTo(12f, 4f)
            lineTo(20f, 11f)
            // 房体
            moveTo(6f, 10f)
            lineTo(6f, 19f)
            lineTo(18f, 19f)
            lineTo(18f, 10f)
            // 门
            moveTo(10f, 19f)
            lineTo(10f, 14f)
            lineTo(14f, 14f)
            lineTo(14f, 19f)
        }
    }

    val Cards: ImageVector by lazy {
        icon("AppCards") {
            // 后卡片
            moveTo(7f, 3.5f)
            lineTo(18.5f, 3.5f)
            curveTo(19.3f, 3.5f, 20f, 4.2f, 20f, 5f)
            lineTo(20f, 13.5f)
            // 前卡片
            moveTo(5.5f, 7.5f)
            lineTo(17f, 7.5f)
            curveTo(17.8f, 7.5f, 18.5f, 8.2f, 18.5f, 9f)
            lineTo(18.5f, 19f)
            curveTo(18.5f, 19.8f, 17.8f, 20.5f, 17f, 20.5f)
            lineTo(5.5f, 20.5f)
            curveTo(4.7f, 20.5f, 4f, 19.8f, 4f, 19f)
            lineTo(4f, 9f)
            curveTo(4f, 8.2f, 4.7f, 7.5f, 5.5f, 7.5f)
            close()
            // 对勾
            moveTo(7.5f, 14f)
            lineTo(10f, 16.5f)
            lineTo(14.5f, 11.5f)
        }
    }

    val Timer: ImageVector by lazy {
        icon("AppTimer") {
            // 表体
            moveTo(12f, 21f)
            curveTo(7.6f, 21f, 4f, 17.4f, 4f, 13f)
            curveTo(4f, 8.6f, 7.6f, 5f, 12f, 5f)
            curveTo(16.4f, 5f, 20f, 8.6f, 20f, 13f)
            curveTo(20f, 17.4f, 16.4f, 21f, 12f, 21f)
            close()
            // 顶部按钮
            moveTo(10f, 2f)
            lineTo(14f, 2f)
            // 指针
            moveTo(12f, 13f)
            lineTo(12f, 8.5f)
            moveTo(12f, 13f)
            lineTo(15f, 14.5f)
        }
    }

    val BookWrong: ImageVector by lazy {
        icon("AppBookWrong") {
            // 书本
            moveTo(4f, 5f)
            curveTo(4f, 4.2f, 4.7f, 3.5f, 5.5f, 3.5f)
            lineTo(18.5f, 3.5f)
            curveTo(19.3f, 3.5f, 20f, 4.2f, 20f, 5f)
            lineTo(20f, 19f)
            curveTo(20f, 19.8f, 19.3f, 20.5f, 18.5f, 20.5f)
            lineTo(5.5f, 20.5f)
            curveTo(4.7f, 20.5f, 4f, 19.8f, 4f, 19f)
            close()
            // X 标记
            moveTo(9.2f, 9.2f)
            lineTo(14.8f, 14.8f)
            moveTo(14.8f, 9.2f)
            lineTo(9.2f, 14.8f)
        }
    }

    val Tune: ImageVector by lazy {
        icon("AppTune") {
            // 三条滑杆线
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            // 三个滑块（实心圆点视觉：小圆）
            moveTo(15f, 7f)
            lineTo(15f, 7.01f)
            moveTo(8f, 12f)
            lineTo(8f, 12.01f)
            moveTo(12.5f, 17f)
            lineTo(12.5f, 17.01f)
        }
    }

    // ---------- 通用 ----------

    val Grid: ImageVector by lazy {
        icon("AppGrid") {
            moveTo(4.5f, 4.5f)
            lineTo(9.5f, 4.5f)
            lineTo(9.5f, 9.5f)
            lineTo(4.5f, 9.5f)
            close()
            moveTo(14.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 9.5f)
            lineTo(14.5f, 9.5f)
            close()
            moveTo(4.5f, 14.5f)
            lineTo(9.5f, 14.5f)
            lineTo(9.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
            moveTo(14.5f, 14.5f)
            lineTo(19.5f, 14.5f)
            lineTo(19.5f, 19.5f)
            lineTo(14.5f, 19.5f)
            close()
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("AppChevronRight") {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("AppChevronLeft") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }

    val Check: ImageVector by lazy {
        icon("AppCheck") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }

    val Close: ImageVector by lazy {
        icon("AppClose") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Flame: ImageVector by lazy {
        icon("AppFlame") {
            moveTo(12f, 21f)
            curveTo(8.7f, 21f, 6f, 18.3f, 6f, 15f)
            curveTo(6f, 10f, 12f, 3f, 12f, 3f)
            curveTo(12f, 3f, 18f, 10f, 18f, 15f)
            curveTo(18f, 18.3f, 15.3f, 21f, 12f, 21f)
            close()
            moveTo(12f, 21f)
            curveTo(10.3f, 21f, 9f, 19.7f, 9f, 18f)
            curveTo(9f, 15.5f, 12f, 12.5f, 12f, 12.5f)
            curveTo(12f, 12.5f, 15f, 15.5f, 15f, 18f)
            curveTo(15f, 19.7f, 13.7f, 21f, 12f, 21f)
        }
    }

    val Play: ImageVector by lazy {
        icon("AppPlay") {
            moveTo(8f, 5.5f)
            lineTo(18f, 12f)
            lineTo(8f, 18.5f)
            close()
        }
    }

    val Bell: ImageVector by lazy {
        icon("AppBell") {
            moveTo(12f, 3.5f)
            curveTo(9f, 3.5f, 7f, 5.8f, 7f, 9f)
            lineTo(7f, 13f)
            lineTo(5.5f, 16.5f)
            lineTo(18.5f, 16.5f)
            lineTo(17f, 13f)
            lineTo(17f, 9f)
            curveTo(17f, 5.8f, 15f, 3.5f, 12f, 3.5f)
            close()
            moveTo(10f, 19.5f)
            curveTo(10.4f, 20.4f, 11.1f, 21f, 12f, 21f)
            curveTo(12.9f, 21f, 13.6f, 20.4f, 14f, 19.5f)
        }
    }

    val Import: ImageVector by lazy {
        icon("AppImport") {
            moveTo(12f, 3.5f)
            lineTo(12f, 14f)
            moveTo(7.5f, 10f)
            lineTo(12f, 14.5f)
            lineTo(16.5f, 10f)
            moveTo(4.5f, 17.5f)
            lineTo(4.5f, 19f)
            curveTo(4.5f, 19.8f, 5.2f, 20.5f, 6f, 20.5f)
            lineTo(18f, 20.5f)
            curveTo(18.8f, 20.5f, 19.5f, 19.8f, 19.5f, 19f)
            lineTo(19.5f, 17.5f)
        }
    }

    val Trash: ImageVector by lazy {
        icon("AppTrash") {
            moveTo(4.5f, 6.5f)
            lineTo(19.5f, 6.5f)
            moveTo(9f, 6.5f)
            lineTo(9f, 4.5f)
            lineTo(15f, 4.5f)
            lineTo(15f, 6.5f)
            moveTo(6.5f, 6.5f)
            lineTo(7.5f, 19f)
            curveTo(7.6f, 19.8f, 8.2f, 20.5f, 9f, 20.5f)
            lineTo(15f, 20.5f)
            curveTo(15.8f, 20.5f, 16.4f, 19.8f, 16.5f, 19f)
            lineTo(17.5f, 6.5f)
            moveTo(10f, 10f)
            lineTo(10f, 17f)
            moveTo(14f, 10f)
            lineTo(14f, 17f)
        }
    }

    val Refresh: ImageVector by lazy {
        icon("AppRefresh") {
            moveTo(20f, 12f)
            curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
            curveTo(8.9f, 4f, 6.2f, 5.8f, 4.9f, 8.4f)
            moveTo(4f, 12f)
            curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
            curveTo(15.1f, 20f, 17.8f, 18.2f, 19.1f, 15.6f)
            moveTo(4.9f, 4.5f)
            lineTo(4.9f, 8.9f)
            lineTo(9.3f, 8.9f)
            moveTo(19.1f, 19.5f)
            lineTo(19.1f, 15.1f)
            lineTo(14.7f, 15.1f)
        }
    }

    val Dots: ImageVector by lazy {
        icon("AppDots") {
            moveTo(5.5f, 12f)
            lineTo(5.51f, 12f)
            moveTo(12f, 12f)
            lineTo(12.01f, 12f)
            moveTo(18.5f, 12f)
            lineTo(18.51f, 12f)
        }
    }

    val Filter: ImageVector by lazy {
        icon("AppFilter") {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(7f, 12f)
            lineTo(17f, 12f)
            moveTo(10f, 18f)
            lineTo(14f, 18f)
        }
    }
}
