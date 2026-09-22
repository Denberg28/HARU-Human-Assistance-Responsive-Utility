package io.haru.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HaruLockScreenDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU lock screen") },
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
                    if (enabled) {
                        "Lock-screen HARU · ON"
                    } else {
                        "Lock-screen HARU · OFF"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )

                Text(
                    if (enabled) {
                        "HARU is armed for the lock screen. The interactive bar is created after the phone goes to sleep and is shown only when Android confirms the keyguard is locked."
                    } else {
                        "HARU will stay out of the lock screen until you enable it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (enabled) {
                            "Turn off lock-screen HARU"
                        } else {
                            "Enable lock-screen HARU"
                        }
                    )
                }

                Text(
                    "The HARU bar never opens over the unlocked HARU app. Unlocking closes the bar. Outside the compact bar, the normal system lock screen remains in control.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "No draw-over-other-apps, accessibility, keyguard-dismiss, wake-lock, or full-screen-intent permission is used.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
