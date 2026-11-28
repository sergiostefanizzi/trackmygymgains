package com.trackmygymgains

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trackmygymgains.ui.navigation.AppNavHost
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackMyGymGainsTheme {
                AppNavHost()
            }
        }
    }
}
