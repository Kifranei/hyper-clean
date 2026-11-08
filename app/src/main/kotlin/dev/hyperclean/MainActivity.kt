package dev.hyperclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hyperclean.ui.CleanViewModel
import dev.hyperclean.ui.HyperCleanApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CleanViewModel = viewModel()
            HyperCleanApp(viewModel)
        }
    }
}
