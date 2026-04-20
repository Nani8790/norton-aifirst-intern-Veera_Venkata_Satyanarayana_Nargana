package com.veera.scammessagedetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.veera.scammessagedetector.ui.ScamDetectorScreen
import com.veera.scammessagedetector.ui.theme.ScamMessageDetectorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity host for the application.
 *
 * [@AndroidEntryPoint] allows Hilt to inject dependencies into this activity and
 * creates a child component of the application-level Hilt component established
 * by [ScamDetectorApplication]. Without this annotation, [@HiltViewModel] injection
 * in child composables would not function.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScamMessageDetectorTheme {
                ScamDetectorScreen()
            }
        }
    }
}
