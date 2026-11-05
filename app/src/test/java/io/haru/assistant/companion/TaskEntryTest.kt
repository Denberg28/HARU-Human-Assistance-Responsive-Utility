package io.haru.assistant.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class TaskEntryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val store get() = AndroidCompanionStore(context)

    @Test fun newTaskIsFirstAndKeepsExistingManualOrderAfterReload() {
        store.addTask("First")
        store.addTask("Second")
        val before = store.load().tasks.reversed()
        store.reorderOpenTasks(before.map { it.id })
        val added = store.addTask("  New   task  ").tasks
        assertEquals("New task", added.first().text)
        assertEquals(before, added.drop(1))
        assertEquals(added, store.load().tasks)
        assertTrue(store.load().todayLines().first().startsWith("Tasks: New task"))
        assertEquals("Focus · New task · +2", HaruCheckerContentFactory.lockScreenTaskLine(store.load()))
    }

    @Test fun taskActionsKeepTheirTargetAfterPrependAndReorder() {
        val selected = store.addTask("Selected").tasks.single()
        store.addTask("Another")
        store.updateTask(selected.id, "Edited")
        assertEquals(listOf("Another", "Edited"), store.load().tasks.map { it.text })
        store.reorderOpenTasks(store.load().tasks.reversed().map { it.id })
        store.completeTask(selected.id)
        assertTrue(store.load().tasks.single { it.id == selected.id }.done)
        store.deleteTask(selected.id)
        assertEquals(listOf("Another"), store.load().tasks.map { it.text })
        val remaining = store.load()
        store.updateTask(selected.id, "Stale edit")
        store.deleteTask(selected.id)
        store.completeTask(selected.id)
        assertEquals(remaining, store.load())
    }

    @Test fun capacityRetainsNewFirstEntryAndBlankInputDoesNotEvictAnything() {
        val raw = JSONArray()
        repeat(200) { index -> raw.put(JSONObject().put("id", "task-$index").put("text", "Task $index")) }
        context.getSharedPreferences("haru_companion", 0).edit().putString("tasks", raw.toString()).commit()
        val original = store.load()
        assertEquals(original, store.addTask(" \n\t "))
        val added = store.addTask("Newest").tasks
        assertEquals(200, added.size)
        assertEquals("Newest", added.first().text)
        assertEquals(original.tasks.take(199), added.drop(1))
        assertEquals(added, store.load().tasks)
    }

    @Test fun duplicateAndMissingLegacyIdsBecomeUniqueAndStayStable() {
        context.getSharedPreferences("haru_companion", 0).edit().putString("tasks",
            """[{"id":"same","text":"One"},{"id":"same","text":"Two"},{"text":"Three"}]""").commit()
        val tasks = store.load().tasks
        assertEquals(3, tasks.map { it.id }.toSet().size)
        assertEquals("same", tasks.first().id)
        assertEquals(tasks, store.load().tasks)
    }

    @Test fun staleOrDuplicateDragOrderDoesNotLoseNewTasks() {
        val old = store.addTask("Older").tasks.map { it.id }
        val snapshot = store.addTask("Newer")
        assertEquals(snapshot, store.reorderOpenTasks(old))
        assertEquals(snapshot, store.reorderOpenTasks(listOf(old.single(), old.single())))
    }
}
