#!/usr/bin/env python3
"""Vendor Kyant0/AndroidLiquidGlass (backdrop) library sources into the app.
Merges commonMain + androidMain for a single Android source set.
Apache-2.0 (c) Kyant0 — vendored per license.
"""
import os, re, shutil, sys

SRC = "/home/z/tools/research/alG/backdrop/src"
DST = "/home/z/my-project/DroneQuiz/app/src/main/java"

def cp(rel):
    src = os.path.join(SRC, "commonMain/kotlin", rel)
    dst = os.path.join(DST, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy(src, dst)

# 1. copy all commonMain files
base = os.path.join(SRC, "commonMain/kotlin/com/kyant/backdrop")
for root, dirs, files in os.walk(base):
    for f in files:
        full = os.path.join(root, f)
        rel = os.path.relpath(full, os.path.join(SRC, "commonMain/kotlin"))
        cp(rel)

# 2. overwrite expect/actual files with merged Android-only versions
PLATFORM = '''package com.kyant.backdrop

import android.os.Build

fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
'''

RUNTIME_SHADER = '''package com.kyant.backdrop

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb

interface RuntimeShader {

    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)

    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)

    fun setColorUniform(name: String, color: Color)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun createRuntimeShader(shaderString: String): RuntimeShader {
    return AndroidRuntimeShader(android.graphics.RuntimeShader(shaderString))
}

fun RuntimeShader.asComposeShader(): Shader {
    return asAndroidRuntimeShader()
}

fun RuntimeShader.asAndroidRuntimeShader(): android.graphics.RuntimeShader {
    return (this as AndroidRuntimeShader).shader
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidRuntimeShader(val shader: android.graphics.RuntimeShader) : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) {
        shader.setFloatUniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        shader.setFloatUniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        shader.setFloatUniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        shader.setFloatUniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        shader.setFloatUniform(name, values)
    }

    override fun setIntUniform(name: String, value: Int) {
        shader.setIntUniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        shader.setIntUniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        shader.setIntUniform(name, value1, value2, value3)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        shader.setIntUniform(name, value1, value2, value3, value4)
    }

    override fun setIntUniform(name: String, values: IntArray) {
        shader.setIntUniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        shader.setColorUniform(name, color.toArgb())
    }
}
'''

PAINT = '''package com.kyant.backdrop.internal

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
'''

RENDER_EFFECT = '''package com.kyant.backdrop.internal

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
'''

def w(rel, content):
    path = os.path.join(DST, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)

w("com/kyant/backdrop/Platform.kt", PLATFORM)
w("com/kyant/backdrop/RuntimeShader.kt", RUNTIME_SHADER)
w("com/kyant/backdrop/internal/Paint.kt", PAINT)
w("com/kyant/backdrop/internal/RenderEffect.kt", RENDER_EFFECT)

# 3. strip org.intellij Language annotations everywhere
count = 0
for root, dirs, files in os.walk(os.path.join(DST, "com/kyant/backdrop")):
    for f in files:
        if not f.endswith(".kt"):
            continue
        p = os.path.join(root, f)
        t = open(p).read()
        t2 = re.sub(r'import org\.intellij\.lang\.annotations\.Language\n', '', t)
        t2 = re.sub(r'@Language\("AGSL"\)\s*', '', t2)
        if t2 != t:
            open(p, 'w').write(t2)
            count += 1
print(f"stripped annotations in {count} files")

# 4. copy catalog utils (interaction primitives) with small adaptations
UTILS = "/home/z/tools/research/alG/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils"
UDST = os.path.join(DST, "com/kyant/backdrop/catalog/utils")
os.makedirs(UDST, exist_ok=True)
for f in ["DampedDragAnimation.kt", "InteractiveHighlight.kt", "DragGestureInspector.kt"]:
    t = open(os.path.join(UTILS, f)).read()
    t = t.replace("import kotlin.time.Clock\n", "")
    t = t.replace("Clock.System.now().toEpochMilliseconds()", "System.currentTimeMillis()")
    t = t.replace("RuntimeShader(", "createRuntimeShader(")
    open(os.path.join(UDST, f), 'w').write(t)

# 5. summary
total = 0
bad = []
for root, dirs, files in os.walk(os.path.join(DST, "com/kyant/backdrop")):
    for f in files:
        if f.endswith('.kt'):
            total += 1
            t = open(os.path.join(root, f)).read()
            if re.search(r'^\s*(expect|actual)\s+', t, re.M):
                bad.append(f)
print(f"vendored {total} kotlin files; leftover expect/actual:", bad if bad else "none")
