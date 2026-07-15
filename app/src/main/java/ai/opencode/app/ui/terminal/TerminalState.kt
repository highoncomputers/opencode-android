package ai.opencode.app.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

class TerminalState(
    rows: Int = 24,
    cols: Int = 80,
) {
    val buffer = TerminalBuffer(rows, cols)

    var currentFg: Color by mutableStateOf(defaultFgColor)
    var currentBg: Color by mutableStateOf(defaultBgColor)
    var currentAttributes by mutableStateOf(TextAttributes.DEFAULT)

    var screenMode by mutableStateOf(false)
    var originMode by mutableStateOf(false)

    var needsRender by mutableStateOf(false)
        private set

    private var parseState = ParseState.GROUND
    private val csiParams = StringBuilder()
    private val csiIntermediate = StringBuilder()
    private val oscBuffer = StringBuilder()
    private var oscCommand = -1

    private var charsetG0 = 'B'
    private var charsetG1 = '0'
    private var selectedCharset = 0

    private enum class ParseState {
        GROUND, ESCAPE, CSI, OSC, OSC_ESC, DCS, DCS_STRING, SOS, PM, APC
    }

    companion object {
        val defaultFgColor = Color(0xFFCCCCCC)
        val defaultBgColor = Color.Transparent

        private val ansiStandardColors = intArrayOf(
            0xFF000000.toInt(), 0xFFCC3333.toInt(), 0xFF33CC33.toInt(), 0xFFCCCC33.toInt(),
            0xFF3333CC.toInt(), 0xFFCC33CC.toInt(), 0xFF33CCCC.toInt(), 0xFFCCCCCC.toInt(),
        )

        private val ansiBrightColors = intArrayOf(
            0xFF555555.toInt(), 0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFFF55.toInt(),
            0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFFFF.toInt(),
        )

        private val xterm256Colors: Array<Color> = Array(256) { index ->
            when {
                index < 16 -> Color(if (index < 8) ansiStandardColors[index] else ansiBrightColors[index - 8])
                index < 232 -> {
                    val i = index - 16
                    val r = i / 36
                    val g = (i / 6) % 6
                    val b = i % 6
                    val red = if (r == 0) 0 else 55 + r * 40
                    val green = if (g == 0) 0 else 55 + g * 40
                    val blue = if (b == 0) 0 else 55 + b * 40
                    Color(red, green, blue)
                }
                else -> {
                    val gray = 8 + (index - 232) * 10
                    Color(gray, gray, gray)
                }
            }
        }

        fun getColor(index: Int): Color = when {
            index < 0 -> defaultFgColor
            index < 256 -> xterm256Colors[index]
            else -> defaultFgColor
        }

        fun cjkWidth(codePoint: Int): Int = when {
            codePoint in 0x1100..0x115F -> 2
            codePoint in 0x2E80..0x303E -> 2
            codePoint in 0x3040..0x309F -> 1
            codePoint in 0x30A0..0x30FF -> 2
            codePoint in 0x3100..0x312F -> 2
            codePoint in 0x3130..0x318F -> 2
            codePoint in 0x3200..0x32FF -> 2
            codePoint in 0x3400..0x4DBF -> 2
            codePoint in 0x4E00..0x9FFF -> 2
            codePoint in 0xA000..0xA4CF -> 2
            codePoint in 0xAC00..0xD7AF -> 2
            codePoint in 0xF900..0xFAFF -> 2
            codePoint in 0xFE30..0xFE6F -> 2
            codePoint in 0xFF01..0xFF60 -> 2
            codePoint in 0xFFE0..0xFFE6 -> 2
            codePoint in 0x20000..0x2FA1F -> 2
            codePoint in 0x30000..0x3134F -> 2
            codePoint in 0x1F000..0x1F02F -> 2
            codePoint in 0x1F200..0x1F2FF -> 2
            codePoint in 0x1F300..0x1F9FF -> 2
            codePoint in 0x20000..0x2FFFD -> 2
            codePoint in 0x30000..0x3FFFD -> 2
            else -> 1
        }

        private fun decodeUtf8(data: ByteArray, offset: Int): Pair<Int, Int> {
            val b0 = data[offset].toInt() and 0xFF
            return when {
                b0 < 0x80 -> Pair(b0, 1)
                b0 and 0xE0 == 0xC0 -> {
                    if (offset + 1 >= data.size) return Pair(-1, 0)
                    val b1 = data[offset + 1].toInt() and 0xFF
                    val cp = ((b0 and 0x1F) shl 6) or (b1 and 0x3F)
                    Pair(cp, 2)
                }
                b0 and 0xF0 == 0xE0 -> {
                    if (offset + 2 >= data.size) return Pair(-1, 0)
                    val b1 = data[offset + 1].toInt() and 0xFF
                    val b2 = data[offset + 2].toInt() and 0xFF
                    val cp = ((b0 and 0x0F) shl 12) or ((b1 and 0x3F) shl 6) or (b2 and 0x3F)
                    Pair(cp, 3)
                }
                b0 and 0xF8 == 0xF0 -> {
                    if (offset + 3 >= data.size) return Pair(-1, 0)
                    val b1 = data[offset + 1].toInt() and 0xFF
                    val b2 = data[offset + 2].toInt() and 0xFF
                    val b3 = data[offset + 3].toInt() and 0xFF
                    val cp = ((b0 and 0x07) shl 18) or ((b1 and 0x3F) shl 12) or
                            ((b2 and 0x3F) shl 6) or (b3 and 0x3F)
                    Pair(cp, 4)
                }
                else -> Pair(0xFFFD, 1)
            }
        }

        private fun decodeLineDrawing(ch: Char): Char = when (ch) {
            'j' -> '\u2518'
            'k' -> '\u2510'
            'l' -> '\u250C'
            'm' -> '\u2514'
            'n' -> '\u2534'
            'q' -> '\u2500'
            't' -> '\u251C'
            'u' -> '\u2524'
            'v' -> '\u253C'
            'w' -> '\u252C'
            'x' -> '\u2502'
            'a' -> '\u2591'
            '0' -> '\u2588'
            '1' -> '\u2592'
            '2' -> '\u2593'
            'f' -> '\u00B0'
            'g' -> '\u00B1'
            'h' -> '\u2592'
            'i' -> '\u2666'
            'j' -> '\u2518'
            'n' -> '\u2534'
            '~' -> '\u2500'
            else -> ch
        }

        private fun applyCharset(ch: Char, g: Char): Char = when (g) {
            '0' -> decodeLineDrawing(ch)
            'A' -> ch
            'B' -> ch
            else -> ch
        }
    }

    fun consumeBytes(data: ByteArray) {
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when (parseState) {
                ParseState.GROUND -> {
                    when (b) {
                        0x00 -> { /* NUL */ }
                        0x05 -> {
                            // ENQ - send answerback
                        }
                        0x07 -> {
                            buffer.bell()
                        }
                        0x08 -> {
                            buffer.backspace()
                        }
                        0x09 -> {
                            buffer.horizontalTab()
                        }
                        0x0A, 0x0B, 0x0C -> {
                            buffer.lineFeed()
                            markDirty()
                        }
                        0x0D -> {
                            buffer.carriageReturn()
                        }
                        0x0E -> {
                            selectedCharset = 0
                        }
                        0x0F -> {
                            selectedCharset = 1
                        }
                        0x1B -> {
                            parseState = ParseState.ESCAPE
                            csiParams.clear()
                            csiIntermediate.clear()
                        }
                        else -> {
                            if (b in 0x20..0x7E || b >= 0xC0) {
                                var ch: Char
                                var charWidth = 1

                                if (b < 0x80) {
                                    ch = b.toChar()
                                } else {
                                    val (codePoint, bytesConsumed) = decodeUtf8(data, i)
                                    if (codePoint == -1) {
                                        i++
                                        continue
                                    }
                                    i += bytesConsumed - 1

                                    ch = try {
                                        String(Character.toChars(codePoint))[0]
                                    } catch (_: Exception) {
                                        '?'
                                    }

                                    charWidth = cjkWidth(codePoint)
                                    if (charWidth > 1) {
                                        // Zero-width joiner and combining chars
                                        if (codePoint in 0x200B..0x200F ||
                                            codePoint in 0x2028..0x2029 ||
                                            codePoint in 0xFE00..0xFE0F ||
                                            codePoint in 0x200C..0x200D
                                        ) {
                                            charWidth = 0
                                        }
                                    }
                                }

                                val activeCharset = when (selectedCharset) {
                                    0 -> charsetG0
                                    1 -> charsetG1
                                    else -> 'B'
                                }

                                if (ch in '\u0020'..'\u007E' && activeCharset != 'B') {
                                    ch = applyCharset(ch, activeCharset)
                                }

                                if (charWidth > 0) {
                                    buffer.putChar(ch, currentFg, currentBg, currentAttributes, charWidth)
                                    markDirty()
                                } else {
                                    // Zero-width: just update the cell without advancing cursor
                                    // For combining chars, overwrite previous cell
                                    val col = (buffer.cursor.col - 1).coerceAtLeast(0)
                                    if (col >= 0 && col < buffer.size.cols) {
                                        // Ignore combining marks for simplicity
                                    }
                                }
                            }
                        }
                    }
                }

                ParseState.ESCAPE -> {
                    when (b) {
                        0x5B -> { // [
                            parseState = ParseState.CSI
                            csiParams.clear()
                            csiIntermediate.clear()
                        }
                        0x5D -> { // ]
                            parseState = ParseState.OSC
                            oscBuffer.clear()
                            oscCommand = -1
                        }
                        0x50 -> { // P - DCS
                            parseState = ParseState.DCS
                            oscBuffer.clear()
                        }
                        0x58 -> { // X - SOS
                            parseState = ParseState.SOS
                        }
                        0x5E -> { // ^ - PM
                            parseState = ParseState.PM
                        }
                        0x5F -> { // _ - APC
                            parseState = ParseState.APC
                        }
                        0x20 -> { // Space - skip intermediate
                            // Some terminals use ESC space before final char
                        }
                        0x23 -> { // # - ESC #
                            // Could be ESC # 8 for screen alignment test
                        }
                        0x28 -> { // ( - designate G0 charset
                            parseState = ParseState.ESCAPE
                            charsetG0 = 'B'
                        }
                        0x29 -> { // ) - designate G1 charset
                            parseState = ParseState.ESCAPE
                            charsetG1 = 'B'
                        }
                        0x37 -> { // 7 - DECSC (save cursor + attributes)
                            buffer.saveCursor()
                            parseState = ParseState.GROUND
                        }
                        0x38 -> { // 8 - DECRC (restore cursor + attributes)
                            buffer.restoreCursor()
                            parseState = ParseState.GROUND
                        }
                        0x3D -> { // = - Application keypad
                            buffer.setApplicationKeypad(true)
                            parseState = ParseState.GROUND
                        }
                        0x3E -> { // > - Normal keypad
                            buffer.setApplicationKeypad(false)
                            parseState = ParseState.GROUND
                        }
                        0x43 -> { // C - IND (index)
                            buffer.indexLine()
                            parseState = ParseState.GROUND
                        }
                        0x44 -> { // D - RI (reverse index) - no, D is IND on VT100
                            buffer.indexLine()
                            parseState = ParseState.GROUND
                        }
                        0x45 -> { // E - NEL (next line)
                            buffer.nextLine()
                            markDirty()
                            parseState = ParseState.GROUND
                        }
                        0x48 -> { // H - HTS (set tab stop at current col)
                            buffer.setTabStop(buffer.cursor.col)
                            parseState = ParseState.GROUND
                        }
                        0x4D -> { // M - RI (reverse index)
                            buffer.reverseIndexLine()
                            markDirty()
                            parseState = ParseState.GROUND
                        }
                        0x63 -> { // c - RIS (full reset)
                            fullReset()
                            markDirty()
                            parseState = ParseState.GROUND
                        }
                        0x6E -> { // n - Invoke G2 charset as GL
                            selectedCharset = 1
                            parseState = ParseState.GROUND
                        }
                        0x6F -> { // o - Invoke G3 charset as GL
                            selectedCharset = 1
                            parseState = ParseState.GROUND
                        }
                        0x7C -> { // | - Invoke G3 as GR
                            parseState = ParseState.GROUND
                        }
                        0x7D -> { // } - Invoke G2 as GR
                            parseState = ParseState.GROUND
                        }
                        0x7E -> { // ~ - Invoke G1 as GR
                            parseState = ParseState.GROUND
                        }
                        else -> {
                            // Unknown ESC sequence, return to ground
                            parseState = ParseState.GROUND
                        }
                    }
                }

                ParseState.CSI -> {
                    when {
                        b in 0x30..0x3F -> csiParams.append(b.toChar())
                        b in 0x20..0x2F -> csiIntermediate.append(b.toChar())
                        b in 0x40..0x7E -> {
                            val finalChar = b.toChar()
                            val params = csiParams.toString()
                            val intermediate = csiIntermediate.toString()
                            handleCsi(params, intermediate, finalChar)
                            parseState = ParseState.GROUND
                            markDirty()
                        }
                        else -> {
                            parseState = ParseState.GROUND
                        }
                    }
                }

                ParseState.OSC -> {
                    when (b) {
                        0x07 -> {
                            handleOsc(oscBuffer.toString())
                            parseState = ParseState.GROUND
                        }
                        0x1B -> {
                            parseState = ParseState.OSC_ESC
                        }
                        else -> {
                            if (b in 0x20..0x7E) {
                                oscBuffer.append(b.toChar())
                            }
                        }
                    }
                }

                ParseState.OSC_ESC -> {
                    if (b == 0x5C) { // \ - ST
                        handleOsc(oscBuffer.toString())
                    }
                    parseState = ParseState.GROUND
                }

                ParseState.DCS -> {
                    when (b) {
                        0x07, 0x1B -> {
                            // DCS payload complete (DCS terminated by ST)
                            parseState = if (b == 0x1B) ParseState.OSC_ESC else ParseState.GROUND
                        }
                        else -> {
                            if (b in 0x20..0x7E) {
                                oscBuffer.append(b.toChar())
                            }
                        }
                    }
                }

                ParseState.DCS_STRING -> {
                    // Consume DCS string data until ST
                    if (b == 0x1B) {
                        parseState = ParseState.OSC_ESC
                    }
                }

                ParseState.SOS, ParseState.PM, ParseState.APC -> {
                    // Skip until ST
                    if (b == 0x1B) {
                        parseState = ParseState.OSC_ESC
                    }
                }
            }
            i++
        }
    }

    private fun parseCsiParams(params: String): IntArray {
        if (params.isEmpty()) return intArrayOf()
        return params.split(';').map { seg ->
            seg.trim().ifEmpty { "0" }.toIntOrNull() ?: 0
        }.toIntArray()
    }

    private fun parseCsiParam(params: String, index: Int, default: Int): Int {
        val parts = params.split(';')
        return if (index < parts.size) {
            parts[index].trim().ifEmpty { default.toString() }.toIntOrNull() ?: default
        } else {
            default
        }
    }

    private fun handleCsi(params: String, intermediate: String, finalChar: Char) {
        val parsed = parseCsiParams(params)

        when (finalChar) {
            'A' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursorUp(n)
            }
            'B' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursorDown(n)
            }
            'C' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursorForward(n)
            }
            'D' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursorBackward(n)
            }
            'E' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.setCursorRow(buffer.cursor.row + n)
                buffer.setCursorCol(0)
            }
            'F' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.setCursorRow(buffer.cursor.row - n)
                buffer.setCursorCol(0)
            }
            'G' -> {
                val col = parsed.getOrElse(0) { 1 }.coerceAtLeast(1) - 1
                buffer.setCursorCol(col)
            }
            'H', 'f' -> {
                val row = parsed.getOrElse(0) { 1 }.coerceAtLeast(1) - 1
                val col = parsed.getOrElse(1) { 1 }.coerceAtLeast(1) - 1
                buffer.setCursorPosition(row, col)
            }
            'J' -> {
                val mode = parsed.getOrElse(0) { 0 }
                buffer.eraseDisplay(mode)
            }
            'K' -> {
                val mode = parsed.getOrElse(0) { 0 }
                buffer.eraseLine(mode)
            }
            'L' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.insertLines(n)
            }
            'M' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.deleteLines(n)
            }
            'P' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.deleteCharacters(n)
            }
            '@' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.insertCharacters(n)
            }
            'S' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.scrollUp(n)
            }
            'T' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.scrollDown(n)
            }
            'X' -> {
                val n = parsed.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.eraseCharacters(n)
            }
            'd' -> {
                val row = parsed.getOrElse(0) { 1 }.coerceAtLeast(1) - 1
                buffer.setCursorRow(row)
            }
            'b' -> {
                // REP - Repeat character, ignored for now
            }
            'm' -> {
                handleSgr(parsed)
            }
            'r' -> {
                val top = parsed.getOrElse(0) { 1 }.coerceAtLeast(1) - 1
                val bottom = parsed.getOrElse(1) { buffer.size.rows }.coerceAtLeast(1) - 1
                buffer.setScrollRegion(top, bottom)
            }
            'n' -> {
                // DSR - Device Status Report, needs to respond
                // 6 = cursor position, 5 = OK
            }
            'c' -> {
                // DA - Device Attributes
                // Would respond with terminal identification
            }
            'h' -> {
                handleSetMode(parsed, intermediate)
            }
            'l' -> {
                handleResetMode(parsed, intermediate)
            }
            's' -> {
                buffer.saveCursor()
            }
            'u' -> {
                buffer.restoreCursor()
            }
            'g' -> {
                val mode = parsed.getOrElse(0) { 0 }
                when (mode) {
                    0 -> buffer.clearTabStop(buffer.cursor.col)
                    3 -> buffer.clearAllTabStops()
                }
            }
            'i' -> {
                // MC - Media Copy (print), ignore
            }
            'p' -> {
                if (intermediate == "!") {
                    // DECSTR - Soft terminal reset
                    fullReset()
                }
            }
            'q' -> {
                if (intermediate == " ") {
                    // DECSCUSR - Set cursor style
                    val style = parsed.getOrElse(0) { 0 }
                    when (style) {
                        0, 1 -> buffer.setCursorStyle(CursorStyle.BLOCK)
                        2 -> buffer.setCursorStyle(CursorStyle.UNDERLINE)
                        3 -> buffer.setCursorStyle(CursorStyle.BEAM)
                    }
                }
            }
        }
    }

    private fun handleSetMode(params: IntArray, intermediate: String) {
        if (intermediate == "?") {
            for (p in params) {
                when (p) {
                    1 -> buffer.setApplicationCursorKeys(true)
                    3 -> buffer.resize(buffer.size.rows, 132)
                    5 -> {
                        screenMode = true
                        buffer.setReverseVideo(true)
                    }
                    6 -> {
                        originMode = true
                        buffer.setOriginMode(true)
                    }
                    7 -> buffer.setAutoWrapMode(true)
                    9 -> { /* X10 mouse tracking */ }
                    12 -> buffer.setCursorStyle(CursorStyle.BEAM)
                    25 -> buffer.setCursorVisible(true)
                    47 -> { /* Alternate screen buffer */ }
                    69 -> { /* Left/right margin mode */ }
                    95 -> { /* No clear screen on DECCOLM */ }
                    1000 -> { /* X11 mouse tracking */ }
                    1002 -> { /* Button-event mouse tracking */ }
                    1003 -> { /* Any-event mouse tracking */ }
                    1004 -> { /* Focus events */ }
                    1006 -> { /* SGR mouse mode */ }
                    1015 -> { /* UTX mouse mode */ }
                    1034 -> { /* Interpret Meta key */ }
                    1049 -> {
                        buffer.saveCursor()
                        buffer.clear()
                    }
                    2004 -> buffer.setBracketedPasteMode(true)
                }
            }
        } else {
            for (p in params) {
                when (p) {
                    2 -> { /* Keyboard locked */ }
                    4 -> buffer.setInsertMode(true)
                    20 -> buffer.setAutoWrapMode(true)
                }
            }
        }
    }

    private fun handleResetMode(params: IntArray, intermediate: String) {
        if (intermediate == "?") {
            for (p in params) {
                when (p) {
                    1 -> buffer.setApplicationCursorKeys(false)
                    3 -> buffer.resize(buffer.size.rows, 80)
                    5 -> {
                        screenMode = false
                        buffer.setReverseVideo(false)
                    }
                    6 -> {
                        originMode = false
                        buffer.setOriginMode(false)
                    }
                    7 -> buffer.setAutoWrapMode(false)
                    9 -> { /* Disable X10 mouse */ }
                    12 -> buffer.setCursorStyle(CursorStyle.BLOCK)
                    25 -> buffer.setCursorVisible(false)
                    47 -> { /* Normal screen buffer */ }
                    69 -> { /* Disable left/right margins */ }
                    1049 -> {
                        buffer.clear()
                        buffer.restoreCursor()
                    }
                    2004 -> buffer.setBracketedPasteMode(false)
                }
            }
        } else {
            for (p in params) {
                when (p) {
                    2 -> { /* Keyboard unlocked */ }
                    4 -> buffer.setInsertMode(false)
                    20 -> buffer.setAutoWrapMode(false)
                }
            }
        }
    }

    private fun handleSgr(params: IntArray) {
        if (params.isEmpty() || (params.size == 1 && params[0] == 0)) {
            resetAttributes()
            return
        }

        var i = 0
        while (i < params.size) {
            val p = params[i]
            when (p) {
                0 -> resetAttributes()
                1 -> currentAttributes = currentAttributes.copy(bold = true)
                2 -> currentAttributes = currentAttributes.copy(dim = true)
                3 -> currentAttributes = currentAttributes.copy(italic = true)
                4 -> {
                    val style = if (i + 1 < params.size && params[i + 1] in 1..5) {
                        params[++i]
                    } else 1
                    currentAttributes = currentAttributes.copy(
                        underline = style in 1..3,
                        overline = style == 5
                    )
                }
                5 -> currentAttributes = currentAttributes.copy(blink = true)
                7 -> currentAttributes = currentAttributes.copy(inverse = true)
                8 -> currentAttributes = currentAttributes.copy(hidden = true)
                9 -> currentAttributes = currentAttributes.copy(strikethrough = true)
                21 -> currentAttributes = currentAttributes.copy(bold = true)
                22 -> currentAttributes = currentAttributes.copy(bold = false, dim = false)
                23 -> currentAttributes = currentAttributes.copy(italic = false)
                24 -> currentAttributes = currentAttributes.copy(underline = false, overline = false)
                25 -> currentAttributes = currentAttributes.copy(blink = false)
                27 -> currentAttributes = currentAttributes.copy(inverse = false)
                28 -> currentAttributes = currentAttributes.copy(hidden = false)
                29 -> currentAttributes = currentAttributes.copy(strikethrough = false)

                in 30..37 -> currentFg = getColor(p - 30)
                38 -> {
                    when {
                        i + 1 < params.size && params[i + 1] == 5 -> {
                            if (i + 2 < params.size) {
                                currentFg = getColor(params[i + 2])
                                i += 2
                            }
                        }
                        i + 1 < params.size && params[i + 1] == 2 -> {
                            if (i + 4 < params.size) {
                                val r = params[i + 2].coerceIn(0, 255)
                                val g = params[i + 3].coerceIn(0, 255)
                                val b = params[i + 4].coerceIn(0, 255)
                                currentFg = Color(r, g, b)
                                i += 4
                            }
                        }
                    }
                }
                39 -> currentFg = defaultFgColor

                in 40..47 -> currentBg = getColor(p - 40)
                48 -> {
                    when {
                        i + 1 < params.size && params[i + 1] == 5 -> {
                            if (i + 2 < params.size) {
                                currentBg = getColor(params[i + 2])
                                i += 2
                            }
                        }
                        i + 1 < params.size && params[i + 1] == 2 -> {
                            if (i + 4 < params.size) {
                                val r = params[i + 2].coerceIn(0, 255)
                                val g = params[i + 3].coerceIn(0, 255)
                                val b = params[i + 4].coerceIn(0, 255)
                                currentBg = Color(r, g, b)
                                i += 4
                            }
                        }
                    }
                }
                49 -> currentBg = defaultBgColor

                in 90..97 -> currentFg = getColor(p - 90 + 8)
                in 100..107 -> currentBg = getColor(p - 100 + 8)
            }
            i++
        }
    }

    private fun resetAttributes() {
        currentAttributes = TextAttributes.DEFAULT
        currentFg = defaultFgColor
        currentBg = defaultBgColor
    }

    private fun handleOsc(osc: String) {
        val separatorIndex = osc.indexOf(';')
        val command = if (separatorIndex >= 0) {
            osc.substring(0, separatorIndex).toIntOrNull() ?: -1
        } else {
            -1
        }
        val value = if (separatorIndex >= 0) osc.substring(separatorIndex + 1) else ""

        when (command) {
            0, 2 -> buffer.setWindowTitle(value)
            4 -> { /* Change/query color number */ }
            10 -> { /* Set default foreground color */ }
            11 -> { /* Set default background color */ }
            52 -> { /* Clipboard control */ }
            104 -> { /* Reset color */ }
            112 -> { /* Reset cursor color */ }
        }
    }

    fun fullReset() {
        parseState = ParseState.GROUND
        resetAttributes()
        buffer.clear()
        buffer.setCursorVisible(true)
        buffer.setCursorStyle(CursorStyle.BLOCK)
        buffer.setAutoWrapMode(true)
        buffer.setOriginMode(false)
        buffer.setInsertMode(false)
        buffer.setWraparoundMode(true)
        buffer.setBracketedPasteMode(false)
        buffer.setApplicationCursorKeys(false)
        buffer.setApplicationKeypad(false)
        buffer.setReverseVideo(false)
        buffer.setScrollRegionToAll()
        screenMode = false
        originMode = false
        charsetG0 = 'B'
        charsetG1 = '0'
        selectedCharset = 0
        for (i in buffer.tabStops.indices step 8) {
            buffer.tabStops[i] = true
        }
        for (i in 1 until buffer.tabStops.size step 8) {
            buffer.tabStops[i] = false
        }
    }

    fun resize(rows: Int, cols: Int) {
        buffer.resize(rows, cols)
        markDirty()
    }

    fun consumeBytes(data: ByteArray, offset: Int, length: Int) {
        if (offset == 0 && length == data.size) {
            consumeBytes(data)
        } else {
            val slice = data.copyOfRange(offset, offset + length)
            consumeBytes(slice)
        }
    }

    fun markDirty() {
        needsRender = true
    }

    fun getCharAt(row: Int, col: Int): TerminalCell {
        val lines = buffer.getVisibleLines()
        return if (row in lines.indices && col in lines[row].indices) {
            lines[row][col]
        } else {
            TerminalCell()
        }
    }
}
