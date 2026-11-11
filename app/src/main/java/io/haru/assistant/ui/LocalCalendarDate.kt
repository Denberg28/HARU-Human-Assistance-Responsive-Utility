package io.haru.assistant.ui

import android.annotation.SuppressLint
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate

/** Refresh at midnight, clock/zone changes, and resume without a polling timer. */
@SuppressLint("UnspecifiedRegisterReceiverFlag")
@Composable
internal fun rememberLocalCalendarDate(): LocalDate {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { date = LocalDate.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // These are protected system-only actions. The platform registration
            // avoids AndroidX's synthetic permission requirement on API 26–32.
            context.registerReceiver(receiver, filter)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) date = LocalDate.now()
        }
        lifecycle.addObserver(observer)
        date = LocalDate.now()
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return date
}
