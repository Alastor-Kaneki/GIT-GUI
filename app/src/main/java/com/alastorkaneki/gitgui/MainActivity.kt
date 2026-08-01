package com.alastorkaneki.gitgui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        immersive()
        setContent {
            GitGuiTheme {
                val model: MainViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                GitGuiApp(state, model) { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun GitGuiApp(state: AppState, model: MainViewModel, openUrl: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            model.dismissMessage()
        }
    }
    state.deviceCode?.let { code ->
        LaunchedEffect(code.deviceCode) { openUrl(code.verificationUri) }
        DeviceDialog(code, openUrl)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.Black, modifier = Modifier.navigationBarsPadding()) {
                val items = listOf(
                    Triple("Repos", Icons.Default.Folder, 0),
                    Triple("Changes", Icons.Default.Commit, 1),
                    Triple("History", Icons.Default.History, 2),
                    Triple("Commands", Icons.Default.Code, 3),
                    Triple("Settings", Icons.Default.Settings, 4)
                )
                items.forEach { item ->
                    NavigationBarItem(selected = tab == item.third, onClick = { tab = item.third }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when (tab) {
                0 -> RepositoryScreen(state, model)
                1 -> ChangesScreen(state, model)
                2 -> HistoryScreen(state)
                3 -> CommandsScreen(state, model)
                else -> SettingsScreen(state, model)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun RepositoryScreen(state: AppState, model: MainViewModel) {
    var cloneUrl by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("GIT GUI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("AMOLED Git workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            RainbowCard(state) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(state.profile?.let { "Connected as ${it.login}" } ?: "GitHub account", fontWeight = FontWeight.Bold)
                        Text(if (state.profile == null) "One-tap device authorization. Nothing to paste." else "${state.githubRepositories.size} remote repositories loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.profile == null) RainbowButton(state, "Connect", model::connectWithDeviceFlow) else TextButton(onClick = model::logout) { Text("Disconnect") }
                }
            }
        }
        item {
            RainbowCard(state) {
                Text("Clone repository", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                RainbowTextField(state, cloneUrl, { cloneUrl = it }, "HTTPS clone URL", Modifier.fillMaxWidth(), true)
                RainbowTextField(state, cloneName, { cloneName = it }, "Local name (optional)", Modifier.fillMaxWidth(), true)
                Spacer(Modifier.height(8.dp))
                RainbowButton(state, "Clone") { model.clone(cloneUrl, cloneName) }
            }
        }
        item {
            RainbowCard(state) {
                Text("Initialize repository", fontWeight = FontWeight.Bold)
                RainbowTextField(state, newName, { newName = it }, "Repository name", Modifier.fillMaxWidth(), true)
                Spacer(Modifier.height(8.dp))
                RainbowButton(state, "Create") { model.initialize(newName) }
            }
        }
        item { Text("Local repositories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.repositories.isEmpty()) item { Text("No local repositories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.repositories, key = { it.path }) { repository ->
            RainbowCard(state, Modifier.clickable { model.select(repository) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(repository.name, fontWeight = FontWeight.Bold)
                        Text(repository.branch, color = MaterialTheme.colorScheme.secondary)
                        Text(repository.path, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.selectedRepository?.path == repository.path) Text("ACTIVE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.profile != null && state.githubRepositories.isNotEmpty()) {
            item { Text("GitHub repositories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.githubRepositories.take(25), key = { it.fullName }) { repository ->
                RainbowCard(state) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repository.fullName, fontWeight = FontWeight.Bold)
                            Text(if (repository.privateRepo) "Private" else "Public", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { model.clone(repository.cloneUrl, repository.name) }) { Text("Clone") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangesScreen(state: AppState, model: MainViewModel) {
    var commitMessage by remember { mutableStateOf("") }
    val repository = state.selectedRepository
    if (repository == null) return EmptySelection()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(repository.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(repository.branch, color = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = model::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        item {
            RainbowCard(state) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = model::fetch, modifier = Modifier.weight(1f)) { Text("Fetch") }
                    FilledTonalButton(onClick = model::pull, modifier = Modifier.weight(1f)) { Text("Pull") }
                    FilledTonalButton(onClick = model::push, modifier = Modifier.weight(1f)) { Text("Push") }
                }
            }
        }
        item {
            RainbowCard(state) {
                RainbowTextField(state, commitMessage, { commitMessage = it }, "Commit message", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RainbowButton(state, "Stage all") { model.stage() }
                    RainbowButton(state, "Commit") {
                        model.commit(commitMessage)
                        commitMessage = ""
                    }
                }
            }
        }
        item { Text("Working tree (${state.files.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.files.isEmpty()) item { Text("Working tree clean.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.files, key = { it.path + it.state }) { file ->
            RainbowCard(state) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.path, fontFamily = FontFamily.Monospace)
                        Text(file.state, color = if (file.state == "conflict") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    }
                    if (file.state.startsWith("staged")) TextButton(onClick = { model.unstage(file.path) }) { Text("Unstage") }
                    else TextButton(onClick = { model.stage(file.path) }) { Text("Stage") }
                }
            }
        }
        if (state.diff.isNotBlank()) {
            item {
                Text("Diff", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFF090909), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(state.diff, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp), color = Color(0xFFD7FFD7))
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: AppState) {
    val repository = state.selectedRepository ?: return EmptySelection()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(repository.name, color = MaterialTheme.colorScheme.secondary)
        }
        item {
            Text("Branches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.branches.joinToString("  •  ").ifBlank { "No branches" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.commits, key = { it.hash }) { commit ->
            RainbowCard(state) {
                Text(commit.message, fontWeight = FontWeight.Bold)
                Text("${commit.shortHash} • ${commit.author}", color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                Text(DateFormat.getDateTimeInstance().format(Date(commit.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CommandsScreen(state: AppState, model: MainViewModel) {
    var command by remember { mutableStateOf("git status") }
    val examples = listOf("git help", "git status", "git branch -a", "git log", "git diff", "git stash list", "git remote -v")
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Command center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(state.selectedRepository?.name ?: "Select a repository first", color = MaterialTheme.colorScheme.secondary)
        RainbowCard(state) {
            RainbowTextField(
                state = state,
                value = command,
                onValueChange = { command = it },
                label = "Git command",
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(8.dp))
            RainbowButton(state, "Run") { model.execute(command) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            examples.take(3).forEach { example -> TextButton(onClick = { command = example }) { Text(example.removePrefix("git ")) } }
        }
        examples.drop(3).forEach { example -> TextButton(onClick = { command = example }) { Text(example) } }
        Text("Output", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(color = Color(0xFF070707), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().rainbowBorder(state.rainbowEnabled, state.rainbowSpeed, state.rainbowReverse)) {
            Text(state.commandOutput, modifier = Modifier.padding(14.dp), fontFamily = FontFamily.Monospace, color = Color(0xFFD5FFD5))
        }
        Text("Run git help for every embedded command. Desktop-only external tools and features unavailable in JGit are excluded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(state: AppState, model: MainViewModel) {
    var gitName by remember(state.gitName) { mutableStateOf(state.gitName) }
    var gitEmail by remember(state.gitEmail) { mutableStateOf(state.gitEmail) }
    var enabled by remember(state.rainbowEnabled) { mutableStateOf(state.rainbowEnabled) }
    var reverse by remember(state.rainbowReverse) { mutableStateOf(state.rainbowReverse) }
    var speed by remember(state.rainbowSpeed) { mutableStateOf(state.rainbowSpeed) }
    val previewState = state.copy(rainbowEnabled = enabled, rainbowReverse = reverse, rainbowSpeed = speed)
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        RainbowCard(previewState) {
            Text("GitHub", fontWeight = FontWeight.Bold)
            Text(
                state.profile?.let { "Connected as ${it.login}." } ?: "GitHub authorization is built into this app. No Client ID or token needs to be pasted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (state.profile == null) RainbowButton(previewState, "Connect GitHub", model::connectWithDeviceFlow)
            else FilledTonalButton(onClick = model::logout) { Text("Disconnect GitHub") }
        }
        RainbowCard(previewState) {
            Text("Commit identity", fontWeight = FontWeight.Bold)
            RainbowTextField(previewState, gitName, { gitName = it }, "User name", Modifier.fillMaxWidth(), true)
            RainbowTextField(previewState, gitEmail, { gitEmail = it }, "Email", Modifier.fillMaxWidth(), true)
        }
        RainbowCard(previewState) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Animated rainbow outlines")
                RainbowSwitch(previewState, enabled) { enabled = it }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reverse direction")
                RainbowSwitch(previewState, reverse) { reverse = it }
            }
            Text("Speed ${"%.2f".format(speed)}×")
            Slider(speed, { speed = it }, valueRange = 0.25f..3f)
        }
        RainbowButton(previewState, "Save settings") { model.saveSettings(gitName, gitEmail, enabled, reverse, speed) }
        if (state.selectedRepository != null) {
            Button(onClick = model::deleteSelected, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.Delete, null)
                Text("Delete selected local repository")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeviceDialog(code: DeviceCode, openUrl: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Connect GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHub opened automatically. Enter this code:")
                Text(code.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                Text("The app will finish connecting automatically after authorization.")
            }
        },
        confirmButton = { Button(onClick = { openUrl(code.verificationUri) }) { Text("Open GitHub again") } }
    )
}

@Composable
private fun EmptySelection() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Hub, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("Select or clone a repository first.")
        }
    }
}

@Composable
private fun RainbowCard(state: AppState, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().rainbowBorder(state.rainbowEnabled, state.rainbowSpeed, state.rainbowReverse),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF070707))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun RainbowButton(state: AppState, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.rainbowBorder(state.rainbowEnabled, state.rainbowSpeed, state.rainbowReverse, 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text) }
}

@Composable
private fun RainbowTextField(
    state: AppState,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    textStyle: TextStyle? = null
) {
    val shape = RoundedCornerShape(14.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.rainbowBorder(state.rainbowEnabled, state.rainbowSpeed, state.rainbowReverse, 14.dp, 1.8.dp),
        singleLine = singleLine,
        shape = shape,
        textStyle = textStyle ?: LocalTextStyle.current,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (state.rainbowEnabled) Color.Transparent else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (state.rainbowEnabled) Color.Transparent else MaterialTheme.colorScheme.outline,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedContainerColor = Color(0xFF050505),
            unfocusedContainerColor = Color(0xFF050505)
        )
    )
}

@Composable
private fun RainbowSwitch(state: AppState, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val centerX by animateDpAsState(if (checked) 40.dp else 16.dp, label = "rainbow-switch-thumb")
    val angle = rememberRainbowAngle(state.rainbowSpeed, state.rainbowReverse)
    val fallbackBrush: Brush = SolidColor(if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
    Canvas(
        Modifier
            .size(56.dp, 32.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        val brush = if (state.rainbowEnabled) rainbowBrush(size, angle) else fallbackBrush
        val radius = size.height / 2f
        drawRoundRect(brush = brush, cornerRadius = CornerRadius(radius, radius), alpha = if (checked) 0.5f else 0.24f)
        val inset = 2.dp.toPx()
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.68f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(radius - inset, radius - inset)
        )
        drawRoundRect(brush = brush, cornerRadius = CornerRadius(radius, radius), style = Stroke(2.dp.toPx()))
        drawCircle(brush = brush, radius = 10.dp.toPx(), center = Offset(centerX.toPx(), size.height / 2f))
    }
}
