package io.haru.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HaruTheme {
                val haruViewModel: HaruViewModel = viewModel()
                HaruScreen(viewModel = haruViewModel)
            }
        }
    }
}
