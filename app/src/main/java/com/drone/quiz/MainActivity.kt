package com.drone.quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.drone.quiz.data.settings.AppSettings
import com.drone.quiz.ui.nav.AppRoot
import com.drone.quiz.ui.theme.DroneTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by ServiceLocator.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            DroneTheme(themeMode = settings.themeMode, fontLevel = settings.fontLevel) {
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    runCatching {
                        ServiceLocator.repo.ensureBankLoaded(applicationContext)
                    }
                    ready = true
                }
                if (ready) {
                    AppRoot(settings = settings)
                }
            }
        }
    }
}
