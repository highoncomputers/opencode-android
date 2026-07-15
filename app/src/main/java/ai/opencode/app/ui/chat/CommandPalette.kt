package ai.opencode.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SlashCommand(
    val name: String,
    val description: String
)

val defaultSlashCommands = listOf(
    SlashCommand("compact", "Compact conversation history"),
    SlashCommand("clear", "Clear current session"),
    SlashCommand("model", "Switch model"),
    SlashCommand("agent", "Switch agent"),
    SlashCommand("help", "Show available commands"),
    SlashCommand("theme", "Toggle terminal theme"),
    SlashCommand("export", "Export conversation"),
    SlashCommand("stats", "Show usage statistics")
)

@Composable
fun CommandPalette(
    commands: List<SlashCommand> = defaultSlashCommands,
    query: String = "",
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = if (query.isBlank()) {
        commands
    } else {
        commands.filter {
            it.name.contains(query.removePrefix("/"), ignoreCase = true)
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Commands",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered) { command ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCommandSelected(command.name) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "/${command.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = command.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
