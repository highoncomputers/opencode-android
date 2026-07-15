package ai.opencode.app.ui.terminal

import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VT100Sequences {
    val ESC = byteArrayOf(0x1B)
    val TAB = byteArrayOf(0x09)
    val ENTER = byteArrayOf(0x0D)
    val BACKSPACE = byteArrayOf(0x7F)
    val DELETE = byteArrayOf(0x1B, 0x5B, 0x33, 0x7E)
    val PIPE = byteArrayOf(0x7C)
    val SLASH = byteArrayOf(0x2F)
    val TILDE = byteArrayOf(0x7E)
    val BACKTICK = byteArrayOf(0x60)
    val MINUS = byteArrayOf(0x2D)
    val EQUALS = byteArrayOf(0x3D)
    val PERIOD = byteArrayOf(0x2E)
    val COMMA = byteArrayOf(0x2C)
    val SEMICOLON = byteArrayOf(0x3B)
    val QUOTE = byteArrayOf(0x27)
    val LBRACKET = byteArrayOf(0x5B)
    val RBRACKET = byteArrayOf(0x5D)
    val BACKSLASH = byteArrayOf(0x5C)

    fun arrowUp(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x41)
    fun arrowDown(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x42)
    fun arrowRight(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x43)
    fun arrowLeft(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x44)

    fun home(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x48)
    fun end(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x46)
    fun pageUp(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x35, 0x7E)
    fun pageDown(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x36, 0x7E)
    fun insert(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x32, 0x7E)

    fun functionKey(n: Int): ByteArray = when (n) {
        1 -> byteArrayOf(0x1B, 0x4F, 0x50)
        2 -> byteArrayOf(0x1B, 0x4F, 0x51)
        3 -> byteArrayOf(0x1B, 0x4F, 0x52)
        4 -> byteArrayOf(0x1B, 0x4F, 0x53)
        5 -> byteArrayOf(0x1B, 0x5B, 0x31, 0x35, 0x7E)
        6 -> byteArrayOf(0x1B, 0x5B, 0x31, 0x37, 0x7E)
        7 -> byteArrayOf(0x1B, 0x5B, 0x31, 0x38, 0x7E)
        8 -> byteArrayOf(0x1B, 0x5B, 0x31, 0x39, 0x7E)
        9 -> byteArrayOf(0x1B, 0x5B, 0x32, 0x30, 0x7E)
        10 -> byteArrayOf(0x1B, 0x5B, 0x32, 0x31, 0x7E)
        11 -> byteArrayOf(0x1B, 0x5B, 0x32, 0x33, 0x7E)
        12 -> byteArrayOf(0x1B, 0x5B, 0x32, 0x34, 0x7E)
        else -> byteArrayOf()
    }

    fun ctrlKey(c: Char): ByteArray {
        val code = (c.lowercaseChar().code - 'a'.code + 1).toByte()
        if (code < 0x01 || code > 0x1A) return byteArrayOf()
        return byteArrayOf(code)
    }

    fun altKey(c: Char): ByteArray = byteArrayOf(0x1B, c.code.toByte())

    fun ctrlArrowUp(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x35, 0x41)
    fun ctrlArrowDown(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x35, 0x42)
    fun ctrlArrowRight(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x35, 0x43)
    fun ctrlArrowLeft(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x35, 0x44)

    fun shiftHome(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x48)
    fun shiftEnd(): ByteArray = byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x46)

    fun keyToSequence(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): ByteArray? {
        if (ctrl && !alt) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> ctrlArrowUp()
                KeyEvent.KEYCODE_DPAD_DOWN -> ctrlArrowDown()
                KeyEvent.KEYCODE_DPAD_RIGHT -> ctrlArrowRight()
                KeyEvent.KEYCODE_DPAD_LEFT -> ctrlArrowLeft()
                else -> {
                    val letter = when (keyCode) {
                        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                            ('a' + (keyCode - KeyEvent.KEYCODE_A))
                        else -> return null
                    }
                    ctrlKey(letter.toChar())
                }
            }
        }

        if (alt && !ctrl) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> byteArrayOf(0x1B, 0x1B, 0x5B, 0x41)
                KeyEvent.KEYCODE_DPAD_DOWN -> byteArrayOf(0x1B, 0x1B, 0x5B, 0x42)
                KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B, 0x1B, 0x5B, 0x43)
                KeyEvent.KEYCODE_DPAD_LEFT -> byteArrayOf(0x1B, 0x1B, 0x5B, 0x44)
                else -> {
                    val letter = when (keyCode) {
                        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                            ('a' + (keyCode - KeyEvent.KEYCODE_A))
                        else -> return null
                    }
                    altKey(letter.toChar())
                }
            }
        }

        if (shift) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> arrowUp()
                KeyEvent.KEYCODE_DPAD_DOWN -> arrowDown()
                KeyEvent.KEYCODE_DPAD_RIGHT -> arrowRight()
                KeyEvent.KEYCODE_DPAD_LEFT -> arrowLeft()
                KeyEvent.KEYCODE_MOVE_HOME -> shiftHome()
                KeyEvent.KEYCODE_MOVE_END -> shiftEnd()
                else -> null
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> arrowUp()
            KeyEvent.KEYCODE_DPAD_DOWN -> arrowDown()
            KeyEvent.KEYCODE_DPAD_RIGHT -> arrowRight()
            KeyEvent.KEYCODE_DPAD_LEFT -> arrowLeft()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> ENTER
            KeyEvent.KEYCODE_DEL -> BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> DELETE
            KeyEvent.KEYCODE_TAB -> TAB
            KeyEvent.KEYCODE_PAGE_UP -> pageUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> pageDown()
            KeyEvent.KEYCODE_MOVE_HOME -> home()
            KeyEvent.KEYCODE_MOVE_END -> end()
            KeyEvent.KEYCODE_INSERT -> insert()
            KeyEvent.KEYCODE_F1 -> functionKey(1)
            KeyEvent.KEYCODE_F2 -> functionKey(2)
            KeyEvent.KEYCODE_F3 -> functionKey(3)
            KeyEvent.KEYCODE_F4 -> functionKey(4)
            KeyEvent.KEYCODE_F5 -> functionKey(5)
            KeyEvent.KEYCODE_F6 -> functionKey(6)
            KeyEvent.KEYCODE_F7 -> functionKey(7)
            KeyEvent.KEYCODE_F8 -> functionKey(8)
            KeyEvent.KEYCODE_F9 -> functionKey(9)
            KeyEvent.KEYCODE_F10 -> functionKey(10)
            KeyEvent.KEYCODE_F11 -> functionKey(11)
            KeyEvent.KEYCODE_F12 -> functionKey(12)
            KeyEvent.KEYCODE_ESCAPE -> ESC
            else -> null
        }
    }

    fun textToPasteBracketed(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val prefix = "\u001B[200~".toByteArray(Charsets.UTF_8)
        val suffix = "\u001B[201~".toByteArray(Charsets.UTF_8)
        return prefix + bytes + suffix
    }

    fun textToBytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)
}

@Composable
fun TerminalInputBar(
    onKeyInput: (ByteArray) -> Unit,
    onPaste: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ctrlMode by remember { mutableStateOf(false) }
    var altMode by remember { mutableStateOf(false) }
    var shiftMode by remember { mutableStateOf(false) }

    val buttonStyle = TextStyle(
        color = Color(0xFFCCCCCC),
        fontSize = 12.sp,
    )

    val activeButtonStyle = TextStyle(
        color = Color(0xFF4FC1FF),
        fontSize = 12.sp,
    )

    @Composable
    fun KeyButton(
        label: String,
        onClick: () -> Unit,
        isActive: Boolean = false,
        wide: Boolean = false,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier
                .then(if (wide) Modifier.width(42.dp) else Modifier)
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isActive) Color(0xFF264F78) else Color(0xFF2D2D2D))
                .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = if (isActive) activeButtonStyle else buttonStyle,
                maxLines = 1,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        // Row 1: modifiers + arrows + common symbols
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyButton(
                label = "Esc",
                onClick = { onKeyInput(VT100Sequences.ESC) },
            )
            KeyButton(
                label = "Tab",
                onClick = { onKeyInput(VT100Sequences.TAB) },
            )
            KeyButton(
                label = "Ctrl",
                isActive = ctrlMode,
                onClick = { ctrlMode = !ctrlMode },
            )
            KeyButton(
                label = "Alt",
                isActive = altMode,
                onClick = { altMode = !altMode },
            )
            KeyButton(
                label = "Shift",
                isActive = shiftMode,
                onClick = { shiftMode = !shiftMode },
            )

            Spacer(modifier = Modifier.width(6.dp))

            KeyButton(
                label = "\u2190",
                onClick = {
                    val seq = if (ctrlMode) VT100Sequences.ctrlArrowLeft() else VT100Sequences.arrowLeft()
                    onKeyInput(seq)
                    ctrlMode = false; altMode = false; shiftMode = false
                },
            )
            KeyButton(
                label = "\u2191",
                onClick = {
                    val seq = if (ctrlMode) VT100Sequences.ctrlArrowUp() else VT100Sequences.arrowUp()
                    onKeyInput(seq)
                    ctrlMode = false; altMode = false; shiftMode = false
                },
            )
            KeyButton(
                label = "\u2193",
                onClick = {
                    val seq = if (ctrlMode) VT100Sequences.ctrlArrowDown() else VT100Sequences.arrowDown()
                    onKeyInput(seq)
                    ctrlMode = false; altMode = false; shiftMode = false
                },
            )
            KeyButton(
                label = "\u2192",
                onClick = {
                    val seq = if (ctrlMode) VT100Sequences.ctrlArrowRight() else VT100Sequences.arrowRight()
                    onKeyInput(seq)
                    ctrlMode = false; altMode = false; shiftMode = false
                },
            )

            Spacer(modifier = Modifier.width(6.dp))

            KeyButton(
                label = "|",
                onClick = { onKeyInput(VT100Sequences.PIPE) },
            )
            KeyButton(
                label = "/",
                onClick = { onKeyInput(VT100Sequences.SLASH) },
            )
            KeyButton(
                label = "~",
                onClick = { onKeyInput(VT100Sequences.TILDE) },
            )
            KeyButton(
                label = "`",
                onClick = { onKeyInput(VT100Sequences.BACKTICK) },
            )
            KeyButton(
                label = "-",
                onClick = { onKeyInput(VT100Sequences.MINUS) },
            )
            KeyButton(
                label = "_",
                onClick = {
                    val bytes = if (altMode) byteArrayOf(0x1B, 0x2D) else byteArrayOf(0x5F)
                    onKeyInput(bytes)
                    altMode = false
                },
                wide = true,
            )
            KeyButton(
                label = "=",
                onClick = { onKeyInput(VT100Sequences.EQUALS) },
            )
            KeyButton(
                label = "+",
                onClick = {
                    val bytes = if (shiftMode) byteArrayOf(0x2B) else byteArrayOf(0x3D)
                    onKeyInput(bytes)
                    shiftMode = false
                },
            )
            KeyButton(
                label = ".",
                onClick = { onKeyInput(VT100Sequences.PERIOD) },
            )
            KeyButton(
                label = ",",
                onClick = { onKeyInput(VT100Sequences.COMMA) },
            )
            KeyButton(
                label = ";",
                onClick = { onKeyInput(VT100Sequences.SEMICOLON) },
            )
            KeyButton(
                label = "'",
                onClick = { onKeyInput(VT100Sequences.QUOTE) },
            )
            KeyButton(
                label = "[",
                onClick = { onKeyInput(VT100Sequences.LBRACKET) },
            )
            KeyButton(
                label = "]",
                onClick = { onKeyInput(VT100Sequences.RBRACKET) },
            )
            KeyButton(
                label = "\\",
                onClick = { onKeyInput(VT100Sequences.BACKSLASH) },
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Row 2: navigation + function keys + paste
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyButton(
                label = "Home",
                onClick = {
                    val seq = if (shiftMode) VT100Sequences.shiftHome() else VT100Sequences.home()
                    onKeyInput(seq)
                    shiftMode = false
                },
            )
            KeyButton(
                label = "End",
                onClick = {
                    val seq = if (shiftMode) VT100Sequences.shiftEnd() else VT100Sequences.end()
                    onKeyInput(seq)
                    shiftMode = false
                },
            )
            KeyButton(
                label = "PgUp",
                onClick = { onKeyInput(VT100Sequences.pageUp()) },
            )
            KeyButton(
                label = "PgDn",
                onClick = { onKeyInput(VT100Sequences.pageDown()) },
            )
            KeyButton(
                label = "Del",
                onClick = { onKeyInput(VT100Sequences.DELETE) },
            )
            KeyButton(
                label = "Ins",
                onClick = { onKeyInput(VT100Sequences.insert()) },
            )

            Spacer(modifier = Modifier.width(6.dp))

            KeyButton(
                label = "F1",
                onClick = { onKeyInput(VT100Sequences.functionKey(1)) },
            )
            KeyButton(
                label = "F2",
                onClick = { onKeyInput(VT100Sequences.functionKey(2)) },
            )
            KeyButton(
                label = "F3",
                onClick = { onKeyInput(VT100Sequences.functionKey(3)) },
            )
            KeyButton(
                label = "F4",
                onClick = { onKeyInput(VT100Sequences.functionKey(4)) },
            )
            KeyButton(
                label = "F5",
                onClick = { onKeyInput(VT100Sequences.functionKey(5)) },
            )
            KeyButton(
                label = "F6",
                onClick = { onKeyInput(VT100Sequences.functionKey(6)) },
            )
            KeyButton(
                label = "F7",
                onClick = { onKeyInput(VT100Sequences.functionKey(7)) },
            )
            KeyButton(
                label = "F8",
                onClick = { onKeyInput(VT100Sequences.functionKey(8)) },
            )
            KeyButton(
                label = "F9",
                onClick = { onKeyInput(VT100Sequences.functionKey(9)) },
            )
            KeyButton(
                label = "F10",
                onClick = { onKeyInput(VT100Sequences.functionKey(10)) },
            )
            KeyButton(
                label = "F11",
                onClick = { onKeyInput(VT100Sequences.functionKey(11)) },
            )
            KeyButton(
                label = "F12",
                onClick = { onKeyInput(VT100Sequences.functionKey(12)) },
            )

            Spacer(modifier = Modifier.width(6.dp))

            KeyButton(
                label = "Paste",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString() ?: ""
                        if (text.isNotEmpty()) {
                            onPaste(text)
                        }
                    }
                },
                wide = true,
            )
        }
    }
}

@Composable
fun TerminalHiddenInput(
    onKeyInput: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange(0)))
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val newText = newValue.text
            if (newText.isNotEmpty()) {
                val bytes = newText.toByteArray(Charsets.UTF_8)
                onKeyInput(bytes)
                textFieldValue = TextFieldValue(
                    text = "",
                    selection = TextRange(0),
                )
            } else {
                textFieldValue = newValue
            }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
        cursorBrush = SolidColor(Color.Transparent),
    )
}

class TerminalInputConnection(
    private val onKeyInput: (ByteArray) -> Unit,
) : InputConnection {

    override fun beginBatchEdit(): Boolean = true

    override fun endBatchEdit(): Boolean = true

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null && text.isNotEmpty()) {
            val bytes = text.toString().toByteArray(Charsets.UTF_8)
            onKeyInput(bytes)
        }
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength) {
            onKeyInput(byteArrayOf(0x7F))
        }
        return true
    }

    override fun performEditorAction(actionId: Int): Boolean {
        onKeyInput(byteArrayOf(0x0D))
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val ctrl = event.metaState and KeyEvent.META_CTRL_ON != 0
        val alt = event.metaState and KeyEvent.META_ALT_ON != 0
        val shift = event.metaState and KeyEvent.META_SHIFT_ON != 0

        val sequence = VT100Sequences.keyToSequence(event.keyCode, ctrl, alt, shift)
        if (sequence != null) {
            onKeyInput(sequence)
            return true
        }

        if (event.unicodeChar != 0) {
            val char = event.unicodeChar.toChar()
            val bytes = char.toString().toByteArray(Charsets.UTF_8)
            onKeyInput(bytes)
            return true
        }

        return false
    }

    override fun getExtractedText(
        request: android.view.inputmethod.ExtractedTextRequest?,
        flags: Int,
    ): android.view.inputmethod.ExtractedText? = null

    override fun finishComposingText(): Boolean = true

    override fun clearMetaKeyStates(flags: Int): Boolean = true

    override fun reportFullscreenMode(enabled: Boolean): Boolean = true

    override fun getCharacterBounds(index: Int): android.graphics.Rect = android.graphics.Rect()

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

    override fun setComposingRegion(start: Int, end: Int): Boolean = true

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

    override fun getSelectedText(flags: Int): CharSequence = ""

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun getRequestedSelectionStart(): Int = 0

    override fun getRequestedSelectionEnd(): Int = 0

    override fun setSelection(start: Int, end: Int): Boolean = false

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

    override fun performContextMenuAction(id: Int): Boolean = false

    override fun performPrivateCommand(action: String?, extras: android.os.Bundle?): Boolean = false

    override fun restartInput() {}

    override fun isFullscreenMode(): Boolean = false
}
