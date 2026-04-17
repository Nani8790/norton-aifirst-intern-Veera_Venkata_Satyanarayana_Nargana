package com.veera.scammessagedetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.veera.scammessagedetector.ui.ScamDetectorScreen
import com.veera.scammessagedetector.ui.theme.ScamMessageDetectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScamMessageDetectorTheme {
                // Call your new screen here directly!
                // Claude's code already includes a Scaffold to handle padding.
                ScamDetectorScreen()
            }
        }
    }
}