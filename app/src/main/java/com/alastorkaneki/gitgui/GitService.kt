package com.alastorkaneki.gitgui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GitService(context: Context) {
    private val root = File(context.filesDir, "repositories").apply { mkdirs() }

    suspend fun repositories(): List<LocalRepository> = withContext(Dispatchers.IO) {
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && File(it, ".git").isDirectory }
            .mapNotNull { directory ->
                runCatching {
                    Git.open(directory).use { git ->
                        LocalRepository(directory.name, directory.absolutePath, git.repository.branch)
                    }
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun initialize(name: String, gitName: String, gitEmail: String): LocalRepository = withContext(Dispatchers.IO) {
        val clean = safeName(name)
        val directory = File(root, clean)
        check(!directory.exists()) { "A repository named $clean already exists." }
        Git.init().setDirectory(directory).call().use { git ->
            configureIdentity(git, gitName, gitEmail)
            LocalRepository(clean, directory.absolutePath, git.repository.branch)
        }
    }

    suspend fun clone(url: String, name: String, token: String?, gitName: String, gitEmail: String): LocalRepository = withContext(Dispatchers.IO) {
        val clean = safeName(name.ifBlank { url.substringAfterLast('/').removeSuffix(".git") })
        val directory = File(root, clean)
        check(!directory.exists()) { "A repository named $clean already exists." }
        val command = Git.cloneRepository().setURI(url.trim()).setDirectory(directory)
        credentials(token)?.let(command::setCredentialsProvider)
        command.call().use { git ->
            configureIdentity(git, gitName, gitEmail)
            LocalRepository(clean, directory.absolutePath, git.repository.branch)
        }
    }

    suspend fun delete(repository: LocalRepository) = withContext(Dispatchers.IO) {
        check(repository.path.startsWith(root.absolutePath)) { "Invalid repository path." }
        File(repository.path).deleteRecursively()
    }

    suspend fun status(repository: LocalRepository): List<GitFile> = withGit(repository) { git ->
        val status = git.status().call()
        buildList {
            status.added.forEach { add(GitFile(it, "staged new")) }
            status.changed.forEach { add(GitFile(it, "staged modified")) }
            status.removed.forEach { add(GitFile(it, "staged deleted")) }
            status.modified.forEach { add(GitFile(it, "modified")) }
            status.missing.forEach { add(GitFile(it, "deleted")) }
            status.untracked.forEach { add(GitFile(it, "untracked")) }
            status.conflicting.forEach { add(GitFile(it, "conflict")) }
        }.distinctBy { it.path to it.state }.sortedBy { it.path }
    }

    suspend fun history(repository: LocalRepository): List<CommitItem> = withGit(repository) { git ->
        runCatching {
            git.log().setMaxCount(100).call().map { commit ->
                CommitItem(
                    hash = commit.name,
                    shortHash = commit.name.take(8),
                    message = commit.shortMessage,
                    author = commit.authorIdent?.name ?: "Unknown",
                    timestamp = commit.commitTime * 1000L
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun branches(repository: LocalRepository): List<String> = withGit(repository) { git ->
        git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().map { it.name.removePrefix(Constants.R_HEADS).removePrefix(Constants.R_REMOTES) }
    }

    suspend fun diff(repository: LocalRepository): String = withGit(repository) { git ->
        val output = ByteArrayOutputStream()
        DiffFormatter(output).use { formatter ->
            formatter.setRepository(git.repository)
            val head = git.repository.resolve("HEAD^{tree}")
            if (head != null) {
                val oldTree = CanonicalTreeParser().apply {
                    git.repository.newObjectReader().use { reset(it, head) }
                }
                formatter.format(oldTree, org.eclipse.jgit.treewalk.FileTreeIterator(git.repository))
            }
        }
        output.toString(Charsets.UTF_8.name())
    }

    suspend fun stage(repository: LocalRepository, path: String = ".") = withGit(repository) { git ->
        git.add().addFilepattern(path).call()
        if (path == ".") git.add().setUpdate(true).addFilepattern(".").call()
    }

    suspend fun unstage(repository: LocalRepository, path: String) = withGit(repository) { git ->
        git.reset().addPath(path).call()
    }

    suspend fun commit(repository: LocalRepository, message: String, gitName: String, gitEmail: String): String = withGit(repository) { git ->
        check(message.isNotBlank()) { "Commit message is required." }
        configureIdentity(git, gitName, gitEmail)
        val commit = git.commit().setMessage(message.trim()).call()
        commit.name
    }

    suspend fun fetch(repository: LocalRepository, token: String?): String = withGit(repository) { git ->
        val command = git.fetch().setRemoveDeletedRefs(true)
        credentials(token)?.let(command::setCredentialsProvider)
        command.call().messages.ifBlank { "Fetch complete." }
    }

    suspend fun pull(repository: LocalRepository, token: String?): String = withGit(repository) { git ->
        val command = git.pull()
        credentials(token)?.let(command::setCredentialsProvider)
        command.call().toString()
    }

    suspend fun push(repository: LocalRepository, token: String?): String = withGit(repository) { git ->
        val command = git.push().setPushAll().setPushTags()
        credentials(token)?.let(command::setCredentialsProvider)
        command.call().joinToString("\n") { result -> result.remoteUpdates.joinToString { "${it.remoteName}: ${it.status}" } }
    }

    suspend fun execute(repository: LocalRepository, rawCommand: String, token: String?, gitName: String, gitEmail: String): String = withGit(repository) { git ->
        val args = split(rawCommand.trim().removePrefix("git").trim())
        if (args.isEmpty()) return@withGit "Enter a git command."
        when (args[0]) {
            "status" -> {
                val state = git.status().call()
                buildString {
                    appendLine("On branch ${git.repository.branch}")
                    appendLine("Added: ${state.added.joinToString()}")
                    appendLine("Changed: ${state.changed.joinToString()}")
                    appendLine("Modified: ${state.modified.joinToString()}")
                    appendLine("Missing: ${state.missing.joinToString()}")
                    appendLine("Untracked: ${state.untracked.joinToString()}")
                    append("Conflicts: ${state.conflicting.joinToString()}")
                }
            }
            "add" -> {
                val paths = args.drop(1).ifEmpty { listOf(".") }
                paths.forEach { git.add().addFilepattern(it).call() }
                if (paths.contains(".")) git.add().setUpdate(true).addFilepattern(".").call()
                "Staged ${paths.joinToString()}."
            }
            "reset" -> {
                val type = when {
                    "--hard" in args -> ResetCommand.ResetType.HARD
                    "--soft" in args -> ResetCommand.ResetType.SOFT
                    else -> ResetCommand.ResetType.MIXED
                }
                val ref = args.drop(1).firstOrNull { !it.startsWith("-") } ?: Constants.HEAD
                git.reset().setMode(type).setRef(ref).call()
                "Reset $type to $ref."
            }
            "restore" -> {
                val paths = args.drop(1).filterNot { it.startsWith("-") }
                check(paths.isNotEmpty()) { "Usage: git restore <path>" }
                paths.forEach { git.checkout().addPath(it).call() }
                "Restored ${paths.joinToString()}."
            }
            "commit" -> {
                val index = args.indexOfFirst { it == "-m" || it == "--message" }
                check(index >= 0 && index + 1 < args.size) { "Usage: git commit -m \"message\"" }
                configureIdentity(git, gitName, gitEmail)
                val result = git.commit().setMessage(args[index + 1]).call()
                "Committed ${result.name.take(8)}."
            }
            "log" -> git.log().setMaxCount(50).call().joinToString("\n") { "${it.name.take(8)} ${it.shortMessage}" }
            "diff" -> {
                val output = ByteArrayOutputStream()
                DiffFormatter(output).use { formatter ->
                    formatter.setRepository(git.repository)
                    git.diff().call().forEach { formatter.format(it) }
                }
                output.toString(Charsets.UTF_8.name()).ifBlank { "No unstaged diff." }
            }
            "branch" -> branchCommand(git, args)
            "checkout", "switch" -> {
                check(args.size > 1) { "Usage: git ${args[0]} <branch>" }
                val create = "-b" in args || "-c" in args
                val branch = args.last()
                git.checkout().setCreateBranch(create).setName(branch).call()
                "Now on $branch."
            }
            "fetch" -> {
                val command = git.fetch().setRemoveDeletedRefs(true)
                credentials(token)?.let(command::setCredentialsProvider)
                command.call().messages.ifBlank { "Fetch complete." }
            }
            "pull" -> {
                val command = git.pull()
                credentials(token)?.let(command::setCredentialsProvider)
                command.call().toString()
            }
            "push" -> {
                val command = git.push().setPushAll().setPushTags()
                credentials(token)?.let(command::setCredentialsProvider)
                command.call().joinToString("\n") { it.remoteUpdates.joinToString { update -> "${update.remoteName}: ${update.status}" } }
            }
            "merge" -> {
                check(args.size > 1) { "Usage: git merge <branch>" }
                val id = git.repository.resolve(args[1]) ?: error("Unknown ref ${args[1]}.")
                git.merge().include(id).call().toString()
            }
            "rebase" -> {
                check(args.size > 1) { "Usage: git rebase <branch>" }
                git.rebase().setUpstream(args[1]).call().status.toString()
            }
            "cherry-pick" -> {
                check(args.size > 1) { "Usage: git cherry-pick <commit>" }
                val id = git.repository.resolve(args[1]) ?: error("Unknown commit ${args[1]}.")
                git.cherryPick().include(id).call().status.toString()
            }
            "revert" -> {
                check(args.size > 1) { "Usage: git revert <commit>" }
                val id = git.repository.resolve(args[1]) ?: error("Unknown commit ${args[1]}.")
                RevWalk(git.repository).use { walk ->
                    git.revert().include(walk.parseCommit(id)).call()?.name?.take(8) ?: "Revert failed."
                }
            }
            "stash" -> stashCommand(git, args)
            "tag" -> tagCommand(git, args)
            "clean" -> git.clean().setCleanDirectories("-d" in args).setIgnore("-x" !in args).call().joinToString(prefix = "Removed: ")
            "remote" -> remoteCommand(git)
            "config" -> configCommand(git, args)
            "rev-parse" -> {
                check(args.size > 1) { "Usage: git rev-parse <ref>" }
                git.repository.resolve(args[1])?.name ?: error("Unknown ref ${args[1]}.")
            }
            "show" -> showCommand(git, args.getOrNull(1) ?: Constants.HEAD)
            else -> "${args[0]} is not implemented by the embedded JGit command engine yet."
        }
    }

    private fun branchCommand(git: Git, args: List<String>): String {
        if (args.size == 1 || "-a" in args) {
            return git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().joinToString("\n") { ref ->
                val name = ref.name.removePrefix(Constants.R_HEADS).removePrefix(Constants.R_REMOTES)
                if (name == git.repository.branch) "* $name" else "  $name"
            }
        }
        if ("-d" in args || "-D" in args) {
            val name = args.last()
            return git.branchDelete().setForce("-D" in args).setBranchNames(name).call().joinToString()
        }
        if ("-m" in args) {
            val names = args.dropWhile { it != "-m" }.drop(1)
            val command = git.branchRename()
            if (names.size > 1) command.setOldName(names[0])
            command.setNewName(names.last())
            return command.call().name
        }
        return git.branchCreate().setName(args.last()).call().name
    }

    private fun stashCommand(git: Git, args: List<String>): String = when (args.getOrNull(1)) {
        "list" -> git.stashList().call().mapIndexed { index, commit -> "stash@{$index}: ${commit.shortMessage}" }.joinToString("\n")
        "apply" -> git.stashApply().setStashRef(args.getOrNull(2) ?: "stash@{0}").call().name
        "drop" -> git.stashDrop().setStashRef(stashIndex(args.getOrNull(2))).call()?.name ?: "Dropped."
        "pop" -> {
            val ref = args.getOrNull(2) ?: "stash@{0}"
            git.stashApply().setStashRef(ref).call()
            git.stashDrop().setStashRef(stashIndex(ref)).call()
            "Applied and dropped $ref."
        }
        else -> git.stashCreate().setWorkingDirectoryMessage(args.drop(2).joinToString(" ").ifBlank { "GIT GUI stash" }).call()?.name ?: "Nothing to stash."
    }

    private fun stashIndex(ref: String?): Int = ref
        ?.substringAfter("stash@{")
        ?.substringBefore('}')
        ?.toIntOrNull()
        ?: 0

    private fun tagCommand(git: Git, args: List<String>): String {
        if (args.size == 1) return git.tagList().call().joinToString("\n") { it.name.removePrefix(Constants.R_TAGS) }
        if ("-d" in args) return git.tagDelete().setTags(args.last()).call().joinToString()
        return git.tag().setName(args.last()).call().name.removePrefix(Constants.R_TAGS)
    }

    private fun remoteCommand(git: Git): String {
        val config = git.repository.config
        return config.getSubsections("remote").joinToString("\n") { name ->
            val fetch = config.getString("remote", name, "url").orEmpty()
            val push = config.getString("remote", name, "pushurl") ?: fetch
            "$name\t$fetch (fetch)\n$name\t$push (push)"
        }.ifBlank { "No remotes configured." }
    }

    private fun configCommand(git: Git, args: List<String>): String {
        check(args.size > 1) { "Usage: git config <section.key> [value]" }
        val key = args[1]
        val section = key.substringBefore('.')
        val name = key.substringAfter('.', "")
        check(name.isNotBlank()) { "Use section.key syntax." }
        val config = git.repository.config
        if (args.size > 2) {
            config.setString(section, null, name, args.drop(2).joinToString(" "))
            config.save()
            return "$key updated."
        }
        return config.getString(section, null, name).orEmpty()
    }

    private fun showCommand(git: Git, ref: String): String {
        val id = git.repository.resolve(ref) ?: error("Unknown ref $ref.")
        RevWalk(git.repository).use { walk ->
            val commit = walk.parseCommit(id)
            val date = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(commit.commitTime.toLong()))
            return buildString {
                appendLine("commit ${commit.name}")
                appendLine("Author: ${commit.authorIdent?.name} <${commit.authorIdent?.emailAddress}>")
                appendLine("Date: $date")
                appendLine()
                append(commit.fullMessage)
            }
        }
    }

    private fun configureIdentity(git: Git, name: String, email: String) {
        val config = git.repository.config
        if (name.isNotBlank()) config.setString("user", null, "name", name)
        if (email.isNotBlank()) config.setString("user", null, "email", email)
        config.save()
    }

    private fun credentials(token: String?): UsernamePasswordCredentialsProvider? = token?.takeIf { it.isNotBlank() }?.let {
        UsernamePasswordCredentialsProvider("x-access-token", it)
    }

    private suspend fun <T> withGit(repository: LocalRepository, block: (Git) -> T): T = withContext(Dispatchers.IO) {
        Git.open(File(repository.path)).use(block)
    }

    private fun safeName(value: String): String {
        val clean = value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-')
        check(clean.isNotBlank()) { "Repository name is required." }
        return clean
    }

    private fun split(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        input.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character.isWhitespace() -> if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
