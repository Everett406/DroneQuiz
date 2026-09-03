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
import androidx.compose.runtime.collectAsState
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
import com.drone.quiz.ServiceLocator
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
 * 列表/网格/滚动容器"顶部已滚出的像素"（供柔化连续渐显；
 * 在 draw 阶段读取，状态变化只触发重绘，零重组、零 Modifier 重建）。
 */
fun androidx.compose.foundation.lazy.LazyListState.scrolledFromTopPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE * 0.5f else firstVisibleItemScrollOffset.toFloat()

fun androidx.compose.foundation.lazy.grid.LazyGridState.scrolledFromTopPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE * 0.5f else firstVisibleItemScrollOffset.toFloat()

fun androidx.compose.foundation.ScrollState.scrolledFromTopPx(): Float = value.toFloat()

/**
 * 网格"距底部剩余像素"近似（未显示 item 数 × 平均可视行高 + 尾部残量），
 * 用于底部柔化连续渐显。
 */
fun androidx.compose.foundation.lazy.grid.LazyGridState.remainingBottomPx(): Float {
    val info = layoutInfo
    val infos = info.visibleItemsInfo
    if (infos.isEmpty()) return 0f
    val last = infos.last()
    if (last.index >= info.totalItemsCount - 1) return 0f
    val lastBottom = last.offset.y + last.size.height
    val tail = (info.viewportEndOffset - lastBottom).coerceAtLeast(0)
    val avg = infos.map { it.size.height }.average().toFloat().coerceAtLeast(1f)
    return tail + (info.totalItemsCount - 1 - last.index) * avg
}

/**
 * 上下双向柔化（答题卡网格上下边缘渐隐，替代硬切行）。
 * 同样 draw 阶段读强度：topScrolledPx = 顶部已滚出像素，
 * bottomRemainingPx = 距底部剩余像素；贴边的一侧自动无柔化，题号不再被裁切。
 */
fun Modifier.softVerticalEdges(
    top: Dp = 20.dp,
    bottom: Dp = 24.dp,
    topScrolledPx: () -> Float = { Float.MAX_VALUE * 0.5f },
    bottomRemainingPx: () -> Float = { Float.MAX_VALUE * 0.5f }
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val th = top.toPx()
        val bh = bottom.toPx()
        val ts = (topScrolledPx() / th).coerceIn(0f, 1f)
        val bs = (bottomRemainingPx() / bh).coerceIn(0f, 1f)
        if (size.height > th + bh) {
            if (th > 0f && ts > 0.01f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = ts),
                        startY = 0f, endY = th
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            if (bh > 0f && bs > 0.01f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = bs), 1f to Color.Black.copy(alpha = 0f),
                        startY = size.height - bh, endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
    }

/**
 * 标题下缘雾化条（v2.6.3：彻底替代列表离屏蒙版柔化，根除伪影）。
 *
 * 此前方案：对整个列表做离屏 + DstIn 渐隐蒙版——蒙版层与列表内液态玻璃卡的
 * 折射层相互作，产生重影/色块伪影（蒙版×玻璃为方案固有问题，modifier 稳定化
 * 只能减轻不能根除，用户 v2.6.2 实测确认"依然存在，只是减轻了一点点"）。
 *
 * 新方案：在内容之上叠一条"背景色雾"——纯渐变色块，零离屏、零蒙版、零
 * graphicsLayer，与玻璃卡完全无互作，必然无伪影。内容滚入标题下方时被雾
 * 遮没，视觉等效柔化。雾色跟随主题（亮米白/深墨），壁纸模式取纱色保持一致。
 * 滚离顶部才渐显（连续跟手），停在顶部时无雾、首屏内容不被遮挡。
 */
@Composable
fun TopFog(
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    scrolledPx: (() -> Float)? = null
) {
    val ui = LocalUi.current
    val settings by ServiceLocator.settings.settings.collectAsState(
        initial = com.drone.quiz.data.settings.AppSettings()
    )
    val base = if (settings.wallpaper.isBlank()) {
        if (ui.isDark) Color(0xFF1C1815) else Color(0xFFFAF6EF)
    } else {
        if (ui.isDark) Color(0xFF161310) else Color(0xFFF6F1E6)
    }
    // 强度：顶部已滚出像素 / 雾高（连续跟手）；未传滚动源则恒显
    val strength = if (scrolledPx != null) {
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }
        (scrolledPx() / hPx).coerceIn(0f, 1f)
    } else 1f
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to base.copy(alpha = 0.98f * strength),
                    0.5f to base.copy(alpha = 0.55f * strength),
                    1f to base.copy(alpha = 0f)
                )
            )
    )
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
