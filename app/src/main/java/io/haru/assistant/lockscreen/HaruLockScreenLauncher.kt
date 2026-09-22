package io.haru.assistant.lockscreen

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Single lock-screen activity launch path.
 *
 * Android 14+ requires explicit background-activity-start opt-in when a
 * PendingIntent is used from the background. Xiaomi/POCO/Redmi devices may
 * additionally require their OEM "Show on Lock screen" and background-window
 * permissions, which HARU exposes in its setup UI.
 */
internal object HaruLockScreenLauncher {
    fun launch(context: Context) {
        val intent =
            Intent(
                context,
                HaruLockScreenActivity::class.java,
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }

        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val balMode =
                    if (Build.VERSION.SDK_INT >= 36) {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    } else {
                        @Suppress("DEPRECATION")
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }

                val creatorOptions =
                    ActivityOptions.makeBasic()
                        .setPendingIntentCreatorBackgroundActivityStartMode(
                            balMode
                        )
                        .toBundle()

                val pendingIntent =
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE,
                        creatorOptions,
                    )

                val senderOptions =
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            balMode
                        )
                        .toBundle()

                pendingIntent.send(
                    context,
                    0,
                    null,
                    null,
                    null,
                    null,
                    senderOptions,
                )
            } else {
                context.startActivity(intent)
            }
        }
    }

    private const val REQUEST_CODE = 9107
}
