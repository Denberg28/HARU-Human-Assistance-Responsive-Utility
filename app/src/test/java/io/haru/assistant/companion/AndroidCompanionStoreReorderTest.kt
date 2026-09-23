package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCompanionStoreReorderTest {
    @Test
    fun reorderOpenTasksPersistsRequestedOrder() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_companion", 0)
            .edit()
            .clear()
            .commit()

        val store = AndroidCompanionStore(context)
        store.addTask("First")
        store.addTask("Second")
        store.addTask("Third")

        val reordered = store.reorderOpenTasks(listOf(2, 0, 1))

        assertEquals(
            listOf("Third", "First", "Second"),
            reordered.tasks.map { it.text },
        )
        assertEquals(
            listOf("Third", "First", "Second"),
            store.load().tasks.map { it.text },
        )
    }

    @Test
    fun reorderOpenTasksKeepsCompletedSlotsStable() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_companion", 0)
            .edit()
            .clear()
            .commit()

        val store = AndroidCompanionStore(context)
        store.addTask("First")
        store.addTask("Completed")
        store.addTask("Third")
        store.completeTask(1)

        val reordered = store.reorderOpenTasks(listOf(2, 0))

        assertEquals("Third", reordered.tasks[0].text)
        assertEquals("Completed", reordered.tasks[1].text)
        assertEquals("First", reordered.tasks[2].text)
        assertEquals(true, reordered.tasks[1].done)
    }
}
