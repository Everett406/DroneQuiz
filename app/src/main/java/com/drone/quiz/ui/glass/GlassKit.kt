package com.drone.quiz.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 全局玻璃特效开关。
 * 安全模式（启动异常自动降级）或用户在设置中关闭后，
 * 所有折射玻璃退化为质感材质（无 RenderEffect/AGSL），保证可用性。
 */
object GlassRuntime {
    var enabled by mutableStateOf(true)
}

/**
 * 【官方 backdrop 库硬约束】drawBackdrop 节点不得位于 Modifier.layerBackdrop 记录层内部，
 * 否则形成"内容绘制自身"的循环引用 → RenderThread SIGSEGV（官方 FAQ / glass-bottom-sheet 教程）。
 *
 * 因此本 APP 的玻璃使用规则：
 * - 滚动内容流中的卡片/按钮/滑杆/开关：默认使用 [glassMaterial]（iOS regular material 观感）
 * - 记录层外的浮动元素（底栏、弹窗、悬浮条）：refracts = true 使用真折射玻璃
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    blurDp: Dp = 16.dp,
    lensHeightDp: Dp = 18.dp,
    lensAmountDp: Dp = 24.dp,
    surfaceColor: Color? = null,
    depth: Boolean = false
): Modifier = if (!GlassRuntime.enabled) {
    Modifier
        .clip(shape)
        .then(if (surfaceColor != null) Modifier.background(surfaceColor) else Modifier)
} else {
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurDp.toPx())
            lens(lensHeightDp.toPx(), lensAmountDp.toPx(), depthEffect = depth)
        },
        onDrawSurface = {
            if (surfaceColor != null) drawRect(surfaceColor)
        }
    )
}

/**
 * 内容流质感材质：半透明渐变表面 + 顶部高光描边 + 轻阴影。
 * 不采样 backdrop，无任何循环风险；同时也是安全模式的降级形态。
 */
@Composable
fun Modifier.glassMaterial(shape: Shape, elevated: Boolean = false): Modifier {
    val ui = LocalUi.current
    return this
        .shadow(
            elevation = (if (elevated) 10 else 5).dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (ui.isDark) 0.30f else 0.06f),
            spotColor = Color.Black.copy(alpha = if (ui.isDark) 0.42f else 0.10f)
        )
        .clip(shape)
        .background(
            if (ui.isDark) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.07f))
            ) else Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.56f))
            )
        )
        .border(
            width = 0.75.dp,
            brush = if (ui.isDark) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f))
            ) else Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.28f))
            ),
            shape = shape
        )
}

/**
 * 按压缩放（非玻璃元素也有的"按下去"手感）。
 */
@Composable
fun rememberPressScale(
    pressedScale: Float = 0.965f,
    enabled: Boolean = true
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by if (enabled) source.collectIsPressedAsState() else remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pressScale"
    )
    return Modifier
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {}
}

/**
 * 玻璃卡片容器。内容流中默认质感材质；浮动元素传 refracts = true 使用真折射。
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    surfaceAlpha: Float = 0.5f,
    refracts: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(cornerRadius)
    val clickMod = if (onClick != null) Modifier.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick
    ) else Modifier
    val surfaceMod = if (refracts && GlassRuntime.enabled) {
        Modifier.glass(
            backdrop = backdrop,
            shape = shape,
            blurDp = 18.dp,
            lensHeightDp = 14.dp,
            lensAmountDp = 20.dp,
            surfaceColor = ui.surface.copy(alpha = surfaceAlpha)
        )
    } else {
        Modifier.glassMaterial(shape)
    }
    Box(
        modifier
            .then(surfaceMod)
            .then(clickMod),
        content = content
    )
}

/**
 * 玻璃按钮（按压缩放；refracts 时按下折射增强、轻微膨胀、位置跟手）。
 * 内容流中默认质感材质胶囊。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    heightDp: Dp = 50.dp,
    isInteractive: Boolean = true,
    refracts: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val ui = LocalUi.current

    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "btnPressScale"
    )

    val glassActive = refracts && GlassRuntime.enabled

    val containerModifier = if (!glassActive) {
        // 材质模式（内容流默认 / 安全模式）：质感胶囊，保留按压缩放
        modifier
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .shadow(5.dp, RoundedCornerShape(50), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(50))
            .background(
                if (surfaceColor != Color.Unspecified) surfaceColor
                else if (ui.isDark) Color.White.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.72f)
            )
            .border(
                0.75.dp,
                if (ui.isDark) Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.06f))
                ) else Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.30f))
                ),
                RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = pressSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .height(heightDp)
            .padding(horizontal = 18.dp)
    } else {
        // 真折射模式：官方 LiquidButton 式按压折射增强 + 跟手位移
        modifier
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint != Color.Unspecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor != Color.Unspecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = pressSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(heightDp)
            .padding(horizontal = 18.dp)
    }

    Row(
        containerModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 圆形玻璃图标按钮。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 46.dp,
    iconSize: Dp = 21.dp,
    iconTint: Color = Color.Unspecified,
    refracts: Boolean = false
) {
    val ui = LocalUi.current
    Box(modifier) {
        GlassButton(
            onClick = onClick,
            backdrop = backdrop,
            heightDp = sizeDp,
            refracts = refracts
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconTint != Color.Unspecified) iconTint else ui.text,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * 可拖动玻璃滑杆（点击 + 连续拖动，支持步进吸附）。
 * 内容流中默认质感材质；轨道与滑块严格垂直居中。
 */
@Composable
fun GlassSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0f,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    refracts: Boolean = false
) {
    val ui = LocalUi.current
    val glassActive = refracts && GlassRuntime.enabled
    val trackColor =
        if (!ui.isDark) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val accentColor = ui.ink

    val density = LocalDensity.current
    val blurPx = with(density) { 8.dp.toPx() }
    val lens1 = with(density) { 10.dp.toPx() }
    val lens2 = with(density) { 14.dp.toPx() }
    val trackBackdrop = rememberLayerBackdrop()

    fun snap(v: Float): Float =
        if (step > 0f) {
            val k = ((v - valueRange.start) / step).let { kotlin.math.floor(it + 0.5f) }
            (valueRange.start + k * step).coerceIn(valueRange)
        } else v.coerceIn(valueRange)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val trackWidth = constraints.maxWidth.toFloat()

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = 0.01f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(snap(targetValue))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        snap(
                            if (isLtr) (targetValue + delta).coerceIn(valueRange)
                            else (targetValue - delta).coerceIn(valueRange)
                        )
                    )
                }
            )
        }
        androidx.compose.runtime.LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { v ->
                    if (abs(dampedDragAnimation.targetValue - v) > 0.001f) {
                        dampedDragAnimation.updateValue(v)
                    }
                }
        }

        // 轨道区：24dp 高的居中容器，6dp 轨道 + 填充条严格垂直居中
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(if (glassActive) Modifier.layerBackdrop(trackBackdrop) else Modifier),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta).coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(snap(targetValue))
                            onValueChange(snap(targetValue))
                        }
                    }
            )

            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable: Measurable, constraints: Constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).toInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        // 滑块：40x24，与轨道同一容器垂直居中（修复此前"偏下"）
        val thumbBase = Modifier
            .align(Alignment.CenterStart)
            .graphicsLayer {
                translationX =
                    (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                        .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                        (if (isLtr) 1f else -1f)
            }
            .then(dampedDragAnimation.modifier)

        if (!glassActive) {
            // 材质模式：实色滑块 + 轻阴影，无 RenderEffect
            Box(
                thumbBase
                    .shadow(4.dp, RoundedCornerShape(50), spotColor = Color.Black.copy(alpha = 0.22f))
                    .clip(RoundedCornerShape(50))
                    .background(if (ui.isDark) Color(0xFFF2EBDD) else Color.White)
                    .size(40.dp, 24.dp)
            )
        } else {
            Box(
                thumbBase
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 1f, progress)
                                val scaleY = lerp(0f, 1f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            blur(blurPx * (1f - progress))
                            lens(
                                lens1 * progress,
                                lens2 * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(radius = 4.dp * progress, alpha = progress)
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                Color.White.copy(
                                    alpha = (1f - progress) * if (ui.isDark) 0.25f else 0.9f
                                )
                            )
                        }
                    )
                    .size(40.dp, 24.dp)
            )
        }
    }
}

/**
 * 玻璃开关。内容流中默认质感材质轨道。
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    refracts: Boolean = false
) {
    val ui = LocalUi.current
    val glassActive = refracts && GlassRuntime.enabled
    val progress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "toggle"
    )

    val containerMod = if (!glassActive) {
        modifier
            .shadow(3.dp, RoundedCornerShape(50), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(50))
            .background(if (checked) ui.ink else (if (ui.isDark) Color.White.copy(0.16f) else ui.line))
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Switch
            ) { onCheckedChange(!checked) }
            .size(52.dp, 30.dp)
    } else {
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(10f.dp.toPx(), 18f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(ui.surface.copy(alpha = 0.6f))
                    drawRect(
                        if (checked) ui.accent.copy(alpha = 0.85f) else ui.ink.copy(alpha = 0.12f)
                    )
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Switch
            ) { onCheckedChange(!checked) }
            .size(52.dp, 30.dp)
    }

    Box(containerMod) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = (size.width - 24.dp.toPx() - 4.dp.toPx()) * progress.value
                }
                .size(24.dp)
                .shadow(2.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}
