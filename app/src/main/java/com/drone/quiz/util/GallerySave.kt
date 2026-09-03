package com.drone.quiz.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/**
 * 收款码保存到相册（MediaStore Pictures/题屿）。
 * minSdk 31：MediaStore 写入无需任何存储权限。
 */
object GallerySave {

    /** @return true = 保存成功 */
    fun savePngFromRaw(context: Context, rawRes: Int, displayName: String): Boolean =
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/题屿")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                context.resources.openRawResource(rawRes).copyTo(out)
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null, null
            )
            true
        }.getOrDefault(false)
}
