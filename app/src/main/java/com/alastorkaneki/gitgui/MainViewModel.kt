package com.alastorkaneki.gitgui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val git by lazy(LazyThreadSafetyMode.NONE) { GitService(application) }
    private val extendedGit by lazy(LazyThreadSafetyMode.NONE) { ExtendedGitCommands(application) }
    private val github by lazy(LazyThreadSafetyMode.NONE) { GitHubService() }
    private val mutableState = MutableStateFlow(
        AppState(
            gitName = preferences.gitName,
            gitEmail = preferences.gitEmail,
            rainbowEnabled = preferences.rainbowEnabled,
            rainbowReverse = preferences.rainbowReverse,
            rainbowSpeed = preferences.rainbowSpeed
        )
    )
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    init {
        reloadRepositories()
        preferences.token()?.let { token ->
            viewModelScope.launch {
                runCatching {
                    val profile = github.profile(token)
                    val repositories = github.repositories(token)
                    mutableState.value = mutableState.value.copy(profile = profile, githubRepositories = repositories)
                }
            }
        }
    }

    fun reloadRepositories() = launchAction {
        val repositories = git.repositories()
        val selected = mutableState.value.selectedRepository?.let { current -> repositories.firstOrNull { it.name == current.name } }
        mutableState.value = mutableState.value.copy(repositories = repositories, selectedRepository = selected)
        selected?.let { refreshInternal(it) }
    }

    fun initialize(name: String) = launchAction {
        val repository = git.initialize(name, mutableState.value.gitName, mutableState.value.gitEmail)
        val repositories = git.repositories()
        mutableState.value = mutableState.value.copy(repositories = repositories, selectedRepository = repository, message = "Created ${repository.name}.")
        refreshInternal(repository)
    }

    fun clone(url: String, name: String) = launchAction {
        val repository = git.clone(url, name, preferences.token(), mutableState.value.gitName, mutableState.value.gitEmail)
        val repositories = git.repositories()
        mutableState.value = mutableState.value.copy(repositories = repositories, selectedRepository = repository, message = "Cloned ${repository.name}.")
        refreshInternal(repository)
    }

    fun select(repository: LocalRepository) = launchAction {
        mutableState.value = mutableState.value.copy(selectedRepository = repository)
        refreshInternal(repository)
    }

    fun deleteSelected() = launchAction {
        val repository = requireRepository()
        git.delete(repository)
        mutableState.value = mutableState.value.copy(selectedRepository = null, files = emptyList(), commits = emptyList(), branches = emptyList(), diff = "")
        mutableState.value = mutableState.value.copy(repositories = git.repositories(), message = "Deleted local copy of ${repository.name}.")
    }

    fun refresh() = launchAction { refreshInternal(requireRepository()) }

    fun stage(path: String = ".") = launchAction {
        val repository = requireRepository()
        git.stage(repository, path)
        refreshInternal(repository)
    }

    fun unstage(path: String) = launchAction {
        val repository = requireRepository()
        git.unstage(repository, path)
        refreshInternal(repository)
    }

    fun commit(message: String) = launchAction {
        val repository = requireRepository()
        val hash = git.commit(repository, message, mutableState.value.gitName, mutableState.value.gitEmail)
        mutableState.value = mutableState.value.copy(message = "Committed ${hash.take(8)}.")
        refreshInternal(repository)
    }

    fun fetch() = launchAction {
        mutableState.value = mutableState.value.copy(message = git.fetch(requireRepository(), preferences.token()))
        refreshInternal(requireRepository())
    }

    fun pull() = launchAction {
        mutableState.value = mutableState.value.copy(message = git.pull(requireRepository(), preferences.token()))
        refreshInternal(requireRepository())
    }

    fun push() = launchAction {
        mutableState.value = mutableState.value.copy(message = git.push(requireRepository(), preferences.token()))
        refreshInternal(requireRepository())
    }

    fun execute(command: String) = launchAction {
        val repository = requireRepository()
        val token = preferences.token()
        val output = if (extendedGit.handles(command)) {
            extendedGit.execute(repository, command, token)
        } else {
            git.execute(repository, command, token, mutableState.value.gitName, mutableState.value.gitEmail)
        }
        mutableState.value = mutableState.value.copy(commandOutput = output)
        refreshInternal(repository)
    }

    fun saveSettings(name: String, email: String, enabled: Boolean, reverse: Boolean, speed: Float) {
        preferences.gitName = name
        preferences.gitEmail = email
        preferences.rainbowEnabled = enabled
        preferences.rainbowReverse = reverse
        preferences.rainbowSpeed = speed
        mutableState.value = mutableState.value.copy(
            gitName = name.trim(),
            gitEmail = email.trim(),
            rainbowEnabled = enabled,
            rainbowReverse = reverse,
            rainbowSpeed = speed,
            message = "Settings saved."
        )
    }

    fun connectWithDeviceFlow() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching {
                val clientId = BuildConfig.GITHUB_CLIENT_ID
                check(clientId.isNotBlank() && clientId != CLIENT_ID_PLACEHOLDER) { "GitHub sign-in is not configured in this build." }
                val code = github.requestDeviceCode(clientId)
                mutableState.value = mutableState.value.copy(deviceCode = code, busy = false, message = "Authorize ${code.userCode} on GitHub.")
                val token = github.pollToken(clientId, code)
                preferences.saveToken(token)
                val profile = github.profile(token)
                val repositories = github.repositories(token)
                mutableState.value = mutableState.value.copy(profile = profile, githubRepositories = repositories, deviceCode = null, busy = false, message = "Connected as ${profile.login}.")
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(deviceCode = null, busy = false, message = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun logout() {
        preferences.saveToken(null)
        mutableState.value = mutableState.value.copy(profile = null, githubRepositories = emptyList(), deviceCode = null, message = "GitHub disconnected.")
    }

    fun dismissMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private suspend fun refreshInternal(repository: LocalRepository) {
        val repositories = git.repositories()
        val updated = repositories.firstOrNull { it.name == repository.name } ?: repository
        mutableState.value = mutableState.value.copy(
            repositories = repositories,
            selectedRepository = updated,
            files = git.status(updated),
            commits = git.history(updated),
            branches = git.branches(updated),
            diff = git.diff(updated)
        )
    }

    private fun requireRepository(): LocalRepository = mutableState.value.selectedRepository ?: error("Select a repository first.")

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            runCatching { block() }.onFailure { error ->
                mutableState.value = mutableState.value.copy(message = error.message ?: error.javaClass.simpleName)
            }
            mutableState.value = mutableState.value.copy(busy = false)
        }
    }

    private companion object {
        const val CLIENT_ID_PLACEHOLDER = "GITHUBCLIENTID000000"
    }
}
