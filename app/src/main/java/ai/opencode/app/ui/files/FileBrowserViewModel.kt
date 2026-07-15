package ai.opencode.app.ui.files

import ai.opencode.core.git.Git
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val gitStatus: Git.ChangeStatus? = null
)

data class FileBrowserUiState(
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val gitStatus: Git.Status? = null
) {
    val filteredFiles: List<FileItem>
        get() = if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

@Singleton
class FileBrowserViewModel @Inject constructor() : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    init {
        navigateTo("/")
    }

    fun navigateTo(path: String) {
        _uiState.update { it.copy(isLoading = true, error = null, currentPath = path) }
        _uiState.update {
            it.copy(
                pathSegments = buildPathSegments(path),
                isLoading = false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun navigateUp(): Boolean {
        val current = _uiState.value.currentPath
        if (current == "/") return false
        val parent = current.substringBeforeLast("/", "/")
        navigateTo(parent)
        return true
    }

    fun navigateToSegment(index: Int) {
        val segments = _uiState.value.pathSegments
        if (index < 0) {
            navigateTo("/")
        } else {
            val path = segments.take(index + 1).joinToString("/")
            navigateTo("/$path")
        }
    }

    private fun buildPathSegments(path: String): List<String> {
        if (path == "/") return emptyList()
        return path.split("/").filter { it.isNotBlank() }
    }
}
