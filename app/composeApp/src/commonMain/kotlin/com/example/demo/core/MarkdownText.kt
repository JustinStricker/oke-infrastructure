package com.example.demo.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed class MdInline {
    data class Text(val text: String) : MdInline()
    data class Bold(val text: String) : MdInline()
    data class Italic(val text: String) : MdInline()
    data class InlineCode(val text: String) : MdInline()
    data class Strikethrough(val text: String) : MdInline()
    data class Link(val text: String, val url: String) : MdInline()
    data class Image(val alt: String, val url: String) : MdInline()
}

private sealed class MdBlock {
    data class Heading(val level: Int, val content: String) : MdBlock()
    data class Paragraph(val content: String) : MdBlock()
    data class Blockquote(val content: String) : MdBlock()
    data class UnorderedList(val items: List<String>) : MdBlock()
    data class OrderedList(val items: List<String>) : MdBlock()
    data class CheckboxList(val items: List<Pair<Boolean, String>>) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data object HorizontalRule : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
}

/** Parse inline markdown into a styled AnnotatedString. */
private fun parseInlineMarkdown(text: String): AnnotatedString {
    val elements = parseInlineElements(text)
    return buildAnnotatedString {
        elements.forEach { element ->
            when (element) {
                is MdInline.Text -> append(element.text)
                is MdInline.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(element.text); pop()
                }
                is MdInline.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(element.text); pop()
                }
                is MdInline.InlineCode -> {
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF2D2D2D),
                        color = Color(0xFF80CBC4)
                    ))
                    append(element.text); pop()
                }
                is MdInline.Strikethrough -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(element.text); pop()
                }
                is MdInline.Link -> {
                    pushStyle(SpanStyle(
                        color = Color(0xFF64B5F6),
                        textDecoration = TextDecoration.Underline
                    ))
                    append(element.text); pop()
                }
                is MdInline.Image -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF9E9E9E)))
                    append("[Image: ${element.alt}]"); pop()
                }
            }
        }
    }
}

private fun parseInlineElements(text: String): List<MdInline> {
    val result = mutableListOf<MdInline>()
    var remaining = text
    val patterns = listOf(
        "code"           to Regex("`([^`]+)`"),
        "image"          to Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)"),
        "link"           to Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"),
        "strikethrough"  to Regex("~~([^~]+)~~"),
        "bold"           to Regex("\\*\\*(.+?)\\*\\*"),
        "italic"         to Regex("\\*(.+?)\\*"),
    )
    while (remaining.isNotEmpty()) {
        var earliestIdx = remaining.length
        var earliestType: String? = null
        var earliestMatch: MatchResult? = null
        for ((type, regex) in patterns) {
            regex.find(remaining)?.let { m ->
                if (m.range.first < earliestIdx) {
                    earliestIdx = m.range.first; earliestType = type; earliestMatch = m
                }
            }
        }
        if (earliestType == null || earliestMatch == null) {
            result.add(MdInline.Text(remaining)); break
        }
        if (earliestIdx > 0) result.add(MdInline.Text(remaining.substring(0, earliestIdx)))
        val g = earliestMatch.groupValues
        when (earliestType) {
            "code"          -> result.add(MdInline.InlineCode(g[1]))
            "image"         -> result.add(MdInline.Image(g[1], g[2]))
            "link"          -> result.add(MdInline.Link(g[1], g[2]))
            "strikethrough" -> result.add(MdInline.Strikethrough(g[1]))
            "bold"          -> result.add(MdInline.Bold(g[1]))
            "italic"        -> result.add(MdInline.Italic(g[1]))
        }
        remaining = remaining.substring(earliestMatch.range.last + 1)
    }
    return result
}

/** Split raw markdown into a list of block-level elements. */
private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.split("\n")
    var i = 0
    val ulPattern = Regex("^[\\-*+]\\s+(.+)$")
    val olPattern = Regex("^(\\d+)\\.\\s+(.+)$")

    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) { i++; continue }

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val code = mutableListOf<String>(); i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.add(lines[i]); i++ }
            i++ // skip ```
            blocks.add(MdBlock.CodeBlock(language, code.joinToString("\n")))
            continue
        }

        // Horizontal rule (---, ***, ___)
        val trimmed = line.trimStart()
        if (trimmed.length >= 3) {
            val nonSpace = trimmed.filter { it != ' ' }
            if (nonSpace.length >= 3 && nonSpace.all { it == nonSpace.first() } && nonSpace.first() in "-_*") {
                blocks.add(MdBlock.HorizontalRule); i++; continue
            }
        }

        // Heading
        val hMatch = Regex("^(#{1,6})\\s+(.+)$").find(line.trimStart())
        if (hMatch != null) { blocks.add(MdBlock.Heading(hMatch.groupValues[1].length, hMatch.groupValues[2])); i++; continue }

        // Blockquote
        if (line.trimStart().startsWith(">")) {
            val q = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) { q.add(lines[i].trimStart().removePrefix(">").trim()); i++ }
            blocks.add(MdBlock.Blockquote(q.joinToString(" "))); continue
        }

        // Unordered list / checkbox
        val ulMatch = ulPattern.find(line.trimStart())
        if (ulMatch != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) { val m = ulPattern.find(lines[i].trimStart()); if (m == null) break; items.add(m.groupValues[1]); i++ }
            val cbRegex = Regex("^\\[([ xX])\\]\\s+(.+)$")
            val cbs = items.map { cbRegex.find(it) }
            if (cbs.all { it != null }) {
                blocks.add(MdBlock.CheckboxList(cbs.map { (it!!.groupValues[1].trim().lowercase() == "x") to it.groupValues[2] }))
            } else {
                blocks.add(MdBlock.UnorderedList(items))
            }
            continue
        }

        // Ordered list
        val olMatch = olPattern.find(line.trimStart())
        if (olMatch != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                if (lines[i].isBlank()) { i++; break }
                val m = olPattern.find(lines[i].trimStart()); if (m == null) break
                items.add(m.groupValues[2]); i++
            }
            blocks.add(MdBlock.OrderedList(items)); continue
        }

        // Table
        if (line.contains("|") && i + 1 < lines.size) {
            val sepLine = lines[i + 1].trim()
            if (sepLine.matches(Regex("^[\\s|:\\-]+$")) && sepLine.contains("-")) {
                val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }; i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains("|")) {
                    val row = lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (row.isNotEmpty()) rows.add(row); i++
                }
                blocks.add(MdBlock.Table(headers, rows)); continue
            }
        }

        // Paragraph
        val para = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank()
            && !lines[i].trimStart().startsWith("```")
            && !lines[i].trimStart().startsWith("#")
            && !lines[i].trimStart().startsWith(">")
            && !ulPattern.matches(lines[i].trimStart())
            && !olPattern.matches(lines[i].trimStart())
            && !(lines[i].trimStart().let { t -> t.length >= 3 && t.filter { it != ' ' }.let { ns -> ns.length >= 3 && ns.all { c -> c in "-_*" } } })
        ) { para.add(lines[i]); i++ }
        if (para.isNotEmpty()) blocks.add(MdBlock.Paragraph(para.joinToString("\n"))) else i++
    }
    return blocks
}

@Composable
fun MarkdownText(text: String) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) { 1 -> MaterialTheme.typography.headlineLarge; 2 -> MaterialTheme.typography.headlineMedium; 3 -> MaterialTheme.typography.headlineSmall; 4 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium }
                    Text(parseInlineMarkdown(block.content), style = style, fontWeight = if (block.level <= 3) FontWeight.Bold else FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                is MdBlock.Paragraph -> Text(parseInlineMarkdown(block.content), style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
                is MdBlock.Blockquote -> Surface(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small) {
                    Text(parseInlineMarkdown(block.content), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is MdBlock.UnorderedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(parseInlineMarkdown(item), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { idx, item ->
                        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(parseInlineMarkdown(item), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is MdBlock.CheckboxList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { (checked, item) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (checked) "☑ " else "☐ ", color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(parseInlineMarkdown(item), style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (checked) TextDecoration.LineThrough else null), color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is MdBlock.CodeBlock -> Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(12.dp)) {
                        if (block.language.isNotEmpty()) Text(block.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                        Text(block.code, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp), color = Color(0xFFD4D4D4))
                    }
                }
                is MdBlock.HorizontalRule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                is MdBlock.Table -> Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(4.dp)) {
                        if (block.headers.isNotEmpty()) {
                            Row(Modifier.padding(vertical = 4.dp)) { block.headers.forEach { h -> Text(parseInlineMarkdown(h), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.primary) } }
                            HorizontalDivider(thickness = 1.dp)
                        }
                        block.rows.forEach { row -> Row(Modifier.padding(vertical = 4.dp)) { row.forEach { c -> Text(parseInlineMarkdown(c), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 2.dp)) } } }
                    }
                }
            }
        }
    }
}