package com.basitce.hapticbeats.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.basitce.hapticbeats.R

sealed class Screen(
    val route: String,
    @StringRes val titleResId: Int,
    val icon: ImageVector
) {
    object Playback : Screen("playback", R.string.nav_player, Icons.Default.GraphicEq)
    object Library : Screen("library", R.string.nav_library, Icons.Default.LibraryMusic)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}
