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
import io.haru.assistant.companion.HaruHomeWidgetStatus

@Composable
fun HaruHomeWidgetDialog(
    status: HaruHomeWidgetStatus,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU Home companion") },
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
                    status.message,
                    style = MaterialTheme.typography.titleSmall,
                )

                if (!status.enabled) {
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Resume check-ins")
                    }
                }

                if (
                    status.installedCount == 0 &&
                    status.pinSupported
                ) {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add HARU to Home screen")
                    }
                }

                Text(
                    "Add HARU once, then the launcher keeps her on your Home screen until you remove her. " +
                        "Long-press and drag the HARU bar anywhere you want.",
                )

                if (
                    status.installedCount == 0 &&
                    !status.pinSupported
                ) {
                    Text(
                        "Your launcher does not support one-tap widget pinning. Long-press an empty Home-screen area → Widgets → HARU → HARU Home companion.",
                    )
                }

                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check placement")
                }

                Text(
                    "Tap anywhere on HARU's bar for an immediate local reaction. No chat opens and no network request is made. " +
                        "Android Home widgets cannot provide continuous high-frame-rate animation, so HARU uses fast tap reactions instead.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
