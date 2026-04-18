package com.basitce.hapticbeats.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.basitce.hapticbeats.MyApplication
import com.basitce.hapticbeats.R
import com.basitce.hapticbeats.ui.library.LibraryScreen
import com.basitce.hapticbeats.ui.library.LibraryViewModel
import com.basitce.hapticbeats.ui.library.LibraryViewModelFactory
import com.basitce.hapticbeats.ui.navigation.Screen
import com.basitce.hapticbeats.ui.playback.PlaybackScreen
import com.basitce.hapticbeats.ui.playback.PlaybackUiState
import com.basitce.hapticbeats.ui.playback.PlaybackViewModel
import com.basitce.hapticbeats.ui.playback.PlaybackViewModelFactory
import com.basitce.hapticbeats.ui.settings.SettingsScreen
import com.basitce.hapticbeats.ui.settings.SettingsViewModel
import com.basitce.hapticbeats.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    app: MyApplication,
    settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            app,
            app.billingManager,
            app.hapticPlayer,
            app.repository
        )
    )
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }

    val settingsState by settingsViewModel.uiState.collectAsState()

    val playbackViewModel: PlaybackViewModel = viewModel(
        factory = PlaybackViewModelFactory(
            app,
            app.repository,
            app.hapticPlayer,
            app.audioAnalyzer
        )
    )
    val playbackUiState by playbackViewModel.uiState.collectAsState()

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(app, app.repository)
    )

    LaunchedEffect(
        settingsState.isAudioEnabled,
        settingsState.isHapticsEnabled,
        settingsState.defaultIntensity
    ) {
        playbackViewModel.applyOutputSettings(
            audioEnabled = settingsState.isAudioEnabled,
            hapticsEnabled = settingsState.isHapticsEnabled,
            intensity = settingsState.defaultIntensity
        )
    }

    val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions.toTypedArray())

        val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            batteryManager.isCharging
        } else {
            false
        }

        if (batteryLevel in 1..14 && !isCharging) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.low_battery_warning),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (currentScreen != Screen.Settings) {
                Column {
                    if (playbackUiState.selectedSong != null && currentScreen != Screen.Playback) {
                        MiniPlayer(
                            uiState = playbackUiState,
                            onPlayPause = {
                                if (playbackUiState.isPlaying) playbackViewModel.pause() else playbackViewModel.play()
                            },
                            onClick = { currentScreen = Screen.Playback }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        listOf(Screen.Library, Screen.Playback).forEach { screen ->
                            val title = stringResource(screen.titleResId)
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = title) },
                                label = { Text(text = title) },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceColorAtElevation(LocalAbsoluteTonalElevation.current)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentScreen) {
                Screen.Library -> LibraryScreen(
                    viewModel = libraryViewModel,
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onSongClick = { song ->
                        playbackViewModel.loadSong(song)
                        currentScreen = Screen.Playback
                    }
                )

                Screen.Playback -> PlaybackScreen(
                    viewModel = playbackViewModel,
                    isVisualHapticsEnabled = settingsState.isVisualHapticsEnabled,
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onBrowseLibrary = { currentScreen = Screen.Library }
                )

                Screen.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { currentScreen = Screen.Library }
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    uiState: PlaybackUiState,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        shadowElevation = 8.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title.ifBlank { stringResource(R.string.select_song) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = uiState.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.nav_player),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
