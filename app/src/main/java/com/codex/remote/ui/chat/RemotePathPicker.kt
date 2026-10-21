package com.codex.remote.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState


@Composable
internal fun RemotePathPickerDialog(
    state: AppUiState,
    projectRoot: String,
    onNavigate: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentPath = state.remoteDirectoryPath ?: projectRoot
    val parentPath = remoteParentPath(currentPath)?.takeIf { parent ->
        remotePathIsWithinRoot(projectRoot, parent)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件和文件夹") },
        text = {
            Column {
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.isRemoteDirectoryLoading -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    state.remoteDirectoryError != null -> Column(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.remoteDirectoryError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onNavigate(currentPath) }) { Text("重试") }
                    }
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        if (parentPath != null) {
                            item(key = "parent") {
                                RemotePathRow("..", true) { onNavigate(parentPath) }
                            }
                        }
                        items(state.remoteDirectoryEntries, key = { "${it.name}:${it.isDirectory}" }) { entry ->
                            val childPath = appendRemotePath(currentPath, entry.name)
                            RemotePathRow(entry.name, entry.isDirectory) {
                                if (entry.isDirectory) onNavigate(childPath) else onSelect(childPath)
                            }
                        }
                        if (state.remoteDirectoryEntries.isEmpty() && parentPath == null) {
                            item { Text("该文件夹为空", Modifier.padding(vertical = 24.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(currentPath) }, enabled = !state.isRemoteDirectoryLoading) {
                Text("选择此文件夹")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RemotePathRow(name: String, directory: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (directory) Icons.Outlined.FolderOpen else Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (directory) Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

internal fun appendRemotePath(parent: String, child: String): String {
    val separator = if ('\\' in parent && '/' !in parent) '\\' else '/'
    return parent.trimEnd('/', '\\') + separator + child.trimStart('/', '\\')
}

internal fun remoteParentPath(path: String): String? {
    val trimmed = path.trim().trimEnd('/', '\\')
    if (trimmed.isEmpty() || trimmed == "/" || (trimmed.length == 2 && trimmed[1] == ':')) return null
    val separatorIndex = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    if (separatorIndex < 0) return null
    if (separatorIndex == 0) return "/"
    if (separatorIndex == 2 && trimmed.getOrNull(1) == ':') return trimmed.substring(0, 3)
    return trimmed.substring(0, separatorIndex)
}

private fun remotePathIsWithinRoot(root: String, path: String): Boolean {
    val windows = '\\' in root || root.getOrNull(1) == ':'
    val normalizedRoot = root.trimEnd('/', '\\')
    val normalizedPath = path.trimEnd('/', '\\')
    return if (windows) normalizedPath.startsWith(normalizedRoot, ignoreCase = true) else normalizedPath.startsWith(normalizedRoot)
}

internal fun remotePathComposerToken(root: String, path: String): String {
    val windows = '\\' in root || root.getOrNull(1) == ':'
    val separator = if (windows) '\\' else '/'
    val rootPrefix = root.trimEnd('/', '\\') + separator
    val relative = if (path.startsWith(rootPrefix, ignoreCase = windows)) path.substring(rootPrefix.length) else path
    val normalized = relative.replace('\\', '/')
    return if (' ' in normalized) "@\"$normalized\"" else "@$normalized"
}
