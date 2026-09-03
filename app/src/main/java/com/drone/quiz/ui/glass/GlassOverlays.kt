package com.drone.quiz.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import com.drone.quiz.ui.theme.LocalUi

/**
 * 弹窗打开时置 true：AppRoot 据此对内容层施加真模糊（iOS 风格），
 * 取代传统的深色遮罩——背后内容被模糊虚化，玻璃面板折射背景层。
 */
object OverlayBlur {
    var active by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * 玻璃覆盖层通用骨架。
 *
 * ⚠️ 必须与 AppRoot 同窗口渲染（不能用 Dialog/ModalBottomSheet——独立窗口无法采样主窗口 backdrop）。
 * 覆盖层位于内容记录层内，只能折射 bgBackdrop（背景层），不可折射包含自身的内容层（循环 → SIGSEGV）。
 *
 * 背景处理：不再使用深色遮罩——OverlayBlur.active 置 true 后 AppRoot 对内容层做真模糊；
 * scrim 仅保留一层极淡的颜色层用于层次感。点击 scrim 关闭；面板消费自身点按防误关。
 */
@Composable
private fun GlassOverlay(
    scrimColor: Color,
    contentAlignment: Alignment,
    panelShape: Shape,
    panelModifier: Modifier,
    backdrop: com.kyant.backdrop.Backdrop,
    onDismiss: () -> Unit,
    panel: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismiss)

    // 弹窗存续期间让内容层进入模糊态（AppRoot 响应），退出自动恢复
    DisposableEffect(Unit) {
        OverlayBlur.active = true
        onDispose { OverlayBlur.active = false }
    }

    Box(Modifier.fillMaxSize()) {
        // 极淡 scrim：仅层次感，主角是内容层真模糊（点按关闭）
        Box(
            Modifier
                .matchParentSize()
                .background(scrimColor)
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                }
        )
        // 面板：真玻璃（折射背景层）；安全模式自动降级为实色
        Box(
            Modifier
                .align(contentAlignment)
                .then(panelModifier)
                .pointerInput(Unit) { detectTapGestures { } } // 消费面板内点按，防误关
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(
                        backdrop = backdrop,
                        shape = panelShape,
                        blurDp = 24.dp,
                        lensHeightDp = 16.dp,
                        lensAmountDp = 22.dp,
                        // 降透明度让折射可见：此前 0.9 过实，看起来像不透明色块而非玻璃
                        surfaceColor = LocalUi.current.surface.copy(alpha = 0.62f)
                    )
                    .then(Modifier) // 占位保持链式可读
            ) {
                panel()
            }
        }
    }
}

/**
 * iOS 26 风玻璃底部面板（替代 ModalBottomSheet）。
 * 调用方用 AnimatedVisibility 包裹以获得出入场动画。
 */
@Composable
fun GlassBottomSheet(
    backdrop: com.kyant.backdrop.Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val ui = LocalUi.current
    GlassOverlay(
            // 极淡 scrim 配合内容层真模糊；不再有明显黑罩感
            scrimColor = Color.Black.copy(alpha = if (ui.isDark) 0.22f else 0.10f),
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
            onDismiss = onDismiss
        ) {
            content()
            Spacer(Modifier.height(16.dp))
        }
}

/**
 * iOS 26 风玻璃对话框（替代 AlertDialog）。
 */
@Composable
fun GlassConfirmDialog(
    backdrop: com.kyant.backdrop.Backdrop,
    title: String,
    body: String,
    confirmText: String,
    dismissText: String,
    confirmColor: Color? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val ui = LocalUi.current
    GlassOverlay(
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

/**
 * 出入场动画（面板上滑 + 渐显；对话框缩放淡入由调用方按需扩展）。
 */
@Composable
fun SheetVisibility(visible: Boolean, onDismissed: () -> Unit = {}, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
            targetOffsetY = { it }
        ) + fadeOut()
    ) {
        content()
    }
}
