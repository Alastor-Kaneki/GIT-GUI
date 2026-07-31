package com.alastorkaneki.gitgui.github

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val publicRepos: Int,
    val privateRepos: Int
)

data class GitHubRepositoryInfo(
    val name: String,
    val fullName: String,
    val description: String?,
    val private: Boolean,
    val cloneUrl: String,
    val defaultBranch: String,
    val updatedAt: String,
    val language: String?,
    val stars: Int
)

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

sealed interface TokenPollResult {
    data class Success(val token: String) : TokenPollResult
    data object Pending : TokenPollResult
    data class SlowDown(val extraSeconds: Int = 5) : TokenPollResult
    data class Failure(val message: String) : TokenPollResult
}
