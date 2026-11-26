package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.data.CrossPlatformBackupManager
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.resolveSharedImageModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class TextBlockKind { P, H1, H2, H3, BULLET, NUMBERED, QUOTE }

private sealed class DocBlock {
    abstract val id: String

    data class TextBlock(
        override val id: String,
        val kind: TextBlockKind = TextBlockKind.P,
        val text: String = "",
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val spans: List<SharedFormatSpan> = emptyList()
    ) : DocBlock()

    data class ImageBlock(
        override val id: String,
        val url: String,
        val widthPercent: Int = 100
    ) : DocBlock()

    data class TableBlock(
        override val id: String,
        val rows: List<List<String>>
    ) : DocBlock()
}

private fun nextBlockId(): String = "blk_${Random.nextLong(100000, 999999)}"

private fun htmlToDocBlocks(html: String): List<DocBlock> {
    if (html.isBlank()) return listOf(DocBlock.TextBlock(id = nextBlockId()))
    val parsed = parseHtmlOrMarkdownBlocks(html, preserveInlineTags = true)
    if (parsed.isEmpty()) return listOf(DocBlock.TextBlock(id = nextBlockId()))
    return parsed.map { cb ->
        when (cb) {
            is ContentBlock.Header -> {
                val (plain, spans) = parseHtmlToRichText(cb.text)
                DocBlock.TextBlock(
                    id = nextBlockId(),
                    kind = when (cb.level) {
                        1 -> TextBlockKind.H1
                        2 -> TextBlockKind.H2
                        else -> TextBlockKind.H3
                    },
                    text = plain.trimEnd('\r', '\n'),
                    spans = spans
                )
            }
            is ContentBlock.Quote -> {
                val (plain, spans) = parseHtmlToRichText(cb.text)
                DocBlock.TextBlock(id = nextBlockId(), kind = TextBlockKind.QUOTE, text = plain.trimEnd('\r', '\n'), spans = spans)
            }
            is ContentBlock.ListItem -> {
                val (plain, spans) = parseHtmlToRichText(cb.text)
                DocBlock.TextBlock(
                    id = nextBlockId(),
                    kind = if (cb.orderedPrefix != null) TextBlockKind.NUMBERED else TextBlockKind.BULLET,
                    text = plain.trimEnd('\r', '\n'),
                    spans = spans
                )
            }
            is ContentBlock.Paragraph -> {
                val (plain, spans) = parseHtmlToRichText(cb.text)
                DocBlock.TextBlock(id = nextBlockId(), kind = TextBlockKind.P, text = plain.trimEnd('\r', '\n'), spans = spans)
            }
            is ContentBlock.Image -> DocBlock.ImageBlock(id = nextBlockId(), url = cb.url, widthPercent = cb.widthPercent)
            is ContentBlock.Table -> DocBlock.TableBlock(
                id = nextBlockId(),
                rows = cb.rows.map { row ->
                    row.map { cell ->
                        cell.replace("<[^>]*>".toRegex(), "")
                            .replace("&nbsp;", " ")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .trim()
                    }
                }
            )
        }
    }
}

private fun formatTextBlockHtml(b: DocBlock.TextBlock): String {
    var t = if (b.spans.isNotEmpty()) {
        richTextToHtml(b.text, b.spans)
    } else {
        b.text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
    if (b.isBold) t = "<b>$t</b>"
    if (b.isItalic) t = "<i>$t</i>"
    if (b.isUnderline) t = "<u>$t</u>"
    return t.replace("\n", "<br>")
}

private fun docBlocksToHtml(blocks: List<DocBlock>): String {
    val sb = StringBuilder()
    var i = 0
    while (i < blocks.size) {
        when (val b = blocks[i]) {
            is DocBlock.TextBlock -> {
                val inner = formatTextBlockHtml(b)
                when (b.kind) {
                    TextBlockKind.H1 -> sb.append("<h1>$inner</h1>")
                    TextBlockKind.H2 -> sb.append("<h2>$inner</h2>")
                    TextBlockKind.H3 -> sb.append("<h3>$inner</h3>")
                    TextBlockKind.QUOTE -> sb.append("<blockquote>$inner</blockquote>")
                    TextBlockKind.BULLET -> {
                        sb.append("<ul>")
                        while (i < blocks.size && (blocks[i] as? DocBlock.TextBlock)?.kind == TextBlockKind.BULLET) {
                            val item = blocks[i] as DocBlock.TextBlock
                            sb.append("<li>${formatTextBlockHtml(item)}</li>")
                            i++
                        }
                        sb.append("</ul>")
                        continue
                    }
                    TextBlockKind.NUMBERED -> {
                        sb.append("<ol>")
                        while (i < blocks.size && (blocks[i] as? DocBlock.TextBlock)?.kind == TextBlockKind.NUMBERED) {
                            val item = blocks[i] as DocBlock.TextBlock
                            sb.append("<li>${formatTextBlockHtml(item)}</li>")
                            i++
                        }
                        sb.append("</ol>")
                        continue
                    }
                    TextBlockKind.P -> sb.append("<div>$inner</div>")
                }
            }
            is DocBlock.ImageBlock -> {
                sb.append("""<img width="${b.widthPercent}%" src="${b.url}" />""")
            }
            is DocBlock.TableBlock -> {
                sb.append("<table><tbody>")
                b.rows.forEachIndexed { rIdx, row ->
                    sb.append("<tr>")
                    row.forEach { cell ->
                        val tag = if (rIdx == 0) "th" else "td"
                        sb.append("<$tag>${cell.replace("\n", "<br>")}</$tag>")
                    }
                    sb.append("</tr>")
                }
                sb.append("</tbody></table>")
            }
        }
        i++
    }
    return sb.toString()
}

@Composable
fun SharedBlockReviewEditor(
    existingReview: ReviewItem?,
    products: List<ProductItem>,
    allLibraryPictures: List<PictureItem>,
    nextReviewNumber: Int,
    preselectedProductId: String?,
    onSave: (ReviewItem) -> Unit,
    onCancel: () -> Unit,
    onOpenLightbox: (List<PictureItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(existingReview?.title ?: "") }
    var authorName by remember { mutableStateOf(existingReview?.authorName ?: "Verified Reviewer") }
    var rating by remember { mutableIntStateOf(existingReview?.rating ?: 5) }
    var selectedProductId by remember {
        mutableStateOf(
            existingReview?.productIds?.firstOrNull()
                ?: preselectedProductId
                ?: products.firstOrNull()?.id
        )
    }

    val blocks = remember(existingReview?.id) {
        mutableStateListOf<DocBlock>().apply {
            addAll(htmlToDocBlocks(existingReview?.body.orEmpty()))
        }
    }

    var focusedBlockIndex by remember { mutableIntStateOf(0) }
    val blockSelections = remember { mutableStateMapOf<String, TextRange>() }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var activeTableCell by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) } // blockIdx, row, col

    var zoomPercent by rememberSaveable { mutableIntStateOf(85) }
    var showTableMenu by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showProductDropdown by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var showAppLibraryDialog by remember { mutableStateOf(false) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Multiple()
    ) { files ->
        files?.forEach { pf ->
            scope.launch {
                runCatching {
                    val pic = CrossPlatformBackupManager.savePictureBytesToStorage(pf.readBytes(), pf.name)
                    val insertIdx = (focusedBlockIndex + 1).coerceIn(0, blocks.size)
                    val newImg = DocBlock.ImageBlock(id = nextBlockId(), url = "file://${pic.url}", widthPercent = 100)
                    blocks.add(insertIdx, newImg)
                    blocks.add(insertIdx + 1, DocBlock.TextBlock(id = nextBlockId()))
                    selectedImageId = newImg.id
                    focusedBlockIndex = insertIdx + 1
                }
            }
        }
    }

    fun insertImageUrlAtCursor(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        val insertIdx = (focusedBlockIndex + 1).coerceIn(0, blocks.size)
        val newImg = DocBlock.ImageBlock(id = nextBlockId(), url = clean, widthPercent = 100)
        blocks.add(insertIdx, newImg)
        blocks.add(insertIdx + 1, DocBlock.TextBlock(id = nextBlockId()))
        selectedImageId = newImg.id
        focusedBlockIndex = insertIdx + 1
    }

    fun insertTableAtCursor(rows: Int, cols: Int) {
        val insertIdx = (focusedBlockIndex + 1).coerceIn(0, blocks.size)
        val initialRows = List(rows) { r ->
            List(cols) { c -> if (r == 0) "Header ${c + 1}" else "" }
        }
        blocks.add(insertIdx, DocBlock.TableBlock(id = nextBlockId(), rows = initialRows))
        blocks.add(insertIdx + 1, DocBlock.TextBlock(id = nextBlockId()))
        activeTableCell = Triple(insertIdx, 0, 0)
        focusedBlockIndex = insertIdx
    }

    fun toggleInlineStyle(styleType: String) {
        val b = blocks.getOrNull(focusedBlockIndex) as? DocBlock.TextBlock ?: return
        val sel = blockSelections[b.id] ?: TextRange(b.text.length)
        if (!sel.collapsed && sel.length > 0) {
            val s = sel.min.coerceIn(0, b.text.length)
            val e = sel.max.coerceIn(0, b.text.length)
            if (s < e) {
                val hasStyle = b.spans.any { sp ->
                    sp.start <= s && sp.end >= e && when (styleType) {
                        "bold" -> sp.isBold
                        "italic" -> sp.isItalic
                        "underline" -> sp.isUnderline
                        else -> false
                    }
                }
                val updatedSpans = if (hasStyle) {
                    b.spans.mapNotNull { sp ->
                        if (sp.start < e && sp.end > s) {
                            val modified = when (styleType) {
                                "bold" -> sp.copy(isBold = false)
                                "italic" -> sp.copy(isItalic = false)
                                "underline" -> sp.copy(isUnderline = false)
                                else -> sp
                            }
                            if (!modified.isBold && !modified.isItalic && !modified.isUnderline && !modified.isStrike && modified.url == null) null else modified
                        } else sp
                    }
                } else {
                    b.spans + SharedFormatSpan(
                        start = s,
                        end = e,
                        isBold = styleType == "bold",
                        isItalic = styleType == "italic",
                        isUnderline = styleType == "underline"
                    )
                }
                blocks[focusedBlockIndex] = b.copy(spans = updatedSpans)
                return
            }
        }
        blocks[focusedBlockIndex] = when (styleType) {
            "bold" -> b.copy(isBold = !b.isBold)
            "italic" -> b.copy(isItalic = !b.isItalic)
            "underline" -> b.copy(isUnderline = !b.isUnderline)
            else -> b
        }
    }

    fun toggleBlockKindAtCursor(targetKind: TextBlockKind) {
        val idx = focusedBlockIndex
        val b = blocks.getOrNull(idx) as? DocBlock.TextBlock ?: return
        val newKind = if (b.kind == targetKind) TextBlockKind.P else targetKind
        val sel = blockSelections[b.id] ?: TextRange(b.text.length)
        if (!b.text.contains('\n')) {
            blocks[idx] = b.copy(kind = newKind)
            return
        }
        val cursor = sel.start.coerceIn(0, b.text.length)
        val lineStart = b.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1 || cursor == 0) 0 else it + 1 }
        val lineEnd = b.text.indexOf('\n', cursor).let { if (it == -1) b.text.length else it }

        val beforeText = if (lineStart > 0) b.text.substring(0, lineStart - 1) else ""
        val currentText = b.text.substring(lineStart, lineEnd)
        val afterText = if (lineEnd < b.text.length) b.text.substring(lineEnd + 1) else ""

        val replacement = mutableListOf<DocBlock.TextBlock>()
        var targetOffset = 0
        if (beforeText.isNotEmpty()) {
            replacement.add(b.copy(id = nextBlockId(), text = beforeText, spans = emptyList()))
            targetOffset = 1
        }
        val currentBlock = b.copy(id = b.id, kind = newKind, text = currentText, spans = emptyList())
        replacement.add(currentBlock)
        if (afterText.isNotEmpty()) {
            replacement.add(b.copy(id = nextBlockId(), text = afterText, spans = emptyList()))
        }
        blocks.removeAt(idx)
        blocks.addAll(idx, replacement)
        val newIdx = idx + targetOffset
        focusedBlockIndex = newIdx
        val relCursor = (cursor - lineStart).coerceIn(0, currentText.length)
        blockSelections[currentBlock.id] = TextRange(relCursor)
    }

    fun updateActiveTextBlock(transform: (DocBlock.TextBlock) -> DocBlock.TextBlock) {
        val idx = focusedBlockIndex.coerceIn(0, blocks.lastIndex.coerceAtLeast(0))
        val cur = blocks.getOrNull(idx)
        if (cur is DocBlock.TextBlock) {
            blocks[idx] = transform(cur)
        } else {
            val newBlock = transform(DocBlock.TextBlock(id = nextBlockId()))
            val insertIdx = (idx + 1).coerceIn(0, blocks.size)
            blocks.add(insertIdx, newBlock)
            focusedBlockIndex = insertIdx
        }
    }

    if (showUrlInputDialog) {
        AlertDialog(
            onDismissRequest = { showUrlInputDialog = false },
            title = { Text("Insert Image URL", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = urlInputText,
                    onValueChange = { urlInputText = it },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        insertImageUrlAtCursor(urlInputText)
                        urlInputText = ""
                        showUrlInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) { Text("Insert", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showUrlInputDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAppLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showAppLibraryDialog = false },
            title = { Text("App Photo Library", fontWeight = FontWeight.Bold) },
            text = {
                if (allLibraryPictures.isEmpty()) {
                    Text("No photos in library.", color = Color(0xFF64748B))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allLibraryPictures) { pic ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        insertImageUrlAtCursor(pic.url)
                                        showAppLibraryDialog = false
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = resolveSharedImageModel(pic.url, pic.name),
                                    contentDescription = pic.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9))
                                )
                                Text(pic.name.ifBlank { pic.url.substringAfterLast('/') }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAppLibraryDialog = false }) { Text("Close") } }
        )
    }

    val selectedProduct = products.find { it.id == selectedProductId }
    val activeTextBlock = blocks.getOrNull(focusedBlockIndex) as? DocBlock.TextBlock

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF1F5F9))) {
        // 1. FIXED TOP HEADER BAR (Compact)
        Surface(color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (existingReview == null) "Write Review" else "Edit Review",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        (1..5).forEach { star ->
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Rate $star",
                                tint = if (star <= rating) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp).clickable { rating = star }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val htmlBody = docBlocksToHtml(blocks)
                            val inlinePics = blocks.filterIsInstance<DocBlock.ImageBlock>().mapIndexed { idx, img ->
                                PictureItem(
                                    id = "rev_pic_$idx",
                                    name = img.url.substringAfterLast('/').substringBefore('?'),
                                    url = img.url.removePrefix("file://")
                                )
                            }
                            val saved = ReviewItem(
                                id = existingReview?.id ?: "rev_${Random.nextLong(100000000, 999999999)}",
                                reviewNumber = existingReview?.reviewNumber ?: nextReviewNumber,
                                title = title.trim().ifBlank { "${selectedProduct?.name ?: "Product"} Review" },
                                body = htmlBody,
                                authorName = authorName.trim().ifBlank { "Verified Reviewer" },
                                rating = rating,
                                productIds = if (selectedProduct != null) listOf(selectedProduct.id) else emptyList(),
                                productTitles = if (selectedProduct != null) listOf(selectedProduct.name) else emptyList(),
                                pictures = inlinePics,
                                createdTime = existingReview?.createdTime ?: ""
                            )
                            onSave(saved)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Save Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. FIXED WORD-STYLE FORMATTING TOOLBAR (Pinned above scroll area!)
        Surface(color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolbarPill("H1", activeTextBlock?.kind == TextBlockKind.H1) {
                    toggleBlockKindAtCursor(TextBlockKind.H1)
                }
                ToolbarPill("H2", activeTextBlock?.kind == TextBlockKind.H2) {
                    toggleBlockKindAtCursor(TextBlockKind.H2)
                }
                ToolbarPill("H3", activeTextBlock?.kind == TextBlockKind.H3) {
                    toggleBlockKindAtCursor(TextBlockKind.H3)
                }

                VerticalDivider(modifier = Modifier.height(22.dp), color = Color(0xFFE2E8F0))

                ToolbarPill("B", activeTextBlock?.isBold == true, fontWeight = FontWeight.Black) {
                    toggleInlineStyle("bold")
                }
                ToolbarPill("I", activeTextBlock?.isItalic == true, fontStyle = FontStyle.Italic) {
                    toggleInlineStyle("italic")
                }
                ToolbarPill("U", activeTextBlock?.isUnderline == true, underline = true) {
                    toggleInlineStyle("underline")
                }

                VerticalDivider(modifier = Modifier.height(22.dp), color = Color(0xFFE2E8F0))

                ToolbarIconPill(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet List", activeTextBlock?.kind == TextBlockKind.BULLET) {
                    toggleBlockKindAtCursor(TextBlockKind.BULLET)
                }
                ToolbarIconPill(Icons.Default.FormatListNumbered, "Numbered List", activeTextBlock?.kind == TextBlockKind.NUMBERED) {
                    toggleBlockKindAtCursor(TextBlockKind.NUMBERED)
                }
                ToolbarIconPill(Icons.Default.FormatQuote, "Quote", activeTextBlock?.kind == TextBlockKind.QUOTE) {
                    toggleBlockKindAtCursor(TextBlockKind.QUOTE)
                }

                VerticalDivider(modifier = Modifier.height(22.dp), color = Color(0xFFE2E8F0))

                // Table Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTableCell != null) Color(0xFFEEF2FF) else Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.clickable { showTableMenu = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = "Table", modifier = Modifier.size(15.dp), tint = Color(0xFF334155))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF64748B))
                        }
                    }
                    DropdownMenu(expanded = showTableMenu, onDismissRequest = { showTableMenu = false }) {
                        DropdownMenuItem(text = { Text("Insert 2x2 Table") }, onClick = { showTableMenu = false; insertTableAtCursor(2, 2) })
                        DropdownMenuItem(text = { Text("Insert 3x3 Table") }, onClick = { showTableMenu = false; insertTableAtCursor(3, 3) })
                        DropdownMenuItem(
                            text = { Text("Add Row Below") },
                            enabled = activeTableCell != null,
                            onClick = {
                                showTableMenu = false
                                activeTableCell?.let { (bIdx, rIdx, _) ->
                                    val tbl = blocks.getOrNull(bIdx) as? DocBlock.TableBlock ?: return@let
                                    val cols = tbl.rows.firstOrNull()?.size ?: 2
                                    val updated = tbl.rows.toMutableList()
                                    updated.add((rIdx + 1).coerceAtMost(updated.size), List(cols) { "" })
                                    blocks[bIdx] = tbl.copy(rows = updated)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Column Right") },
                            enabled = activeTableCell != null,
                            onClick = {
                                showTableMenu = false
                                activeTableCell?.let { (bIdx, _, cIdx) ->
                                    val tbl = blocks.getOrNull(bIdx) as? DocBlock.TableBlock ?: return@let
                                    val updated = tbl.rows.mapIndexed { r, row ->
                                        row.toMutableList().apply { add((cIdx + 1).coerceAtMost(size), if (r == 0) "Header" else "") }
                                    }
                                    blocks[bIdx] = tbl.copy(rows = updated)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Row", color = Color(0xFFDC2626)) },
                            enabled = activeTableCell != null,
                            onClick = {
                                showTableMenu = false
                                activeTableCell?.let { (bIdx, rIdx, _) ->
                                    val tbl = blocks.getOrNull(bIdx) as? DocBlock.TableBlock ?: return@let
                                    if (tbl.rows.size > 1) {
                                        val updated = tbl.rows.toMutableList().apply { removeAt(rIdx) }
                                        blocks[bIdx] = tbl.copy(rows = updated)
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Column", color = Color(0xFFDC2626)) },
                            enabled = activeTableCell != null,
                            onClick = {
                                showTableMenu = false
                                activeTableCell?.let { (bIdx, _, cIdx) ->
                                    val tbl = blocks.getOrNull(bIdx) as? DocBlock.TableBlock ?: return@let
                                    if ((tbl.rows.firstOrNull()?.size ?: 0) > 1) {
                                        val updated = tbl.rows.map { row -> row.toMutableList().apply { if (cIdx < size) removeAt(cIdx) } }
                                        blocks[bIdx] = tbl.copy(rows = updated)
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Table", color = Color(0xFFDC2626)) },
                            enabled = activeTableCell != null,
                            onClick = {
                                showTableMenu = false
                                activeTableCell?.let { (bIdx, _, _) ->
                                    if (bIdx in blocks.indices) blocks.removeAt(bIdx)
                                    activeTableCell = null
                                }
                            }
                        )
                    }
                }

                // Insert Photo Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.clickable { showPhotoMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF4F46E5))
                            Text("Insert Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF4F46E5))
                        }
                    }
                    DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF4F46E5)) },
                            text = { Text("Browse Files & Storage", fontWeight = FontWeight.SemiBold) },
                            onClick = { showPhotoMenu = false; imagePicker.launch() }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF10B981)) },
                            text = { Text("From App Photo Library", fontWeight = FontWeight.SemiBold) },
                            onClick = { showPhotoMenu = false; showAppLibraryDialog = true }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFD97706)) },
                            text = { Text("Enter Image URL", fontWeight = FontWeight.SemiBold) },
                            onClick = { showPhotoMenu = false; showUrlInputDialog = true }
                        )
                    }
                }

                ToolbarIconPill(Icons.Default.FormatClear, "Clear Formatting", false) {
                    updateActiveTextBlock { it.copy(kind = TextBlockKind.P, isBold = false, isItalic = false, isUnderline = false) }
                }

                // Word-style Zoom [- 85% +]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        IconButton(onClick = { zoomPercent = (zoomPercent - 10).coerceAtLeast(50) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(13.dp))
                        }
                        Text("$zoomPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { zoomPercent = (zoomPercent + 10).coerceAtMost(150) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        // 3. FIXED METADATA ROW (Compact: Product, Title, Author)
        Surface(color = Color(0xFFF8FAFC), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.height(34.dp).clickable { showProductDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(14.dp))
                            Text(
                                text = selectedProduct?.name ?: "Select product",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = showProductDropdown, onDismissRequest = { showProductDropdown = false }) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text(prod.name, fontWeight = if (prod.id == selectedProductId) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { selectedProductId = prod.id; showProductDropdown = false }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.weight(1.5f).height(34.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A)),
                            cursorBrush = SolidColor(Color(0xFF4F46E5)),
                            modifier = Modifier.fillMaxWidth()
                        ) { inner ->
                            if (title.isEmpty()) Text("Review title...", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            inner()
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        BasicTextField(
                            value = authorName,
                            onValueChange = { authorName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A)),
                            cursorBrush = SolidColor(Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f)
                        ) { inner ->
                            if (authorName.isEmpty()) Text("Author", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            inner()
                        }
                    }
                }
            }
        }

        // 4. SCROLLABLE CENTERED DOCUMENT PAGE (Word-style Zoom)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.TopCenter
        ) {
            val pageWidth = (maxWidth * (zoomPercent / 100f)).coerceAtLeast(360.dp)
            val textScale = (zoomPercent / 100f).coerceIn(0.6f, 1.5f)

            LazyColumn(
                modifier = Modifier.widthIn(max = pageWidth).fillMaxHeight().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            var numberedCounter = 0
                            blocks.forEachIndexed { bIdx, block ->
                                if ((block as? DocBlock.TextBlock)?.kind == TextBlockKind.NUMBERED) {
                                    numberedCounter++
                                } else {
                                    numberedCounter = 0
                                }

                                when (block) {
                                    is DocBlock.TextBlock -> {
                                        val fontSp = when (block.kind) {
                                            TextBlockKind.H1 -> 22f * textScale
                                            TextBlockKind.H2 -> 18f * textScale
                                            TextBlockKind.H3 -> 16f * textScale
                                            else -> 14f * textScale
                                        }
                                        val weight = when {
                                            block.kind in listOf(TextBlockKind.H1, TextBlockKind.H2, TextBlockKind.H3) -> FontWeight.Bold
                                            block.isBold -> FontWeight.Bold
                                            else -> FontWeight.Normal
                                        }
                                        val prefix = when (block.kind) {
                                            TextBlockKind.BULLET -> "•  "
                                            TextBlockKind.NUMBERED -> "$numberedCounter.  "
                                            else -> ""
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (block.kind == TextBlockKind.QUOTE) {
                                                        Modifier
                                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                                            .padding(10.dp)
                                                    } else Modifier
                                                )
                                        ) {
                                            val lineHeightSp = (fontSp * 1.4f).sp
                                            if (prefix.isNotEmpty()) {
                                                Text(
                                                    text = prefix,
                                                    fontSize = fontSp.sp,
                                                    lineHeight = lineHeightSp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4F46E5),
                                                    modifier = Modifier.alignByBaseline()
                                                )
                                            }
                                            var isBlockFocused by remember(block.id) { mutableStateOf(false) }
                                            val currentSelection = blockSelections[block.id] ?: TextRange(block.text.length)
                                            val tfv = TextFieldValue(
                                                text = block.text,
                                                selection = TextRange(
                                                    currentSelection.start.coerceIn(0, block.text.length),
                                                    currentSelection.end.coerceIn(0, block.text.length)
                                                )
                                            )
                                            BasicTextField(
                                                value = tfv,
                                                onValueChange = { newTfv ->
                                                    val newVal = newTfv.text
                                                    if (isBlockFocused || !newTfv.selection.collapsed || newVal != block.text) {
                                                        blockSelections[block.id] = newTfv.selection
                                                    }
                                                    // Auto-continue bullet/numbered list on Enter
                                                    if ((block.kind == TextBlockKind.BULLET || block.kind == TextBlockKind.NUMBERED) && newVal.endsWith("\n") && newVal.length > block.text.length) {
                                                        val trimmed = newVal.removeSuffix("\n")
                                                        blocks[bIdx] = block.copy(text = trimmed)
                                                        val nextKind = if (trimmed.isBlank()) TextBlockKind.P else block.kind
                                                        val nextBlock = DocBlock.TextBlock(id = nextBlockId(), kind = nextKind)
                                                        blocks.add(bIdx + 1, nextBlock)
                                                        focusedBlockIndex = bIdx + 1
                                                    } else {
                                                        val adjustedSpans = if (newVal != block.text && block.spans.isNotEmpty()) {
                                                            adjustSpansOnTextChange(block.text, newVal, block.spans)
                                                        } else block.spans
                                                        blocks[bIdx] = block.copy(text = newVal, spans = adjustedSpans)
                                                    }
                                                },
                                                visualTransformation = VisualTransformation { text ->
                                                    TransformedText(
                                                        if (block.spans.isNotEmpty()) buildFormattedAnnotatedString(text.text, block.spans) else text,
                                                        OffsetMapping.Identity
                                                    )
                                                },
                                                textStyle = TextStyle(
                                                    fontSize = fontSp.sp,
                                                    lineHeight = lineHeightSp,
                                                    fontWeight = weight,
                                                    fontStyle = if (block.isItalic || block.kind == TextBlockKind.QUOTE) FontStyle.Italic else FontStyle.Normal,
                                                    textDecoration = if (block.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                                    color = Color(0xFF0F172A)
                                                ),
                                                cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .alignByBaseline()
                                                    .onFocusChanged {
                                                        isBlockFocused = it.isFocused
                                                        if (it.isFocused) { focusedBlockIndex = bIdx; activeTableCell = null }
                                                    }
                                            ) { innerTextField ->
                                                if (block.text.isEmpty() && bIdx == blocks.lastIndex) {
                                                    Text("Type here...", fontSize = fontSp.sp, color = Color(0xFFCBD5E1))
                                                }
                                                innerTextField()
                                            }
                                        }
                                        val blockUrls = remember(block.text) { extractClickableUrls(block.text) }
                                        if (blockUrls.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                blockUrls.forEach { url ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFEFF6FF),
                                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                        modifier = Modifier.clickable {
                                                            try { uriHandler.openUri(url) } catch (_: Exception) {}
                                                        }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(12.dp))
                                                            Text(
                                                                text = formatLinkChipLabel(url),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFF1D4ED8),
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is DocBlock.ImageBlock -> {
                                        val isSelected = selectedImageId == block.id
                                        val resolved = resolveSharedImageModel(block.url, block.url.substringAfterLast('/'))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedImageId = if (isSelected) null else block.id
                                                    focusedBlockIndex = bIdx
                                                }
                                        ) {
                                            AsyncImage(
                                                model = resolved,
                                                contentDescription = "Review image",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .fillMaxWidth(block.widthPercent / 100f)
                                                    .heightIn(min = 80.dp, max = 900.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                            )

                                            if (isSelected) {
                                                // Floating dark pill matching Screenshot #1 & #3: Full View | - 30% 60% 100% + | Delete
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF0F172A),
                                                    shadowElevation = 6.dp,
                                                    modifier = Modifier.align(Alignment.Center)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = Color(0xFF4F46E5),
                                                            modifier = Modifier.clickable {
                                                                onOpenLightbox(listOf(PictureItem(block.id, block.url.substringAfterLast('/'), block.url)), 0)
                                                            }
                                                        ) {
                                                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Full View", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Icon(
                                                            Icons.Default.Remove,
                                                            contentDescription = "Smaller",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp).clickable {
                                                                blocks[bIdx] = block.copy(widthPercent = (block.widthPercent - 10).coerceAtLeast(25))
                                                            }
                                                        )
                                                        listOf(30, 60, 100).forEach { pct ->
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (block.widthPercent == pct) Color(0xFF334155) else Color.Transparent,
                                                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                                                modifier = Modifier.clickable { blocks[bIdx] = block.copy(widthPercent = pct) }
                                                            ) {
                                                                Text("$pct%", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                                            }
                                                        }
                                                        Icon(
                                                            Icons.Default.Add,
                                                            contentDescription = "Larger",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp).clickable {
                                                                blocks[bIdx] = block.copy(widthPercent = (block.widthPercent + 10).coerceAtMost(100))
                                                            }
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = Color(0xFFEF4444),
                                                            modifier = Modifier.clickable {
                                                                blocks.removeAt(bIdx)
                                                                selectedImageId = null
                                                            }
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.padding(5.dp).size(15.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is DocBlock.TableBlock -> {
                                        val colCount = block.rows.maxOfOrNull { it.size } ?: 1
                                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                            val fitsWithoutScroll = maxWidth >= (colCount * 76).dp
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                                                val cellText = row.getOrElse(cIdx) { "" }
                                                                val isCellActive = activeTableCell == Triple(bIdx, rIdx, cIdx)
                                                                Box(
                                                                    modifier = (if (fitsWithoutScroll) Modifier.weight(1f) else Modifier.widthIn(min = 90.dp, max = 170.dp))
                                                                        .fillMaxHeight()
                                                                        .border(if (isCellActive) 1.5.dp else 0.5.dp, if (isCellActive) Color(0xFF4F46E5) else Color(0xFFE2E8F0))
                                                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                                                ) {
                                                                    BasicTextField(
                                                                        value = cellText,
                                                                        onValueChange = { newCell ->
                                                                            val updatedRows = block.rows.mapIndexed { rr, rList ->
                                                                                if (rr == rIdx) {
                                                                                    MutableList(colCount) { cc -> if (cc == cIdx) newCell else rList.getOrElse(cc) { "" } }
                                                                                } else rList
                                                                            }
                                                                            blocks[bIdx] = block.copy(rows = updatedRows)
                                                                        },
                                                                        textStyle = TextStyle(
                                                                            fontSize = (12.5f * textScale).sp,
                                                                            fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                                                            color = Color(0xFF0F172A)
                                                                        ),
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .onFocusChanged { if (it.isFocused) { activeTableCell = Triple(bIdx, rIdx, cIdx); focusedBlockIndex = bIdx } }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        val tableUrls = remember(block.rows) {
                                            block.rows.flatten().flatMap { extractClickableUrls(it) }.distinct()
                                        }
                                        if (tableUrls.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                tableUrls.forEach { url ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFEFF6FF),
                                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                        modifier = Modifier.clickable {
                                                            try { uriHandler.openUri(url) } catch (_: Exception) {}
                                                        }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(12.dp))
                                                            Text(
                                                                text = formatLinkChipLabel(url),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFF1D4ED8),
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Tap area at bottom of page to add a new paragraph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable {
                                        if (blocks.lastOrNull() !is DocBlock.TextBlock) {
                                            blocks.add(DocBlock.TextBlock(id = nextBlockId()))
                                        }
                                        focusedBlockIndex = blocks.lastIndex
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarPill(
    label: String,
    isActive: Boolean,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontStyle: FontStyle = FontStyle.Normal,
    underline: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) Color(0xFFEEF2FF) else Color.White,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
            color = if (isActive) Color(0xFF4F46E5) else Color(0xFF334155),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ToolbarIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) Color(0xFFEEF2FF) else Color.White,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isActive) Color(0xFF4F46E5) else Color(0xFF334155),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).size(16.dp)
        )
    }
}

private val clickableUrlRegex = Regex(
    """\b((?:https?|upnote|marginnote\w*|obsidian|notion|craftdocs|bear|things|onenote|goodnotes|notability|logseq)://[^\s<>"'\)]+)""",
    RegexOption.IGNORE_CASE
)

private fun extractClickableUrls(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return clickableUrlRegex.findAll(text).mapNotNull { match ->
        var raw = match.value
        while (raw.isNotEmpty() && (raw.endsWith(",") || raw.endsWith(".") || raw.endsWith(";") || raw.endsWith(":") || raw.endsWith(">") || raw.endsWith("!"))) {
            raw = raw.dropLast(1)
        }
        raw.takeIf { it.isNotBlank() }
    }.distinct().toList()
}

private fun formatLinkChipLabel(url: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> "Open YouTube"
        lower.startsWith("upnote://") -> "Open UpNote"
        lower.startsWith("marginnote") -> "Open MarginNote"
        lower.startsWith("obsidian://") -> "Open Obsidian"
        lower.startsWith("notion://") -> "Open Notion"
        else -> {
            val host = url.substringAfter("://").substringBefore("/").removePrefix("www.")
            if (host.isNotBlank()) "Open $host" else "Open Link"
        }
    }
}
