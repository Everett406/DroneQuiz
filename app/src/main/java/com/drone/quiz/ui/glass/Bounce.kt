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
 * iOS 式上下过冲回弹（rubber-band）v5。
 *
 * 惯性丢失真根因（v2.6.0 后 GitHub 调研定位，sinasamaki 原文铁证）：
 * NestedScrollConnection.onPreFling 的返回值 = 本层**消费**的速度，
 * 未消费的部分才会交还子列表继续 fling。sinasamaki 未过冲时
 * `return super.onPreFling(available)` = Velocity.Zero = 消费 0 = 速度全部交还。
 * 而 v3/v4 把"要交还的速度"当返回值写成了 `return available` —— 语义完全颠倒，
 * 等于把普通 fling 的全部速度一口吞掉：子列表收到 0 速度，
 * 松手即停、毫无惯性（用户连续三轮反馈的"一松手它就停了"）。
 *
 * v5 修正（返回值语义全部翻转）：
 * - 未过冲 → 返回 Velocity.Zero（消费 0，普通 fling 零干扰，惯性完整保留）；
 * - 过冲 + 向内容侧强甩（会过零）→ 返回 Velocity.Zero（速度全部交还，
 *   列表立即带惯性滚动），过冲量异步摩擦衰减归零、与滚动并行；
 * - 过冲 + 甩不回/往外甩 → 返回 Velocity(0f, v)（消费全部垂直速度，
 *   子列表在边缘无路可滚），异步软弹簧带速度回弹；
 * - onPostFling 兑底：fling 途中撞边产生的过冲量在 fling 自然结束后
 *   带残余速度弹簧回弹（onPreFling 已启动的动画不打断）。
 *
 * 过冲量 rawAmount 保持 mutableFloatStateOf 同步直写（v4 的零竞态设计保留）。
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
            // 返回值 = 消费的速度（v5 语义修正）。
            // 未过冲：消费 0，全部速度交还列表——普通 fling 惯性完整保留（本轮修复核心）。
            // 本函数零挂起点、立即返回，绝不阻塞嵌套滚动链。
            if (rawAmount == 0f) return Velocity.Zero

            val previousSign = sign(rawAmount)
            val amountAbs = abs(rawAmount)
            val v = available.y

            if (v != 0f && sign(v) != previousSign && abs(v) * 0.5f > amountAbs) {
                // 强甩向内容侧（会过零）：消费 0，全部速度交还列表（惯性立即生效），
                // 过冲量异步摩擦衰减归零，与滚动并行（iOS 式衔接）
                cancelRelease()
                val start = rawAmount
                releaseJob = scope.launch {
                    runCatching {
                        releaseAnim.snapTo(start)
                        releaseAnim.animateDecay(
                            initialVelocity = v,
                            animationSpec = exponentialDecay()
                        ) {
                            if (sign(value) != previousSign) {
                                rawAmount = 0f
                                applyDisplay()
                                throw kotlinx.coroutines.CancellationException("bounce crossed zero")
                            }
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

            // 甩不回 / 往外甩 / 零速度：消费全部垂直速度（列表在边缘无路可滚），
            // 异步软弹簧带速度回弹
            cancelRelease()
            val start = rawAmount
            releaseJob = scope.launch {
                runCatching {
                    releaseAnim.snapTo(start)
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
            return Velocity(0f, v)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // fling 自然结束兜底：fling 途中撞边产生的过冲量在此带残余速度回弹。
            // onPreFling 已启动的回弹/衰减动画（releaseJob 在跑）不打断，避免双重动画。
            if (rawAmount != 0f && releaseJob?.isActive != true) {
                cancelRelease()
                val start = rawAmount
                val residual = available.y
                releaseJob = scope.launch {
                    runCatching {
                        releaseAnim.snapTo(start)
                        releaseAnim.animateTo(
                            0f,
                            spring(stiffness = 200f),
                            initialVelocity = residual
                        ) {
                            rawAmount = value
                            applyDisplay()
                        }
                    }
                    if (!scope.isActive) return@launch
                    rawAmount = 0f
                    applyDisplay()
                }
            }
            return super.onPostFling(consumed, available)
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
