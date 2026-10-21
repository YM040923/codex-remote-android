package com.codex.remote.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.remote.ui.theme.MonoText


private enum class MarkdownBlockType { TEXT, CODE, TABLE, QUOTE, LIST, HEADING, SEPARATOR }

private data class MarkdownBlock(
    val type: MarkdownBlockType,
    val content: String,
    val language: String? = null,
    val level: Int = 0,
)

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        // Fenced code block
        val fenceMatch = Regex("^(\\s*)(```|~~~)(.*)$").matchEntire(line)
        if (fenceMatch != null) {
            val indent = fenceMatch.groupValues[1]
            val fence = fenceMatch.groupValues[2]
            val lang = fenceMatch.groupValues[3].trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val codeLine = lines[i]
                val closing = Regex("^\\s*${Regex.escape(fence)}\\s*$").matchEntire(codeLine)
                if (closing != null) { i++; break }
                codeLines.add(codeLine.removePrefix(indent))
                i++
            }
            blocks.add(MarkdownBlock(MarkdownBlockType.CODE, codeLines.joinToString("\n"), lang))
            continue
        }
        // Table block (| ... | line followed by |---|---| separator)
        if (line.trim().startsWith("|") && i + 1 < lines.size &&
            Regex("^\\|?\\s*:?-{2,}:?\\s*\\|.*").matches(lines[i + 1].trim())) {
            val tableLines = mutableListOf(line)
            i++
            while (i < lines.size && lines[i].trim().startsWith("|")) { tableLines.add(lines[i]); i++ }
            blocks.add(MarkdownBlock(MarkdownBlockType.TABLE, tableLines.joinToString("\n")))
            continue
        }
        // Heading
        val headingMatch = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line.trim())
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock(MarkdownBlockType.HEADING, text, level = level))
            i++
            continue
        }
        // Horizontal rule
        if (Regex("^(\\s*[-*_]\\s*){3,}$").matches(line.trim())) {
            blocks.add(MarkdownBlock(MarkdownBlockType.SEPARATOR, ""))
            i++
            continue
        }
        // Block quote
        if (line.trim().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock(MarkdownBlockType.QUOTE, quoteLines.joinToString("\n")))
            continue
        }
        // List item
        if (Regex("^\\s*([-*+]|\\d+\\.)\\s+.*$").matches(line)) {
            val listLines = mutableListOf<String>()
            while (i < lines.size && Regex("^\\s*([-*+]|\\d+\\.)\\s+.*$").matches(lines[i])) {
                listLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock(MarkdownBlockType.LIST, listLines.joinToString("\n")))
            continue
        }
        // Empty line - skip
        if (line.isBlank()) { i++; continue }
        // Text paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].trim().startsWith("```") && !lines[i].trim().startsWith("~~~") &&
            !lines[i].trim().startsWith("|") && !lines[i].trim().startsWith("#") &&
            !lines[i].trim().startsWith(">") &&
            !Regex("^\\s*([-*+]|\\d+\\.)\\s+.*$").matches(lines[i]) &&
            !Regex("^(\\s*[-*_]\\s*){3,}$").matches(lines[i].trim())) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock(MarkdownBlockType.TEXT, paraLines.joinToString("\n")))
        }
    }
    return blocks
}

private const val InlineCodeTag = "inline-code"

// Recursive so nested emphasis like **bold *italic*** keeps both spans.
private fun renderInlineMarkdown(text: String, textColor: Color, codeText: Color): AnnotatedString {
    return buildAnnotatedString {
        var pos = 0
        while (pos < text.length) {
            // Inline code `code` — the chip itself is drawn from the layout boxes, not a span background.
            if (text[pos] == '`') {
                val end = text.indexOf('`', pos + 1)
                if (end > pos) {
                    pushStringAnnotation(InlineCodeTag, "")
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeText)) {
                        append(renderInlineMarkdown(text.substring(pos + 1, end), textColor, codeText))
                    }
                    pop()
                    pos = end + 1
                    continue
                }
            }
            // Math arrives as $$…$$ from normalizeLatexMarkdown; drop the markers rather than show them.
            // Italic is the closest a hand-rolled renderer gets to typeset math without breaking line rhythm.
            if (text.startsWith("$$", pos)) {
                val end = text.indexOf("$$", pos + 2)
                if (end > pos + 2) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(rewriteMathScripts(text.substring(pos + 2, end)))
                    }
                    pos = end + 2
                    continue
                }
            }
            // Bold **text**
            if (text.startsWith("**", pos)) {
                val end = text.indexOf("**", pos + 2)
                if (end > pos) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(renderInlineMarkdown(text.substring(pos + 2, end), textColor, codeText))
                    }
                    pos = end + 2
                    continue
                }
            }
            // Italic *text* or _text_
            if ((text[pos] == '*' || text[pos] == '_') && pos + 1 < text.length && text[pos + 1] != '*' && text[pos + 1] != '_') {
                val marker = text[pos]
                val end = text.indexOf(marker, pos + 1)
                if (end > pos && end < text.length && text[end - 1] != ' ') {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(renderInlineMarkdown(text.substring(pos + 1, end), textColor, codeText))
                    }
                    pos = end + 1
                    continue
                }
            }
            // Strikethrough ~~text~~
            if (text.startsWith("~~", pos)) {
                val end = text.indexOf("~~", pos + 2)
                if (end > pos) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(renderInlineMarkdown(text.substring(pos + 2, end), textColor, codeText))
                    }
                    pos = end + 2
                    continue
                }
            }
            // Link [text](url)
            if (text[pos] == '[') {
                val textEnd = text.indexOf(']', pos + 1)
                if (textEnd > pos && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', textEnd + 2)
                    if (urlEnd > textEnd) {
                        val linkText = text.substring(pos + 1, textEnd)
                        val url = text.substring(textEnd + 2, urlEnd)
                        pushStringAnnotation("url", url)
                        append(linkText)
                        pop()
                        pos = urlEnd + 1
                        continue
                    }
                }
            }
            append(text[pos])
            pos++
        }
    }
}

private val mathSuperscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻', '+' to '⁺',
)

private val mathSubscripts = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '-' to '₋', '+' to '₊',
)

// A LaTeX typesetter is out of scope, so digit exponents and subscripts become Unicode glyphs
// and anything the tables cannot cover keeps its source form.
private fun rewriteMathScripts(body: String): String {
    val out = StringBuilder(body.length)
    var index = 0
    while (index < body.length) {
        val table = when (body[index]) {
            '^' -> mathSuperscripts
            '_' -> mathSubscripts
            else -> null
        }
        if (table == null) {
            out.append(body[index])
            index++
            continue
        }
        val braced = body.getOrNull(index + 1) == '{'
        val start = if (braced) index + 2 else index + 1
        val end = if (braced) body.indexOf('}', start) else (start + 1).coerceAtMost(body.length)
        val group = if (end >= start) body.substring(start, end) else ""
        val glyphs = group.mapNotNull { table[it] }
        if (glyphs.isEmpty() || glyphs.size != group.length) {
            out.append(body[index])
            index++
            continue
        }
        glyphs.forEach { out.append(it) }
        index = if (braced) end + 1 else end
    }
    return out.toString()
}

// SpanStyle.background fills the whole line box, which turns inline code into grey bars that
// collide with the line above. Drawing the character layout boxes gives a chip that hugs the glyphs.
private fun Modifier.inlineCodeChips(
    annotated: AnnotatedString,
    layoutResult: () -> TextLayoutResult?,
    chipColor: Color,
): Modifier = drawBehind {
    val layout = layoutResult() ?: return@drawBehind
    val radius = 5.dp.toPx()
    val slack = 2.dp.toPx()
    val lastOffset = (annotated.length - 1).coerceAtLeast(0)
    annotated.getStringAnnotations(InlineCodeTag, 0, annotated.length).forEach { annotation ->
        var line = -1
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = 0f
        var bottom = 0f
        val flush = {
            if (left < right) {
                drawRoundRect(
                    color = chipColor,
                    topLeft = Offset(left - slack, top - slack),
                    size = Size((right - left) + slack * 2f, (bottom - top) + slack * 2f),
                    cornerRadius = CornerRadius(radius),
                )
            }
        }
        for (offset in annotation.start..annotation.end.coerceAtMost(lastOffset)) {
            val currentLine = layout.getLineForOffset(offset)
            if (currentLine != line) {
                flush()
                line = currentLine
                left = Float.MAX_VALUE
                top = Float.MAX_VALUE
                right = 0f
                bottom = 0f
            }
            val box = layout.getBoundingBox(offset)
            left = minOf(left, box.left)
            top = minOf(top, box.top)
            right = maxOf(right, box.right)
            bottom = maxOf(bottom, box.bottom)
        }
        flush()
    }
}

@Composable
private fun MarkdownInlineText(
    annotated: AnnotatedString,
    chipColor: Color,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = modifier.inlineCodeChips(annotated, { layout }, chipColor),
        style = style,
        fontWeight = fontWeight,
        onTextLayout = { layout = it },
    )
}

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, textColor) { renderInlineMarkdown(text, textColor, textColor) }
    var layout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = modifier
            .inlineCodeChips(annotated, { layout }, chipColor)
            .pointerInput(annotated) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { annotation ->
                        runCatching { uriHandler.openUri(annotation.item) }
                    }
                }
            },
        style = style.copy(color = textColor),
        onTextLayout = { layout = it },
    )
}

@Composable
internal fun MarkdownBlockRenderer(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownBlocks(normalizeLatexMarkdown(text)) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val raisedSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeText = MaterialTheme.colorScheme.onSurface
    val tableBorderColor = MaterialTheme.colorScheme.outline
    val quoteBarColor = MaterialTheme.colorScheme.outline
    // One selection scope per reply: long-press copies prose, code and table cells together.
    SelectionContainer {
        Column(modifier) {
            blocks.forEachIndexed { index, block ->
                if (index > 0) {
                    // Headings and rules need more air than plain paragraphs do.
                    val gap = when (block.type) {
                        MarkdownBlockType.HEADING -> 20.dp
                        MarkdownBlockType.SEPARATOR -> 16.dp
                        MarkdownBlockType.TABLE, MarkdownBlockType.CODE -> 16.dp
                        else -> 12.dp
                    }
                    Spacer(Modifier.height(gap))
                }
                when (block.type) {
                    MarkdownBlockType.CODE -> CodeBlockRenderer(block.content, block.language, raisedSurface, codeText)
                    MarkdownBlockType.TABLE -> TableBlockRenderer(block.content, textColor, tableBorderColor, raisedSurface)
                    MarkdownBlockType.HEADING -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        MarkdownText(block.content, style = style)
                    }
                    MarkdownBlockType.QUOTE -> {
                        // Tone alone would not read as a quotation, so the rule stays and the fill does not.
                        Box(Modifier.fillMaxWidth()) {
                            Box(Modifier.matchParentSize()) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(quoteBarColor, RoundedCornerShape(2.dp)))
                            }
                            MarkdownText(
                                block.content,
                                modifier = Modifier.padding(start = 13.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    MarkdownBlockType.LIST -> ListBlockRenderer(block.content, textColor, codeText, raisedSurface)
                    MarkdownBlockType.SEPARATOR -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    MarkdownBlockType.TEXT -> MarkdownText(block.content, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CodeBlockRenderer(code: String, language: String?, bg: Color, textColor: Color) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language?.takeIf { it.isNotBlank() } ?: "代码",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        if (copied) "已复制" else "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Wrapping beats a silent horizontal clip on a phone-width screen.
            // Selection is handled by the reply-wide container, so no nested one here.
            Text(code, style = MonoText, color = textColor)
        }
    }
}

@Composable
private fun TableBlockRenderer(content: String, textColor: Color, dividerColor: Color, chipColor: Color) {
    val lines = content.trim().lines().filter { it.isNotBlank() }
    if (lines.size < 2) { Text(content, color = textColor); return }
    val rows = lines.map { it.trim().trim('|').split("|").map { c -> c.trim() } }
    val header = rows.first()
    val body = rows.drop(2) // skip header + separator
    // Equal weights give a one-character column the same room as a sentence; width by longest cell reads far closer.
    val columnWeights = header.indices.map { column ->
        rows.mapNotNull { it.getOrNull(column) }.maxOf { it.length }.coerceIn(4, 24).toFloat()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, dividerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 9.dp),
            ) {
                header.forEachIndexed { index, cell ->
                    Box(
                        modifier = Modifier.weight(columnWeights.getOrElse(index) { 1f })
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        MarkdownInlineText(
                            renderInlineMarkdown(cell, textColor, textColor),
                            chipColor = chipColor,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = dividerColor)
            body.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    row.forEachIndexed { index, cell ->
                        Box(
                            modifier = Modifier.weight(columnWeights.getOrElse(index) { 1f })
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            MarkdownInlineText(
                                renderInlineMarkdown(cell, textColor, textColor),
                                chipColor = chipColor,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = dividerColor)
            }
        }
    }
}

@Composable
private fun ListBlockRenderer(content: String, textColor: Color, codeText: Color, chipColor: Color) {
    val lines = content.lines()
    var orderedIndex = 0
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val match = Regex("^(\\s*)([-*+]|\\d+\\.)\\s+(.*)$").matchEntire(line)
            if (match != null) {
                val indent = match.groupValues[1].length
                val marker = match.groupValues[2]
                var text = match.groupValues[3]
                val ordered = marker.matches(Regex("\\d+\\."))
                orderedIndex = if (ordered) orderedIndex + 1 else 0
                val checkbox = Regex("^\\[( |x|X)\\]\\s+(.*)$").matchEntire(text)
                val glyph = when {
                    checkbox != null -> if (checkbox.groupValues[1].isNotBlank()) "☑" else "☐"
                    ordered -> "$orderedIndex."
                    else -> "•"
                }
                if (checkbox != null) text = checkbox.groupValues[2]
                val padLeft = (indent / 2 + 1) * 12
                Row(modifier = Modifier.padding(start = padLeft.dp).padding(vertical = 2.dp)) {
                    Text(glyph, style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.width(20.dp))
                    MarkdownInlineText(
                        renderInlineMarkdown(text, textColor, codeText),
                        chipColor = chipColor,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal fun normalizeLatexMarkdown(markdown: String): String {
    if ('$' !in markdown) return markdown
    val output = StringBuilder(markdown.length + 16)
    var index = 0
    var fenceCharacter: Char? = null
    var fenceLength = 0
    var inlineBackticks = 0
    while (index < markdown.length) {
        val character = markdown[index]
        if (character == '`' || character == '~') {
            var runEnd = index + 1
            while (runEnd < markdown.length && markdown[runEnd] == character) runEnd++
            val runLength = runEnd - index
            if (fenceCharacter != null) {
                if (character == fenceCharacter && runLength >= fenceLength) {
                    fenceCharacter = null
                    fenceLength = 0
                }
            } else if (inlineBackticks == 0 && runLength >= 3) {
                fenceCharacter = character
                fenceLength = runLength
            } else if (character == '`') {
                inlineBackticks = when {
                    inlineBackticks == 0 -> runLength
                    inlineBackticks == runLength -> 0
                    else -> inlineBackticks
                }
            }
            output.append(markdown, index, runEnd)
            index = runEnd
            continue
        }
        if (fenceCharacter != null || inlineBackticks > 0 || character != '$' || markdown.isEscapedAt(index)) {
            output.append(character)
            index++
            continue
        }
        if (markdown.startsWith("$$", index)) {
            output.append("$$")
            index += 2
            continue
        }
        if (markdown.getOrNull(index + 1)?.isWhitespace() != false) {
            output.append(character)
            index++
            continue
        }
        var closing = index + 1
        while (closing < markdown.length && markdown[closing] != '\n') {
            if (markdown[closing] == '$' && !markdown.isEscapedAt(closing) &&
                markdown.getOrNull(closing - 1)?.isWhitespace() == false &&
                markdown.getOrNull(closing + 1) != '$'
            ) {
                break
            }
            closing++
        }
        if (closing < markdown.length && markdown[closing] == '$') {
            output.append("$$")
            output.append(markdown, index + 1, closing)
            output.append("$$")
            index = closing + 1
        } else {
            output.append(character)
            index++
        }
    }
    return output.toString()
}

private fun String.isEscapedAt(index: Int): Boolean {
    var slashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        slashes++
        cursor--
    }
    return slashes % 2 == 1
}
