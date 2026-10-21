package com.codex.remote.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ConnectionStatus
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.ui.theme.CodexGreen
import com.codex.remote.ui.theme.CodexSpacing
import java.text.DateFormat
import java.util.Date


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceContent(
    state: AppUiState,
    showMenu: Boolean,
    onMenu: () -> Unit,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onLoadOlderHistory: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onRefreshExternalThreadSnapshot: () -> Unit,
    onSelectThread: (RemoteThread) -> Unit,
    onStartReview: (ReviewTargetKind, String) -> Unit,
    onRunInit: () -> Unit,
    onLoadMcpStatus: () -> Unit,
    onReloadMcpServers: () -> Unit,
    onStartMcpLogin: (String) -> Unit,
    onSubmitFeedback: (String, String) -> Unit,
    onSetGoal: (String) -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
    onClearGoal: () -> Unit,
    onShowStatus: () -> Unit,
    onNewThread: () -> Unit,
    onRenameCurrentThread: () -> Unit,
    onArchiveCurrentThread: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
    onSetCollaborationMode: (String) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onLoadRemoteDirectory: (String) -> Unit,
    onClearRemoteDirectory: () -> Unit,
    onStartLogin: () -> Unit,
    onOpenConnections: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showGoalEditor by remember(state.selectedThreadId) { mutableStateOf(false) }
    var pendingGoalObjective by remember(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var showReviewDialog by remember(state.selectedThreadId) { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val conversationKey = state.selectedThreadId ?: "project:${state.selectedProjectPath.orEmpty()}"
    val conversationListState = remember(conversationKey) { LazyListState() }
    var followLatest by remember(conversationKey) { mutableStateOf(true) }
    var hasPositionedConversation by remember(conversationKey) { mutableStateOf(false) }
    val currentThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
    val canCompose = state.connectionStatus == ConnectionStatus.CONNECTED &&
        state.remoteAccount?.canRunCodex == true &&
        state.models.isNotEmpty() &&
        (state.selectedProjectPath?.isNotBlank() == true || state.selectedThreadId != null)
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }
    LaunchedEffect(state.threadGoal?.objective, state.isGoalLoading, state.goalError) {
        val pending = pendingGoalObjective ?: return@LaunchedEffect
        if (!state.isGoalLoading && state.goalError == null && state.threadGoal?.objective == pending) {
            showGoalEditor = false
            pendingGoalObjective = null
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {},
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(horizontal = CodexSpacing.small, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showMenu) {
                            FilledTonalIconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.Menu, contentDescription = "打开会话", modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        // While the session is unsettled the conversation area is replaced by a full screen
                        // that says why, so the header carries only the dot's tone.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ConnectionDot(state)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                currentThread?.title
                                    ?: state.projects.firstOrNull { it.path == state.selectedProjectPath }?.name
                                    ?: state.activeConnection?.name.orEmpty(),
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (currentThread != null && !state.isExternalWriterSnapshot) {
                            ThreadHeaderOverflow(
                                onRename = onRenameCurrentThread,
                                onCompact = onCompactThread,
                                compactEnabled = !state.isTurnRunning,
                                onFork = onForkThread,
                                onReview = { showReviewDialog = true },
                                onArchive = onArchiveCurrentThread,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (state.connectionStatus) {
                ConnectionStatus.CONNECTING -> ConnectionState(
                    icon = null,
                    title = "正在通过 SSH 连接",
                    detail = state.connectionMessage,
                    loading = true,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                ConnectionStatus.ERROR -> ConnectionState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "连接失败",
                    detail = state.connectionMessage,
                    loading = false,
                    action = onOpenConnections,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                ConnectionStatus.CONNECTED -> when {
                    state.remoteAccount?.canRunCodex != true -> RemoteAuthenticationState(
                        loading = state.isLoginStarting,
                        onLogin = onStartLogin,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                    state.models.isEmpty() -> ConnectionState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "没有可用模型",
                        detail = "远端 app-server 没有返回可用模型。请检查远端 Codex 版本和模型提供方配置。",
                        loading = false,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                    else -> Column(Modifier.fillMaxSize().padding(padding)) {
                        if (state.isExternalWriterSnapshot) {
                            ExternalWriterBanner(
                                state = state,
                                onRefresh = onRefreshExternalThreadSnapshot,
                                onFork = onForkThread,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Conversation(
                            state = state,
                            listState = conversationListState,
                            followLatest = followLatest,
                            onFollowLatestChange = { followLatest = it },
                            hasPositionedConversation = hasPositionedConversation,
                            onConversationPositioned = { hasPositionedConversation = true },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onLoadOlderHistory = onLoadOlderHistory,
                            onResend = { prompt -> onSend(prompt, emptyList(), emptyList(), false) },
                        )
                        if (canCompose && !state.isExternalWriterSnapshot) {
                            Composer(
                                state = state,
                                onSend = onSend,
                                onStop = onStop,
                                onCompactThread = onCompactThread,
                                onForkThread = onForkThread,
                                onOpenReview = {
                                    if (state.selectedThreadId == null) {
                                        onStartReview(ReviewTargetKind.UNCOMMITTED_CHANGES, "")
                                    } else {
                                        showReviewDialog = true
                                    }
                                },
                                onRunInit = onRunInit,
                                onOpenMcpStatus = {
                                    showMcpDialog = true
                                    onLoadMcpStatus()
                                },
                                onOpenFeedback = { showFeedbackDialog = true },
                                onEditGoal = { showGoalEditor = true },
                                onSetGoalStatus = onSetGoalStatus,
                                onClearGoal = onClearGoal,
                                onShowStatus = {
                                    showStatusDialog = true
                                    onShowStatus()
                                },
                                onNewThread = onNewThread,
                                onRenameCurrentThread = onRenameCurrentThread,
                                onArchiveCurrentThread = onArchiveCurrentThread,
                                onSetModel = onSetModel,
                                onSetReasoningEffort = onSetReasoningEffort,
                                onSetServiceTier = onSetServiceTier,
                                onSetCollaborationMode = onSetCollaborationMode,
                                onSetPermissionProfile = onSetPermissionProfile,
                                onSetPermissionMode = onSetPermissionMode,
                                onLoadRemoteDirectory = onLoadRemoteDirectory,
                                onClearRemoteDirectory = onClearRemoteDirectory,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                ConnectionStatus.DISCONNECTED -> ConnectionState(
                    icon = Icons.Outlined.Computer,
                    title = "已断开",
                    detail = state.activeConnection?.name.orEmpty(),
                    loading = false,
                    action = onOpenConnections,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                    )
                    .testTag("top-snackbar-host"),
            )
        }
    }
    if (showGoalEditor) {
        GoalEditorDialog(
            initialObjective = state.threadGoal?.objective.orEmpty(),
            isSaving = state.isGoalLoading,
            error = state.goalError,
            onDismiss = {
                showGoalEditor = false
                pendingGoalObjective = null
            },
            onSave = { objective ->
                pendingGoalObjective = objective.trim()
                onSetGoal(objective)
            },
        )
    }
    if (showReviewDialog) {
        ReviewDialog(
            onDismiss = { showReviewDialog = false },
            onStart = { kind, value ->
                showReviewDialog = false
                onStartReview(kind, value)
            },
        )
    }
    if (showMcpDialog) {
        McpStatusDialog(
            state = state,
            onDismiss = { showMcpDialog = false },
            onRefresh = onReloadMcpServers,
            onLogin = onStartMcpLogin,
        )
    }
    if (showStatusDialog) {
        RemoteStatusDialog(state = state, onDismiss = { showStatusDialog = false })
    }
    if (showFeedbackDialog) {
        FeedbackDialog(
            isSubmitting = state.isFeedbackSubmitting,
            error = state.feedbackError,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { classification, reason ->
                showFeedbackDialog = false
                onSubmitFeedback(classification, reason)
            },
        )
    }
}

@Composable
private fun ExternalWriterBanner(
    state: AppUiState,
    onRefresh: () -> Unit,
    onFork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("此对话由另一处 Codex 窗口打开", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            state.isTurnRunning ->
                                "远端显示任务正在执行。此连接没有取得该对话的写入权，所以此页只显示状态，不会发送停止或新消息。"
                            state.threads.firstOrNull { it.id == state.selectedThreadId }?.status.equals("idle", ignoreCase = true) ->
                                "当前未检测到运行中的任务，但此连接仍没有写入权；请使用持有该对话的共享 Codex 服务继续。"
                            state.threads.firstOrNull { it.id == state.selectedThreadId }?.status.equals("notLoaded", ignoreCase = true) ->
                                "当前共享服务未加载该对话，无法确认另一处任务是否仍在运行；此页保持只读。"
                            else ->
                                "正在同步远端对话状态。此页保持只读；若要停止或继续，请确保 Android 与另一窗口连接到同一个共享 Codex 服务。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "状态每 5 秒同步一次。只有成功连接到持有该对话的共享 app-server 时，才能停止任务并继续原对话。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "正在自动尝试接管写者锁，当另一窗口释放后将自动恢复控制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    state.externalSnapshotUpdatedAt?.let { updatedAt ->
                        Text(
                            "最近同步 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updatedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            state.externalSnapshotError?.let { error ->
                Text(
                    "快照更新失败：$error",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.takeoverRetryError?.let { error ->
                Text(
                    "接管重试失败：$error",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRefresh, enabled = !state.isExternalSnapshotRefreshing) {
                    if (state.isExternalSnapshotRefreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("刷新")
                }
                TextButton(onClick = onFork, enabled = !state.isBusy) {
                    Text("另开副本")
                }
            }
        }
    }
}

@Composable
private fun ThreadHeaderOverflow(
    onRename: () -> Unit,
    onCompact: () -> Unit,
    compactEnabled: Boolean,
    onFork: () -> Unit,
    onReview: () -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "任务操作", modifier = Modifier.size(20.dp))
        }
        ThreadActionsMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onRename = {
                expanded = false
                onRename()
            },
            onCompact = {
                expanded = false
                onCompact()
            },
            compactEnabled = compactEnabled,
            onFork = {
                expanded = false
                onFork()
            },
            onReview = {
                expanded = false
                onReview()
            },
            onArchive = {
                expanded = false
                onArchive()
            },
        )
    }
}

// The dot is the only color on this screen and the only live proof that the SSH session is up, so it
// keeps a seat on the title line instead of costing the header a second row.
@Composable
private fun ConnectionDot(state: AppUiState) {
    val color = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> CodexGreen
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
internal fun GoalBar(
    goal: ThreadGoal,
    loading: Boolean,
    onEdit: () -> Unit,
    onSetStatus: (ThreadGoalStatus) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = ConversationMaxWidth)
            .animateContentSize()
            .padding(horizontal = CodexSpacing.large, vertical = CodexSpacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                goal.objective,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "目标 · ${goal.status.displayGoalStatus()}",
                style = MaterialTheme.typography.labelSmall,
                color = goal.status.goalStatusColor(),
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(7.dp))
        }
        IconButton(onClick = onEdit, enabled = !loading, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑目标", modifier = Modifier.size(18.dp))
        }
        when (goal.status) {
            ThreadGoalStatus.ACTIVE -> IconButton(
                onClick = { onSetStatus(ThreadGoalStatus.PAUSED) },
                enabled = !loading,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Outlined.Pause, contentDescription = "暂停目标", modifier = Modifier.size(18.dp))
            }
            ThreadGoalStatus.PAUSED,
            ThreadGoalStatus.BLOCKED,
            ThreadGoalStatus.USAGE_LIMITED,
            -> IconButton(
                onClick = { onSetStatus(ThreadGoalStatus.ACTIVE) },
                enabled = !loading,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "继续目标", modifier = Modifier.size(19.dp))
            }
            ThreadGoalStatus.BUDGET_LIMITED,
            ThreadGoalStatus.COMPLETE,
            -> Unit
        }
        IconButton(onClick = onClear, enabled = !loading, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "清除目标", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RemoteAuthenticationState(
    loading: Boolean,
    onLogin: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(16.dp))
            Text("登录远端 Codex", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "SSH 已连接，但远端 Codex 没有可用账号。登录会发生在远端主机，完成后会自动加载模型。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onLogin, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "正在开始登录" else "使用 ChatGPT 登录")
            }
        }
    }
}

@Composable
private fun ConnectionState(
    icon: ImageVector?,
    title: String,
    detail: String,
    loading: Boolean,
    modifier: Modifier,
    action: (() -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp)
            else icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = action) { Text("连接") }
            }
        }
    }
}
