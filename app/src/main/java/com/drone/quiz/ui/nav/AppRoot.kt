package com.drone.quiz.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drone.quiz.screens.ExamConfigScreen
import com.drone.quiz.screens.ExamResultScreen
import com.drone.quiz.screens.ExamScreen
import com.drone.quiz.screens.HomeScreen
import com.drone.quiz.screens.PracticeScreen
import com.drone.quiz.screens.SettingsScreen
import com.drone.quiz.screens.WrongBookScreen
import com.drone.quiz.ui.glass.AppIcons
import com.drone.quiz.ui.glass.GlassBottomTabs
import com.drone.quiz.ui.glass.TabIconSlot
import com.drone.quiz.ui.theme.LocalUi
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

object Routes {
    const val HOME = "home"
    const val PRACTICE_PATTERN = "practice?src={src}"
    const val PRACTICE = "practice"
    const val EXAM_CONFIG = "examConfig"
    const val EXAM_RUN_PATTERN = "examRun/{examId}"
    const val EXAM_RESULT_PATTERN = "examResult/{examId}"
    const val WRONG = "wrong"
    const val SETTINGS = "settings"

    /** Tab 页对应的 destination route（practice 的 destination route 是 pattern 形式） */
    val tabDestinations = listOf(HOME, PRACTICE_PATTERN, EXAM_CONFIG, WRONG, SETTINGS)

    /** Tab 顺序对应的导航目标 */
    val tabTargets = listOf(HOME, PRACTICE, EXAM_CONFIG, WRONG, SETTINGS)
}

@Composable
fun AppRoot(settings: com.drone.quiz.data.settings.AppSettings) {
    val ui = LocalUi.current
    val navController: NavHostController = rememberNavController()

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

    Box(Modifier.fillMaxSize()) {
        // 层 1：背景渐变（液态玻璃的"壁纸"采样源）
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(bgBackdrop)
                .background(ui.bgGradient)
        )

        // 层 2：内容层（透明背景，浮于背景之上；同时作为底栏折射的内容源）
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(contentBackdrop)
        ) {
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
                        onSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.PRACTICE_PATTERN) { entry ->
                    val src = entry.arguments?.getString("src") ?: "all"
                    PracticeScreen(backdrop = bgBackdrop, src = src)
                }
                composable(Routes.EXAM_CONFIG) {
                    ExamConfigScreen(
                        backdrop = bgBackdrop,
                        onStart = { examId ->
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
                        onExit = { navController.popBackStack() }
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
                            navController.navigate("practice?src=wrong") { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(backdrop = bgBackdrop)
                }
            }
        }

        // 浮动玻璃底栏（仅 Tab 页显示；离场下滑独立动画，不与页面转场叠加）
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
                val slot: @Composable RowScope.(Int) -> Unit = { i ->
                    when (i) {
                        0 -> TabIconSlot(0, AppIcons.Home)
                        1 -> TabIconSlot(1, AppIcons.Cards)
                        2 -> TabIconSlot(2, AppIcons.Timer)
                        3 -> TabIconSlot(3, AppIcons.BookWrong)
                        else -> TabIconSlot(4, AppIcons.Tune)
                    }
                }
                if (index == -1) {
                    repeat(5) { i -> slot(i) }
                } else {
                    slot(index)
                }
            }
        }
    }
}
