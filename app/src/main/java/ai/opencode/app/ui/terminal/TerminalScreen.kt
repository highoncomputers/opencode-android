package ai.opencode.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.opencode.app.ui.theme.TerminalAmber
import ai.opencode.app.ui.theme.TerminalGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val terminalState by viewModel.terminalState.collectAsState()

    LaunchedEffect(Unit) {
        if (!uiState.isConnected && !uiState.isConnecting) {
            viewModel.startSession()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.destroySession()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = terminalState.buffer.windowTitle.ifEmpty { "Terminal" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (uiState.isConnecting) {
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (uiState.error != null) {
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearScreen() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E1E1E)),
        ) {
            if (uiState.isConnected) {
                TerminalView(
                    state = terminalState,
                    modifier = Modifier.fillMaxSize(),
                )

                TerminalInputBar(
                    onKeyInput = { data -> viewModel.writeInput(data) },
                    onPaste = { text -> viewModel.writePastedText(text) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                )
            } else if (uiState.isConnecting) {
                Text(
                    text = "Starting terminal session...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalGreen,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Terminal Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    IconButton(
                        onClick = { viewModel.startSession() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(
                            text = "Retry",
                            color = TerminalGreen,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else if (uiState.exitCode != null) {
                Text(
                    text = "Session exited with code ${uiState.exitCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalAmber,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
