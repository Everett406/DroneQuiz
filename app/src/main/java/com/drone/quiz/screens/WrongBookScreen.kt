package com.drone.quiz.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.db.WrongWithQuestion
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.TagChip
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softTopFade
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.rememberBounceState
import com.drone.quiz.ui.glass.BounceLazyColumn
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WrongBookScreen(
    backdrop: Backdrop,
    onPractice: () -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings.collectAsState(initial = com.drone.quiz.data.settings.AppSettings())
    val wrongList by ServiceLocator.repo.activeWrong().collectAsState(initial = emptyList())
    val bounce = rememberBounceState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // ---- 筛选：题型 + 分类 ----
    var typeFilter by remember { mutableStateOf("all") }   // all | single | judge
    var catFilter by remember { mutableStateOf("all") }
    val catsInBook = wrongList.map { it.category }.distinct()
    val filtered = wrongList.filter { w ->
        (typeFilter == "all" || (typeFilter == "judge") == (w.type == "judge")) &&
            (catFilter == "all" || w.category == catFilter)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定标题 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 错题特训入口保留列表顶部"图案+字"按钮一个（此前右上角还有一个纯图标重复入口）
            Column(Modifier.weight(1f)) {
                ScreenTitle("错题本", "共 ${filtered.size} 题待消灭")
            }
            if (filtered.isNotEmpty()) {
                GlassButton(
                    onClick = onPractice,
                    backdrop = backdrop,
                    surfaceColor = ui.ink,
                    heightDp = 40.dp
                ) {
                    Icon(AppIcons.Play, null, tint = ui.onInk, modifier = Modifier.size(14.dp))
                    Text(
                        "开始特训",
                        color = ui.onInk, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (wrongList.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    backdrop = backdrop,
                    Modifier.padding(horizontal = 40.dp),
                    cornerRadius = 26.dp
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            AppIcons.BookWrong, null,
                            tint = ui.correct, modifier = Modifier.size(38.dp)
                        )
                        Text(
                            "错题本是空的",
                            color = ui.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            "答错的题会自动收进来，\n连续答对 ${settings.removeThreshold} 次自动移除",
                            color = ui.textSub,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        } else {
            // ---- 固定筛选行：题型 + 分类 chips ----
            // bottom 12dp：列表卡片滚动到顶时与胶囊之间保留固定空隙（此前贴边擦蹭）
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部", typeFilter == "all" && catFilter == "all") {
                    typeFilter = "all"; catFilter = "all"
                }
                FilterChip("单选", typeFilter == "single") {
                    typeFilter = if (typeFilter == "single") "all" else "single"
                }
                FilterChip("判断", typeFilter == "judge") {
                    typeFilter = if (typeFilter == "judge") "all" else "judge"
                }
                catsInBook.forEach { cat ->
                    FilterChip(cat, catFilter == cat) {
                        catFilter = if (catFilter == cat) "all" else cat
                    }
                }
            }

            // ---- 列表 + 右侧快速滚动把手 ----
            Box(Modifier.weight(1f)) {
                BounceLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .softTopFade(36.dp) { listState.scrolledFromTopPx() },
                    state = bounce,
                    listState = listState
                ) {
                    item {
                        Text(
                            "答对 ${settings.removeThreshold} 次自动移除 · 点卡片看解析",
                            color = ui.textSub, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                    items(filtered, key = { it.qid }) { item ->
                        WrongItem(
                            item = item,
                            backdrop = backdrop,
                            threshold = settings.removeThreshold,
                            onRemove = {
                                scope.launch { ServiceLocator.repo.removeWrongForever(item.qid) }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(130.dp)) }
                }
                if (filtered.size >= 8) {
                    FastScrollHandle(
                        listState = listState,
                        itemCount = filtered.size,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 3.dp)
                    )
                }
            
}
        }
    }
}

/** 筛选 chip（紧凑胶囊，微交互：按压缩放回弹 + 选中态颜色渐变过渡）。 */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.45f, stiffness = 700f
        ),
        label = "chipScale"
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) ui.ink else ui.ink.copy(alpha = 0.06f),
        animationSpec = androidx.compose.animation.core.spring(stiffness = 550f),
        label = "chipBg"
    )
    val borderC by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) ui.ink else ui.ink.copy(alpha = 0.12f),
        animationSpec = androidx.compose.animation.core.spring(stiffness = 550f),
        label = "chipBorder"
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) ui.onInk else ui.textSub,
        animationSpec = androidx.compose.animation.core.spring(stiffness = 550f),
        label = "chipFg"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .scale(scale)
            .background(bg)
            .border(1.dp, borderC, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 右侧快速滚动把手 v3（用户反馈"一跳一跳像齿轮、拖动不像列表在滚"）：
 * - 齿轮感根因：v2 跟随只读 `firstVisibleItemIndex`——每跨一题才更新一格（卡片高百余 dp）；
 *   拖动用 `scrollToItem(index)` 也只能整题整题地跳。
 * - v3 全链路改**像素级连续映射**：
 *   跟随 fraction = 已滚出像素 / 可滚总像素（首 item 的 index+offset 换算，逐像素平滑）；
 *   拖动 = 同一模型反向映射（目标像素 → scrollToItem(index, offset) 亚题精度），
 *   上下滑与把手全程同步、无级差；
 * - 绝对映射原则保留：拖动期间 fraction 只由手势驱动，列表回填不回写（无自反馈漂移）；
 * - 样式收敛：拇指 5dp×44dp 胶囊，静态 40% 墨色（比 v3 稍清晰好认），拖动加粗提亮 72%。
 */
@Composable
private fun FastScrollHandle(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeight by remember { mutableFloatStateOf(1f) }
    val thumbHeightPx = with(density) { 44.dp.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 列表像素几何：可见 item 平均高 → 全列表一维像素模型（头尾小 item 摊入平均，足够准）
    fun thumbFractionFromList(): Float {
        val info = listState.layoutInfo
        val vis = info.visibleItemsInfo
        if (vis.isEmpty() || info.totalItemsCount == 0) return 0f
        val avg = vis.map { it.size }.average().coerceAtLeast(1.0)
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toDouble()
        val maxScroll = (info.totalItemsCount * avg - viewport).coerceAtLeast(1.0)
        val first = vis.first()
        val scrolled = (first.index * avg - first.offset).coerceAtLeast(0.0)
        return (scrolled / maxScroll).toFloat().coerceIn(0f, 1f)
    }

    fun scrollToFraction(f: Float) {
        val info = listState.layoutInfo
        val vis = info.visibleItemsInfo
        if (vis.isEmpty() || info.totalItemsCount == 0) return
        val avg = vis.map { it.size }.average().coerceAtLeast(1.0)
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toDouble()
        val maxScroll = (info.totalItemsCount * avg - viewport).coerceAtLeast(1.0)
        val targetPx = f.toDouble() * maxScroll
        val idx = (targetPx / avg).toInt().coerceIn(0, info.totalItemsCount - 1)
        val off = (targetPx - idx * avg).roundToInt().coerceAtLeast(0)
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.scrollToItem(idx, off) }
    }

    // 非拖动态：拇指逐像素跟随列表（index+offset 连续读，替代 v2 的纯序号跳格）
    LaunchedEffect(dragging) {
        if (!dragging) {
            androidx.compose.runtime.snapshotFlow { thumbFractionFromList() }
                .collect { fraction = it }
        }
    }

    val trackRange = (trackHeight - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetY = fraction * trackRange

    Box(
        modifier
            .fillMaxHeight()
            .onSizeChanged { trackHeight = it.height.toFloat() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            Modifier
                .width(44.dp)
                .fillMaxHeight()
                .pointerInput(trackHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dy ->
                        change.consume()
                        fraction = (fraction + dy / trackRange).coerceIn(0f, 1f)
                        scrollToFraction(fraction)
                    }
                }
                .pointerInput(trackHeight, thumbHeightPx) {
                    detectTapGestures { offset ->
                        fraction = ((offset.y - thumbHeightPx / 2f) / trackRange)
                            .coerceIn(0f, 1f)
                        dragging = true
                        scope.launch {
                            scrollToFraction(fraction)
                            kotlinx.coroutines.delay(350)
                            dragging = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val thumbScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (dragging) 1.25f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 550f),
                label = "thumbScale"
            )
            val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (dragging) 0.72f else 0.40f,
                animationSpec = androidx.compose.animation.core.spring(stiffness = 550f),
                label = "thumbAlpha"
            )
            Box(
                Modifier
                    .offset(y = with(density) { (thumbOffsetY - trackRange / 2f).toDp() })
                    .size(width = 5.dp, height = with(density) { thumbHeightPx.toDp() })
                    .scale(thumbScale)
                    .clip(Capsule())
                    .background(ui.ink.copy(alpha = thumbAlpha))
            )
        }
    }
}

@Composable
private fun WrongItem(
    item: WrongWithQuestion,
    backdrop: Backdrop,
    threshold: Int,
    onRemove: () -> Unit
) {
    val ui = LocalUi.current
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth(),
        cornerRadius = 22.dp,
        onClick = { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类目标签降调：中性色即可，无需标红（红色留给错误语义）
                TagChip(item.category)
                Spacer(Modifier.width(6.dp))
                TagChip(if (item.type == "judge") "判断" else "单选")
                Spacer(Modifier.weight(1f))
                Text(
                    SimpleDateFormat("MM/dd", Locale.CHINA).format(Date(item.addedAt)),
                    color = ui.textSub, fontSize = 11.sp
                )
                Icon(
                    AppIcons.Trash,
                    null,
                    tint = ui.textSub,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(16.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onRemove
                        )
                )
            }
            Text(
                item.question,
                color = ui.text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp)
            )
            // 移除进度点
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    "连续答对 ${item.correctStreak}/$threshold",
                    color = ui.textSub, fontSize = 11.sp
                )
                Spacer(Modifier.width(8.dp))
                repeat(threshold) { i ->
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < item.correctStreak) ui.correct
                                else ui.ink.copy(alpha = 0.12f)
                            )
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("错 ${item.wrongCount} 次", color = ui.wrong, fontSize = 11.sp)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    if (item.type != "judge") {
                        val opts = runCatching {
                            kotlinx.serialization.json.Json.decodeFromString<List<String>>(item.options)
                        }.getOrDefault(emptyList())
                        opts.forEachIndexed { i, opt ->
                            val isAnswer = i == item.answer
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${('A' + i)}",
                                    color = if (isAnswer) ui.correct else ui.textSub,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    opt,
                                    color = if (isAnswer) ui.correct else ui.text,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            "正确答案：${if (item.answer == 0) "正确" else "错误"}",
                            color = ui.correct,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.explanation.isNotBlank()) {
                        Text(
                            item.explanation,
                            color = ui.textSub,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
