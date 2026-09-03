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
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.SectionLabel
import com.drone.quiz.screens.common.SegmentedRow
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.BounceContainer
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/**
 * 刷题配置入口页（刷题 Tab 首页）。
 * 进入刷题 Tab 不再直接开刷：先选范围（全部/单选/判断）与分类，
 * 点"开始刷题"进入全屏刷题页。
 */
@Composable
fun PracticeConfigScreen(
    backdrop: Backdrop,
    onStart: (src: String, type: String, cat: String, resume: Boolean) -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settings.settings
        .collectAsState(initial = AppSettings())

    var typeFilter by remember { mutableStateOf("all") }   // all | single | judge
    var catFilter by remember { mutableStateOf("all") }
    var categories by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var accuracy by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }
    var todayAnswered by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching {
            categories = ServiceLocator.repo.categories().map { it.category to it.cnt }
            total = categories.sumOf { it.second }
        }
        runCatching { accuracy = (ServiceLocator.repo.accuracy() * 100).toInt() }
        runCatching { wrongCount = ServiceLocator.repo.wrongCount().first() }
        runCatching {
            val days = ServiceLocator.repo.last7Days()
            todayAnswered = days.lastOrNull()?.answered ?: 0
        }
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
                .padding(horizontal = 20.dp)
        ) {
            ScreenTitle("刷题", "选择范围，开始你的练习", Modifier.padding(vertical = 16.dp))

            // ---- 题型范围 ----
            SectionLabel("题目范围")
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        when (typeFilter) {
                            "single" -> "只刷单选题"
                            "judge" -> "只刷判断题"
                            else -> "全部题型"
                        },
                        color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    SegmentedRow(
                        options = listOf("全部", "单选", "判断"),
                        selectedIndex = when (typeFilter) {
                            "single" -> 1
                            "judge" -> 2
                            else -> 0
                        },
                        onSelect = {
                            typeFilter = when (it) {
                                1 -> "single"
                                2 -> "judge"
                                else -> "all"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }

            // ---- 分类 ----
            SectionLabel("分类", Modifier.padding(top = 14.dp))
            GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        if (catFilter == "all") "全部分类" else catFilter,
                        color = ui.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConfigChip("全部", catFilter == "all") { catFilter = "all" }
                        categories.forEach { (cat, _) ->
                            ConfigChip(cat, catFilter == cat) {
                                catFilter = if (catFilter == cat) "all" else cat
                            }
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
                onClick = { onStart("all", typeFilter, catFilter, false) },
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
