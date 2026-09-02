package com.drone.quiz.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.repo.Question
import com.drone.quiz.screens.common.TagChip
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassIconButton
import com.drone.quiz.ui.glass.GlassSlider
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    backdrop: Backdrop,
    src: String = "all"
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings.collectAsState()

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var catFilter by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    val answers = remember { mutableStateMapOf<Long, Int>() }
    var showPanel by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState { questions.size }

    LaunchedEffect(Unit) {
        runCatching {
            categories = ServiceLocator.repo.categories().map { it.category to it.cnt }
        }
    }

    LaunchedEffect(src, typeFilter, catFilter) {
        loading = true
        questions = if (src == "wrong") {
            ServiceLocator.repo.loadWrongPractice()
        } else {
            ServiceLocator.repo.loadPractice(
                category = catFilter,
                type = typeFilter,
                random = settings.practiceOrder == 1
            )
        }
        answers.clear()
        pagerState.scrollToPage(0)
        loading = false
    }

    fun onPick(q: Question, index: Int) {
        if (answers.containsKey(q.id)) return
        answers[q.id] = index
        val correct = index == q.answer
        scope.launch {
            ServiceLocator.repo.recordAnswer(
                qid = q.id,
                isCorrect = correct,
                mode = if (src == "wrong") "wrong" else "practice",
                removeThreshold = settings.removeThreshold
            )
        }
        if (correct && settings.autoNext && pagerState.currentPage < questions.size - 1) {
            scope.launch {
                delay(700)
                val target = pagerState.currentPage + 1
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 顶部大标题（最顶上） ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (src == "wrong") "错题特训" else "刷题",
                    color = ui.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (questions.isEmpty()) "加载中…" else "第 ${pagerState.currentPage + 1} / ${questions.size} 题" +
                        if (src == "wrong") "" else " · " + (catFilter ?: "全部分类"),
                    color = ui.textSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            GlassIconButton(
                onClick = { showPanel = true },
                backdrop = backdrop,
                icon = AppIcons.Grid
            )
        }

        // ---- 筛选 chips ----
        if (src != "wrong") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部", typeFilter == null && catFilter == null) {
                    typeFilter = null; catFilter = null
                }
                FilterChip("单选", typeFilter == "single") { typeFilter = "single" }
                FilterChip("判断", typeFilter == "judge") { typeFilter = "judge" }
                categories.take(6).forEach { (cat, _) ->
                    FilterChip(cat, catFilter == cat) {
                        catFilter = if (catFilter == cat) null else cat
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- 题目 Pager ----
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("正在加载题库…", color = ui.textSub, fontSize = 14.sp)
            }
        } else if (questions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Cards, null, tint = ui.textSub, modifier = Modifier.size(40.dp))
                    Text(
                        if (src == "wrong") "错题本是空的，继续保持！" else "没有符合条件的题目",
                        color = ui.textSub,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { questions[it].id }
            ) { page ->
                val q = questions[page]
                QuestionCard(
                    q = q,
                    picked = answers[q.id],
                    index = page + 1,
                    backdrop = backdrop,
                    onPick = { onPick(q, it) }
                )
            }

            // ---- 底部玻璃操作条 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    },
                    backdrop = backdrop,
                    icon = AppIcons.ChevronLeft,
                    sizeDp = 44.dp
                )
                GlassSlider(
                    value = { (pagerState.currentPage + 1).toFloat() },
                    onValueChange = { v ->
                        scope.launch {
                            pagerState.scrollToPage((v - 1f).roundToInt().coerceIn(0, questions.size - 1))
                        }
                    },
                    valueRange = 1f..questions.size.toFloat().coerceAtLeast(1f),
                    step = 1f,
                    backdrop = backdrop,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                GlassIconButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(questions.size - 1)
                            )
                        }
                    },
                    backdrop = backdrop,
                    icon = AppIcons.ChevronRight,
                    sizeDp = 44.dp
                )
            }
        }
    }

    // ---- 题号面板 ----
    if (showPanel) {
        ModalBottomSheet(
            onDismissRequest = { showPanel = false },
            containerColor = if (ui.isDark) Color(0xFF26221C) else Color(0xFFFAF6EF)
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "题目面板",
                        color = ui.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    PanelLegend(ui.correct, "答对")
                    PanelLegend(ui.wrong, "答错")
                    PanelLegend(null, "未答")
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(questions.size) { i ->
                        val q = questions[i]
                        val picked = answers[q.id]
                        val isCurrent = pagerState.currentPage == i
                        PanelCell(
                            number = i + 1,
                            state = when {
                                picked == null -> 0
                                picked == q.answer -> 1
                                else -> 2
                            },
                            isCurrent = isCurrent,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(i)
                                }
                                showPanel = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PanelLegend(color: Color?, label: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color ?: ui.ink.copy(alpha = 0.12f))
        )
        Text(label, color = ui.textSub, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
    }
}

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
 * 单张题目卡片。
 */
@Composable
private fun QuestionCard(
    q: Question,
    picked: Int?,
    index: Int,
    backdrop: Backdrop,
    onPick: (Int) -> Unit
) {
    val ui = LocalUi.current
    GlassCard(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        cornerRadius = 28.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(q.category)
                Spacer(Modifier.width(6.dp))
                TagChip(if (q.isJudge) "判断" else "单选")
                Spacer(Modifier.weight(1f))
                Text("第 $index 题", color = ui.textSub, fontSize = 11.sp)
            }
            Text(
                q.text,
                color = ui.text,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                q.optionsOrJudge.forEachIndexed { i, opt ->
                    OptionRow(
                        label = optionLabel(i, q.isJudge),
                        text = opt,
                        state = optionState(i, picked, q.answer),
                        enabled = picked == null,
                        onClick = { onPick(i) }
                    )
                }
            }

            AnimatedVisibility(
                visible = picked != null,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isCorrect = picked == q.answer
                Column(Modifier.padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isCorrect) AppIcons.Check else AppIcons.Close,
                            null,
                            tint = if (isCorrect) ui.correct else ui.wrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (isCorrect) "回答正确" else "回答错误",
                            color = if (isCorrect) ui.correct else ui.wrong,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "正确答案：${optionLabel(q.answer, q.isJudge)}",
                            color = ui.textSub,
                            fontSize = 12.sp
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ui.ink.copy(alpha = 0.05f))
                            .padding(14.dp)
                    ) {
                        Text(
                            "解析",
                            color = ui.textSub,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            q.explanation.ifBlank { "暂无解析" },
                            color = ui.text,
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun optionLabel(i: Int, isJudge: Boolean): String =
    if (isJudge) listOf("√", "×")[i.coerceIn(0, 1)] else ('A' + i).toString()

private fun optionState(i: Int, picked: Int?, answer: Int): Int =
    if (picked == null) 0
    else when {
        i == answer -> 1        // 正确项
        i == picked -> 2        // 误选项
        else -> 0
    }

@Composable
private fun OptionRow(
    label: String,
    text: String,
    state: Int, // 0 默认 1 正确 2 误选
    enabled: Boolean,
    onClick: () -> Unit
) {
    val ui = LocalUi.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == 2) {
            listOf(-14f, 12f, -9f, 6f, -3f, 0f).forEach { v ->
                shake.animateTo(v, spring(dampingRatio = 0.6f, stiffness = 900f))
            }
        }
    }
    val bg = when (state) {
        1 -> ui.correct.copy(alpha = 0.14f)
        2 -> ui.wrong.copy(alpha = 0.14f)
        else -> ui.ink.copy(alpha = 0.05f)
    }
    val borderColor = when (state) {
        1 -> ui.correct.copy(alpha = 0.6f)
        2 -> ui.wrong.copy(alpha = 0.6f)
        else -> ui.ink.copy(alpha = 0.08f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        1 -> ui.correct
                        2 -> ui.wrong
                        else -> ui.ink.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state > 0) {
                Icon(
                    if (state == 1) AppIcons.Check else AppIcons.Close,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(label, color = ui.textSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text,
            color = ui.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        if (state == 1) {
            Text("正确", color = ui.correct, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else if (state == 2) {
            Text("你选的", color = ui.wrong, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 题号面板格子。
 */
@Composable
private fun PanelCell(number: Int, state: Int, isCurrent: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                when (state) {
                    1 -> ui.correct.copy(alpha = 0.2f)
                    2 -> ui.wrong.copy(alpha = 0.2f)
                    else -> ui.ink.copy(alpha = 0.06f)
                }
            )
            .then(
                if (isCurrent) Modifier.border(2.dp, ui.ink, CircleShape)
                else Modifier.border(1.dp, ui.ink.copy(alpha = 0.08f), CircleShape)
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = when (state) {
                1 -> ui.correct
                2 -> ui.wrong
                else -> ui.text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
