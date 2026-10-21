package com.codex.remote.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteThread
import com.codex.remote.ui.theme.CodexGreen


@Composable
internal fun WorkspaceSidebar(
    state: AppUiState,
    modifier: Modifier,
    onNewThread: () -> Unit,
    onSelectProject: (RemoteProject) -> Unit,
    onSelectThread: (RemoteThread) -> Unit,
    onRenameThread: (RemoteThread) -> Unit,
    onArchiveThread: (RemoteThread) -> Unit,
    onSetThreadPinned: (RemoteThread, Boolean) -> Unit,
    onOpenArchivedTasks: () -> Unit,
    onOpenConnections: () -> Unit,
) {
    val expandedProjects = remember(state.activeConnection?.id) { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember(state.activeConnection?.id) { mutableStateOf("") }
    val displayedProjects = remember(state.projects, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            state.projects
        } else {
            state.projects.mapNotNull { project ->
                val projectMatches = project.name.contains(query, true) || project.path.contains(query, true)
                val matchingThreads = if (projectMatches) project.threads else project.threads.filter {
                    it.title.contains(query, true) || it.cwd.contains(query, true)
                }
                project.copy(threads = matchingThreads).takeIf { matchingThreads.isNotEmpty() || projectMatches }
            }
        }
    }
    Column(modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text("Codex", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        SidebarAction(Icons.Outlined.Add, "新任务", onNewThread)
        SidebarAction(Icons.Outlined.Archive, "已归档任务", onOpenArchivedTasks)
        // Flat search field — no outline border, just a soft background pill
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            // shortcut: placeholder + clear button via decoration box
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("搜索任务", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "清除搜索",
                            modifier = Modifier.size(16.dp).clickable { searchQuery = "" },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "项目",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            if (displayedProjects.isEmpty()) {
                item(key = "empty-projects") {
                    Text(
                        if (searchQuery.isBlank()) "未找到 Codex 对话" else "没有匹配的任务",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            displayedProjects.forEach { project ->
                item(key = "project-${project.id}") {
                    val expanded = searchQuery.isNotBlank() || expandedProjects[project.id] == true
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { expandedProjects[project.id] = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            contentDescription = if (expanded) "收起项目" else "展开项目",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            project.threads.size.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (searchQuery.isNotBlank() || expandedProjects[project.id] == true) {
                    item(key = "new-${project.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onSelectProject(project)
                                    onNewThread()
                                }
                                .padding(start = 38.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("新任务", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(project.threads, key = { "thread-${it.id}" }) { thread ->
                        ThreadSidebarRow(
                            thread = thread,
                            selected = thread.id == state.selectedThreadId,
                            onSelect = { onSelectThread(thread) },
                            onRename = { onRenameThread(thread) },
                            onArchive = { onArchiveThread(thread) },
                            onSetPinned = { onSetThreadPinned(thread, it) },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConnections).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Computer, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.activeConnection?.name ?: "远端主机", style = MaterialTheme.typography.titleSmall)
                Text(
                    state.activeConnection?.let { "${it.username}@${it.host}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (state.remoteAccount?.canRunCodex == false) {
                Icon(Icons.Outlined.Warning, contentDescription = "未登录", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            } else {
                Icon(Icons.Outlined.Settings, contentDescription = "连接设置", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ThreadSidebarRow(
    thread: RemoteThread,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 38.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                thread.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (thread.isPinned) {
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = "已固定",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (thread.status.contains("active", true)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(CodexGreen))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "任务操作", modifier = Modifier.size(16.dp))
            }
            ThreadActionsMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onRename = {
                    menuOpen = false
                    onRename()
                },
                onArchive = {
                    menuOpen = false
                    onArchive()
                },
                isPinned = thread.isPinned,
                onSetPinned = {
                    menuOpen = false
                    onSetPinned(it)
                },
            )
        }
    }
}

@Composable
internal fun ThreadActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onCompact: (() -> Unit)? = null,
    compactEnabled: Boolean = true,
    onFork: (() -> Unit)? = null,
    onReview: (() -> Unit)? = null,
    isPinned: Boolean? = null,
    onSetPinned: (Boolean) -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("重命名") },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            onClick = onRename,
        )
        onCompact?.let { compact ->
            DropdownMenuItem(
                text = { Text("压缩上下文") },
                leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                enabled = compactEnabled,
                onClick = compact,
            )
        }
        onFork?.let { fork ->
            DropdownMenuItem(
                text = { Text("另开任务") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null) },
                onClick = fork,
            )
        }
        onReview?.let { review ->
            DropdownMenuItem(
                text = { Text("审查更改") },
                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                onClick = review,
            )
        }
        if (isPinned != null) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "取消固定" else "固定") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = { onSetPinned(!isPinned) },
            )
        }
        DropdownMenuItem(
            text = { Text("归档") },
            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            onClick = onArchive,
        )
    }
}

@Composable
private fun SidebarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
