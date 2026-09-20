package io.haru.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.companion.HaruBubbleContentFactory
import io.haru.assistant.companion.HaruIdlePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

/** Local presence only: no AI requests, microphone, alarms, or background service. */
@Composable
fun HaruCompanionCard(
    snapshot: CompanionSnapshot,
    quiet: Boolean,
    busy: Boolean,
    draft: String,
    visible: Boolean,
    onChat: (String) -> Unit,
) {
    var step by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mayAdvance = visible && HaruIdlePolicy.canAdvance(true, quiet, busy, draft)
    LaunchedEffect(lifecycle, mayAdvance) {
        if (mayAdvance) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = System.currentTimeMillis()
            while (isActive) {
                delay(HaruIdlePolicy.INTERVAL_MS)
                now = System.currentTimeMillis()
                step = (step + 1L) % 12L
            }
        }
    }
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val content = HaruBubbleContentFactory.create(hour, step, snapshot, now, quiet)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(content.greeting, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(content.line, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                content.face,
                fontSize = 24.sp,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Another expression and conversation starter") {
                        now = System.currentTimeMillis()
                        step = (step + 1L) % 12L
                    }
                    .padding(vertical = 12.dp),
            )
        }
        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(enabled = !busy, onClick = {
                now = System.currentTimeMillis()
                step = (step + 1L) % 12L
            }) { Text("Another") }
            TextButton(enabled = !busy && draft.isBlank(), onClick = { onChat(content.conversationPrompt) }) {
                Text("Let's chat")
            }
        }
    }
}
