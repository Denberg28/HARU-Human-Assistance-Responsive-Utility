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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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

/** Local caring checker only: no AI request, microphone, alarm, or chat draft. */
@Composable
fun HaruCompanionCard(
    snapshot: CompanionSnapshot,
    quiet: Boolean,
    busy: Boolean,
    draft: String,
    visible: Boolean,
) {
    var step by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var acknowledged by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mayAdvance =
        visible &&
            !acknowledged &&
            HaruIdlePolicy.canAdvance(true, quiet, busy, draft)

    LaunchedEffect(lifecycle, mayAdvance, step) {
        if (mayAdvance) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(HaruIdlePolicy.nextIntervalMillis(step))
                if (isActive) {
                    now = System.currentTimeMillis()
                    step = (step + 1L) % 120L
                }
            }
        }
    }

    LaunchedEffect(acknowledged) {
        if (acknowledged) {
            delay(HaruIdlePolicy.ACKNOWLEDGEMENT_MS)
            acknowledged = false
            now = System.currentTimeMillis()
            step = (step + 1L) % 120L
        }
    }

    val hour =
        Calendar.getInstance().apply {
            timeInMillis = now
        }.get(Calendar.HOUR_OF_DAY)

    val content =
        HaruBubbleContentFactory.create(
            hourOfDay = hour,
            step = step,
            snapshot = snapshot,
            now = now,
            quiet = quiet,
        )
    val acknowledgement =
        HaruBubbleContentFactory.acknowledgement(step)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (acknowledged) "HARU" else content.greeting,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (acknowledged) acknowledgement.line else content.line,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                if (acknowledged) acknowledgement.face else content.face,
                fontSize = 26.sp,
                modifier =
                    Modifier
                        .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
                        .clickable(
                            enabled = !busy,
                            role = Role.Button,
                            onClickLabel = "Acknowledge HARU",
                        ) {
                            acknowledged = true
                        }
                        .padding(vertical = 12.dp),
            )
        }
    }
}
