package com.drone.quiz.screens.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.data.repo.Question
import com.drone.quiz.data.repo.QuestionTypes
import com.drone.quiz.data.repo.UserAnswer
import com.drone.quiz.data.repo.displayUserAnswer
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.theme.LocalReadingFont
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.Backdrop

/**
 * 题型作答组件族（v2.8.0）：刷题页与模考页共用。
 * 设计要点：
 * - 多选：点选切换（不判分），底部「提交」后一次性判定（全对才算对）；
 * - 填空：每空一个输入行，键盘弹出时由页面根级 imePadding 避让；全部填完才可提交；
 * - 简答：多行草稿 → 提交看参考答案 → 自评「答对了/答错了」计分（用户选定口径）。
 */

@Composable
fun QuestionTypeTag(type: String) {
    TagChip(QuestionTypes.label(type))
}

// ---------- 选项行（single/judge 保持原样式；multi 为可多选变体） ----------

@Composable
fun OptionRow(
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
    val (bg, borderColor) = optionColors(state)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptionBadge(label, state)
        Text(
            text,
            color = ui.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        ResultTag(state)
    }
}

/** 多选选项行：state 同 OptionRow；selected = 提交前已勾选；resultState ≥0 表示已提交。 */
@Composable
fun OptionToggleRow(
    label: String,
    text: String,
    selected: Boolean,
    resultState: Int, // -1 未提交（勾选态），0/1/2 已提交
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val ui = LocalUi.current
    val submitted = resultState >= 0
    val (bg, borderColor) = if (submitted) optionColors(resultState)
    else if (selected) ui.ink.copy(alpha = 0.10f) to ui.ink.copy(alpha = 0.35f)
    else ui.ink.copy(alpha = 0.05f) to ui.ink.copy(alpha = 0.08f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(interactionSource = null, indication = null, onClick = onToggle) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 多选用方框勾选，区别于单选圆徽章
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        submitted && resultState == 1 -> ui.correct
                        submitted && resultState == 2 -> ui.wrong
                        selected -> ui.ink
                        else -> ui.ink.copy(alpha = 0.08f)
                    }
                )
                .border(1.dp, ui.ink.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected || (submitted && resultState == 1)) {
                Icon(
                    AppIcons.Check, null,
                    tint = if (submitted && resultState == 2) Color.White else ui.onInk,
                    modifier = Modifier.size(14.dp)
                )
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
        ResultTag(resultState.takeIf { submitted })
    }
}

@Composable
private fun OptionBadge(label: String, state: Int) {
    val ui = LocalUi.current
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
        Text(
            label,
            color = if (state > 0) Color.White else ui.textSub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ResultTag(state: Int?) {
    val ui = LocalUi.current
    when (state) {
        1 -> Text("正确", color = ui.correct, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        2 -> Text("你选的", color = ui.wrong, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun optionColors(state: Int): Pair<Color, Color> {
    val ui = LocalUi.current
    return when (state) {
        1 -> ui.correct.copy(alpha = 0.14f) to ui.correct.copy(alpha = 0.6f)
        2 -> ui.wrong.copy(alpha = 0.14f) to ui.wrong.copy(alpha = 0.6f)
        else -> ui.ink.copy(alpha = 0.05f) to ui.ink.copy(alpha = 0.08f)
    }
}

// ---------- 判定结果头 + 解析 ----------

@Composable
fun ResultHeader(isCorrect: Boolean?, suffix: String = "") {
    val ui = LocalUi.current
    if (isCorrect == null) return
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
        if (suffix.isNotBlank()) {
            Text(suffix, color = ui.textSub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun CorrectAnswerLine(text: String) {
    val ui = LocalUi.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("正确答案：", color = ui.textSub, fontSize = 12.sp)
        Text(
            text,
            color = ui.correct,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun YourAnswerLine(text: String) {
    val ui = LocalUi.current
    Text("你的答案：$text", color = ui.textSub, fontSize = 12.sp)
}

@Composable
fun ParseBlock(explanation: String) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ui.ink.copy(alpha = 0.05f))
            .padding(14.dp)
    ) {
        Text("解析", color = ui.textSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            explanation.ifBlank { "暂无解析" },
            color = ui.text,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------- 填空 ----------

/**
 * 填空作答区：每空一行输入（圆角浅底），提交后逐空展示对错与可接受答案。
 * 键盘避让由页面根级 Modifier.imePadding() 统一处理。
 */
@Composable
fun BlankAnswerFields(
    count: Int,
    values: List<String>,
    onValueChange: (index: Int, value: String) -> Unit,
    enabled: Boolean,
    showResult: Boolean = false,
    answers: List<List<String>> = emptyList()
) {
    val ui = LocalUi.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { i ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                showResult && answers.getOrNull(i)?.contains(values[i]) == true ->
                                    ui.correct.copy(alpha = 0.12f)
                                showResult -> ui.wrong.copy(alpha = 0.12f)
                                else -> ui.ink.copy(alpha = 0.05f)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                showResult && answers.getOrNull(i)?.contains(values[i]) == true ->
                                    ui.correct.copy(alpha = 0.5f)
                                showResult -> ui.wrong.copy(alpha = 0.5f)
                                else -> ui.ink.copy(alpha = 0.08f)
                            },
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("第${i + 1}空", color = ui.textSub, fontSize = 12.sp)
                    androidx.compose.material3.TextField(
                        value = values.getOrNull(i).orEmpty(),
                        onValueChange = { if (enabled) onValueChange(i, it) },
                        enabled = enabled,
                        singleLine = true,
                        placeholder = { Text("请输入第${i + 1}空的答案", color = ui.textSub.copy(alpha = 0.6f), fontSize = 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = ui.text, fontSize = 14.sp,
                            fontFamily = LocalReadingFont.current
                        ),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = ui.ink
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )
                }
                if (showResult) {
                    Text(
                        "可接受答案：${answers.getOrNull(i)?.joinToString(" / ").orEmpty()}",
                        color = ui.textSub, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 3.dp)
                    )
                }
            }
        }
    }
}

// ---------- 简答 ----------

/** 简答草稿输入（多行）；提交后锁定。 */
@Composable
fun ShortDraftField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String = "写下你的答案…"
) {
    val ui = LocalUi.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (enabled) onValueChange(it) },
        enabled = enabled,
        minLines = 3,
        maxLines = 6,
        placeholder = { Text(placeholder, color = ui.textSub.copy(alpha = 0.6f), fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = ui.text, fontSize = 14.sp, lineHeight = 21.sp,
            fontFamily = LocalReadingFont.current
        ),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ui.ink.copy(alpha = 0.35f),
            unfocusedBorderColor = ui.ink.copy(alpha = 0.12f),
            disabledBorderColor = ui.ink.copy(alpha = 0.08f),
            cursorColor = ui.ink,
            focusedContainerColor = ui.ink.copy(alpha = 0.03f),
            unfocusedContainerColor = ui.ink.copy(alpha = 0.03f),
            disabledContainerColor = ui.ink.copy(alpha = 0.03f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 简答参考答案 + 自评（用户选定口径：自评判分）。
 * graded 前展示「答对了/答错了」两个按钮；graded 后展示自评结论。
 */
@Composable
fun ShortReferenceCard(
    reference: String,
    yourText: String,
    graded: Boolean,
    selfCorrect: Boolean?,
    onGrade: (correct: Boolean) -> Unit
) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ui.ink.copy(alpha = 0.05f))
            .padding(14.dp)
    ) {
        Text("参考答案", color = ui.textSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            reference,
            color = ui.text,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (yourText.isNotBlank()) {
            Text(
                "你的回答：$yourText",
                color = ui.textSub, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (!graded) {
            Text(
                "对照参考答案，给自己打个分吧（计入统计）",
                color = ui.textSub, fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ui.correct.copy(alpha = 0.14f))
                        .border(1.dp, ui.correct.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable(interactionSource = null, indication = null) { onGrade(true) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("答对了", color = ui.correct, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ui.wrong.copy(alpha = 0.14f))
                        .border(1.dp, ui.wrong.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable(interactionSource = null, indication = null) { onGrade(false) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("答错了", color = ui.wrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    if (selfCorrect == true) AppIcons.Check else AppIcons.Close,
                    null,
                    tint = if (selfCorrect == true) ui.correct else ui.wrong,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    if (selfCorrect == true) "自评：答对了" else "自评：答错了",
                    color = if (selfCorrect == true) ui.correct else ui.wrong,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// ---------- 通用提交按钮 ----------

@Composable
fun SubmitAnswerButton(
    enabled: Boolean,
    hint: String = "提交答案",
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val ui = LocalUi.current
    GlassButton(
        onClick = onClick,
        backdrop = backdrop,
        surfaceColor = if (enabled) ui.ink else ui.ink.copy(alpha = 0.25f),
        heightDp = 46.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        isInteractive = enabled
    ) {
        Text(
            hint,
            color = if (enabled) ui.onInk else ui.textSub,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------- 错题/成绩条目里的参考答案（多题型） ----------

@Composable
fun WrongAnswerBlock(q: Question, userAnswer: String?) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            q.text,
            color = ui.text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium
        )
        if (userAnswer != null) {
            Text(
                "你的答案：$userAnswer",
                color = ui.wrong,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            "正确答案：${q.correctAnswerText()}",
            color = ui.correct,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (q.explanation.isNotBlank()) {
            Text(
                q.explanation,
                color = ui.textSub,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
