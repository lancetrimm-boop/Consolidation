package com.example.ui.theme

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePreferencesTest {

    @Test
    fun testDefaultThemeMode_IsSystem() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ThemePreferences(context)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode.value)
    }

    @Test
    fun testSetThemeMode_PersistsValue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ThemePreferences(context)
        
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs.themeMode.value)

        val newPrefs = ThemePreferences(context)
        assertEquals(ThemeMode.DARK, newPrefs.themeMode.value)
    }
}
