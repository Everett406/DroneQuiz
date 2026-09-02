package com.kyant.backdrop.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asAndroidRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

internal fun RenderEffect?.chain(other: RenderEffect): RenderEffect {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return other
    return if (this != null) {
        android.graphics.RenderEffect.createChainEffect(
            other.asAndroidRenderEffect(),
            this.asAndroidRenderEffect()
        ).asComposeRenderEffect()
    } else {
        other
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShaderEffect(
    runtimeShader: RuntimeShader,
    uniformShaderName: String
): RenderEffect {
    return android.graphics.RenderEffect.createRuntimeShaderEffect(
        runtimeShader.asAndroidRuntimeShader(),
        uniformShaderName
    ).asComposeRenderEffect()
}

internal fun ColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter
): RenderEffect {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return android.graphics.RenderEffect.createColorFilterEffect(
            colorFilter.asAndroidColorFilter()
        ).asComposeRenderEffect()
    }
    return if (renderEffect != null) {
        android.graphics.RenderEffect.createColorFilterEffect(
            colorFilter.asAndroidColorFilter(),
            renderEffect.asAndroidRenderEffect()
        ).asComposeRenderEffect()
    } else {
        android.graphics.RenderEffect.createColorFilterEffect(
            colorFilter.asAndroidColorFilter(),
        ).asComposeRenderEffect()
    }
}
