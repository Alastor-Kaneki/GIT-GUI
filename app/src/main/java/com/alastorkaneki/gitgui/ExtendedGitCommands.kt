package com.alastorkaneki.gitgui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RemoteSetUrlCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtendedGitCommands(context: Context) {
    private val exports = File(context.filesDir, "exports").apply { mkdirs() }

    fun handles(raw: String): Boolean {
        val name = split(raw.trim().removePrefix("git").trim()).firstOrNull()?.lowercase() ?: return false
        return name in COMMANDS
    }

    suspend fun execute(repository: LocalRepository, raw: String, token: String?): String = withContext(Dispatchers.IO) {
        Git.open(File(repository.path)).use { git ->
            val args = split(raw.trim().removePrefix("git").trim())
            when (args.firstOrNull()?.lowercase()) {
                "help", "--help", "-h" -> help()
                "version", "--version" -> "git-compatible embedded command engine\nJGit 7.6.0.202603022253-r\nGIT GUI 0.2.0"
                "apply" -> apply(git, args)
                "archive" -> archive(git, args)
                "blame", "annotate" -> blame(git, args)
                "describe" -> describe(git, args)
                "gc", "repack" -> gc(git, args)
                "shortlog" -> shortlog(git, args)
                "ls-remote" -> lsRemote(git, args, token)
                "merge-base" -> mergeBase(git, args)
                "name-rev" -> nameRev(git, args)
                "notes" -> notes(git, args)
                "pack-refs" -> packRefs(git)
                "reflog" -> reflog(git, args)
                "remote" -> remote(git, args)
                "rm" -> rm(git, args)
                "mv" -> mv(git, args)
                "submodule" -> submodule(git, args, token)
                "cat-file" -> catFile(git, args)
                "check-ignore" -> checkIgnore(git, args)
                "count-objects" -> countObjects(git)
                "for-each-ref", "show-ref" -> refs(git, args.getOrNull(1))
                "grep" -> grep(git, args)
                "hash-object" -> hashObject(git, args)
                "ls-files" -> lsFiles(git, args)
                "ls-tree" -> lsTree(git, args)
                "rev-list" -> revList(git, args)
                "symbolic-ref" -> symbolicRef(git, args)
                "update-ref" -> updateRef(git, args)
                else -> help()
            }
        }
    }

    private fun apply(git: Git, args: List<String>): String {
        val path = value(args, 1) ?: error("Usage: git apply <patch-file>")
        val patch = workFile(git, path, true)
        val result = FileInputStream(patch).use { git.apply().setPatch(it).call() }
        return result.updatedFiles.joinToString(prefix = "Updated: ") { it.relativeTo(git.repository.workTree).path }.ifBlank { "Patch applied." }
    }

    private fun archive(git: Git, args: List<String>): String {
        val ref = value(args, 1) ?: Constants.HEAD
        val tree = git.repository.resolve("$ref^{tree}") ?: error("Unknown tree $ref.")
        val outputName = option(args, "-o", "--output")?.substringAfterLast('/') ?: "${git.repository.workTree.name}-${safe(ref)}.zip"
        val output = File(exports, if (outputName.endsWith(".zip", true)) outputName else "$outputName.zip")
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            TreeWalk(git.repository).use { walk ->
                walk.addTree(tree)
                walk.isRecursive = true
                while (walk.next()) {
                    if (walk.getFileMode(0) == FileMode.GITLINK) continue
                    zip.putNextEntry(ZipEntry(walk.pathString))
                    git.repository.open(walk.getObjectId(0)).copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
        return "Archive created: ${output.absolutePath}"
    }

    private fun blame(git: Git, args: List<String>): String {
        val path = value(args, 1) ?: error("Usage: git blame <file>")
        val result = git.blame().setFilePath(path).call() ?: return "No blame information."
        result.computeAll()
        val contents = result.resultContents ?: return "No blame information."
        return buildString {
            for (line in 0 until contents.size()) {
                append(result.getSourceCommit(line)?.name?.take(8) ?: "00000000")
                append(' ')
                append(result.getSourceAuthor(line)?.name ?: "Unknown")
                append(' ')
                append(result.getSourceLine(line) + 1)
                append(" | ")
                appendLine(contents.getString(line))
            }
        }.trimEnd()
    }

    private fun describe(git: Git, args: List<String>): String {
        val target = value(args, 1) ?: Constants.HEAD
        val id = git.repository.resolve(target) ?: error("Unknown ref $target.")
        return git.describe().setTarget(id).setLong("--long" in args).setAlways("--always" in args).call() ?: id.name
    }

    private fun gc(git: Git, args: List<String>): String {
        val result = git.gc().setAggressive("--aggressive" in args).call()
        return result.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "Garbage collection complete." }
    }

    private fun shortlog(git: Git, args: List<String>): String {
        val commits = git.log().setMaxCount(maxCount(args, 500)).call().toList()
        return commits.groupBy { it.authorIdent?.name ?: "Unknown" }
            .toList()
            .sortedByDescending { it.second.size }
            .joinToString("\n") { (author, items) ->
                if ("-s" in args || "--summary" in args) "${items.size}\t$author"
                else "$author (${items.size}):\n${items.joinToString("\n") { "      ${it.shortMessage}" }}"
            }
    }

    private fun lsRemote(git: Git, args: List<String>, token: String?): String {
        val remote = value(args, 1) ?: "origin"
        val command = git.lsRemote().setRemote(remote).setHeads("--heads" in args).setTags("--tags" in args)
        credentials(token)?.let(command::setCredentialsProvider)
        return command.call().joinToString("\n") { "${it.objectId.name}\t${it.name}" }.ifBlank { "No remote refs." }
    }

    private fun mergeBase(git: Git, args: List<String>): String {
        val values = values(args, 1)
        check(values.size >= 2) { "Usage: git merge-base <commit> <commit>" }
        RevWalk(git.repository).use { walk ->
            walk.revFilter = RevFilter.MERGE_BASE
            values.take(2).forEach { ref ->
                val id = git.repository.resolve(ref) ?: error("Unknown ref $ref.")
                walk.markStart(walk.parseCommit(id))
            }
            return walk.next()?.name ?: "No merge base."
        }
    }

    private fun nameRev(git: Git, args: List<String>): String {
        val refs = values(args, 1).ifEmpty { listOf(Constants.HEAD) }
        val command = git.nameRev()
        refs.forEach { command.add(git.repository.resolve(it) ?: error("Unknown ref $it.")) }
        return command.call().entries.joinToString("\n") { "${it.key.name}\t${it.value}" }
    }

    private fun notes(git: Git, args: List<String>): String = when (args.getOrNull(1) ?: "list") {
        "list" -> git.notesList().call().joinToString("\n") { "${it.data.name} ${it.name}" }.ifBlank { "No notes." }
        "show" -> {
            val ref = args.getOrNull(2) ?: Constants.HEAD
            RevWalk(git.repository).use { walk ->
                val id = git.repository.resolve(ref) ?: error("Unknown ref $ref.")
                val note = git.notesShow().setObjectId(walk.parseAny(id)).call() ?: return "No note for $ref."
                String(git.repository.open(note.data).bytes, Charsets.UTF_8)
            }
        }
        "add" -> {
            val ref = args.getOrNull(2) ?: Constants.HEAD
            val message = option(args, "-m", "--message") ?: error("Usage: git notes add <ref> -m \"message\"")
            RevWalk(git.repository).use { walk ->
                val id = git.repository.resolve(ref) ?: error("Unknown ref $ref.")
                git.notesAdd().setObjectId(walk.parseAny(id)).setMessage(message).call()
            }
            "Note added to $ref."
        }
        "remove" -> {
            val ref = args.getOrNull(2) ?: Constants.HEAD
            RevWalk(git.repository).use { walk ->
                val id = git.repository.resolve(ref) ?: error("Unknown ref $ref.")
                git.notesRemove().setObjectId(walk.parseAny(id)).call()
            }
            "Note removed from $ref."
        }
        else -> error("Usage: git notes [list|show|add|remove]")
    }

    private fun packRefs(git: Git): String {
        git.packRefs().call()
        return "References packed."
    }

    private fun reflog(git: Git, args: List<String>): String {
        val ref = value(args, 1) ?: Constants.HEAD
        return git.reflog().setRef(ref).call().take(maxCount(args, 100)).mapIndexed { index, entry ->
            "$ref@{$index} ${entry.newId.name.take(8)} ${entry.who.name}: ${entry.comment}"
        }.joinToString("\n").ifBlank { "No reflog entries." }
    }

    private fun remote(git: Git, args: List<String>): String = when (args.getOrNull(1)) {
        null, "-v", "--verbose" -> git.remoteList().call().joinToString("\n") { item ->
            val fetch = item.urIs.joinToString()
            val push = item.pushURIs.ifEmpty { item.urIs }.joinToString()
            "${item.name}\t$fetch (fetch)\n${item.name}\t$push (push)"
        }.ifBlank { "No remotes configured." }
        "add" -> {
            val name = args.getOrNull(2) ?: error("Usage: git remote add <name> <url>")
            val url = args.getOrNull(3) ?: error("Usage: git remote add <name> <url>")
            git.remoteAdd().setName(name).setUri(URIish(url)).call()
            "Remote $name added."
        }
        "remove", "rm" -> {
            val name = args.getOrNull(2) ?: error("Usage: git remote remove <name>")
            git.remoteRemove().setRemoteName(name).call()
            "Remote $name removed."
        }
        "set-url" -> {
            val items = values(args, 2)
            val name = items.getOrNull(0) ?: error("Usage: git remote set-url <name> <url>")
            val url = items.getOrNull(1) ?: error("Usage: git remote set-url <name> <url>")
            git.remoteSetUrl()
                .setRemoteName(name)
                .setRemoteUri(URIish(url))
                .setUriType(if ("--push" in args) RemoteSetUrlCommand.UriType.PUSH else RemoteSetUrlCommand.UriType.FETCH)
                .call()
            "Remote $name URL updated."
        }
        "get-url" -> {
            val name = args.getOrNull(2) ?: error("Usage: git remote get-url <name>")
            val item = git.remoteList().call().firstOrNull { it.name == name } ?: error("Unknown remote $name.")
            (if ("--push" in args) item.pushURIs.ifEmpty { item.urIs } else item.urIs).joinToString("\n")
        }
        "show" -> {
            val name = args.getOrNull(2) ?: error("Usage: git remote show <name>")
            val item = git.remoteList().call().firstOrNull { it.name == name } ?: error("Unknown remote $name.")
            "* remote $name\n  Fetch URL: ${item.urIs.joinToString()}\n  Push URL: ${item.pushURIs.ifEmpty { item.urIs }.joinToString()}\n  Fetch specs: ${item.fetchRefSpecs.joinToString()}"
        }
        else -> error("Usage: git remote [-v|add|remove|set-url|get-url|show]")
    }

    private fun rm(git: Git, args: List<String>): String {
        val paths = values(args, 1)
        check(paths.isNotEmpty()) { "Usage: git rm [--cached] <path>..." }
        val command = git.rm().setCached("--cached" in args)
        paths.forEach(command::addFilepattern)
        command.call()
        return "Removed ${paths.joinToString()}."
    }

    private fun mv(git: Git, args: List<String>): String {
        val items = values(args, 1)
        check(items.size == 2) { "Usage: git mv <source> <destination>" }
        val source = workFile(git, items[0], true)
        val destination = workFile(git, items[1], false)
        destination.parentFile?.mkdirs()
        check(source.renameTo(destination)) { "Could not move ${items[0]} to ${items[1]}." }
        git.rm().setCached(true).addFilepattern(items[0]).call()
        git.add().addFilepattern(items[1]).call()
        return "Moved ${items[0]} to ${items[1]}."
    }

    private fun submodule(git: Git, args: List<String>, token: String?): String = when (args.getOrNull(1) ?: "status") {
        "status" -> {
            val command = git.submoduleStatus()
            values(args, 2).forEach(command::addPath)
            command.call().entries.joinToString("\n") { (path, status) ->
                "$path\t${status.type}\t${status.headId?.name ?: status.indexId?.name.orEmpty()}"
            }.ifBlank { "No submodules." }
        }
        "add" -> {
            val items = values(args, 2)
            val uri = items.getOrNull(0) ?: error("Usage: git submodule add <url> <path>")
            val path = items.getOrNull(1) ?: error("Usage: git submodule add <url> <path>")
            val command = git.submoduleAdd().setURI(uri).setPath(path)
            credentials(token)?.let(command::setCredentialsProvider)
            command.call().close()
            "Submodule added at $path."
        }
        "init" -> {
            val command = git.submoduleInit()
            values(args, 2).forEach(command::addPath)
            command.call().joinToString(prefix = "Initialized: ")
        }
        "update" -> {
            val command = git.submoduleUpdate().setFetch("--remote" in args || "--init" in args)
            values(args, 2).forEach(command::addPath)
            credentials(token)?.let(command::setCredentialsProvider)
            command.call().joinToString(prefix = "Updated: ")
        }
        "sync" -> {
            val command = git.submoduleSync()
            values(args, 2).forEach(command::addPath)
            command.call().entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "Submodules synchronized." }
        }
        "deinit" -> {
            val paths = values(args, 2)
            check(paths.isNotEmpty()) { "Usage: git submodule deinit <path>..." }
            val command = git.submoduleDeinit()
            paths.forEach(command::addPath)
            command.call().joinToString("\n").ifBlank { "Submodules deinitialized." }
        }
        else -> error("Usage: git submodule [status|add|init|update|sync|deinit]")
    }

    private fun catFile(git: Git, args: List<String>): String {
        check(args.size >= 2) { "Usage: git cat-file [-t|-s|-p] <object>" }
        val mode = args.getOrNull(1)?.takeIf { it.startsWith("-") } ?: "-p"
        val ref = args.last()
        val id = git.repository.resolve(ref) ?: error("Unknown object $ref.")
        val loader = git.repository.open(id)
        return when (mode) {
            "-t" -> Constants.typeString(loader.type)
            "-s" -> loader.size.toString()
            "-e" -> "Object exists."
            else -> when (loader.type) {
                Constants.OBJ_COMMIT -> git.log().add(id).setMaxCount(1).call().firstOrNull()?.let { "commit ${it.name}\nAuthor: ${it.authorIdent?.name} <${it.authorIdent?.emailAddress}>\n\n${it.fullMessage}" } ?: ""
                Constants.OBJ_TREE -> lsTree(git, listOf("ls-tree", ref))
                else -> String(loader.getBytes(MAX_OUTPUT), Charsets.UTF_8)
            }
        }
    }

    private fun checkIgnore(git: Git, args: List<String>): String {
        val paths = values(args, 1)
        check(paths.isNotEmpty()) { "Usage: git check-ignore <path>..." }
        val ignored = git.status().call().ignoredNotInIndex
        return paths.filter { path -> ignored.any { it == path || path.startsWith("$it/") } }.joinToString("\n").ifBlank { "No supplied paths are ignored." }
    }

    private fun countObjects(git: Git): String {
        val objects = File(git.repository.directory, "objects")
        val loose = objects.listFiles().orEmpty().filter { it.isDirectory && it.name.length == 2 }.sumOf { it.listFiles().orEmpty().size }
        val packs = File(objects, "pack").listFiles().orEmpty().count { it.extension == "pack" }
        val bytes = objects.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return "count: $loose\nsize: $bytes bytes\nin-pack files: $packs"
    }

    private fun refs(git: Git, pattern: String?): String = git.repository.refDatabase.getRefsByPrefix(Constants.R_REFS)
        .filter { pattern.isNullOrBlank() || it.name.contains(pattern) }
        .sortedBy { it.name }
        .joinToString("\n") { "${it.objectId?.name.orEmpty()} ${it.name}" }
        .ifBlank { "No refs." }

    private fun grep(git: Git, args: List<String>): String {
        val items = values(args, 1)
        val pattern = items.getOrNull(0) ?: error("Usage: git grep <pattern> [path]")
        val prefix = items.getOrNull(1)
        val ignoreCase = "-i" in args || "--ignore-case" in args
        val needle = if (ignoreCase) pattern.lowercase() else pattern
        val cache = git.repository.readDirCache()
        return buildList {
            for (index in 0 until cache.entryCount) {
                val path = cache.getEntry(index).pathString
                if (prefix != null && !path.startsWith(prefix)) continue
                val file = File(git.repository.workTree, path)
                if (!file.isFile || file.length() > MAX_GREP_FILE) continue
                runCatching { file.readLines() }.getOrNull()?.forEachIndexed { line, text ->
                    val source = if (ignoreCase) text.lowercase() else text
                    if (source.contains(needle)) add("$path:${line + 1}:$text")
                }
            }
        }.joinToString("\n").ifBlank { "No matches." }
    }

    private fun hashObject(git: Git, args: List<String>): String {
        val path = value(args, 1) ?: error("Usage: git hash-object <file>")
        val file = workFile(git, path, true)
        git.repository.newObjectInserter().use { inserter ->
            val id = FileInputStream(file).use { inserter.insert(Constants.OBJ_BLOB, file.length(), it) }
            if ("-w" in args) inserter.flush()
            return id.name
        }
    }

    private fun lsFiles(git: Git, args: List<String>): String {
        val cache = git.repository.readDirCache()
        return buildList {
            for (index in 0 until cache.entryCount) {
                val entry = cache.getEntry(index)
                if ("--stage" in args || "-s" in args) add("${entry.fileMode.bits.toString(8)} ${entry.objectId.name} ${entry.stage}\t${entry.pathString}")
                else add(entry.pathString)
            }
        }.joinToString("\n").ifBlank { "Index is empty." }
    }

    private fun lsTree(git: Git, args: List<String>): String {
        val ref = value(args, 1) ?: Constants.HEAD
        val tree = git.repository.resolve("$ref^{tree}") ?: error("Unknown tree $ref.")
        return buildString {
            TreeWalk(git.repository).use { walk ->
                walk.addTree(tree)
                walk.isRecursive = "-r" in args
                while (walk.next()) {
                    append(walk.getFileMode(0).toString())
                    append(' ')
                    append(Constants.typeString(git.repository.open(walk.getObjectId(0)).type))
                    append(' ')
                    append(walk.getObjectId(0).name)
                    append('\t')
                    appendLine(walk.pathString)
                }
            }
        }.trimEnd().ifBlank { "Tree is empty." }
    }

    private fun revList(git: Git, args: List<String>): String {
        val refs = values(args, 1).ifEmpty { listOf(Constants.HEAD) }
        val command = git.log().setMaxCount(maxCount(args, 1000))
        refs.forEach { command.add(git.repository.resolve(it) ?: error("Unknown ref $it.")) }
        return command.call().joinToString("\n") { if ("--oneline" in args) "${it.name} ${it.shortMessage}" else it.name }
    }

    private fun symbolicRef(git: Git, args: List<String>): String {
        check(args.size >= 2) { "Usage: git symbolic-ref <name> [target]" }
        val name = normalizeRef(args[1])
        if (args.size == 2) return git.repository.exactRef(name)?.target?.name ?: error("Unknown ref $name.")
        return git.repository.updateRef(name).link(normalizeRef(args[2])).toString()
    }

    private fun updateRef(git: Git, args: List<String>): String {
        val items = values(args, 1)
        val name = normalizeRef(items.getOrNull(0) ?: error("Usage: git update-ref [-d] <ref> [new-value]"))
        val update = git.repository.updateRef(name)
        if ("-d" in args) return update.delete().toString()
        val newValue = items.getOrNull(1) ?: error("New value is required.")
        update.setNewObjectId(git.repository.resolve(newValue) ?: error("Unknown object $newValue."))
        items.getOrNull(2)?.let { update.setExpectedOldObjectId(git.repository.resolve(it) ?: error("Unknown object $it.")) }
        return update.update().toString()
    }

    private fun help(): String = """
Usable Git commands in GIT GUI:

Workspace: init clone status add apply rm mv restore clean commit
History: log shortlog show diff blame annotate reflog rev-list rev-parse describe name-rev merge-base
Branches: branch checkout switch merge rebase cherry-pick revert reset tag stash
Remotes: remote fetch pull push ls-remote submodule
Objects and refs: archive cat-file hash-object ls-files ls-tree show-ref for-each-ref symbolic-ref update-ref notes pack-refs count-objects gc repack
Search: grep check-ignore
Utility: help version config

init and clone are native actions on the Repositories screen. Commands requiring external desktop tools, Git LFS, credential helpers, multiple worktrees, remote helpers, or features missing from JGit are not exposed.
""".trim()

    private fun credentials(token: String?): UsernamePasswordCredentialsProvider? = token?.takeIf { it.isNotBlank() }?.let {
        UsernamePasswordCredentialsProvider("x-access-token", it)
    }

    private fun workFile(git: Git, path: String, mustExist: Boolean): File {
        val root = git.repository.workTree.canonicalFile
        val file = File(root, path).canonicalFile
        check(file.path == root.path || file.path.startsWith(root.path + File.separator)) { "Path escapes the repository." }
        if (mustExist) check(file.exists()) { "$path does not exist." }
        return file
    }

    private fun values(args: List<String>, start: Int): List<String> = args.drop(start).filterNot { it.startsWith("-") }

    private fun value(args: List<String>, start: Int): String? = values(args, start).firstOrNull()

    private fun option(args: List<String>, vararg names: String): String? {
        args.forEachIndexed { index, item ->
            names.forEach { name ->
                if (item == name) return args.getOrNull(index + 1)
                if (item.startsWith("$name=")) return item.substringAfter('=')
            }
        }
        return null
    }

    private fun maxCount(args: List<String>, fallback: Int): Int {
        args.firstOrNull { it.startsWith("--max-count=") }?.substringAfter('=')?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
        val index = args.indexOf("-n")
        return if (index >= 0) args.getOrNull(index + 1)?.toIntOrNull()?.coerceAtLeast(1) ?: fallback else fallback
    }

    private fun normalizeRef(value: String): String = when {
        value == Constants.HEAD -> value
        value.startsWith(Constants.R_REFS) -> value
        else -> Constants.R_HEADS + value
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "HEAD" }

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

    private companion object {
        val COMMANDS = setOf(
            "help", "--help", "-h", "version", "--version", "apply", "archive", "blame", "annotate", "describe", "gc", "repack", "shortlog", "ls-remote", "merge-base", "name-rev", "notes", "pack-refs", "reflog", "remote", "rm", "mv", "submodule", "cat-file", "check-ignore", "count-objects", "for-each-ref", "show-ref", "grep", "hash-object", "ls-files", "ls-tree", "rev-list", "symbolic-ref", "update-ref"
        )
        const val MAX_OUTPUT = 2 * 1024 * 1024
        const val MAX_GREP_FILE = 2 * 1024 * 1024L
    }
}
