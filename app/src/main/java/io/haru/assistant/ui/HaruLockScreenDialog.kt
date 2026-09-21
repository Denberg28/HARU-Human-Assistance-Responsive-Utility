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

@Composable
fun HaruLockScreenDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit,
    onToggle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU on your lock screen") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (enabled) {
                        "Caring check-ins are active."
                    } else {
                        "Caring check-ins are paused."
                    },
                    style = MaterialTheme.typography.titleSmall,
                )

                Button(
                    onClick = onSetWallpaper,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Set HARU as live wallpaper")
                }

                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (enabled) {
                            "Pause HARU check-ins"
                        } else {
                            "Resume HARU check-ins"
                        }
                    )
                }

                Text(
                    "In the Android wallpaper preview, choose Lock screen only when your phone offers it. " +
                        "Some launchers combine Home and Lock screen live wallpapers; that choice is controlled by Android/your launcher.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "HARU runs, rests, sleeps at night, and reacts when you tap her bubble. " +
                        "Animation stops whenever the wallpaper is not visible.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
