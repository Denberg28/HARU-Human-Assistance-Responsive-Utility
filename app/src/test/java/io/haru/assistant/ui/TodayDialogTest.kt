package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.haru.assistant.companion.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyTaskAndUndeliveredReminderCanBeReached() {
        val snapshot = CompanionSnapshot(
            tasks = (1..20).map { CompanionTask("Task $it") },
            reminders = (1..6).map { CompanionReminder("r$it", "Reminder $it", 1L) },
        )
        compose.setContent {
            MaterialTheme { SimpleTodayDialog(snapshot, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> }) }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Task 20"))
        compose.onNodeWithText("Task 20").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Due · Reminder 6", substring = true))
        compose.onNodeWithText("Due · Reminder 6", substring = true).assertIsDisplayed()
    }

    @Test fun addingEditingAndCompletingUpdateTheStoredTask() {
        val store = AndroidCompanionStore(RuntimeEnvironment.getApplication())
        store.addTask("Existing task")
        var snapshot by mutableStateOf(store.load())
        compose.setContent {
            MaterialTheme {
                SimpleTodayDialog(snapshot, {}, { snapshot = store.addTask(it) },
                    { id, text -> snapshot = store.updateTask(id, text) },
                    { snapshot = store.deleteTask(it) }, { snapshot = store.completeTask(it) },
                    { snapshot = store.reorderOpenTasks(it) }, { _, _ -> })
            }
        }
        compose.onNodeWithText("New task").performTextInput("Latest task")
        compose.onNodeWithText("Add task").performClick()
        compose.runOnIdle { assertEquals(listOf("Latest task", "Existing task"), store.load().tasks.map { it.text }) }
        compose.onNodeWithText("Latest task").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Updated task")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Updated task").performClick()
        compose.onNodeWithText("Mark complete").performClick()
        compose.onNodeWithText("Updated task").assertDoesNotExist()
        compose.runOnIdle { assertTrue(store.load().tasks.first().done) }
    }
}
