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
    onOpenAppSettings: () -> Unit,
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
                        "HARU is armed for the lock screen. On Xiaomi, POCO, or Redmi phones, HyperOS/MIUI may still block it until HARU is allowed to show on the lock screen."
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

                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open phone app settings")
                }

                Text(
                    "For HyperOS/MIUI: open Other permissions and allow Show on Lock screen. If present, also allow Display pop-up windows while running in the background.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "The HARU bar never opens over the unlocked HARU app. Unlocking closes the bar. Outside the compact bar, the normal system lock screen remains in control.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "HARU does not request draw-over-other-apps, accessibility, keyguard-dismiss, wake-lock, or full-screen-intent permission.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
