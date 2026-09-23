package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCompanionStoreReorderTest {
    @Test
    fun taskIdsPersistAcrossReloads() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_companion", 0)
            .edit()
            .clear()
            .commit()

        val store = AndroidCompanionStore(context)
        val first = store.addTask("Stable")
        val id = first.tasks.single().id

        assertEquals(id, store.load().tasks.single().id)
    }

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

        val before = store.load()
        val reordered =
            store.reorderOpenTasks(
                listOf(
                    before.tasks[2].id,
                    before.tasks[0].id,
                    before.tasks[1].id,
                )
            )

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

        val before = store.load()
        val reordered =
            store.reorderOpenTasks(
                listOf(
                    before.tasks[2].id,
                    before.tasks[0].id,
                )
            )

        assertEquals("Third", reordered.tasks[0].text)
        assertEquals("Completed", reordered.tasks[1].text)
        assertEquals("First", reordered.tasks[2].text)
        assertEquals(true, reordered.tasks[1].done)
    }
}
