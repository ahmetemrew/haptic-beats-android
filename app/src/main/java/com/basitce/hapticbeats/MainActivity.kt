package com.basitce.hapticbeats

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.basitce.hapticbeats.ui.MainScreen
import com.basitce.hapticbeats.ui.theme.HapticBeatsTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MyApplication

        val settingsFactory = com.basitce.hapticbeats.ui.settings.SettingsViewModelFactory(
            app,
            app.billingManager,
            app.hapticPlayer,
            app.repository
        )
        val settingsViewModel = ViewModelProvider(this, settingsFactory)[com.basitce.hapticbeats.ui.settings.SettingsViewModel::class.java]

        setContent {
            val settingsState = settingsViewModel.uiState.collectAsState().value

            HapticBeatsTheme(themeMode = settingsState.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(app = app, settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}
