package ai.opencode.core.git

import kotlinx.serialization.Serializable

object Git {
    @Serializable
    data class Repository(
        val rootPath: String,
        val worktreePath: String = rootPath,
        val isWorktree: Boolean = false,
        val branch: String? = null,
        val remote: String? = null,
        val head: CommitRef? = null
    )

    @Serializable
    data class CommitRef(
        val hash: String,
        val shortHash: String = hash.take(7),
        val message: String? = null,
        val author: Author? = null,
        val time: Long = 0
    )

    @Serializable
    data class Author(
        val name: String,
        val email: String
    )

    @Serializable
    data class ChangeSet(
        val repository: String,
        val branch: String? = null,
        val ahead: Int = 0,
        val behind: Int = 0,
        val staged: List<FileChange> = emptyList(),
        val unstaged: List<FileChange> = emptyList(),
        val untracked: List<String> = emptyList(),
        val conflicted: List<FileChange> = emptyList()
    ) {
        val hasChanges: Boolean
            get() = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty() || conflicted.isNotEmpty()
    }

    @Serializable
    data class FileChange(
        val path: String,
        val status: ChangeStatus,
        val oldPath: String? = null,
        val additions: Int = 0,
        val deletions: Int = 0
    )

    @Serializable
    enum class ChangeStatus {
        Added,
        Modified,
        Deleted,
        Renamed,
        Copied,
        Untracked,
        Conflicted,
        TypeChanged
    }

    @Serializable
    data class TreeID(val value: String) {
        override fun toString() = value
    }

    @Serializable
    data class Worktree(
        val path: String,
        val branch: String,
        val head: CommitRef? = null,
        val isMain: Boolean = false,
        val isLocked: Boolean = false,
        val lockedReason: String? = null,
        val prunable: Boolean = false
    )

    @Serializable
    data class Diff(
        val file: String,
        val status: ChangeStatus,
        val hunks: List<Hunk> = emptyList(),
        val binary: Boolean = false
    )

    @Serializable
    data class Hunk(
        val oldStart: Int,
        val oldLines: Int,
        val newStart: Int,
        val newLines: Int,
        val content: String
    )

    @Serializable
    data class LogEntry(
        val hash: String,
        val shortHash: String = hash.take(7),
        val message: String,
        val author: Author,
        val time: Long,
        val files: List<String> = emptyList()
    )

    @Serializable
    data class Branch(
        val name: String,
        val isCurrent: Boolean = false,
        val isRemote: Boolean = false,
        val upstream: String? = null,
        val head: CommitRef? = null
    )

    @Serializable
    data class Status(
        val repository: String,
        val currentBranch: String? = null,
        val isClean: Boolean = true,
        val ahead: Int = 0,
        val behind: Int = 0,
        val stagedCount: Int = 0,
        val unstagedCount: Int = 0,
        val untrackedCount: Int = 0
    )
}
