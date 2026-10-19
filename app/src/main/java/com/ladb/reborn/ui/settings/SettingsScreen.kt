package com.ladb.reborn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ladb.reborn.AppContainer

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsState()
    var startupDraft by remember(settings.startupCommand) { mutableStateOf(settings.startupCommand) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- Appearance ----
        SectionCard("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(0 to "System", 1 to "Light", 2 to "Dark").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { container.settings.update { it.copy(themeMode = mode) } },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Terminal font size — ${settings.fontSp}sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.fontSp.toFloat(),
                onValueChange = { container.settings.update { s -> s.copy(fontSp = it.toInt().coerceIn(9, 22)) } },
                valueRange = 9f..22f,
                steps = 12,
            )
        }

        // ---- Terminal ----
        SectionCard("Terminal") {
            OutlinedTextField(
                value = startupDraft,
                onValueChange = { startupDraft = it },
                label = { Text("Startup command") },
                supportingText = { Text("Runs right after the shell connects") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            TextButton(onClick = {
                container.settings.update { it.copy(startupCommand = startupDraft.trim()) }
            }) { Text("Save startup command") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Keep screen on",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { container.settings.update { s -> s.copy(keepScreenOn = it) } },
                )
            }
        }

        // ---- Data ----
        SectionCard("Data") {
            OutlinedButton(onClick = { container.history.clear() }) {
                Text("Clear command history")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { container.adbManager.resetConnection() }) {
                Text("Reset ADB connection (re-pair)")
            }
        }

        // ---- About ----
        SectionCard("About") {
            Text("LADB Reborn 3.0.0", fontWeight = FontWeight.SemiBold)
            Text(
                "A local ADB shell — talks to your own device over Wireless debugging. " +
                    "No data leaves the device.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            Text(
                "Built on the original LADB concept by draco (Tyler Nijmeh), the AOSP adb client, " +
                    "and the Termux terminal-emulator engine (Apache-2.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
    Spacer(Modifier.height(12.dp))
}
