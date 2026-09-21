package io.haru.assistant.ui

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import io.haru.assistant.lockscreen.HaruLockScreenActivity
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
    val context = LocalContext.current
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
                    enabled = enabled,
                    onClick = {
                        onDismiss()
                        context.startActivity(Intent(context, HaruLockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start interactive session")
                }

                Text(
                    "Start a session, then press the power button to lock your phone. " +
                        "Wake the screen and tap HARU to change her expression. " +
                        "Close the session to return to your normal lock screen.",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedButton(
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
                    "Wallpaper taps depend on your phone: some lock screens block them. " +
                        "Use the interactive session when wallpaper taps do not work. " +
                        "Animation pauses when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
