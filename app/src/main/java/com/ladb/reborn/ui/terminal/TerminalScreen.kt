package com.ladb.reborn.ui.terminal

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ladb.reborn.AppContainer
import com.ladb.reborn.core.ConnState
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

private val BUILTIN_COMMANDS = listOf(
    "pm list packages",
    "pm list packages -3",
    "pm path ",
    "dumpsys battery",
    "dumpsys activity top",
    "dumpsys package ",
    "wm size",
    "wm density",
    "getprop ro.build.version.release",
    "logcat -d",
    "screencap -p /sdcard/screenshot.png",
    "ps -A",
    "top",
    "df -h",
    "cat /proc/cpuinfo",
    "settings get global adb_wifi_enabled",
    "am force-stop ",
    "pm uninstall --user 0 ",
    "pm grant ",
    "pm revoke ",
    "input keyevent ",
    "cmd package compile -m speed ",
)

@Composable
fun TerminalScreen(container: AppContainer) {
    val adb = container.adbManager
    val controller = adb.controller
    val settings by container.settings.settings.collectAsState()
    val history by container.history.items.collectAsState()
    val conn by adb.state.collectAsState()

    var frame by remember { mutableIntStateOf(0) }
    var scrollRows by remember { mutableIntStateOf(0) }
    var gridCols by remember { mutableIntStateOf(80) }
    var gridRows by remember { mutableIntStateOf(24) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var historyIndex by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    var packageNames by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current

    // Coalesced redraw ticker: emulator changes funnel through a conflated channel.
    val tick = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(controller) {
        controller.onTextChange = { tick.trySend(Unit) }
        for (unit in tick) frame++
    }

    // Apply grid size to the pty session.
    LaunchedEffect(gridCols, gridRows) { controller.resize(gridCols, gridRows) }

    // Preload package names for autocomplete.
    LaunchedEffect(Unit) {
        runCatching { packageNames = container.apps.searchPackageNames() }
    }

    fun send() {
        val command = input.text.trim()
        if (command.isEmpty()) return
        controller.writeLine(command)
        container.history.add(command)
        input = TextFieldValue("")
        historyIndex = null
        scrollRows = 0
    }

    fun historyPrev() {
        if (history.isEmpty()) return
        if (historyIndex == null) {
            draft = input.text
            historyIndex = history.lastIndex
        } else {
            historyIndex = (historyIndex!! - 1).coerceAtLeast(0)
        }
        val cmd = history[historyIndex!!]
        input = TextFieldValue(cmd, TextRange(cmd.length))
    }

    fun historyNext() {
        val idx = historyIndex ?: return
        if (idx >= history.lastIndex) {
            historyIndex = null
            input = TextFieldValue(draft, TextRange(draft.length))
        } else {
            historyIndex = idx + 1
            val cmd = history[historyIndex!!]
            input = TextFieldValue(cmd, TextRange(cmd.length))
        }
    }

    // Autocomplete pool = recent history (newest first) + builtins + package names.
    val lastToken = input.text.substringAfterLast(' ', missingDelimiterValue = input.text)
    val suggestions = remember(input.text, history, packageNames) {
        if (lastToken.isBlank() || input.text.endsWith(" ")) {
            emptyList()
        } else {
            val pool = (history.asReversed() + BUILTIN_COMMANDS + packageNames).distinct()
            val starts = pool.filter { it != input.text && it.startsWith(lastToken, ignoreCase = true) }
            val contains = pool.filter { it != input.text && !it.startsWith(lastToken, ignoreCase = true) && it.contains(lastToken, ignoreCase = true) }
            (starts + contains).take(8)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Terminal viewport ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            TerminalCanvasView(
                sessionProvider = { controller.session },
                frame = frame,
                fontSp = settings.fontSp.toFloat(),
                scrollRowsProvider = { scrollRows },
                onGridChanged = { cols, rows -> gridCols = cols; gridRows = rows },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val transcript = controller.session?.getEmulator()?.getScreen()?.getActiveTranscriptRows() ?: 0
                            scrollRows = (scrollRows - (dragAmount / TerminalGrid.lineHeight).roundToInt())
                                .coerceIn(0, transcript)
                        }
                    },
            )

            // Compact toolbar overlay (top-right): copy screen, clear.
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = {
                        controller.session?.let { clipboard.setText(AnnotatedString(grabVisibleScreen(it))) }
                    },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        "Copy screen",
                        Modifier
                            .padding(6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = { controller.writeLine("clear") },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                ) {
                    Icon(
                        Icons.Outlined.ClearAll,
                        "Clear terminal",
                        Modifier
                            .padding(6.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Jump-to-latest pill when scrolled up.
            if (scrollRows > 0) {
                Surface(
                    onClick = { scrollRows = 0 },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        "Jump to latest",
                        Modifier
                            .padding(8.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Status overlay when shell is not running.
            if (conn !is ConnState.Connected) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text(
                            "Connecting…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        TextButton(onClick = { adb.bootstrap() }) {
                            Icon(Icons.Outlined.Refresh, null); Text(" Retry")
                        }
                    }
                }
            }
        }

        // ---- Suggestions ----
        if (suggestions.isNotEmpty()) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val newText = input.text.dropLast(lastToken.length) + suggestion + " "
                            input = TextFieldValue(newText, TextRange(newText.length))
                        },
                        label = { Text(suggestion, maxLines = 1) },
                    )
                }
            }
        }

        // ---- Input bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { historyPrev() }, enabled = history.isNotEmpty()) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous command")
            }
            IconButton(onClick = { historyNext() }, enabled = historyIndex != null) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next command")
            }
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    historyIndex = null
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { historyPrev(); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { historyNext(); true }
                                else -> false
                            }
                        } else false
                    },
                placeholder = { Text(if (conn is ConnState.Connected) "shell" else "connecting…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = { send() }, enabled = input.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
            }
        }
    }
}
