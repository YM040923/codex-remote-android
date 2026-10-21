package com.codex.remote.ui.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.TimelineKind
import com.codex.remote.ui.theme.CodexSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged


private data class HistoryScrollAnchor(val itemId: String, val itemOffset: Int)

@Composable
internal fun Conversation(
    state: AppUiState,
    listState: LazyListState,
    followLatest: Boolean,
    onFollowLatestChange: (Boolean) -> Unit,
    hasPositionedConversation: Boolean,
    onConversationPositioned: () -> Unit,
    modifier: Modifier,
    onLoadOlderHistory: () -> Unit,
    onResend: (String) -> Unit,
) {
    val conversationKey = state.selectedThreadId ?: "project:${state.selectedProjectPath.orEmpty()}"
    val scrollScope = rememberCoroutineScope()
    val presentationTimeline = remember(state.timeline) { groupTimelineSteps(state.timeline) }
    var pendingHistoryAnchor by remember(conversationKey) { mutableStateOf<HistoryScrollAnchor?>(null) }
    var programmaticScroll by remember(conversationKey) { mutableStateOf(false) }
    val historyHeaderVisible = state.hasOlderHistory ||
        state.isOlderHistoryLoading ||
        state.olderHistoryError != null

    fun visibleHistoryAnchor(): HistoryScrollAnchor? {
        val firstTimelineItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != HISTORY_CONTROL_KEY &&
                item.key != WORKING_ITEM_KEY &&
                item.key != BOTTOM_SENTINEL_KEY
        } ?: return null
        val itemId = firstTimelineItem.key as? String ?: return null
        val sourceItemId = presentationTimeline
            .firstOrNull { it.item.id == itemId }
            ?.sourceItemIds
            ?.lastOrNull()
            ?: itemId
        return HistoryScrollAnchor(sourceItemId, firstTimelineItem.offset)
    }

    if (state.timeline.isEmpty()) {
        val project = state.projects.firstOrNull { it.path == state.selectedProjectPath }
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.isBusy) "正在加载最近消息" else project?.name ?: "没有远端 Codex 历史记录",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    project?.path ?: state.connectionMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasOlderHistory && !state.isBusy) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onLoadOlderHistory) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("加载更早消息")
                    }
                }
            }
        }
        return
    }

    val requestOlderHistory = {
        if (state.hasOlderHistory && !state.isOlderHistoryLoading && pendingHistoryAnchor == null) {
            pendingHistoryAnchor = visibleHistoryAnchor()
            onLoadOlderHistory()
        }
    }

    LaunchedEffect(
        conversationKey,
        presentationTimeline.firstOrNull()?.item?.id,
        presentationTimeline.lastOrNull()?.item?.id,
        hasPositionedConversation,
    ) {
        if (hasPositionedConversation || presentationTimeline.isEmpty()) return@LaunchedEffect
        val leadingItems = if (historyHeaderVisible) 1 else 0
        val workingItems = if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) 1 else 0
        programmaticScroll = true
        try {
            listState.scrollToAbsoluteBottom(leadingItems + presentationTimeline.size + workingItems)
            onFollowLatestChange(true)
            onConversationPositioned()
        } finally {
            programmaticScroll = false
        }
    }
    LaunchedEffect(listState, conversationKey, hasPositionedConversation) {
        if (!hasPositionedConversation) return@LaunchedEffect
        snapshotFlow {
            listState.isScrollInProgress to listState.canScrollForward
        }.distinctUntilChanged().collect { (isScrolling, canScrollForward) ->
            if (!programmaticScroll && isScrolling) onFollowLatestChange(!canScrollForward)
        }
    }
    LaunchedEffect(listState, conversationKey, hasPositionedConversation, followLatest) {
        if (!hasPositionedConversation || !followLatest) return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.canScrollForward,
                listState.isScrollInProgress,
                listState.layoutInfo.totalItemsCount,
            )
        }.distinctUntilChanged().collect { (canScrollForward, isScrolling, totalItemsCount) ->
            if (!canScrollForward || isScrolling || programmaticScroll || totalItemsCount == 0) {
                return@collect
            }
            withFrameNanos { }
            if (!listState.canScrollForward || listState.isScrollInProgress || programmaticScroll) {
                return@collect
            }
            programmaticScroll = true
            try {
                listState.scrollToAbsoluteBottom(totalItemsCount - 1)
            } finally {
                programmaticScroll = false
            }
        }
    }
    LaunchedEffect(
        listState,
        state.selectedThreadId,
        state.hasOlderHistory,
        state.isOlderHistoryLoading,
        pendingHistoryAnchor,
    ) {
        if (!state.hasOlderHistory || state.isOlderHistoryLoading || pendingHistoryAnchor != null) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (firstVisibleIndex, isScrolling) ->
                if (isScrolling && firstVisibleIndex <= HISTORY_PREFETCH_INDEX) requestOlderHistory()
            }
    }
    LaunchedEffect(
        listState,
        state.selectedThreadId,
        state.isOlderHistoryLoading,
        pendingHistoryAnchor != null,
        presentationTimeline.firstOrNull()?.item?.id,
    ) {
        if (!state.isOlderHistoryLoading || pendingHistoryAnchor == null) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key != HISTORY_CONTROL_KEY &&
                    item.key != WORKING_ITEM_KEY &&
                    item.key != BOTTOM_SENTINEL_KEY
            }?.let { item -> item.key to item.offset }
        }.distinctUntilChanged().collect {
            visibleHistoryAnchor()?.let { anchor -> pendingHistoryAnchor = anchor }
        }
    }
    LaunchedEffect(
        state.selectedThreadId,
        state.isOlderHistoryLoading,
        state.olderHistoryError,
        presentationTimeline.firstOrNull()?.item?.id,
    ) {
        val anchor = pendingHistoryAnchor ?: return@LaunchedEffect
        if (state.isOlderHistoryLoading) return@LaunchedEffect
        val timelineIndex = presentationTimeline.indexOfFirst { anchor.itemId in it.sourceItemIds }
        if (timelineIndex >= 0) {
            val leadingItems = if (historyHeaderVisible) 1 else 0
            programmaticScroll = true
            try {
                listState.scrollToItem(timelineIndex + leadingItems)
                withFrameNanos { }
                listState.scrollBy(-anchor.itemOffset.toFloat())
            } finally {
                programmaticScroll = false
            }
        }
        pendingHistoryAnchor = null
    }
    val conversationBottomPadding = with(LocalDensity.current) { CodexSpacing.large.toPx() }
    LaunchedEffect(
        state.selectedThreadId,
        presentationTimeline.size,
        presentationTimeline.lastOrNull()?.item?.body?.length,
        state.isTurnRunning,
        historyHeaderVisible,
        pendingHistoryAnchor,
        followLatest,
        hasPositionedConversation,
    ) {
        if (!hasPositionedConversation || presentationTimeline.isEmpty() || !followLatest ||
            state.isOlderHistoryLoading || pendingHistoryAnchor != null
        ) {
            return@LaunchedEffect
        }
        val leadingItems = if (historyHeaderVisible) 1 else 0
        val workingItems = if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) 1 else 0
        programmaticScroll = true
        try {
            listState.followBottomGrowth(
                conversationBottomPadding,
                leadingItems + presentationTimeline.size + workingItems,
            )
        } finally {
            programmaticScroll = false
        }
    }

    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("conversation-list"),
            state = listState,
            contentPadding = PaddingValues(horizontal = ConversationGutter, vertical = CodexSpacing.large),
        ) {
        if (historyHeaderVisible) {
            item(key = HISTORY_CONTROL_KEY) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("history-control"),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.isOlderHistoryLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        state.olderHistoryError != null && state.hasOlderHistory -> TextButton(onClick = requestOlderHistory) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("重试加载更早消息", maxLines = 1)
                        }
                        state.olderHistoryError != null -> Text(
                            "无法继续加载更早消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.hasOlderHistory -> TextButton(onClick = requestOlderHistory) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("加载更早消息")
                        }
                    }
                }
            }
        }
            items(presentationTimeline, key = { it.item.id }) { presentation ->
                val steps = presentation.steps
                val itemModifier = Modifier.fillMaxWidth().widthIn(max = ConversationMaxWidth)
                // A reply can only be re-asked with the prompt that produced it, and the timeline is the
                // only place that still pairs the two.
                val prompt = if (steps.isEmpty() && presentation.item.kind == TimelineKind.AGENT) {
                    presentationTimeline.takeWhile { it.item.id != presentation.item.id }
                        .lastOrNull { it.item.kind == TimelineKind.USER }?.item?.body
                } else {
                    null
                }
                Box(
                    Modifier.fillMaxWidth()
                        .padding(bottom = if (steps.isEmpty()) timelineItemSpacing(presentation.item.kind) else 20.dp)
                        .testTag("timeline-item-${presentation.item.id}"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when {
                        steps.isNotEmpty() -> StepGroupCard(presentation, itemModifier)
                        prompt != null && presentation.item.body.isNotBlank() && !state.isTurnRunning ->
                            Column(itemModifier) {
                                TimelineRow(presentation.item, Modifier.fillMaxWidth())
                                ReplyActions(
                                    body = presentation.item.body,
                                    prompt = prompt,
                                    onResend = onResend,
                                    modifier = Modifier.testTag("reply-actions-${presentation.item.id}"),
                                )
                            }
                        else -> TimelineRow(presentation.item, itemModifier)
                    }
                }
            }
            if (state.isTurnRunning && presentationTimeline.lastOrNull()?.item?.kind != TimelineKind.AGENT) {
                item(key = WORKING_ITEM_KEY) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = ConversationMaxWidth).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("正在处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = BOTTOM_SENTINEL_KEY) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("conversation-bottom"))
            }
        }
        if (!followLatest) {
            SmallFloatingActionButton(
                onClick = {
                    scrollScope.launch {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        if (lastIndex >= 0) {
                            programmaticScroll = true
                            try {
                                listState.scrollToAbsoluteBottom(lastIndex, animate = true)
                                onFollowLatestChange(true)
                            } finally {
                                programmaticScroll = false
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .testTag("conversation-bottom-button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "回到最新消息")
            }
        }
    }
}

private const val HISTORY_CONTROL_KEY = "history-control"

private const val WORKING_ITEM_KEY = "working"

private const val BOTTOM_SENTINEL_KEY = "bottom-sentinel"

private const val HISTORY_PREFETCH_INDEX = 2

internal val ConversationMaxWidth = 820.dp

// Both reference chat clients indent the reading column by ~7% of screen width; 16dp made the
// conversation look like a settings list rather than a document.
private val ConversationGutter = 28.dp

// Turn boundaries need more air than the steps inside a turn; both steps sit on the 4pt grid.
private fun timelineItemSpacing(kind: TimelineKind): Dp = when (kind) {
    TimelineKind.USER, TimelineKind.AGENT -> 20.dp
    TimelineKind.COMPACTION, TimelineKind.ERROR -> 12.dp
    else -> 8.dp
}

private suspend fun LazyListState.scrollToAbsoluteBottom(lastIndex: Int, animate: Boolean = false) {
    if (lastIndex < 0) return
    if (animate) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    var attempts = 0
    var settledFrames = 0
    while (attempts++ < MAX_BOTTOM_SCROLL_STEPS) {
        withFrameNanos { }
        if (!canScrollForward) {
            settledFrames++
            if (settledFrames >= BOTTOM_LAYOUT_SETTLE_FRAMES) return
            continue
        }
        settledFrames = 0
        val layout = layoutInfo
        val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
        scrollBy(viewportHeight.toFloat())
    }
}

private const val MAX_BOTTOM_SCROLL_STEPS = 1_000

private const val BOTTOM_LAYOUT_SETTLE_FRAMES = 8

// A streaming reply re-measures every frame. Re-pinning by index relayouts the whole list and reads
// as a jump, so nudge by the exact overflow and only fall back when a new item pushed the end away.
private suspend fun LazyListState.followBottomGrowth(bottomPadding: Float, lastIndex: Int) {
    withFrameNanos { }
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val overflow = (last.offset + last.size) - (layoutInfo.viewportEndOffset - bottomPadding)
    if (overflow > 0f) scrollBy(overflow)
    if (canScrollForward) scrollToAbsoluteBottom(lastIndex)
}
