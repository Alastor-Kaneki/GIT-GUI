package com.alastorkaneki.gitgui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alastorkaneki.gitgui.data.AppSettings
import com.alastorkaneki.gitgui.data.SecureTokenStore
import com.alastorkaneki.gitgui.data.SettingsRepository
import com.alastorkaneki.gitgui.git.BranchInfo
import com.alastorkaneki.gitgui.git.CommitInfo
import com.alastorkaneki.gitgui.git.GitRepositoryManager
import com.alastorkaneki.gitgui.git.LocalRepositoryInfo
import com.alastorkaneki.gitgui.git.OperationResult
import com.alastorkaneki.gitgui.git.RepositoryStatus
import com.alastorkaneki.gitgui.git.StashInfo
import com.alastorkaneki.gitgui.git.TagInfo
import com.alastorkaneki.gitgui.github.DeviceCode
import com.alastorkaneki.gitgui.github.GitHubAuthService
import com.alastorkaneki.gitgui.github.GitHubRepositoryInfo
import com.alastorkaneki.gitgui.github.GitHubUser
import com.alastorkaneki.gitgui.github.TokenPollResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val repositories: List<LocalRepositoryInfo> = emptyList(),
    val selectedRepository: LocalRepositoryInfo? = null,
    val repositoryStatus: RepositoryStatus? = null,
    val commits: List<CommitInfo> = emptyList(),
    val branches: List<BranchInfo> = emptyList(),
    val stashes: List<StashInfo> = emptyList(),
    val tags: List<TagInfo> = emptyList(),
    val githubUser: GitHubUser? = null,
    val githubRepositories: List<GitHubRepositoryInfo> = emptyList(),
    val deviceCode: DeviceCode? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val tokenStore = SecureTokenStore(application)
    private val git = GitRepositoryManager(application)
    private val github = GitHubAuthService()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        refreshRepositories()
        refreshAccount()
    }

    fun refreshRepositories(select: File? = _state.value.selectedRepository?.path) {
        viewModelScope.launch {
            runLoading {
                val repositories = git.listRepositories()
                val selected = select?.let { path -> repositories.find { it.path == path } }
                    ?: _state.value.selectedRepository?.let { old -> repositories.find { it.path == old.path } }
                _state.update { it.copy(repositories = repositories, selectedRepository = selected) }
                selected?.let { loadRepositoryDetails(it) }
            }
        }
    }

    fun selectRepository(repository: LocalRepositoryInfo) {
        _state.update { it.copy(selectedRepository = repository) }
        viewModelScope.launch { loadRepositoryDetails(repository) }
    }

    fun initRepository(name: String) {
        viewModelScope.launch {
            runLoading {
                val repository = git.initRepository(name)
                postMessage("Created ${repository.name}.")
                refreshRepositories(repository.path)
            }
        }
    }

    fun cloneRepository(url: String, name: String? = null) {
        viewModelScope.launch {
            runLoading {
                val repository = git.cloneRepository(url.trim(), name, tokenStore.read())
                postMessage("Cloned ${repository.name}.")
                refreshRepositories(repository.path)
            }
        }
    }

    fun deleteSelectedRepository() {
        val selected = _state.value.selectedRepository ?: return
        viewModelScope.launch {
            runLoading {
                handle(git.deleteRepository(selected.path))
                _state.update { it.copy(selectedRepository = null, repositoryStatus = null, commits = emptyList()) }
                refreshRepositories(null)
            }
        }
    }

    fun stageAll() = selectedOperation { git.stageAll(it) }
    fun unstageAll() = selectedOperation { git.unstageAll(it) }
    fun commit(message: String, name: String, email: String) = selectedOperation { git.commit(it, message, name, email) }
    fun fetch() = selectedOperation { git.fetch(it, tokenStore.read()) }
    fun pull() = selectedOperation { git.pull(it, tokenStore.read()) }
    fun push() = selectedOperation { git.push(it, tokenStore.read()) }
    fun createBranch(name: String, checkout: Boolean) = selectedOperation { git.createBranch(it, name, checkout) }
    fun checkoutBranch(name: String) = selectedOperation { git.checkoutBranch(it, name) }
    fun deleteBranch(name: String, force: Boolean) = selectedOperation { git.deleteBranch(it, name, force) }
    fun stash(message: String) = selectedOperation { git.stash(it, message) }
    fun applyStash(index: Int) = selectedOperation { git.applyStash(it, index) }
    fun dropStash(index: Int) = selectedOperation { git.dropStash(it, index) }
    fun createTag(name: String, message: String) = selectedOperation { git.createTag(it, name, message) }
    fun merge(branch: String) = selectedOperation { git.merge(it, branch) }
    fun rebase(upstream: String) = selectedOperation { git.rebase(it, upstream) }
    fun cherryPick(commitId: String) = selectedOperation { git.cherryPick(it, commitId) }
    fun hardReset(ref: String) = selectedOperation { git.hardReset(it, ref) }
    fun clean() = selectedOperation { git.clean(it) }

    fun connectWithToken(token: String) {
        viewModelScope.launch {
            runLoading {
                val normalized = token.trim()
                val user = github.validateToken(normalized)
                tokenStore.save(normalized)
                val repositories = github.fetchRepositories(normalized)
                _state.update { it.copy(githubUser = user, githubRepositories = repositories) }
                postMessage("Connected as ${user.login}.")
            }
        }
    }

    fun startDeviceFlow() {
        viewModelScope.launch {
            runLoading {
                val code = github.requestDeviceCode(BuildConfig.GITHUB_CLIENT_ID)
                _state.update { it.copy(deviceCode = code) }
                pollDeviceFlow(code)
            }
        }
    }

    fun disconnectGitHub() {
        tokenStore.clear()
        _state.update { it.copy(githubUser = null, githubRepositories = emptyList(), deviceCode = null) }
        postMessage("GitHub disconnected.")
    }

    fun refreshAccount() {
        val token = tokenStore.read() ?: return
        viewModelScope.launch {
            runCatching {
                val user = github.fetchUser(token)
                val repositories = github.fetchRepositories(token)
                _state.update { it.copy(githubUser = user, githubRepositories = repositories) }
            }.onFailure {
                tokenStore.clear()
                _state.update { state -> state.copy(githubUser = null, githubRepositories = emptyList()) }
            }
        }
    }

    fun setRainbowEnabled(enabled: Boolean) = updateSettings { it.copy(rainbowEnabled = enabled) }
    fun setRainbowReverse(reverse: Boolean) = updateSettings { it.copy(reverseRainbow = reverse) }
    fun setRainbowSpeed(speedMs: Int) = updateSettings { it.copy(rainbowSpeedMs = speedMs.coerceIn(1500, 12000)) }
    fun setImmersiveMode(enabled: Boolean) = updateSettings { it.copy(immersiveMode = enabled) }
    fun setHaptics(enabled: Boolean) = updateSettings { it.copy(hapticsEnabled = enabled) }

    fun clearNotice() {
        _state.update { it.copy(message = null, error = null) }
    }

    private suspend fun loadRepositoryDetails(repository: LocalRepositoryInfo) {
        runCatching {
            val status = git.status(repository.path)
            val commits = git.commits(repository.path)
            val branches = git.branches(repository.path)
            val stashes = git.stashes(repository.path)
            val tags = git.tags(repository.path)
            val refreshed = git.listRepositories().find { it.path == repository.path } ?: repository
            _state.update {
                it.copy(
                    selectedRepository = refreshed,
                    repositoryStatus = status,
                    commits = commits,
                    branches = branches,
                    stashes = stashes,
                    tags = tags
                )
            }
        }.onFailure { postError(it) }
    }

    private fun selectedOperation(block: suspend (File) -> OperationResult) {
        val repository = _state.value.selectedRepository ?: run {
            postError(IllegalStateException("Select a repository first."))
            return
        }
        viewModelScope.launch {
            runLoading {
                handle(block(repository.path))
                refreshRepositories(repository.path)
            }
        }
    }

    private suspend fun pollDeviceFlow(code: DeviceCode) {
        var interval = code.interval.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline && _state.value.deviceCode?.deviceCode == code.deviceCode) {
            delay(interval * 1000L)
            when (val result = github.pollToken(BuildConfig.GITHUB_CLIENT_ID, code.deviceCode)) {
                is TokenPollResult.Success -> {
                    tokenStore.save(result.token)
                    val user = github.fetchUser(result.token)
                    val repositories = github.fetchRepositories(result.token)
                    _state.update {
                        it.copy(githubUser = user, githubRepositories = repositories, deviceCode = null)
                    }
                    postMessage("Connected as ${user.login}.")
                    return
                }
                TokenPollResult.Pending -> Unit
                is TokenPollResult.SlowDown -> interval += result.extraSeconds
                is TokenPollResult.Failure -> {
                    _state.update { it.copy(deviceCode = null) }
                    postError(IllegalStateException(result.message))
                    return
                }
            }
        }
        if (_state.value.deviceCode?.deviceCode == code.deviceCode) {
            _state.update { it.copy(deviceCode = null) }
            postError(IllegalStateException("The GitHub code expired."))
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { block() }.onFailure { postError(it) }
        _state.update { it.copy(loading = false) }
    }

    private fun handle(result: OperationResult) {
        if (result.success) postMessage(result.message) else postError(IllegalStateException(result.message))
    }

    private fun postMessage(message: String) {
        _state.update { it.copy(message = message, error = null) }
    }

    private fun postError(throwable: Throwable) {
        _state.update { it.copy(error = throwable.message ?: throwable.javaClass.simpleName, message = null) }
    }
}
