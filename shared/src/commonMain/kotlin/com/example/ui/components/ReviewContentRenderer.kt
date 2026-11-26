package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Header(val text: String, val level: Int) : ContentBlock
    data class Image(val url: String, val alt: String) : ContentBlock
    data class ListItem(val text: String) : ContentBlock
}

fun parseHtmlOrMarkdownBlocks(rawHtmlOrMarkdown: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = rawHtmlOrMarkdown
        .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
        .replace("</p>".toRegex(RegexOption.IGNORE_CASE), "\n\n")
        .replace("<p[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
        .replace("</div>".toRegex(RegexOption.IGNORE_CASE), "\n")
        .replace("<div[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
        .lines()

    val imgRegex = "<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>".toRegex(RegexOption.IGNORE_CASE)

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isBlank()) continue

        val imgMatch = imgRegex.find(line)
        if (imgMatch != null) {
            val src = imgMatch.groupValues[1]
            blocks.add(ContentBlock.Image(url = src, alt = "Review Image"))
            val textWithoutImg = line.replace(imgRegex, "").trim()
            if (textWithoutImg.isNotBlank()) {
                blocks.add(ContentBlock.Paragraph(cleanHtmlTags(textWithoutImg)))
            }
            continue
        }

        if (line.startsWith("### ")) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("### ")), 3))
        } else if (line.startsWith("## ")) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("## ")), 2))
        } else if (line.startsWith("# ")) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line.removePrefix("# ")), 1))
        } else if (line.startsWith("<h1>", ignoreCase = true)) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line), 1))
        } else if (line.startsWith("<h2>", ignoreCase = true)) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line), 2))
        } else if (line.startsWith("<h3>", ignoreCase = true)) {
            blocks.add(ContentBlock.Header(cleanHtmlTags(line), 3))
        } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
            blocks.add(ContentBlock.ListItem(cleanHtmlTags(line.substring(2))))
        } else if (line.startsWith("<li>", ignoreCase = true)) {
            blocks.add(ContentBlock.ListItem(cleanHtmlTags(line)))
        } else {
            blocks.add(ContentBlock.Paragraph(cleanHtmlTags(line)))
        }
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
        .trim()
}

@Composable
fun CrossPlatformReviewRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = parseHtmlOrMarkdownBlocks(content)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(blocks.size) { index ->
            when (val block = blocks[index]) {
                is ContentBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                is ContentBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is ContentBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is ContentBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
