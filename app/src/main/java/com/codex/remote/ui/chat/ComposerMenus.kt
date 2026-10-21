package com.codex.remote.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerTriggerKind


@Composable
internal fun ComposerAddMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    properties: PopupProperties,
    skills: List<ComposerMentionOption>,
    plugins: List<ComposerMentionOption>,
    canBrowseRemoteFiles: Boolean,
    canAttachImage: Boolean,
    planModeAvailable: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onBrowseRemoteFiles: () -> Unit,
    onAttachImage: () -> Unit,
    onOpenGoal: () -> Unit,
    onPlanMode: () -> Unit,
    onMention: (ComposerMentionOption) -> Unit,
) {
    Box {
        IconButton(onClick = onOpen, modifier = modifier.size(32.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(330.dp).heightIn(max = 440.dp),
            properties = properties,
        ) {
            Text(
                "添加",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddMenuItem(
                icon = Icons.Outlined.FolderOpen,
                title = "文件和文件夹",
                description = "选择远端项目中的路径",
                enabled = canBrowseRemoteFiles,
                onClick = onBrowseRemoteFiles,
            )
            AddMenuItem(
                icon = Icons.Outlined.Image,
                title = "图片",
                description = "从 Android 设备添加图片",
                enabled = canAttachImage,
                onClick = onAttachImage,
            )
            AddMenuItem(
                icon = Icons.Outlined.Flag,
                title = "目标",
                description = "设置要持续追求的目标",
                onClick = onOpenGoal,
            )
            AddMenuItem(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "计划模式",
                description = "开启远端计划模式",
                enabled = planModeAvailable,
                onClick = onPlanMode,
            )
            if (skills.isNotEmpty() || plugins.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "技能和插件",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                skills.forEach { option ->
                    AddMenuItem(
                        icon = Icons.Outlined.Psychology,
                        title = option.title,
                        description = option.description,
                        onClick = { onMention(option) },
                    )
                }
                plugins.forEach { option ->
                    AddMenuItem(
                        icon = Icons.Outlined.Extension,
                        title = option.title,
                        description = option.description,
                        onClick = { onMention(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}

internal fun extractGoalSlashObjective(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/goal", ignoreCase = true)) return null
    val suffix = trimmed.drop(5)
    if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) return null
    return suffix.trim()
}

internal enum class SlashCommandAction {
    MODEL, REASONING, SERVICE_TIER, PLAN_MODE, PERMISSIONS, SKILLS, PLUGINS, RENAME, ARCHIVE, COMPACT,
    FORK, REVIEW, INIT, MCP, FEEDBACK,
    GOAL, NEW_TASK, STATUS,
}

internal data class SlashCommand(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val action: SlashCommandAction,
    val value: String? = null,
)

internal data class ComposerMentionOption(
    val kind: ComposerMentionKind,
    val title: String,
    val name: String,
    val description: String,
    val path: String,
    val token: String,
)

internal fun desktopSlashCommands(hasThread: Boolean): List<SlashCommand> = buildList {
    add(SlashCommand("model", "选择此任务使用的模型", Icons.Outlined.SmartToy, SlashCommandAction.MODEL))
    add(SlashCommand("reasoning", "设置推理强度", Icons.Outlined.Psychology, SlashCommandAction.REASONING))
    add(SlashCommand("plan", "切换到计划模式", Icons.Outlined.Flag, SlashCommandAction.PLAN_MODE))
    add(SlashCommand("permissions", "更改操作审批方式", Icons.Outlined.Tune, SlashCommandAction.PERMISSIONS))
    add(SlashCommand("skills", "引用远端技能", Icons.Outlined.Psychology, SlashCommandAction.SKILLS))
    add(SlashCommand("plugins", "引用已安装的远端插件", Icons.Outlined.Extension, SlashCommandAction.PLUGINS))
    add(SlashCommand("goal", "将下一条消息设为此任务的目标", Icons.Outlined.Flag, SlashCommandAction.GOAL))
    add(SlashCommand("init", "为此项目创建 AGENTS.md 指引", Icons.Outlined.Description, SlashCommandAction.INIT))
    add(SlashCommand("mcp", "查看远端 MCP 服务状态", Icons.Outlined.Hub, SlashCommandAction.MCP))
    add(SlashCommand("feedback", "发送此任务的反馈", Icons.Outlined.ErrorOutline, SlashCommandAction.FEEDBACK))
    add(SlashCommand("compact", "压缩此任务的上下文", Icons.Outlined.Description, SlashCommandAction.COMPACT))
    if (hasThread) {
        add(SlashCommand("rename", "重命名当前任务", Icons.Outlined.Edit, SlashCommandAction.RENAME))
        add(SlashCommand("archive", "归档当前任务", Icons.Outlined.Archive, SlashCommandAction.ARCHIVE))
        add(SlashCommand("fork", "在新任务中继续", Icons.AutoMirrored.Outlined.CallSplit, SlashCommandAction.FORK))
        add(SlashCommand("review-mode", "审查未提交的更改或其他目标", Icons.Outlined.Code, SlashCommandAction.REVIEW))
    }
    add(SlashCommand("new", "在此项目中开始新任务", Icons.Outlined.Add, SlashCommandAction.NEW_TASK))
    add(SlashCommand("status", "查看远端 Codex 连接状态", Icons.Outlined.Computer, SlashCommandAction.STATUS))
}

@Composable
internal fun ComposerSuggestionPopup(
    triggerKind: ComposerTriggerKind,
    slashCommands: List<SlashCommand>,
    mentionOptions: List<ComposerMentionOption>,
    loading: Boolean,
    error: String?,
    mentionKindFilter: ComposerMentionKind?,
    onSlashCommand: (SlashCommand) -> Unit,
    onMention: (ComposerMentionOption) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 836.dp).padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 5.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            if (triggerKind == ComposerTriggerKind.SLASH_COMMAND) {
                if (slashCommands.isEmpty()) item { ComposerPopupMessage("未找到命令") }
                items(slashCommands, key = { "slash-${it.name}" }) { command ->
                    ComposerPopupRow(
                        icon = command.icon,
                        title = "/${command.name}",
                        description = command.description,
                        onClick = { onSlashCommand(command) },
                    )
                }
            } else {
                item {
                    Text(
                        when (mentionKindFilter) {
                            ComposerMentionKind.SKILL -> "技能"
                            ComposerMentionKind.PLUGIN -> "插件"
                            null -> "技能和插件"
                        },
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(mentionOptions, key = { "mention-${it.kind}-${it.path}" }) { option ->
                    val detail = option.description.takeIf(String::isNotBlank)
                        ?.let { "${option.token} · $it" } ?: option.token
                    ComposerPopupRow(
                        icon = if (option.kind == ComposerMentionKind.SKILL) Icons.Outlined.Psychology else Icons.Outlined.Extension,
                        title = option.title,
                        description = detail,
                        onClick = { onMention(option) },
                    )
                }
                if (mentionOptions.isEmpty()) {
                    item {
                        ComposerPopupMessage(
                            when {
                                loading -> "正在从远端 Codex 加载…"
                                !error.isNullOrBlank() -> error
                                else -> "未找到匹配的远端技能或已安装插件"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPopupRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComposerPopupMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
