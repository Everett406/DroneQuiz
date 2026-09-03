package com.drone.quiz.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.repo.Question
import com.drone.quiz.screens.common.TagChip
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softTopFade
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassIconButton
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/**
 * 题目搜索页：题干 / 选项 / 解析 全文检索（防抖 300ms，最多返回 80 条）。
 * 结果点开可看全部选项、正确答案与解析。
 */
@Composable
fun SearchScreen(
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val ui = LocalUi.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Question>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    // 输入防抖搜索
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        results = runCatching { ServiceLocator.repo.searchQuestions(q) }.getOrDefault(emptyList())
        searching = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定头部：返回 + 搜索框 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                onClick = onBack,
                backdrop = backdrop,
                icon = AppIcons.ChevronLeft
            )
            GlassCard(
                backdrop = backdrop,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                cornerRadius = 22.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AppIcons.Search, null,
                        tint = ui.textSub, modifier = Modifier.size(18.dp)
                    )
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索题目 / 选项 / 解析",
                                color = ui.textSub, fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = ui.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(ui.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            AppIcons.Close, null,
                            tint = ui.textSub,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(
                                    interactionSource = null,
                                    indication = null
                                ) { query = "" }
                        )
                    }
                }
            }
        }

        // ---- 结果列表 ----
        // 顶部柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影，滑出渐显跟手）
        val resultListState = rememberLazyListState()
        LazyColumn(
            Modifier
                .fillMaxSize()
                .softTopFade(20.dp) { resultListState.scrolledFromTopPx() },
            state = resultListState
        ) {
            item {
                Text(
                    when {
                        query.isBlank() -> "输入关键词，如「升阻比」「锂电池」「迫降」"
                        searching -> "搜索中…"
                        results.isEmpty() -> "没有匹配的题目"
                        else -> "找到 ${results.size} 条（最多展示 80 条）"
                    },
                    color = ui.textSub, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            items(results, key = { it.id }) { q ->
                SearchResultItem(
                    q = q,
                    expanded = expandedId == q.id,
                    backdrop = backdrop,
                    onClick = { expandedId = if (expandedId == q.id) null else q.id }
                )
            }
            item { Spacer(Modifier.height(130.dp)) }
        }

    }
}

@Composable
private fun SearchResultItem(
    q: Question,
    expanded: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val ui = LocalUi.current
    GlassCard(
        backdrop = backdrop,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth(),
        cornerRadius = 22.dp,
        onClick = onClick
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(q.category)
                Spacer(Modifier.width(6.dp))
                TagChip(if (q.isJudge) "判断" else "单选")
                Spacer(Modifier.weight(1f))
                Text(
                    "正确答案：${optionLabel(q.answer, q.isJudge)}",
                    color = ui.correct, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                q.text,
                color = ui.text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    q.optionsOrJudge.forEachIndexed { i, opt ->
                        val isAnswer = i == q.answer
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAnswer) ui.correct else ui.ink.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    optionLabel(i, q.isJudge),
                                    color = if (isAnswer) ui.correct else ui.textSub,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                opt,
                                color = if (isAnswer) ui.correct else ui.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                    if (q.explanation.isNotBlank()) {
                        Text(
                            q.explanation,
                            color = ui.textSub,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ui.ink.copy(alpha = 0.05f))
                                .padding(10.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                Text(
                    "点开查看全部选项与解析",
                    color = ui.textSub, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
