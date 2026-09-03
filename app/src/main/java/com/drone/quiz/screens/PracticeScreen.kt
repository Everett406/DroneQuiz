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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.settings.PracticeSession
import com.drone.quiz.data.settings.AppSettings
import com.drone.quiz.data.repo.Question
import com.drone.quiz.screens.common.TagChip
import com.drone.quiz.screens.common.remainingBottomPx
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softVerticalEdges
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassIconButton
import com.drone.quiz.ui.glass.GlassSlider
import com.drone.quiz.ui.glass.GlassBottomSheet
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全屏刷题页（由配置页进入；非 Tab destination，无底栏遮挡）。
 * 进度实时持久化：答题/翻页即写 DataStore 会话快照（后台静默保存，不丢记录）。
 * 左右切题带转盘弧度（rotationY + 透视），配合全局 iOS 式回弹。
 */
@Composable
fun PracticeRunScreen(
    backdrop: Backdrop,
    src: String = "all",
    type: String = "all",
    cat: String = "all",
    resume: Boolean = false,
    onExit: () -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings
        .collectAsState(initial = AppSettings())

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var sessionMode by remember { mutableStateOf(resume) }
    // 恢复中：抑制翻页持久化，防止 snapshotFlow 初始发射把快照 index 覆写回 0
    // （旧版"每次点开都是第一题"的真病灶：恢复→发射 currentPage=0→异步落盘覆盖原进度→
    //   恢复跳页读到的是被覆写后的 0）
    var restoring by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Long, Int>() }
    var showPanel by remember { mutableStateOf(false) }
    var restoredTick by remember { mutableIntStateOf(0) }
    var reloadTick by remember { mutableIntStateOf(0) }
    // 本会话所属模式槽（0 顺序 / 1 随机）：load 时定案，跳转/落盘全链路用它，
    // 保证顺序与随机各自的进度存在各自的槽里，互不覆盖
    var sessionOrder by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState { questions.size }

    // ---- 题库就绪自动重载：启动门控超时放行/导入后台完成后，题数 0→N 触发重试 ----
    LaunchedEffect(Unit) {
        var prev = -1
        ServiceLocator.repo.countFlow().collect { c ->
            if (c > 0 && prev == 0 && questions.isEmpty() && !loading) {
                reloadTick++
            }
            prev = c
        }
    }

    // ---- 加载：优先恢复会话快照，否则按筛选参数新开；超时兑底永不定死 ----
    LaunchedEffect(src, type, cat, resume, reloadTick) {
        loading = true
        loadError = false
        // 关键：必须挂起读 DataStore 真值——collectAsState 首帧是 AppSettings() 默认值，
        // 协程首帧启动时 DataStore 往往尚未发射，用快照里的 practiceOrder 永远是默认 0(顺序)，
        // 用户选了随机也会被当成顺序加载（v2.7.2 及之前"随机=顺序"的第二根因）
        val order = runCatching {
            ServiceLocator.settings.settings.first().practiceOrder
        }.getOrDefault(0)
        sessionOrder = order
        val result = withTimeoutOrNull(10_000) {
            runCatching {
            if (resume) {
                val s = ServiceLocator.settings.currentPracticeSession(order)
                if (s != null && s.ids.isNotEmpty() && !sessionComplete(s)) {
                    sessionMode = true
                    restoring = true
                    val qs = ServiceLocator.repo.loadPracticeByIds(s.ids)
                    answers.clear()
                    s.answers.forEach { (k, v) -> k.toLongOrNull()?.let { answers[it] = v } }
                    restoredTick++          // 恢复完成后跳转进度
                    qs
                } else {
                    // 无会话可恢复 / 上轮已全部刷完：退化为按参数新开
                    sessionMode = false
                    loadByFilter(src, type, cat, order == 1)
                }
            } else {
                sessionMode = false
                answers.clear()
                // 自动接续：存在同筛选参数的会话快照 → 无缝恢复
                // （刷过的题保留对错标记、直接定位到上次进度；换筛选=开新会话；
                //   上一轮已全部刷完 → 不再接续，随机模式自然重新洗牌）
                val snap = runCatching { ServiceLocator.settings.currentPracticeSession(order) }.getOrNull()
                if (src == "all" && snap != null && snap.src == "all" &&
                    !sessionComplete(snap) &&
                    snap.type == type && snap.cat == cat
                ) {
                    val qs = ServiceLocator.repo.loadPracticeByIds(snap.ids)
                    if (qs.isNotEmpty()) {
                        sessionMode = true
                        restoring = true
                        snap.answers.forEach { (k, v) -> k.toLongOrNull()?.let { answers[it] = v } }
                        restoredTick++
                        qs
                    } else {
                        loadByFilter(src, type, cat, order == 1)
                    }
                } else {
                    loadByFilter(src, type, cat, order == 1)
                }
            }
            }
        }
        questions = result?.getOrElse { emptyList() }.orEmpty()
        loadError = result == null || result.isFailure
        loading = false
        // 注意：恢复路径不在此处落盘——落盘只发生在真实翻页/作答时，
        // 避免用尚未跳转的 currentPage(0) 覆盖快照进度
    }

    // 恢复会话：等 Pager 上屏后跳到上次进度；跳转完成后才恢复持久化（restoring 解除）
    LaunchedEffect(restoredTick) {
        if (restoredTick > 0 && questions.isNotEmpty()) {
            val target = ServiceLocator.settings.currentPracticeSession(sessionOrder)?.index ?: 0
            pagerState.scrollToPage(target.coerceIn(0, questions.size - 1))
            restoring = false
        }
    }

    // 新会话首次落盘（拿到题目列表即建快照，答第一题前退出也能续）
    var sessionSeeded by remember { mutableStateOf(false) }
    LaunchedEffect(questions) {
        if (questions.isNotEmpty() && !sessionSeeded && !sessionMode) {
            sessionSeeded = true
            pagerState.scrollToPage(0)
            persistSession(src, type, cat, sessionOrder, questions, answers, 0)
        }
    }

    // 翻页进度实时保存（恢复跳页期间静默，防止把进度覆写回 0）
    LaunchedEffect(questions) {
        if (questions.isEmpty()) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .collectLatest { page ->
                if (!loading && !restoring) {
                    persistSession(src, type, cat, sessionOrder, questions, answers, page)
                }
            }
    }

    fun onPick(q: Question, index: Int) {
        if (answers.containsKey(q.id)) return
        answers[q.id] = index
        val correct = index == q.answer
        // 落盘挂应用级协程域：页面退出不再取消，记录绝不丢失
        ServiceLocator.appScope.launch {
            runCatching {
                ServiceLocator.repo.recordAnswer(
                    qid = q.id,
                    isCorrect = correct,
                    mode = if (src == "wrong") "wrong" else "practice",
                    removeThreshold = settings.removeThreshold
                )
            }
        }
        persistSession(src, type, cat, sessionOrder, questions, answers, pagerState.currentPage)
        if (correct && settings.autoNext && pagerState.currentPage < questions.size - 1) {
            scope.launch {
                kotlinx.coroutines.delay(700)
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
        // ---- 顶部：返回 + 标题 + 题号面板 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                onClick = onExit,
                backdrop = backdrop,
                icon = AppIcons.ChevronLeft
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    if (src == "wrong") "错题特训" else "刷题",
                    color = ui.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (questions.isEmpty()) "加载中…"
                    else "第 ${pagerState.currentPage + 1} / ${questions.size} 题" +
                        if (src == "wrong" || cat == "all") "" else " · $cat",
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

        // ---- 题目 Pager ----
        when {
            loading -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("正在加载题库…", color = ui.textSub, fontSize = 14.sp)
            }
            questions.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        AppIcons.Cards, null,
                        tint = ui.textSub, modifier = Modifier.size(40.dp)
                    )
                    Text(
                        when {
                            loadError -> "题库加载失败"
                            src == "wrong" -> "错题本是空的，继续保持！"
                            else -> "没有符合条件的题目"
                        },
                        color = ui.textSub,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (loadError) {
                        GlassButton(
                            onClick = { reloadTick++ },
                            backdrop = backdrop,
                            heightDp = 40.dp,
                            modifier = Modifier.padding(top = 14.dp)
                        ) {
                            Text("重试", color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    key = { questions[it].id }
                ) { page ->
                    val q = questions[page]
                    // 切页纵深感：离场页轻微后缩（v2.5.1 简化，详见下方 graphicsLayer 注释）
                    Box(
                        Modifier.graphicsLayer {
                            // 轻微纵深：离场页微缩。不做 3D 旋转/透明度渐隐——
                            // rotationY 会让卡片上下角超出 Pager 视口被裁剪（"上下切一刀"），
                            // 且与玻璃 backdrop 采样不兼容产生阴影状伪影（v2.5.1 移除）
                            val off = ((page - pagerState.currentPage) -
                                pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                            val s = 1f - abs(off) * 0.03f
                            scaleX = s
                            scaleY = s
                        }
                    ) {
                        QuestionCard(
                            q = q,
                            picked = answers[q.id],
                            index = page + 1,
                            backdrop = backdrop,
                            onPick = { onPick(q, it) }
                        )
                    }
                }

                // ---- 底部玻璃操作条（全屏模式，无底栏遮挡，仅避让手势条） ----
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
                                pagerState.scrollToPage((v - 1f).roundToInt().coerceIn(0, questions.size - 1))
                            }
                        },
                        valueRange = 1f..questions.size.toFloat().coerceAtLeast(1f),
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
    }

    // ---- 题号面板（渲染在 AppRoot 顶层传送门：内容层模糊不波及面板自身） ----
    GlassBottomSheet(
        visible = showPanel,
        backdrop = backdrop,
        onDismiss = { showPanel = false }
    ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
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
                // 网格柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影）；
                // 贴顶/贴底的一侧自动无柔化，题号不再被渐变裁切
                val panelGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = panelGridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(vertical = 14.dp)
                        .softVerticalEdges(
                            top = 16.dp, bottom = 22.dp,
                            topScrolledPx = { panelGridState.scrolledFromTopPx() },
                            bottomRemainingPx = { panelGridState.remainingBottomPx() }
                        ),
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

private suspend fun loadByFilter(
    src: String,
    type: String,
    cat: String,
    random: Boolean
): List<Question> =
    if (src == "wrong") {
        ServiceLocator.repo.loadWrongPractice()
    } else {
        ServiceLocator.repo.loadPractice(
            category = if (cat == "all") null else cat,
            type = if (type == "all") null else type,
            random = random
        )
    }

/** 会话是否已全部刷完（刷完的会话不再接续，下次进入自动开新一轮） */
internal fun sessionComplete(s: PracticeSession): Boolean {
    if (s.ids.isEmpty()) return false
    val answered = s.answers.keys.mapNotNull { it.toLongOrNull() }.toSet()
    return s.ids.all { it in answered }
}

/**
 * 会话快照落盘（挂 appScope，静默失败不影响 UI）。
 * 错题特训（src="wrong"）不落盘：快照只有一份全局槽位，特训一写就把主刷题会话覆掉，
 * 用户回配置页再开刷时接续判定 snap.src=="all" 失败 → 从头开始（"不会跳到上次进度"真因之一）。
 * 特训进度本就由 recordAnswer 记账，无需会话恢复。
 */
internal fun persistSession(
    src: String,
    type: String,
    cat: String,
    order: Int,
    questions: List<Question>,
    answers: Map<Long, Int>,
    index: Int
) {
    if (questions.isEmpty() || src == "wrong") return
    ServiceLocator.appScope.launch {
        runCatching {
            ServiceLocator.settings.setPracticeSession(
                PracticeSession(
                    src = src,
                    type = type,
                    cat = cat,
                    ids = questions.map { it.id },
                    answers = answers.mapKeys { it.key.toString() },
                    index = index
                ),
                order
            )
        }
    }
}

@Composable
internal fun PanelLegend(color: Color?, label: String) {
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

/**
 * 单张题目卡片。
 * 内容（题目/选项/解析）超过卡片可用高度时整体可垂直滚动——
 * 此前解析展开后被裁切在屏幕外且无法滚动查看。
 */
@Composable
internal fun QuestionCard(
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
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
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

internal fun optionLabel(i: Int, isJudge: Boolean): String =
    if (isJudge) listOf("√", "×")[i.coerceIn(0, 1)] else ('A' + i).toString()

internal fun optionState(i: Int, picked: Int?, answer: Int): Int =
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
            // v2.7.2：正确/误选项徽章恢复显示原字母（白字），不再替换成 ✓/✕ 图标——
            // 用户反馈"绿色选项里 ABC 不显示"；正确/你选的语义由行尾文字标签表达
            Text(
                label,
                color = if (state > 0) Color.White else ui.textSub,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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
