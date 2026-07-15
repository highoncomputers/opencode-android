package ai.opencode.app.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val DEFAULT_FONT_SIZE_SP = 13f
private const val CURSOR_BLINK_MS = 530L
private val SELECTION_COLOR = Color(0xFF264F78)

@Composable
fun TerminalView(
    state: TerminalState,
    modifier: Modifier = Modifier,
    fontSize: Float = DEFAULT_FONT_SIZE_SP,
    onTextSelected: ((String) -> Unit)? = null,
) {
    val buffer = state.buffer
    val textMeasurer = rememberTextMeasurer(cacheSize = 2048)
    val density = LocalDensity.current
    val context = LocalContext.current

    val monospaceTypeface = remember { Typeface.MONOSPACE }

    val fontFamily = remember(monospaceTypeface) { FontFamily(monospaceTypeface) }

    val baseTextStyle = remember(fontFamily, fontSize) {
        TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal,
            color = TerminalState.defaultFgColor,
        )
    }

    val charWidthPx = remember(textMeasurer, baseTextStyle) {
        textMeasurer.measure("M", style = baseTextStyle).size.width.toFloat()
    }

    val charHeightPx = remember(textMeasurer, baseTextStyle) {
        textMeasurer.measure("M", style = baseTextStyle).size.height.toFloat()
    }

    val lineSpacingPx = with(density) { 2.dp.toPx() }
    val totalLineHeight = charHeightPx + lineSpacingPx

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var isUserScrolling by remember { mutableStateOf(false) }

    val scrollbackSize = buffer.getScrollbackSize()
    val totalLines = scrollbackSize + buffer.size.rows
    val totalContentHeight = totalLines * totalLineHeight

    var selectionAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectionCursor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isSelecting by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }

    var cursorBlinkOn by remember { mutableStateOf(true) }

    LaunchedEffect(buffer.cursorVisible) {
        if (!buffer.cursorVisible) {
            cursorBlinkOn = true
            return@LaunchedEffect
        }
        while (isActive) {
            cursorBlinkOn = true
            delay(CURSOR_BLINK_MS)
            cursorBlinkOn = false
            delay(CURSOR_BLINK_MS)
        }
    }

    LaunchedEffect(state.needsRender) {
        if (!isUserScrolling && state.needsRender) {
            val maxScroll = (totalContentHeight - charHeightPx * 2).coerceAtLeast(0f)
            if (scrollOffset < maxScroll - totalLineHeight * 2) {
                scrollOffset = maxScroll
            }
        }
    }

    fun screenToCell(x: Float, y: Float): Pair<Int, Int> {
        val visibleTop = totalContentHeight - scrollOffset
        val rawY = y - visibleTop
        val row = (rawY / totalLineHeight).toInt()
        val col = (x / charWidthPx).toInt().coerceIn(0, buffer.size.cols - 1)
        return row.coerceIn(0, buffer.size.rows - 1) to col
    }

    fun getSelectedText(): String {
        val anchor = selectionAnchor ?: return ""
        val cur = selectionCursor ?: return ""
        val (startRow, startCol) = if (anchor.first < cur.first ||
            (anchor.first == cur.first && anchor.second <= cur.second)
        ) anchor else cur
        val (endRow, endCol) = if (anchor.first < cur.first ||
            (anchor.first == cur.first && anchor.second <= cur.second)
        ) cur else anchor

        val sb = StringBuilder()
        val scrollbackLines = buffer.getScrollbackLines()
        val visibleLines = buffer.getVisibleLines()

        for (row in startRow..endRow) {
            val line = when {
                row < 0 -> {
                    val idx = scrollbackSize + row
                    if (idx in scrollbackLines.indices) scrollbackLines[idx] else continue
                }
                row < visibleLines.size -> visibleLines[row]
                else -> continue
            }

            val from = if (row == startRow) startCol.coerceIn(0, line.size - 1) else 0
            val to = if (row == endRow) endCol.coerceIn(0, line.size - 1) else line.size - 1

            var col = from
            while (col <= to && col < line.size) {
                val cell = line[col]
                if (cell.width > 0 && cell.char != '\u0000') {
                    sb.append(cell.char)
                }
                col += cell.width.coerceAtLeast(1)
            }
            if (row < endRow) sb.append('\n')
        }
        return sb.toString()
    }

    fun copySelectedText() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("terminal_selection", text)
            clipboard.setPrimaryClip(clip)
            onTextSelected?.invoke(text)
        }
        selectionAnchor = null
        selectionCursor = null
        isSelecting = false
        longPressTriggered = false
    }

    fun isSelected(row: Int, col: Int): Boolean {
        val anchor = selectionAnchor ?: return false
        val cur = selectionCursor ?: return false
        val (sr, sc) = if (anchor.first < cur.first ||
            (anchor.first == cur.first && anchor.second <= cur.second)
        ) anchor else cur
        val (er, ec) = if (anchor.first < cur.first ||
            (anchor.first == cur.first && anchor.second <= cur.second)
        ) cur else anchor
        return when {
            row < sr || row > er -> false
            row == sr && row == er -> col in sc..ec
            row == sr -> col >= sc
            row == er -> col <= ec
            else -> true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!isSelecting) {
                            val (row, col) = screenToCell(offset.x, offset.y)
                            buffer.setCursorPosition(row, col)
                            state.markDirty()
                            cursorBlinkOn = true
                        }
                    },
                    onLongPress = { offset ->
                        longPressTriggered = true
                        isSelecting = true
                        val cell = screenToCell(offset.x, offset.y)
                        selectionAnchor = cell
                        selectionCursor = cell
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        longPressTriggered = true
                        isSelecting = true
                        selectionAnchor = screenToCell(offset.x, offset.y)
                        selectionCursor = selectionAnchor
                    },
                    onDrag = { change, _ ->
                        if (isSelecting) {
                            selectionCursor = screenToCell(change.position.x, change.position.y)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (isSelecting) {
                            val text = getSelectedText()
                            if (text.isNotEmpty()) {
                                copySelectedText()
                            } else {
                                isSelecting = false
                                selectionAnchor = null
                                selectionCursor = null
                            }
                        }
                        longPressTriggered = false
                    },
                    onDragCancel = {
                        isSelecting = false
                        selectionAnchor = null
                        selectionCursor = null
                        longPressTriggered = false
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var prevY = 0f
                    val down = awaitFirstDown(requireUnconsumed = false)
                    prevY = down.position.y
                    isUserScrolling = true

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break

                        val dy = change.position.y - prevY
                        prevY = change.position.y

                        val maxScroll = (totalContentHeight - charHeightPx * 2).coerceAtLeast(0f)
                        scrollOffset = (scrollOffset - dy).coerceIn(0f, maxScroll)
                        change.consume()
                    }

                    isUserScrolling = false
                }
            },
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val contentTop = totalContentHeight - scrollOffset
        val scrollbackLines = buffer.getScrollbackLines()
        val visibleLines = buffer.getVisibleLines()

        clipRect {
            // Draw scrollback lines
            for (i in scrollbackLines.indices) {
                val screenY = contentTop + i * totalLineHeight
                if (screenY + totalLineHeight < 0 || screenY > canvasHeight) continue

                val row = i - scrollbackSize
                drawTerminalLine(
                    line = scrollbackLines[i],
                    screenY = screenY,
                    charWidthPx = charWidthPx,
                    charHeightPx = charHeightPx,
                    textMeasurer = textMeasurer,
                    baseTextStyle = baseTextStyle,
                    isSelected = { col -> isSelected(row, col) },
                    selectionColor = SELECTION_COLOR,
                )
            }

            // Draw visible screen lines
            for (row in visibleLines.indices) {
                val screenY = contentTop + (scrollbackSize + row) * totalLineHeight
                if (screenY + totalLineHeight < 0 || screenY > canvasHeight) continue

                drawTerminalLine(
                    line = visibleLines[row],
                    screenY = screenY,
                    charWidthPx = charWidthPx,
                    charHeightPx = charHeightPx,
                    textMeasurer = textMeasurer,
                    baseTextStyle = baseTextStyle,
                    isSelected = { col -> isSelected(row, col) },
                    selectionColor = SELECTION_COLOR,
                )
            }

            // Draw cursor
            if (buffer.cursorVisible && cursorBlinkOn) {
                val cursorRow = buffer.cursor.row
                val cursorCol = buffer.cursor.col
                val scrollbackCount = scrollbackLines.size
                val cursorScreenY = contentTop + (scrollbackCount + cursorRow) * totalLineHeight
                val cursorScreenX = cursorCol * charWidthPx

                if (cursorRow in visibleLines.indices && cursorCol in visibleLines[cursorRow].indices) {
                    val cell = visibleLines[cursorRow][cursorCol]
                    val effectiveFg = if (cell.fgColor == Color.Unspecified) {
                        TerminalState.defaultFgColor
                    } else {
                        cell.fgColor
                    }

                    when (buffer.cursorStyle) {
                        CursorStyle.BLOCK -> {
                            drawRect(
                                color = effectiveFg.copy(alpha = 0.7f),
                                topLeft = Offset(cursorScreenX, cursorScreenY),
                                size = Size(charWidthPx, charHeightPx),
                            )
                            if (cell.char != ' ' && cell.char != '\u0000') {
                                val invertedFg = if (cell.attributes.inverse) {
                                    if (cell.bgColor == Color.Transparent) Color(0xFF1E1E1E) else cell.bgColor
                                } else {
                                    Color(0xFF1E1E1E)
                                }
                                val cursorCharStyle = baseTextStyle.copy(
                                    color = invertedFg,
                                    fontWeight = if (cell.attributes.bold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (cell.attributes.italic) FontStyle.Italic else FontStyle.Normal,
                                )
                                val textLayout = textMeasurer.measure(cell.char.toString(), cursorCharStyle)
                                drawText(textLayout, topLeft = Offset(cursorScreenX, cursorScreenY))
                            }
                        }
                        CursorStyle.BEAM -> {
                            drawLine(
                                color = effectiveFg,
                                start = Offset(cursorScreenX + 1, cursorScreenY),
                                end = Offset(cursorScreenX + 1, cursorScreenY + charHeightPx),
                                strokeWidth = 2f,
                            )
                        }
                        CursorStyle.UNDERLINE -> {
                            drawLine(
                                color = effectiveFg,
                                start = Offset(cursorScreenX, cursorScreenY + charHeightPx - 2),
                                end = Offset(cursorScreenX + charWidthPx, cursorScreenY + charHeightPx - 2),
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
            }

            // Scroll fade at bottom
            if (scrollOffset < totalContentHeight - canvasHeight) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E1E1E), Color.Transparent),
                        startY = canvasHeight - 40f,
                        endY = canvasHeight,
                    ),
                    topLeft = Offset(0f, canvasHeight - 40f),
                    size = Size(canvasWidth, 40f),
                )
            }
            // Scroll fade at top
            if (scrollOffset > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E1E1E), Color.Transparent),
                        startY = 0f,
                        endY = 40f,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(canvasWidth, 40f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerminalLine(
    line: Array<TerminalCell>,
    screenY: Float,
    charWidthPx: Float,
    charHeightPx: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    baseTextStyle: TextStyle,
    isSelected: (Int) -> Boolean,
    selectionColor: Color,
) {
    var col = 0
    while (col < line.size) {
        val cell = line[col]
        if (cell.width == 0) {
            col++
            continue
        }

        val cellX = col * charWidthPx
        val selected = isSelected(col)

        var cellFg = if (cell.fgColor == Color.Unspecified) TerminalState.defaultFgColor else cell.fgColor
        var cellBg = cell.bgColor

        if (cell.attributes.inverse) {
            val tmpFg = cellFg
            val tmpBg = if (cellBg == Color.Transparent) Color(0xFF1E1E1E) else cellBg
            cellFg = tmpBg
            cellBg = tmpFg
        }

        if (cell.attributes.hidden) {
            cellFg = if (cellBg == Color.Transparent) Color(0xFF1E1E1E) else cellBg
        }

        if (cell.attributes.dim && !cell.attributes.bold) {
            cellFg = cellFg.copy(alpha = 0.6f)
        }

        if (selected) {
            cellBg = selectionColor
            cellFg = Color.White
        }

        if (cellBg != Color.Transparent) {
            drawRect(
                color = cellBg,
                topLeft = Offset(cellX, screenY),
                size = Size(charWidthPx * cell.width.coerceAtLeast(1), charHeightPx),
            )
        }

        if (cell.char != ' ' && cell.char != '\u0000') {
            var fontWeight = FontWeight.Normal
            var fontStyle = FontStyle.Normal
            var textDecoration: TextDecoration? = null

            if (cell.attributes.bold) fontWeight = FontWeight.Bold
            if (cell.attributes.italic) fontStyle = FontStyle.Italic
            if (cell.attributes.underline || cell.attributes.overline) {
                textDecoration = TextDecoration.Underline
            }
            if (cell.attributes.strikethrough) {
                textDecoration = if (textDecoration != null) {
                    TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                } else {
                    TextDecoration.LineThrough
                }
            }

            val charStyle = baseTextStyle.copy(
                color = cellFg,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
            )

            val textLayout = textMeasurer.measure(cell.char.toString(), charStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(cellX, screenY),
            )
        }

        col += cell.width.coerceAtLeast(1)
    }
}
