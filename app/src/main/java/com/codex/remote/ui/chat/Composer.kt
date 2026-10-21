package com.codex.remote.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.ComposerMentionKind
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.ComposerTriggerKind
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.composerToken
import com.codex.remote.domain.findComposerTrigger
import com.codex.remote.domain.replaceComposerTrigger
import com.codex.remote.ui.theme.CodexSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
internal fun Composer(
    state: AppUiState,
    onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit,
    onStop: () -> Unit,
    onCompactThread: () -> Unit,
    onForkThread: () -> Unit,
    onOpenReview: () -> Unit,
    onRunInit: () -> Unit,
    onOpenMcpStatus: () -> Unit,
    onOpenFeedback: () -> Unit,
    onEditGoal: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    var text by remember(state.selectedThreadId) { mutableStateOf(TextFieldValue()) }
    var selectedMentions by remember(state.selectedThreadId) { mutableStateOf(emptyList<ComposerMention>()) }
    var attachments by remember(state.selectedThreadId) { mutableStateOf(emptyList<ComposerImageAttachment>()) }
    var attachmentError by remember(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var mentionKindFilter by remember(state.selectedThreadId) { mutableStateOf<ComposerMentionKind?>(null) }
    var modelSettingsMenu by remember(state.selectedThreadId) { mutableStateOf(false) }
    var modelSettingsPage by remember(state.selectedThreadId) { mutableStateOf(ModelSettingsPage.ROOT) }
    var addMenuOpen by remember(state.selectedThreadId) { mutableStateOf(false) }
    var showRemotePathPicker by remember(state.selectedThreadId) { mutableStateOf(false) }
    var goalModeActive by remember(state.selectedThreadId) { mutableStateOf(false) }
    var policyMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val composerScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        composerScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readComposerImageAttachment(context, uri) } }
            result.onSuccess { attachment ->
                attachments = (attachments + attachment).takeLast(MAX_COMPOSER_IMAGES)
                attachmentError = null
            }.onFailure { error ->
                attachmentError = error.message ?: "无法附加图片"
            }
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val composerMenuProperties = remember { PopupProperties(focusable = false) }
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModel }
    val selectedServiceTier = selectedModel?.serviceTiers?.firstOrNull { it.id == state.selectedServiceTier }
    val supportsImages = selectedModel?.inputModalities?.any { it.equals("image", true) } == true
    val selectedThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
    val cwd = selectedThread?.cwd?.takeIf(String::isNotBlank) ?: state.selectedProjectPath.orEmpty()
    val addMenuSkills = state.skills.filter { it.enabled && (it.cwds.isEmpty() || cwd in it.cwds) }
    val addMenuPlugins = state.plugins.filter { it.enabled }
    val planModeAvailable = state.collaborationModes.any { it.mode == "plan" }
    val trigger = findComposerTrigger(text.text, text.selection.start)
    val inlineGoalObjective = extractGoalSlashObjective(text.text)?.takeIf(String::isNotBlank)
    val allSlashCommands = (
        desktopSlashCommands(hasThread = state.selectedThreadId != null) +
            selectedModel?.serviceTiers.orEmpty().map { tier ->
                SlashCommand(
                    name = tier.id.lowercase(),
                    description = tier.description.ifBlank { "使用 ${tier.name.displayServiceTier()} 服务等级" },
                    icon = Icons.Outlined.SmartToy,
                    action = SlashCommandAction.SERVICE_TIER,
                    value = tier.id,
                )
            }
        ).distinctBy { it.name }
    val slashCommands = allSlashCommands.filter { command ->
        trigger?.kind == ComposerTriggerKind.SLASH_COMMAND &&
            (trigger.query.isBlank() || command.name.contains(trigger.query, ignoreCase = true) ||
                command.description.contains(trigger.query, ignoreCase = true))
    }
    val mentionOptions = if (trigger?.kind == ComposerTriggerKind.MENTION) {
        val query = trigger.query.trim()
        buildList {
            if (mentionKindFilter != ComposerMentionKind.PLUGIN) {
                state.skills.asSequence()
                    .filter { it.enabled && (it.cwds.isEmpty() || cwd in it.cwds) }
                    .filter {
                        query.isEmpty() || it.name.contains(query, true) ||
                            it.displayName.contains(query, true) || it.description.contains(query, true)
                    }
                    .forEach { skill ->
                        add(
                            ComposerMentionOption(
                                kind = ComposerMentionKind.SKILL,
                                title = skill.displayName,
                                name = skill.name,
                                description = skill.description,
                                path = skill.path,
                                token = skill.composerToken(),
                            ),
                        )
                    }
            }
            if (mentionKindFilter != ComposerMentionKind.SKILL) {
                state.plugins.asSequence()
                    .filter { it.enabled }
                    .filter {
                        query.isEmpty() || it.name.contains(query, true) ||
                            it.displayName.contains(query, true) || it.description.contains(query, true)
                    }
                    .forEach { plugin ->
                        add(
                            ComposerMentionOption(
                                kind = ComposerMentionKind.PLUGIN,
                                title = plugin.displayName,
                                name = plugin.displayName,
                                description = plugin.description.ifBlank { plugin.marketplace },
                                path = plugin.mentionPath,
                                token = plugin.composerToken(),
                            ),
                        )
                    }
            }
        }
    } else {
        emptyList()
    }

    fun replaceTrigger(replacement: String) {
        val activeTrigger = trigger ?: return
        val update = replaceComposerTrigger(text.text, activeTrigger, replacement)
        text = TextFieldValue(update.text, TextRange(update.cursor))
    }

    fun insertAtCursor(value: String) {
        val start = minOf(text.selection.start, text.selection.end).coerceIn(0, text.text.length)
        val end = maxOf(text.selection.start, text.selection.end).coerceIn(start, text.text.length)
        val updated = text.text.replaceRange(start, end, value)
        text = TextFieldValue(updated, TextRange(start + value.length))
    }

    fun insertMention(option: ComposerMentionOption) {
        insertAtCursor("${option.token} ")
        selectedMentions = (selectedMentions + ComposerMention(
            kind = option.kind,
            name = option.name,
            path = option.path,
            token = option.token,
        )).distinctBy { "${it.kind}:${it.path}" }
    }

    fun activateGoalMode() {
        goalModeActive = true
        if (state.selectedCollaborationMode == "plan") onSetCollaborationMode("default")
    }

    fun executeSlashCommand(command: SlashCommand) {
        when (command.action) {
            SlashCommandAction.MODEL -> {
                replaceTrigger("")
                modelSettingsPage = ModelSettingsPage.MODEL
                modelSettingsMenu = true
            }
            SlashCommandAction.REASONING -> {
                replaceTrigger("")
                modelSettingsPage = ModelSettingsPage.REASONING
                modelSettingsMenu = true
            }
            SlashCommandAction.SERVICE_TIER -> {
                replaceTrigger("")
                onSetServiceTier(command.value.takeUnless { it == state.selectedServiceTier })
            }
            SlashCommandAction.PLAN_MODE -> {
                replaceTrigger("")
                goalModeActive = false
                onSetCollaborationMode("plan")
            }
            SlashCommandAction.PERMISSIONS -> {
                replaceTrigger("")
                policyMenu = true
            }
            SlashCommandAction.SKILLS -> {
                replaceTrigger("\$")
                mentionKindFilter = ComposerMentionKind.SKILL
            }
            SlashCommandAction.PLUGINS -> {
                replaceTrigger("\$")
                mentionKindFilter = ComposerMentionKind.PLUGIN
            }
            SlashCommandAction.RENAME -> {
                replaceTrigger("")
                onRenameCurrentThread()
            }
            SlashCommandAction.ARCHIVE -> {
                replaceTrigger("")
                onArchiveCurrentThread()
            }
            SlashCommandAction.COMPACT -> {
                replaceTrigger("")
                onCompactThread()
            }
            SlashCommandAction.FORK -> {
                replaceTrigger("")
                onForkThread()
            }
            SlashCommandAction.REVIEW -> {
                replaceTrigger("")
                onOpenReview()
            }
            SlashCommandAction.INIT -> {
                replaceTrigger("")
                onRunInit()
            }
            SlashCommandAction.MCP -> {
                replaceTrigger("")
                onOpenMcpStatus()
            }
            SlashCommandAction.FEEDBACK -> {
                replaceTrigger("")
                onOpenFeedback()
            }
            SlashCommandAction.GOAL -> {
                replaceTrigger("")
                activateGoalMode()
            }
            SlashCommandAction.NEW_TASK -> {
                replaceTrigger("")
                onNewThread()
            }
            SlashCommandAction.STATUS -> {
                replaceTrigger("")
                onShowStatus()
            }
        }
    }

    fun performComposerAction() {
        when {
            state.isTurnRunning -> onStop()
            state.canResumeTurn -> {
                val submittedText = text.text.takeIf(String::isNotBlank)
                    ?: "请从上次中断的位置继续完成之前的任务。"
                val submittedMentions = selectedMentions
                val submittedAttachments = attachments
                text = TextFieldValue()
                selectedMentions = emptyList()
                attachments = emptyList()
                attachmentError = null
                mentionKindFilter = null
                goalModeActive = false
                onSend(
                    submittedText,
                    submittedMentions,
                    submittedAttachments,
                    false,
                )
            }
            text.text.isNotBlank() || attachments.isNotEmpty() -> {
                val exactCommand = text.text.trim().takeIf { attachments.isEmpty() }
                    ?.takeIf { it.startsWith('/') && it.drop(1).none(Char::isWhitespace) }
                    ?.drop(1)
                    ?.let { name -> allSlashCommands.firstOrNull { it.name.equals(name, true) } }
                if (exactCommand != null) {
                    executeSlashCommand(exactCommand)
                    text = TextFieldValue()
                } else {
                    val submittedText = inlineGoalObjective ?: text.text
                    val submittedMentions = selectedMentions
                    val submittedAttachments = attachments
                    val submitAsGoal = goalModeActive || inlineGoalObjective != null
                    text = TextFieldValue()
                    selectedMentions = emptyList()
                    attachments = emptyList()
                    attachmentError = null
                    mentionKindFilter = null
                    goalModeActive = false
                    onSend(
                        submittedText,
                        submittedMentions,
                        submittedAttachments,
                        submitAsGoal,
                    )
                }
            }
        }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (trigger != null) {
                ComposerSuggestionPopup(
                    triggerKind = trigger.kind,
                    slashCommands = slashCommands,
                    mentionOptions = mentionOptions,
                    loading = state.isComposerCatalogLoading,
                    error = state.composerCatalogError,
                    mentionKindFilter = mentionKindFilter,
                    onSlashCommand = ::executeSlashCommand,
                    onMention = { option ->
                        replaceTrigger("${option.token} ")
                        selectedMentions = (selectedMentions + ComposerMention(
                            kind = option.kind,
                            name = option.name,
                            path = option.path,
                            token = option.token,
                        )).distinctBy { "${it.kind}:${it.path}" }
                        mentionKindFilter = null
                    },
                )
            }
            state.threadGoal?.let { goal ->
                GoalBar(
                    goal = goal,
                    loading = state.isGoalLoading,
                    onEdit = onEditGoal,
                    onSetStatus = onSetGoalStatus,
                    onClear = onClearGoal,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = ConversationMaxWidth)
                    .padding(horizontal = CodexSpacing.large, vertical = CodexSpacing.small),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = CodexSpacing.large, vertical = CodexSpacing.small)) {
                    if (attachments.isNotEmpty()) {
                        ComposerAttachments(
                            attachments = attachments,
                            onRemove = { id -> attachments = attachments.filterNot { it.id == id } },
                        )
                    }
                    attachmentError?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(bottom = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // The chip marks the message being composed, so it belongs on the text's own line:
                    // the toolbar row below already overflows a phone width with add, permission, model
                    // and send, and the chip scrolled out of sight there.
                    if (goalModeActive) {
                        GoalModeIndicator(
                            onClear = { goalModeActive = false },
                            modifier = Modifier.padding(bottom = 4.dp).testTag("composer-goal-marker"),
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { updated ->
                            val previousTrigger = findComposerTrigger(text.text, text.selection.start)
                            val nextTrigger = findComposerTrigger(updated.text, updated.selection.start)
                            text = updated
                            selectedMentions = selectedMentions.filter { updated.text.contains(it.token) }
                            if (nextTrigger?.kind != ComposerTriggerKind.MENTION ||
                                previousTrigger?.kind != ComposerTriggerKind.MENTION
                            ) {
                                mentionKindFilter = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("composer-input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 7,
                        enabled = true,
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                if (text.text.isEmpty()) {
                                    Text(
                                        if (goalModeActive) {
                                            "描述你的目标，最好包含可衡量的结果"
                                        } else {
                                            "询问 Codex"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left group scrolls; the right group (model + send) stays pinned.
                        Row(
                            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ComposerAddMenu(
                                modifier = Modifier.testTag("composer-add"),
                                expanded = addMenuOpen,
                                properties = composerMenuProperties,
                                skills = addMenuSkills.map { skill ->
                                    ComposerMentionOption(
                                        kind = ComposerMentionKind.SKILL,
                                        title = skill.displayName,
                                        name = skill.name,
                                        description = skill.description,
                                        path = skill.path,
                                        token = skill.composerToken(),
                                    )
                                },
                                plugins = addMenuPlugins.map { plugin ->
                                    ComposerMentionOption(
                                        kind = ComposerMentionKind.PLUGIN,
                                        title = plugin.displayName,
                                        name = plugin.displayName,
                                        description = plugin.description.ifBlank { plugin.marketplace },
                                        path = plugin.mentionPath,
                                        token = plugin.composerToken(),
                                    )
                                },
                                canBrowseRemoteFiles = cwd.isNotBlank(),
                                canAttachImage = supportsImages && attachments.size < MAX_COMPOSER_IMAGES,
                                planModeAvailable = planModeAvailable,
                                onOpen = { addMenuOpen = true },
                                onDismiss = { addMenuOpen = false },
                                onBrowseRemoteFiles = {
                                    addMenuOpen = false
                                    showRemotePathPicker = true
                                    onLoadRemoteDirectory(cwd)
                                },
                                onAttachImage = {
                                    addMenuOpen = false
                                    imagePicker.launch("image/*")
                                },
                                onOpenGoal = {
                                    addMenuOpen = false
                                    activateGoalMode()
                                },
                                onPlanMode = {
                                    addMenuOpen = false
                                    goalModeActive = false
                                    onSetCollaborationMode("plan")
                                },
                                onMention = { option ->
                                    addMenuOpen = false
                                    insertMention(option)
                                },
                            )
                            ComposerPermissionControl(
                                state = state,
                                expanded = policyMenu,
                                properties = composerMenuProperties,
                                onOpen = { policyMenu = true },
                                onDismiss = { policyMenu = false },
                                onSetPermissionMode = onSetPermissionMode,
                                onSetPermissionProfile = onSetPermissionProfile,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        if (isKeyboardVisible) {
                            IconButton(
                                onClick = { keyboardController?.hide() },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardHide,
                                    contentDescription = "隐藏键盘",
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        ContextUsageRing(
                            usage = state.threadTokenUsage,
                            onClick = onShowStatus,
                            modifier = Modifier.testTag("composer-context"),
                        )
                        Spacer(Modifier.width(2.dp))
                        ComposerModelControl(
                            state = state,
                            label = modelSettingsSummary(
                                modelId = selectedModel?.id,
                                effort = state.selectedReasoningEffort,
                                serviceTier = selectedServiceTier?.name,
                            ),
                            expanded = modelSettingsMenu,
                            page = modelSettingsPage,
                            properties = composerMenuProperties,
                            onOpen = {
                                modelSettingsPage = ModelSettingsPage.ROOT
                                modelSettingsMenu = true
                            },
                            onDismiss = {
                                modelSettingsMenu = false
                                modelSettingsPage = ModelSettingsPage.ROOT
                            },
                            onPageChange = { modelSettingsPage = it },
                            onSetModel = onSetModel,
                            onSetReasoningEffort = onSetReasoningEffort,
                            onSetServiceTier = onSetServiceTier,
                        )
                        val composerActionEnabled = when {
                            state.isTurnRunning -> state.activeTurnId != null && !state.isStoppingTurn
                            state.canResumeTurn -> !state.isStoppingTurn
                            goalModeActive || inlineGoalObjective != null ->
                                text.text.isNotBlank() && !state.isGoalLoading
                            else -> text.text.isNotBlank() || attachments.isNotEmpty()
                        }
                        val composerActionActive = composerActionEnabled ||
                            state.isTurnRunning || state.canResumeTurn || state.isStoppingTurn
                        ComposerSendButton(
                            enabled = composerActionEnabled,
                            active = composerActionActive,
                            stopping = state.isStoppingTurn,
                            running = state.isTurnRunning,
                            canResume = state.canResumeTurn,
                            onAction = ::performComposerAction,
                        )
                    }
                }
            }
        }
    }
    if (showRemotePathPicker) {
        RemotePathPickerDialog(
            state = state,
            projectRoot = cwd,
            onNavigate = onLoadRemoteDirectory,
            onSelect = { path ->
                insertAtCursor("${remotePathComposerToken(cwd, path)} ")
                showRemotePathPicker = false
                onClearRemoteDirectory()
            },
            onDismiss = {
                showRemotePathPicker = false
                onClearRemoteDirectory()
            },
        )
    }
}

@Composable
private fun ComposerAttachments(
    attachments: List<ComposerImageAttachment>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { attachment ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.height(30.dp).padding(start = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        attachment.displayName,
                        modifier = Modifier.widthIn(max = 140.dp),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onRemove(attachment.id) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "移除图片", modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSendButton(
    enabled: Boolean,
    active: Boolean,
    stopping: Boolean,
    running: Boolean,
    canResume: Boolean,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        IconButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.testTag("composer-send"),
        ) {
            when {
                stopping -> CircularProgressIndicator(
                    Modifier.size(18.dp).semantics {
                        contentDescription = "正在暂停任务"
                    },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                running -> Icon(
                    Icons.Filled.Stop,
                    contentDescription = "暂停任务",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                canResume -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "继续任务",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                else -> Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "发送",
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ComposerPermissionControl(
    state: AppUiState,
    expanded: Boolean,
    properties: PopupProperties,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onSetPermissionProfile: (String?) -> Unit,
) {
    Box {
        CompactComposerMenuButton(
            modifier = Modifier.testTag("composer-permissions"),
            label = permissionModeLabel(state),
            maxLabelWidth = 88.dp,
            leadingIcon = Icons.Outlined.Tune,
            showChevron = false,
            onClick = onOpen,
        )
        PermissionDropdown(
            state = state,
            expanded = expanded,
            properties = properties,
            onDismiss = onDismiss,
            onSetPermissionMode = {
                onSetPermissionMode(it)
                onDismiss()
            },
            onSetPermissionProfile = {
                onSetPermissionProfile(it)
                onDismiss()
            },
        )
    }
}

@Composable
private fun ComposerModelControl(
    state: AppUiState,
    label: String,
    expanded: Boolean,
    page: ModelSettingsPage,
    properties: PopupProperties,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPageChange: (ModelSettingsPage) -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
) {
    Box {
        CompactComposerMenuButton(
            modifier = Modifier.testTag("composer-model"),
            label = label,
            maxLabelWidth = 144.dp,
            enabled = state.models.isNotEmpty(),
            onClick = onOpen,
        )
        ModelSettingsDropdown(
            state = state,
            expanded = expanded,
            page = page,
            properties = properties,
            onDismiss = onDismiss,
            onPageChange = onPageChange,
            onSetModel = onSetModel,
            onSetReasoningEffort = onSetReasoningEffort,
            onSetServiceTier = onSetServiceTier,
        )
    }
}

@Composable
private fun CompactComposerMenuButton(
    label: String,
    maxLabelWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier.height(32.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            modifier = Modifier.widthIn(max = maxLabelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) {
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun GoalModeIndicator(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(32.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClear)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = "取消目标标记",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "目标",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun readComposerImageAttachment(context: Context, uri: Uri): ComposerImageAttachment {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
        ?: throw IllegalArgumentException("仅支持添加图片附件")
    var displayName = "image"
    var declaredSize: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: displayName
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
    }
    if (declaredSize != null && declaredSize!! > MAX_COMPOSER_IMAGE_BYTES) {
        throw IllegalArgumentException("图片大小不能超过 20 MB")
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取所选图片")
    if (bytes.size > MAX_COMPOSER_IMAGE_BYTES) throw IllegalArgumentException("图片大小不能超过 20 MB")
    return ComposerImageAttachment(
        displayName = displayName,
        mimeType = mimeType,
        dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
    )
}

private const val MAX_COMPOSER_IMAGES = 4

private const val MAX_COMPOSER_IMAGE_BYTES = 20 * 1024 * 1024
