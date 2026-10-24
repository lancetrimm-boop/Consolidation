package com.example.util

import android.graphics.Bitmap
import android.graphics.Matrix

object MediaOrientationUtils {

    fun normalizeRotation(degrees: Int): Int {
        val mod = degrees % 360
        return if (mod < 0) mod + 360 else mod
    }

    fun shouldSwapDimensions(rotationDegrees: Int): Boolean {
        val normalized = normalizeRotation(rotationDegrees)
        return normalized == 90 || normalized == 270
    }

    fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = normalizeRotation(rotationDegrees)
        if (normalized == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(normalized.toFloat())
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
