package com.ladb.reborn.ui.terminal

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ladb.reborn.AppContainer
import com.ladb.reborn.core.ConnState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Zero-width sentinel that keeps one character in the hidden IME field so
// backspace always produces a deletion we can forward to the pty.
private const val IME_SENTINEL = "\u200B"

@Composable
fun TerminalScreen(container: AppContainer) {
    val adb = container.adbManager
    val controller = adb.controller
    val settings by container.settings.settings.collectAsState()
    val conn by adb.state.collectAsState()

    var frame by remember { mutableIntStateOf(0) }
    var scrollRows by remember { mutableIntStateOf(0) }
    var gridCols by remember { mutableIntStateOf(80) }
    var gridRows by remember { mutableIntStateOf(24) }
    var ctrlSticky by remember { mutableStateOf(false) }
    var ime by remember { mutableStateOf(TextFieldValue(IME_SENTINEL)) }
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

    // Show the keyboard as soon as the terminal opens.
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { focusRequester.requestFocus() }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        if (ctrlSticky) {
            text.forEach { ch -> controller.writeBytes(byteArrayOf((ch.code and 0x1f).toByte())) }
            ctrlSticky = false
        } else {
            controller.writeBytes(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun sendBytes(vararg bytes: Int) {
        controller.writeBytes(bytes.map { it.toByte() }.toByteArray())
    }

    fun sendEscSeq(c: Char) = sendBytes(0x1B, '['.code, c.code)

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
                        detectTapGestures { focusRequester.requestFocus() }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val transcript = controller.session?.getEmulator()?.getScreen()?.getActiveTranscriptRows() ?: 0
                            scrollRows = (scrollRows - (dragAmount / TerminalGrid.lineHeight).roundToInt())
                                .coerceIn(0, transcript)
                        }
                    },
            )

            // Invisible IME capture field: the terminal itself is the editor.
            BasicTextField(
                value = ime,
                onValueChange = { v ->
                    val old = ime.text
                    val new = v.text
                    when {
                        new.length > old.length -> sendText(new.substring(old.length))
                        new.length < old.length -> repeat(old.length - new.length) {
                            sendBytes(0x7F) // DEL
                        }
                    }
                    ime = TextFieldValue(IME_SENTINEL, TextRange(IME_SENTINEL.length))
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                sendBytes('\r'.code); true
                            }
                            event.key == Key.Backspace -> { sendBytes(0x7F); true }
                            event.key == Key.Tab -> { sendBytes(0x09); true }
                            event.key == Key.Escape -> { sendBytes(0x1B); true }
                            event.key == Key.DirectionUp -> { sendEscSeq('A'); true }
                            event.key == Key.DirectionDown -> { sendEscSeq('B'); true }
                            event.key == Key.DirectionLeft -> { sendEscSeq('D'); true }
                            event.key == Key.DirectionRight -> { sendEscSeq('C'); true }
                            event.isCtrlPressed && event.nativeKeyEvent.getUnicodeChar(0) != 0 -> {
                                val c = event.nativeKeyEvent.getUnicodeChar(0).toChar()
                                sendBytes(c.code and 0x1f)
                                true
                            }
                            else -> false // printable keys flow into the field and are diffed out
                        }
                    },
                textStyle = TextStyle(fontSize = 8.sp, color = Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                ),
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

        // ---- Extra keys row (Termux-style) ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExtraKey("ESC", Modifier.weight(1f)) { sendBytes(0x1B) }
            ExtraKey("TAB", Modifier.weight(1f)) { sendBytes(0x09) }
            ExtraKey("CTRL", Modifier.weight(1f), active = ctrlSticky) { ctrlSticky = !ctrlSticky }
            ExtraKey("←", Modifier.weight(1f)) { sendEscSeq('D') }
            ExtraKey("↑", Modifier.weight(1f)) { sendEscSeq('A') }
            ExtraKey("↓", Modifier.weight(1f)) { sendEscSeq('B') }
            ExtraKey("→", Modifier.weight(1f)) { sendEscSeq('C') }
        }
    }
}

@Composable
private fun ExtraKey(label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
