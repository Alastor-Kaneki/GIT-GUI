package com.alastorkaneki.gitgui

data class LocalRepository(
    val name: String,
    val path: String,
    val branch: String
)

data class GitFile(
    val path: String,
    val state: String
)

data class CommitItem(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val timestamp: Long
)

data class GitHubProfile(
    val login: String,
    val displayName: String?
)

data class GitHubRepository(
    val name: String,
    val fullName: String,
    val cloneUrl: String,
    val privateRepo: Boolean
)

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class AppState(
    val repositories: List<LocalRepository> = emptyList(),
    val selectedRepository: LocalRepository? = null,
    val files: List<GitFile> = emptyList(),
    val commits: List<CommitItem> = emptyList(),
    val branches: List<String> = emptyList(),
    val diff: String = "",
    val commandOutput: String = "Ready.",
    val profile: GitHubProfile? = null,
    val githubRepositories: List<GitHubRepository> = emptyList(),
    val deviceCode: DeviceCode? = null,
    val clientId: String = "",
    val gitName: String = "Alastor Kaneki",
    val gitEmail: String = "",
    val rainbowEnabled: Boolean = true,
    val rainbowReverse: Boolean = false,
    val rainbowSpeed: Float = 1f,
    val busy: Boolean = false,
    val message: String? = null
)
