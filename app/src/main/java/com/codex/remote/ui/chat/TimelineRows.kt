package com.codex.remote.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.FileChangeSummary
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.ui.theme.MonoText


@Composable
internal fun TimelineRow(item: TimelineItem, modifier: Modifier, flat: Boolean = false) {
    when (item.kind) {
        TimelineKind.USER -> Row(modifier, horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.widthIn(max = 620.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 7.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    SelectionContainer { Text(item.body, style = MaterialTheme.typography.bodyLarge) }
                    if (item.isGoal) {
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.testTag("timeline-goal-${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "作为目标发送",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        // Assistant messages sit directly on the page background; hierarchy comes from
        // spacing and weight, not from a card, matching the reference chat clients.
        TimelineKind.AGENT -> MarkdownBlockRenderer(
            item.body,
            modifier.widthIn(max = ConversationMaxWidth)
                .testTag("timeline-agent-${item.id}"),
        )
        TimelineKind.REASONING -> ExpandableTool(
            modifier,
            Icons.Outlined.SmartToy,
            item.title.ifBlank { "推理" },
            item.body,
            item.status,
            expansionKey = item.id,
            detailTitle = "思考过程",
            flat = flat,
        )
        TimelineKind.PLAN -> ExpandableTool(
            modifier,
            Icons.AutoMirrored.Outlined.List,
            item.title.ifBlank { "计划" },
            item.body,
            item.status,
            expansionKey = item.id,
            detailTitle = "执行计划",
            flat = flat,
        )
        TimelineKind.COMMAND -> ExpandableTool(
            modifier, Icons.Outlined.Terminal, item.title.ifBlank { "命令" }, item.body, item.status,
            expansionKey = item.id, mono = true, detailTitle = "执行命令",
            commandLine = item.title.takeIf { it.isNotBlank() && it != MultiCommandTitle },
            flat = flat,
        )
        TimelineKind.FILE_CHANGE -> FileChangesTool(item, modifier, flat = flat)
        TimelineKind.TOOL -> ExpandableTool(
            modifier, Icons.Outlined.Build, item.title.ifBlank { "工具" }, item.body, item.status,
            expansionKey = item.id, mono = true, detailTitle = "工具输出", flat = flat,
        )
        TimelineKind.REVIEW -> ExpandableTool(
            modifier, Icons.Outlined.Code, item.title.ifBlank { "代码审查" }, item.body, item.status,
            expansionKey = item.id, detailTitle = "代码审查", flat = flat,
        )
        TimelineKind.COMPACTION -> Row(
            modifier = modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Compress,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                item.title.ifBlank { "上下文已压缩" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TimelineKind.ERROR -> ExpandableTool(
            modifier, Icons.Outlined.ErrorOutline, item.title.ifBlank { "错误" }, item.body, item.status,
            expansionKey = item.id,
        )
    }
}

// Same labelSmall gray as the code block's copy affordance, so a finished reply gains an action without
// gaining a second visual voice. Codex has no regenerate RPC and stores no remote item id, so "再来一轮"
// honestly means re-sending the prompt as a new turn.
@Composable
internal fun ReplyActions(
    body: String,
    prompt: String,
    onResend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(body) { mutableStateOf(false) }
    val actionColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(body))
                copied = true
            },
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(
                if (copied) "已复制" else "复制",
                style = MaterialTheme.typography.labelSmall,
                color = actionColor,
            )
        }
        TextButton(
            onClick = { onResend(prompt) },
            enabled = prompt.isNotBlank(),
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("再来一轮", style = MaterialTheme.typography.labelSmall, color = actionColor)
        }
    }
}

@Composable
private fun FileChangesTool(item: TimelineItem, modifier: Modifier, flat: Boolean = false) {
    var detailOpen by remember(item.id) { mutableStateOf(false) }
    val counts = item.fileChanges.fold(0 to 0) { total, change ->
        val changeCounts = diffLineCounts(change.diff)
        total.first + changeCounts.first to total.second + changeCounts.second
    }
    val title = when (item.fileChanges.size) {
        0 -> item.title.ifBlank { "文件更改" }
        1 -> item.fileChanges.first().path
        else -> "已编辑 ${item.fileChanges.size} 个文件"
    }
    Surface(
        modifier = modifier.testTag("file-changes-${item.id}"),
        shape = MaterialTheme.shapes.small,
        color = if (flat) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        StepRow(
            icon = Icons.Outlined.Code,
            title = title,
            status = item.status,
            flat = flat,
            additions = counts.first,
            deletions = counts.second,
            onClick = { detailOpen = true },
        )
    }
    if (detailOpen) {
        StepDetailSheet(title = "文件更改", tag = "step-detail-files-${item.id}", onDismiss = { detailOpen = false }) {
            if (item.fileChanges.isEmpty()) {
                StepDetailSection("详情", item.body.ifBlank { "暂无文件改动" }, mono = true)
            } else {
                item.fileChanges.forEachIndexed { index, change ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    FileChangeBlock(change)
                }
            }
        }
    }
}

@Composable
private fun FileChangeBlock(change: FileChangeSummary) {
    val counts = diffLineCounts(change.diff)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                change.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    change.kind.displayFileChangeKind(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (counts.first > 0 || counts.second > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "+${counts.first}  -${counts.second}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (change.diff.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                change.diff.lineSequence().forEach { line ->
                    val background = when {
                        line.startsWith("+") && !line.startsWith("+++") -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        line.startsWith("-") && !line.startsWith("---") -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        else -> Color.Transparent
                    }
                    Text(
                        line.ifEmpty { " " },
                        modifier = Modifier.widthIn(min = 680.dp).background(background)
                            .padding(horizontal = 12.dp, vertical = 1.dp),
                        style = MonoText,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

internal fun diffLineCounts(diff: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions++
            line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
    }
    return additions to deletions
}

private fun String.displayFileChangeKind(): String = when (lowercase()) {
    "add", "added", "create", "created" -> "新增"
    "delete", "deleted", "remove", "removed" -> "删除"
    "rename", "renamed", "move", "moved" -> "重命名"
    else -> "已修改"
}
