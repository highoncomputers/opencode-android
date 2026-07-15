package ai.opencode.app.ui.stats

import ai.opencode.app.ui.components.EmptyState
import ai.opencode.app.ui.components.LoadingIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.opencode.core.session.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    sessionId: String? = null,
    onBack: () -> Unit,
    onSessionClick: (String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (sessionId != null) "Session Stats" else "Usage Statistics") },
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
        when {
            uiState.isLoading -> {
                LoadingIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp)
                )
            }
            uiState.totalSessions == 0 && sessionId == null -> {
                EmptyState(
                    title = "No usage data",
                    subtitle = "Start chatting to see your statistics",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp)
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (sessionId != null) {
                        SessionStatsSection(
                            tokens = uiState.sessionTokens,
                            cost = uiState.sessionCost
                        )
                    } else {
                        OverviewStatsSection(
                            totalSessions = uiState.totalSessions,
                            totalTokens = uiState.totalTokens,
                            totalCost = uiState.formattedCost,
                            totalApiCalls = uiState.totalApiCalls
                        )

                        TokenBreakdownSection(
                            inputTokens = uiState.totalInputTokens,
                            outputTokens = uiState.totalOutputTokens,
                            cacheTokens = uiState.totalCacheTokens
                        )

                        ModelUsageSection(modelUsage = uiState.modelUsage)

                        ActivityChart(dailyActivity = uiState.dailyActivity)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStatsSection(
    tokens: Session.TokenUsage?,
    cost: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (tokens != null) {
                StatRow(label = "Input tokens", value = tokens.input.toLocaleString())
                StatRow(label = "Output tokens", value = tokens.output.toLocaleString())
                StatRow(label = "Cache tokens", value = tokens.cache.toLocaleString())
                StatRow(label = "Total tokens", value = tokens.total.toLocaleString())
                StatRow(label = "Estimated cost", value = "$${"%.4f".format(cost)}")
            } else {
                Text(
                    text = "No token data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverviewStatsSection(
    totalSessions: Int,
    totalTokens: Long,
    totalCost: String,
    totalApiCalls: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(label = "Sessions", value = "$totalSessions")
                StatCard(label = "API Calls", value = "$totalApiCalls")
                StatCard(label = "Cost", value = totalCost)
            }

            StatRow(label = "Total Tokens", value = totalTokens.toLocaleString())
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TokenBreakdownSection(
    inputTokens: Long,
    outputTokens: Long,
    cacheTokens: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Token Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val total = (inputTokens + outputTokens + cacheTokens).coerceAtLeast(1)

            TokenBar(
                label = "Input",
                tokens = inputTokens,
                fraction = inputTokens.toFloat() / total,
                color = MaterialTheme.colorScheme.primary
            )

            TokenBar(
                label = "Output",
                tokens = outputTokens,
                fraction = outputTokens.toFloat() / total,
                color = MaterialTheme.colorScheme.tertiary
            )

            TokenBar(
                label = "Cache",
                tokens = cacheTokens,
                fraction = cacheTokens.toFloat() / total,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun TokenBar(
    label: String,
    tokens: Long,
    fraction: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tokens.toLocaleString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color,
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
            ) {}
        }
    }
}

@Composable
private fun ModelUsageSection(modelUsage: List<ModelUsage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Model Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (modelUsage.isEmpty()) {
                Text(
                    text = "No model usage data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                modelUsage.forEach { usage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = usage.modelId,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = usage.providerId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${usage.requestCount} calls",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "$${"%.4f".format(usage.cost)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityChart(dailyActivity: List<DailyActivity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (dailyActivity.isEmpty()) {
                Text(
                    text = "No activity data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxTokens = dailyActivity.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
                val chartColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val barWidth = size.width / dailyActivity.size.coerceAtLeast(1)
                    val chartHeight = size.height - 20.dp.toPx()

                    dailyActivity.forEachIndexed { index, activity ->
                        val barHeight = (activity.tokens.toFloat() / maxTokens) * chartHeight
                        val x = index * barWidth + barWidth * 0.2f

                        drawRect(
                            color = chartColor.copy(alpha = 0.7f),
                            topLeft = Offset(x, chartHeight - barHeight),
                            size = Size(barWidth * 0.6f, barHeight)
                        )
                    }
                }
            }
        }
    }
}

private fun Long.toLocaleString(): String {
    return "%,d".format(this)
}
