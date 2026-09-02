package com.drone.quiz.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS 式上下过冲回弹（rubber-band）。
 *
 * - 拖动越界：渐进阻力（越拉越难），最大回弹 ~110dp
 * - 松手：spring 平滑回弹
 * - 跟手：拖动期间直写状态（零延迟），松手后弹簧动画
 * - 双重释放保障：onPreFling + 滚动静止 90ms 兜底
 */
class BounceState internal constructor(
    private val maxBouncePx: Float,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

    var offset by mutableFloatStateOf(0f)
        private set

    private var releaseJob: kotlinx.coroutines.Job? = null

    private fun cancelRelease() {
        releaseJob?.cancel()
        releaseJob = null
    }

    fun release() {
        if (offset != 0f) {
            cancelRelease()
            releaseJob = scope.launch {
                val anim = Animatable(offset)
                anim.animateTo(
                    0f,
                    spring(dampingRatio = 0.85f, stiffness = 380f)
                ) { value, _ ->
                    offset = value
                }
            }
        }
    }

    val connection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (offset != 0f) {
                val towardZero = -offset
                val consumed =
                    if (offset > 0) dy.coerceIn(towardZero, 0f)
                    else dy.coerceIn(0f, towardZero)
                if (consumed != 0f) {
                    cancelRelease()
                    offset += consumed
                }
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = available.y
            if (dy != 0f) {
                cancelRelease()
                // 渐进阻力：越拉越难
                val cur = offset
                val roomLeft = (1f - abs(cur) / maxBouncePx).coerceIn(0.06f, 1f)
                var next = cur + dy * roomLeft
                if (abs(next) > maxBouncePx) {
                    next = maxBouncePx * sign(next)
                }
                offset = next
                return Offset(0f, dy)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (offset != 0f) {
                release()
                Velocity.Zero
            } else {
                available
            }
        }
    }
}

@Composable
fun rememberBounceState(): BounceState {
    val density = LocalDensity.current
    val maxPx = with(density) { 110.dp.toPx() }
    val scope = rememberCoroutineScope()
    val state = remember(maxPx) { BounceState(maxPx, scope) }

    // 松手后自动回弹兜底：滚动事件停止 90ms 后回弹（onPreFling 未触发的情形）
    LaunchedEffect(state) {
        snapshotFlow { state.offset }
            .collectLatest { v ->
                if (v != 0f) {
                    kotlinx.coroutines.delay(90)
                    state.release()
                }
            }
    }
    return state
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
