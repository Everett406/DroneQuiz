package com.drone.quiz.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.settings.AppSettings
import com.drone.quiz.data.settings.PracticeSession
import com.drone.quiz.data.repo.QuestionTypes
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.SectionLabel
import com.drone.quiz.screens.common.SegmentedRow
import com.drone.quiz.screens.common.heroSearchField
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softTopFade
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.BounceContainer
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassConfirmDialog
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 刷题配置入口页（刷题 Tab 首页）。
 * 进入刷题 Tab 不再直接开刷：先选范围（全部/单选/判断）与分类，
 * 点"开始刷题"进入全屏刷题页。
 */
@Composable
fun PracticeConfigScreen(
    backdrop: Backdrop,
    onSearch: () -> Unit = {},
    onStart: (src: String, type: String, cat: String, resume: Boolean) -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings
        .collectAsState(initial = AppSettings())

    // 题型/分类筛选（v2.8.0 自适应：题型选项 = 当前题库实际拥有的题型；分类不再有「全部」）
    var typeFilter by remember { mutableStateOf("all") }   // all | single | multi | blank | judge | short
    var catFilter by remember { mutableStateOf("") }
    var types by remember { mutableStateOf<List<String>>(emptyList()) }
    // 当前模式的会话快照（双槽：顺序/随机各存各的进度），响应式——切换模式提示即随之切换；
    // 也用于“重新开始”按钮与二次确认（v2.7.4）
    var showResetConfirm by remember { mutableStateOf(false) }
    val sessionFlow = remember(settings.practiceOrder) {
        ServiceLocator.settings.practiceSession(settings.practiceOrder)
    }
    val lastSession by sessionFlow.collectAsState(initial = null)
    var categories by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var accuracy by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var todayAnswered by remember { mutableIntStateOf(0) }

    // v2.8.2 修复题库隔离 bug：原先 LaunchedEffect(Unit) 在首帧读到的是 collectAsState 的
    // 默认值 currentBank="drone" 且永不重跑 → 切库后分类/概览永远加载旧题库。
    // 改为持续收集 settings（按 currentBank 去重）：首发射就是 DataStore 真值，切库后自动重载。
    LaunchedEffect(Unit) {
        ServiceLocator.settings.settings
            .distinctUntilChangedBy { it.currentBank }
            .collectLatest { st ->
                runCatching {
                    val bank = st.currentBank
                    types = ServiceLocator.repo.typesInBank(bank)
                    val cats = ServiceLocator.repo.categoriesOf(bank).map { it.category to it.cnt }
                    categories = cats
                    total = cats.sumOf { it.second }
                    // 分类去掉「全部」后，默认选中第一个分类（单分类题库效果与原全部一致）
                    if (catFilter !in cats.map { it.first }) catFilter = cats.firstOrNull()?.first.orEmpty()
                }
                runCatching { accuracy = (ServiceLocator.repo.accuracy(bank) * 100).toInt() }
                runCatching { wrongCount = ServiceLocator.repo.wrongCount(bank) }
                // 今日已刷同步按题库隔离（v2.8.2）；连击/近 7 天为全局习惯数据
                runCatching {
                    val (_, today, _) = ServiceLocator.repo.last7DaysByBank(bank)
                    todayAnswered = today
                }
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定标题 + 搜索入口（不随滚动） ----
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenTitle("刷题", "选择范围，开始你的练习", Modifier.padding(vertical = 16.dp))
            GlassCard(
                backdrop = backdrop,
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .heroSearchField(),
                cornerRadius = 22.dp,
                onClick = onSearch
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AppIcons.Search, null,
                        tint = ui.textSub, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "搜索题目 / 选项 / 解析",
                        color = ui.textSub, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        AppIcons.ChevronRight, null,
                        tint = ui.textSub, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 标题柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影，滑出渐显跟手）
        val cfgScroll = rememberScrollState()
        BounceContainer(
            Modifier
                .weight(1f)
                .softTopFade(36.dp) { cfgScroll.scrolledFromTopPx() }
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(cfgScroll)
                .padding(horizontal = 20.dp)
        ) {

            // ---- 题型范围（自适应：只列出当前题库拥有的题型） ----
            SectionLabel("题目范围")
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        when (typeFilter) {
                            "all" -> "全部题型"
                            else -> "只刷${QuestionTypes.label(typeFilter)}题"
                        },
                        color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConfigChip("全部", typeFilter == "all") { typeFilter = "all" }
                        types.forEach { t ->
                            ConfigChip(
                                QuestionTypes.label(t),
                                typeFilter == t
                            ) {
                                typeFilter = if (typeFilter == t) "all" else t
                            }
                        }
                    }
                }
            }

            // ---- 分类（v2.8.0：去掉「全部」，只保留当前题库的真实分类） ----
            SectionLabel("分类", Modifier.padding(top = 14.dp))
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        catFilter.ifBlank { "加载中…" },
                        color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { (cat, _) ->
                            ConfigChip(cat, catFilter == cat) { catFilter = cat }
                        }
                    }
                }
            }

            // ---- 顺序 ----
            SectionLabel("题目顺序", Modifier.padding(top = 14.dp))
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        if (settings.practiceOrder == 1) "随机顺序" else "题库顺序",
                        color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    SegmentedRow(
                        options = listOf("顺序", "随机"),
                        selectedIndex = settings.practiceOrder,
                        onSelect = { scope.launch { ServiceLocator.settings.setPracticeOrder(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }

            // ---- 开始刷题 ----
            GlassButton(
                onClick = { if (catFilter.isNotBlank()) onStart("all", typeFilter, catFilter, false) },
                backdrop = backdrop,
                surfaceColor = ui.ink,
                heightDp = 54.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
            ) {
                Icon(AppIcons.Cards, null, tint = ui.onInk, modifier = Modifier.size(18.dp))
                Text(
                    "开始刷题",
                    color = ui.onInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ---- 续刷提示：当前模式下有未完成进度时展示；"重新开始"胶囊按钮 + 二次确认（v2.7.4） ----
            val snap = lastSession
            val snapReusable = snap != null && snap.src == "all" &&
                snap.type == typeFilter && snap.cat == catFilter &&
                !sessionComplete(snap) && (snap.index > 0 || snap.answers.isNotEmpty())
            if (snapReusable && snap != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "将接续上次进度 · 第 ${(snap.index + 1).coerceAtMost(snap.ids.size)} / ${snap.ids.size} 题",
                        color = ui.textSub, fontSize = 12.sp
                    )
                    // 重新设计：浅红底描边胶囊（原裸文字偏丑），点击弹二次确认，不再一键直清
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ui.wrong.copy(alpha = 0.10f))
                            .border(1.dp, ui.wrong.copy(alpha = 0.30f), RoundedCornerShape(50))
                            .clickable { showResetConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "重新开始",
                            color = ui.wrong, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ---- 学习概览（填充页面 + 有用信息） ----
            SectionLabel("学习概览")
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 18.dp)
                ) {
                    OverviewCell("题库", "$total 题", Modifier.weight(1f))
                    OverviewCell("今日已刷", "$todayAnswered 题", Modifier.weight(1f))
                    OverviewCell("总正确率", "$accuracy%", Modifier.weight(1f))
                    OverviewCell("待复习", "$wrongCount 题", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(130.dp))
        }
        
}
    }

    // 重新开始二次确认：双保险，防误触一键清进度（v2.7.4）
    if (showResetConfirm) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "重新开始？",
            body = "将清空${if (settings.practiceOrder == 1) "随机" else "顺序"}模式的本次刷题进度（含作答记录），下次从开头开始。此操作不可撤销。",
            confirmText = "清空重开",
            dismissText = "取消",
            confirmColor = ui.wrong,
            onConfirm = {
                showResetConfirm = false
                scope.launch {
                    runCatching {
                        ServiceLocator.settings.setPracticeSession(null, settings.practiceOrder)
                    }
                }
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}

@Composable
private fun OverviewCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = LocalUi.current.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = LocalUi.current.textSub,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun ConfigChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
