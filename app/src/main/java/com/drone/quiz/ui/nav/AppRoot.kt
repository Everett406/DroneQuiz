package com.drone.quiz.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drone.quiz.ServiceLocator
import com.drone.quiz.screens.ExamConfigScreen
import com.drone.quiz.screens.ExamResultScreen
import com.drone.quiz.screens.ExamScreen
import com.drone.quiz.screens.HomeScreen
import com.drone.quiz.screens.PracticeConfigScreen
import com.drone.quiz.screens.PracticeRunScreen
import com.drone.quiz.screens.SearchScreen
import com.drone.quiz.screens.SettingsScreen
import com.drone.quiz.screens.WrongBookScreen
import com.drone.quiz.screens.common.LocalNavAnimatedVisibilityScope
import com.drone.quiz.screens.common.LocalSharedTransitionScope
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassBottomTabs
import com.drone.quiz.ui.glass.GlassOverlayPortal
import com.drone.quiz.ui.glass.LocalBgBackdrop
import com.drone.quiz.ui.glass.LocalContentBackdrop
import com.drone.quiz.ui.glass.OverlayBlur
import com.drone.quiz.ui.glass.TabIconSlot
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val PRACTICE_PATTERN = "practice?src={src}"
    const val PRACTICE = "practice"
    const val PRACTICE_RUN_PATTERN =
        "practiceRun?src={src}&type={type}&cat={cat}&resume={resume}"
    const val EXAM_CONFIG = "examConfig"
    const val EXAM_RUN_PATTERN = "examRun/{examId}"
    const val EXAM_RESULT_PATTERN = "examResult/{examId}"
    const val WRONG = "wrong"
    const val SETTINGS = "settings"
    const val SEARCH = "search"

    /** Tab 页对应的 destination route（practice 的 destination route 是 pattern 形式） */
    val tabDestinations = listOf(HOME, PRACTICE_PATTERN, EXAM_CONFIG, WRONG, SETTINGS)

    /** Tab 顺序对应的导航目标 */
    val tabTargets = listOf(HOME, PRACTICE, EXAM_CONFIG, WRONG, SETTINGS)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(settings: com.drone.quiz.data.settings.AppSettings) {
    val ui = LocalUi.current
    val navController: NavHostController = rememberNavController()
    val appScope = rememberCoroutineScope()

    // 双记录层架构（对齐 Kyant0 官方 demo）:
    // 1. bgBackdrop —— 只记录背景渐变（"壁纸"）。内容流玻璃卡片折射它，
    //    卡片不在该记录层内 → 无循环采样，无 SIGSEGV 风险。
    // 2. contentBackdrop —— 记录 NavHost 内容。底栏折射"背景+内容"，
    //    底栏在其记录层之外 → 安全。
    val bgBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabIndex = Routes.tabDestinations.indexOf(route)
    val isTabRoute = tabIndex >= 0

    fun navigateTab(index: Int) {
        if (index == tabIndex) return
        val target = Routes.tabTargets[index]
        navController.navigate(target) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 弹窗打开时内容层真模糊（iOS 风格），替代深色遮罩。
    // 弹窗面板自身渲染在 PortalHost（模糊区之外），不再被连帶模糊。
    val overlayBlur by animateDpAsState(
        targetValue = if (OverlayBlur.active) 16.dp else 0.dp,
        animationSpec = tween(220),
        label = "overlayBlur"
    )

    CompositionLocalProvider(
        LocalBgBackdrop provides bgBackdrop,
        LocalContentBackdrop provides contentBackdrop
    ) {
        Box(Modifier.fillMaxSize()) {
        // 层 1：背景（默认渐变 / 可选全局壁纸作"纹路"，可模糊）
        var wallBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        androidx.compose.runtime.LaunchedEffect(settings.wallpaper) {
            wallBmp = if (settings.wallpaper.isBlank()) null
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(settings.wallpaper)?.asImageBitmap()
                }.getOrNull()
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(bgBackdrop)
                .background(ui.bgGradient)
        ) {
            val bmp = wallBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .then(if (settings.wallpaperBlur) Modifier.blur(24.dp) else Modifier)
                )
                // 主题纱：字体颜色自适应的等价实现——任意壁纸上保证文字对比
                // （亮色叠米白纱、深色叠墨色纱；开启模糊后壁纸干扰更低，纱可更透）
                val scrim by animateColorAsState(
                    targetValue = if (ui.isDark)
                        Color(0xFF161310).copy(alpha = if (settings.wallpaperBlur) 0.40f else 0.54f)
                    else
                        Color(0xFFF6F1E6).copy(alpha = if (settings.wallpaperBlur) 0.36f else 0.50f),
                    animationSpec = tween(300),
                    label = "wallScrim"
                )
                Box(Modifier.matchParentSize().background(scrim))
            }
        }

        // 层 2：内容层（透明背景，浮于背景之上；同时作为底栏折射的内容源）
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(contentBackdrop)
                .blur(overlayBlur)
        ) {
            SharedTransitionLayout {
            // Hero 根因修复：scope 从未 provide → heroSearchField() 永远走
            // "current==null 则原样返回"的兜底，sharedElement 从未挂上（v2.7.0 病灶）
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                // 统一转场：淡入 + 轻缩放，单一动画源，杜绝重叠
                enterTransition = {
                    fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                },
                exitTransition = {
                    fadeOut(tween(200)) + scaleOut(targetScale = 0.97f, animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280))
                },
                popExitTransition = {
                    fadeOut(tween(200)) + scaleOut(targetScale = 0.97f, animationSpec = tween(200))
                }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        backdrop = bgBackdrop,
                        onPractice = { navigateTab(1) },
                        onExam = { navigateTab(2) },
                        onWrong = { navigateTab(3) },
                        // 与底栏切 tab 行为完全一致（保存页面状态、栈可预测）
                        onSettings = { navigateTab(4) }
                    )
                }
                // 刷题 Tab：配置入口页（选范围/分类后进入全屏刷题，不再直接刷）。
                // 此 route 是 tab destination（有底栏），只承载配置页；
                // 全屏刷题一律走 practiceRun（非 tab route，无底栏遮挡）
                composable(Routes.PRACTICE_PATTERN) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    PracticeConfigScreen(
                        backdrop = bgBackdrop,
                        onSearch = {
                            navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                        },
                        onStart = { src2, type, cat, resume ->
                            val catEnc = android.net.Uri.encode(cat)
                            navController.navigate(
                                "practiceRun?src=$src2&type=$type&cat=$catEnc&resume=$resume"
                            ) { launchSingleTop = true }
                        }
                    )
                    }
                }
                // 题目搜索（题干/选项/解析全文检索）
                composable(Routes.SEARCH) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    SearchScreen(
                        backdrop = bgBackdrop,
                        onBack = { navController.popBackStack() }
                    )
                    }
                }
                // 全屏刷题页（非 Tab destination → 无底栏遮挡；返回即回到配置页）
                composable(Routes.PRACTICE_RUN_PATTERN) { entry ->
                    val src = entry.arguments?.getString("src") ?: "all"
                    val type = entry.arguments?.getString("type") ?: "all"
                    val cat = entry.arguments?.getString("cat") ?: "all"
                    val resume = entry.arguments?.getString("resume") == "true"
                    PracticeRunScreen(
                        backdrop = bgBackdrop,
                        src = src,
                        type = type,
                        cat = cat,
                        resume = resume,
                        onExit = { navController.popBackStack() }
                    )
                }
                composable(Routes.EXAM_CONFIG) {
                    ExamConfigScreen(
                        backdrop = bgBackdrop,
                        onStart = { examId ->
                            navController.navigate("examRun/$examId") { launchSingleTop = true }
                        },
                        onResumeExam = { examId ->
                            navController.navigate("examRun/$examId") { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.EXAM_RUN_PATTERN) { entry ->
                    val examId = entry.arguments?.getString("examId")?.toLongOrNull() ?: 0L
                    ExamScreen(
                        backdrop = bgBackdrop,
                        examId = examId,
                        onSubmit = { id ->
                            navController.navigate("examResult/$id") {
                                popUpTo(Routes.EXAM_CONFIG)
                                launchSingleTop = true
                            }
                        },
                        // 放弃考试：删除该次模考记录（此前只退页面，DB 残留"进行中"记录）
                        onExit = {
                            appScope.launch {
                                runCatching { ServiceLocator.repo.abandonExam(examId) }
                            }
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.EXAM_RESULT_PATTERN) { entry ->
                    val examId = entry.arguments?.getString("examId")?.toLongOrNull() ?: 0L
                    ExamResultScreen(
                        backdrop = bgBackdrop,
                        examId = examId,
                        onHome = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onWrong = {
                            navController.navigate(Routes.WRONG) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.WRONG) {
                    WrongBookScreen(
                        backdrop = bgBackdrop,
                        onPractice = {
                            navController.navigate(
                                "practiceRun?src=wrong&type=all&cat=all&resume=false"
                            ) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(backdrop = bgBackdrop)
                }
            }
            }
            }
        }

        // 浮动玻璃底栏（仅 Tab 页显示；离场下滑独立动画，不与页面转场叠加）
        // 注：不给底栏套 Modifier.blur——方形 blur 层会在胶囊四周留下方框裁剪痕迹，
        // 底栏玻璃本身折射的就是已模糊的内容层，视觉已一致
        AnimatedVisibility(
            visible = isTabRoute,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(
                    bottom = 10.dp + WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                ),
            enter = slideInVertically(
                animationSpec = tween(260),
                initialOffsetY = { it }
            ) + fadeIn(tween(260)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it }
            ) + fadeOut(tween(180))
        ) {
            GlassBottomTabs(
                selectedTabIndex = { tabIndex.coerceAtLeast(0) },
                onTabSelected = { index -> navigateTab(index) },
                // 折射"背景 + 滚动内容"：内容从底栏下方滑过时透过玻璃可见
                backdrop = rememberCombinedBackdrop(bgBackdrop, contentBackdrop),
                tabsCount = 5
            ) { index ->
                // index == -1：玻璃底胶囊与隐藏染色层要求渲染"整排 5 个图标位"（官方约定）；
                // 此前 -1 误入 else 分支只渲染了一个全宽 Tune 图标，导致底栏 5 个图标全部消失
                // 单击 tab 即切换（官方 LiquidBottomTab 同款 Role.Tab），拖拽选中块仍可用
                val slot: @Composable RowScope.(Int) -> Unit = { i ->
                    when (i) {
                        0 -> TabIconSlot(0, AppIcons.Home) { navigateTab(0) }
                        1 -> TabIconSlot(1, AppIcons.Cards) { navigateTab(1) }
                        2 -> TabIconSlot(2, AppIcons.Timer) { navigateTab(2) }
                        3 -> TabIconSlot(3, AppIcons.BookWrong) { navigateTab(3) }
                        else -> TabIconSlot(4, AppIcons.Tune) { navigateTab(4) }
                    }
                }
                if (index == -1) {
                    repeat(5) { i -> slot(i) }
                } else {
                    slot(index)
                }
            }
        }

        // 层 4：弹窗传送门宿主（内容模糊区 + 底栏之上）。
        // 面板在此渲染 → 永远清晰；独立读作用域，避免弹窗高频重组拖动整个 AppRoot。
        PortalHost()
    }
    }
}

/** 弹窗传送门宿主：单独提取以隔离 GlassOverlayPortal.entries 的读取重组范围。 */
@Composable
private fun PortalHost() {
    GlassOverlayPortal.entries.forEach { entry ->
        // key 稳定槽位：列表增减时不串位、不丢 remember 状态
        key(entry.id) { entry.content() }
    }
}
