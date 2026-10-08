package com.example.ui.screens

import android.graphics.Bitmap
import com.example.util.MediaOrientationUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairwiseOrientationTest {

    @Test
    fun testNormalizeRotation_StandardDegrees() {
        assertEquals(0, MediaOrientationUtils.normalizeRotation(0))
        assertEquals(90, MediaOrientationUtils.normalizeRotation(90))
        assertEquals(180, MediaOrientationUtils.normalizeRotation(180))
        assertEquals(270, MediaOrientationUtils.normalizeRotation(270))
        assertEquals(0, MediaOrientationUtils.normalizeRotation(360))
        assertEquals(270, MediaOrientationUtils.normalizeRotation(-90))
    }

    @Test
    fun testShouldSwapDimensions_PortraitOrientations() {
        assertTrue(MediaOrientationUtils.shouldSwapDimensions(90))
        assertTrue(MediaOrientationUtils.shouldSwapDimensions(270))
        assertFalse(MediaOrientationUtils.shouldSwapDimensions(0))
        assertFalse(MediaOrientationUtils.shouldSwapDimensions(180))
    }

    @Test
    fun testRotateBitmapIfNeeded_IdentityOnZeroDegrees() {
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        val rotated = MediaOrientationUtils.rotateBitmapIfNeeded(bitmap, 0)
        assertSame(bitmap, rotated)
        assertEquals(100, rotated.width)
        assertEquals(200, rotated.height)
    }
}
