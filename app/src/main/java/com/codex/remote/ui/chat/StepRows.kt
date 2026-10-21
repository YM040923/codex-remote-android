package com.codex.remote.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.remote.ui.theme.CodexGreen
import com.codex.remote.ui.theme.MonoText


// The reference clients show a finished run of work as one bare gray line with the chevron
// hugging the text; the steps stay reachable underneath it rather than as a stack of cards.
@Composable
internal fun StepGroupCard(presentation: TimelinePresentationItem, modifier: Modifier) {
    val steps = presentation.steps
    val status = presentation.item.status
    var expanded by remember(presentation.item.id) { mutableStateOf(status.isTimelineItemRunning()) }
    LaunchedEffect(presentation.item.id, status) {
        expanded = status.isTimelineItemRunning()
    }
    Column(modifier.animateContentSize().testTag("timeline-steps-${presentation.item.id}")) {
        Row(
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { expanded = !expanded }
                .testTag("timeline-steps-header-${presentation.item.id}")
                .padding(horizontal = 2.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val running = status.isTimelineItemRunning()
            Text(
                text = (if (running) "正在处理 " else "已处理 ") + steps.size + " 个步骤",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            if (running) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(5.dp))
            } else {
                timelineStatusLabel(status)?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                }
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "收起处理步骤" else "展开处理步骤",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp)) {
                steps.forEach { step ->
                    TimelineRow(step.item, Modifier.fillMaxWidth().padding(bottom = 6.dp), flat = true)
                }
            }
        }
    }
}

@Composable
internal fun ExpandableTool(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    status: String,
    expansionKey: String,
    mono: Boolean = false,
    flat: Boolean = false,
    detailTitle: String = title,
    commandLine: String? = null,
) {
    var detailOpen by remember(expansionKey) { mutableStateOf(false) }
    Surface(
        modifier = modifier.testTag("timeline-tool-$expansionKey"),
        shape = MaterialTheme.shapes.small,
        // Steps inside a summary fold into a quiet list; a lone step still carries its own surface.
        color = if (flat) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        StepRow(icon, title, status, flat, onClick = { detailOpen = true })
    }
    if (detailOpen) {
        StepDetailSheet(title = detailTitle, tag = "step-detail-$expansionKey", onDismiss = { detailOpen = false }) {
            commandLine?.let { StepDetailSection("命令", it, mono = true, shellHighlight = true) }
            StepDetailSection(
                label = if (mono) "输出" else "详情",
                text = body.ifBlank { "暂无内容" },
                mono = mono,
            )
        }
    }
}

// Measured from the reference step lists: a 20dp icon, the label in body-size secondary ink, and the
// chevron hugging the text instead of chasing the screen edge, which is what makes a run of steps
// read as one quiet column rather than a stack of rows.
@Composable
internal fun StepRow(
    icon: ImageVector,
    title: String,
    status: String,
    flat: Boolean,
    additions: Int = 0,
    deletions: Int = 0,
    onClick: () -> Unit,
) {
    val ink = if (flat) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                start = if (flat) 2.dp else 12.dp,
                end = if (flat) 10.dp else 12.dp,
                top = if (flat) 8.dp else 10.dp,
                bottom = if (flat) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(if (flat) 20.dp else 17.dp),
            tint = ink,
        )
        Spacer(Modifier.width(if (flat) 8.dp else 9.dp))
        Text(
            title,
            // fill = false keeps the cluster together; the weight still caps a long path at the row
            // width, so it ellipsizes instead of pushing the chevron off screen.
            modifier = Modifier.weight(1f, fill = !flat),
            style = if (flat) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (additions > 0) {
            Spacer(Modifier.width(8.dp))
            Text("+$additions", style = MaterialTheme.typography.labelMedium, color = CodexGreen)
        }
        if (deletions > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "-$deletions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        timelineStatusLabel(status)?.let { label ->
            Spacer(Modifier.width(8.dp))
            if (status.isTimelineItemRunning()) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp)
                Spacer(Modifier.width(7.dp))
            }
            // A failed step keeps the same gray as every other status; the word carries the signal.
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "查看详情",
            modifier = Modifier.size(18.dp),
            tint = ink,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepDetailSheet(
    title: String,
    tag: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
            Text(
                title,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd).testTag("$tag-close"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 28.dp)
                .testTag(tag),
        ) {
            content()
        }
    }
}

@Composable
internal fun StepDetailSection(
    label: String,
    text: String,
    mono: Boolean,
    shellHighlight: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.onErrorContainer
    Text(
        label,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val body: @Composable () -> Unit = {
        SelectionContainer {
            Text(
                text = if (shellHighlight) highlightShellTokens(text, accent) else AnnotatedString(text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                style = if (mono) MonoText else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    // Command output is code, so it gets a surface; prose reads fine sitting on the sheet itself.
    if (mono) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            body()
        }
    } else {
        body()
    }
    Spacer(Modifier.height(14.dp))
}

private val ShellTokenPattern = Regex("&&|\\|\\||[-\\w.~]+/[-\\w.~/@]*")

// The reference client tints paths and shell operators in a muted brick so a command line scans as
// code without a highlighter. onErrorContainer is that brick in the light theme and a readable
// salmon in the dark one, so the accent needs no new token and no theme branch.
private fun highlightShellTokens(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    ShellTokenPattern.findAll(text).forEach { match ->
        append(text.substring(lastEnd, match.range.first))
        withStyle(SpanStyle(color = accent)) { append(match.value) }
        lastEnd = match.range.last + 1
    }
    append(text.substring(lastEnd))
}

internal fun String.isTimelineItemRunning(): Boolean =
    equals("inProgress", ignoreCase = true) ||
        equals("in_progress", ignoreCase = true) ||
        equals("running", ignoreCase = true) ||
        equals("started", ignoreCase = true)

// A finished step needs no badge; only in-flight and failed states are worth the user's attention.
private fun timelineStatusLabel(status: String): String? = when {
    status.isBlank() -> null
    status.isTimelineItemRunning() -> "进行中"
    status.equals("completed", ignoreCase = true) || status.equals("success", ignoreCase = true) -> null
    status.equals("failed", ignoreCase = true) || status.equals("error", ignoreCase = true) -> "失败"
    status.equals("cancelled", ignoreCase = true) || status.equals("aborted", ignoreCase = true) -> "已取消"
    else -> status
}
