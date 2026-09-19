package io.haru.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.haru.assistant.HaruViewModel
import io.haru.assistant.core.HaruMood
import io.haru.assistant.voice.HaruVoiceController

@Composable
fun HaruScreen(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.uiState

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
}
