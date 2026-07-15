package ai.opencode.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.util.io.NullOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class GitManager {

    private val repositoryCache = ConcurrentHashMap<String, JGit>()
    private val repoMutex = Mutex()

    suspend fun discover(directory: String): Git.Repository? = withContext(Dispatchers.IO) {
        try {
            val dir = File(directory)
            if (!dir.exists()) return@withContext null

            val repository = FileRepositoryBuilder()
                .findGitDir(dir)
                .readEnvironment()
                .findGitDir()
                .setup()
                .build()

            val gitRoot = repository.directory.parentFile?.absolutePath
                ?: repository.directory.absolutePath

            val head = resolveHead(repository)
            val branch = repository.branch
            val remote = try {
                repository.config.getString("remote", "origin", "url")
            } catch (_: Exception) {
                null
            }

            Git.Repository(
                rootPath = gitRoot,
                worktreePath = repository.workTree.absolutePath,
                isWorktree = isWorktree(repository),
                branch = branch,
                remote = remote,
                head = head
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun status(directory: String): Git.ChangeSet = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext emptyChangeSet(directory)

        try {
            val repository = git.repository
            val branch = repository.branch

            val status = git.status().call()

            val staged = status.added.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Added,
                    additions = countAddedLines(git, path)
                )
            } + status.changed.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Modified
                )
            } + status.removed.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Deleted
                )
            }

            val unstaged = status.modified.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Modified
                )
            } + status.removed.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Deleted
                )
            }

            val untracked = status.untracked.toList()

            val conflicted = status.conflicting.map { path ->
                Git.FileChange(
                    path = path,
                    status = Git.ChangeStatus.Conflicted
                )
            }

            val trackingStatus = try {
                BranchTrackingStatus.of(repository, "refs/heads/$branch")
            } catch (_: Exception) {
                null
            }

            val ahead = trackingStatus?.aheadCount ?: 0
            val behind = trackingStatus?.behindCount ?: 0

            Git.ChangeSet(
                repository = directory,
                branch = branch,
                ahead = ahead,
                behind = behind,
                staged = staged,
                unstaged = unstaged,
                untracked = untracked,
                conflicted = conflicted
            )
        } catch (e: Exception) {
            emptyChangeSet(directory)
        }
    }

    suspend fun diff(
        directory: String,
        path: String? = null,
        staged: Boolean = false
    ): List<Git.Diff> = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext emptyList()

        try {
            val repository = git.repository
            val diffs = mutableListOf<Git.Diff>()

            val df = DiffFormatter(NullOutputStream.INSTANCE)
            df.setRepository(repository)

            val entries = try {
                val headCommit = repository.resolve(Constants.HEAD)
                val treeParser = CanonicalTreeParser()
                if (headCommit != null) {
                    treeParser.reset(repository.newObjectReader(), headCommit)
                }
                if (staged) {
                    df.scan(treeParser, FileTreeIterator(repository))
                } else {
                    df.scan(treeParser, FileTreeIterator(repository))
                }
            } catch (_: Exception) {
                emptyList<DiffEntry>()
            }

            for (entry in entries) {
                if (path != null && entry.newPath != path && entry.oldPath != path) continue

                val hunks = mutableListOf<Git.Hunk>()
                try {
                    val diffOutput = java.io.ByteArrayOutputStream()
                    val formattter = DiffFormatter(diffOutput)
                    formattter.setRepository(repository)
                    formattter.format(entry)
                    val diffText = diffOutput.toString("UTF-8")
                    hunks.addAll(parseDiffHunks(diffText))
                } catch (_: Exception) {
                }

                val changeStatus = when (entry.changeType) {
                    DiffEntry.ChangeType.ADD -> Git.ChangeStatus.Added
                    DiffEntry.ChangeType.MODIFY -> Git.ChangeStatus.Modified
                    DiffEntry.ChangeType.DELETE -> Git.ChangeStatus.Deleted
                    DiffEntry.ChangeType.RENAME -> Git.ChangeStatus.Renamed
                    DiffEntry.ChangeType.COPY -> Git.ChangeStatus.Copied
                    else -> Git.ChangeStatus.Modified
                }

                diffs.add(Git.Diff(
                    file = entry.newPath ?: entry.oldPath ?: "unknown",
                    status = changeStatus,
                    hunks = hunks,
                    binary = false
                ))
            }

            df.close()
            diffs
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun log(
        directory: String,
        limit: Int = 20,
        path: String? = null
    ): List<Git.LogEntry> = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext emptyList()

        try {
            val logCommand = git.log()
            logCommand.setMaxCount(limit)

            if (path != null) {
                logCommand.addPath(path)
            }

            val commits = logCommand.call()

            commits.map { commit ->
                val author = Git.Author(
                    name = commit.authorIdent.name,
                    email = commit.authorIdent.emailAddress
                )

                val files = if (path == null) {
                    try {
                        emptyList<String>()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                Git.LogEntry(
                    hash = commit.id.name,
                    shortHash = commit.id.abbreviate(7).name(),
                    message = commit.shortMessage ?: commit.fullMessage,
                    author = author,
                    time = commit.authorIdent.getWhen().time,
                    files = files
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun branch(directory: String): String? = withContext(Dispatchers.IO) {
        try {
            val git = getGit(directory) ?: return@withContext null
            git.repository.branch
        } catch (_: Exception) {
            null
        }
    }

    suspend fun branches(
        directory: String,
        includeRemotes: Boolean = true
    ): List<Git.Branch> = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext emptyList()

        try {
            val branches = mutableListOf<Git.Branch>()
            val currentBranch = git.repository.branch

            val localBranches = git.branchList().call()
            for (ref in localBranches) {
                val name = ref.name.removePrefix("refs/heads/")
                val head = resolveHead(git.repository, ref)
                branches.add(Git.Branch(
                    name = name,
                    isCurrent = name == currentBranch,
                    isRemote = false,
                    head = head
                ))
            }

            if (includeRemotes) {
                val remoteBranches = git.branchList()
                    .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                    .call()

                for (ref in remoteBranches) {
                    val fullName = ref.name
                    val name = fullName.removePrefix("refs/remotes/").removePrefix("origin/")
                    if (branches.any { it.name == name }) continue

                    val head = resolveHead(git.repository, ref)
                    branches.add(Git.Branch(
                        name = name,
                        isCurrent = false,
                        isRemote = true,
                        upstream = fullName,
                        head = head
                    ))
                }
            }

            branches
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun checkout(
        directory: String,
        ref: String
    ): Boolean = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext false

        try {
            git.checkout()
                .setName(ref)
                .setCreateBranch(false)
                .call()
            true
        } catch (_: Exception) {
            try {
                git.checkout()
                    .setName(ref)
                    .setCreateBranch(true)
                    .call()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun commit(
        directory: String,
        message: String,
        files: List<String>? = null
    ): Git.CommitRef? = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext null

        try {
            if (files != null) {
                val addCommand = git.add()
                for (file in files) {
                    addCommand.addFilepattern(file)
                }
                addCommand.call()
            } else {
                git.add()
                    .addFilepattern(".")
                    .call()
            }

            val commit = git.commit()
                .setMessage(message)
                .setAll(false)
                .call()

            val author = Git.Author(
                name = commit.authorIdent.name,
                email = commit.authorIdent.emailAddress
            )

            Git.CommitRef(
                hash = commit.id.name,
                shortHash = commit.id.abbreviate(7).name(),
                message = commit.shortMessage ?: commit.fullMessage,
                author = author,
                time = commit.authorIdent.getWhen().time
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun add(
        directory: String,
        paths: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val git = getGit(directory) ?: return@withContext false

        try {
            val addCommand = git.add()
            for (path in paths) {
                addCommand.addFilepattern(path)
            }
            addCommand.call()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun statusSummary(directory: String): Git.Status = withContext(Dispatchers.IO) {
        val changeSet = status(directory)

        Git.Status(
            repository = directory,
            currentBranch = changeSet.branch,
            isClean = !changeSet.hasChanges,
            ahead = changeSet.ahead,
            behind = changeSet.behind,
            stagedCount = changeSet.staged.size,
            unstagedCount = changeSet.unstaged.size,
            untrackedCount = changeSet.untracked.size
        )
    }

    suspend fun isRepository(directory: String): Boolean {
        return discover(directory) != null
    }

    suspend fun root(directory: String): String? {
        return discover(directory)?.rootPath
    }

    suspend fun headCommit(directory: String): Git.CommitRef? = withContext(Dispatchers.IO) {
        try {
            val git = getGit(directory) ?: return@withContext null
            resolveHead(git.repository)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun currentBranchAheadBehind(directory: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val git = getGit(directory) ?: return@withContext Pair(0, 0)
            val repository = git.repository
            val branch = repository.branch ?: return@withContext Pair(0, 0)

            val trackingStatus = BranchTrackingStatus.of(repository, "refs/heads/$branch")
            Pair(trackingStatus?.aheadCount ?: 0, trackingStatus?.behindCount ?: 0)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    private suspend fun getGit(directory: String): JGit? = repoMutex.withLock {
        val normalizedPath = File(directory).canonicalPath

        repositoryCache[normalizedPath]?.let { git ->
            return@withLock git
        }

        try {
            val repository = FileRepositoryBuilder()
                .findGitDir(File(normalizedPath))
                .readEnvironment()
                .findGitDir()
                .setup()
                .build()

            val git = JGit.wrap(repository)
            val gitDirPath = repository.directory.canonicalPath
            repositoryCache[gitDirPath] = git
            repositoryCache[normalizedPath] = git
            git
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveHead(
        repository: Repository,
        ref: Ref? = null
    ): Git.CommitRef? {
        return try {
            val resolvedRef = ref ?: repository.exactRef(Constants.HEAD)
                ?: repository.findRef(Constants.HEAD)

            if (resolvedRef != null) {
                val peeledRef = repository.peel(resolvedRef)
                val commit = repository.parseCommit(peeledRef.getObjectId())

                Git.CommitRef(
                    hash = commit.id.name,
                    shortHash = commit.id.abbreviate(7).name(),
                    message = commit.shortMessage,
                    author = Git.Author(
                        name = commit.authorIdent.name,
                        email = commit.authorIdent.emailAddress
                    ),
                    time = commit.authorIdent.getWhen().time
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isWorktree(repository: Repository): Boolean {
        return try {
            val workTree = repository.workTree
            val gitDir = repository.directory
            workTree.absolutePath != gitDir.parentFile?.absolutePath
        } catch (_: Exception) {
            false
        }
    }

    private fun countAddedLines(git: JGit, path: String): Int {
        return try {
            val baos = java.io.ByteArrayOutputStream()
            val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
            formatter.setRepository(git.repository)
            val entries = formatter.scan(
                org.eclipse.jgit.treewalk.FileTreeIterator(git.repository),
                org.eclipse.jgit.treewalk.FileTreeIterator(git.repository)
            )
            val matching = entries.firstOrNull { it.newPath == path || it.oldPath == path }
            if (matching != null) {
                baos.reset()
                formatter.format(matching)
                val output = baos.toString("UTF-8")
                output.lines().count { it.startsWith("+") && !it.startsWith("+++") }
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun parseDiffHunks(diffText: String): List<Git.Hunk> {
        val hunks = mutableListOf<Git.Hunk>()
        val lines = diffText.lines()
        var currentHunk: Git.Hunk? = null
        val hunkContent = StringBuilder()

        for (line in lines) {
            if (line.startsWith("@@")) {
                currentHunk?.let { hunks.add(it.copy(content = hunkContent.toString())) }
                hunkContent.clear()

                val headerRegex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
                val match = headerRegex.find(line)
                if (match != null) {
                    currentHunk = Git.Hunk(
                        oldStart = match.groupValues[1].toIntOrNull() ?: 0,
                        oldLines = 0,
                        newStart = match.groupValues[2].toIntOrNull() ?: 0,
                        newLines = 0,
                        content = ""
                    )
                }
            } else if (currentHunk != null) {
                hunkContent.appendLine(line)
            }
        }

        currentHunk?.let { hunks.add(it.copy(content = hunkContent.toString())) }
        return hunks
    }

    private fun emptyChangeSet(directory: String) = Git.ChangeSet(repository = directory)
}
