package com.alastorkaneki.gitgui.git

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.time.Instant

class GitRepositoryManager(context: Context) {
    val repositoriesRoot: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "repositories"
    ).apply { mkdirs() }

    suspend fun listRepositories(): List<LocalRepositoryInfo> = withContext(Dispatchers.IO) {
        repositoriesRoot.listFiles()
            ?.filter { it.isDirectory && File(it, ".git").isDirectory }
            ?.mapNotNull { directory -> runCatching { describe(directory) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    suspend fun initRepository(name: String): LocalRepositoryInfo = withContext(Dispatchers.IO) {
        val safeName = sanitizeName(name)
        val directory = File(repositoriesRoot, safeName)
        require(!directory.exists() || directory.listFiles().isNullOrEmpty()) { "A non-empty folder already uses that name." }
        directory.mkdirs()
        Git.init().setDirectory(directory).call().use { git ->
            val gitIgnore = File(directory, ".gitignore")
            if (!gitIgnore.exists()) gitIgnore.writeText(".idea/\n.gradle/\nlocal.properties\n*.jks\n*.keystore\n")
            git.add().addFilepattern(".gitignore").call()
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("GIT GUI", "git-gui@local")
                .setCommitter("GIT GUI", "git-gui@local")
                .call()
        }
        describe(directory)
    }

    suspend fun cloneRepository(url: String, name: String?, token: String?): LocalRepositoryInfo = withContext(Dispatchers.IO) {
        val inferred = url.substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }
        val directory = File(repositoriesRoot, sanitizeName(name?.ifBlank { inferred } ?: inferred))
        require(!directory.exists()) { "A repository with that folder name already exists." }
        val command = Git.cloneRepository().setURI(url).setDirectory(directory).setCloneAllBranches(true)
        credentials(token)?.let(command::setCredentialsProvider)
        try {
            command.call().close()
        } catch (throwable: Throwable) {
            directory.deleteRecursively()
            throw throwable
        }
        describe(directory)
    }

    suspend fun deleteRepository(directory: File): OperationResult = withContext(Dispatchers.IO) {
        if (!isManagedRepository(directory)) return@withContext OperationResult(false, "Only managed repositories can be deleted.")
        val deleted = directory.deleteRecursively()
        OperationResult(deleted, if (deleted) "Repository deleted." else "The repository could not be deleted.")
    }

    suspend fun status(directory: File): RepositoryStatus = withGit(directory) { git ->
        val status = git.status().call()
        RepositoryStatus(
            added = status.added.sorted(),
            changed = status.changed.sorted(),
            modified = status.modified.sorted(),
            missing = status.missing.sorted(),
            removed = status.removed.sorted(),
            untracked = status.untracked.sorted(),
            conflicting = status.conflicting.sorted()
        )
    }

    suspend fun commits(directory: File, limit: Int = 100): List<CommitInfo> = withGit(directory) { git ->
        runCatching { git.log().setMaxCount(limit).call() }.getOrDefault(emptyList()).map { commit ->
            CommitInfo(
                id = commit.name,
                shortId = commit.abbreviate(8).name(),
                message = commit.shortMessage,
                author = commit.authorIdent.name,
                email = commit.authorIdent.emailAddress,
                time = Instant.ofEpochSecond(commit.commitTime.toLong())
            )
        }
    }

    suspend fun branches(directory: File): List<BranchInfo> = withGit(directory) { git ->
        val current = git.repository.fullBranch
        git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().map { ref ->
            BranchInfo(
                name = Repository.shortenRefName(ref.name),
                current = ref.name == current,
                remote = ref.name.startsWith(Constants.R_REMOTES)
            )
        }.sortedWith(compareByDescending<BranchInfo> { it.current }.thenBy { it.name })
    }

    suspend fun stageAll(directory: File): OperationResult = operation(directory, "All changes staged.") { git ->
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
    }

    suspend fun unstageAll(directory: File): OperationResult = operation(directory, "Staging area cleared.") { git ->
        git.reset().setMode(ResetCommand.ResetType.MIXED).call()
    }

    suspend fun commit(directory: File, message: String, authorName: String, authorEmail: String): OperationResult =
        operation(directory, "Commit created.") { git ->
            require(message.isNotBlank()) { "Commit message cannot be empty." }
            git.commit()
                .setMessage(message.trim())
                .setAuthor(authorName.ifBlank { "GIT GUI" }, authorEmail.ifBlank { "git-gui@local" })
                .setCommitter(authorName.ifBlank { "GIT GUI" }, authorEmail.ifBlank { "git-gui@local" })
                .call()
        }

    suspend fun createBranch(directory: File, name: String, checkout: Boolean): OperationResult =
        operation(directory, "Branch created.") { git ->
            git.branchCreate().setName(name.trim()).call()
            if (checkout) git.checkout().setName(name.trim()).call()
        }

    suspend fun checkoutBranch(directory: File, name: String): OperationResult = operation(directory, "Checked out $name.") { git ->
        git.checkout().setName(name).call()
    }

    suspend fun deleteBranch(directory: File, name: String, force: Boolean): OperationResult =
        operation(directory, "Branch deleted.") { git ->
            git.branchDelete().setBranchNames(name).setForce(force).call()
        }

    suspend fun fetch(directory: File, token: String?): OperationResult = operation(directory, "Fetch complete.") { git ->
        val command = git.fetch().setRemote("origin").setRemoveDeletedRefs(true)
        credentials(token)?.let(command::setCredentialsProvider)
        command.call()
    }

    suspend fun pull(directory: File, token: String?): OperationResult = operation(directory, "Pull complete.") { git ->
        val command = git.pull().setRemote("origin")
        credentials(token)?.let(command::setCredentialsProvider)
        val result = command.call()
        require(result.isSuccessful) { result.toString() }
    }

    suspend fun push(directory: File, token: String?): OperationResult = operation(directory, "Push complete.") { git ->
        val command = git.push().setRemote("origin").setPushAll().setPushTags()
        credentials(token)?.let(command::setCredentialsProvider)
        val results = command.call().toList()
        val rejected = results.flatMap { it.remoteUpdates }.filter { it.status.name.startsWith("REJECTED") }
        require(rejected.isEmpty()) { rejected.joinToString { "${it.remoteName}: ${it.status}" } }
    }

    suspend fun stash(directory: File, message: String): OperationResult = operation(directory, "Changes stashed.") { git ->
        val command = git.stashCreate()
        if (message.isNotBlank()) command.setWorkingDirectoryMessage(message.trim())
        require(command.call() != null) { "There were no local changes to stash." }
    }

    suspend fun stashes(directory: File): List<StashInfo> = withGit(directory) { git ->
        git.stashList().call().mapIndexed { index, commit ->
            StashInfo(index, commit.name, commit.shortMessage)
        }
    }

    suspend fun applyStash(directory: File, index: Int): OperationResult = operation(directory, "Stash applied.") { git ->
        git.stashApply().setStashRef("stash@{$index}").call()
    }

    suspend fun dropStash(directory: File, index: Int): OperationResult = operation(directory, "Stash dropped.") { git ->
        git.stashDrop().setStashRef(index).call()
    }

    suspend fun tags(directory: File): List<TagInfo> = withGit(directory) { git ->
        git.tagList().call().map { ref -> TagInfo(Repository.shortenRefName(ref.name), ref.objectId.name) }
    }

    suspend fun createTag(directory: File, name: String, message: String): OperationResult = operation(directory, "Tag created.") { git ->
        val command = git.tag().setName(name.trim())
        if (message.isNotBlank()) command.setMessage(message.trim())
        command.call()
    }

    suspend fun merge(directory: File, branch: String): OperationResult = operation(directory, "Merge complete.") { git ->
        val ref = git.repository.findRef(branch) ?: error("Branch not found: $branch")
        val result = git.merge().include(ref).call()
        require(result.mergeStatus.isSuccessful) { result.mergeStatus.toString() }
    }

    suspend fun rebase(directory: File, upstream: String): OperationResult = operation(directory, "Rebase complete.") { git ->
        val result = git.rebase().setUpstream(upstream).call()
        require(result.status.isSuccessful) { result.status.toString() }
    }

    suspend fun cherryPick(directory: File, commitId: String): OperationResult = operation(directory, "Cherry-pick complete.") { git ->
        val objectId = git.repository.resolve(commitId) ?: error("Commit not found: $commitId")
        val result = git.cherryPick().include(objectId).call()
        require(result.status == CherryPickResult.CherryPickStatus.OK) { result.status.toString() }
    }

    suspend fun hardReset(directory: File, ref: String): OperationResult = operation(directory, "Hard reset complete.") { git ->
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(ref).call()
    }

    suspend fun clean(directory: File): OperationResult = operation(directory, "Untracked files cleaned.") { git ->
        git.clean().setCleanDirectories(true).setForce(true).call()
    }

    private suspend fun <T> withGit(directory: File, block: (Git) -> T): T = withContext(Dispatchers.IO) {
        Git.open(directory).use(block)
    }

    private suspend fun operation(directory: File, successMessage: String, block: (Git) -> Unit): OperationResult =
        withContext(Dispatchers.IO) {
            runCatching {
                Git.open(directory).use(block)
                OperationResult(true, successMessage)
            }.getOrElse { OperationResult(false, it.message ?: it.javaClass.simpleName) }
        }

    private fun describe(directory: File): LocalRepositoryInfo {
        FileRepositoryBuilder().setGitDir(File(directory, ".git")).readEnvironment().build().use { repository ->
            val git = Git(repository)
            val status = git.status().call()
            return LocalRepositoryInfo(
                name = directory.name,
                path = directory,
                branch = runCatching { repository.branch }.getOrDefault("DETACHED"),
                remoteUrl = repository.config.getString("remote", "origin", "url"),
                dirty = !status.isClean
            )
        }
    }

    private fun credentials(token: String?): UsernamePasswordCredentialsProvider? =
        token?.takeIf { it.isNotBlank() }?.let { UsernamePasswordCredentialsProvider("oauth2", it) }

    private fun sanitizeName(name: String): String {
        val safe = name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-')
        require(safe.isNotBlank()) { "Repository name cannot be empty." }
        return safe
    }

    private fun isManagedRepository(directory: File): Boolean =
        directory.canonicalPath.startsWith(repositoriesRoot.canonicalPath + File.separator)
}
