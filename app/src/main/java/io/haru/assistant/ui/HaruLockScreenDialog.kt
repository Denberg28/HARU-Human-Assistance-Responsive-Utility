package io.haru.assistant.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.haru.assistant.lockscreen.HaruLockScreenActivity

@Composable
fun HaruLockScreenDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU on your lock screen") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (enabled) "Caring check-ins are active." else "Caring check-ins are paused.",
                    style = MaterialTheme.typography.titleSmall,
                )

                Button(
                    enabled = enabled,
                    onClick = {
                        onDismiss()
                        context.startActivity(
                            Intent(context, HaruLockScreenActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Show interactive HARU bar")
                }

                Text(
                    "HARU now opens as only the compact touch bar—there is no full HARU screen " +
                        "and no replacement wallpaper. Open the bar, then lock the phone. " +
                        "When you wake it, your normal lock-screen wallpaper and controls remain visible.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "Only the HARU bar handles taps. Touches outside it continue to the system lock screen, " +
                        "and unlocking closes HARU automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (enabled) "Pause HARU check-ins" else "Resume HARU check-ins")
                }

                Text(
                    "No draw-over-other-apps, accessibility, keyguard-dismiss, wake-lock, " +
                        "or full-screen-intent permission is used.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
