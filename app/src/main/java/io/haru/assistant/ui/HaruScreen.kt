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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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

@Composable
fun HaruScreen(
    viewModel: HaruViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HARU",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Human Assistance & Responsive Utility",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(24.dp))

            HaruFace(mood = state.mood)

            Spacer(Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = state.mood.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedTextField(
                value = state.command,
                onValueChange = viewModel::updateCommand,
                label = { Text("Ask HARU") },
                placeholder = { Text("Try: calculate 22.2 * 60") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isBusy && state.command.isNotBlank()
                ) {
                    Text("Send")
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.updateCommand("help"); viewModel.submit() },
                    enabled = !state.isBusy
                ) {
                    Text("Help")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "v0.1 • local-first",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
