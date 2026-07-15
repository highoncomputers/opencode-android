package ai.opencode.app.ui.files

import ai.opencode.app.ui.components.EmptyState
import ai.opencode.app.ui.components.LoadingIndicator
import ai.opencode.app.ui.components.SearchBar
import ai.opencode.app.ui.theme.GitAdded
import ai.opencode.app.ui.theme.GitConflicted
import ai.opencode.app.ui.theme.GitDeleted
import ai.opencode.app.ui.theme.GitModified
import ai.opencode.app.ui.theme.GitUntracked
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.opencode.core.git.Git
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    initialPath: String? = null,
    onBack: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    LaunchedEffect(initialPath) {
        if (initialPath != null) {
            viewModel.navigateTo(initialPath)
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.Filled.List else Icons.Filled.GridView,
                            contentDescription = "Toggle view"
                        )
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
        ) {
            BreadcrumbNav(
                pathSegments = uiState.pathSegments,
                onSegmentClick = viewModel::navigateToSegment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "Search files...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                uiState.isLoading -> {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                }
                uiState.filteredFiles.isEmpty() -> {
                    EmptyState(
                        title = "Empty directory",
                        subtitle = "This folder contains no files",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                }
                uiState.isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.filteredFiles) { file ->
                            FileGridItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        onNavigateToPath(file.path)
                                    }
                                }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.filteredFiles) { file ->
                            FileListItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        onNavigateToPath(file.path)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbNav(
    pathSegments: List<String>,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onSegmentClick(-1) }
        )

        pathSegments.forEachIndexed { index, segment ->
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = segment,
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == pathSegments.lastIndex)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSegmentClick(index) }
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: FileItem,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateText = if (file.lastModified > 0) dateFormat.format(Date(file.lastModified)) else ""
    val sizeText = formatFileSize(file.size)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getFileIcon(file),
            contentDescription = null,
            tint = getFileColor(file),
            modifier = Modifier
                .size(24.dp)
                .padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                file.gitStatus?.let { status ->
                    GitStatusBadge(status = status)
                }
            }
        }

        if (!file.isDirectory) {
            Text(
                text = sizeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun FileGridItem(
    file: FileItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = getFileIcon(file),
            contentDescription = null,
            tint = getFileColor(file),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun GitStatusBadge(status: Git.ChangeStatus) {
    val color = when (status) {
        Git.ChangeStatus.Added -> GitAdded
        Git.ChangeStatus.Modified -> GitModified
        Git.ChangeStatus.Deleted -> GitDeleted
        Git.ChangeStatus.Conflicted -> GitConflicted
        Git.ChangeStatus.Untracked -> GitUntracked
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (status) {
        Git.ChangeStatus.Added -> "A"
        Git.ChangeStatus.Modified -> "M"
        Git.ChangeStatus.Deleted -> "D"
        Git.ChangeStatus.Conflicted -> "C"
        Git.ChangeStatus.Untracked -> "?"
        Git.ChangeStatus.Renamed -> "R"
        Git.ChangeStatus.Copied -> "C"
        Git.ChangeStatus.TypeChanged -> "T"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun getFileIcon(file: FileItem): ImageVector {
    return if (file.isDirectory) {
        Icons.Filled.Folder
    } else {
        Icons.Filled.InsertDriveFile
    }
}

private fun getFileColor(file: FileItem): Color {
    if (file.isDirectory) return GitModified
    return when (file.name.substringAfterLast(".").lowercase()) {
        "kt" -> Color(0xFFA97BFF)
        "java" -> Color(0xFFE76F00)
        "xml" -> Color(0xFFE34C26)
        "json" -> Color(0xFF5B5B5B)
        "md" -> Color(0xFF083FA1)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    }
}
