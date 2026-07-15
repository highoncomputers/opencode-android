package ai.opencode.app.ui.settings

import ai.opencode.app.ui.components.StatusBadge
import ai.opencode.app.ui.theme.AppThemeMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Server") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Status:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp)
                    )
                    StatusBadge(
                        isConnected = uiState.isServerConnected
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::updateServerUrl,
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = viewModel::testConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text("Test Connection")
                }
            }

            SettingsSection(title = "API Key") {
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (uiState.apiKeyMasked)
                        PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                            Icon(
                                imageVector = if (uiState.apiKeyMasked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }

            SettingsSection(title = "Appearance") {
                var themeExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (uiState.themeMode) {
                            AppThemeMode.SYSTEM -> "System"
                            AppThemeMode.LIGHT -> "Light"
                            AppThemeMode.DARK -> "Dark"
                            AppThemeMode.TERMINAL -> "Terminal"
                        },
                        onValueChange = {},
                        label = { Text("Theme") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System") },
                            onClick = {
                                viewModel.updateThemeMode(AppThemeMode.SYSTEM)
                                themeExpanded = false
                            },
                            leadingIcon = if (uiState.themeMode == AppThemeMode.SYSTEM) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Light") },
                            onClick = {
                                viewModel.updateThemeMode(AppThemeMode.LIGHT)
                                themeExpanded = false
                            },
                            leadingIcon = if (uiState.themeMode == AppThemeMode.LIGHT) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Dark") },
                            onClick = {
                                viewModel.updateThemeMode(AppThemeMode.DARK)
                                themeExpanded = false
                            },
                            leadingIcon = if (uiState.themeMode == AppThemeMode.DARK) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Terminal (Green on Black)") },
                            onClick = {
                                viewModel.updateThemeMode(AppThemeMode.TERMINAL)
                                themeExpanded = false
                            },
                            leadingIcon = if (uiState.themeMode == AppThemeMode.TERMINAL) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }

            SettingsSection(title = "Defaults") {
                var agentExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = agentExpanded,
                    onExpandedChange = { agentExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.defaultAgent,
                        onValueChange = {},
                        label = { Text("Default Agent") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = agentExpanded,
                        onDismissRequest = { agentExpanded = false }
                    ) {
                        uiState.availableAgents.forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent) },
                                onClick = {
                                    viewModel.updateDefaultAgent(agent)
                                    agentExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(title = "Permissions") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-approve permissions",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Automatically allow tool execution without prompting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = uiState.autoApprovePermissions,
                        onCheckedChange = viewModel::updateAutoApprove,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            SettingsSection(title = "Shell") {
                OutlinedTextField(
                    value = uiState.shell,
                    onValueChange = viewModel::updateShell,
                    label = { Text("Shell path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "OpenCode Android",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "v${uiState.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Build",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2026.07.15",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}
