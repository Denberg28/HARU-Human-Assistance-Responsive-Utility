package io.haru.assistant.companion

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruCheckerStoreTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetPreferences() {
        context
            .getSharedPreferences(
                "haru_checker",
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(
                "haru_bubble",
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsEnabledAndPersistsToggle() {
        val store = HaruCheckerStore(context)
        assertTrue(store.isEnabled())

        store.setEnabled(false)
        assertFalse(HaruCheckerStore(context).isEnabled())

        store.setEnabled(true)
        assertTrue(HaruCheckerStore(context).isEnabled())
    }

    @Test
    fun readsLegacyBubblePreferenceWhenNewValueMissing() {
        context
            .getSharedPreferences(
                "haru_checker",
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()

        context
            .getSharedPreferences(
                "haru_bubble",
                Context.MODE_PRIVATE,
            )
            .edit()
            .putBoolean("enabled", false)
            .commit()

        assertFalse(HaruCheckerStore(context).isEnabled())
    }
}
