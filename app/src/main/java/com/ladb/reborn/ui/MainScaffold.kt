package com.ladb.reborn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ladb.reborn.AppContainer
import com.ladb.reborn.ui.apps.AppsScreen
import com.ladb.reborn.ui.settings.SettingsScreen
import com.ladb.reborn.ui.terminal.TerminalScreen

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val DESTINATIONS = listOf(
    Destination("terminal", "Terminal", Icons.Outlined.Terminal),
    Destination("apps", "Apps", Icons.Filled.Apps),
    Destination("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun MainScaffold(container: AppContainer) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                DESTINATIONS.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selected) {
                0 -> TerminalScreen(container)
                1 -> AppsScreen(container)
                else -> SettingsScreen(container)
            }
        }
    }
}
