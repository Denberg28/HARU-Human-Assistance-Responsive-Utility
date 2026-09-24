package io.haru.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruThemePreferenceStoreTest {
    @Test
    fun defaultsToLavenderLightAndPersistsSelections() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_theme_preferences", 0)
            .edit()
            .clear()
            .commit()

        val store = HaruThemePreferenceStore(context)
        assertEquals(HaruThemeColor.LAVENDER, store.load())
        assertEquals(HaruBackgroundTheme.LIGHT, store.loadBackground())

        store.save(HaruThemeColor.GREEN)
        store.saveBackground(HaruBackgroundTheme.SEPIA)

        val reloaded = HaruThemePreferenceStore(context)
        assertEquals(HaruThemeColor.GREEN, reloaded.load())
        assertEquals(HaruBackgroundTheme.SEPIA, reloaded.loadBackground())
    }

    @Test
    fun invalidStoredValuesFallBackSafely() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_theme_preferences", 0)
            .edit()
            .putString("theme_color", "INVALID")
            .putString("background_theme", "INVALID")
            .commit()

        val store = HaruThemePreferenceStore(context)
        assertEquals(HaruThemeColor.LAVENDER, store.load())
        assertEquals(HaruBackgroundTheme.LIGHT, store.loadBackground())
    }
}
