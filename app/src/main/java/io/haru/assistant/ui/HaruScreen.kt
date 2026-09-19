package io.haru.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.haru.assistant.HaruViewModel
import io.haru.assistant.core.HaruMood
import io.haru.assistant.localai.LocalAiStatus
import io.haru.assistant.voice.HaruVoiceController

@Composable
fun HaruScreen(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    localAiStatus: LocalAiStatus,
    localAiBusy: Boolean,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onImportLocalModel: () -> Unit,
    onDownloadLocalModel: (String) -> Unit,
    onValidateLocalModel: (String) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    onOpenModelLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.uiState
    var showLocalAi by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "HARU",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Human Assistance & Responsive Utility",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(18.dp))
            HaruFace(mood = state.mood)
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = state.mood.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Voice: " + voiceStatus.speechInput + " STT • " + voiceStatus.speechOutput,
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showLocalAi = true },
                enabled = !state.isBusy,
            ) {
                Text(
                    if (localAiStatus.installedModels.isEmpty()) {
                        "Local AI setup"
                    } else {
                        "Local AI • " + localAiStatus.installedModels.size
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            OutlinedTextField(
                value = state.command,
                onValueChange = viewModel::updateCommand,
                label = { Text("Ask HARU") },
                placeholder = { Text("Type or tap Mic") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isBusy && state.command.isNotBlank(),
                ) {
                    Text("Send")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onMicClick,
                    enabled = !state.isBusy || state.mood == HaruMood.LISTENING,
                ) {
                    Text(if (state.mood == HaruMood.LISTENING) "Listening…" else "Mic")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onSpeakClick,
                    enabled = !state.isBusy && state.message.isNotBlank(),
                ) {
                    Text("Speak")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "local STT/TTS • AI-provider independent",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (showLocalAi) {
        LocalAiSetupDialog(
            status = localAiStatus,
            busy = localAiBusy,
            onDismiss = { if (!localAiBusy) showLocalAi = false },
            onImport = onImportLocalModel,
            onDownload = onDownloadLocalModel,
            onValidate = onValidateLocalModel,
            onDelete = onDeleteLocalModel,
            onOpenLibrary = onOpenModelLibrary,
        )
    }
}

@Composable
private fun LocalAiSetupDialog(
    status: LocalAiStatus,
    busy: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onDownload: (String) -> Unit,
    onValidate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var downloadUrl by remember { mutableStateOf("") }
    var selectedModel by remember(status.installedModels) {
        mutableStateOf(
            status.activeModel.takeIf { it.isNotBlank() }
                ?: status.installedModels.firstOrNull().orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text("Done")
            }
        },
        title = {
            Text("Local AI on this phone")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Phone readiness",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status.ramGb.toString() + " GB RAM • " +
                        status.freeStorageGb.toString() + " GB free",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Recommended: " + status.recommendedTier,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import .litertlm from phone")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenLibrary,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open LiteRT model library")
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Download from HTTPS URL",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("https://…/model.litertlm") },
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onDownload(downloadUrl.trim()) },
                    enabled = !busy &&
                        downloadUrl.trim().startsWith("https://") &&
                        downloadUrl.trim().substringBefore('?').endsWith(".litertlm"),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Working…" else "Download model")
                }

                if (status.installedModels.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Installed models",
                        fontWeight = FontWeight.SemiBold,
                    )

                    status.installedModels.forEach { name ->
                        Spacer(Modifier.height(6.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (name == selectedModel) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedModel = name
                                            onValidate(name)
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text("Validate")
                                    }
                                    TextButton(
                                        onClick = {
                                            if (selectedModel == name) selectedModel = ""
                                            onDelete(name)
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }

                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Keep HARU open while setup finishes.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    )
}
