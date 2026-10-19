package com.ladb.reborn.ui.apps

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ladb.reborn.AppContainer
import com.ladb.reborn.core.AppEntry
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppFilter(val label: String) {
    ALL("All"), USER("User"), SYSTEM("System"), DISABLED("Disabled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(container: AppContainer) {
    val apps by container.apps.apps.collectAsState()
    val loading by container.apps.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) { container.apps.refresh() }

    val filtered = remember(apps, query, filter) {
        apps.filter { entry ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !entry.isSystem
                AppFilter.SYSTEM -> entry.isSystem
                AppFilter.DISABLED -> !entry.enabled
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                entry.label.contains(q, ignoreCase = true) ||
                entry.packageName.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${apps.size} apps…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                    )
                }
            }

            if (loading && apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { entry ->
                        AppRow(
                            entry = entry,
                            iconLoader = { pkg -> container.apps.iconFor(pkg) },
                            onClick = { selected = entry },
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))

        selected?.let { entry ->
            AppDetailSheet(
                container = container,
                entry = entry,
                onDismiss = { selected = null },
                onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                onMutated = { scope.launch { container.apps.refresh(force = true); selected = null } },
                context = context,
            )
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, iconLoader: (String) -> Drawable?, onClick: () -> Unit) {
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.packageName) {
        value = withContext(Dispatchers.IO) {
            iconLoader(entry.packageName)?.toBitmap(96, 96)
        }
    }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(iconBitmap, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.versionName ?: "?", style = MaterialTheme.typography.bodySmall)
                Text(
                    when {
                        !entry.enabled -> "disabled"
                        entry.isSystem -> "system"
                        else -> "user"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(bitmap: Bitmap?, size: androidx.compose.ui.unit.Dp) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.size(size))
        } else {
            Icon(Icons.Filled.Android, null, Modifier.size(size * 0.8f))
        }
    }
}
