package com.kyant.backdrop

import android.os.Build

fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
