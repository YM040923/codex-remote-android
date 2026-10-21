package com.codex.remote.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.ui.chat.ApprovalDialog
import com.codex.remote.ui.chat.ArchiveThreadDialog
import com.codex.remote.ui.chat.ArchivedTasksDialog
import com.codex.remote.ui.chat.DeleteArchivedThreadDialog
import com.codex.remote.ui.chat.HostKeyConfirmationDialog
import com.codex.remote.ui.chat.RemoteDeviceLoginDialog
import com.codex.remote.ui.chat.RenameThreadDialog
import com.codex.remote.ui.chat.WorkspaceContent
import com.codex.remote.ui.chat.WorkspaceSidebar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: AppUiState,
    onOpenConnections: () -> Unit,
    onNewThread: () -> Unit,
    onSelectProject: (RemoteProject) -> Unit,
    onSelectThread: (RemoteThread) -> Unit,
    onLoadOlderHistory: () -> Unit,
    onRenameThread: (RemoteThread, String) -> Unit,
    onArchiveThread: (RemoteThread) -> Unit,
    onLoadArchivedThreads: () -> Unit,
    onUnarchiveThread: (RemoteThread) -> Unit,
    onDeleteArchivedThread: (RemoteThread) -> Unit,
    onSetThreadPinned: (RemoteThread, Boolean) -> Unit,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onRefreshExternalThreadSnapshot: () -> Unit,
    onStartReview: (ReviewTargetKind, String) -> Unit,
    onRunInit: () -> Unit,
    onLoadMcpStatus: () -> Unit,
    onReloadMcpServers: () -> Unit,
    onStartMcpLogin: (String) -> Unit,
    onMcpAuthorizationHandled: () -> Unit,
    onSubmitFeedback: (String, String) -> Unit,
    onSetGoal: (String) -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
    onClearGoal: () -> Unit,
    onShowStatus: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
    onSetCollaborationMode: (String) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onLoadRemoteDirectory: (String) -> Unit,
    onClearRemoteDirectory: () -> Unit,
    onStartLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onApproval: (String, Map<String, List<String>>) -> Unit,
    onTrustHostKey: () -> Unit,
    onRejectHostKey: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var renameTarget by remember { mutableStateOf<RemoteThread?>(null) }
    var archiveTarget by remember { mutableStateOf<RemoteThread?>(null) }
    var showArchivedTasks by remember { mutableStateOf(false) }
    var deleteArchivedTarget by remember { mutableStateOf<RemoteThread?>(null) }
    LaunchedEffect(state.mcpAuthorizationUrl) {
        val authorizationUrl = state.mcpAuthorizationUrl ?: return@LaunchedEffect
        try {
            runCatching { uriHandler.openUri(authorizationUrl) }
        } finally {
            onMcpAuthorizationHandled()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                WorkspaceSidebar(
                    state = state,
                    modifier = Modifier.width(286.dp).fillMaxHeight(),
                    onNewThread = onNewThread,
                    onSelectProject = onSelectProject,
                    onSelectThread = onSelectThread,
                    onRenameThread = { renameTarget = it },
                    onArchiveThread = { archiveTarget = it },
                    onSetThreadPinned = onSetThreadPinned,
                    onOpenArchivedTasks = {
                        showArchivedTasks = true
                        onLoadArchivedThreads()
                    },
                    onOpenConnections = onOpenConnections,
                )
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                WorkspaceContent(
                    state = state,
                    showMenu = false,
                    onMenu = {},
                    onSend = onSend,
                    onStop = onStop,
                    onLoadOlderHistory = onLoadOlderHistory,
                    onCompactThread = onCompactThread,
                    onForkThread = onForkThread,
                    onRefreshExternalThreadSnapshot = onRefreshExternalThreadSnapshot,
                    onSelectThread = onSelectThread,
                    onStartReview = onStartReview,
                    onRunInit = onRunInit,
                    onLoadMcpStatus = onLoadMcpStatus,
                    onReloadMcpServers = onReloadMcpServers,
                    onStartMcpLogin = onStartMcpLogin,
                    onSubmitFeedback = onSubmitFeedback,
                    onSetGoal = onSetGoal,
                    onSetGoalStatus = onSetGoalStatus,
                    onClearGoal = onClearGoal,
                    onShowStatus = onShowStatus,
                    onNewThread = onNewThread,
                    onRenameCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { renameTarget = it } },
                    onArchiveCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { archiveTarget = it } },
                    onSetModel = onSetModel,
                    onSetReasoningEffort = onSetReasoningEffort,
                    onSetServiceTier = onSetServiceTier,
                    onSetCollaborationMode = onSetCollaborationMode,
                    onSetPermissionProfile = onSetPermissionProfile,
                    onSetPermissionMode = onSetPermissionMode,
                    onLoadRemoteDirectory = onLoadRemoteDirectory,
                    onClearRemoteDirectory = onClearRemoteDirectory,
                    onStartLogin = onStartLogin,
                    onOpenConnections = onOpenConnections,
                    onDismissNotice = onDismissNotice,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                    ) {
                        WorkspaceSidebar(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onNewThread = {
                                onNewThread()
                                scope.launch { drawerState.close() }
                            },
                            onSelectProject = {
                                onSelectProject(it)
                                scope.launch { drawerState.close() }
                            },
                            onSelectThread = {
                                onSelectThread(it)
                                scope.launch { drawerState.close() }
                            },
                            onRenameThread = { renameTarget = it },
                            onArchiveThread = { archiveTarget = it },
                            onSetThreadPinned = onSetThreadPinned,
                            onOpenArchivedTasks = {
                                showArchivedTasks = true
                                onLoadArchivedThreads()
                                scope.launch { drawerState.close() }
                            },
                            onOpenConnections = {
                                scope.launch { drawerState.close() }
                                onOpenConnections()
                            },
                        )
                    }
                },
            ) {
                WorkspaceContent(
                    state = state,
                    showMenu = true,
                    onMenu = { scope.launch { drawerState.open() } },
                    onSend = onSend,
                    onStop = onStop,
                    onLoadOlderHistory = onLoadOlderHistory,
                    onCompactThread = onCompactThread,
                    onForkThread = onForkThread,
                    onRefreshExternalThreadSnapshot = onRefreshExternalThreadSnapshot,
                    onSelectThread = onSelectThread,
                    onStartReview = onStartReview,
                    onRunInit = onRunInit,
                    onLoadMcpStatus = onLoadMcpStatus,
                    onReloadMcpServers = onReloadMcpServers,
                    onStartMcpLogin = onStartMcpLogin,
                    onSubmitFeedback = onSubmitFeedback,
                    onSetGoal = onSetGoal,
                    onSetGoalStatus = onSetGoalStatus,
                    onClearGoal = onClearGoal,
                    onShowStatus = onShowStatus,
                    onNewThread = onNewThread,
                    onRenameCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { renameTarget = it } },
                    onArchiveCurrentThread = { state.selectedThreadId?.let { id -> state.threads.firstOrNull { it.id == id } }?.let { archiveTarget = it } },
                    onSetModel = onSetModel,
                    onSetReasoningEffort = onSetReasoningEffort,
                    onSetServiceTier = onSetServiceTier,
                    onSetCollaborationMode = onSetCollaborationMode,
                    onSetPermissionProfile = onSetPermissionProfile,
                    onSetPermissionMode = onSetPermissionMode,
                    onLoadRemoteDirectory = onLoadRemoteDirectory,
                    onClearRemoteDirectory = onClearRemoteDirectory,
                    onStartLogin = onStartLogin,
                    onOpenConnections = onOpenConnections,
                    onDismissNotice = onDismissNotice,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    state.pendingApproval?.let { approval ->
        ApprovalDialog(
            approval = approval,
            onDecision = onApproval,
        )
    }
    state.pendingHostKeyFingerprint?.let { fingerprint ->
        HostKeyConfirmationDialog(
            host = state.activeConnection?.let { "${it.host}:${it.port}" }.orEmpty(),
            fingerprint = fingerprint,
            onTrust = onTrustHostKey,
            onReject = onRejectHostKey,
        )
    }
    state.remoteDeviceLogin?.let { login ->
        RemoteDeviceLoginDialog(
            login = login,
            onOpen = { uriHandler.openUri(login.verificationUrl) },
            onCancel = onCancelLogin,
        )
    }
    renameTarget?.let { thread ->
        RenameThreadDialog(
            thread = thread,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                onRenameThread(thread, name)
                renameTarget = null
            },
        )
    }
    archiveTarget?.let { thread ->
        ArchiveThreadDialog(
            thread = thread,
            onDismiss = { archiveTarget = null },
            onArchive = {
                onArchiveThread(thread)
                archiveTarget = null
            },
        )
    }
    if (showArchivedTasks) {
        ArchivedTasksDialog(
            state = state,
            onDismiss = { showArchivedTasks = false },
            onRefresh = onLoadArchivedThreads,
            onUnarchive = onUnarchiveThread,
            onDelete = { deleteArchivedTarget = it },
        )
    }
    deleteArchivedTarget?.let { thread ->
        DeleteArchivedThreadDialog(
            thread = thread,
            onDismiss = { deleteArchivedTarget = null },
            onDelete = {
                onDeleteArchivedThread(thread)
                deleteArchivedTarget = null
            },
        )
    }
}
