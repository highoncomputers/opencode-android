package ai.opencode.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = block.text,
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = block.code,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .horizontalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is MarkdownBlock.InlineCode -> {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = block.code,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = (block.indent * 16 + 8).dp, top = 2.dp, bottom = 2.dp)) {
                        Text(
                            text = if (block.ordered) "${block.number}." else "\u2022",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        SelectionContainer {
                            Text(
                                text = renderInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Surface(
                        shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = block.text,
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock()
    data class InlineCode(val code: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class ListItem(val text: String, val ordered: Boolean, val number: Int = 0, val indent: Int = 0) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data object HorizontalRule : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var i = 0
    var orderedCounter = 1

    while (i < lines.size) {
        val line = lines[i]

        when {
            line.matches(Regex("^#{1,6}\\s+.+")) -> {
                val level = line.takeWhile { it == '#' }.length
                val content = line.drop(level).trim()
                blocks.add(MarkdownBlock.Header(level, content))
                orderedCounter = 1
            }
            line.matches(Regex("^```.*")) -> {
                val language = line.removePrefix("```").trim().ifBlank { null }
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    if (codeBuilder.isNotEmpty()) codeBuilder.append("\n")
                    codeBuilder.append(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(language, codeBuilder.toString()))
            }
            line.matches(Regex("^[-*_]{3,}\\s*$")) -> {
                blocks.add(MarkdownBlock.HorizontalRule)
                orderedCounter = 1
            }
            line.matches(Regex("^>\\s*.+")) -> {
                val content = line.removePrefix(">").trim()
                blocks.add(MarkdownBlock.Blockquote(content))
                orderedCounter = 1
            }
            line.matches(Regex("^\\d+\\.\\s+.+")) -> {
                val match = Regex("^(\\d+)\\.\\s+(.+)").find(line)
                if (match != null) {
                    val num = match.groupValues[1].toIntOrNull() ?: 1
                    val content = match.groupValues[2]
                    blocks.add(MarkdownBlock.ListItem(content, ordered = true, number = num))
                    orderedCounter = num + 1
                }
            }
            line.matches(Regex("^[-+]\\s+.+")) -> {
                val content = line.drop(2).trim()
                blocks.add(MarkdownBlock.ListItem(content, ordered = false))
                orderedCounter = 1
            }
            line.isBlank() -> {
                orderedCounter = 1
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
        i++
    }
    return blocks
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            when {
                remaining.startsWith("**") || remaining.startsWith("__") -> {
                    val delimiter = if (remaining.startsWith("**")) "**" else "__"
                    val end = remaining.indexOf(delimiter, 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(remaining.substring(2, end))
                        }
                        remaining = remaining.substring(end + 2)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                remaining.startsWith("*") || remaining.startsWith("_") -> {
                    val delimiter = if (remaining.startsWith("*")) "*" else "_"
                    val end = remaining.indexOf(delimiter, 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                remaining.startsWith("`") -> {
                    val end = remaining.indexOf('`', 1)
                    if (end > 0) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color(0x20808080)
                        )) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                remaining.startsWith("[") -> {
                    val linkEnd = remaining.indexOf("](")
                    val closeEnd = if (linkEnd > 0) remaining.indexOf(")", linkEnd + 2) else -1
                    if (linkEnd > 0 && closeEnd > 0) {
                        val linkText = remaining.substring(1, linkEnd)
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                        remaining = remaining.substring(closeEnd + 1)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                else -> {
                    val nextSpecial = remaining.indexOfAny(charArrayOf('*', '_', '`', '['))
                    if (nextSpecial > 0) {
                        append(remaining.substring(0, nextSpecial))
                        remaining = remaining.substring(nextSpecial)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
            }
        }
    }
}
