package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.resolveSharedImageModel

sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Header(val text: String, val level: Int) : ContentBlock
    data class Image(val url: String, val alt: String, val widthPercent: Int = 100) : ContentBlock
    data class ListItem(val text: String, val orderedPrefix: String? = null) : ContentBlock
    data class Quote(val text: String) : ContentBlock
    data class Table(val rows: List<List<String>>) : ContentBlock
}

fun parseHtmlOrMarkdownBlocks(
    rawHtmlOrMarkdown: String,
    preserveInlineTags: Boolean = false
): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val tableRegex = """<table[^>]*>([\s\S]*?)</table>""".toRegex(RegexOption.IGNORE_CASE)
    var lastIndex = 0

    for (tableMatch in tableRegex.findAll(rawHtmlOrMarkdown)) {
        val before = rawHtmlOrMarkdown.substring(lastIndex, tableMatch.range.first)
        if (before.isNotBlank()) {
            blocks.addAll(parseNonTableBlocks(before, preserveInlineTags))
        }
        val tableHtml = tableMatch.groupValues[1]
        val rows = """<tr[^>]*>([\s\S]*?)</tr>""".toRegex(RegexOption.IGNORE_CASE)
            .findAll(tableHtml)
            .map { rowMatch ->
                """<t[hd][^>]*>([\s\S]*?)</t[hd]>""".toRegex(RegexOption.IGNORE_CASE)
                    .findAll(rowMatch.groupValues[1])
                    .map { cellMatch ->
                        val withNewlines = cellMatch.groupValues[1]
                            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
                        if (preserveInlineTags) cleanInlinePreservingTags(withNewlines) else cleanHtmlTags(withNewlines)
                    }
                    .toList()
            }
            .filter { it.isNotEmpty() }
            .toList()
        if (rows.isNotEmpty()) {
            blocks.add(ContentBlock.Table(rows))
        }
        lastIndex = tableMatch.range.last + 1
    }

    val tail = rawHtmlOrMarkdown.substring(lastIndex)
    if (tail.isNotBlank()) {
        blocks.addAll(parseNonTableBlocks(tail, preserveInlineTags))
    }
    return blocks
}

private fun parseNonTableBlocks(raw: String, preserveInlineTags: Boolean = false): List<ContentBlock> {
    val cleanInline: (String) -> String = { s ->
        if (preserveInlineTags) cleanInlinePreservingTags(s) else cleanHtmlTags(s)
    }
    val blocks = mutableListOf<ContentBlock>()
    // Normalize block boundaries onto separate lines so single-line HTML (like UpNote/ShineEditor) parses cleanly
    val normalized = raw
        .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
        .replace("</(h[1-6]|p|div|li|blockquote|ul|ol)>".toRegex(RegexOption.IGNORE_CASE), "$0\n")
        .replace("<(h[1-6]|p|div|li|blockquote|img)\\b".toRegex(RegexOption.IGNORE_CASE), "\n<$1")
    val lines = normalized.lines()

    val imgRegex = """<img[^>]+src=["']([^"']+)["'][^>]*>""".toRegex(RegexOption.IGNORE_CASE)
    val mdImgRegex = """!\[([^\]]*)\]\(([^)]+)\)""".toRegex()
    val widthRegex = """width=["']?(\d+)%["']?""".toRegex(RegexOption.IGNORE_CASE)

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.isBlank()) {
            i++
            continue
        }

        // Markdown pipe table detection: | col1 | col2 |
        if (line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2) {
            val tableRows = mutableListOf<List<String>>()
            var j = i
            while (j < lines.size) {
                val tLine = lines[j].trim()
                if (!tLine.startsWith("|") || !tLine.endsWith("|")) break
                val cells = tLine.removePrefix("|").removeSuffix("|").split("|").map { cleanHtmlTags(it) }
                val isSeparator = cells.all { it.isNotBlank() && it.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }
                if (!isSeparator) {
                    tableRows.add(cells)
                }
                j++
            }
            if (tableRows.isNotEmpty()) {
                blocks.add(ContentBlock.Table(tableRows))
                i = j
                continue
            }
        }

        // HTML <img> tags (can be multiple or mixed with text)
        val imgMatches = imgRegex.findAll(line).toList()
        if (imgMatches.isNotEmpty()) {
            imgMatches.forEach { m ->
                val src = m.groupValues[1]
                val wPct = widthRegex.find(m.value)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(20, 100) ?: 100
                blocks.add(ContentBlock.Image(url = src, alt = "Review Image", widthPercent = wPct))
            }
            val textWithoutImg = cleanHtmlTags(line.replace(imgRegex, ""))
            if (textWithoutImg.isNotBlank()) {
                blocks.add(ContentBlock.Paragraph(textWithoutImg))
            }
            i++
            continue
        }

        // Markdown ![alt](url)
        val mdImgMatch = mdImgRegex.find(line)
        if (mdImgMatch != null) {
            blocks.add(ContentBlock.Image(url = mdImgMatch.groupValues[2].trim(), alt = mdImgMatch.groupValues[1].ifBlank { "Review Image" }))
            val rest = cleanHtmlTags(line.replace(mdImgRegex, ""))
            if (rest.isNotBlank()) blocks.add(ContentBlock.Paragraph(rest))
            i++
            continue
        }

        when {
            line.startsWith("### ") -> blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("### ")), 3))
            line.startsWith("## ") -> blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("## ")), 2))
            line.startsWith("# ") -> blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("# ")), 1))
            line.startsWith("<h1", ignoreCase = true) -> cleanHtmlTags(line).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.Header(it, 1)) }
            line.startsWith("<h2", ignoreCase = true) -> cleanHtmlTags(line).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.Header(it, 2)) }
            line.startsWith("<h3", ignoreCase = true) -> cleanHtmlTags(line).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.Header(it, 3)) }
            line.startsWith("<blockquote", ignoreCase = true) || line.startsWith("> ") -> {
                cleanInline(line.removePrefix("> ")).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.Quote(it)) }
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                cleanInline(line.substring(2)).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.ListItem(it)) }
            }
            Regex("""^\d+\.\s+""").containsMatchIn(line) -> {
                val m = Regex("""^(\d+\.)\s+(.*)$""").find(line)
                if (m != null) {
                    val itemText = cleanInline(m.groupValues[2])
                    if (itemText.isNotBlank()) blocks.add(ContentBlock.ListItem(itemText, orderedPrefix = m.groupValues[1]))
                }
            }
            line.startsWith("<li", ignoreCase = true) -> cleanInline(line).takeIf { it.isNotBlank() }?.let { blocks.add(ContentBlock.ListItem(it)) }
            else -> {
                val cleaned = cleanInline(line)
                if (cleaned.isNotBlank()) blocks.add(ContentBlock.Paragraph(cleaned))
            }
        }
        i++
    }
    return blocks
}

private fun cleanHtmlTags(html: String): String {
    return html.replace("<[^>]*>".toRegex(), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
}

private fun cleanInlinePreservingTags(html: String): String {
    // Strip block/wrapper tags while preserving inline formatting & <a> links for AnnotatedString parsing
    return html.replace("""</?(?!a\b|b\b|strong\b|i\b|em\b|u\b|s\b|strike\b|del\b)[a-zA-Z0-9]+\b[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "")
        .replace("&nbsp;", " ")
        .trim()
}

@Composable
fun CrossPlatformReviewRenderer(
    content: String,
    zoomPercent: Int = 100,
    onImageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val blocks = parseHtmlOrMarkdownBlocks(content, preserveInlineTags = true)
    val scale = (zoomPercent / 100f).coerceIn(0.6f, 1.6f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Header -> {
                    val baseSp = when (block.level) {
                        1 -> 21f
                        2 -> 18f
                        else -> 16f
                    }
                    Text(
                        text = block.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseSp * scale).sp,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                is ContentBlock.Paragraph -> {
                    Text(
                        text = parseSharedRichTextToAnnotatedString(
                            block.text,
                            baseColor = Color(0xFF1E293B),
                            linkColor = Color(0xFF2563EB)
                        ),
                        fontSize = (14f * scale).sp,
                        lineHeight = (21f * scale).sp
                    )
                }
                is ContentBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(Color(0xFF4F46E5))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = parseSharedRichTextToAnnotatedString(
                                block.text,
                                baseColor = Color(0xFF475569),
                                linkColor = Color(0xFF2563EB)
                            ),
                            fontSize = (13.5f * scale).sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                is ContentBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = if (block.orderedPrefix != null) "${block.orderedPrefix}  " else "•  ",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5),
                            fontSize = (14f * scale).sp
                        )
                        Text(
                            text = parseSharedRichTextToAnnotatedString(
                                block.text,
                                baseColor = Color(0xFF1E293B),
                                linkColor = Color(0xFF2563EB)
                            ),
                            fontSize = (14f * scale).sp,
                            lineHeight = (20f * scale).sp
                        )
                    }
                }
                is ContentBlock.Image -> {
                    val resolved = resolveSharedImageModel(block.url, block.url.substringAfterLast('/'))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = resolved,
                            contentDescription = block.alt,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(block.widthPercent / 100f)
                                .heightIn(min = 140.dp, max = 380.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .clickable { onImageClick(block.url) }
                        )
                    }
                }
                is ContentBlock.Table -> {
                    val colCount = block.rows.maxOfOrNull { it.size } ?: 1
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val fitsWithoutScroll = maxWidth >= (colCount * 76).dp
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            color = Color.White,
                            modifier = if (fitsWithoutScroll) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            }
                        ) {
                            Column(modifier = if (fitsWithoutScroll) Modifier.fillMaxWidth() else Modifier) {
                                block.rows.forEachIndexed { rIdx, row ->
                                    Row(
                                        modifier = (if (fitsWithoutScroll) Modifier.fillMaxWidth() else Modifier)
                                            .height(IntrinsicSize.Min)
                                            .background(if (rIdx == 0) Color(0xFFF8FAFC) else Color.White)
                                    ) {
                                        for (cIdx in 0 until colCount) {
                                            val cell = row.getOrElse(cIdx) { "" }
                                            Box(
                                                modifier = (if (fitsWithoutScroll) Modifier.weight(1f) else Modifier.widthIn(min = 90.dp, max = 170.dp))
                                                    .fillMaxHeight()
                                                    .border(0.5.dp, Color(0xFFE2E8F0))
                                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = parseSharedRichTextToAnnotatedString(
                                                        cell,
                                                        baseColor = Color(0xFF0F172A),
                                                        linkColor = Color(0xFF2563EB)
                                                    ),
                                                    fontSize = (12.5f * scale).sp,
                                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
