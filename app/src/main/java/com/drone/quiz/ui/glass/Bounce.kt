package com.drone.quiz.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS 式上下过冲回弹（rubber-band），v2.5.1 按社区标准实现（sinasamaki
 * "Overscroll animations in Jetpack Compose" 的 NestedScroll 方案）重写。
 *
 * 此前两版手感差的根因：
 * - v2.4.x：90ms 定时器兜底与手势抢状态（抖动）；
 * - v2.5.0：显示曲线起手斜率只有 0.55（一起手就跟不上手）+ 渐近线仅 110dp +
 *   回弹弹簧把松手速度当初速度且无钳制（会甩过头荡到另一侧）。
 *
 * 本版要点：
 * - 原始过冲量与显示量分离：显示量 = 缓动曲线(原始量 / 容器高度×1.5) × 容器高度。
 *   CubicBezier(0.5, 0.5, 1.0, 0.25) 起手斜率恰为 1:1（绝对跟手），越拉越"韧"，
 *   渐近线为容器高度（iOS 同款用 dimension，不是固定小上限）。
 * - 拖动过冲用 snapTo 直写（零延迟）；已过冲时只允许向零回退，不许反向叠加。
 * - 松手甩动的三种走向（onPreFling，exponentialDecay 预测）：
 *   · 甩不回内容侧 → StiffnessLow 软弹簧带速度回弹，速度全部消费；
 *   · 会冲过零点 → 过冲量按摩擦衰减滑行，过零瞬间 snapTo(0)，
 *     剩余速度原样交还列表继续滚动（iOS 式无缝衔接，不吞甩动）；
 *   · 零速度松手 → 直接软弹回。
 * - 惯性撞边：onPostScroll 只做显示跟踪，fling 结束 onPostFling 带残余速度软弹回。
 */
class BounceState internal constructor(
    private val scope: CoroutineScope
) {

    /** 原始过冲量（无界）。 */
    private val amount = Animatable(0f)

    /** 显示位移（px），由缓动曲线从原始量饱和映射而来。 */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 容器高度（px），onSizeChanged 更新；决定过冲渐近线。 */
    private var length = 1f

    // 起手 1:1 跟手、渐进变重的橡胶曲线
    private val easing = CubicBezierEasing(0.5f, 0.5f, 1.0f, 0.25f)

    private fun applyDisplay() {
        val a = amount.value
        offset = sign(a) * easing.transform(abs(a) / (length * 1.5f)).coerceIn(0f, 1f) * length
    }

    /** 过冲量累加：已过冲时只允许向零回退，不许反向叠加。 */
    private fun accumulated(delta: Float): Float {
        val previous = amount.value
        val next = previous + delta
        return when {
            previous > 0f -> next.coerceAtLeast(0f)
            previous < 0f -> next.coerceAtMost(0f)
            else -> next
        }
    }

    /** 容器尺寸变化时由修饰符回调。 */
    fun onContainerSizeChanged(sizePx: Int) {
        if (sizePx > 0) length = sizePx.toFloat()
    }

    val connection = object : NestedScrollConnection {

        // 拖动中已过冲：过冲层优先消费（snapTo 直写，零延迟跟手）
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (amount.value != 0f && source != NestedScrollSource.SideEffect) {
                scope.launch {
                    amount.snapTo(accumulated(available.y))
                    applyDisplay()
                }
                return available
            }
            return Offset.Zero
        }

        // 子列表到边后的剩余量（拖动/惯性）：转入过冲显示；不消费，
        // 速度信息保留给 onPostFling 做带速度回弹
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y != 0f) {
                scope.launch {
                    amount.snapTo(accumulated(available.y))
                    applyDisplay()
                }
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (amount.value != 0f && available.y != 0f) {
                val previousSign = sign(amount.value)
                var unconsumed = available.y
                // 过零预测（不依赖 DecayAnimationSpec.calculateTargetValue——
                // 新版 Compose 已移除该成员）：惯性滑行距离 ≈ |v|×0.5s（spline 衰减经验值），
                // 能越过剩余过冲量即判定会过零
                val towardZero = sign(available.y) != previousSign
                val willCross = towardZero && abs(available.y) * 0.5f > abs(amount.value)
                if (!willCross) {
                    // 甩不回内容侧：软弹簧带松手速度回弹，速度全部消费
                    amount.animateTo(
                        0f,
                        spring(stiffness = 200f),
                        initialVelocity = available.y
                    ) { applyDisplay() }
                } else {
                    // 强甩向内容侧：摩擦衰减滑行，过零瞬间交还剩余速度（无缝衔接）
                    try {
                        amount.animateDecay(
                            initialVelocity = available.y,
                            animationSpec = exponentialDecay()
                        ) {
                            if (sign(value) != previousSign) {
                                unconsumed -= velocity
                                scope.launch {
                                    amount.snapTo(0f)
                                    applyDisplay()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // snapTo(0) 会取消本衰减动画（预期路径），吞掉即可
                    }
                }
                return Velocity(0f, unconsumed)
            }
            if (amount.value != 0f) {
                // 零速度松手：直接软弹回
                amount.animateTo(0f, spring(stiffness = 200f)) { applyDisplay() }
                return Velocity.Zero
            }
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // 惯性撞边结束：带残余速度软弹回
            if (amount.value != 0f) {
                amount.animateTo(
                    0f,
                    spring(stiffness = 200f),
                    initialVelocity = available.y
                ) { applyDisplay() }
            }
            return available
        }
    }
}

@Composable
fun rememberBounceState(): BounceState {
    val scope = rememberCoroutineScope()
    return remember { BounceState(scope) }
}

/**
 * 带上下回弹的 LazyColumn；contentPadding.bottom 请留出底栏高度。
 */
@Composable
fun BounceLazyColumn(
    modifier: Modifier = Modifier,
    state: BounceState = rememberBounceState(),
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier
            .graphicsLayer { translationY = state.offset }
            .onSizeChanged { state.onContainerSizeChanged(it.height) }
            .nestedScroll(state.connection)
    ) {
        LazyColumn(content = content)
    }
}

/**
 * 带上下回弹的容器（包 verticalScroll 内容）。
 */
@Composable
fun BounceContainer(
    modifier: Modifier = Modifier,
    state: BounceState = rememberBounceState(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .graphicsLayer { translationY = state.offset }
            .onSizeChanged { state.onContainerSizeChanged(it.height) }
            .nestedScroll(state.connection),
        content = content
    )
}
