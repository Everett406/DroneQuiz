package com.drone.quiz.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.LocalBgBackdrop
import com.drone.quiz.ui.glass.LocalContentBackdrop
import com.drone.quiz.ui.glass.glass
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.delay

/**
 * 首启功能引导（v2.9.0，用户口径：页面高亮气泡式，带着一步一步认识题屿）。
 *
 * 结构：
 * - [Tour]：8 步导览定义（欢迎卡 + 各功能页锚点步骤）；
 * - [OnboardingBus]：全局开关（首启自动触发与设置页「使用引导」重看入口共用）；
 * - [OnboardingAnchors]：锚点注册表（屏幕坐标 + bringIntoView 请求器）；
 * - [onboardingAnchor]：埋点 Modifier，各屏把引导要强调的元素挂上即可；
 * - [TourHost]：覆盖层（AppRoot 第 6 层）——挖孔遮罩 + 玻璃气泡，编排切页/滚动/淡入。
 *
 * 设计约定：
 * - 引导期间遮罩吃掉全部触控（App 本体只读展示），切页由引导自己驱动，状态不会脱钩；
 * - 锚点不在屏内时先 bringIntoView 滚进屏幕再放气泡；锚点始终缺席（如空错题本没有
 *   「开始特训」按钮）则退化为整屏变暗 + 气泡居中，绝不空转卡死；
 * - 「跳过即不再弹」：跳过/翻完都由宿主 onFinish 落 onboarding_done 标记；
 *   设置页重看（replay 模式）只影响本次会话。
 */

// ==================== 步骤定义 ====================

data class TourStep(
    val anchor: String?,   // 高亮锚点 key；null = 欢迎卡（无锚点，居中展示）
    val tab: Int,          // 目标底栏 tab（-1 = 不切页，欢迎卡用）
    val title: String,
    val body: String,      // 多段用 \n 分隔
    val cta: String = "下一步"
)

object Tour {
    val steps = listOf(
        TourStep(
            null, -1,
            "欢迎来到题屿",
            "这里是一座把无人机装调题库搬进手机的小岛：800 道带解析的题，顺序随机随你刷，" +
                "模考自动阅卷，还有帮你「记仇」的错题本。\n接下来一分钟，带你把小岛逛一圈。" +
                "想直接开刷？随时点「跳过引导」就行。",
            "开始逛一逛"
        ),
        TourStep(
            "home_overview", 0,
            "首页 · 你的学习日志",
            "进度环是整座题库的刷题覆盖率，预估通过率和近 7 天的曲线也都在这儿——" +
                "刷得越多，小岛越热闹。右上角可以进设置。"
        ),
        TourStep(
            "practice_search", 1,
            "顺便 · 想找哪题搜哪题",
            "这个搜索框能全文检索题干、选项和解析，最近搜过的 8 条也会记住。" +
                "翻书找题不如搜一下，最快。"
        ),
        TourStep(
            "practice_start", 1,
            "刷题 · 想怎么刷都行",
            "在上面选好题型、顺序或随机，点「开始刷题」就进题。答错马上看解析；" +
                "连续刷 20 分钟，我会提醒你抬头看看远处。"
        ),
        TourStep(
            "exam_start", 2,
            "模考 · 全真演练",
            "题数、时长、及格线都自己定，倒计时一结束自动交卷、当场出成绩。" +
                "历史成绩随时回看，交卷后答错的题也一并进错题本。"
        ),
        TourStep(
            "wrong_title", 3,
            "错题本 · 专治不会",
            "答错的题自动收进来，连续答对几次就自动移出；点「开始特训」只刷错题，" +
                "不占刷题进度。现在还是空的？很好，说明它还没开始记仇。"
        ),
        TourStep(
            "settings_banks", 4,
            "自带题库不够用？",
            "这里能导入自己的题库：纯文字用 CSV，带图的打包成 ZIP。材料是 Excel / Word / PDF " +
                "的话，把「提示词」复制给 Agent，让它替你整理成能导入的格式。"
        ),
        TourStep(
            "settings_look", 4,
            "最后，把它变成你的题屿",
            "四款阅读字体、任意壁纸、深色模式随心换。以后想找我，设置底部的「关于」里有" +
                "检查更新，也有请喝奶茶——好了，去刷题吧！",
            "开始刷题"
        )
    )
}

// ==================== 全局总线 ====================

/** 引导开关（首启自动触发与设置页「使用引导」重看入口共用）。 */
object OnboardingBus {
    var active by mutableStateOf(false)
        private set
    var stepIndex by mutableIntStateOf(0)
        private set
    var replayMode by mutableStateOf(false)
        private set

    fun start(replay: Boolean) {
        replayMode = replay
        stepIndex = 0
        active = true
    }

    fun next() {
        if (stepIndex < Tour.steps.lastIndex) stepIndex++ else active = false
    }

    fun skip() {
        active = false
    }
}

/** 锚点注册表：各屏 [onboardingAnchor] 上报坐标，TourHost 读取定位挖孔与气泡。 */
object OnboardingAnchors {
    val rects = mutableStateMapOf<String, Rect>()

    /**
     * 每个 key 一个共享 requester（requester 只是指向当前附着节点的句柄，
     * 离开组合再回来重新附着即可复用，无需 remember）。
     */
    internal val requesters = mutableMapOf<String, BringIntoViewRequester>()

    internal fun requesterFor(key: String): BringIntoViewRequester =
        requesters.getOrPut(key) { BringIntoViewRequester() }
}

/**
 * 引导锚点埋点：上报 boundsInRoot 供挖孔定位；引导切到该步时把元素滚进屏幕。
 * 纯修饰符工厂（ui 1.9 起 Modifier.composed 已隐藏，不再用 remember+DisposableEffect）。
 */
fun Modifier.onboardingAnchor(key: String): Modifier =
    this
        .bringIntoViewRequester(OnboardingAnchors.requesterFor(key))
        .onGloballyPositioned { OnboardingAnchors.rects[key] = it.boundsInRoot() }

// ==================== 覆盖层宿主 ====================

/**
 * 引导覆盖层（AppRoot 第 6 层：PortalHost / 打赏弹窗之上）。
 *
 * 编排：步骤切换 →（需要时）切底栏 tab 等转场 → bringIntoView 滚锚点 → 气泡淡入。
 * 气泡坐标实时读锚点注册表，滚动中跟随不跳变。
 */
@Composable
fun TourHost(
    backdrop: Backdrop,
    currentTab: Int,
    onNavigateTab: (Int) -> Unit,
    onFinish: () -> Unit
) {
    if (!OnboardingBus.active) return
    val ui = LocalUi.current
    val density = LocalDensity.current
    val step = Tour.steps[OnboardingBus.stepIndex]
    val isLast = OnboardingBus.stepIndex == Tour.steps.lastIndex

    fun endTour() {
        OnboardingBus.skip()
        onFinish()
    }

    // 系统返回 = 跳过（与跳过按钮同语义）
    BackHandler(onBack = { endTour() })

    // 步骤编排
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(OnboardingBus.stepIndex) {
        ready = false
        if (step.tab >= 0 && step.tab != currentTab) {
            onNavigateTab(step.tab)
            delay(430)          // 页面转场 280ms + 余量
        } else {
            delay(190)
        }
        step.anchor?.let { key ->
            OnboardingAnchors.requesters[key]?.let { r ->
                runCatching { r.bringIntoView() }
            }
            delay(280)          // 滚动动画 + 布局稳定
        }
        ready = true
    }

    // 覆盖层尺寸（= 组合根尺寸），气泡定位/夹边全用它，与 boundsInRoot 同坐标系
    var layerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { layerSize = it }
    ) {
        val anchorRect = step.anchor?.let { OnboardingAnchors.rects[it] }

        // ---- 挖孔遮罩：整屏变暗，锚点处挖圆角孔 + 呼吸描边 ----
        val breath by rememberInfiniteTransition(label = "tourBreath").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "tourBreath"
        )
        Canvas(Modifier.fillMaxSize()) {
            val scrim = Color.Black.copy(alpha = 0.45f)
            val hole = anchorRect?.let { r ->
                val grow = 10.dp.toPx()
                val l = (r.left - grow).coerceAtLeast(0f)
                val t = (r.top - grow).coerceAtLeast(0f)
                val rr = (r.right + grow).coerceAtMost(size.width)
                val b = (r.bottom + grow).coerceAtMost(size.height)
                if (rr - l > 1f && b - t > 1f) Rect(l, t, rr, b) else null
            }
            if (hole == null) {
                drawRect(scrim)
            } else {
                val corner = CornerRadius(22.dp.toPx())
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(hole, corner))
                }
                drawPath(path, scrim)
                // 柔光外圈 + 呼吸描边（微动效提示"看这里"）
                drawRoundRect(
                    color = ui.accent.copy(alpha = 0.30f * breath),
                    topLeft = Offset(hole.left - 4.dp.toPx(), hole.top - 4.dp.toPx()),
                    size = Size(hole.width + 8.dp.toPx(), hole.height + 8.dp.toPx()),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(width = 5.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f * breath),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // ---- 气泡 / 欢迎卡 ----
        var bubbleHpx by remember { mutableStateOf(0) }
        val marginPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 14.dp.toPx() }
        val minPadPx = with(density) { 12.dp.toPx() }
        val bubbleWpx = layerSize.width - marginPx * 2

        // 纵向位置：锚点下方优先，空间不足放上方；欢迎卡垂直居中；锚点缺席兜底中下部
        val bubbleOffsetY: Float = when {
            step.anchor == null ->
                layerSize.height * 0.5f - bubbleHpx * 0.5f
            anchorRect != null -> {
                val below = anchorRect.bottom + gapPx
                val above = anchorRect.top - gapPx - bubbleHpx
                val y = if (below + bubbleHpx <= layerSize.height - minPadPx) below else above
                y.coerceIn(minPadPx, (layerSize.height - bubbleHpx - minPadPx).coerceAtLeast(minPadPx))
            }
            else ->
                layerSize.height * 0.60f - bubbleHpx * 0.5f
        }

        // 玻璃折射源：与弹窗面板同款（背景层 + 内容层合成）
        val bgLayer = LocalBgBackdrop.current
        val contentLayer = LocalContentBackdrop.current
        val panelBackdrop = if (bgLayer != null && contentLayer != null) {
            rememberCombinedBackdrop(bgLayer, contentLayer)
        } else {
            backdrop
        }

        AnimatedVisibility(
            visible = ready,
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 6 },
            exit = fadeOut(tween(150)),
            modifier = Modifier.offset(
                x = with(density) { marginPx.toInt().toDp() },
                y = with(density) { bubbleOffsetY.toInt().toDp() }
            )
        ) {
            Column(
                Modifier
                    .width(with(density) { bubbleWpx.toInt().toDp() })
                    .glass(
                        backdrop = panelBackdrop,
                        shape = RoundedCornerShape(24.dp),
                        blurDp = 18.dp,
                        lensHeightDp = 14.dp,
                        lensAmountDp = 18.dp,
                        surfaceColor = ui.surface.copy(alpha = 0.78f)
                    )
                    .onSizeChanged { bubbleHpx = it.height }
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // 步骤圆点（当前步加宽成胶囊）
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tour.steps.forEachIndexed { i, _ ->
                        val cur = i == OnboardingBus.stepIndex
                        Box(
                            Modifier
                                .size(width = if (cur) 16.dp else 5.dp, height = 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (cur) ui.accent else ui.textSub.copy(alpha = 0.35f))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    step.title,
                    color = ui.text, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    step.body,
                    color = ui.text.copy(alpha = 0.86f),
                    fontSize = 14.sp, lineHeight = 23.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 跳过：文字按钮（垂直 8dp padding 保证 ~30dp 触达高度）
                    Text(
                        "跳过引导",
                        color = ui.textSub, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) { endTour() }
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    GlassButton(
                        onClick = { if (isLast) endTour() else OnboardingBus.next() },
                        backdrop = backdrop,
                        surfaceColor = ui.ink,
                        heightDp = 44.dp
                    ) {
                        Text(
                            if (isLast) step.cta else "下一步",
                            color = ui.onInk, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
