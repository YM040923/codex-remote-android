package com.codex.remote.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind


internal data class TimelinePresentationItem(
    val item: TimelineItem,
    val sourceItemIds: List<String>,
    // Non-empty when a run of work steps was folded into one summary row; the steps stay reachable.
    val steps: List<TimelinePresentationItem> = emptyList(),
)

private val stepGroupKinds = setOf(
    TimelineKind.REASONING,
    TimelineKind.PLAN,
    TimelineKind.COMMAND,
    TimelineKind.TOOL,
    TimelineKind.FILE_CHANGE,
    TimelineKind.REVIEW,
)

// A run of commands folds into one row that is a container, not a command, so the detail sheet
// must not present its summary label as a shell line.
internal const val MultiCommandTitle = "运行了多个命令"

internal fun groupTimelineSteps(timeline: List<TimelineItem>): List<TimelinePresentationItem> =
    groupStepRuns(foldCommandRuns(timeline))

private fun groupStepRuns(items: List<TimelinePresentationItem>): List<TimelinePresentationItem> = buildList {
    var index = 0
    while (index < items.size) {
        val entry = items[index]
        if (entry.item.kind !in stepGroupKinds) {
            add(entry)
            index++
            continue
        }
        var end = index + 1
        while (end < items.size && items[end].item.kind in stepGroupKinds) end++
        val run = items.subList(index, end)
        val status = when {
            run.any { it.item.status.isTimelineItemRunning() } -> "inProgress"
            run.any { it.item.status.equals("failed", ignoreCase = true) } -> "failed"
            else -> "completed"
        }
        add(
            TimelinePresentationItem(
                item = TimelineItem(
                    id = "steps:${run.first().item.id}",
                    kind = run.first().item.kind,
                    status = status,
                ),
                sourceItemIds = run.flatMap(TimelinePresentationItem::sourceItemIds),
                steps = run.toList(),
            ),
        )
        index = end
    }
}

private fun foldCommandRuns(timeline: List<TimelineItem>): List<TimelinePresentationItem> = buildList {
    var index = 0
    while (index < timeline.size) {
        val item = timeline[index]
        if (item.kind != TimelineKind.COMMAND) {
            add(TimelinePresentationItem(item, listOf(item.id)))
            index++
            continue
        }
        var end = index + 1
        while (end < timeline.size && timeline[end].kind == TimelineKind.COMMAND) end++
        val commands = timeline.subList(index, end)
        if (commands.size == 1) {
            add(TimelinePresentationItem(item, listOf(item.id)))
        } else {
            val status = when {
                commands.any { it.status.isTimelineItemRunning() } -> "inProgress"
                commands.any { it.status.equals("failed", ignoreCase = true) } -> "failed"
                else -> "completed"
            }
            val body = commands.mapIndexed { commandIndex, command ->
                buildString {
                    append(commandIndex + 1)
                    append(". ")
                    append(command.title.ifBlank { "命令" })
                    if (command.body.isNotBlank()) {
                        append('\n')
                        append(command.body.trimEnd())
                    }
                }
            }.joinToString("\n\n")
            add(
                TimelinePresentationItem(
                    item = TimelineItem(
                        id = "command-group:${commands.first().id}",
                        kind = TimelineKind.COMMAND,
                        title = MultiCommandTitle,
                        body = body,
                        status = status,
                    ),
                    sourceItemIds = commands.map(TimelineItem::id),
                ),
            )
        }
        index = end
    }
}
