package com.drone.quiz.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drone.quiz.ServiceLocator
import com.drone.quiz.data.repo.BankImport
import com.drone.quiz.data.repo.ImportPreview
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassBottomSheet
import com.drone.quiz.ui.glass.GlassConfirmDialog
import com.drone.quiz.ui.theme.LocalUi
import com.drone.quiz.util.GallerySave
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 题库导入底部弹窗（v2.8.0）：JSON / CSV 双通道 + 逐行校验报告 + 命名确认。 */
@Composable
fun BankImportSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onImported: (bankId: String, name: String, count: Int) -> Unit
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf("") }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var showTemplate by remember { mutableStateOf(false) }
    var templateSaved by remember { mutableStateOf<String?>(null) }

    fun reset() {
        preview = null; sourceName = ""; nameDraft = ""; importMsg = null
    }

    fun handleBytes(bytes: ByteArray, fileName: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BankImport.parse(bytes) }
            }
            result.onSuccess { p ->
                preview = p
                sourceName = fileName.substringBeforeLast('.')
                nameDraft = sourceName
                importMsg = null
            }.onFailure { e ->
                importMsg = "解析失败：${e.message ?: "文件格式不正确"}"
            }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes == null) importMsg = "无法读取文件"
                else handleBytes(bytes, uri.lastPathSegment ?: "bank.json")
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes == null) importMsg = "无法读取文件"
                else handleBytes(bytes, uri.lastPathSegment ?: "bank.csv")
            }
        }
    }

    GlassBottomSheet(
        visible = visible,
        backdrop = backdrop,
        onDismiss = {
            onDismiss()
            reset()
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // v2.8.2：键盘避让（此前命名输入框被键盘完全遮挡，弹窗不上移，用户反馈）
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // 抓手由 GlassBottomSheet 自带（v2.8.2 删去重复把手）
            Text(
                if (preview == null) "导入题库" else "导入预览",
                color = ui.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (preview == null) {
                Text(
                    "支持 JSON 或 CSV（UTF-8）。导入会成为独立题库，随时可在题库管理中切换。",
                    color = ui.textSub, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImportOptionCard(
                        title = "JSON 文件",
                        desc = "{\"questions\":[…]} 结构",
                        modifier = Modifier.weight(1f),
                        backdrop = backdrop
                    ) { jsonLauncher.launch(arrayOf("application/json", "text/plain")) }
                    ImportOptionCard(
                        title = "CSV 表格",
                        desc = "Excel / AI 生成的表格",
                        modifier = Modifier.weight(1f),
                        backdrop = backdrop
                    ) { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/plain;charset=utf-8", "application/csv")) }
                }

                Text(
                    "想做自己的题库？把学习内容丢给 AI，让它按模板整理成 CSV 即可。",
                    color = ui.textSub, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        onClick = { showTemplate = true },
                        backdrop = backdrop,
                        surfaceColor = ui.surface.copy(alpha = 0.6f),
                        heightDp = 42.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("查看 CSV 模板", color = ui.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GlassButton(
                        onClick = {
                            // v2.8.2：改用系统分享（可发微信/文件助手等，零基础友好），
                            // 替代原先只存到「下载/题屿」的方式（用户反馈）
                            val ok = GalleryShare.shareTextFile(
                                context, "tiyu_bank_template.csv", CSV_TEMPLATE
                            )
                            if (!ok) templateSaved = "分享失败，可试复制模板"
                        },
                        backdrop = backdrop,
                        surfaceColor = ui.surface.copy(alpha = 0.6f),
                        heightDp = 42.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            templateSaved ?: "分享给好友 / 电脑",
                            color = if (templateSaved?.startsWith("分享失败") == true) ui.wrong else ui.text,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                importMsg?.let {
                    Text(
                        it,
                        color = ui.wrong, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                val p = preview!!
                // 导入报告
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Check, null, tint = ui.correct, modifier = Modifier.size(16.dp))
                    Text(
                        " 识别成功 ${p.ok.size} 题",
                        color = ui.correct, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    if (p.errors.isNotEmpty()) {
                        Text(
                            " · 失败 ${p.errors.size} 行",
                            color = ui.wrong, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (p.errors.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ui.wrong.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        p.errors.take(8).forEach { err ->
                            Text(
                                err,
                                color = ui.wrong, fontSize = 11.sp, lineHeight = 15.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                        if (p.errors.size > 8) {
                            Text(
                                "…以及另外 ${p.errors.size - 8} 行失败",
                                color = ui.wrong.copy(alpha = 0.8f), fontSize = 11.sp
                            )
                        }
                    }
                }
                // 分类摘要
                if (p.categories.isNotEmpty()) {
                    Text(
                        "分类：${p.categories.joinToString("、")}",
                        color = ui.textSub, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // 题库名称
                Text(
                    "题库名称",
                    color = ui.textSub, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ui.ink.copy(alpha = 0.35f),
                        unfocusedBorderColor = ui.ink.copy(alpha = 0.12f),
                        cursorColor = ui.ink,
                        focusedContainerColor = ui.ink.copy(alpha = 0.03f),
                        unfocusedContainerColor = ui.ink.copy(alpha = 0.03f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        onClick = { reset() },
                        backdrop = backdrop,
                        surfaceColor = ui.surface.copy(alpha = 0.6f),
                        heightDp = 48.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重选文件", color = ui.textSub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GlassButton(
                        onClick = {
                            if (!importing && p.ok.isNotEmpty() && nameDraft.isNotBlank()) {
                                importing = true
                                scope.launch {
                                    runCatching {
                                        ServiceLocator.repo.importParsedBank(nameDraft.trim(), p)
                                    }.onSuccess { bankId ->
                                        onImported(bankId, nameDraft.trim(), p.ok.size)
                                        reset()
                                    }.onFailure { e ->
                                        importMsg = "导入失败：${e.message ?: "未知错误"}"
                                    }
                                    importing = false
                                }
                            }
                        },
                        backdrop = backdrop,
                        surfaceColor = if (p.ok.isNotEmpty() && nameDraft.isNotBlank()) ui.ink else ui.ink.copy(alpha = 0.25f),
                        heightDp = 48.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (importing) "导入中…" else "确认导入",
                            color = if (p.ok.isNotEmpty() && nameDraft.isNotBlank()) ui.onInk else ui.textSub,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                importMsg?.let {
                    Text(it, color = ui.wrong, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }

    if (showTemplate) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "CSV 题库模板",
            body = CSV_TEMPLATE,
            confirmText = "复制模板",
            dismissText = "关闭",
            onConfirm = {
                showTemplate = false
                clipboard.setText(AnnotatedString(CSV_TEMPLATE))
            },
            onDismiss = { showTemplate = false }
        )
    }
}

@Composable
private fun ImportOptionCard(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val ui = LocalUi.current
    GlassButton(
        onClick = onClick,
        backdrop = backdrop,
        surfaceColor = ui.ink.copy(alpha = 0.06f),
        heightDp = 76.dp,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.Import, null, tint = ui.text, modifier = Modifier.size(18.dp))
            Text(
                title,
                color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(desc, color = ui.textSub, fontSize = 10.sp)
        }
    }
}

/** CSV 模板：与《无人机装调题库_清洗版_含解析.csv》同构，并示范多选/填空/简答。 */
const val CSV_TEMPLATE = """题号,题型,题干,选项A,选项B,选项C,选项D,答案,解析,备注
1,单选,时间压力可能会导致人为差错，是因为____。,人的工作速度不比机器,期限是一种激励方式,可能会因为赶时间而出错,,C,选C。赶时间会压缩人的检查与思考流程。,备注可留空
2,多选,下列属于无人机安全飞行做法的有____。,避开人群上空,雨天户外飞行,保持视距内飞行,远离机场净空区,"ACD",多选题答案写字母（全对才算对），至少两个。,
3,判断,植保无人机可以用来电力巡线、航拍摄影。,对,错,,,错,判断题：选项A填「对」、选项B填「错」，答案写「对/错/正确/错误」均可。,
4,填空,水的化学式是____，一个水分子中含有____个氢原子。,,,,,"H2O||2",填空：题干用 ____ 占位（3个以上下划线）；多空用 || 分隔；一空多个可接受写法用 | 分隔（如：汉|漢）。,
5,简答,请简述什么是视距内（VLOS）飞行。,,,,,指驾驶员或观测员在目视距离范围内操纵和观察无人机的飞行方式。,简答题需填写参考答案；作答后对照自评，计入正确率。,
"""
