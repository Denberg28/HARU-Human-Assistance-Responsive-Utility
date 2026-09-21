package io.haru.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.haru.assistant.companion.HaruBubbleStatus

@Composable
fun HaruBubbleSetupDialog(
    status: HaruBubbleStatus,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU on your Home screen") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(status.message, style = MaterialTheme.typography.titleSmall)
                if (!status.enabled) {
                    Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text("Resume companion") }
                }
                if (status.installedCount == 0 && status.pinSupported) {
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add to Home screen") }
                }
                Text("Confirm Add in your launcher's prompt. Turning the companion on alone does not place a widget.")
                Text("No prompt? Long-press an empty area of your Home screen → Widgets → HARU → HARU Bubble. Drag it to the upper-right or any free space. Unlock the Home layout if your launcher prevents changes.")
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Check placement") }
                Text("Tap HARU's face for a new expression and prompt. Tap the text to open a chat draft, then press Send. Long-press the widget to move, resize, or remove it.")
                Text("Greetings and starters work offline. Home widgets refresh about every 30 minutes; Android may delay them to save battery. Inside HARU, idle prompts change every 20 seconds while the screen is active.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
