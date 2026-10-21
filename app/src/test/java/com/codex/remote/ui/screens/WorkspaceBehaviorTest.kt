package com.codex.remote.ui.screens

import com.codex.remote.domain.PermissionMode
import com.codex.remote.domain.TimelineItem
import com.codex.remote.domain.TimelineKind
import com.codex.remote.ui.chat.appendRemotePath
import com.codex.remote.ui.chat.diffLineCounts
import com.codex.remote.ui.chat.extractGoalSlashObjective
import com.codex.remote.ui.chat.groupTimelineSteps
import com.codex.remote.ui.chat.isTimelineItemRunning
import com.codex.remote.ui.chat.normalizeLatexMarkdown
import com.codex.remote.ui.chat.permissionModeFor
import com.codex.remote.ui.chat.remoteParentPath
import com.codex.remote.ui.chat.remotePathComposerToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBehaviorTest {
    @Test
    fun remotePathNavigationSupportsPosixAndWindowsHosts() {
        assertEquals("/srv/app/src", appendRemotePath("/srv/app/", "src"))
        assertEquals("C:\\work\\app\\src", appendRemotePath("C:\\work\\app\\", "src"))
        assertEquals("/srv/app", remoteParentPath("/srv/app/src"))
        assertEquals("C:\\work\\app", remoteParentPath("C:\\work\\app\\src"))
        assertNull(remoteParentPath("/"))
        assertNull(remoteParentPath("C:\\"))
    }

    @Test
    fun selectedRemotePathsBecomeReadableComposerReferences() {
        assertEquals("@src/Main.kt", remotePathComposerToken("/srv/app", "/srv/app/src/Main.kt"))
        assertEquals("@\"docs/release notes.md\"", remotePathComposerToken("C:\\work\\app", "C:\\work\\app\\docs\\release notes.md"))
    }

    @Test
    fun goalSlashCommandExtractsOnlyARealGoalPrefix() {
        assertEquals("Ship release", extractGoalSlashObjective("/goal Ship release"))
        assertEquals("Ship release", extractGoalSlashObjective("  /GOAL   Ship release  "))
        assertEquals("", extractGoalSlashObjective("/goal"))
        assertNull(extractGoalSlashObjective("/goalkeeper is not a command"))
        assertNull(extractGoalSlashObjective("explain /goal"))
    }

    @Test
    fun reasoningOnlyAutoExpandsWhileItIsRunning() {
        assertTrue("inProgress".isTimelineItemRunning())
        assertTrue("running".isTimelineItemRunning())
        assertFalse("completed".isTimelineItemRunning())
        assertFalse("failed".isTimelineItemRunning())
    }

    @Test
    fun markdownLatexNormalizationLeavesCodeUntouched() {
        val markdown = """Inline ${'$'}x^2 + y^2${'$'}.
`val raw = "${'$'}notMath${'$'}"`
```kotlin
val block = "${'$'}stillRaw${'$'}"
```
""".trimIndent()

        assertEquals(
            """Inline ${'$'}${'$'}x^2 + y^2${'$'}${'$'}.
`val raw = "${'$'}notMath${'$'}"`
```kotlin
val block = "${'$'}stillRaw${'$'}"
```
""".trimIndent(),
            normalizeLatexMarkdown(markdown),
        )
    }

    @Test
    fun permissionModesDistinguishAutoReviewAndRemoteProfiles() {
        assertEquals(PermissionMode.ASK, permissionModeFor(":workspace", "on-request", "user"))
        assertEquals(PermissionMode.AUTO_REVIEW, permissionModeFor(":workspace", "on-request", "auto_review"))
        assertEquals(PermissionMode.FULL_ACCESS, permissionModeFor(":danger-full-access", "never", "user"))
        assertEquals(PermissionMode.READ_ONLY, permissionModeFor(":read-only", "on-request", "user"))
        assertNull(permissionModeFor("team-policy", "on-request", "user"))
    }

    @Test
    fun structuredDiffCountsIgnoreFileHeaders() {
        assertEquals(2 to 1, diffLineCounts("--- a/app.kt\n+++ b/app.kt\n-old\n+new\n+next"))
    }

    @Test
    fun aRunOfStepsCollapsesIntoOneSummaryThatKeepsItsSourceIds() {
        val timeline = listOf(
            TimelineItem("reasoning", TimelineKind.REASONING, body = "thinking"),
            TimelineItem("cmd-1", TimelineKind.COMMAND, title = "pwd", body = "/srv/app", status = "completed"),
            TimelineItem("cmd-2", TimelineKind.COMMAND, title = "git status", body = "clean", status = "completed"),
            TimelineItem("answer", TimelineKind.AGENT, body = "Done"),
        )

        val presentation = groupTimelineSteps(timeline)

        assertEquals(2, presentation.size)
        val group = presentation[0]
        assertEquals("steps:reasoning", group.item.id)
        assertEquals("completed", group.item.status)
        assertEquals(listOf("reasoning", "cmd-1", "cmd-2"), group.sourceItemIds)
        assertEquals(2, group.steps.size)
        // Adjacent commands still fold into a single step inside the run.
        val commands = group.steps[1]
        assertEquals("command-group:cmd-1", commands.item.id)
        assertEquals("运行了多个命令", commands.item.title)
        assertTrue(commands.item.body.contains("1. pwd"))
        assertTrue(commands.item.body.contains("2. git status"))
        assertEquals("Done", presentation[1].item.body)
    }

    @Test
    fun aRunningStepRunReportsInProgressSoTheSummaryStaysOpen() {
        val presentation = groupTimelineSteps(
            listOf(TimelineItem("tool", TimelineKind.TOOL, title = "Remote tool", status = "started")),
        )

        assertEquals(1, presentation.size)
        assertEquals("inProgress", presentation[0].item.status)
        assertEquals(1, presentation[0].steps.size)
    }
}
