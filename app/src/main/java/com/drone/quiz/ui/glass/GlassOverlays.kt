package com.drone.quiz.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.launch

/**
 * 弹窗打开时置 true：AppRoot 据此对内容层施加真模糊（iOS 风格）。
 * 注意：弹窗面板本身已通过 GlassOverlayPortal 渲染在模糊区之外，不会被连帶模糊。
 */
object OverlayBlur {
    var active by mutableStateOf(false)
}

/**
 * 弹窗传送门。
 *
 * 背景：v2.4.0 的做法是把整个内容层 blur——但弹窗面板本身就渲染在内容层里，
 * 于是"答题卡展开时把答题卡自己也模糊了"。
 *
 * v2.5.0：弹窗面板不再原地渲染，而是注册进 [entries]，由 AppRoot 在内容层
 * （模糊区）之上、底栏之上统一渲染。面板因此永远清晰，且位于内容记录层之外，
 * 可安全折射"背景+内容"合成层（真 iOS 玻璃，此前因循环采样风险只能折射背景层）。
 *
 * 多槽位列表：同屏多个弹窗（如模考页确认框 + 答题卡）互不覆盖，后组合者在上。
 */
object GlassOverlayPortal {

    class Entry(val id: Any, val content: @Composable () -> Unit)

    val entries = mutableStateListOf<Entry>()

    /** 组合期间调用：按 id 注册/刷新面板内容（幂等，每次重组刷新 lambda）。 */
    fun set(id: Any, content: @Composable () -> Unit) {
        val idx = entries.indexOfFirst { it.id === id }
        if (idx >= 0) {
            entries[idx] = Entry(id, content)
        } else {
            entries.add(Entry(id, content))
        }
    }

    fun remove(id: Any) {
        entries.removeAll { it.id === id }
    }
}

/** AppRoot 提供的双记录层；弹窗面板用它合成折射源。 */
val LocalBgBackdrop = compositionLocalOf<Backdrop?> { null }
val LocalContentBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * 弹窗主体（scrim + 玻璃面板），渲染在 AppRoot 顶层。
 *
 * - scrim 极淡：层次感主角是内容层真模糊（AppRoot 响应 OverlayBlur.active）；
 *   点 scrim 关闭，面板消费自身点按防误关。
 * - 面板折射 combined(背景层, 内容层)——内容层此刻正被模糊，折射出的正是
 *   "毛玻璃后的世界"。
 */
@Composable
private fun GlassOverlayPanel(
    scrimColor: Color,
    contentAlignment: Alignment,
    panelShape: Shape,
    panelModifier: Modifier,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    panelOffsetProvider: (() -> Float)? = null,
    headerStrip: (@Composable ColumnScope.() -> Unit)? = null,
    panel: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(scrimColor)
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                }
        )
        val bg = LocalBgBackdrop.current
        val contentBd = LocalContentBackdrop.current
        // 面板已处于内容记录层之外 → 折射"背景+内容"安全（与底栏同款）
        val panelBackdrop = if (bg != null && contentBd != null) {
            rememberCombinedBackdrop(bg, contentBd)
        } else {
            backdrop
        }
        Box(
            Modifier
                .align(contentAlignment)
                .then(panelModifier)
                .then(
                    if (panelOffsetProvider != null) Modifier.graphicsLayer {
                        translationY = panelOffsetProvider().coerceAtLeast(0f)
                    } else Modifier
                )
                .pointerInput(Unit) { detectTapGestures { } } // 消费面板内点按，防误关
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(
                        backdrop = panelBackdrop,
                        shape = panelShape,
                        blurDp = 24.dp,
                        lensHeightDp = 16.dp,
                        lensAmountDp = 22.dp,
                        // 降透明度让折射可见：过实会像不透明色块而非玻璃
                        surfaceColor = LocalUi.current.surface.copy(alpha = 0.62f)
                    )
            ) {
                if (headerStrip != null) headerStrip()
                panel()
            }
        }
    }
}

/**
 * 弹窗注册骨架：BackHandler + OverlayBlur 状态 + 传送门生命周期。
 * 本体不渲染任何东西；[content]（含出入场动画，由各弹窗自带）在 AppRoot 顶层渲染。
 */
@Composable
private fun GlassOverlayRegistration(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    val overlayId = remember { Any() }

    // 弹窗可见期间内容层进入模糊态（AppRoot 响应）；随 visible 关闭
    DisposableEffect(visible) {
        OverlayBlur.active = visible
        onDispose { }
    }
    // 整体离开组合：兜底恢复模糊态并注销传送门槽位
    DisposableEffect(Unit) {
        onDispose {
            OverlayBlur.active = false
            GlassOverlayPortal.remove(overlayId)
        }
    }

    GlassOverlayPortal.set(overlayId, content)
}

/**
 * iOS 26 风玻璃底部面板（替代 ModalBottomSheet）。
 * - 无深色遮罩：内容层真模糊已足够层次（用户反馈）；scrim 仅作点击关闭的透明层
 * - 顶部小把手：拖动把手区可下滑跟手，松手超过阈值关闭、否则弹性回位
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassOverlayRegistration(visible, onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                initialOffsetY = { it }
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
                targetOffsetY = { it }
            ) + fadeOut(tween(180))
        ) {
            val ui = LocalUi.current
            val density = LocalDensity.current
            val dismissThreshold = with(density) { 110.dp.toPx() }
            val dragY = remember { Animatable(0f) }
            val dragScope = rememberCoroutineScope()

            // 每次打开复位拖拽偏移
            androidx.compose.runtime.LaunchedEffect(visible) {
                if (visible) dragY.snapTo(0f)
            }

            GlassOverlayPanel(
                // 无深色遮罩：透明层仅承担点击关闭
                scrimColor = Color.Transparent,
                contentAlignment = Alignment.BottomCenter,
                panelShape = RoundedCornerShape(28.dp),
                panelModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(
                        bottom = 12.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                    ),
                backdrop = backdrop,
                onDismiss = onDismiss,
                panelOffsetProvider = { dragY.value },
                headerStrip = {
                    // 把手 + 可拖拽热区（仅顶部条响应拖拽，不与内部网格滚动冲突）
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragScope.launch { dragY.stop() } },
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        dragScope.launch {
                                            dragY.snapTo((dragY.value + dy).coerceAtLeast(0f))
                                        }
                                    },
                                    onDragEnd = {
                                        dragScope.launch {
                                            if (dragY.value > dismissThreshold) {
                                                onDismiss()
                                            } else {
                                                dragY.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.85f, stiffness = 380f)
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        dragScope.launch {
                                            dragY.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.85f, stiffness = 380f)
                                            )
                                        }
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 4.5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ui.textSub.copy(alpha = 0.45f))
                        )
                    }
                }
            ) {
                content()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 锚点玻璃小菜单（v2.8.2，替代首页题库切换的 Popup + 原生 surface）：
 * - 走传送门渲染在内容层之上，面板折射「背景+内容」合成层 → 与其他弹窗同款液态玻璃；
 * - 锚定在触发者（副标题胶囊）下方，入场缩放+淡入动画（transformOrigin 在锚点方向）；
 * - 透明 scrim 点击关闭；OverlayBlur 生效期间内容层+背景层一起被真模糊。
 *
 * @param anchorPx 触发者在窗口坐标系下的 (x, y) 像素位置与高度（px）
 */
@Composable
fun GlassAnchorMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    anchorXpx: Float,
    anchorYpx: Float,
    anchorHeightPx: Float,
    backdrop: Backdrop,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassOverlayRegistration(visible, onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f)
            ),
            exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(130))
        ) {
            val density = LocalDensity.current
            val xDp = with(density) { anchorXpx.toDp() }
            // 菜单顶部 = 触发者底部 + 8dp 间距；最小不低于状态栏之下
            val yDp = with(density) { (anchorYpx + anchorHeightPx + 8f).toDp() }
            GlassOverlayPanel(
                scrimColor = Color.Transparent,
                contentAlignment = Alignment.TopStart,
                panelShape = RoundedCornerShape(18.dp),
                panelModifier = Modifier
                    .padding(start = xDp, top = yDp)
                    .width(248.dp),
                backdrop = backdrop,
                onDismiss = onDismiss
            ) {
                Column(Modifier.padding(vertical = 6.dp)) { content() }
            }
        }
    }
}

/**
 * iOS 26 风玻璃对话框（替代 AlertDialog）。
 * 保持 `if (show) GlassConfirmDialog(...)` 的调用形式：进组合即注册传送门，
 * 出组合即注销（面板缩放淡入；退场即时，与既有行为一致）。
 */
@Composable
fun GlassConfirmDialog(
    backdrop: Backdrop,
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    confirmColor: Color? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassOverlayRegistration(visible = true, onDismiss = onDismiss) {
        // 对话框缩放淡入（退场因组合销毁即时消失，与旧行为一致）
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
            ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.95f, animationSpec = tween(140))
        ) {
            val ui = LocalUi.current
            GlassOverlayPanel(
                scrimColor = Color.Black.copy(alpha = if (ui.isDark) 0.22f else 0.10f),
                contentAlignment = Alignment.Center,
                panelShape = RoundedCornerShape(26.dp),
                panelModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                backdrop = backdrop,
                onDismiss = onDismiss
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                    Text(
                        title,
                        color = ui.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        body,
                        color = ui.textSub,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassButton(
                            onClick = onDismiss,
                            backdrop = backdrop,
                            heightDp = 44.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(dismissText, color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GlassButton(
                            onClick = onConfirm,
                            backdrop = backdrop,
                            surfaceColor = (confirmColor ?: ui.ink).copy(alpha = 0.92f),
                            heightDp = 44.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                confirmText,
                                color = if (confirmColor != null) Color.White else ui.onInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
