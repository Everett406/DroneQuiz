package com.drone.quiz.ui.glass

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS 式上下过冲回弹（rubber-band，"豆腐般丝滑"）。
 *
 * 设计要点（v2.5.0 重写，修复此前实现的手感问题）：
 * - 经典 iOS rubber-band 位移曲线：位移 = d·(1 − 1/(x·c/d + 1))，c≈0.55。
 *   渐进阻力连续无拐点，起手近 1:1 跟手，越拉越"韧"，渐近线 d（约 110dp），永不过冲越界。
 * - 虚拟位移（无界）与显示位移分离：拖动/fling 都只累加虚拟量，显示量由曲线唯一决定，
 *   拖动中任何时刻反向都严格沿原曲线返回（此前线性阻力在往返时手感发涩）。
 * - 回弹触发点完备且不打架：
 *   · 拖动松手 → onPreFling（带初速度，弹簧先顺势后回）；
 *   · fling 撞边 → 滚动结束的 onPostFling（此前靠 90ms 定时器兜底，会在按住不动/慢拖时
 *     与手势抢状态造成抖动，这是"不够丝滑"的根源，已删除）。
 * - 回弹弹簧：临界阻尼（dampingRatio = 1f），无肉眼可见的过零震荡，软而不晃。
 */
class BounceState internal constructor(
    private val maxBouncePx: Float,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

    /** 显示位移（px），正 = 内容被向下拉（顶部过冲）。 */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 虚拟位移（无界）：显示位移 = rubber(virtual)。 */
    private var virtual = 0f

    private var releaseJob: Job? = null

    /** iOS UIScrollView 同款 rubber-band 映射。 */
    private fun rubber(x: Float): Float {
        if (x == 0f) return 0f
        val c = 0.55f
        val ax = abs(x)
        return sign(x) * maxBouncePx * (1f - 1f / (ax * c / maxBouncePx + 1f))
    }

    private fun applyFromVirtual() {
        offset = rubber(virtual)
    }

    private fun cancelRelease() {
        releaseJob?.cancel()
        releaseJob = null
    }

    /**
     * 回弹到 0。初速度取自松手/fling 的纵向速度（按比例阻尼引入），
     * 让"甩出去松手"先顺势再被拉回，接近 iOS 原生手感。
     */
    fun release(initialVelocityPxPerSec: Float = 0f) {
        if (virtual == 0f) return
        cancelRelease()
        releaseJob = scope.launch {
            val anim = Animatable(virtual)
            val finished = runCatching {
                anim.animateTo(
                    0f,
                    spring(
                        dampingRatio = 1f,          // 临界阻尼：软、无过零震荡
                        stiffness = 320f,
                        visibilityThreshold = 0.1f
                    ),
                    initialVelocity = initialVelocityPxPerSec * 0.35f
                ) {
                    // 每帧沿曲线回写：位移始终受 rubber-band 约束，视觉平滑
                    virtual = value
                    offset = rubber(value)
                }
            }.isSuccess
            if (finished) {
                virtual = 0f
                offset = 0f
            }
            releaseJob = null
        }
    }

    val connection = object : NestedScrollConnection {

        // 已过冲时，向回拖优先由过冲层消费（沿曲线退回），绝不反向叠加
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (virtual != 0f) {
                val towardZero = -virtual
                val consumed =
                    if (virtual > 0) dy.coerceIn(towardZero, 0f)
                    else dy.coerceIn(0f, towardZero)
                if (consumed != 0f) {
                    cancelRelease()
                    virtual += consumed
                    applyFromVirtual()
                }
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        // 子列表到边后剩余的拖动/惯性位移 → 全部转入 rubber-band
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = available.y
            if (dy != 0f) {
                cancelRelease()
                virtual += dy
                applyFromVirtual()
                return Offset(0f, dy)
            }
            return Offset.Zero
        }

        // 拖动松手：带初速度回弹
        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (virtual != 0f) {
                release(available.y)
                Velocity.Zero
            } else {
                available
            }
        }

        // fling 滚动结束仍压在边上（惯性撞边）：滚动结束时统一回弹
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (virtual != 0f) {
                release(available.y)
                return Velocity.Zero
            }
            return available
        }
    }
}

@Composable
fun rememberBounceState(): BounceState {
    val density = LocalDensity.current
    val maxPx = with(density) { 110.dp.toPx() }
    val scope = rememberCoroutineScope()
    return remember(maxPx) { BounceState(maxPx, scope) }
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
            .nestedScroll(state.connection),
        content = content
    )
}
