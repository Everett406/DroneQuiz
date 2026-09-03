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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS 式上下过冲回弹（rubber-band）v4。
 *
 * v3 用户反馈"停止滑动它就停了、没有惯性"的两处根因：
 * 1. 过冲量存在 Animatable 里，拖动/惯性每帧经 scope.launch{ snapTo } 异步写入——
 *    fling 结束的 onPostFling 与排队中的 snapTo 存在时序竞态：读到旧值 0 就跳过回弹，
 *    随后 snapTo 又把过冲量写回非零 → amount 残留。此后每次松手 onPreFling 都误入
 *    "过冲处理"分支并同步挂起几百毫秒（软弹簧动画），fling 被延迟到回弹结束才开始，
 *    体感就是"甩完先顿一下、惯性没了"。
 * 2. onPreFling 内同步挂起动画本身就会阻塞嵌套滚动链。
 *
 * v4 结构：
 * - 原始过冲量 rawAmount 为 mutableFloatStateOf，拖动/惯性路径**同步直写**（零协程、
 *   零竞态、零延迟）；只有"松手回弹"用 Animatable 驱动（期间拖动会立刻取消它）。
 * - onPreFling **绝不同步挂起动画**：
 *   · 未过冲 → 原样交还速度（普通 fling 完全不受影响，这是用户反馈的场景）；
 *   · 过冲 + 向内容侧强甩（会过零）→ 立刻交还全部速度（列表立即带着惯性滚），
 *     过冲量异步摩擦衰减归零，与滚动并行（iOS 式衔接，无跳变、无阻塞）；
 *   · 过冲 + 甩不回/往外甩 → 异步软弹簧带速度回弹，速度消费（子列表在边缘无路可滚）；
 *   · 零速度松手 → 异步软弹回。
 */
class BounceState internal constructor(
    private val scope: CoroutineScope
) {

    /** 原始过冲量（无界，同步直写）。 */
    private var rawAmount by mutableFloatStateOf(0f)

    /** 显示位移（px），由缓动曲线从原始量饱和映射而来。 */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 容器高度（px），onSizeChanged 更新；决定过冲渐近线。 */
    private var length = 1f

    /** 松手回弹动画（拖动开始时取消）。 */
    private val releaseAnim = Animatable(0f)
    private var releaseJob: kotlinx.coroutines.Job? = null

    // 起手 1:1 跟手、渐进变重的橡胶曲线（sinasamaki 社区标准同款）
    private val easing = CubicBezierEasing(0.5f, 0.5f, 1.0f, 0.25f)

    private fun applyDisplay() {
        val a = rawAmount
        offset = sign(a) * easing.transform(abs(a) / (length * 1.5f)).coerceIn(0f, 1f) * length
    }

    /** 过冲量累加：已过冲时只允许向零回退，不许反向叠加。 */
    private fun accumulated(delta: Float): Float {
        val previous = rawAmount
        val next = previous + delta
        return when {
            previous > 0f -> next.coerceAtLeast(0f)
            previous < 0f -> next.coerceAtMost(0f)
            else -> next
        }
    }

    private fun cancelRelease() {
        releaseJob?.cancel()
        releaseJob = null
    }

    /** 容器尺寸变化时由修饰符回调。 */
    fun onContainerSizeChanged(sizePx: Int) {
        if (sizePx > 0) length = sizePx.toFloat()
    }

    val connection = object : NestedScrollConnection {

        // 拖动中已过冲：过冲层优先消费（同步直写，零延迟跟手）
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (rawAmount != 0f && source != NestedScrollSource.SideEffect) {
                cancelRelease()
                rawAmount = accumulated(available.y)
                applyDisplay()
                return available
            }
            return Offset.Zero
        }

        // 子列表到边后的剩余量（拖动/惯性）：转入过冲显示；不消费，
        // 速度信息保留给松手/惯性结束阶段
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y != 0f) {
                cancelRelease()
                rawAmount = accumulated(available.y)
                applyDisplay()
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // 注意：本函数内部零挂起点、立即返回——绝不阻塞嵌套滚动链
            if (rawAmount == 0f) return available   // 普通滚动：fling 原样放行，零干扰

            val previousSign = sign(rawAmount)
            val amountAbs = abs(rawAmount)
            val v = available.y

            if (v != 0f && sign(v) != previousSign && abs(v) * 0.5f > amountAbs) {
                // 强甩向内容侧（会过零）：全部速度立刻交还列表（惯性立即生效），
                // 过冲量异步摩擦衰减归零，与滚动并行
                cancelRelease()
                releaseJob = scope.launch {
                    runCatching {
                        releaseAnim.snapTo(rawAmount)
                        releaseAnim.animateDecay(
                            initialVelocity = v,
                            animationSpec = exponentialDecay()
                        ) {
                            rawAmount = value
                            applyDisplay()
                            if (sign(value) != previousSign) {
                                rawAmount = 0f
                                applyDisplay()
                                throw kotlinx.coroutines.CancellationException("bounce crossed zero")
                            }
                        }
                    }
                    if (!scope.isActive) return@launch
                    rawAmount = 0f
                    applyDisplay()
                }
                return available
            }

            // 甩不回 / 往外甩 / 零速度：异步软弹簧带速度回弹，速度消费
            // （子列表在边缘无路可滚，消费不影响体验）
            cancelRelease()
            releaseJob = scope.launch {
                runCatching {
                    releaseAnim.snapTo(rawAmount)
                    releaseAnim.animateTo(
                        0f,
                        spring(stiffness = 200f),
                        initialVelocity = v
                    ) {
                        rawAmount = value
                        applyDisplay()
                    }
                }
                if (!scope.isActive) return@launch
                rawAmount = 0f
                applyDisplay()
            }
            return Velocity.Zero
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
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier
            .graphicsLayer { translationY = state.offset }
            .onSizeChanged { state.onContainerSizeChanged(it.height) }
            .nestedScroll(state.connection)
    ) {
        LazyColumn(state = listState, content = content)
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
