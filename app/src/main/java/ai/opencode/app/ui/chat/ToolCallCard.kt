package ai.opencode.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.opencode.app.ui.theme.StatusError
import ai.opencode.app.ui.theme.StatusOnline
import ai.opencode.app.ui.theme.StatusWarning
import ai.opencode.core.message.Message

@Composable
fun ToolCallCard(
    message: Message.Tool,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val statusIcon = when (message.status) {
        Message.ToolStatus.Running -> Icons.Filled.Sync
        Message.ToolStatus.Completed -> Icons.Filled.CheckCircle
        Message.ToolStatus.Error -> Icons.Filled.Error
        Message.ToolStatus.Cancelled -> Icons.Filled.Error
    }

    val statusColor = when (message.status) {
        Message.ToolStatus.Running -> StatusWarning
        Message.ToolStatus.Completed -> StatusOnline
        Message.ToolStatus.Error -> StatusError
        Message.ToolStatus.Cancelled -> StatusError
    }

    val statusText = when (message.status) {
        Message.ToolStatus.Running -> "Running"
        Message.ToolStatus.Completed -> "Done"
        Message.ToolStatus.Error -> "Error"
        Message.ToolStatus.Cancelled -> "Cancelled"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusText,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = message.title ?: message.toolRef.name ?: "Tool Call",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontSize = 10.sp
                    )

                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.input.isNotEmpty()) {
                            Text(
                                text = "Input:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = message.input.entries.joinToString("\n") { (k, v) ->
                                        "$k: $v"
                                    },
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (message.output.isNotEmpty()) {
                            Text(
                                text = "Output:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = message.output.joinToString("\n") { it.content },
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = if (message.status == Message.ToolStatus.Error)
                                        StatusError else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 20
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
