package com.codex.remote.ui.chat

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.ThreadGoalStatus
import java.text.DateFormat
import java.util.Date


@Composable
internal fun GoalEditorDialog(
    initialObjective: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var objective by remember(initialObjective) { mutableStateOf(initialObjective) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initialObjective.isBlank()) "添加目标" else "编辑目标") },
        text = {
            Column {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth().testTag("goal-editor-input"),
                    label = { Text("目标") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !isSaving,
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(objective) },
                enabled = objective.isNotBlank() && !isSaving,
            ) {
                Text(if (isSaving) "正在保存…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") }
        },
    )
}

@Composable
internal fun ReviewDialog(
    onDismiss: () -> Unit,
    onStart: (ReviewTargetKind, String) -> Unit,
) {
    var target by remember { mutableStateOf(ReviewTargetKind.UNCOMMITTED_CHANGES) }
    var targetValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("代码审查") },
        text = {
            Column {
                ReviewTargetOption(
                    label = "未提交的更改",
                    selected = target == ReviewTargetKind.UNCOMMITTED_CHANGES,
                    onSelect = { target = ReviewTargetKind.UNCOMMITTED_CHANGES },
                )
                ReviewTargetOption(
                    label = "与基准分支比较",
                    selected = target == ReviewTargetKind.BASE_BRANCH,
                    onSelect = { target = ReviewTargetKind.BASE_BRANCH },
                )
                ReviewTargetOption(
                    label = "自定义审查",
                    selected = target == ReviewTargetKind.CUSTOM,
                    onSelect = { target = ReviewTargetKind.CUSTOM },
                )
                if (target != ReviewTargetKind.UNCOMMITTED_CHANGES) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (target == ReviewTargetKind.BASE_BRANCH) "基准分支" else "说明")
                        },
                        minLines = if (target == ReviewTargetKind.CUSTOM) 2 else 1,
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(target, targetValue) },
                enabled = target == ReviewTargetKind.UNCOMMITTED_CHANGES || targetValue.isNotBlank(),
            ) { Text("开始审查") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ReviewTargetOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FeedbackDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val classifications = listOf(
        "bug" to "问题反馈",
        "bad" to "结果不正确",
        "good" to "结果有帮助",
        "other" to "其他",
    )
    var classification by remember { mutableStateOf("bug") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) },
        title = { Text("发送反馈") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                classifications.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = classification == value,
                            onClick = { classification = value },
                        ).padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = classification == value,
                            onClick = { classification = value },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("详细说明") },
                    minLines = 3,
                    maxLines = 6,
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(classification, reason) }, enabled = !isSubmitting) {
                Text("发送")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun McpStatusDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MCP 服务") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    state.isMcpStatusLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在加载远端 MCP 状态…")
                    }
                    !state.mcpStatusError.isNullOrBlank() -> Text(
                        state.mcpStatusError,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.mcpServers.isEmpty() -> Text("尚未配置 MCP 服务")
                    else -> state.mcpServers.forEachIndexed { index, server ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${server.toolCount} 个工具 · ${server.resourceCount} 个资源",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (server.authStatus == "notLoggedIn") {
                                TextButton(
                                    onClick = { onLogin(server.name) },
                                    enabled = !state.isMcpLoginStarting,
                                ) {
                                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("登录")
                                }
                            } else {
                                Text(
                                    server.authStatus.displayMcpAuthStatus(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.isMcpStatusLoading && !state.isMcpLoginStarting) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("重新加载")
            }
        },
    )
}

@Composable
internal fun ArchivedTasksDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onUnarchive: (RemoteThread) -> Unit,
    onDelete: (RemoteThread) -> Unit,
) {
    var searchQuery by remember(state.activeConnection?.id) { mutableStateOf("") }
    val filteredThreads = remember(state.archivedThreads, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) state.archivedThreads else state.archivedThreads.filter { thread ->
            thread.title.contains(query, true) || thread.cwd.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已归档任务") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索已归档任务") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "清除搜索")
                            }
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    state.isArchivedThreadsLoading -> Row(
                        modifier = Modifier.padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在加载已归档任务…")
                    }
                    !state.archivedThreadsError.isNullOrBlank() -> Text(
                        state.archivedThreadsError,
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    filteredThreads.isEmpty() -> Text(
                        if (searchQuery.isBlank()) "没有已归档的任务" else "没有匹配的已归档任务",
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    ) {
                        items(filteredThreads, key = { "archived-${it.id}" }) { thread ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        thread.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${thread.cwd} · ${formatThreadTime(thread.updatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onUnarchive(thread) }) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = "恢复任务")
                                }
                                IconButton(onClick = { onDelete(thread) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "永久删除任务",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.isArchivedThreadsLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("刷新")
            }
        },
    )
}

@Composable
internal fun DeleteArchivedThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
        title = { Text("永久删除任务？") },
        text = { Text("${thread.title}\n\n此操作会删除远端 Codex 历史记录，且无法撤销。") },
        confirmButton = {
            Button(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun RemoteStatusDialog(state: AppUiState, onDismiss: () -> Unit) {
    val usage = state.threadTokenUsage
    val contextPercent = if (usage?.modelContextWindow != null && usage.modelContextWindow > 0) {
        usage.totalTokens * 100.0 / usage.modelContextWindow
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("远端状态") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                StatusLine("任务 ID", state.selectedThreadId ?: "新任务", monospace = true)
                StatusLine("Codex", state.remoteServer?.codexVersion?.ifBlank { "未知" } ?: "未知")
                StatusLine("模型", state.selectedModel ?: "未知")
                StatusLine(
                    "上下文",
                    contextPercent?.let { "${it.toInt().coerceIn(0, 100)}% · ${usage?.totalTokens ?: 0} 个 token" }
                        ?: "等待使用量数据",
                )
                if (state.isStatusLoading) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    state.rateLimits?.let { limits ->
                        limits.planType?.let { StatusLine("计划", it) }
                        limits.primary?.let { StatusLine("主额度", it.displayRateLimit()) }
                        limits.secondary?.let { StatusLine("次级额度", it.displayRateLimit()) }
                        when {
                            limits.creditsUnlimited -> StatusLine("额度", "无限制")
                            !limits.creditsBalance.isNullOrBlank() -> StatusLine("额度", limits.creditsBalance)
                        }
                    }
                    state.statusError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun StatusLine(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}

private fun String.displayMcpAuthStatus(): String = when (this) {
    "notLoggedIn" -> "未登录"
    "bearerToken" -> "API 密钥"
    "oAuth" -> "OAuth"
    "unsupported" -> "无需验证"
    else -> ifBlank { "未知" }
}

private fun com.codex.remote.domain.RateLimitWindowSnapshot.displayRateLimit(): String {
    val reset = resetsAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it * 1000)) }
    return buildString {
        append("已使用 ${usedPercent.toInt().coerceIn(0, 100)}%")
        if (windowDurationMinutes != null) append(" · ${windowDurationMinutes} 分钟")
        if (reset != null) append(" · 重置时间：$reset")
    }
}

@Composable
internal fun ThreadGoalStatus.goalStatusColor(): Color = when (this) {
    ThreadGoalStatus.ACTIVE, ThreadGoalStatus.COMPLETE -> MaterialTheme.colorScheme.secondary
    ThreadGoalStatus.BLOCKED, ThreadGoalStatus.USAGE_LIMITED, ThreadGoalStatus.BUDGET_LIMITED ->
        MaterialTheme.colorScheme.error
    ThreadGoalStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun ThreadGoalStatus.displayGoalStatus(): String = when (this) {
    ThreadGoalStatus.ACTIVE -> "运行中"
    ThreadGoalStatus.PAUSED -> "已暂停"
    ThreadGoalStatus.BLOCKED -> "受阻"
    ThreadGoalStatus.USAGE_LIMITED -> "用量受限"
    ThreadGoalStatus.BUDGET_LIMITED -> "预算受限"
    ThreadGoalStatus.COMPLETE -> "已完成"
}
