package com.drone.quiz.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.drone.quiz.BuildConfig
import com.drone.quiz.R
import com.drone.quiz.ServiceLocator
import com.drone.quiz.screens.common.ScreenTitle
import com.drone.quiz.screens.common.scrolledFromTopPx
import com.drone.quiz.screens.common.softTopFade
import com.drone.quiz.screens.common.SectionLabel
import com.drone.quiz.screens.common.SegmentedRow
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassButton
import com.drone.quiz.ui.glass.GlassCard
import com.drone.quiz.ui.glass.GlassToggle
import com.drone.quiz.ui.glass.GlassSlider
import com.drone.quiz.ui.glass.GlassConfirmDialog
import com.drone.quiz.ui.glass.BounceContainer
import com.drone.quiz.ui.theme.LocalReadingFont
import com.drone.quiz.ui.theme.LocalUi
import com.drone.quiz.ui.theme.ReadingFontOptions
import com.drone.quiz.ui.theme.readingFontOption
import com.drone.quiz.work.ReminderScheduler
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(backdrop: Backdrop) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by ServiceLocator.settings.settings.collectAsState(initial = com.drone.quiz.data.settings.AppSettings())

    var bankInfo by remember { mutableStateOf("加载中…") }
    var nameDraft by remember(settings.nickname) { mutableStateOf(settings.nickname) }
    val nameDirty = nameDraft.trim() != settings.nickname
    var importMsg by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // 通知权限：Android 13+ 系统弹窗请求；拒绝则静默不置位，不再展示说教文案
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                ServiceLocator.settings.setDailyNotify(true)
                ReminderScheduler.ensureChannel(context)
                ReminderScheduler.schedule(context)
            }
        }
    }

    fun enableDailyNotify() {
        scope.launch {
            ServiceLocator.settings.setDailyNotify(true)
            ReminderScheduler.ensureChannel(context)
            ReminderScheduler.schedule(context)
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val cats = ServiceLocator.repo.categories()
            val total = cats.sumOf { it.cnt }
            bankInfo = "共 $total 题 · ${cats.size} 个分类"
        }.onFailure { bankInfo = "题库为空，请导入" }
    }

    // 壁纸选择器：导入后复制到私有目录（重启/源文件删除后仍可用）。
    // v2.6.3 修复"更换壁纸不生效"：此前固定写 wallpaper.jpg，DataStore 路径字符串不变，
    // 背景层的 LaunchedEffect(settings.wallpaper) 不重触发，永远显示第一次的图；
    // 现在文件名带唯一时间戳（路径必变→必然重新解码），并在导入成功后清理旧文件。

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val dst = java.io.File(context.filesDir, "custom_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { input.copyTo(it) }
                    } ?: error("无法读取图片")
                    val old = settings.wallpaper
                    ServiceLocator.settings.setWallpaper(dst.absolutePath)
                    // 新路径已生效，清理旧文件（内置/导入均存私有目录）
                    if (old.startsWith(context.filesDir.absolutePath) && old != dst.absolutePath) {
                        java.io.File(old).delete()
                    }
                }
            }
        }
    }

    // 内置壁纸：选择后同样复制到私有目录（复用同一条加载链路，支持模糊/纱/折射）
    fun useBuiltinWallpaper(resId: Int, key: String) {
        scope.launch {
            runCatching {
                val dst = java.io.File(context.filesDir, "builtin_${key}_${System.currentTimeMillis()}.jpg")
                context.resources.openRawResource(resId).use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                }
                val old = settings.wallpaper
                ServiceLocator.settings.setWallpaper(dst.absolutePath)
                if (old.startsWith(context.filesDir.absolutePath) && old != dst.absolutePath) {
                    java.io.File(old).delete()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: error("无法读取文件")
                    ServiceLocator.repo.importBank(bytes)
                }.onSuccess { r ->
                    importMsg = "导入成功：${r.count} 题 / ${r.categories.size} 个分类"
                }.onFailure { e ->
                    importMsg = "导入失败：${e.message ?: "文件格式不正确"}"
                }
                runCatching {
                    val cats = ServiceLocator.repo.categories()
                    bankInfo = "共 ${cats.sumOf { it.cnt }} 题 · ${cats.size} 个分类"
                }
            }
        }
    }

    // 全局同款 iOS 回弹：与首页/错题本/刷题页一致（此前设置页是硬边界）
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- 固定标题（不随滚动；内容滚入时在其下缘柔化渐隐） ----
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenTitle("设置", "外观 / 刷题 / 数据", Modifier.padding(vertical = 16.dp))
        }

        // 标题柔化：draw 阶段直读滚动像素（Modifier 稳定无伪影，滑出渐显跟手）
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

        // ---- 外观 ----
        SectionLabel("外观")
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.padding(18.dp)) {
                // ---- 昵称（首页问候语用；默认不取名，只按时间问候；可自定义≤5字，确认生效） ----
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("昵称", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "首页会按时间向你问候",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        // v2.7.2：光标位置随文字起点（此前 TextAlign.End 导致光标顶到最右）；
                        // 宽度 140→112dp（用户反馈"太宽"）
                        Box(
                            Modifier
                                .width(112.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ui.ink.copy(alpha = if (ui.isDark) 0.10f else 0.05f))
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            BasicTextField(
                                value = nameDraft,
                                onValueChange = { nameDraft = it.take(5) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = ui.text, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = LocalReadingFont.current
                                ),
                                // 光标用正文墨色：accent 橙过扎眼（用户反馈）
                                cursorBrush = SolidColor(ui.text),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (nameDraft.isBlank()) {
                                Text(
                                    "未设置",
                                    color = ui.textSub.copy(alpha = 0.55f), fontSize = 14.sp,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }
                        }
                        // 有未保存修改时出现"确认"（显式生效，替代此前的防抖自动保存）
                        androidx.compose.animation.AnimatedVisibility(nameDirty) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ui.accent)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        // setNickname 是 suspend：clickable 普通上下文需挂协程（CI #4 教训）
                                        scope.launch {
                                            ServiceLocator.settings.setNickname(nameDraft.trim())
                                        }
                                        focusManager.clearFocus(force = true)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "确认",
                                    color = Color.White, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("主题", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (settings.themeMode) {
                                1 -> "浅色（奶油）"
                                2 -> "深色（暖夜）"
                                else -> "跟随系统"
                            },
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = listOf("跟随", "浅色", "深色"),
                        selectedIndex = settings.themeMode,
                        onSelect = { scope.launch { ServiceLocator.settings.setThemeMode(it) } },
                        modifier = Modifier.width(180.dp)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("字号", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf("小", "标准", "大", "特大").getOrElse(settings.fontLevel) { "标准" },
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = listOf("小", "标准", "大", "特大"),
                        selectedIndex = settings.fontLevel,
                        onSelect = { scope.launch { ServiceLocator.settings.setFontLevel(it) } },
                        modifier = Modifier.width(200.dp)
                    )
                }
                // ---- 阅读字体（v2.7.2 新增）：系统默认 + 三款内嵌阅读字体，切换全局生效 ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("阅读字体", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            readingFontOption(settings.readingFont).desc,
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = ReadingFontOptions.map { it.label },
                        selectedIndex = ReadingFontOptions
                            .indexOfFirst { it.id == settings.readingFont }
                            .coerceAtLeast(0),
                        onSelect = { i ->
                            scope.launch {
                                ServiceLocator.settings.setReadingFont(ReadingFontOptions[i].id)
                            }
                        },
                        modifier = Modifier.width(196.dp)
                    )
                }
                // 所见即所得：示例行用当前选中字体渲染（全局切换后整页即预览）
                Text(
                    "这是一段示例文本 Aa 0123",
                    color = ui.text,
                    fontSize = 14.sp,
                    fontFamily = LocalReadingFont.current,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("画面特效", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.effects) "液态玻璃折射 / 模糊（推荐）"
                            else "已关闭（省电，老旧设备更流畅）",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    GlassToggle(
                        checked = { settings.effects },
                        onCheckedChange = { v ->
                            scope.launch { ServiceLocator.settings.setEffects(v) }
                        },
                        backdrop = backdrop
                    )
                }
                // 底栏玻璃模糊度：三档可调
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("底栏玻璃模糊", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf("轻透", "适中", "朦胧").getOrElse(settings.glassBlur) { "适中" },
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = listOf("低", "中", "高"),
                        selectedIndex = settings.glassBlur,
                        onSelect = { scope.launch { ServiceLocator.settings.setGlassBlur(it) } },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }

        // ---- 刷题 ----
        SectionLabel("刷题", Modifier.padding(top = 16.dp))
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("题目顺序", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.practiceOrder == 1) "随机" else "顺序",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = listOf("顺序", "随机"),
                        selectedIndex = settings.practiceOrder,
                        onSelect = { scope.launch { ServiceLocator.settings.setPracticeOrder(it) } },
                        modifier = Modifier.width(150.dp)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("答对自动下一题", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.autoNext) "答对后自动跳转" else "手动切换",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    GlassToggle(
                        checked = { settings.autoNext },
                        onCheckedChange = { v ->
                            scope.launch { ServiceLocator.settings.setAutoNext(v) }
                        },
                        backdrop = backdrop
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("错题移除档位", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "连续答对 ${settings.removeThreshold} 次移除",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    SegmentedRow(
                        options = listOf("1次", "2次", "3次"),
                        selectedIndex = settings.removeThreshold - 1,
                        onSelect = {
                            scope.launch { ServiceLocator.settings.setRemoveThreshold(it + 1) }
                        },
                        modifier = Modifier.width(150.dp)
                    )
                }
                Column(Modifier.padding(top = 18.dp)) {
                    Text("及格分", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${settings.passScore} 分（50 – 95，步进 5）",
                        color = ui.textSub, fontSize = 12.sp
                    )
                    GlassSlider(
                        value = { settings.passScore.toFloat() },
                        onValueChange = { v ->
                            scope.launch { ServiceLocator.settings.setPassScore(v.roundToInt()) }
                        },
                        valueRange = 50f..95f,
                        step = 5f,
                        backdrop = backdrop,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        // ---- 全局壁纸（背景纹路，可模糊） ----
        SectionLabel("全局壁纸", Modifier.padding(top = 16.dp))
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (settings.wallpaper.isBlank()) "未设置 · 使用默认渐变"
                    else "已设置 · 作全局背景纹路",
                    color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "导入后作为各页面的背景质感；开启模糊后更含蓄、文字更易读",
                    color = ui.textSub, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                // ---- 内置纹路：点击即设为全局背景（复制进私有目录，支持模糊/纱/折射） ----
                val builtins = listOf(
                    Triple("山野清晨", R.drawable.wp_forest_light, "forest_light"),
                    Triple("林海深处", R.drawable.wp_forest_deep, "forest_deep"),
                    Triple("云上航线", R.drawable.wp_sky, "sky"),
                    Triple("黄昏原野", R.drawable.wp_dusk, "dusk")
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    builtins.forEach { (label, resId, key) ->
                        val selected = settings.wallpaper.contains("builtin_$key")
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (selected)
                                            Modifier.border(2.dp, ui.accent, RoundedCornerShape(14.dp))
                                        else Modifier
                                    )
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) { useBuiltinWallpaper(resId, key) }
                            ) {
                                Image(
                                    painter = painterResource(resId),
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            Text(
                                label,
                                color = if (selected) ui.accent else ui.textSub,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        onClick = { wallpaperPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        ) },
                        backdrop = backdrop,
                        surfaceColor = ui.ink,
                        heightDp = 40.dp
                    ) {
                        Text(
                            if (settings.wallpaper.isBlank()) "导入壁纸" else "更换壁纸",
                            color = ui.onInk, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (settings.wallpaper.isNotBlank()) {
                        GlassButton(
                            onClick = { scope.launch { ServiceLocator.settings.setWallpaper("") } },
                            backdrop = backdrop,
                            surfaceColor = ui.surface.copy(alpha = 0.6f),
                            heightDp = 40.dp
                        ) {
                            Text("恢复默认", color = ui.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (settings.wallpaper.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("壁纸模糊", color = ui.textSub, fontSize = 11.sp)
                            GlassToggle(
                                checked = { settings.wallpaperBlur },
                                onCheckedChange = { v ->
                                    scope.launch { ServiceLocator.settings.setWallpaperBlur(v) }
                                },
                                backdrop = backdrop
                            )
                        }
                    }
                }
            }
        }

        // ---- 提醒 ----
        SectionLabel("提醒", Modifier.padding(top = 16.dp))
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("每日提醒", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.dailyNotify) "已开启 · 刷过当天不打扰"
                            else "每天定时提醒 · 刷过不打扰",
                            color = ui.textSub, fontSize = 12.sp
                        )
                    }
                    GlassToggle(
                        checked = { settings.dailyNotify },
                        onCheckedChange = { v ->
                            when {
                                !v -> scope.launch {
                                    ServiceLocator.settings.setDailyNotify(false)
                                    ReminderScheduler.cancel(context)
                                }

                                Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED ->
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                                else -> enableDailyNotify()
                            }
                        },
                        backdrop = backdrop
                    )
                }
            }
        }

        // ---- 数据 ----
        SectionLabel("数据", Modifier.padding(top = 16.dp))
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Import, null, tint = ui.textSub, modifier = Modifier.size(18.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text("题库", color = ui.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(bankInfo, color = ui.textSub, fontSize = 12.sp)
                    }
                }
                importMsg?.let {
                    Text(
                        it,
                        color = if (it.startsWith("导入成功")) ui.correct else ui.wrong,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    "支持 JSON 格式：{\"questions\":[{\"category\":\"…\",\"type\":\"single|judge\"," +
                        "\"question\":\"…\",\"options\":[…],\"answer\":0,\"explanation\":\"…\"}]}，" +
                        "判断题无需 options。",
                    color = ui.textSub,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        backdrop = backdrop,
                        surfaceColor = ui.ink,
                        heightDp = 44.dp
                    ) {
                        Icon(AppIcons.Import, null, tint = ui.onInk, modifier = Modifier.size(16.dp))
                        Text("导入题库", color = ui.onInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    GlassButton(
                        onClick = { showClearConfirm = true },
                        backdrop = backdrop,
                        surfaceColor = ui.wrong.copy(alpha = 0.16f),
                        heightDp = 44.dp
                    ) {
                        Icon(AppIcons.Trash, null, tint = ui.wrong, modifier = Modifier.size(16.dp))
                        Text("清空记录", color = ui.wrong, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---- 关于 ----
        SectionLabel("关于", Modifier.padding(top = 16.dp))
        GlassCard(backdrop = backdrop, Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Bell, null, tint = ui.accent, modifier = Modifier.size(18.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text("题屿 v${BuildConfig.VERSION_NAME}", color = ui.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "液态玻璃 by Kyant0 backdrop · 离线本地题库",
                        color = ui.textSub, fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(130.dp))
        }
        
}
    }

    if (showClearConfirm) {
        GlassConfirmDialog(
            backdrop = backdrop,
            title = "清空做题记录？",
            body = "将清空刷题记录、统计、打卡、错题本与最近模考（含未完成的考试），题库保留。此操作不可撤销。",
            confirmText = "清空",
            dismissText = "取消",
            confirmColor = ui.wrong,
            onConfirm = {
                showClearConfirm = false
                scope.launch {
                    ServiceLocator.repo.clearAllRecords()
                    // 刷题进度快照（顺序+随机双槽）同属做题记录，一并清掉
                    runCatching { ServiceLocator.settings.setPracticeSession(null, 0) }
                    runCatching { ServiceLocator.settings.setPracticeSession(null, 1) }
                    importMsg = "记录已清空"
                }
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}
