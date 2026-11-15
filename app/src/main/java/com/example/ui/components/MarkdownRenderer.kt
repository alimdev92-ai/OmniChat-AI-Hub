package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeMonoFamily
import com.example.ui.theme.EditorialSerifFamily
import com.example.ui.theme.LocalKairoColors
import kotlinx.coroutines.delay

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class BulletItem(val marker: String, val text: String) : MdBlock
    data class BlockQuote(val text: String) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    useEditorialSerif: Boolean = true
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val bodyFamily = if (useEditorialSerif) EditorialSerifFamily else MaterialTheme.typography.bodyLarge.fontFamily

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 22.sp
                        2 -> 19.sp
                        else -> 17.sp
                    }
                    Text(
                        text = parseInlineMarkdown(
                            block.text,
                            MaterialTheme.colorScheme.primary,
                            LocalKairoColors.current.codeBackground
                        ),
                        style = TextStyle(
                            fontFamily = EditorialSerifFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = size,
                            lineHeight = (size.value + 7).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(
                            block.text,
                            MaterialTheme.colorScheme.primary,
                            LocalKairoColors.current.codeBackground
                        ),
                        style = TextStyle(
                            fontFamily = bodyFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.5.sp,
                            lineHeight = 25.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                is MdBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.marker,
                            style = TextStyle(
                                fontFamily = bodyFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(
                                block.text,
                                MaterialTheme.colorScheme.primary,
                                LocalKairoColors.current.codeBackground
                            ),
                            style = TextStyle(
                                fontFamily = bodyFamily,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.BlockQuote -> {
                    val accent = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = accent,
                                    start = Offset(4.dp.toPx(), 2.dp.toPx()),
                                    end = Offset(4.dp.toPx(), size.height - 2.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = parseInlineMarkdown(
                                block.text,
                                MaterialTheme.colorScheme.primary,
                                LocalKairoColors.current.codeBackground
                            ),
                            style = TextStyle(
                                fontFamily = bodyFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.5.sp,
                                lineHeight = 23.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                is MdBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }

                MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = LocalKairoColors.current.subtleDivider
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    val kairoColors = LocalKairoColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = kairoColors.codeBackground,
        border = BorderStroke(1.dp, kairoColors.codeBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) kairoColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = kairoColors.codeBorder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontFamily = CodeMonoFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val lines = input.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(
                MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2]
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith("> ")) {
            flushParagraph()
            blocks.add(MdBlock.BlockQuote(trimmed.removePrefix("> ")))
            i++
            continue
        }

        val bulletMatch = Regex("^[-*]\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks.add(MdBlock.BulletItem(marker = "•", text = bulletMatch.groupValues[1]))
            i++
            continue
        }

        val orderedMatch = Regex("^(\\d+\\.)\\s+(.+)$").find(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            blocks.add(
                MdBlock.BulletItem(
                    marker = orderedMatch.groupValues[1],
                    text = orderedMatch.groupValues[2]
                )
            )
            i++
            continue
        }

        paragraphBuffer.add(line)
        i++
    }
    flushParagraph()
    return blocks
}

private fun parseInlineMarkdown(
    text: String,
    accentColor: Color,
    codeBgColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            // Markdown Link [label](url)
            if (text[idx] == '[') {
                val closeBracket = text.indexOf(']', idx + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        pushLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = accentColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        )
                        append(label)
                        pop()
                        idx = closeParen + 1
                        continue
                    }
                }
            }

            // Inline code `code`
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = CodeMonoFamily,
                            fontSize = 13.5.sp,
                            background = codeBgColor
                        )
                    ) {
                        append(" ${text.substring(idx + 1, end)} ")
                    }
                    idx = end + 1
                    continue
                }
            }

            // Bold **text**
            if (idx + 1 < text.length && text[idx] == '*' && text[idx + 1] == '*') {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                    continue
                }
            }

            // Italic *text*
            if (text[idx] == '*') {
                val end = text.indexOf('*', idx + 1)
                if (end != -1 && end > idx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                    continue
                }
            }

            append(text[idx])
            idx++
        }
    }
}
