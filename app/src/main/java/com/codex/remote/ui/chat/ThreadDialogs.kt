package com.codex.remote.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.ApprovalKind
import com.codex.remote.domain.RemoteDeviceLogin
import com.codex.remote.domain.RemoteThread
import com.codex.remote.ui.theme.MonoText
import java.text.DateFormat
import java.util.Date


@Composable
internal fun ApprovalDialog(
    approval: com.codex.remote.domain.ApprovalRequest,
    onDecision: (String, Map<String, List<String>>) -> Unit,
) {
    val answers = remember(approval.requestId) {
        mutableStateMapOf<String, String>().apply {
            approval.questions.forEach { question ->
                this[question.id] = question.options.firstOrNull().orEmpty()
            }
        }
    }
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                if (approval.kind == ApprovalKind.FILE_CHANGE) Icons.Outlined.Code else Icons.Outlined.Terminal,
                contentDescription = null,
            )
        },
        title = { Text(approval.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(approval.detail, style = MaterialTheme.typography.bodyMedium)
                if (approval.kind == ApprovalKind.USER_INPUT) {
                    approval.questions.forEach { question ->
                        if (question.header.isNotBlank()) {
                            Text(question.header, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(question.question, style = MaterialTheme.typography.bodyMedium)
                        question.options.forEach { option ->
                            val selected = answers[question.id] == option
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .selectable(selected = selected, onClick = { answers[question.id] = option })
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                                else Spacer(Modifier.width(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                        OutlinedTextField(
                            value = answers[question.id].orEmpty(),
                            onValueChange = { answers[question.id] = it },
                            label = { Text("回复") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        SelectionContainer {
                            Text(approval.detail, Modifier.fillMaxWidth().padding(10.dp), style = MonoText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDecision("accept", answers.mapValues { listOf(it.value) })
            }, enabled = approval.kind != ApprovalKind.USER_INPUT || answers.values.all { it.isNotBlank() }) {
                Text(if (approval.kind == ApprovalKind.USER_INPUT) "发送" else "允许一次")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDecision("decline", emptyMap()) }) { Text("拒绝") }
                if (approval.kind == ApprovalKind.COMMAND || approval.kind == ApprovalKind.FILE_CHANGE) {
                    TextButton(onClick = { onDecision("acceptForSession", emptyMap()) }) { Text("在本会话中允许") }
                }
            }
        },
    )
}

@Composable
internal fun RenameThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(thread.id) { mutableStateOf(thread.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text("重命名任务") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("任务名称") },
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ArchiveThreadDialog(
    thread: RemoteThread,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
        title = { Text("归档任务？") },
        text = {
            Text("${thread.title} 会从“项目”中移除，但远端 Codex 历史记录会保留在归档中。")
        },
        confirmButton = { Button(onClick = onArchive) { Text("归档") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun RemoteDeviceLoginDialog(
    login: RemoteDeviceLogin,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = { Text("登录远端 Codex") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("在浏览器中打开登录页并输入设备码。完成后此窗口会自动关闭。")
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    SelectionContainer {
                        Text(
                            login.userCode,
                            Modifier.fillMaxWidth().padding(14.dp),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        login.verificationUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("打开登录页面") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
internal fun HostKeyConfirmationDialog(
    host: String,
    fingerprint: String,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = { Text("验证 SSH 主机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(host, style = MaterialTheme.typography.titleMedium)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    SelectionContainer {
                        Text(fingerprint, Modifier.fillMaxWidth().padding(12.dp), style = MonoText)
                    }
                }
                Text(
                    "请与服务器管理员或 ssh-keygen 输出核对此指纹。确认前不会发送登录凭据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onTrust) { Text("信任并连接") } },
        dismissButton = { TextButton(onClick = onReject) { Text("取消") } },
    )
}

internal fun formatThreadTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochSeconds * 1000))
}

internal fun String.displayEffort(): String = when (lowercase()) {
    "none" -> "无"
    "minimal" -> "最低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "很高"
    "ultra" -> "极高"
    "max" -> "最高"
    else -> replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}
