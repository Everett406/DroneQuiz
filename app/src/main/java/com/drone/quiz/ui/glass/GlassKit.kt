package com.drone.quiz.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.BlendMode
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
 * 静态玻璃表面：真折射（AGSL lens）+ 真模糊 + vibrancy + 表面着色。
 * shape 必须是 CornerBasedShape（RoundedCornerShape）。
 */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    blurDp: Dp = 16.dp,
    lensHeightDp: Dp = 18.dp,
    lensAmountDp: Dp = 24.dp,
    surfaceColor: Color? = null,
    depth: Boolean = false
): Modifier = drawBackdrop(
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
 * 玻璃卡片容器。
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    surfaceAlpha: Float = 0.5f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(cornerRadius)
    val press = if (onClick != null) rememberPressScale() else Modifier
    Box(
        modifier
            .then(press)
            .glass(
                backdrop = backdrop,
                shape = shape,
                blurDp = 18.dp,
                lensHeightDp = 14.dp,
                lensAmountDp = 20.dp,
                surfaceColor = ui.surface.copy(alpha = surfaceAlpha)
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        content = content
    )
}

/**
 * 液态玻璃按钮（按下时折射增强、轻微膨胀、位置跟随手势）。
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
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier
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
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
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
            .padding(horizontal = 18.dp),
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
    iconTint: Color = Color.Unspecified
) {
    val ui = LocalUi.current
    Box(modifier) {
        GlassButton(
            onClick = onClick,
            backdrop = backdrop,
            surfaceColor = ui.surface.copy(alpha = 0.55f),
            heightDp = sizeDp
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
 */
@Composable
fun GlassSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0f,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current
    val trackColor =
        if (!ui.isDark) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val accentColor = ui.ink

    val trackBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val blurPx = with(density) { 8.dp.toPx() }
    val lens1 = with(density) { 10.dp.toPx() }
    val lens2 = with(density) { 14.dp.toPx() }

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

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
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
                    .height(6.dp)
                    .fillMaxWidth()
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

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            (if (isLtr) 1f else -1f)
                }
                .then(dampedDragAnimation.modifier)
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
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
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

/**
 * 玻璃开关。
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current
    val progress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "toggle"
    )

    Box(
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
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = (size.width - 24.dp.toPx() - 4.dp.toPx()) * progress.value
                }
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}
