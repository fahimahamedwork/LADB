package com.ladb.reborn.ui.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ladb.reborn.AppContainer
import com.ladb.reborn.core.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    container: AppContainer,
    entry: AppEntry,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onMutated: () -> Unit,
    context: Context,
) {
    val repo = container.apps
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var permsLoading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.packageName) {
        value = withContext(Dispatchers.IO) { repo.iconFor(entry.packageName)?.toBitmap(128, 128) }
    }

    LaunchedEffect(entry.packageName) {
        permsLoading = true
        granted = runCatching { repo.grantedPermissions(entry.packageName) }.getOrDefault(emptyMap())
        permsLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconBitmap != null) {
                    Image(iconBitmap!!.asImageBitmap(), null, Modifier.size(48.dp))
                } else {
                    Icon(Icons.Filled.Android, null, Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            DetailRow("Version", "${entry.versionName ?: "?"} (${entry.versionCode})")
            DetailRow("Target SDK", entry.targetSdk.toString())
            DetailRow("Kind", if (entry.isSystem) "System app" else "User app")
            DetailRow("State", if (entry.enabled) "Enabled" else "Disabled")
            DetailRow("Installed", DateFormat.getDateInstance().format(Date(entry.installTime)))
            DetailRow("UID", entry.uid.toString())
            DetailRow("APK", entry.apkPath, monospace = true)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ---- Actions ----
            Text("Actions", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (busy) return@OutlinedButton
                    busy = true
                    Thread {
                        val r = kotlinx.coroutines.runBlocking { repo.launch(entry.packageName) }
                        busy = false
                        onMessage(r.fold({ "Launched ${entry.label}" }, { "Launch failed: ${it.message}" }))
                    }.start()
                }, enabled = !busy) { Text("Launch") }

                OutlinedButton(onClick = { confirmAction = "force-stop" }, enabled = !busy) { Text("Force stop") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmAction = "uninstall" }, enabled = !busy) { Text("Uninstall") }
                OutlinedButton(onClick = { confirmAction = "clear" }, enabled = !busy) { Text("Clear data") }
                if (entry.enabled) {
                    OutlinedButton(onClick = { confirmAction = "disable" }, enabled = !busy) { Text("Disable") }
                } else {
                    OutlinedButton(onClick = { confirmAction = "enable" }, enabled = !busy) { Text("Enable") }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                busy = true
                Thread {
                    val r = kotlinx.coroutines.runBlocking { repo.extractApk(entry) }
                    busy = false
                    r.fold(
                        { uri -> repo.shareApk(uri); onMessage("APK exported") },
                        { onMessage("Extract failed: ${it.message}") },
                    )
                }.start()
            }, enabled = !busy && entry.apkPath.isNotBlank()) { Text("Extract APK") }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ---- Permissions ----
            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            if (permsLoading) {
                Text("Loading granted state…", style = MaterialTheme.typography.bodySmall)
            } else {
                val displayPerms = entry.permissions.filter { it.startsWith("android.permission.") }
                if (displayPerms.isEmpty()) {
                    Text("No permissions requested.", style = MaterialTheme.typography.bodySmall)
                } else {
                    displayPerms.forEach { perm ->
                        val shortName = perm.substringAfterLast('.')
                        val isGranted = granted[perm]
                        ListItem(
                            headlineContent = { Text(shortName, style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = {
                                Text(
                                    when (isGranted) {
                                        null -> "not grantable / not requested at runtime"
                                        true -> "granted"
                                        false -> "revoked"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                if (isGranted != null) {
                                    Switch(
                                        checked = isGranted,
                                        enabled = !busy,
                                        onCheckedChange = { want ->
                                            busy = true
                                            val map = granted.toMutableMap()
                                            Thread {
                                                val r = kotlinx.coroutines.runBlocking {
                                                    if (want) repo.grant(entry.packageName, perm)
                                                    else repo.revoke(entry.packageName, perm)
                                                }
                                                r.fold(
                                                    { map[perm] = want; onMessage("$shortName ${if (want) "granted" else "revoked"}") },
                                                    { onMessage("Failed: ${r.exceptionOrNull()?.message ?: "error"}") },
                                                )
                                                granted = map
                                                busy = false
                                            }.start()
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- Confirmations ----
    val confirm = confirmAction
    val confirmContent: Triple<String, String, () -> Result<String>>? = when (confirm) {
        "uninstall" -> Triple("Uninstall app", "Uninstall ${entry.label} for user 0?") {
            kotlinx.coroutines.runBlocking { repo.uninstall(entry.packageName) }
        }
        "clear" -> Triple("Clear data", "Clear all app data of ${entry.label}?") {
            kotlinx.coroutines.runBlocking { repo.clearData(entry.packageName) }
        }
        "force-stop" -> Triple("Force stop", "Force stop ${entry.label}?") {
            kotlinx.coroutines.runBlocking { repo.forceStop(entry.packageName) }
        }
        "disable" -> Triple("Disable app", "Disable ${entry.label} for user 0?") {
            kotlinx.coroutines.runBlocking { repo.disable(entry.packageName) }
        }
        "enable" -> Triple("Enable app", "Enable ${entry.label}?") {
            kotlinx.coroutines.runBlocking { repo.enable(entry.packageName) }
        }
        else -> null
    }
    if (confirmContent != null) {
        val (title, message, run) = confirmContent
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    busy = true
                    Thread {
                        val r = run()
                        busy = false
                        r.fold(
                            { onMessage("$title: done"); onMutated() },
                            { onMessage("Failed: ${it.message ?: "error"}") },
                        )
                    }.start()
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
