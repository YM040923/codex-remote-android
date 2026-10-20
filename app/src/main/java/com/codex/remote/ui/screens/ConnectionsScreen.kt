package com.codex.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ConnectionDraft
import com.codex.remote.domain.ConnectionDraftIssue
import com.codex.remote.domain.ConnectionStatus
import com.codex.remote.domain.RemotePlatform
import com.codex.remote.domain.SavedConnection
import com.codex.remote.domain.validationIssues
import com.codex.remote.ui.theme.CodexSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SavedConnection?) -> Unit,
    onDelete: (SavedConnection) -> Unit,
    onConnect: (SavedConnection) -> Unit,
    onSave: (ConnectionDraft, Boolean) -> Unit,
    onCloseEditor: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SavedConnection?>(null) }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {},
        topBar = {
            TopAppBar(
                title = { Text("远程主机") },
                navigationIcon = {
                    if (state.activeConnection != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回工作区")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加连接")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.savedConnections.isEmpty()) {
                EmptyConnections(onAdd)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp)
                        .padding(horizontal = CodexSpacing.xLarge, vertical = CodexSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(CodexSpacing.medium),
                ) {
                    Text(
                        "SSH 主机",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    state.savedConnections.forEach { connection ->
                        ConnectionRow(
                            connection = connection,
                            isActive = state.activeConnection?.id == connection.id,
                            isConnecting = state.activeConnection?.id == connection.id && state.connectionStatus == ConnectionStatus.CONNECTING,
                            onConnect = { onConnect(connection) },
                            onEdit = { onEdit(connection) },
                            onDelete = { pendingDelete = connection },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp,
                    ) {
                        Row(Modifier.padding(CodexSpacing.large), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "凭据由 Android Keystore 加密。首次连接保存主机指纹，密钥变化时会阻止连接。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("top-snackbar-host"),
            )
        }
    }

    if (state.showConnectionEditor) {
        ConnectionEditor(
            original = state.editingConnection,
            busy = state.isBusy,
            onDismiss = onCloseEditor,
            onSave = onSave,
        )
    }

    pendingDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 ${connection.name}？") },
            text = { Text("保存的主机和加密凭据将从此设备移除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(connection)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EmptyConnections(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight().widthIn(max = 420.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("连接远程主机", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Codex 运行在远端主机上。连接后会自动导入项目和对话。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("添加 SSH 主机")
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: SavedConnection,
    isActive: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onConnect,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CodexSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 1.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Router, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(connection.name, style = MaterialTheme.typography.titleMedium)
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "当前主机",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Text(
                    "${connection.username}@${connection.host}:${connection.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isConnecting) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作", modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isActive) "重新连接" else "连接") },
                        onClick = {
                            menuExpanded = false
                            onConnect()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ConnectionEditor(
    original: SavedConnection?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ConnectionDraft, Boolean) -> Unit,
) {
    var draft by remember(original?.id) { mutableStateOf(original.toDraft()) }
    var attemptedSave by remember(original?.id) { mutableStateOf(false) }
    val validationIssues = draft.validationIssues(original)
    val validationIssue = validationIssues.firstOrNull()
    val submit: (Boolean) -> Unit = { connectAfterSave ->
        attemptedSave = true
        if (validationIssues.isEmpty()) onSave(draft, connectAfterSave)
    }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭")
                    }
                    Text(
                        if (original == null) "添加 SSH 主机" else "编辑 SSH 主机",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).widthIn(max = 720.dp)
                        .align(Alignment.CenterHorizontally).padding(CodexSpacing.xLarge),
                    verticalArrangement = Arrangement.spacedBy(CodexSpacing.large),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(CodexSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(CodexSpacing.medium),
                        ) {
                            Text("连接信息", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = { draft = draft.copy(name = it) },
                                label = { Text("显示名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                isError = attemptedSave && ConnectionDraftIssue.CONNECTION_NAME in validationIssues,
                                supportingText = if (attemptedSave && ConnectionDraftIssue.CONNECTION_NAME in validationIssues) {
                                    { Text("必填") }
                                } else null,
                            )
                            Text(
                                "项目和对话会从远端 Codex 历史记录中自动读取。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SectionLabel("SSH 主机")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(CodexSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(CodexSpacing.medium),
                        ) {
                            OutlinedTextField(
                                value = draft.host,
                                onValueChange = { draft = draft.copy(host = it) },
                                label = { Text("主机地址") },
                                placeholder = { Text("devbox.example.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                isError = attemptedSave && ConnectionDraftIssue.HOST in validationIssues,
                                supportingText = if (attemptedSave && ConnectionDraftIssue.HOST in validationIssues) {
                                    { Text("必填") }
                                } else null,
                            )
                            OutlinedTextField(
                                value = draft.port,
                                onValueChange = { draft = draft.copy(port = it.filter(Char::isDigit)) },
                                label = { Text("端口") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = attemptedSave && ConnectionDraftIssue.PORT in validationIssues,
                                supportingText = if (attemptedSave && ConnectionDraftIssue.PORT in validationIssues) {
                                    { Text("1-65535") }
                                } else null,
                            )
                            OutlinedTextField(
                                value = draft.username,
                                onValueChange = { draft = draft.copy(username = it) },
                                label = { Text("用户名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                isError = attemptedSave && ConnectionDraftIssue.USERNAME in validationIssues,
                                supportingText = if (attemptedSave && ConnectionDraftIssue.USERNAME in validationIssues) {
                                    { Text("必填") }
                                } else null,
                            )
                        }
                    }
                    SectionLabel("身份验证")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.authType == AuthType.PASSWORD,
                            onClick = { draft = draft.copy(authType = AuthType.PASSWORD) },
                            label = { Text("密码") },
                            shape = RoundedCornerShape(18.dp),
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        FilterChip(
                            selected = draft.authType == AuthType.PRIVATE_KEY,
                            onClick = { draft = draft.copy(authType = AuthType.PRIVATE_KEY) },
                            label = { Text("私钥") },
                            shape = RoundedCornerShape(18.dp),
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                    if (draft.authType == AuthType.PASSWORD) {
                        OutlinedTextField(
                            value = draft.password,
                            onValueChange = { draft = draft.copy(password = it) },
                            label = { Text(if (original == null) "密码" else "密码（留空则保持不变）") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = attemptedSave && ConnectionDraftIssue.PASSWORD in validationIssues,
                            supportingText = if (attemptedSave && ConnectionDraftIssue.PASSWORD in validationIssues) {
                                { Text("必填") }
                            } else null,
                        )
                    } else {
                        OutlinedTextField(
                            value = draft.privateKey,
                            onValueChange = { draft = draft.copy(privateKey = it) },
                            label = { Text(if (original == null) "OpenSSH / PEM 私钥" else "私钥（留空则保持不变）") },
                            minLines = 5,
                            maxLines = 9,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            isError = attemptedSave && ConnectionDraftIssue.PRIVATE_KEY in validationIssues,
                            supportingText = if (attemptedSave && ConnectionDraftIssue.PRIVATE_KEY in validationIssues) {
                                { Text("必填") }
                            } else null,
                        )
                        OutlinedTextField(
                            value = draft.passphrase,
                            onValueChange = { draft = draft.copy(passphrase = it) },
                            label = { Text("私钥口令（可选）") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SectionLabel("远端系统")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RemotePlatform.entries.forEach { platform ->
                            FilterChip(
                                selected = draft.platform == platform,
                                onClick = { draft = draft.copy(platform = platform) },
                                label = { Text(platform.displayName, maxLines = 1, softWrap = false) },
                                shape = RoundedCornerShape(18.dp),
                            )
                        }
                    }
                    if (draft.hostKeyFingerprint.isNotBlank()) {
                        SectionLabel("主机密钥")
                        OutlinedTextField(
                            value = draft.hostKeyFingerprint,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("已保存的指纹") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = {
                                    draft = draft.copy(hostKeyFingerprint = "", clearHostKeyFingerprint = true)
                                }) { Text("清除") }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                HorizontalDivider()
                if (attemptedSave && validationIssue != null) {
                    Text(
                        validationIssue.message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    // Full-screen dialogs can report a zero navigation-bar inset, so reserve fixed space.
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 72.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { submit(false) }, enabled = !busy) { Text("保存") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { submit(true) }, enabled = !busy) { Text("保存并连接") }
                }
            }
        }
    }
}

private val ConnectionDraftIssue.message: String
    get() = when (this) {
        ConnectionDraftIssue.CONNECTION_NAME -> "请输入显示名称。"
        ConnectionDraftIssue.HOST -> "请输入 SSH 主机地址。"
        ConnectionDraftIssue.PORT -> "端口必须介于 1 到 65535 之间。"
        ConnectionDraftIssue.USERNAME -> "请输入用户名。"
        ConnectionDraftIssue.PASSWORD -> "请输入密码。"
        ConnectionDraftIssue.PRIVATE_KEY -> "请粘贴 OpenSSH 或 PEM 格式的私钥。"
    }

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun SavedConnection?.toDraft(): ConnectionDraft = if (this == null) ConnectionDraft() else ConnectionDraft(
    id = id,
    name = name,
    host = host,
    port = port.toString(),
    username = username,
    authType = authType,
    hostKeyFingerprint = hostKeyFingerprint,
    platform = platform,
)

private val RemotePlatform.displayName: String
    get() = when (this) {
        RemotePlatform.AUTO -> "自动"
        RemotePlatform.POSIX -> "Linux / macOS"
        RemotePlatform.WINDOWS -> "Windows"
    }
