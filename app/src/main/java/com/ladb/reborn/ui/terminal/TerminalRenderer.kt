package com.ladb.reborn.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Renders the Termux TerminalBuffer onto a Compose Canvas using only
 * public API: emulator.getScreen(), row.getStyle(cell), row.findStartOfColumn(cell).
 *
 * Coordinate system (from TerminalBuffer docs):
 *   external rows: -activeTranscriptRows .. screenRows-1  (0 = top of live screen)
 */
object TerminalGrid {
    @Volatile var charWidth: Float = 9f
    @Volatile var lineHeight: Float = 16f
}

@Composable
fun TerminalCanvasView(
    sessionProvider: () -> TerminalSession?,
    frame: Int,
    fontSp: Float,
    scrollRowsProvider: () -> Int,
    onGridChanged: (cols: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSp.sp.toPx() }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = textSizePx
    }
    val fm = paint.fontMetrics
    val lineHeight = ceil(fm.descent - fm.ascent)
    val charWidth = paint.measureText("MMMM") / 4f
    val baselineOffset = -fm.ascent

    Canvas(modifier) {
        val session = sessionProvider()
        val viewCols = floor(size.width / charWidth).toInt().coerceAtLeast(20)
        val viewRows = floor(size.height / lineHeight).toInt().coerceAtLeast(5)
        onGridChanged(viewCols, viewRows)

        val emulator: TerminalEmulator? = session?.getEmulator()
        if (emulator == null) {
            drawRect(Color(0xFF0C1210))
            return@Canvas
        }

        val buffer = emulator.getScreen()
        val screenRows = emulator.mRows
        val cols = emulator.mColumns.coerceAtMost(viewCols)
        val transcript = buffer.getActiveTranscriptRows()
        val scroll = scrollRowsProvider().coerceIn(0, transcript)
        val topExternal = -scroll
        val palette = emulator.mColors.mCurrentColors

        val defaultBg = palette[TextStyle.COLOR_INDEX_BACKGROUND]
        val defaultFg = palette[TextStyle.COLOR_INDEX_FOREGROUND]

        drawRect(Color(defaultBg))

        val nativeCanvas = drawContext.canvas.nativeCanvas
        var row = 0
        while (row < viewRows) {
            val extRow = topExternal + row
            if (extRow >= screenRows) break
            if (extRow < -transcript) { row++; continue }

            val internal = buffer.externalToInternalRow(extRow)
            val line = buffer.allocateFullLineIfNecessary(internal)
            val chars = line.mText
            val used = line.getSpaceUsed()
            if (chars == null || used <= 0) { row++; continue }

            val rowTop = row * lineHeight
            val baseline = rowTop + baselineOffset

            var cell = 0
            while (cell < cols) {
                val style = line.getStyle(cell)
                var runEnd = cell + 1
                while (runEnd < cols && line.getStyle(runEnd) == style) runEnd++

                val charStart = line.findStartOfColumn(cell)
                val charEnd = if (runEnd >= cols) used else line.findStartOfColumn(runEnd)
                if (charStart >= used || charEnd <= charStart) break
                val count = charEnd - charStart

                val fgRaw = TextStyle.decodeForeColor(style)
                val bgRaw = TextStyle.decodeBackColor(style)
                val effect = TextStyle.decodeEffect(style)

                var fg = if (fgRaw < 0) fgRaw else palette[fgRaw.coerceIn(0, TextStyle.NUM_INDEXED_COLORS - 1)]
                var bg = if (bgRaw < 0) bgRaw else palette[bgRaw.coerceIn(0, TextStyle.NUM_INDEXED_COLORS - 1)]
                val fgIsDefault = fgRaw == TextStyle.COLOR_INDEX_FOREGROUND
                val bgIsDefault = bgRaw == TextStyle.COLOR_INDEX_BACKGROUND

                if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) {
                    val tFg = if (fgIsDefault) defaultBg else fg
                    val tBg = if (bgIsDefault) defaultFg else bg
                    fg = tBg
                    bg = tFg
                }

                val skipBg = bg == defaultBg
                if (!skipBg) {
                    drawRect(
                        color = Color(bg),
                        topLeft = Offset(cell * charWidth, rowTop),
                        size = Size((runEnd - cell) * charWidth, lineHeight),
                    )
                }

                if (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE == 0) {
                    paint.isFakeBoldText = effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0
                    paint.textSkewX = if (effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0) -0.25f else 0f
                    paint.color = fg
                    paint.alpha = if (effect and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0) 136 else 255
                    nativeCanvas.drawText(chars, charStart, count, cell * charWidth, baseline, paint)
                    paint.textSkewX = 0f
                    paint.isFakeBoldText = false
                }

                if (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0) {
                    drawLine(
                        color = Color(fg),
                        start = Offset(cell * charWidth, baseline + 2f),
                        end = Offset(runEnd * charWidth, baseline + 2f),
                        strokeWidth = 1f,
                    )
                }
                if (effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0) {
                    drawLine(
                        color = Color(fg),
                        start = Offset(cell * charWidth, baseline - textSizePx * 0.3f),
                        end = Offset(runEnd * charWidth, baseline - textSizePx * 0.3f),
                        strokeWidth = 1f,
                    )
                }

                cell = runEnd
            }
            row++
        }

        // Cursor overlay
        if (emulator.isCursorEnabled() && emulator.shouldCursorBeVisible()) {
            val cursorRowView = emulator.getCursorRow() + scroll
            if (cursorRowView in 0 until viewRows && emulator.getCursorCol() < cols) {
                drawRect(
                    color = Color(palette[TextStyle.COLOR_INDEX_CURSOR]).copy(alpha = 0.45f),
                    topLeft = Offset(emulator.getCursorCol() * charWidth, cursorRowView * lineHeight),
                    size = Size(charWidth, lineHeight),
                )
            }
        }
    }
}

/** Grab the visible screen text using only public APIs (for "Copy screen"). */
fun grabVisibleScreen(session: TerminalSession): String {
    val emulator = session.getEmulator() ?: return ""
    val buffer = emulator.getScreen()
    val screenRows = emulator.mRows
    val cols = emulator.mColumns
    val sb = StringBuilder()
    for (extRow in 0 until screenRows) {
        val line = buffer.allocateFullLineIfNecessary(buffer.externalToInternalRow(extRow))
        val chars = line.mText
        val used = line.getSpaceUsed()
        if (chars == null || used <= 0) { sb.append('\n'); continue }
        var cell = 0
        while (cell < cols) {
            val start = line.findStartOfColumn(cell)
            val end = if (cell + 1 >= cols) used else line.findStartOfColumn(cell + 1)
            if (start >= used || end <= start) break
            val runText = String(chars, start, (end - start).coerceAtMost(used - start)).trimEnd(' ')
            sb.append(runText)
            cell++
        }
        sb.append('\n')
    }
    return sb.toString().trimEnd('\n')
}
