package com.drone.quiz.ui.gooey

import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 果冻（Gooey）模式核心：RenderEffect 高斯模糊 + alpha 阈值切割。
 *
 * 原理（对齐 sinasamaki《5 Metaball Animations in Jetpack Compose》的
 * MetaContainer / MetaEntity 分层与 gooey.jakubantalik.com 的 liquid-gooey）：
 * - [GooeyContainer]（≈ MetaContainer）：对整层内容施加「blur → alpha 阈值」链式
 *   RenderEffect。层内元素彼此靠近时，blur 使 alpha 在间隙处互相渗出并超过阈值，
 *   切割后形成液态桥接（metaball 融合）；
 * - [GooeyItem]（≈ MetaEntity）：层内单个"液滴"元素（任意形状的色块/图形）；
 * - 不受融合影响的元素（文字、图标、列表内容）必须放在 [GooeyContainer] 之外，
 *   绝不能进模糊阈值——否则文字边缘会被糊掉（任务硬性约束）。
 *
 * API 兼容：
 * - API 33+：createChainEffect(阈值, blur)，阈值 = createRuntimeShaderEffect（AGSL 阶跃切割）；
 * - API 31–32：createChainEffect(阈值, blur)，阈值 = createColorFilterEffect(
 *   ColorMatrixColorFilter alpha 行高对比斜率，线性近似阶跃，slope 越大越接近 step）；
 * - 任何 RenderEffect 构建异常一律回退为无特效（绝不崩溃，与玻璃安全模式同一纪律）。
 *
 * 使用约束：
 * - goo 层必须比内部液滴的**运动/融合范围**大一圈（blur 需要扩散空间），
 *   层边界 = RenderEffect 作用边界，液滴超出层边界的部分会被裁掉；
 * - 只套容器级小组件（底栏选中胶囊 / Toggle 滑块 / Slider 拇指 / 按钮表面层 /
 *   选项勾选框），禁止整屏套用。
 */

/** 一次 gooey 渲染的全部参数。 */
data class GooeyParams(
    val blurPx: Float,
    val threshold: Float
)

/** gooey 默认参数与三档映射（与设置页 glassBlur 低/中/高对齐）。 */
object GooeyDefaults {
    /** 默认 alpha 阈值：低于它的像素被切透明，0.5 为起步手感。 */
    const val DEFAULT_THRESHOLD = 0.5f

    /**
     * glassBlur 三档 → (亚克力模糊 dp, goo blur px, goo threshold)。
     * 档位越高：亚克力越朦胧，goo 融合越夸张（threshold 越低、blur 越大）。
     */
    fun levelParams(level: Int, densityToPx: (Dp) -> Float): Triple<Float, Float, Float> =
        when (level) {
            0 -> Triple(densityToPx(6.dp), densityToPx(4.dp), 0.56f)
            2 -> Triple(densityToPx(12.dp), densityToPx(9.dp), 0.44f)
            else -> Triple(densityToPx(8.dp), densityToPx(6.dp), 0.5f)
        }
}

/** AGSL：对上游（模糊后）像素做 alpha 阶跃切割——metaball 融合的核心一刀。 */
private const val GOOEY_SRC = """
uniform shader composable;
uniform float threshold;

half4 main(float2 p) {
    half4 c = composable.eval(p);
    float a = float(c.a);
    c.a = half(step(threshold, a));
    return c;
}
"""

/**
 * 外层：alpha 阈值切割 RenderEffect（经 createChainEffect 链在内层 blur 之后应用）。
 * - API 33+：AGSL 阶跃（step）；
 * - API 31–32：ColorMatrixColorFilter 的 alpha 行高对比斜率线性近似（slope 越大越接近阶跃）。
 * 返回 null = 构建失败（渲染层异常），调用方回退无特效。
 */
private fun buildAlphaThresholdEffect(threshold: Float) = runCatching {
    if (Build.VERSION.SDK_INT >= 33) {
        val shader = android.graphics.RuntimeShader(GOOEY_SRC)
        shader.setFloatUniform("threshold", threshold)
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "composable")
    } else {
        // a' = slope * (a - threshold)，自动 clip 到 [0,1]
        val slope = 10f
        val matrix = android.graphics.ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, slope, -slope * threshold
            )
        )
        android.graphics.ColorMatrixColorFilter(matrix)
            .let { android.graphics.RenderEffect.createColorFilterEffect(it) }
    }
}.getOrNull()

/**
 * 系统开启「减弱动画」（动画时长缩放 = 0）时：只去掉果冻动效，亚克力表面保留。
 * 读取一次即可（该设置变更会重建 Activity 组合，不做监听）。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * gooey 融合容器（≈ MetaContainer）。
 *
 * 官方 RenderEffect.createChainEffect(outer, inner) 链式实现管线
 * 「内容 → blur(inner) → alpha 阈值(outer)」：层内液滴彼此靠近时，blur 使 alpha
 * 在间隙处互相渗出并超过阈值，切割后形成液态桥接（metaball 融合）。
 * 文字、图标等内容绝不能放进这里。
 *
 * @param blurPx    模糊半径（px）——gooeyness 强度（sinasamaki 的 MetaEntity blur 参数）
 * @param threshold alpha 阈值（0..1）：低于阈值的像素被切透明。越低融合越夸张
 * @param enabled   false 或系统「减弱动画」时退化为普通 Box（无任何 RenderEffect）
 */
@Composable
fun GooeyContainer(
    modifier: Modifier = Modifier,
    blurPx: Float = with(LocalDensity.current) { 6.dp.toPx() },
    threshold: Float = GooeyDefaults.DEFAULT_THRESHOLD,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val active = enabled && !reducedMotion && Build.VERSION.SDK_INT >= 31
    val effect = remember(blurPx, threshold, active) {
        if (!active) {
            null
        } else {
            runCatching {
                val blur = android.graphics.RenderEffect.createBlurEffect(
                    blurPx, blurPx, Shader.TileMode.CLAMP
                )
                val thresholdFx = buildAlphaThresholdEffect(threshold)
                    ?: error("threshold effect unavailable")
                // inner 先应用、outer 后应用：先模糊再阈值切割
                android.graphics.RenderEffect.createChainEffect(thresholdFx, blur)
                    .asComposeRenderEffect()
            }.getOrNull()
        }
    }
    Box(
        modifier.graphicsLayer {
            // effect 为 null 时显式置空，避免残留旧效果（同层复用场景）
            renderEffect = effect
        },
        content = content
    )
}

/**
 * goo 层内的单个液滴（≈ MetaEntity）：任意形状的实色块/图形包装。
 * 只做语义包装（容器），融合由外层 [GooeyContainer] 统一施加。
 */
@Composable
fun GooeyItem(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier, content = content)
}
