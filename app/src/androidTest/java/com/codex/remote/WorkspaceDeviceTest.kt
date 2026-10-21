package com.codex.remote

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.remote.data.rpc.CodexRpcClient
import com.codex.remote.data.store.ConnectionStore
import com.codex.remote.domain.AppUiState
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ComposerImageAttachment
import com.codex.remote.domain.ComposerMention
import com.codex.remote.domain.FileChangeSummary
import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.ReasoningEffortOption
import com.codex.remote.domain.RemoteAccount
import com.codex.remote.domain.RemoteCollaborationMode
import com.codex.remote.domain.RemoteModel
import com.codex.remote.domain.RemotePlugin
import com.codex.remote.domain.RemoteProject
import com.codex.remote.domain.RemoteServiceTier
import com.codex.remote.domain.RemoteSkill
import com.codex.remote.domain.RemoteThread
import com.codex.remote.domain.RemoteThreadTokenUsage
import com.codex.remote.domain.ReviewTargetKind
import com.codex.remote.domain.SavedConnection
import com.codex.remote.domain.ThreadGoal
import com.codex.remote.domain.ThreadGoalStatus
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.ui.screens.WorkspaceScreen
import com.codex.remote.ui.theme.CodexRemoteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WorkspaceDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composerMatchesDesktopLayoutAndPlanIsCommandDriven() {
        val state = mutableStateOf(baseState())
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onAllNodesWithText("Default", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Plan", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("5.6-sol · 极高 · 快速").assertExists()
        composeRule.onNodeWithTag(COMPOSER_SEND).assertContentDescriptionEquals("发送")
        composeRule.onNodeWithTag(COMPOSER_CONTEXT).assertContentDescriptionEquals("上下文已使用 64%")

        val add = composeRule.onNodeWithTag(COMPOSER_ADD).fetchSemanticsNode().boundsInRoot
        val permissions = composeRule.onNodeWithTag(COMPOSER_PERMISSIONS).fetchSemanticsNode().boundsInRoot
        val context = composeRule.onNodeWithTag(COMPOSER_CONTEXT).fetchSemanticsNode().boundsInRoot
        val model = composeRule.onNodeWithTag(COMPOSER_MODEL).fetchSemanticsNode().boundsInRoot
        val send = composeRule.onNodeWithTag(COMPOSER_SEND).fetchSemanticsNode().boundsInRoot
        assertTrue(add.center.x < permissions.center.x)
        assertTrue(permissions.center.x < context.center.x)
        assertTrue(context.center.x < model.center.x)
        assertTrue(model.center.x < send.center.x)

        composeRule.onNodeWithTag(COMPOSER_PERMISSIONS).performClick()
        composeRule.onNodeWithText("替我审批").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(PermissionMode.AUTO_REVIEW, callbacks.permissionMode) }

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/plan")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.runOnIdle { assertEquals("plan", callbacks.collaborationMode) }
        composeRule.onAllNodesWithText("Default", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Plan mode", substring = true).assertCountEquals(0)
    }

    @Test
    fun composerActionPausesAndContinuesTheSameThreadWithoutLosingDraft() {
        val state = mutableStateOf(
            baseState(isTurnRunning = true).copy(
                activeTurnId = "turn-a",
            ),
        )
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("检查测试结果")
        composeRule.onNodeWithTag(COMPOSER_SEND)
            .assertContentDescriptionEquals("暂停任务")
            .performClick()
        composeRule.runOnIdle { assertEquals(1, callbacks.stopRequests) }
        composeRule.onNodeWithTag(COMPOSER_SEND).assertContentDescriptionEquals("正在暂停任务")
        assertEquals("检查测试结果", composerText())
        composeRule.onNodeWithTag("composer-stop").assertDoesNotExist()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isTurnRunning = false,
                activeTurnId = null,
                isStoppingTurn = false,
                canResumeTurn = true,
            )
        }
        composeRule.onNodeWithTag(COMPOSER_SEND)
            .assertContentDescriptionEquals("继续任务")
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("检查测试结果" to false), callbacks.sentMessages)
        }
        assertEquals("", composerText())
    }

    @Test
    fun composerActionUsesSendStopAndResumeStatesAndDisablesEmptySend() {
        val state = mutableStateOf(baseState())
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        val action = composeRule.onNodeWithTag(COMPOSER_SEND)
        action.assertContentDescriptionEquals("发送").assertIsNotEnabled()

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("继续检查")
        action.assertContentDescriptionEquals("发送").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("继续检查" to false), callbacks.sentMessages)
            state.value = state.value.copy(canResumeTurn = true)
        }
        action.assertContentDescriptionEquals("继续任务").assertIsEnabled()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                canResumeTurn = false,
                isTurnRunning = true,
                activeTurnId = "turn-a",
            )
        }
        action.assertContentDescriptionEquals("暂停任务").assertIsEnabled()
    }

    @Test
    fun noticesAreShownAtTheTopOfWorkspaceContent() {
        show(mutableStateOf(baseState().copy(notice = "任务已暂停")))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("任务已暂停").fetchSemanticsNodes().isNotEmpty()
        }
        val host = composeRule.onNodeWithTag("top-snackbar-host").fetchSemanticsNode().boundsInRoot
        val conversation = composeRule.onNodeWithTag(CONVERSATION_LIST).fetchSemanticsNode().boundsInRoot
        val topPlacementTolerance = with(composeRule.density) { 40.dp.toPx() }

        assertTrue(
            "Notice should be anchored near the top of the conversation area",
            host.top <= conversation.top + topPlacementTolerance,
        )
    }

    @Test
    fun stoppedTurnCanContinueWithNoDraft() {
        val state = mutableStateOf(
            baseState().copy(canResumeTurn = true),
        )
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_SEND)
            .assertContentDescriptionEquals("继续任务")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("请从上次中断的位置继续完成之前的任务。" to false), callbacks.sentMessages)
        }
    }

    @Test
    fun separateWriterSnapshotShowsStatusButDoesNotOfferMutations() {
        val state = mutableStateOf(
            baseState(isTurnRunning = true).copy(
                isExternalWriterSnapshot = true,
                externalSnapshotUpdatedAt = System.currentTimeMillis(),
            ),
        )
        show(state)

        composeRule.onNodeWithText("此对话由另一处 Codex 窗口打开").assertIsDisplayed()
        composeRule.onNodeWithText("此页只显示状态", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("另开副本").assertIsDisplayed()
        composeRule.onNodeWithTag(COMPOSER_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(COMPOSER_SEND).assertDoesNotExist()
    }

    @Test
    fun completedTurnCollapsesReasoningCommandsToolsAndFileChangesLive() {
        val runningTimeline = listOf(
            TimelineItem("reasoning", TimelineKind.REASONING, title = "Reasoning", body = "private reasoning", status = "inProgress"),
            TimelineItem("cmd-1", TimelineKind.COMMAND, title = "pwd", body = "/workspace/demo", status = "running"),
            TimelineItem("cmd-2", TimelineKind.COMMAND, title = "git status", body = "clean", status = "running"),
            TimelineItem("tool", TimelineKind.TOOL, title = "Remote tool", body = "tool details", status = "started"),
            TimelineItem(
                "files",
                TimelineKind.FILE_CHANGE,
                status = "inProgress",
                fileChanges = listOf(
                    FileChangeSummary(
                        "app/src/Main.kt",
                        "update",
                        "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n-old\n+val ready = true",
                    ),
                ),
            ),
        )
        val state = mutableStateOf(baseState(timeline = runningTimeline, isTurnRunning = true))
        show(state)

        // A running step is a bare line: its output only exists one tap deeper, inside the sheet.
        scrollTo("timeline-steps-steps:reasoning")
        composeRule.onNodeWithText("正在处理 4 个步骤").assertIsDisplayed()
        composeRule.onNodeWithText("private reasoning").assertDoesNotExist()
        scrollTo("timeline-tool-command-group:cmd-1")
        composeRule.onNodeWithText("运行了多个命令").assertIsDisplayed()
        composeRule.onNodeWithText("/workspace/demo", substring = true).assertDoesNotExist()
        scrollTo("file-changes-files")
        composeRule.onNodeWithText("+val ready = true").assertDoesNotExist()

        scrollTo("timeline-tool-reasoning")
        composeRule.onNodeWithTag("timeline-tool-reasoning").performClick()
        composeRule.onNodeWithTag("step-detail-reasoning").assertIsDisplayed()
        composeRule.onNodeWithText("思考过程").assertIsDisplayed()
        composeRule.onNodeWithText("private reasoning").assertIsDisplayed()
        composeRule.onNodeWithTag("step-detail-reasoning-close").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("step-detail-reasoning").fetchSemanticsNodes().isEmpty()
        }

        scrollTo("timeline-tool-command-group:cmd-1")
        composeRule.onNodeWithTag("timeline-tool-command-group:cmd-1").performClick()
        composeRule.onNodeWithTag("step-detail-command-group:cmd-1").assertIsDisplayed()
        composeRule.onNodeWithText("/workspace/demo", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("step-detail-command-group:cmd-1-close").performClick()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isTurnRunning = false,
                activeTurnId = null,
                timeline = runningTimeline.map { it.copy(status = "completed") },
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("正在处理 4 个步骤").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(COMPOSER_SEND).assertContentDescriptionEquals("发送")

        scrollTo("timeline-steps-steps:reasoning")
        composeRule.onNodeWithText("已处理 4 个步骤").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-tool-reasoning").assertDoesNotExist()
        composeRule.onNodeWithTag("timeline-tool-command-group:cmd-1").assertDoesNotExist()
        composeRule.onNodeWithTag("file-changes-files").assertDoesNotExist()

        composeRule.onNodeWithTag("timeline-steps-header-steps:reasoning").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("timeline-tool-reasoning").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("timeline-tool-reasoning")
        composeRule.onNodeWithText("private reasoning").assertDoesNotExist()
        scrollTo("file-changes-files")
        composeRule.onNodeWithText("+val ready = true").assertDoesNotExist()

        composeRule.onNodeWithTag("file-changes-files").performClick()
        composeRule.onNodeWithTag("step-detail-files-files").assertIsDisplayed()
        composeRule.onNodeWithText("+val ready = true").assertIsDisplayed()
    }

    @Test
    fun singleCommandSheetSplitsCommandLineFromOutput() {
        show(
            mutableStateOf(
                baseState(
                    timeline = listOf(
                        TimelineItem(
                            "cmd",
                            TimelineKind.COMMAND,
                            title = "bash scripts/deploy.sh --check",
                            body = "rollout is stable",
                            status = "completed",
                        ),
                    ),
                ),
            ),
        )
        composeRule.onNodeWithTag("timeline-steps-header-steps:cmd").performClick()
        scrollTo("timeline-tool-cmd")
        composeRule.onNodeWithTag("timeline-tool-cmd").performClick()
        composeRule.onNodeWithTag("step-detail-cmd").assertIsDisplayed()
        composeRule.onNodeWithText("执行命令").assertIsDisplayed()
        composeRule.onNodeWithText("命令").assertIsDisplayed()
        // The row label and the sheet's 命令 section show the same shell line.
        composeRule.onAllNodesWithText("bash scripts/deploy.sh --check").assertCountEquals(2)
        composeRule.onNodeWithText("输出").assertIsDisplayed()
        composeRule.onNodeWithText("rollout is stable").assertIsDisplayed()
    }

    @Test
    fun markdownBoldCodeAndLatexRenderAsAndroidSpans() {
        val markdown = """## Rendered heading

**bold-rendered** and inline ${'$'}x^2${'$'}.

```kotlin
val answer = 42
```
""".trimIndent()
        show(mutableStateOf(baseState(timeline = listOf(TimelineItem("markdown", TimelineKind.AGENT, body = markdown)))))
        composeRule.onNodeWithTag("timeline-agent-markdown").assertExists()

        lateinit var rendered: Spanned
        composeRule.runOnIdle {
            val textView = composeRule.activity.window.decorView.descendantTextViews()
                .firstOrNull { it.text.toString().contains("bold-rendered") }
            assertNotNull(textView)
            rendered = textView!!.text as Spanned
        }
        val plainText = rendered.toString()
        val spans = rendered.getSpans(0, rendered.length, Any::class.java)
        assertFalse(plainText.contains("**"))
        assertFalse(plainText.contains("```"))
        assertTrue(plainText.contains("val answer = 42"))
        assertTrue(spans.any { span ->
            (span is StyleSpan && span.style and Typeface.BOLD != 0) ||
                span.javaClass.name.contains("StrongEmphasis", ignoreCase = true)
        })
        assertTrue(spans.any { span ->
            span.javaClass.name.contains("latex", ignoreCase = true) ||
                span.javaClass.name.contains("drawable", ignoreCase = true)
        })
    }

    @Test
    fun bottomButtonReachesSentinelAfterLongAgentResponse() {
        val longAnswer = (1..80).joinToString("\n\n") { "Rendered answer paragraph $it" }
        val timeline = buildList {
            repeat(20) { index -> add(TimelineItem("user-$index", TimelineKind.USER, body = "Question $index")) }
            add(TimelineItem("last-answer", TimelineKind.AGENT, body = longAnswer))
        }
        show(mutableStateOf(baseState(timeline = timeline)))

        composeRule.onNodeWithTag(CONVERSATION_BOTTOM).assertIsDisplayed()
        composeRule.onNodeWithTag(CONVERSATION_LIST).performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CONVERSATION_BOTTOM_BUTTON).assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(CONVERSATION_BOTTOM).assertIsDisplayed()
    }

    @Test
    fun loadingFiveOlderTurnsKeepsTheVisibleMessageAnchored() {
        val current = (0 until 10).flatMap { index ->
            listOf(
                TimelineItem("new-user-$index", TimelineKind.USER, body = "Current question $index"),
                TimelineItem("new-agent-$index", TimelineKind.AGENT, body = "Current answer $index"),
            )
        }
        val older = (0 until 5).flatMap { index ->
            listOf(
                TimelineItem("old-user-$index", TimelineKind.USER, body = "Older question $index"),
                TimelineItem("old-agent-$index", TimelineKind.AGENT, body = "Older answer $index"),
            )
        }
        val state = mutableStateOf(baseState(timeline = current).copy(hasOlderHistory = true, olderHistoryCursor = "page-2"))
        val callbacks = WorkspaceCallbacks()
        callbacks.onLoadOlder = {
            callbacks.olderLoads++
            state.value = state.value.copy(isOlderHistoryLoading = true)
        }
        show(state, callbacks)

        repeat(12) {
            if (callbacks.olderLoads > 0) return@repeat
            composeRule.onNodeWithTag(CONVERSATION_LIST).performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        assertEquals(1, callbacks.olderLoads)
        val before = composeRule.onNodeWithTag("timeline-item-new-agent-0").fetchSemanticsNode().boundsInRoot.top
        composeRule.runOnIdle {
            state.value = state.value.copy(
                timeline = older + current,
                isOlderHistoryLoading = false,
                hasOlderHistory = false,
                olderHistoryCursor = null,
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("timeline-item-new-agent-0").fetchSemanticsNodes().isNotEmpty()
        }
        val after = composeRule.onNodeWithTag("timeline-item-new-agent-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "History anchor moved from $before to $after (${abs(after - before)} px)",
            abs(after - before) <= 4f,
        )
        composeRule.onNodeWithTag(CONVERSATION_LIST).performScrollToNode(hasTestTag("timeline-item-old-user-0"))
        composeRule.onNodeWithText("Older question 0").assertIsDisplayed()
    }

    @Test
    fun switchingThreadsClearsDraftAndNeverShowsTheOtherTimeline() {
        val threadA = thread("thread-a", "Thread A")
        val threadB = thread("thread-b", "Thread B")
        val state = mutableStateOf(
            baseState(
                timeline = listOf(TimelineItem("a-message", TimelineKind.USER, body = "thread-a-body")),
                threads = listOf(threadA, threadB),
            ),
        )
        show(state)
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("draft-a")
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertExists()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                selectedThreadId = "thread-b",
                timeline = (0 until 30).map { index ->
                    TimelineItem("b-message-$index", TimelineKind.USER, body = "thread-b-body-$index")
                },
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("thread-b-body-29").assertExists()
        composeRule.onNodeWithText("thread-a-body").assertDoesNotExist()
        composeRule.onAllNodesWithTag(CONVERSATION_BOTTOM_BUTTON).assertCountEquals(0)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
        assertFalse(composerText().contains("draft-a"))
        composeRule.runOnIdle {
            assertTrue(state.value.acceptsThreadEvent("thread-b"))
            assertFalse(state.value.acceptsThreadEvent("thread-a"))
            assertFalse(state.value.acceptsThreadEvent(null))
        }
    }

    @Test
    fun addMenuUsesDismissibleGoalMarkerAndStillProvidesPlanSkillsAndPlugins() {
        val state = mutableStateOf(
            baseState().copy(
                skills = listOf(RemoteSkill("qa-check", "QA Skill", "Device test skill", "/skills/qa/SKILL.md", true, setOf("/workspace/demo"))),
                plugins = listOf(RemotePlugin("qa@local", "qa-plugin", "QA Plugin", "Device test plugin", "local", "plugin://qa@local", true)),
            ),
        )
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithText("Unsupported method", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed().performClick()
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("ship-device-tests")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("ship-device-tests" to true), callbacks.sentMessages)
        }
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("计划模式").assertIsDisplayed().performClick()
        assertEquals("plan", callbacks.collaborationMode)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("QA Skill").performScrollTo().performClick()
        assertTrue(composerText().contains("${'$'}qa-check"))
        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("QA Plugin").performScrollTo().performClick()
        assertTrue(composerText().contains("${'$'}qa-plugin"))
    }

    @Test
    fun goalSlashCommandActivatesTheSameMarkerAndOnlyMarksTheNextSend() {
        val state = mutableStateOf(baseState())
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/goal")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(callbacks.sentMessages.isEmpty()) }

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("finish-device-coverage")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
        assertEquals("", composerText())

        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("normal-follow-up")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        assertEquals("", composerText())
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/goal inline-objective")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "finish-device-coverage" to true,
                    "normal-follow-up" to false,
                    "inline-objective" to true,
                ),
                callbacks.sentMessages,
            )
        }
    }

    @Test
    fun goalMarkerWorksBeforeTheRemoteThreadExists() {
        val state = mutableStateOf(baseState(threads = emptyList()).copy(selectedThreadId = null))
        val callbacks = WorkspaceCallbacks()
        show(state, callbacks)

        composeRule.onNodeWithTag(COMPOSER_ADD).performClick()
        composeRule.onNodeWithText("目标").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMPOSER_GOAL_MARKER).assertIsDisplayed()
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("create-thread-with-goal")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("create-thread-with-goal" to true), callbacks.sentMessages)
        }
        composeRule.onAllNodesWithText("请先发送第一条消息", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(COMPOSER_GOAL_MARKER).assertCountEquals(0)
    }

    @Test
    fun goalMessagesAreVisiblyDistinguishedFromNormalMessages() {
        val state = mutableStateOf(
            baseState(
                timeline = listOf(
                    TimelineItem("goal-message", TimelineKind.USER, body = "ship it", isGoal = true),
                    TimelineItem("normal-message", TimelineKind.USER, body = "status update"),
                ),
            ),
        )
        show(state)

        composeRule.onNodeWithTag("timeline-goal-goal-message").assertExists()
        composeRule.onNodeWithText("作为目标发送").assertExists()
        composeRule.onAllNodesWithTag("timeline-goal-normal-message").assertCountEquals(0)
    }

    @Test
    fun connectedHeaderIsOneLineCarriedByTheDotAlone() {
        show(mutableStateOf(baseState()))

        composeRule.onNodeWithText("Thread A").assertIsDisplayed()
        // The host string used to cost the header a second line, and repeated what the full-screen
        // connecting state says anyway.
        composeRule.onAllNodesWithText("10.0.2.2").assertCountEquals(0)
    }

    @Test
    fun completedRepliesOfferCopyAndReAskTheirOwnPrompt() {
        val callbacks = WorkspaceCallbacks()
        val state = mutableStateOf(
            baseState(
                timeline = listOf(
                    TimelineItem("ask-1", TimelineKind.USER, body = "跑一下设备测试"),
                    TimelineItem("answer-1", TimelineKind.AGENT, body = "第一轮结论"),
                    TimelineItem("ask-2", TimelineKind.USER, body = "再解释一遍"),
                    TimelineItem("answer-2", TimelineKind.AGENT, body = "第二轮结论"),
                ),
            ),
        )
        show(state, callbacks)

        val actions = composeRule.onNodeWithTag("reply-actions-answer-2")
        actions.assertIsDisplayed()
        composeRule.onAllNodesWithText("复制").assertCountEquals(2)
        actions.onChildren()[0].performClick()
        composeRule.onNodeWithText("已复制").assertIsDisplayed()
        actions.onChildren()[1].performClick()

        // Each reply re-asks the prompt that produced it, not whichever one came last.
        composeRule.onNodeWithTag("reply-actions-answer-1").onChildren()[1].performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("再解释一遍" to false, "跑一下设备测试" to false), callbacks.sentMessages)
        }

        // A running turn means the replies are not finished business, so the rows go.
        state.value = state.value.copy(isTurnRunning = true)
        composeRule.onAllNodesWithTag("reply-actions-answer-2").assertCountEquals(0)
    }

    @Test
    fun completedFileChangesOpenTheirDiffInADetailSheet() {
        val files = TimelineItem(
            id = "files",
            kind = TimelineKind.FILE_CHANGE,
            status = "completed",
            fileChanges = listOf(
                FileChangeSummary("app/src/Main.kt", "update", "--- a/app/src/Main.kt\n+++ b/app/src/Main.kt\n-old\n+new\n+next"),
                FileChangeSummary("app/src/Status.kt", "add", "--- /dev/null\n+++ b/app/src/Status.kt\n+val ready = true"),
            ),
        )
        show(mutableStateOf(baseState(timeline = listOf(files))))

        // A finished run folds into one summary line, so the file list sits one tap deeper.
        composeRule.onNodeWithText("已处理 1 个步骤").assertIsDisplayed()
        composeRule.onNodeWithText("已编辑 2 个文件").assertDoesNotExist()
        composeRule.onNodeWithTag("timeline-steps-header-steps:files").performClick()
        composeRule.onNodeWithText("已编辑 2 个文件").assertIsDisplayed()
        composeRule.onNodeWithText("--- a/app/src/Main.kt").assertDoesNotExist()
        composeRule.onNodeWithText("已编辑 2 个文件").performClick()
        composeRule.onNodeWithTag("step-detail-files-files").assertIsDisplayed()
        composeRule.onNodeWithText("文件更改").assertIsDisplayed()
        composeRule.onNodeWithText("app/src/Main.kt").assertExists()
        composeRule.onNodeWithText("app/src/Status.kt").assertExists()
        composeRule.onNodeWithText("已修改").assertExists()
        composeRule.onNodeWithText("新增").assertExists()
        composeRule.onNodeWithText("+2  -1").assertExists()
        composeRule.onNodeWithText("--- a/app/src/Main.kt").assertExists()
        composeRule.onNodeWithTag("step-detail-files-files-close").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("step-detail-files-files").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("已编辑 2 个文件").assertIsDisplayed()
        composeRule.onAllNodesWithText("Changes").assertCountEquals(0)
        composeRule.onAllNodesWithText("{\"changes\"", substring = true).assertCountEquals(0)
    }

    @Test
    fun captureBaselineLight() = captureBaseline(dark = false)

    @Test
    fun captureBaselineDark() = captureBaseline(dark = true)

    private fun captureBaseline(dark: Boolean) {
        val label = if (dark) "dark" else "light"
        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("baseline")!!
        dir.mkdirs()
        val all = baselineTimeline()
        val shots = listOf(
            "01-structure" to all.filter { it.id in setOf("ask", "reasoning", "cmd-1", "tool-1", "files") },
            "02-prose" to all.filter { it.id in setOf("ask", "prose") },
            "03-code" to all.filter { it.id == "code" },
            "04-table" to all.filter { it.id == "tbl" },
            "05-full" to all,
        )
        val state = mutableStateOf(baseState(timeline = shots.first().second))
        show(state, WorkspaceCallbacks(), dark)
        shots.forEachIndexed { index, (name, timeline) ->
            if (index > 0) composeRule.runOnIdle { state.value = baseState(timeline = timeline) }
            composeRule.waitForIdle()
            Thread.sleep(if (index == 0) 700 else 500)
            writeShot(dir, "$name-$label.png")
        }

        composeRule.runOnIdle { state.value = baseState(timeline = shots.first().second) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-steps-header-steps:reasoning").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        writeShot(dir, "06-steps-open-$label.png")

        val streamParagraphs = (1..6).joinToString("\n\n") {
            "第 $it 段：灰度期间每个调用点都要能单独回滚，否则线上出问题时无法定位是哪一处引入的。"
        }
        composeRule.runOnIdle {
            state.value = baseState(
                timeline = listOf(
                    TimelineItem("ask-stream", TimelineKind.USER, body = "再详细说说灰度方案", status = "completed"),
                    TimelineItem("stream", TimelineKind.AGENT, body = streamParagraphs, status = "inProgress"),
                ),
                isTurnRunning = true,
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(500)
        writeShot(dir, "07-streaming-$label.png")

        // A turn that is still working: the run stays unfolded and every step is one bare line.
        val runningSteps = listOf(
            TimelineItem("ask-steps", TimelineKind.USER, body = "把发布脚本改成幂等的", status = "completed"),
            TimelineItem(
                "reason-steps",
                TimelineKind.REASONING,
                title = "深度思考",
                body = "先确认脚本能否重入，再决定改哪一段。",
                status = "completed",
            ),
            TimelineItem(
                "cmd-steps",
                TimelineKind.COMMAND,
                title = "bash scripts/deploy.sh --check",
                body = "rollout 稳定，没有残留的 job",
                status = "completed",
            ),
            TimelineItem(
                "files-steps",
                TimelineKind.FILE_CHANGE,
                status = "completed",
                fileChanges = listOf(
                    FileChangeSummary(
                        "scripts/deploy.sh",
                        "update",
                        "--- a/scripts/deploy.sh\n+++ b/scripts/deploy.sh\n-bash deploy.sh\n+set -euo pipefail\n+for attempt in 1 2 3; do\n+  kubectl rollout status deploy/api\n+done",
                    ),
                ),
            ),
            TimelineItem(
                "push-steps",
                TimelineKind.COMMAND,
                title = "git push origin HEAD",
                body = "To github.com:acme/demo.git",
                status = "inProgress",
            ),
        )
        composeRule.runOnIdle { state.value = baseState(timeline = runningSteps, isTurnRunning = true) }
        composeRule.waitForIdle()
        Thread.sleep(500)
        writeShot(dir, "08-running-steps-$label.png")

        composeRule.onNodeWithTag("file-changes-files-steps").performClick()
        composeRule.waitForIdle()
        Thread.sleep(800)
        writeShot(dir, "09-detail-sheet-$label.png")

        composeRule.onNodeWithTag("step-detail-files-files-steps-close").performClick()
        composeRule.waitForIdle()
        Thread.sleep(600)
        composeRule.onNodeWithTag("timeline-tool-cmd-steps").performClick()
        composeRule.waitForIdle()
        Thread.sleep(800)
        writeShot(dir, "10-command-sheet-$label.png")
        composeRule.onNodeWithTag("step-detail-cmd-steps-close").performClick()
        composeRule.waitForIdle()
        Thread.sleep(600)

        // A settled turn: the reply sits bare with its actions under it.
        composeRule.runOnIdle {
            state.value = baseState(
                timeline = listOf(
                    TimelineItem("ask-final", TimelineKind.USER, body = "把发布脚本改成幂等的", status = "completed"),
                    TimelineItem("answer-final", TimelineKind.AGENT, body = baselineProse, status = "completed"),
                ),
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(500)
        writeShot(dir, "11-reply-actions-$label.png")

        // Goal mode: the chip sits on the text's own line, above the placeholder.
        composeRule.onNodeWithTag(COMPOSER_INPUT).performTextInput("/goal")
        composeRule.onNodeWithTag(COMPOSER_SEND).performClick()
        composeRule.waitForIdle()
        Thread.sleep(700)
        writeShot(dir, "12-goal-chip-$label.png")
    }

    @Suppress("DEPRECATION")
    private fun writeShot(dir: java.io.File, name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        java.io.File(dir, name).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(CONVERSATION_LIST).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun composerText(): String = composeRule.onNodeWithTag(COMPOSER_INPUT)
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun show(
        state: MutableState<AppUiState>,
        callbacks: WorkspaceCallbacks = WorkspaceCallbacks(),
        darkTheme: Boolean = false,
    ) {
        composeRule.setContent {
            CodexRemoteTheme(darkTheme = darkTheme) {
                WorkspaceScreen(
                    state = state.value,
                    onOpenConnections = {},
                    onNewThread = {},
                    onSelectProject = {},
                    onSelectThread = {},
                    onLoadOlderHistory = callbacks.onLoadOlder,
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onLoadArchivedThreads = {},
                    onUnarchiveThread = {},
                    onDeleteArchivedThread = {},
                    onSetThreadPinned = { _, _ -> },
                    onSend = callbacks.onSend,
                    onStop = {
                        callbacks.stopRequests++
                        state.value = state.value.copy(isStoppingTurn = true)
                    },
                    onCompactThread = {},
                    onForkThread = {},
                    onRefreshExternalThreadSnapshot = {},
                    onStartReview = { _, _ -> },
                    onRunInit = {},
                    onLoadMcpStatus = {},
                    onReloadMcpServers = {},
                    onStartMcpLogin = {},
                    onMcpAuthorizationHandled = {},
                    onSubmitFeedback = { _, _ -> },
                    onSetGoal = callbacks.onSetGoal,
                    onSetGoalStatus = {},
                    onClearGoal = {},
                    onShowStatus = {},
                    onSetModel = {},
                    onSetReasoningEffort = {},
                    onSetServiceTier = {},
                    onSetCollaborationMode = { callbacks.collaborationMode = it },
                    onSetPermissionProfile = {},
                    onSetPermissionMode = { callbacks.permissionMode = it },
                    onLoadRemoteDirectory = {},
                    onClearRemoteDirectory = {},
                    onStartLogin = {},
                    onCancelLogin = {},
                    onApproval = { _, _ -> },
                    onTrustHostKey = {},
                    onRejectHostKey = {},
                    onDismissNotice = {},
                )
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class RemoteStateDeviceTest {
    @Test
    fun goalAndPlanParamsMatchTheOfficialAppServerProtocol() {
        val goalParams = CodexRpcClient.threadGoalSetParams(
            threadId = "thread-a",
            objective = "ship",
            status = ThreadGoalStatus.ACTIVE,
        )
        assertEquals("thread-a", goalParams["threadId"]?.jsonPrimitive?.content)
        assertEquals("ship", goalParams["objective"]?.jsonPrimitive?.content)
        assertEquals("active", goalParams["status"]?.jsonPrimitive?.content)

        val turnParams = CodexRpcClient.turnStartParams(
            threadId = "thread-a",
            text = "plan request",
            cwd = "/workspace/demo",
            model = "gpt-5.6-sol",
            reasoningEffort = "ultra",
            serviceTier = "fast",
            approvalPolicy = "on-request",
            collaborationMode = RemoteCollaborationMode("Plan", "plan", "gpt-5.6-sol", "ultra"),
            mentions = emptyList(),
        )
        assertEquals("plan", turnParams["collaborationMode"]?.jsonObject?.get("mode")?.jsonPrimitive?.content)
        assertEquals("gpt-5.6-sol", turnParams["model"]?.jsonPrimitive?.content)
        assertEquals("ultra", turnParams["effort"]?.jsonPrimitive?.content)
        assertEquals("fast", turnParams["serviceTier"]?.jsonPrimitive?.content)
    }

    @Test
    fun lastUsedServerPersistsAndIsSelectedForAutomaticRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConnectionStore(context)
        val suffix = UUID.randomUUID().toString()
        val first = store.save(
            com.codex.remote.domain.ConnectionDraft(
                name = "device-first-$suffix",
                host = "127.0.0.1",
                port = "22",
                username = "tester",
                password = "secret",
            ),
        )
        val second = store.save(
            com.codex.remote.domain.ConnectionDraft(
                name = "device-second-$suffix",
                host = "127.0.0.2",
                port = "22",
                username = "tester",
                password = "secret",
            ),
        )
        try {
            store.recordUsed(first.id)
            val persisted = withTimeout(5_000) {
                store.connections.first { connections ->
                    connections.any { it.id == first.id && it.lastUsedAt > 0 } &&
                        connections.any { it.id == second.id }
                }
            }
            assertEquals(first.id, persisted.lastUsedConnectionOrNull()?.id)
        } finally {
            store.delete(first.id)
            store.delete(second.id)
        }
    }
}

private class WorkspaceCallbacks {
    var collaborationMode: String? = null
    var permissionMode: PermissionMode? = null
    val sentMessages = mutableListOf<Pair<String, Boolean>>()
    var stopRequests: Int = 0
    var olderLoads: Int = 0
    var onLoadOlder: () -> Unit = {}
    var onSetGoal: (String) -> Unit = {}
    var onSend: (String, List<ComposerMention>, List<ComposerImageAttachment>, Boolean) -> Unit =
        { text, _, _, asGoal -> sentMessages += text.trim() to asGoal }
}

private fun baseState(
    timeline: List<TimelineItem> = emptyList(),
    isTurnRunning: Boolean = false,
    threads: List<RemoteThread> = listOf(thread("thread-a", "Thread A")),
): AppUiState {
    val connection = SavedConnection(
        id = "connection",
        name = "QA Host",
        host = "10.0.2.2",
        username = "tester",
        authType = AuthType.PASSWORD,
        encryptedPassword = "encrypted",
        lastUsedAt = 1,
    )
    val project = RemoteProject(
        id = "/workspace/demo",
        name = "demo",
        path = "/workspace/demo",
        threads = threads,
        updatedAt = 1,
    )
    val model = RemoteModel(
        id = "gpt-5.6-sol",
        displayName = "GPT-5.6-Sol",
        description = "Device test model",
        isDefault = true,
        supportedReasoningEfforts = listOf(ReasoningEffortOption("ultra", "Maximum")),
        defaultReasoningEffort = "ultra",
        inputModalities = setOf("text", "image"),
        serviceTiers = listOf(RemoteServiceTier("fast", "Fast", "Priority processing")),
        defaultServiceTier = "fast",
    )
    return AppUiState(
        savedConnections = listOf(connection),
        activeConnection = connection,
        connectionStatus = com.codex.remote.domain.ConnectionStatus.CONNECTED,
        threads = threads,
        projects = listOf(project),
        selectedProjectPath = project.path,
        selectedThreadId = threads.firstOrNull()?.id,
        timeline = timeline,
        models = listOf(model),
        selectedModel = model.id,
        selectedReasoningEffort = "ultra",
        selectedServiceTier = "fast",
        collaborationModes = listOf(
            RemoteCollaborationMode("Default", "default"),
            RemoteCollaborationMode("Plan", "plan", model.id, "ultra"),
        ),
        selectedCollaborationMode = "default",
        selectedPermissionProfile = ":workspace",
        approvalPolicy = "on-request",
        approvalsReviewer = "user",
        remoteAccount = RemoteAccount("chatgpt", "qa@example.invalid", "plus", false),
        threadTokenUsage = RemoteThreadTokenUsage(128_000, 120_000, 8_000, 200_000),
        isTurnRunning = isTurnRunning,
        activeTurnId = if (isTurnRunning) "turn-a" else null,
        isRestoringLastConnection = false,
    )
}

private val baselineProse = """
## 这次重构的结论

登录链路从 `SessionRepository` 迁到了 **AuthGateway**，一共影响 3 个调用点。整体方向没问题，但有两处需要在上线前收口。

### 关键路径

1. 点击登录后由 `AuthGateway.signIn()` 申请短期 access token
2. 后台协程每 15 分钟触发一次 `refreshIfNeeded()`
3. 连续失败两次后降级到 `OfflineQueue` 排队重试

> 注意：`OfflineQueue` 目前没有容量上限，长会话会持续增长。
""".trimIndent()

private val baselineCode = """
### 改动位置

```kotlin
suspend fun refreshIfNeeded() {
    if (clock.now() - lastRefresh > TOKEN_TTL) {
        gateway.refresh(scope = this)
    }
}
```
""".trimIndent()

private val baselineTableOnly = """
| 模块 | 风险 | 建议 |
| --- | --- | --- |
| AuthGateway | 中 | 补超时与重试 |
| OfflineQueue | 高 | 设容量上限 |
""".trimIndent()

private val baselineTable = """
| 模块 | 风险 | 建议 |
| --- | --- | --- |
| AuthGateway | 中 | 补超时与重试 |
| OfflineQueue | 高 | 设容量上限 |

公式与正文混排：${'$'}E = mc^2${'$'}，用来检查基线与行距。

- [x] 迁移旧调用点
- [ ] 补齐单元测试
""".trimIndent()

private fun baselineTimeline(): List<TimelineItem> = listOf(
    TimelineItem("ask", TimelineKind.USER, body = "帮我梳理这次登录重构改了哪些地方，风险点列一下"),
    TimelineItem(
        id = "reasoning",
        kind = TimelineKind.REASONING,
        title = "Reasoning",
        body = "先比较 AuthGateway 与 SessionRepository 的职责边界，再统计受影响的调用点。",
        status = "completed",
    ),
    TimelineItem(
        id = "cmd-1",
        kind = TimelineKind.COMMAND,
        title = "git diff --stat HEAD~1",
        body = " app/src/AuthGateway.kt        | 64 +++++++++++++++++++--\n" +
            " app/src/SessionRepository.kt  | 38 -----------------",
        status = "completed",
    ),
    TimelineItem(
        id = "tool-1",
        kind = TimelineKind.TOOL,
        title = "workspace_search",
        body = "命中 3 处：MainActivity.kt:84、TokenStore.kt:21、OfflineQueue.kt:7",
        status = "completed",
    ),
    TimelineItem(
        id = "files",
        kind = TimelineKind.FILE_CHANGE,
        status = "completed",
        fileChanges = listOf(
            FileChangeSummary(
                "app/src/AuthGateway.kt",
                "update",
                "--- a/app/src/AuthGateway.kt\n+++ b/app/src/AuthGateway.kt\n-    val ttl = 900\n+    val ttl = 1800\n+    private val mutex = Mutex()",
            ),
            FileChangeSummary(
                "app/src/OfflineQueue.kt",
                "add",
                "--- /dev/null\n+++ b/app/src/OfflineQueue.kt\n+class OfflineQueue {\n+    private val pending = ArrayDeque<Suspendable>",
            ),
        ),
    ),
    TimelineItem("prose", TimelineKind.AGENT, body = baselineProse),
    TimelineItem("code", TimelineKind.AGENT, body = baselineCode),
    TimelineItem("table", TimelineKind.AGENT, body = baselineTable),
    TimelineItem("tbl", TimelineKind.AGENT, body = baselineTableOnly),
    TimelineItem("ask-2", TimelineKind.USER, body = "OfflineQueue 的上限你建议设多少？"),
    TimelineItem(
        "answer",
        TimelineKind.AGENT,
        body = "先按 **256** 条来设，超过就从队首丢弃并打点；这条链路里单条最长是 4 KB，256 条上限约 1 MB 内存，够用也不会拖慢冷启动。",
    ),
)

private fun thread(id: String, title: String) = RemoteThread(
    id = id,
    title = title,
    cwd = "/workspace/demo",
    updatedAt = 1,
    status = "idle",
)

private fun goal(objective: String) = ThreadGoal(
    threadId = "thread-a",
    objective = objective,
    status = ThreadGoalStatus.ACTIVE,
    tokenBudget = null,
    tokensUsed = 0,
    timeUsedSeconds = 0,
    createdAt = 1,
    updatedAt = 1,
)

private fun View.descendantTextViews(): List<TextView> = buildList {
    if (this@descendantTextViews is TextView) add(this@descendantTextViews)
    if (this@descendantTextViews is ViewGroup) {
        repeat(childCount) { childIndex -> addAll(getChildAt(childIndex).descendantTextViews()) }
    }
}

private const val CONVERSATION_LIST = "conversation-list"
private const val CONVERSATION_BOTTOM = "conversation-bottom"
private const val CONVERSATION_BOTTOM_BUTTON = "conversation-bottom-button"
private const val COMPOSER_INPUT = "composer-input"
private const val COMPOSER_ADD = "composer-add"
private const val COMPOSER_PERMISSIONS = "composer-permissions"
private const val COMPOSER_CONTEXT = "composer-context"
private const val COMPOSER_MODEL = "composer-model"
private const val COMPOSER_SEND = "composer-send"
private const val COMPOSER_GOAL_MARKER = "composer-goal-marker"
