package io.haru.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruThemePreferenceStoreTest {
    @Test
    fun defaultsToLavenderAndPersistsSelection() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_theme_preferences", 0)
            .edit()
            .clear()
            .commit()

        val store = HaruThemePreferenceStore(context)
        assertEquals(HaruThemeColor.LAVENDER, store.load())

        store.save(HaruThemeColor.GREEN)

        assertEquals(HaruThemeColor.GREEN, HaruThemePreferenceStore(context).load())
    }

    @Test
    fun invalidStoredValueFallsBackSafely() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_theme_preferences", 0)
            .edit()
            .putString("theme_color", "INVALID")
            .commit()

        assertEquals(
            HaruThemeColor.LAVENDER,
            HaruThemePreferenceStore(context).load(),
        )
    }
}
