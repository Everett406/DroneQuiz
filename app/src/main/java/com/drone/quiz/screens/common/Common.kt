package com.drone.quiz.screens.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ui.theme.LocalUi

/**
 * 分段选择器：槽内滑动的墨色胶囊。
 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(ui.ink.copy(alpha = if (ui.isDark) 0.22f else 0.07f))
    ) {
        val segWidth = maxWidth / options.size
        val pillX by animateDpAsState(
            targetValue = segWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
            label = "segPill"
        )
        Box(
            Modifier
                .offset(x = pillX + 3.dp, y = 3.dp)
                .size(width = segWidth - 6.dp, height = 34.dp)
                .clip(RoundedCornerShape(50))
                .background(ui.ink)
        )
        Row(Modifier.height(40.dp)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (i == selectedIndex) ui.onInk else ui.textSub,
                        fontSize = 13.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 小标签（类别/题型）。
 */
@Composable
fun TagChip(text: String, color: Color = LocalUi.current.textSub, outline: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (outline) Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                else Modifier.background(color.copy(alpha = 0.12f))
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 卡片区块标题。
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp),
        color = ui.textSub,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * 顶部柔化：内容滚入固定标题下方时按 alpha 渐隐（DstIn 蒙版，
 * 不依赖背景色，任意渐变/壁纸/玻璃面板上都干净）。
 */
fun Modifier.softTopFade(fadeHeight: Dp = 26.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = fadeHeight.toPx()
        if (h > 0f && size.height > h) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, 1f to Color.Black,
                    startY = 0f, endY = h
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * 上下双向柔化（答题卡网格上下边缘渐隐，替代硬切行）。
 */
fun Modifier.softVerticalEdges(top: Dp = 20.dp, bottom: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val th = top.toPx()
        val bh = bottom.toPx()
        if (size.height > th + bh) {
            if (th > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent, 1f to Color.Black,
                        startY = 0f, endY = th
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            if (bh > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black, 1f to Color.Transparent,
                        startY = size.height - bh, endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
    }

/**
 * 屏幕大标题（每页最顶部）。
 */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Column(modifier = modifier) {
        Text(
            title,
            color = ui.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = ui.textSub,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
