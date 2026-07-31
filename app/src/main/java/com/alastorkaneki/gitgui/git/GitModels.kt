package com.alastorkaneki.gitgui.git

import java.io.File
import java.time.Instant

data class LocalRepositoryInfo(
    val name: String,
    val path: File,
    val branch: String,
    val remoteUrl: String?,
    val dirty: Boolean
)

data class RepositoryStatus(
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val conflicting: List<String> = emptyList()
) {
    val isClean: Boolean
        get() = added.isEmpty() && changed.isEmpty() && modified.isEmpty() && missing.isEmpty() &&
            removed.isEmpty() && untracked.isEmpty() && conflicting.isEmpty()
}

data class CommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val email: String,
    val time: Instant
)

data class BranchInfo(
    val name: String,
    val current: Boolean,
    val remote: Boolean
)

data class StashInfo(
    val index: Int,
    val id: String,
    val message: String
)

data class TagInfo(
    val name: String,
    val id: String
)

data class OperationResult(
    val success: Boolean,
    val message: String
)
