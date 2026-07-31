package com.alastorkaneki.gitgui.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alastorkaneki.gitgui.AppUiState
import com.alastorkaneki.gitgui.AppViewModel
import com.alastorkaneki.gitgui.BuildConfig
import com.alastorkaneki.gitgui.data.AppSettings
import com.alastorkaneki.gitgui.git.GitCommandCatalog
import com.alastorkaneki.gitgui.git.LocalRepositoryInfo
import com.alastorkaneki.gitgui.github.DeviceCode
import com.alastorkaneki.gitgui.github.GitHubRepositoryInfo
import com.alastorkaneki.gitgui.ui.components.rainbowModifier
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AppTab(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Repository("Repo", Icons.Rounded.Folder),
    Commands("Commands", Icons.Rounded.Code),
    GitHub("GitHub", Icons.Rounded.AccountCircle),
    Settings("Settings", Icons.Rounded.Settings)
}

private data class FieldSpec(
    val label: String,
    val initial: String = "",
    val keyboardType: KeyboardType = KeyboardType.Text,
    val singleLine: Boolean = true
)

private data class FormRequest(
    val title: String,
    val fields: List<FieldSpec>,
    val confirm: String,
    val checkbox: String? = null,
    val checked: Boolean = false,
    val submit: (List<String>, Boolean) -> Unit
)

@Composable
fun GitGuiApp(state: AppUiState, viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(AppTab.Home) }
    var form by remember { mutableStateOf<FormRequest?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, item.title) },
                        label = { Text(item.title, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .safeDrawingPadding()
                .background(Color.Black)
        ) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    state = state,
                    openRepository = {
                        viewModel.selectRepository(it)
                        tab = AppTab.Repository
                    },
                    initialize = {
                        form = FormRequest(
                            "Initialize repository",
                            listOf(FieldSpec("Repository name")),
                            "Create"
                        ) { values, _ -> viewModel.initRepository(values[0]) }
                    },
                    clone = {
                        form = FormRequest(
                            "Clone repository",
                            listOf(FieldSpec("HTTPS clone URL"), FieldSpec("Folder name (optional)")),
                            "Clone"
                        ) { values, _ -> viewModel.cloneRepository(values[0], values[1].takeIf(String::isNotBlank)) }
                    },
                    account = { tab = AppTab.GitHub }
                )
                AppTab.Repository -> RepositoryScreen(
                    state = state,
                    viewModel = viewModel,
                    form = { form = it },
                    confirm = { title, text, action -> confirm = Triple(title, text, action) }
                )
                AppTab.Commands -> CommandsScreen(state.settings)
                AppTab.GitHub -> GitHubScreen(state, viewModel)
                AppTab.Settings -> SettingsScreen(state.settings, viewModel)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    form?.let { request ->
        FormDialog(
            request = request,
            dismiss = { form = null },
            submit = { values, checked ->
                form = null
                request.submit(values, checked)
            }
        )
    }
    confirm?.let { request ->
        ConfirmDialog(
            title = request.first,
            text = request.second,
            dismiss = { confirm = null },
            confirm = {
                confirm = null
                request.third()
            }
        )
    }
}

@Composable
private fun HomeScreen(
    state: AppUiState,
    openRepository: (LocalRepositoryInfo) -> Unit,
    initialize: () -> Unit,
    clone: () -> Unit,
    account: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("GIT GUI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Native Git tools for Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            RainbowCard(state.settings) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(state.githubUser?.let { "Connected as ${it.login}" } ?: "GitHub not connected", fontWeight = FontWeight.Bold)
                        Text(
                            state.githubUser?.let { "${state.githubRepositories.size} accessible repositories" }
                                ?: "Connect for private clones and authenticated pushes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = account) { Text(if (state.githubUser == null) "Connect" else "Open") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = clone, modifier = Modifier.weight(1f)) { Text("Clone") }
                OutlinedButton(onClick = initialize, modifier = Modifier.weight(1f)) { Text("Initialize") }
            }
        }
        item { SectionTitle("Local repositories", state.repositories.size.toString()) }
        if (state.repositories.isEmpty()) {
            item { EmptyCard("No repositories", "Clone a remote repository or initialize a new one.") }
        } else {
            items(state.repositories, key = { it.path.absolutePath }) { repository ->
                RainbowCard(state.settings) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { openRepository(repository) }.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(repository.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (repository.dirty) "DIRTY" else "CLEAN",
                                color = if (repository.dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(repository.branch, fontFamily = FontFamily.Monospace)
                        Text(
                            repository.remoteUrl ?: "Local only",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    form: (FormRequest) -> Unit,
    confirm: (String, String, () -> Unit) -> Unit
) {
    val repository = state.selectedRepository
    if (repository == null) {
        CenterMessage("No repository selected", "Choose a repository from Home.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(repository.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(repository.path.absolutePath, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item {
            RainbowCard(state.settings) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    InfoRow("Branch", repository.branch)
                    InfoRow("Remote", repository.remoteUrl ?: "None")
                    InfoRow("State", if (state.repositoryStatus?.isClean == true) "Clean" else "Changed")
                }
            }
        }
        item {
            ActionFlow(
                listOf(
                    "Stage all" to viewModel::stageAll,
                    "Unstage" to viewModel::unstageAll,
                    "Fetch" to viewModel::fetch,
                    "Pull" to viewModel::pull,
                    "Push" to viewModel::push,
                    "Refresh" to { viewModel.refreshRepositories(repository.path) }
                )
            )
        }
        item {
            ActionFlow(
                listOf(
                    "Commit" to {
                        form(
                            FormRequest(
                                "Commit staged changes",
                                listOf(
                                    FieldSpec("Commit message", singleLine = false),
                                    FieldSpec("Author name", "GIT GUI"),
                                    FieldSpec("Author email", "git-gui@local", KeyboardType.Email)
                                ),
                                "Commit"
                            ) { values, _ -> viewModel.commit(values[0], values[1], values[2]) }
                        )
                    },
                    "New branch" to {
                        form(
                            FormRequest(
                                "Create branch",
                                listOf(FieldSpec("Branch name")),
                                "Create",
                                "Check out after creating",
                                true
                            ) { values, checked -> viewModel.createBranch(values[0], checked) }
                        )
                    },
                    "Stash" to {
                        form(FormRequest("Stash changes", listOf(FieldSpec("Message (optional)")), "Stash") { values, _ ->
                            viewModel.stash(values[0])
                        })
                    },
                    "Tag" to {
                        form(FormRequest("Create tag", listOf(FieldSpec("Tag name"), FieldSpec("Message (optional)")), "Create") { values, _ ->
                            viewModel.createTag(values[0], values[1])
                        })
                    }
                )
            )
        }
        item {
            ActionFlow(
                listOf(
                    "Merge" to { singleField(form, "Merge branch", "Branch or ref", "Merge", viewModel::merge) },
                    "Rebase" to { singleField(form, "Rebase branch", "Upstream branch or ref", "Rebase", viewModel::rebase) },
                    "Cherry-pick" to { singleField(form, "Cherry-pick commit", "Commit ID", "Apply", viewModel::cherryPick) },
                    "Hard reset" to {
                        form(FormRequest("Hard reset", listOf(FieldSpec("Target ref", "HEAD")), "Reset") { values, _ ->
                            confirm("Hard reset?", "Uncommitted changes will be discarded.") { viewModel.hardReset(values[0]) }
                        })
                    },
                    "Clean" to { confirm("Delete untracked files?", "Untracked files and folders will be permanently removed.", viewModel::clean) },
                    "Delete repo" to { confirm("Delete ${repository.name}?", "The managed repository folder will be permanently removed.", viewModel::deleteSelectedRepository) }
                )
            )
        }
        item { SectionTitle("Working tree", statusCount(state).toString()) }
        item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = state.repositoryStatus
                    if (status == null || status.isClean) Text("Working tree clean", color = MaterialTheme.colorScheme.tertiary)
                    else {
                        StatusGroup("Conflicts", status.conflicting)
                        StatusGroup("Added", status.added)
                        StatusGroup("Changed", status.changed + status.modified)
                        StatusGroup("Removed", status.removed + status.missing)
                        StatusGroup("Untracked", status.untracked)
                    }
                }
            }
        }
        item { SectionTitle("Branches", state.branches.size.toString()) }
        items(state.branches, key = { "${it.remote}:${it.name}" }) { branch ->
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, fontWeight = if (branch.current) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace)
                        Text(if (branch.remote) "Remote" else if (branch.current) "Current" else "Local", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!branch.current && !branch.remote) {
                        TextButton(onClick = { viewModel.checkoutBranch(branch.name) }) { Text("Checkout") }
                        TextButton(onClick = {
                            confirm("Delete branch?", branch.name) { viewModel.deleteBranch(branch.name, true) }
                        }) { Text("Delete") }
                    }
                }
            }
        }
        item { SectionTitle("Stashes", state.stashes.size.toString()) }
        if (state.stashes.isEmpty()) item { EmptyCard("No stashes", "Saved working tree states appear here.") }
        items(state.stashes, key = { it.id }) { stash ->
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stash.message, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { viewModel.applyStash(stash.index) }) { Text("Apply") }
                    TextButton(onClick = { viewModel.dropStash(stash.index) }) { Text("Drop") }
                }
            }
        }
        item { SectionTitle("Tags", state.tags.size.toString()) }
        if (state.tags.isEmpty()) item { EmptyCard("No tags", "Create a tag from the actions above.") }
        items(state.tags, key = { it.id }) { tag ->
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                InfoRow(tag.name, tag.id.take(10), Modifier.padding(14.dp))
            }
        }
        item { SectionTitle("Commits", state.commits.size.toString()) }
        items(state.commits, key = { it.id }) { commit ->
            val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(commit.message, fontWeight = FontWeight.Bold)
                    Text(commit.shortId, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.tertiary)
                    Text("${commit.author} • ${formatter.format(commit.time)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CommandsScreen(settings: AppSettings) {
    var query by remember { mutableStateOf("") }
    var supportedOnly by remember { mutableStateOf(false) }
    val commands = remember(query, supportedOnly) {
        GitCommandCatalog.entries.filter {
            (!supportedOnly || it.guiSupported) &&
                (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) || it.description.contains(query, true))
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Git command catalog", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Every standard command is indexed; native GUI actions are marked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Search commands") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { supportedOnly = !supportedOnly }) {
                Checkbox(supportedOnly, { supportedOnly = it })
                Text("Show native GUI actions only")
            }
        }
        items(commands, key = { it.name }) { command ->
            RainbowCard(settings) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("git ${command.name}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(if (command.guiSupported) "GUI" else "CATALOG", color = if (command.guiSupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(command.category, color = MaterialTheme.colorScheme.primary)
                    Text(command.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GitHubScreen(state: AppUiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("GitHub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Account integration for private repositories and HTTPS authentication.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.deviceCode?.let { code -> item { DeviceCodeCard(code, state.settings, context) } }
        if (state.githubUser == null) {
            item {
                RainbowCard(state.settings) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connect account", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = viewModel::startDeviceFlow,
                            enabled = BuildConfig.GITHUB_CLIENT_ID.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Connect with GitHub") }
                        if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) {
                            Text("Add GITHUB_CLIENT_ID to enable device authorization.", color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                        OutlinedTextField(
                            token,
                            { token = it },
                            label = { Text("Personal access token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = { viewModel.connectWithToken(token) },
                            enabled = token.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Connect with token") }
                        Text("Credentials are encrypted with Android Keystore.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                RainbowCard(state.settings) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(state.githubUser.login, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        state.githubUser.name?.let { Text(it) }
                        Text("${state.githubUser.publicRepos} public • ${state.githubUser.privateRepos} private", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(viewModel::disconnectGitHub, Modifier.fillMaxWidth()) { Text("Disconnect") }
                    }
                }
            }
            item { SectionTitle("Accessible repositories", state.githubRepositories.size.toString()) }
            items(state.githubRepositories, key = { it.fullName }) { repository ->
                GitHubRepositoryCard(repository, state.settings) { viewModel.cloneRepository(repository.cloneUrl, repository.name) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, viewModel: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("AMOLED is permanent; effects and behavior are configurable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingSwitch("Rainbow outlines", "Animate borders throughout the interface.", settings.rainbowEnabled, viewModel::setRainbowEnabled) }
        item { SettingSwitch("Reverse direction", "Run the gradient animation in the opposite direction.", settings.reverseRainbow, viewModel::setRainbowReverse) }
        item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Animation speed", fontWeight = FontWeight.Bold)
                    Text("${settings.rainbowSpeedMs} ms per loop", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = settings.rainbowSpeedMs.toFloat(),
                        onValueChange = { viewModel.setRainbowSpeed(it.toInt()) },
                        valueRange = 1500f..12000f
                    )
                }
            }
        }
        item { SettingSwitch("Immersive mode", "Hide system bars until swiped.", settings.immersiveMode, viewModel::setImmersiveMode) }
        item { SettingSwitch("Haptics", "Allow tactile feedback for supported actions.", settings.hapticsEnabled, viewModel::setHaptics) }
        item {
            RainbowCard(settings) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("About", fontWeight = FontWeight.Bold)
                    InfoRow("Version", "0.1.0")
                    InfoRow("Git engine", "JGit 6.10.1")
                    InfoRow("Theme", "AMOLED only")
                }
            }
        }
    }
}

@Composable
private fun RainbowCard(settings: AppSettings, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(rainbowModifier(settings)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
        elevation = CardDefaults.cardElevation(0.dp),
        content = { content() }
    )
}

@Composable
private fun GitHubRepositoryCard(repository: GitHubRepositoryInfo, settings: AppSettings, clone: () -> Unit) {
    RainbowCard(settings) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(repository.fullName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (repository.private) "PRIVATE" else "PUBLIC", color = MaterialTheme.colorScheme.tertiary)
            }
            repository.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${repository.defaultBranch} • ${repository.language ?: "Unknown"} • ★ ${repository.stars}")
            Button(clone, Modifier.fillMaxWidth()) { Text("Clone") }
        }
    }
}

@Composable
private fun DeviceCodeCard(code: DeviceCode, settings: AppSettings, context: Context) {
    RainbowCard(settings) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Enter this code on GitHub", fontWeight = FontWeight.Bold)
            Text(code.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }) { Text("Open GitHub") }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", code.userCode))
                }) { Text("Copy code") }
            }
            Text("The app is waiting for authorization.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, update: (Boolean) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, update)
        }
    }
}

@Composable
private fun ActionFlow(actions: List<Pair<String, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (label, action) -> OutlinedButton(action) { Text(label) } }
    }
}

private fun singleField(form: (FormRequest) -> Unit, title: String, label: String, confirm: String, submit: (String) -> Unit) {
    form(FormRequest(title, listOf(FieldSpec(label)), confirm) { values, _ -> submit(values[0]) })
}

private fun statusCount(state: AppUiState): Int = state.repositoryStatus?.let {
    it.added.size + it.changed.size + it.modified.size + it.missing.size + it.removed.size + it.untracked.size + it.conflicting.size
} ?: 0

@Composable
private fun StatusGroup(title: String, files: List<String>) {
    if (files.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    files.distinct().forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SectionTitle(title: String, count: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyCard(title: String, text: String) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF050505))) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenterMessage(title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormDialog(request: FormRequest, dismiss: () -> Unit, submit: (List<String>, Boolean) -> Unit) {
    var values by remember(request.title) { mutableStateOf(request.fields.map { it.initial }) }
    var checked by remember(request.title) { mutableStateOf(request.checked) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(request.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                request.fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { value -> values = values.toMutableList().also { it[index] = value } },
                        label = { Text(field.label) },
                        singleLine = field.singleLine,
                        minLines = if (field.singleLine) 1 else 3,
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                request.checkbox?.let { label ->
                    Row(Modifier.clickable { checked = !checked }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, { checked = it })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { submit(values, checked) }) { Text(request.confirm) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}
