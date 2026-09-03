package com.drone.quiz.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import com.drone.quiz.screens.common.TopFog
import com.drone.quiz.screens.common.scrolledFromTopPx
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 2.dp),
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
                    modifier = Modifier.fillMaxSize(),
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
            
                // 标题雾化条：滚入标题下方的内容被背景色雾遮没（零离屏零蒙版，无伪影）
                TopFog { listState.scrolledFromTopPx() }
}
        }
    }
}

/** 筛选 chip（紧凑胶囊）。 */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ui.ink else ui.ink.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) ui.ink else ui.ink.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (selected) ui.onInk else ui.textSub,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 右侧快速滚动把手：拖动按比例映射到列表位置（错题量多时直达）。
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
    val thumbHeight = with(density) { 52.dp.toPx() }
    val lastIndex = (itemCount - 1).coerceAtLeast(1)

    Box(
        modifier
            .fillMaxHeight()
            .onSizeChanged { trackHeight = it.height.toFloat() },
        contentAlignment = Alignment.CenterEnd
    ) {
        val trackRange = (trackHeight - thumbHeight).coerceAtLeast(1f)
        val fraction = (listState.firstVisibleItemIndex.toFloat() / lastIndex).coerceIn(0f, 1f)
        val thumbOffsetY = fraction * trackRange
        Box(
            Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .offset(y = with(density) { (thumbOffsetY - trackRange / 2f).toDp() })
                    .size(width = 4.5.dp, height = with(density) { thumbHeight.toDp() })
                    .clip(Capsule())
                    .background(ui.ink.copy(alpha = 0.32f))
                    .pointerInput(itemCount, trackRange) {
                        detectVerticalDragGestures { change, dy ->
                            change.consume()
                            val base = listState.firstVisibleItemIndex.toFloat() / lastIndex
                            val next = (base + dy / trackRange).coerceIn(0f, 1f)
                            scope.launch {
                                listState.scrollToItem((next * lastIndex).roundToInt())
                            }
                        }
                    }
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
