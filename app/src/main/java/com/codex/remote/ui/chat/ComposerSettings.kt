package com.codex.remote.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.RemoteThreadTokenUsage


private val builtInPermissionProfiles = setOf(":workspace", ":danger-full-access", ":read-only")

internal fun permissionModeFor(
    permissionProfile: String?,
    approvalPolicy: String,
    approvalsReviewer: String,
): PermissionMode? {
    if (permissionProfile != null && permissionProfile !in builtInPermissionProfiles) return null
    return when {
        approvalsReviewer == "auto_review" -> PermissionMode.AUTO_REVIEW
        permissionProfile == ":read-only" -> PermissionMode.READ_ONLY
        permissionProfile == ":danger-full-access" || approvalPolicy == "never" -> PermissionMode.FULL_ACCESS
        else -> PermissionMode.ASK
    }
}

internal fun permissionModeLabel(state: AppUiState): String = when (
    permissionModeFor(state.selectedPermissionProfile, state.approvalPolicy, state.approvalsReviewer)
) {
    PermissionMode.ASK -> "询问"
    PermissionMode.AUTO_REVIEW -> "替我审批"
    PermissionMode.FULL_ACCESS -> "完全访问"
    PermissionMode.READ_ONLY -> "只读"
    null -> state.selectedPermissionProfile?.removePrefix(":") ?: "询问"
}

@Composable
internal fun PermissionDropdown(
    state: AppUiState,
    expanded: Boolean,
    properties: PopupProperties,
    onDismiss: () -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onSetPermissionProfile: (String) -> Unit,
) {
    val selectedMode = permissionModeFor(
        state.selectedPermissionProfile,
        state.approvalPolicy,
        state.approvalsReviewer,
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(310.dp),
        properties = properties,
    ) {
        PermissionDropdownItem(
            title = "询问",
            description = "需要执行命令或修改文件时询问",
            selected = selectedMode == PermissionMode.ASK,
            onClick = { onSetPermissionMode(PermissionMode.ASK) },
        )
        PermissionDropdownItem(
            title = "替我审批",
            description = "由 Codex 自动审查需要批准的操作",
            selected = selectedMode == PermissionMode.AUTO_REVIEW,
            onClick = { onSetPermissionMode(PermissionMode.AUTO_REVIEW) },
        )
        PermissionDropdownItem(
            title = "完全访问",
            description = "无需询问即可访问远端工作区和网络",
            selected = selectedMode == PermissionMode.FULL_ACCESS,
            onClick = { onSetPermissionMode(PermissionMode.FULL_ACCESS) },
        )
        PermissionDropdownItem(
            title = "只读",
            description = "允许读取，但不允许修改远端文件",
            selected = selectedMode == PermissionMode.READ_ONLY,
            onClick = { onSetPermissionMode(PermissionMode.READ_ONLY) },
        )
        val customProfiles = state.permissionProfiles.filter { it.id !in builtInPermissionProfiles }
        if (customProfiles.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            customProfiles.forEach { profile ->
                PermissionDropdownItem(
                    title = profile.id,
                    description = profile.description,
                    selected = selectedMode == null && state.selectedPermissionProfile == profile.id,
                    enabled = profile.allowed,
                    onClick = { onSetPermissionProfile(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun PermissionDropdownItem(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        enabled = enabled,
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
        },
    )
}

@Composable
internal fun ContextUsageRing(
    usage: RemoteThreadTokenUsage?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (usage?.modelContextWindow != null && usage.modelContextWindow > 0) {
        (usage.totalTokens.toFloat() / usage.modelContextWindow.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = (fraction * 100).toInt()
    val track = MaterialTheme.colorScheme.outline
    val progress = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp).semantics {
            contentDescription = if (usage == null) "上下文用量尚不可用" else "上下文已使用 $percent%"
        },
    ) {
        Canvas(Modifier.size(21.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val radius = size.minDimension / 2f - inset
            drawCircle(track, center = Offset(size.width / 2f, size.height / 2f), radius = radius, style = stroke)
            if (fraction > 0f) {
                drawArc(
                    color = progress,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(radius * 2f, radius * 2f),
                    style = stroke,
                )
            }
        }
    }
}

internal enum class ModelSettingsPage { ROOT, MODEL, REASONING, SERVICE_TIER }

@Composable
internal fun ModelSettingsDropdown(
    state: AppUiState,
    expanded: Boolean,
    page: ModelSettingsPage,
    properties: PopupProperties,
    onDismiss: () -> Unit,
    onPageChange: (ModelSettingsPage) -> Unit,
    onSetModel: (String) -> Unit,
    onSetReasoningEffort: (String) -> Unit,
    onSetServiceTier: (String?) -> Unit,
) {
    val selectedModel = state.models.firstOrNull { it.id == state.selectedModel }
    val selectedTier = selectedModel?.serviceTiers?.firstOrNull { it.id == state.selectedServiceTier }
    val fastTier = selectedModel?.serviceTiers?.firstOrNull { tier ->
        tier.id.equals("fast", ignoreCase = true) || tier.name.equals("fast", ignoreCase = true)
    }
    val showFastToggle = fastTier != null && selectedModel.serviceTiers.size == 1

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(300.dp),
        properties = properties,
    ) {
        when (page) {
            ModelSettingsPage.ROOT -> {
                ModelSettingsNavigationRow(
                    label = "模型",
                    value = selectedModel?.displayName ?: "未选择",
                    enabled = state.models.isNotEmpty(),
                    onClick = { onPageChange(ModelSettingsPage.MODEL) },
                )
                ModelSettingsNavigationRow(
                    label = "推理强度",
                    value = state.selectedReasoningEffort?.displayEffort() ?: "默认",
                    enabled = selectedModel?.supportedReasoningEfforts?.isNotEmpty() == true,
                    onClick = { onPageChange(ModelSettingsPage.REASONING) },
                )
                if (selectedModel?.serviceTiers?.isNotEmpty() == true) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    if (showFastToggle) {
                        DropdownMenuItem(
                            text = {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("快速", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        if (state.selectedServiceTier == fastTier.id) "开启" else "关闭",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = state.selectedServiceTier == fastTier.id,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                onSetServiceTier(fastTier.id.takeUnless { it == state.selectedServiceTier })
                            },
                        )
                    } else {
                        ModelSettingsNavigationRow(
                            label = "速度",
                            value = selectedTier?.name?.displayServiceTier() ?: "标准",
                            onClick = { onPageChange(ModelSettingsPage.SERVICE_TIER) },
                        )
                    }
                }
            }
            ModelSettingsPage.MODEL -> {
                ModelSettingsBackRow("模型") { onPageChange(ModelSettingsPage.ROOT) }
                state.models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName, style = MaterialTheme.typography.labelLarge)
                                if (model.description.isNotBlank()) {
                                    Text(
                                        model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetModel(model.id)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (model.id == state.selectedModel) Icon(Icons.Outlined.Check, contentDescription = null)
                        },
                    )
                }
            }
            ModelSettingsPage.REASONING -> {
                ModelSettingsBackRow("推理强度") { onPageChange(ModelSettingsPage.ROOT) }
                selectedModel?.supportedReasoningEfforts.orEmpty().forEach { effort ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(effort.value.displayEffort(), style = MaterialTheme.typography.labelLarge)
                                if (effort.description.isNotBlank()) {
                                    Text(
                                        effort.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetReasoningEffort(effort.value)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (effort.value == state.selectedReasoningEffort) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
            ModelSettingsPage.SERVICE_TIER -> {
                ModelSettingsBackRow("速度") { onPageChange(ModelSettingsPage.ROOT) }
                DropdownMenuItem(
                    text = { Text("标准") },
                    onClick = {
                        onSetServiceTier(null)
                        onPageChange(ModelSettingsPage.ROOT)
                    },
                    trailingIcon = {
                        if (state.selectedServiceTier == null) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                )
                selectedModel?.serviceTiers.orEmpty().forEach { tier ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(tier.name.displayServiceTier(), style = MaterialTheme.typography.labelLarge)
                                if (tier.description.isNotBlank()) {
                                    Text(
                                        tier.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSetServiceTier(tier.id)
                            onPageChange(ModelSettingsPage.ROOT)
                        },
                        trailingIcon = {
                            if (tier.id == state.selectedServiceTier) Icon(Icons.Outlined.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsNavigationRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(
                    value,
                    modifier = Modifier.widthIn(max = 138.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ModelSettingsBackRow(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") },
        onClick = onClick,
    )
    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
}

internal fun modelSettingsSummary(
    modelId: String?,
    effort: String?,
    serviceTier: String?,
): String = listOfNotNull(
    modelId?.removePrefix("gpt-")?.removePrefix("GPT-") ?: "模型",
    effort?.displayEffort(),
    serviceTier?.takeUnless { it.equals("standard", ignoreCase = true) }?.displayServiceTier(),
).joinToString(" · ")

internal fun String.displayServiceTier(): String = when (lowercase()) {
    "fast" -> "快速"
    "standard" -> "标准"
    "priority" -> "优先"
    "flex" -> "弹性"
    else -> this
}
