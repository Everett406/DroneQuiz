package com.drone.quiz.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.drone.quiz.data.repo.Repo
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.SectionLabel
import com.drone.quiz.screens.common.SegmentedRow
import com.drone.quiz.screens.common.remainingBottomPx
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softTopFade
import com.drone.quiz.screens.common.softVerticalEdges
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassIconButton
import com.drone.quiz.ui.glass.GlassSlider
import com.drone.quiz.ui.glass.GlassBottomSheet
import com.drone.quiz.ui.glass.GlassConfirmDialog
import com.drone.quiz.ui.glass.rememberBounceState
import com.drone.quiz.ui.glass.BounceContainer
import com.drone.quiz.ui.theme.LocalUi
import com.drone.quiz.ui.nav.Routes
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 模考会话内存持有者（本地单用户，接受进程被杀后模考丢失）。 */
object ExamSessionHolder {
    var examId: Long = 0
    var questions: List<Question> = emptyList()
    var durationSec: Int = 0
    var outcome: Repo.ExamOutcome? = null
}

// ==================== 配置页 ====================

@Composable
fun ExamConfigScreen(
    backdrop: Backdrop,
    onStart: (Long) -> Unit,
    onResumeExam: (Long) -> Unit = {}
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings
        .collectAsState(initial = com.drone.quiz.data.settings.AppSettings())

    var count by remember { mutableIntStateOf(50) }
    var judgeRatio by remember { mutableStateOf(0.3f) }
    var durationMin by remember { mutableIntStateOf(60) }
    var starting by remember { mutableStateOf(false) }

    val recentExams by ServiceLocator.repo.recentExams().collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定标题 ----
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenTitle("模考", "模拟真实考试 · 倒计时自动交卷", Modifier.padding(vertical = 16.dp))
        }

        // 标题柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影，滑出渐显跟手）
        val scrollState = rememberScrollState()
        BounceContainer(
            Modifier
                .weight(1f)
                .softTopFade(30.dp) { scrollState.scrolledFromTopPx() }
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {

            // ---- 考试设置：2×2 紧凑网格 ----
            // 此前四张大卡各占一大截，题目数量/判断占比/时长/及格分挤满整屏，
            // 孰轻孰重失衡；压缩为两行小卡后，开始考试与最近模考首屏可达。
            SectionLabel("考试设置")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExamSettingCell(
                    backdrop = backdrop,
                    label = "题目数量",
                    value = "$count 题",
                    modifier = Modifier.weight(1f)
                ) {
                    GlassSlider(
                        value = { count.toFloat() },
                        onValueChange = { count = it.roundToInt() },
                        valueRange = 10f..100f,
                        step = 5f,
                        backdrop = backdrop
                    )
                }
                ExamSettingCell(
                    backdrop = backdrop,
                    label = "考试时长",
                    value = "$durationMin 分钟",
                    modifier = Modifier.weight(1f)
                ) {
                    GlassSlider(
                        value = { durationMin.toFloat() },
                        onValueChange = { durationMin = it.roundToInt() },
                        valueRange = 15f..120f,
                        step = 5f,
                        backdrop = backdrop
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExamSettingCell(
                    backdrop = backdrop,
                    label = "判断题占比",
                    value = "${(judgeRatio * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                ) {
                    GlassSlider(
                        value = { judgeRatio },
                        onValueChange = { judgeRatio = it },
                        valueRange = 0f..0.6f,
                        step = 0.1f,
                        backdrop = backdrop
                    )
                }
                ExamSettingCell(
                    backdrop = backdrop,
                    label = "及格分",
                    value = "${settings.passScore} 分",
                    modifier = Modifier.weight(1f)
                ) {
                    GlassSlider(
                        value = { settings.passScore.toFloat() },
                        onValueChange = { v ->
                            scope.launch { ServiceLocator.settings.setPassScore(v.roundToInt()) }
                        },
                        valueRange = 50f..95f,
                        step = 5f,
                        backdrop = backdrop
                    )
                }
            }

            GlassButton(
                onClick = {
                    if (!starting) {
                        starting = true
                        scope.launch {
                            val (id, qs) = ServiceLocator.repo.startExam(
                                total = count,
                                judgeRatio = judgeRatio,
                                durationSec = durationMin * 60,
                                random = true
                            )
                            ExamSessionHolder.examId = id
                            ExamSessionHolder.questions = qs
                            ExamSessionHolder.durationSec = durationMin * 60
                            ExamSessionHolder.outcome = null
                            onStart(id)
                        }
                    }
                },
                backdrop = backdrop,
                surfaceColor = ui.ink,
                heightDp = 54.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
            ) {
                Icon(AppIcons.Play, null, tint = ui.onInk, modifier = Modifier.size(18.dp))
                Text(
                    if (starting) "正在组卷…" else "开始考试",
                    color = ui.onInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentExams.isNotEmpty()) {
                SectionLabel("最近模考")
                recentExams.take(5).forEach { exam ->
                    val unfinished = exam.score == null
                    // 会话仍在内存（刚放弃/返回）时可一键继续考试
                    val resumable = unfinished &&
                        ExamSessionHolder.examId == exam.id &&
                        ExamSessionHolder.questions.isNotEmpty()
                    GlassCard(
                        backdrop = backdrop,
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        cornerRadius = 18.dp,
                        onClick = if (resumable) {
                            { onResumeExam(exam.id) }
                        } else null
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
                                        .format(Date(exam.startedAt)),
                                    color = ui.text, fontSize = 13.sp, fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${exam.total} 题 · ${exam.singleCount} 单选 ${exam.judgeCount} 判断",
                                    color = ui.textSub, fontSize = 11.sp
                                )
                            }
                            if (unfinished) {
                                Text(
                                    if (resumable) "可继续" else "未完成",
                                    color = ui.textSub, fontSize = 12.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                // 删除未完成的幽灵记录（点击不可进/无法清理的问题修复）
                                GlassIconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { ServiceLocator.repo.abandonExam(exam.id) }
                                        }
                                    },
                                    backdrop = backdrop,
                                    icon = AppIcons.Trash,
                                    sizeDp = 36.dp,
                                    iconSize = 16.dp,
                                    iconTint = ui.wrong
                                )
                            } else {
                                exam.score?.let { s ->
                                    Text(
                                        "${s.toInt()}",
                                        color = if (exam.passed == true) ui.correct else ui.wrong,
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (exam.passed == true) " 通过" else " 未通过",
                                        color = ui.textSub, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(130.dp))
        }
        
}
    }
}

/**
 * 模考设置小卡（2×2 网格单元）：标签 + 当前值 + 迷你滑杆，替代旧全宽大卡。
 */
@Composable
private fun ExamSettingCell(
    backdrop: Backdrop,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    slider: @Composable () -> Unit
) {
    val ui = LocalUi.current
    GlassCard(backdrop = backdrop, modifier = modifier, cornerRadius = 20.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = ui.textSub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                value,
                color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Box(Modifier.padding(top = 6.dp)) { slider() }
        }
    }
}

// ==================== 考试页 ====================

@Composable
fun ExamScreen(
    backdrop: Backdrop,
    examId: Long,
    onSubmit: (Long) -> Unit,
    onExit: () -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()

    // 会话优先用内存（SessionHolder）；进程重启/从"可继续"进入时从 DB 恢复
    var questions by remember {
        mutableStateOf(
            ExamSessionHolder.questions
                .takeIf { ExamSessionHolder.examId == examId && it.isNotEmpty() }
                ?: emptyList()
        )
    }
    val total = questions.size

    // -1 表示尚未初始化（等待恢复/内存会话就绪）
    var remaining by remember { mutableIntStateOf(-1) }
    var showConfirm by remember { mutableStateOf(false) }
    var showQuit by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Long, Int>() }
    val pagerState = rememberPagerState { total }

    LaunchedEffect(examId) {
        if (questions.isNotEmpty()) {
            remaining = ExamSessionHolder.durationSec
        } else {
            // 从 DB 重建：题目 + 已答 + 剩余时间（durationSec - 已耗时）
            val resumed = runCatching { ServiceLocator.repo.resumeExam(examId) }.getOrNull()
            if (resumed != null) {
                val (exam, qs, picked) = resumed
                ExamSessionHolder.examId = examId
                ExamSessionHolder.questions = qs
                ExamSessionHolder.durationSec = exam.durationSec
                ExamSessionHolder.outcome = null
                questions = qs
                picked.forEach { (k, v) -> answers[k] = v }
                val elapsed = ((System.currentTimeMillis() - exam.startedAt) / 1000).toInt()
                remaining = (exam.durationSec - elapsed).coerceAtLeast(1)
            } else {
                remaining = 0 // 无法恢复：提示用户返回（onExit 会清理残留记录）
            }
        }
    }

    val submit: () -> Unit = {
        if (!submitting && questions.isNotEmpty()) {
            submitting = true
            scope.launch {
                val s = ServiceLocator.settings.settings.first()
                val outcome = ServiceLocator.repo.submitExam(
                    examId = examId,
                    questions = questions,
                    picked = answers.toMap(),
                    durationSec = ExamSessionHolder.durationSec - remaining,
                    passScore = s.passScore,
                    removeThreshold = s.removeThreshold
                )
                ExamSessionHolder.outcome = outcome
                onSubmit(examId)
            }
        }
    }

    // 倒计时（到 0 自动交卷）；等剩余时间初始化后才开始
    if (remaining >= 0) {
        LaunchedEffect(examId) {
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining--
            }
            submit()
        }
    }

    BackHandler { showQuit = true }

    if (total == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("模考数据丢失，请返回重试", color = ui.textSub)
                GlassButton(
                    onClick = onExit,
                    backdrop = backdrop,
                    heightDp = 44.dp,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text("返回", color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部计时条
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                TimerText(remaining)
                Text(
                    "第 ${pagerState.currentPage + 1} / $total 题",
                    color = ui.textSub, fontSize = 12.sp
                )
            }
            GlassIconButton(
                onClick = { showPanel = true },
                backdrop = backdrop,
                icon = AppIcons.Grid
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(
                onClick = { showQuit = true },
                backdrop = backdrop,
                icon = AppIcons.Close,
                sizeDp = 42.dp
            )
            Spacer(Modifier.width(8.dp))
            GlassButton(
                onClick = { showConfirm = true },
                backdrop = backdrop,
                surfaceColor = ui.ink,
                heightDp = 44.dp
            ) {
                Text("交卷", color = ui.onInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // 题目卡顶部对齐（此前 Pager 默认垂直居中，短题悬在屏幕中间不易阅读）
            verticalAlignment = Alignment.Top,
            key = { questions[it].id }
        ) { page ->
            val q = questions[page]
            ExamQuestionCard(
                q = q,
                index = page + 1,
                picked = answers[q.id],
                backdrop = backdrop,
                onPick = { i ->
                    answers[q.id] = i
                    scope.launch {
                        ServiceLocator.repo.saveExamAnswer(
                            examId, q.id, i, i == q.answer
                        )
                    }
                }
            )
        }

        // 底部操作条（避开手势指示条；滑块与左右按钮拉开间距防误触）
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
                        pagerState.scrollToPage((v - 1f).roundToInt().coerceIn(0, total - 1))
                    }
                },
                valueRange = 1f..total.toFloat().coerceAtLeast(1f),
                step = 1f,
                backdrop = backdrop,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            )
            GlassIconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(total - 1))
                    }
                },
                backdrop = backdrop,
                icon = AppIcons.ChevronRight,
                sizeDp = 44.dp
            )
        }
    }

    if (showConfirm) {
        val unanswered = questions.count { answers[it.id] == null }
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "确认交卷？",
            body = if (unanswered > 0) "还有 $unanswered 题未作答，交卷后将无法修改。"
            else "共 ${questions.size} 题，交卷后将立即评分。",
            confirmText = "交卷",
            dismissText = "继续作答",
            onConfirm = {
                showConfirm = false
                submit()
            },
            onDismiss = { showConfirm = false }
        )
    }

    if (showQuit) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "放弃考试？",
            body = "将删除本次模考记录，成绩不会计入统计。",
            confirmText = "放弃",
            dismissText = "继续考试",
            confirmColor = ui.wrong,
            onConfirm = {
                showQuit = false
                onExit()
            },
            onDismiss = { showQuit = false }
        )
    }

    // ---- 答题卡面板（渲染在 AppRoot 顶层传送门：内容层模糊不波及面板自身） ----
    GlassBottomSheet(
        visible = showPanel,
        backdrop = backdrop,
        onDismiss = { showPanel = false }
    ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "答题卡",
                        color = ui.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    ExamSheetLegend(ui.ink.copy(alpha = 0.25f), "已答")
                    ExamSheetLegend(ui.ink.copy(alpha = 0.08f), "未答")
                }
                // 网格柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影）；
                // 贴顶/贴底的一侧自动无柔化，题号不再被渐变裁切
                val sheetGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = sheetGridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(vertical = 14.dp)
                        .softVerticalEdges(
                            top = 16.dp, bottom = 22.dp,
                            topScrolledPx = { sheetGridState.scrolledFromTopPx() },
                            bottomRemainingPx = { sheetGridState.remainingBottomPx() }
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(total) { i ->
                        val q = questions[i]
                        ExamSheetCell(
                            number = i + 1,
                            answered = answers[q.id] != null,
                            isCurrent = pagerState.currentPage == i,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(i) }
                                showPanel = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
    }
}

@Composable
private fun ExamSheetLegend(color: Color, label: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = ui.textSub, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp))
    }
}

/** 答题卡格子：模考进行中只区分已答/未答（不提示对错，避免影响作答心态）。 */
@Composable
private fun ExamSheetCell(number: Int, answered: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (answered) ui.ink.copy(alpha = 0.25f) else ui.ink.copy(alpha = 0.08f))
            .then(
                if (isCurrent) Modifier.border(2.dp, ui.ink, CircleShape)
                else Modifier.border(1.dp, ui.ink.copy(alpha = 0.08f), CircleShape)
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = if (answered) ui.text else ui.textSub,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimerText(remainingSec: Int) {
    val ui = LocalUi.current
    val urgent = remainingSec <= 60
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "timerPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (urgent) 0.45f else 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(600),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "timerPulse"
    )
    val min = remainingSec / 60
    val sec = remainingSec % 60
    Text(
        String.format("%02d:%02d", min, sec),
        color = if (urgent) ui.wrong else ui.text,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    )
}

@Composable
private fun ExamQuestionCard(
    q: Question,
    index: Int,
    picked: Int?,
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
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.drone.quiz.screens.common.TagChip(q.category)
                Spacer(Modifier.width(6.dp))
                com.drone.quiz.screens.common.TagChip(if (q.isJudge) "判断" else "单选")
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
                    val selected = picked == i
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) ui.ink else ui.ink.copy(alpha = 0.05f))
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) { onPick(i) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) ui.onInk else ui.ink.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (q.isJudge) listOf("√", "×")[i.coerceIn(0, 1)]
                                else ('A' + i).toString(),
                                color = if (selected) ui.ink else ui.textSub,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            opt,
                            color = if (selected) ui.onInk else ui.text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 结果页 ====================

@Composable
fun ExamResultScreen(
    backdrop: Backdrop,
    examId: Long,
    onHome: () -> Unit,
    onWrong: () -> Unit
) {
    val ui = LocalUi.current
    val outcome = ExamSessionHolder.outcome
    var showWrongList by remember { mutableStateOf(false) }

    BounceContainer(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenTitle("模考成绩", null, Modifier.fillMaxWidth().padding(vertical = 16.dp))

            if (outcome == null) {
                Text("成绩加载中…", color = ui.textSub, fontSize = 14.sp)
            } else {
                GlassCard(
                    backdrop = backdrop,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    cornerRadius = 30.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRing(
                            progress = outcome.score / 100f,
                            sizeDp = 150.dp,
                            strokeDp = 13.dp,
                            ringColor = if (outcome.passed) ui.correct else ui.wrong,
                            trackColor = ui.ink.copy(alpha = 0.08f),
                            centerText = "${outcome.score.toInt()}",
                            subText = "分"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        (if (outcome.passed) ui.correct else ui.wrong).copy(alpha = 0.14f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (outcome.passed) "恭喜通过" else "未通过",
                                    color = if (outcome.passed) ui.correct else ui.wrong,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "答对 ${outcome.correct} / ${outcome.total} 题",
                            color = ui.textSub,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                val wrongSingles = outcome.wrongQuestions.count { !it.isJudge }
                val wrongJudges = outcome.wrongQuestions.count { it.isJudge }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    GlassCard(
                        backdrop = backdrop,
                        Modifier.weight(1f),
                        cornerRadius = 20.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "单选错 ${wrongSingles} 题",
                                color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "判断错 ${wrongJudges} 题",
                                color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                if (outcome.wrongQuestions.isNotEmpty()) {
                    GlassCard(
                        backdrop = backdrop,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .animateContentSize(),
                        cornerRadius = 22.dp,
                        onClick = { showWrongList = !showWrongList }
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "错题解析（${outcome.wrongQuestions.size}）",
                                    color = ui.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (showWrongList) AppIcons.Close else AppIcons.ChevronRight,
                                    null,
                                    tint = ui.textSub,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (showWrongList) {
                                Column(Modifier.padding(top = 10.dp)) {
                                    outcome.wrongQuestions.take(20).forEach { q ->
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                q.text,
                                                color = ui.text,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "正确答案：${
                                                    if (q.isJudge) listOf("正确", "错误")[q.answer.coerceIn(0, 1)]
                                                    else "${('A' + q.answer)}·${q.options.getOrNull(q.answer) ?: ""}"
                                                }",
                                                color = ui.correct,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            if (q.explanation.isNotBlank()) {
                                                Text(
                                                    q.explanation,
                                                    color = ui.textSub,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        onClick = onHome,
                        backdrop = backdrop,
                        surfaceColor = ui.ink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("回首页", color = ui.onInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    GlassButton(
                        onClick = onWrong,
                        backdrop = backdrop,
                        surfaceColor = ui.surface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("查看错题本", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}
