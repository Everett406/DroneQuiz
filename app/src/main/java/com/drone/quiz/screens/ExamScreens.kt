package com.drone.quiz.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.repo.Question
import com.drone.quiz.data.repo.QuestionTypes
import com.drone.quiz.data.repo.Repo
import com.drone.quiz.data.repo.UserAnswer
import com.drone.quiz.data.repo.judgeAnswer
import com.drone.quiz.data.repo.isAnswered
import com.drone.quiz.data.repo.optionLabel
import com.drone.quiz.screens.common.BlankAnswerFields
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.SectionLabel
import com.drone.quiz.screens.common.SegmentedRow
import com.drone.quiz.screens.common.SubmitAnswerButton
import com.drone.quiz.screens.common.TagChip
import com.drone.quiz.screens.common.WrongAnswerBlock
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
import com.drone.quiz.ui.glass.GlassToggle
import com.drone.quiz.ui.glass.rememberBounceState
import com.drone.quiz.ui.glass.BounceContainer
import com.drone.quiz.ui.theme.LocalUi
import com.drone.quiz.ui.nav.Routes
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val examJson = Json { ignoreUnknownKeys = true }

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
    onResumeExam: (Long) -> Unit = {},
    onOpenResult: (Long) -> Unit = {}   // 点已完成的历史记录 → 重进该场模考成绩页（v2.7.4）
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings
        .collectAsState(initial = com.drone.quiz.data.settings.AppSettings())

    var count by remember { mutableIntStateOf(50) }
    var judgeRatio by remember { mutableStateOf(0.3f) }
    var durationMin by remember { mutableIntStateOf(60) }
    var starting by remember { mutableStateOf(false) }

    // v2.8.0 题库自适应
    var bankTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var bankTypeCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var typeCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var includeShort by remember { mutableStateOf(settings.examIncludeShort) }
    var typeOrder by remember { mutableStateOf<List<String>>(emptyList()) }

    val recentExams by remember(settings.currentBank) {
        ServiceLocator.repo.recentExams(settings.currentBank)
    }.collectAsState(initial = emptyList())

    LaunchedEffect(settings.currentBank) {
        runCatching {
            // 关键：挂起读 DataStore 真值——collectAsState 首帧是 AppSettings() 默认值（drone），
            // 直接读 settings.currentBank 会先用旧库加载一帧（v2.8.2 与 PracticeConfig 同款修复）
            val st = ServiceLocator.settings.settings.first()
            val bank = st.currentBank
            val types = ServiceLocator.repo.typesInBank(bank)
            bankTypes = types
            bankTypeCounts = ServiceLocator.repo.bankTypeCounts(bank)
            typeOrder = st.examTypeOrder.ifEmpty { QuestionTypes.canonicalOrder }
            includeShort = st.examIncludeShort
            // 多题型题库：初始化每型默认题数（clamp 到题库实际拥有量）
            if (types.size > 2 || types.any { it != "single" && it != "judge" }) {
                typeCounts = buildMap {
                    put("single", minOf(30, bankTypeCounts["single"] ?: 0))
                    if ("multi" in types) put("multi", minOf(5, bankTypeCounts["multi"] ?: 0))
                    if ("blank" in types) put("blank", minOf(5, bankTypeCounts["blank"] ?: 0))
                    if ("judge" in types) put("judge", minOf(15, bankTypeCounts["judge"] ?: 0))
                }
            }
        }
    }

    // 组卷题数（自适应两种模式）：
    // - 只有单选/判断的题库（内置无人机题库）：沿用「题目数量 + 判断占比」滑杆
    // - 含新题型的题库：每型题数步进器
    val classicMode = bankTypes.isNotEmpty() && bankTypes.all { it == "single" || it == "judge" }
    val activeTypes = typeOrder
        .ifEmpty { QuestionTypes.canonicalOrder }
        .filter { it in bankTypes && (it != "short" || includeShort) }
    val plannedCounts: Map<String, Int> = if (classicMode) {
        val judgeAvail = bankTypeCounts["judge"] ?: 0
        val wantJudge = if (judgeAvail == 0) 0 else (count * judgeRatio).toInt().coerceAtMost(judgeAvail)
        mapOf("single" to (count - wantJudge).coerceAtLeast(0), "judge" to wantJudge)
    } else {
        typeCounts.filterKeys { it in activeTypes }.filterValues { it > 0 }
    }
    val plannedTotal = plannedCounts.values.sum()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定标题 ----
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenTitle("模考", "模拟真实考试 · 倒计时自动交卷", Modifier.padding(vertical = 16.dp))
        }

        val scrollState = rememberScrollState()
        BounceContainer(
            Modifier
                .weight(1f)
                .softTopFade(36.dp) { scrollState.scrolledFromTopPx() }
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {

            if (classicMode) {
                // ---- 考试设置：2×2 紧凑网格（单选+判断题库） ----
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
            } else {
                // ---- 含新题型题库：时长 / 及格分 + 每型题数步进 ----
                SectionLabel("考试设置")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                SectionLabel("题型构成", Modifier.padding(top = 14.dp))
                GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        // v2.8.2：加减号步进器 → 每型一根滑杆（0..可用量，步长 1），
                        // 与全 app 滑杆交互一致；加减号点几下才能到几十题，实在难用（用户反馈）
                        val typesInCard = bankTypes.filter { it != "short" || includeShort }
                        if (typesInCard.isEmpty()) {
                            Text(
                                "当前题库暂无可考题型",
                                color = ui.textSub, fontSize = 13.sp
                            )
                        }
                        typesInCard.forEach { t ->
                            val avail = bankTypeCounts[t] ?: 0
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        QuestionTypes.label(t),
                                        color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "  可用 $avail",
                                        color = ui.textSub, fontSize = 11.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${typeCounts[t] ?: 0} / $avail",
                                        color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                if (avail > 0) {
                                    GlassSlider(
                                        value = { (typeCounts[t] ?: 0).toFloat() },
                                        onValueChange = { v ->
                                            typeCounts = typeCounts.toMutableMap().apply {
                                                put(t, v.roundToInt().coerceIn(0, avail))
                                            }
                                        },
                                        valueRange = 0f..avail.toFloat(),
                                        step = 1f,
                                        backdrop = backdrop,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "共 $plannedTotal 题 · 拖动滑杆设定各题型题数",
                            color = ui.textSub, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // ---- 高级选项（默认折叠）：含简答题 + 题型顺序拖拽 ----
            if (bankTypes.size > 1) {
                GlassCard(
                    backdrop = backdrop,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    cornerRadius = 22.dp,
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "高级选项",
                                color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showAdvanced) AppIcons.Close else AppIcons.ChevronRight,
                                null, tint = ui.textSub, modifier = Modifier.size(16.dp)
                            )
                        }
                        // v2.8.2：展开/收起加高度+透明度动画（此前生硬跳变，用户反馈）
                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = expandVertically(tween(260)) + fadeIn(tween(220)),
                            exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
                        ) {
                        if ("short" in bankTypes) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("含简答题", color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            "简答题提交后对照参考答案自评计分",
                                            color = ui.textSub, fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    GlassToggle(
                                        checked = { includeShort },
                                        onCheckedChange = { v ->
                                            includeShort = v
                                            scope.launch { ServiceLocator.settings.setExamIncludeShort(v) }
                                        },
                                        backdrop = backdrop
                                    )
                                }
                            }
                            Text(
                                "题型顺序 · 长按拖动调整（先做哪种题型）",
                                color = ui.textSub, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 14.dp)
                            )
                            ReorderableTypeList(
                                order = activeTypes.ifEmpty { bankTypes.filter { it != "short" || includeShort } },
                                onReorder = { newOrder ->
                                    typeOrder = newOrder
                                    scope.launch { ServiceLocator.settings.setExamTypeOrder(newOrder) }
                                }
                            )
                        } // AnimatedVisibility
                    }
                }
            }

            GlassButton(
                onClick = {
                    if (!starting && plannedTotal > 0) {
                        starting = true
                        scope.launch {
                            val st = ServiceLocator.settings.settings.first()
                            val (id, qs) = ServiceLocator.repo.startExam(
                                bankId = st.currentBank,
                                counts = plannedCounts,
                                durationSec = durationMin * 60,
                                typeOrder = typeOrder.ifEmpty { QuestionTypes.canonicalOrder }
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
                    when {
                        starting -> "正在组卷…"
                        plannedTotal <= 0 -> "请先设置题型题数"
                        else -> "开始考试 · 共 $plannedTotal 题"
                    },
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
                        } else {
                            // 已完成记录：点击重进成绩页（数据从 DB 重建，非仅限刚交完那场）
                            { onOpenResult(exam.id) }
                        }
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
                                    "${exam.total} 题 · ${examTypeSummary(exam)}",
                                    color = ui.textSub, fontSize = 11.sp
                                )
                            }
                            if (unfinished) {
                                Text(
                                    if (resumable) "可继续" else "未完成",
                                    color = ui.textSub, fontSize = 12.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                // 删除未完成的幽灵记录
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

/** 模考记录副标题：按题型拼装（兼容旧记录：无 extraCounts 时只有单选/判断）。 */
private fun examTypeSummary(rec: com.drone.quiz.data.db.ExamRecordEntity): String {
    val extras = rec.extraCounts.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { examJson.decodeFromString<Map<String, Int>>(raw) }.getOrNull()
    } ?: emptyMap()
    val parts = mutableListOf<String>()
    if (rec.singleCount > 0) parts += "${rec.singleCount} 单选"
    extras["multi"]?.takeIf { it > 0 }?.let { parts += "$it 多选" }
    extras["blank"]?.takeIf { it > 0 }?.let { parts += "$it 填空" }
    if (rec.judgeCount > 0) parts += "${rec.judgeCount} 判断"
    extras["short"]?.takeIf { it > 0 }?.let { parts += "$it 简答" }
    return parts.joinToString(" ").ifBlank { "${rec.total} 题" }
}

/**
 * 模考设置小卡（2×2 网格单元）：标签 + 当前值 + 迷你滑杆。
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

/**
 * 题型顺序拖拽列表（长按拖动上下换位）。
 * 实现要点：pointerInput 只注册一次（key 固定），通过 rememberUpdatedState 读最新列表，
 * 避免"拖动中途手势检测器重建"导致的手势丢失。
 */
@Composable
private fun ReorderableTypeList(order: List<String>, onReorder: (List<String>) -> Unit) {
    val ui = LocalUi.current
    val rowH = 46.dp
    val latestOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(Modifier.padding(top = 6.dp)) {
        order.forEachIndexed { index, t ->
            val isDragging = dragging == index
            Row(
                Modifier
                    .height(rowH)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDragging) ui.ink.copy(alpha = 0.08f) else Color.Transparent)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = index; dragOffset = 0f },
                            onDragEnd = { dragging = null; dragOffset = 0f },
                            onDragCancel = { dragging = null; dragOffset = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val cur = latestOrder
                                if (cur.isNotEmpty()) {
                                    val rowPx = rowH.toPx()
                                    var guard = 0
                                    while (abs(dragOffset) > rowPx && guard++ < 10) {
                                        val from = dragging ?: break
                                        val dir = if (dragOffset > 0) 1 else -1
                                        val to = from + dir
                                        if (to !in cur.indices) break
                                        val newList = cur.toMutableList().apply {
                                            val item = removeAt(from); add(to, item)
                                        }
                                        onReorder(newList)
                                        dragging = to
                                        dragOffset -= dir * rowPx
                                    }
                                }
                            }
                        )
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("≡", color = ui.textSub, fontSize = 16.sp)
                Text(
                    QuestionTypes.label(t),
                    color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "第 ${index + 1} 位",
                    color = ui.textSub, fontSize = 11.sp
                )
            }
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
    // 统一作答状态（picked 规范值 + details 完整 UserAnswer）
    val answers = remember { mutableStateMapOf<Long, Int>() }
    val details = remember { mutableStateMapOf<Long, UserAnswer>() }
    val pagerState = rememberPagerState { total }

    LaunchedEffect(examId) {
        if (questions.isNotEmpty()) {
            remaining = ExamSessionHolder.durationSec
        } else {
            // 从 DB 重建：题目 + 已答 + 剩余时间（durationSec - 已耗时）
            val resumed = runCatching { ServiceLocator.repo.resumeExam(examId) }.getOrNull()
            if (resumed != null) {
                val (exam, qs, uas) = resumed
                ExamSessionHolder.examId = examId
                ExamSessionHolder.questions = qs
                ExamSessionHolder.durationSec = exam.durationSec
                ExamSessionHolder.outcome = null
                questions = qs
                uas.forEach { (k, v) ->
                    details[k] = v
                    answers[k] = v.picked
                        ?: if (v.texts.isNotEmpty() || v.text.isNotBlank()) 1 else 0
                }
                val elapsed = ((System.currentTimeMillis() - exam.startedAt) / 1000).toInt()
                remaining = (exam.durationSec - elapsed).coerceAtLeast(1)
            } else {
                remaining = 0 // 无法恢复：提示用户返回
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
                    answers = details.toMap(),
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
            .imePadding()
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
            verticalAlignment = Alignment.Top,
            key = { questions[it].id }
        ) { page ->
            val q = questions[page]
            ExamQuestionCard(
                q = q,
                index = page + 1,
                ua = details[q.id],
                backdrop = backdrop,
                onAnswer = { ua ->
                    details[q.id] = ua
                    answers[q.id] = ua.picked
                        ?: if (ua.texts.isNotEmpty() || ua.text.isNotBlank()) 1 else 0
                    val correct = judgeAnswer(q, ua) ?: false
                    scope.launch {
                        runCatching {
                            ServiceLocator.repo.saveExamAnswer(examId, q.id, ua, correct)
                        }
                    }
                }
            )
        }

        // 底部操作条
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
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(total - 1)
                        )
                    }
                },
                backdrop = backdrop,
                icon = AppIcons.ChevronRight,
                sizeDp = 44.dp
            )
        }
    }

    if (showConfirm) {
        val unanswered = questions.count { !isAnswered(it, details[it.id]) }
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

    // ---- 答题卡面板（渲染在 AppRoot 顶层传送门） ----
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
                            answered = isAnswered(q, details[q.id]),
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

/**
 * 模考题目卡：按题型分流。与刷题页不同——考试中不揭示对错，
 * 选项仅显示已选态；简答题提交后看参考答案并自评（自评结果即该题得分口径）。
 */
@Composable
private fun ExamQuestionCard(
    q: Question,
    index: Int,
    ua: UserAnswer?,
    backdrop: Backdrop,
    onAnswer: (UserAnswer) -> Unit
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
                TagChip(q.category)
                Spacer(Modifier.width(6.dp))
                com.drone.quiz.screens.common.QuestionTypeTag(q.type)
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
            when (q.type) {
                QuestionTypes.MULTI -> ExamMultiSection(q, ua, onAnswer)
                QuestionTypes.BLANK -> ExamBlankSection(q, ua, onAnswer)
                QuestionTypes.SHORT -> ExamShortSection(q, ua, backdrop, onAnswer)
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        q.optionsOrJudge.forEachIndexed { i, opt ->
                            val selected = ua?.picked == i
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) ui.ink else ui.ink.copy(alpha = 0.05f))
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) { onAnswer(UserAnswer(picked = i)) }
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
                                        optionLabel(i, q.isJudge),
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
    }
}

@Composable
private fun ExamMultiSection(q: Question, ua: UserAnswer?, onAnswer: (UserAnswer) -> Unit) {
    val ui = LocalUi.current
    val pickedMask = ua?.picked ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("多选题 · 可选多项，交卷时全对才算对", color = ui.textSub, fontSize = 11.sp)
        q.options.forEachIndexed { i, opt ->
            val selected = pickedMask and (1 shl i) != 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) ui.ink.copy(alpha = 0.10f) else ui.ink.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (selected) ui.ink.copy(alpha = 0.35f) else ui.ink.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null
                    ) {
                        // 点选切换（位掩码），每次变更即落库；交卷时按位掩码全等判分
                        onAnswer(UserAnswer(picked = pickedMask xor (1 shl i)))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ui.ink else ui.ink.copy(alpha = 0.08f))
                        .border(1.dp, ui.ink.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(AppIcons.Check, null, tint = ui.onInk, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    opt,
                    color = ui.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 14.dp, top = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ExamBlankSection(q: Question, ua: UserAnswer?, onAnswer: (UserAnswer) -> Unit) {
    val count = q.blankCount.coerceAtLeast(1)
    var texts by remember(q.id) { mutableStateOf(ua?.texts?.takeIf { it.isNotEmpty() } ?: List(count) { "" }) }
    Column(Modifier.padding(top = 14.dp)) {
        BlankAnswerFields(
            count = count,
            values = texts,
            onValueChange = { i, v ->
                texts = texts.toMutableList().also { it[i] = v }
                onAnswer(UserAnswer(picked = 1, texts = texts))
            },
            enabled = true
        )
        Text(
            "填空按精确匹配判分（区分空格）",
            color = LocalUi.current.textSub, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ExamShortSection(
    q: Question,
    ua: UserAnswer?,
    backdrop: Backdrop,
    onAnswer: (UserAnswer) -> Unit
) {
    val submitted = ua != null
    var draft by remember(q.id, submitted) { mutableStateOf("") }
    Column(Modifier.padding(top = 14.dp)) {
        com.drone.quiz.screens.common.ShortDraftField(
            value = if (submitted) ua!!.text else draft,
            onValueChange = { draft = it },
            enabled = !submitted
        )
        if (!submitted) {
            SubmitAnswerButton(
                enabled = draft.isNotBlank(),
                hint = "提交，看参考答案",
                backdrop = backdrop
            ) {
                onAnswer(UserAnswer(picked = 1, text = draft, graded = false))
            }
        } else {
            com.drone.quiz.screens.common.ShortReferenceCard(
                reference = q.answerText,
                yourText = ua!!.text,
                graded = ua.graded,
                selfCorrect = if (ua.graded) ua.picked == 1 else null,
                onGrade = { correct ->
                    onAnswer(ua.copy(picked = if (correct) 1 else 0, graded = true))
                }
            )
        }
    }
}

// ==================== 结果页 ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamResultScreen(
    backdrop: Backdrop,
    examId: Long,
    onHome: () -> Unit,
    onWrong: () -> Unit,
    onDeleted: () -> Unit = onHome
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()

    // 成绩来源双通道：刚交卷走 ExamSessionHolder（即时、含完整题目对象）；
    // 从"最近模考"历史记录进入时 SessionHolder 为空 → 从数据库按 examId 精确重建
    var outcome by remember(examId) {
        mutableStateOf(ExamSessionHolder.outcome?.takeIf { ExamSessionHolder.examId == examId })
    }
    var loadingDone by remember(examId) { mutableStateOf(outcome != null) }
    // 错题解析展开态
    var showWrongList by remember(examId) { mutableStateOf(false) }

    // 删除本次记录（右上角三点入口；每周限 2 次，弹窗告知）
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQuotaExhausted by remember { mutableStateOf(false) }
    var quotaLeft by remember { mutableIntStateOf(2) }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(examId) {
        if (outcome == null) {
            outcome = runCatching { ServiceLocator.repo.loadExamOutcome(examId) }.getOrNull()
        }
        loadingDone = true
    }

    // 删除成功 → 立即返回（配置页 recentExams 为响应式 Flow，自动刷新）
    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

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
            // 标题行 + 右上角"三点"删除入口（浅色低调，有成绩时才出现）
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle("模考成绩", null, Modifier.weight(1f).padding(vertical = 16.dp))
                if (outcome != null) {
                    GlassIconButton(
                        onClick = {
                            scope.launch {
                                val quota = runCatching {
                                    ServiceLocator.settings.examDeleteQuota()
                                }.getOrDefault(0 to 2)
                                quotaLeft = quota.second
                                if (quota.second > 0) showDeleteConfirm = true
                                else showQuotaExhausted = true
                            }
                        },
                        backdrop = backdrop,
                        icon = AppIcons.Dots,
                        sizeDp = 40.dp,
                        iconSize = 18.dp,
                        iconTint = ui.textSub
                    )
                }
            }

            val oc = outcome
            if (oc == null) {
                Text(
                    if (loadingDone) "未找到这场考试的成绩记录" else "成绩加载中…",
                    color = ui.textSub, fontSize = 14.sp
                )
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
                            progress = oc.score / 100f,
                            sizeDp = 150.dp,
                            strokeDp = 13.dp,
                            ringColor = if (oc.passed) ui.correct else ui.wrong,
                            trackColor = ui.ink.copy(alpha = 0.08f),
                            centerText = "${oc.score.toInt()}",
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
                                        (if (oc.passed) ui.correct else ui.wrong).copy(alpha = 0.14f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (oc.passed) "恭喜通过" else "未通过",
                                    color = if (oc.passed) ui.correct else ui.wrong,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "答对 ${oc.correct} / ${oc.total} 题",
                            color = ui.textSub,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                // 错题分布（按题型自适应，v2.8.0）
                val wrongByType = oc.wrongQuestions.groupingBy { it.type }.eachCount()
                if (wrongByType.isNotEmpty()) {
                    GlassCard(
                        backdrop = backdrop,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        cornerRadius = 20.dp
                    ) {
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuestionTypes.canonicalOrder.forEach { t ->
                                wrongByType[t]?.let { n ->
                                    Text(
                                        "${QuestionTypes.label(t)}错 $n 题",
                                        color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 错题解析：懒加载列表（只组合可见条目）
                if (oc.wrongQuestions.isNotEmpty()) {
                    GlassCard(
                        backdrop = backdrop,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        cornerRadius = 22.dp,
                        onClick = { showWrongList = !showWrongList }
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "错题解析（${oc.wrongQuestions.size}）",
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
                                LazyColumn(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .heightIn(max = 420.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(oc.wrongQuestions, key = { it.id }) { q ->
                                        WrongAnswerBlock(q, oc.userAnswers[q.id])
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

    // 删除确认：弹窗内告知本周剩余额度（每周限 2 次）
    if (showDeleteConfirm) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "删除这次模考记录？",
            body = "将删除本次成绩与全部作答记录，不可恢复。每周最多删除 2 次，本周还可删除 $quotaLeft 次。",
            confirmText = "删除",
            dismissText = "取消",
            confirmColor = ui.wrong,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    runCatching {
                        ServiceLocator.repo.abandonExam(examId)
                        ServiceLocator.settings.recordExamDeletion()
                    }
                    deleted = true
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    // 超限提示：本周 2 次额度已用完
    if (showQuotaExhausted) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "本周删除次数已用完",
            body = "为防误删，每周最多删除 2 次模考记录。本周额度已用完，下周一自动恢复 2 次。",
            confirmText = "知道了",
            dismissText = "关闭",
            onConfirm = { showQuotaExhausted = false },
            onDismiss = { showQuotaExhausted = false }
        )
    }
}
