package com.kyant.backdrop.internal

import android.os.Build
import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asAndroidRuntimeShader

internal fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
    }
}
