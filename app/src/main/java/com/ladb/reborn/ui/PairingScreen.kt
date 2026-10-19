package com.ladb.reborn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ladb.reborn.AppContainer
import com.ladb.reborn.core.ConnState

@Composable
fun BootScreen(container: AppContainer) {
    val events by container.adbManager.events.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text("LADB Reborn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Establishing local ADB shell…", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                events.takeLast(6).forEach { line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PairingScreen(container: AppContainer) {
    val adb = container.adbManager
    val state by adb.state.collectAsState()
    val events by adb.events.collectAsState()
    val pairingPortDiscovered by adb.mdns.pairingPort.collectAsState()
    val clipboard = LocalClipboardManager.current

    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    // Auto-fill the pairing port as soon as mDNS sees the pairing dialog.
    LaunchedEffect(pairingPortDiscovered) {
        if (port.isBlank() && pairingPortDiscovered != null) port = pairingPortDiscovered.toString()
    }

    val reason = (state as? ConnState.NeedsSetup)?.reason
    val busy = state is ConnState.Pairing || state is ConnState.Connecting

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("LADB Reborn", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Local ADB shell — no PC required",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        reason?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Pair with this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. Open Settings → Developer options → Wireless debugging\n" +
                        "2. Tap “Pair device with pairing code”\n" +
                        "3. Enter the port and 6-digit code below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(6) },
                        label = { Text("Pairing port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !busy,
                    )
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text ?: ""
                        // Paste picks the first 1-5 digit number found (port first in clipboard).
                        Regex("(\\d{1,5})").find(clip)?.groupValues?.get(1)?.let { port = it }
                    }) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste port") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("Pairing code") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !busy,
                    )
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text ?: ""
                        Regex("(\\d{6})").findAll(clip).lastOrNull()?.groupValues?.get(1)?.let { code = it }
                    }) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste code") }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { adb.pair(port, code) },
                        enabled = !busy && port.length >= 3 && code.length == 6,
                        modifier = Modifier.weight(1f),
                    ) { Text("Pair & connect") }
                }
                if (pairingPortDiscovered != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pairing dialog detected on port $pairingPortDiscovered ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Already paired?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it.filter(Char::isDigit).take(6) },
                        label = { Text("Wireless debugging port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !busy,
                    )
                    OutlinedButton(
                        onClick = { adb.connectManual(manualPort.toInt()) },
                        enabled = !busy && manualPort.length >= 3,
                    ) { Text("Connect") }
                }
                Text(
                    "Port is shown at the top of the Wireless debugging screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Live console
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { adb.bootstrap() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
                    }
                }
                events.takeLast(8).forEach { line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (events.isEmpty()) {
                    Text(
                        "Waiting…",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { adb.resetConnection() }) { Text("Reset ADB connection") }
        Spacer(Modifier.height(24.dp))
    }
}
