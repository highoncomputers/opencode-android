package ai.opencode.app.ui.terminal

import androidx.compose.ui.graphics.Color

data class TextAttributes(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val inverse: Boolean = false,
    val dim: Boolean = false,
    val blink: Boolean = false,
    val hidden: Boolean = false,
    val overline: Boolean = false,
) {
    fun reset(): TextAttributes = DEFAULT

    fun hasAny(): Boolean =
        bold || underline || italic || strikethrough || inverse || dim || blink || hidden || overline

    companion object {
        val DEFAULT = TextAttributes()
    }
}

data class TerminalCell(
    val char: Char = ' ',
    val fgColor: Color = Color.Unspecified,
    val bgColor: Color = Color.Unspecified,
    val attributes: TextAttributes = TextAttributes.DEFAULT,
    val width: Int = 1,
)

enum class CursorStyle {
    BLOCK, BEAM, UNDERLINE
}

data class CursorPosition(
    val row: Int = 0,
    val col: Int = 0,
)

data class TerminalSize(
    val rows: Int = 24,
    val cols: Int = 80,
)

class TerminalBuffer(
    initialRows: Int = 24,
    initialCols: Int = 80,
    private val maxScrollbackLines: Int = 10_000,
) {
    @Volatile
    var size = TerminalSize(initialRows, initialCols)
        private set

    @Volatile
    var cursor = CursorPosition(0, 0)
        private set

    @Volatile
    var cursorVisible = true
        private set

    @Volatile
    var cursorStyle = CursorStyle.BLOCK
        private set

    @Volatile
    var windowTitle: String = ""
        private set

    @Volatile
    var autoWrapMode = true
        private set

    var originMode = false
        private set

    var insertMode = false
        private set

    var wraparoundMode = true
        private set

    var bracketedPasteMode = false
        private set

    var applicationCursorKeys = false
        private set

    var applicationKeypad = false
        private set

    var reverseVideo = false
        private set

    private val tabWidth = 8
    val tabStops = BooleanArray(1024) { it % tabWidth == 0 }

    private var savedCursor: CursorPosition = CursorPosition(0, 0)
    private var savedAttributes: TextAttributes = TextAttributes.DEFAULT
    private var savedFg: Color = Color.Unspecified
    private var savedBg: Color = Color.Unspecified
    private var savedOriginMode = false
    private var savedAutoWrap = true

    private var _lines: Array<Array<TerminalCell>>
    private val scrollback: ArrayList<Array<TerminalCell>> = ArrayList()
    private var scrollRegionTop = 0
    private var scrollRegionBottom: Int = size.rows - 1

    init {
        _lines = Array(size.rows) { createEmptyLine(size.cols) }
    }

    private fun createEmptyLine(cols: Int): Array<TerminalCell> =
        Array(cols.coerceAtLeast(1)) { TerminalCell() }

    fun getLine(row: Int): Array<TerminalCell> =
        if (row in _lines.indices) _lines[row] else createEmptyLine(size.cols)

    fun getVisibleLines(): Array<Array<TerminalCell>> = _lines

    fun getScrollbackLines(): List<Array<TerminalCell>> = synchronized(scrollback) {
        scrollback.toList()
    }

    fun getScrollbackSize(): Int = synchronized(scrollback) { scrollback.size }

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == size.rows && newCols == size.cols) return

        val oldLines = _lines
        val oldRows = size.rows
        val oldCols = size.cols

        size = TerminalSize(newRows, newCols)
        scrollRegionTop = 0
        scrollRegionBottom = newRows - 1

        _lines = Array(newRows) { row ->
            if (row < oldRows) {
                val oldLine = oldLines[row]
                Array(newCols) { col ->
                    if (col < oldCols) oldLine[col] else TerminalCell()
                }
            } else {
                createEmptyLine(newCols)
            }
        }

        cursor = CursorPosition(
            row = cursor.row.coerceIn(0, newRows - 1),
            col = cursor.col.coerceIn(0, newCols - 1),
        )
    }

    fun setCursorPosition(row: Int, col: Int) {
        val effectiveTop = if (originMode) scrollRegionTop else 0
        val effectiveBottom = if (originMode) scrollRegionBottom else size.rows - 1
        cursor = CursorPosition(
            row = row.coerceIn(effectiveTop, effectiveBottom),
            col = col.coerceIn(0, size.cols - 1),
        )
    }

    fun setCursorRow(row: Int) {
        val effectiveTop = if (originMode) scrollRegionTop else 0
        val effectiveBottom = if (originMode) scrollRegionBottom else size.rows - 1
        cursor = cursor.copy(row = row.coerceIn(effectiveTop, effectiveBottom))
    }

    fun setCursorCol(col: Int) {
        cursor = cursor.copy(col = col.coerceIn(0, size.cols - 1))
    }

    fun moveCursorUp(n: Int = 1) {
        val topBound = if (cursor.row in scrollRegionTop..scrollRegionBottom) scrollRegionTop else 0
        cursor = cursor.copy(row = (cursor.row - n).coerceAtLeast(topBound))
    }

    fun moveCursorDown(n: Int = 1) {
        val bottomBound = if (cursor.row in scrollRegionTop..scrollRegionBottom) {
            scrollRegionBottom
        } else {
            size.rows - 1
        }
        cursor = cursor.copy(row = (cursor.row + n).coerceAtMost(bottomBound))
    }

    fun moveCursorForward(n: Int = 1) {
        cursor = cursor.copy(col = (cursor.col + n).coerceAtMost(size.cols - 1))
    }

    fun moveCursorBackward(n: Int = 1) {
        cursor = cursor.copy(col = (cursor.col - n).coerceAtLeast(0))
    }

    fun setCursorVisible(visible: Boolean) {
        cursorVisible = visible
    }

    fun setCursorStyle(style: CursorStyle) {
        cursorStyle = style
    }

    fun setWindowTitle(title: String) {
        windowTitle = title
    }

    fun setAutoWrapMode(enabled: Boolean) {
        autoWrapMode = enabled
    }

    fun setOriginMode(enabled: Boolean) {
        originMode = enabled
        if (enabled) {
            cursor = CursorPosition(
                row = (cursor.row + scrollRegionTop).coerceIn(scrollRegionTop, scrollRegionBottom),
                col = cursor.col,
            )
        } else {
            cursor = CursorPosition(
                row = (cursor.row - scrollRegionTop).coerceIn(0, size.rows - 1),
                col = cursor.col,
            )
        }
    }

    fun setInsertMode(enabled: Boolean) {
        insertMode = enabled
    }

    fun setWraparoundMode(enabled: Boolean) {
        wraparoundMode = enabled
    }

    fun setBracketedPasteMode(enabled: Boolean) {
        bracketedPasteMode = enabled
    }

    fun setApplicationCursorKeys(enabled: Boolean) {
        applicationCursorKeys = enabled
    }

    fun setApplicationKeypad(enabled: Boolean) {
        applicationKeypad = enabled
    }

    fun setReverseVideo(enabled: Boolean) {
        reverseVideo = enabled
    }

    fun setScrollRegion(top: Int, bottom: Int) {
        scrollRegionTop = top.coerceIn(0, size.rows - 1)
        scrollRegionBottom = bottom.coerceIn(0, size.rows - 1)
        if (scrollRegionTop >= scrollRegionBottom) {
            scrollRegionBottom = scrollRegionTop + 1
            if (scrollRegionBottom >= size.rows) {
                scrollRegionBottom = size.rows - 1
                scrollRegionTop = scrollRegionBottom - 1
            }
        }
        cursor = CursorPosition(row = scrollRegionTop, col = 0)
    }

    fun saveCursor(withAttributes: Boolean = true) {
        savedCursor = cursor
        if (withAttributes) {
            savedOriginMode = originMode
            savedAutoWrap = autoWrapMode
        }
    }

    fun restoreCursor(withAttributes: Boolean = true) {
        cursor = savedCursor
        if (withAttributes) {
            originMode = savedOriginMode
            autoWrapMode = savedAutoWrap
        }
    }

    fun saveAttributes(fg: Color, bg: Color, attrs: TextAttributes) {
        savedFg = fg
        savedBg = bg
        savedAttributes = attrs
    }

    fun restoreAttributes(): Triple<Color, Color, TextAttributes> =
        Triple(savedFg, savedBg, savedAttributes)

    fun setTabStop(col: Int) {
        if (col in tabStops.indices) tabStops[col] = true
    }

    fun clearTabStop(col: Int) {
        if (col in tabStops.indices) tabStops[col] = false
    }

    fun clearAllTabStops() {
        tabStops.fill(false)
    }

    fun setAllTabStops() {
        tabStops.fill(true)
    }

    fun nextTabStop(fromCol: Int): Int {
        for (col in (fromCol + 1) until tabStops.size) {
            if (tabStops[col]) return col.coerceAtMost(size.cols - 1)
        }
        return (size.cols - 1).coerceAtMost(size.cols - 1)
    }

    fun reverseTabStop(fromCol: Int): Int {
        for (col in (fromCol - 1) downTo 0) {
            if (col < tabStops.size && tabStops[col]) return col
        }
        return 0
    }

    fun putChar(ch: Char, fg: Color, bg: Color, attrs: TextAttributes, width: Int = 1) {
        if (cursor.col >= size.cols) {
            if (autoWrapMode) {
                if (wraparoundMode) {
                    if (cursor.row == scrollRegionBottom) {
                        scrollUp(1)
                    } else {
                        cursor = cursor.copy(row = (cursor.row + 1).coerceAtMost(size.rows - 1))
                    }
                }
                cursor = cursor.copy(col = 0)
            } else {
                cursor = cursor.copy(col = size.cols - 1)
            }
        }

        if (width == 2 && cursor.col + 1 >= size.cols) {
            if (cursor.col < size.cols) {
                _lines[cursor.row][cursor.col] = TerminalCell(
                    char = ' ', fgColor = fg, bgColor = bg, attributes = attrs
                )
            }
            if (autoWrapMode && wraparoundMode) {
                if (cursor.row == scrollRegionBottom) {
                    scrollUp(1)
                } else {
                    cursor = cursor.copy(row = (cursor.row + 1).coerceAtMost(size.rows - 1))
                }
                cursor = cursor.copy(col = 0)
            }
        }

        if (insertMode && cursor.col + width < size.cols) {
            val row = cursor.row
            for (i in (size.cols - 1) downTo (cursor.col + width)) {
                if (i - width in _lines[row].indices) {
                    _lines[row][i] = _lines[row][i - width]
                }
            }
        }

        val row = cursor.row
        val col = cursor.col
        if (col in _lines[row].indices) {
            _lines[row][col] = TerminalCell(
                char = ch, fgColor = fg, bgColor = bg, attributes = attrs, width = width
            )
        }
        if (width == 2 && col + 1 in _lines[row].indices) {
            _lines[row][col + 1] = TerminalCell(
                char = '\u0000', fgColor = fg, bgColor = bg, attributes = attrs, width = 0
            )
        }

        val nextCol = col + width
        cursor = if (nextCol >= size.cols) {
            cursor.copy(col = size.cols.coerceAtMost(size.cols))
        } else {
            cursor.copy(col = nextCol)
        }
    }

    fun scrollUp(lines: Int = 1) {
        val count = lines.coerceAtMost(scrollRegionBottom - scrollRegionTop + 1)
        if (count <= 0) return
        for (i in 0 until count) {
            val lineToSave = _lines[scrollRegionTop].copyOf()
            synchronized(scrollback) {
                if (scrollback.size >= maxScrollbackLines) {
                    scrollback.removeAt(0)
                }
                scrollback.add(lineToSave)
            }
            System.arraycopy(
                _lines, scrollRegionTop + 1,
                _lines, scrollRegionTop,
                scrollRegionBottom - scrollRegionTop
            )
            _lines[scrollRegionBottom] = createEmptyLine(size.cols)
        }
    }

    fun scrollDown(lines: Int = 1) {
        val count = lines.coerceAtMost(scrollRegionBottom - scrollRegionTop + 1)
        if (count <= 0) return
        for (i in 0 until count) {
            System.arraycopy(
                _lines, scrollRegionTop,
                _lines, scrollRegionTop + 1,
                scrollRegionBottom - scrollRegionTop
            )
            _lines[scrollRegionTop] = createEmptyLine(size.cols)
        }
    }

    fun insertLines(count: Int = 1) {
        if (cursor.row < scrollRegionTop || cursor.row > scrollRegionBottom) return
        val linesToInsert = count.coerceAtMost(scrollRegionBottom - cursor.row + 1)
        for (i in 0 until linesToInsert) {
            System.arraycopy(
                _lines, cursor.row,
                _lines, cursor.row + 1,
                scrollRegionBottom - cursor.row
            )
            _lines[cursor.row] = createEmptyLine(size.cols)
        }
    }

    fun deleteLines(count: Int = 1) {
        if (cursor.row < scrollRegionTop || cursor.row > scrollRegionBottom) return
        val linesToDelete = count.coerceAtMost(scrollRegionBottom - cursor.row + 1)
        for (i in 0 until linesToDelete) {
            System.arraycopy(
                _lines, cursor.row + 1,
                _lines, cursor.row,
                scrollRegionBottom - cursor.row
            )
            _lines[scrollRegionBottom] = createEmptyLine(size.cols)
        }
    }

    fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseToEndOfLine()
                for (row in (cursor.row + 1) until size.rows) {
                    _lines[row] = createEmptyLine(size.cols)
                }
            }
            1 -> {
                for (row in 0 until cursor.row) {
                    _lines[row] = createEmptyLine(size.cols)
                }
                eraseToStartOfLine()
            }
            2, 3 -> {
                for (row in 0 until size.rows) {
                    _lines[row] = createEmptyLine(size.cols)
                }
                if (mode == 2 || mode == 3) {
                    cursor = CursorPosition(0, 0)
                }
            }
        }
    }

    fun eraseLine(mode: Int) {
        when (mode) {
            0 -> eraseToEndOfLine()
            1 -> eraseToStartOfLine()
            2 -> {
                _lines[cursor.row] = createEmptyLine(size.cols)
            }
        }
    }

    fun eraseToEndOfLine() {
        val row = cursor.row
        for (col in cursor.col until size.cols) {
            if (col in _lines[row].indices) {
                _lines[row][col] = TerminalCell()
            }
        }
    }

    fun eraseToStartOfLine() {
        val row = cursor.row
        for (col in 0..cursor.col.coerceAtMost(size.cols - 1)) {
            if (col in _lines[row].indices) {
                _lines[row][col] = TerminalCell()
            }
        }
    }

    fun eraseCharacters(count: Int = 1) {
        val row = cursor.row
        for (i in 0 until count) {
            val col = cursor.col + i
            if (col in _lines[row].indices) {
                _lines[row][col] = TerminalCell()
            }
        }
    }

    fun deleteCharacters(count: Int = 1) {
        val row = cursor.row
        val n = count.coerceAtMost(size.cols - cursor.col)
        if (n <= 0) return
        val remaining = size.cols - cursor.col - n
        if (remaining > 0 && cursor.col + n in _lines[row].indices) {
            System.arraycopy(
                _lines[row], cursor.col + n,
                _lines[row], cursor.col,
                remaining
            )
        }
        for (i in (size.cols - n) until size.cols) {
            if (i in _lines[row].indices) {
                _lines[row][i] = TerminalCell()
            }
        }
    }

    fun insertCharacters(count: Int = 1) {
        val row = cursor.row
        val n = count.coerceAtMost(size.cols - cursor.col)
        if (n <= 0) return
        val remaining = size.cols - cursor.col - n
        if (remaining > 0 && cursor.col in _lines[row].indices) {
            System.arraycopy(
                _lines[row], cursor.col,
                _lines[row], cursor.col + n,
                remaining
            )
        }
        for (i in cursor.col until (cursor.col + n).coerceAtMost(size.cols)) {
            if (i in _lines[row].indices) {
                _lines[row][i] = TerminalCell()
            }
        }
    }

    fun reverseIndex() {
        if (cursor.row == scrollRegionTop) {
            System.arraycopy(
                _lines, scrollRegionTop,
                _lines, scrollRegionTop + 1,
                scrollRegionBottom - scrollRegionTop
            )
            _lines[scrollRegionTop] = createEmptyLine(size.cols)
        } else {
            cursor = cursor.copy(row = (cursor.row - 1).coerceAtLeast(0))
        }
    }

    fun index() {
        if (cursor.row == scrollRegionBottom) {
            scrollUp(1)
        } else {
            cursor = cursor.copy(row = (cursor.row + 1).coerceAtMost(size.rows - 1))
        }
    }

    fun nextLine() {
        index()
        cursor = cursor.copy(col = 0)
    }

    fun carriageReturn() {
        cursor = cursor.copy(col = 0)
    }

    fun lineFeed() {
        index()
    }

    fun horizontalTab() {
        val next = nextTabStop(cursor.col)
        cursor = cursor.copy(col = next)
    }

    fun reverseHorizontalTab() {
        val prev = reverseTabStop(cursor.col)
        cursor = cursor.copy(col = prev)
    }

    fun backspace() {
        cursor = cursor.copy(col = (cursor.col - 1).coerceAtLeast(0))
    }

    fun bell() {
        // Could trigger audio/vibration callback
    }

    fun indexLine() = index()

    fun reverseIndexLine() = reverseIndex()

    fun clear() {
        for (row in 0 until size.rows) {
            _lines[row] = createEmptyLine(size.cols)
        }
        cursor = CursorPosition(0, 0)
        synchronized(scrollback) {
            scrollback.clear()
        }
    }

    fun setScrollRegionToAll() {
        scrollRegionTop = 0
        scrollRegionBottom = size.rows - 1
    }
}
