package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.resolveImageModel
import com.example.ui.util.UriHelper

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichReviewEditor(
    products: List<ProductItem>,
    initialReview: ReviewItem? = null,
    preselectedProduct: ProductItem? = null,
    onSaveReview: (review: ReviewItem) -> Unit,
    onDismiss: () -> Unit
) {
    // Current selected product for review
    var selectedProduct by remember {
        mutableStateOf(
            preselectedProduct
                ?: products.find { it.id in (initialReview?.productIds ?: emptyList()) }
                ?: products.find { it.name in (initialReview?.productTitles ?: emptyList()) }
                ?: products.firstOrNull()
        )
    }

    var isProductDropdownOpen by remember { mutableStateOf(false) }
    var isTableDropdownOpen by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf(initialReview?.title ?: "") }
    var authorName by remember { mutableStateOf(initialReview?.authorName ?: "Verified Reviewer") }
    var rating by remember { mutableIntStateOf(initialReview?.rating ?: 5) }

    val cfg = LocalConfiguration.current
    val isTablet = cfg.screenWidthDp >= 600 || cfg.smallestScreenWidthDp >= 600
    val defaultZoom = if (isTablet) 105 else 85

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var zoomPercent by rememberSaveable(isTablet) { mutableIntStateOf(defaultZoom) }
    var showZoomMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun handlePickedImageUri(uri: Uri) {
        try {
            val reviewImagesDir = File(context.filesDir, "review_images")
            if (!reviewImagesDir.exists()) reviewImagesDir.mkdirs()
            val filename = "editor_${System.currentTimeMillis()}.jpg"
            val destFile = File(reviewImagesDir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { out -> input.copyTo(out) }
            }
            val picturesDir = File(context.filesDir, "pictures")
            if (!picturesDir.exists()) picturesDir.mkdirs()
            try { destFile.copyTo(File(picturesDir, filename), overwrite = true) } catch (_: Exception) {}

            val webUrl = "https://local.review/review_images/$filename"
            val js = "insertImage('$webUrl');"
            webViewInstance?.evaluateJavascript(js, null)
        } catch (_: Exception) {
            val js = "insertImage('${uri.toString()}');"
            webViewInstance?.evaluateJavascript(js, null)
        }
    }

    // Photo picker launcher (System Photo Picker / Albums)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedImageUri(uri)
        }
    }

    // File picker launcher (System Document / Storage Picker: Downloads, BlueStacks Mac Shared Folder, SD Card)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedImageUri(uri)
        }
    }

    var isPhotoMenuOpen by remember { mutableStateOf(false) }
    var showAppLibraryDialog by remember { mutableStateOf(false) }
    var showUrlInsertDialog by remember { mutableStateOf(false) }
    var urlToInsert by remember { mutableStateOf("") }

    var editorLightboxImages by remember { mutableStateOf<List<PictureItem>?>(null) }
    var editorLightboxIndex by remember { mutableIntStateOf(0) }

    fun openImageInLightbox(url: String) {
        val cleanUrlName = url.substringAfterLast("/").substringBefore("?")
        val decodedCleanName = try { URLDecoder.decode(cleanUrlName, "UTF-8") } catch (_: Exception) { cleanUrlName }

        val allReviewPictures = initialReview?.pictures ?: emptyList()
        val foundIdx = allReviewPictures.indexOfFirst { pic ->
            val picFn = File(pic.url.removePrefix("file://")).name
            pic.url == url || pic.url == decodedCleanName ||
            picFn.equals(cleanUrlName, ignoreCase = true) ||
            picFn.equals(decodedCleanName, ignoreCase = true)
        }

        if (foundIdx != -1) {
            editorLightboxIndex = foundIdx
            editorLightboxImages = allReviewPictures
        } else {
            editorLightboxIndex = 0
            editorLightboxImages = listOf(
                PictureItem(
                    id = "editor_${System.currentTimeMillis()}",
                    name = decodedCleanName.ifBlank { "Review Photo" },
                    url = url
                )
            )
        }
    }

    val initialBodyHtml = remember(initialReview) {
        var body = initialReview?.body ?: "<p>Introduction and specs...</p>"

        // Convert markdown images ![alt](url) to rich HTML figures
        val markdownImgRegex = Regex("""!\[(.*?)\]\((.*?)\)""")
        body = markdownImgRegex.replace(body) { match ->
            val alt = match.groupValues[1].trim()
            val urlOrPath = match.groupValues[2].trim()
            val cleanAlt = alt.ifBlank { "Review Photo" }
            val filename = urlOrPath.substringAfterLast("/").substringBefore("?")
            val targetUrl = if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) urlOrPath else "https://local.review/review_images/$filename"
            "<figure class=\"review-figure\"><img src=\"$targetUrl\" alt=\"$cleanAlt\" loading=\"lazy\" /><figcaption class=\"review-caption\">$cleanAlt</figcaption></figure>"
        }

        // Rewrite all <img> tags to virtual https://local.review URLs so WebView loads them 100% reliably
        val imgTagRegex = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
        body = imgTagRegex.replace(body) { match ->
            val attrs = match.groupValues[1]
            val srcMatch = Regex("""src=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)
            val attIdMatch = Regex("""(?:data-attachment-id|dataAttachmentId|data-attachment-token)=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)
            val altMatch = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)

            val rawSrc = srcMatch?.groupValues?.get(1)?.trim() ?: ""
            val attId = attIdMatch?.groupValues?.get(1)?.trim() ?: ""
            val alt = altMatch?.groupValues?.get(1)?.trim() ?: ""

            val targetUrl = when {
                rawSrc.startsWith("file://") || rawSrc.startsWith("/data/") -> {
                    val fn = rawSrc.substringAfterLast('/')
                    "https://local.review/review_images/$fn"
                }
                rawSrc.contains("review_images/") || rawSrc.contains("pictures/") || rawSrc.contains("images/") -> {
                    val fn = rawSrc.substringAfterLast('/').substringBefore('?')
                    "https://local.review/review_images/$fn"
                }
                attId.isNotBlank() -> "https://local.review/review_images/$attId"
                rawSrc.startsWith("http://") || rawSrc.startsWith("https://") -> rawSrc
                rawSrc.isNotBlank() && !rawSrc.startsWith("blob:") && !rawSrc.startsWith("data:") -> {
                    val fn = rawSrc.substringAfterLast('/').substringBefore('?')
                    "https://local.review/review_images/$fn"
                }
                else -> ""
            }

            if (targetUrl.isNotBlank()) {
                var newAttrs = attrs
                if (srcMatch != null) {
                    newAttrs = newAttrs.replace(srcMatch.value, "src=\"$targetUrl\"")
                } else {
                    newAttrs = "$newAttrs src=\"$targetUrl\""
                }
                "<img$newAttrs>"
            } else {
                match.value
            }
        }
        body
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("rich_review_editor_screen"),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Top Action Bar
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF1E293B)
                                )
                            }
                            Column {
                                Text(
                                    text = if (initialReview != null) "Edit Review" else "Write Review",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = selectedProduct?.name ?: "No product selected",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Right: Interactive Rating Stars + Save Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Stars
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                (1..5).forEach { star ->
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "$star stars",
                                        tint = if (star <= rating) Color(0xFFF59E0B) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { rating = star }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    webViewInstance?.evaluateJavascript("getEditorContent();") { resultJson ->
                                        // resultJson is a quoted JSON string with HTML content
                                        val content = if (resultJson != null && resultJson != "null") {
                                            try {
                                                org.json.JSONObject("{ \"body\": $resultJson }").getString("body")
                                            } catch (e: Exception) {
                                                resultJson.removeSurrounding("\"").replace("\\\"", "\"").replace("\\n", "\n")
                                            }
                                        } else {
                                            initialBodyHtml
                                        }

                                        val prod = selectedProduct
                                        val prodIds = if (prod != null) listOf(prod.id) else emptyList()
                                        val prodTitles = if (prod != null) listOf(prod.name) else emptyList()

                                        val savedItem = ReviewItem(
                                            id = initialReview?.id ?: "",
                                            reviewNumber = initialReview?.reviewNumber ?: 0,
                                            title = title.ifBlank { if (prod != null) "${prod.name} Review" else "User Review" },
                                            body = content,
                                            authorName = authorName.ifBlank { "Verified Reviewer" },
                                            rating = rating,
                                            productIds = prodIds,
                                            productTitles = prodTitles,
                                            pictures = initialReview?.pictures ?: emptyList(),
                                            createdTime = initialReview?.createdTime ?: ""
                                        )
                                        onSaveReview(savedItem)
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Formatting Toolbar (Screenshot edit and add table.png)
                Surface(
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // H1, H2, H3
                        ToolbarTextButton("H1") { webViewInstance?.evaluateJavascript("formatBlock('h1');", null) }
                        ToolbarTextButton("H2") { webViewInstance?.evaluateJavascript("formatBlock('h2');", null) }
                        ToolbarTextButton("H3") { webViewInstance?.evaluateJavascript("formatBlock('h3');", null) }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = Color(0xFFCBD5E1))

                        // B, I, U, S
                        ToolbarIconButton(Icons.Default.FormatBold, "Bold") { webViewInstance?.evaluateJavascript("execCommand('bold');", null) }
                        ToolbarIconButton(Icons.Default.FormatItalic, "Italic") { webViewInstance?.evaluateJavascript("execCommand('italic');", null) }
                        ToolbarIconButton(Icons.Default.FormatUnderlined, "Underline") { webViewInstance?.evaluateJavascript("execCommand('underline');", null) }
                        ToolbarIconButton(Icons.Default.FormatStrikethrough, "Strikethrough") { webViewInstance?.evaluateJavascript("execCommand('strikeThrough');", null) }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = Color(0xFFCBD5E1))

                        // Bullet List, Numbered List, Quote
                        ToolbarIconButton(Icons.Default.FormatListBulleted, "Bullet List") { webViewInstance?.evaluateJavascript("execCommand('insertUnorderedList');", null) }
                        ToolbarIconButton(Icons.Default.FormatListNumbered, "Numbered List") { webViewInstance?.evaluateJavascript("execCommand('insertOrderedList');", null) }
                        ToolbarIconButton(Icons.Default.FormatQuote, "Quote") { webViewInstance?.evaluateJavascript("formatBlock('blockquote');", null) }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = Color(0xFFCBD5E1))

                        // Table Dropdown Menu [⊞ ∨] (Screenshot edit and add table.png)
                        Box {
                            OutlinedButton(
                                onClick = { isTableDropdownOpen = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = "Table", modifier = Modifier.size(14.dp), tint = Color(0xFF334155))
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF64748B))
                            }

                            DropdownMenu(
                                expanded = isTableDropdownOpen,
                                onDismissRequest = { isTableDropdownOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Insert 2x2 Table", fontSize = 12.sp) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("insertTable(2, 2);", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Insert 3x3 Table", fontSize = 12.sp) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("insertTable(3, 3);", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Row Below", fontSize = 12.sp) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("addRow();", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Column Right", fontSize = 12.sp) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("addColumn();", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Row", fontSize = 12.sp, color = Color(0xFFDC2626)) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("deleteRow();", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Column", fontSize = 12.sp, color = Color(0xFFDC2626)) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("deleteColumn();", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Table", fontSize = 12.sp, color = Color(0xFFDC2626)) },
                                    onClick = {
                                        webViewInstance?.evaluateJavascript("deleteTable();", null)
                                        isTableDropdownOpen = false
                                    }
                                )
                            }
                        }

                        // Insert Photo Button with Source Options [🖼️ Insert Photo ▾]
                        Box {
                            OutlinedButton(
                                onClick = { isPhotoMenuOpen = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFFEEF2FF),
                                    contentColor = Color(0xFF4F46E5)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Insert Photo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                            }

                            DropdownMenu(
                                expanded = isPhotoMenuOpen,
                                onDismissRequest = { isPhotoMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp)) },
                                    text = {
                                        Column {
                                            Text("Browse Files & Storage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Downloads, BlueStacks Mac Shared Folder, SD Card", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    },
                                    onClick = {
                                        isPhotoMenuOpen = false
                                        filePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp)) },
                                    text = {
                                        Column {
                                            Text("Photo Gallery & Albums", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("System Photo Picker & Albums", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    },
                                    onClick = {
                                        isPhotoMenuOpen = false
                                        UriHelper.scanCommonStorageFolders(context)
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp)) },
                                    text = {
                                        Column {
                                            Text("From App Photo Library", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Pick from existing product & review photos", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    },
                                    onClick = {
                                        isPhotoMenuOpen = false
                                        showAppLibraryDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp)) },
                                    text = {
                                        Column {
                                            Text("Enter Image URL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Insert from web link or URL", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    },
                                    onClick = {
                                        isPhotoMenuOpen = false
                                        showUrlInsertDialog = true
                                    }
                                )
                            }
                        }

                        // Clear formatting Tx
                        ToolbarIconButton(Icons.Default.FormatClear, "Clear Formatting") {
                            webViewInstance?.evaluateJavascript("execCommand('removeFormat');", null)
                        }

                        VerticalDivider(modifier = Modifier.height(20.dp), color = Color(0xFFCBD5E1))

                        // Word-Style Zoom Pill [- 85% +]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        zoomPercent = (zoomPercent - 10).coerceAtLeast(50)
                                        webViewInstance?.let { wv ->
                                            wv.settings.textZoom = 100
                                            wv.evaluateJavascript("if (typeof setEditorZoom === 'function') { setEditorZoom($zoomPercent); } else if (document.body) { document.body.style.zoom = '${zoomPercent}%'; }", null)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                }

                                Box {
                                    Text(
                                        text = "${zoomPercent}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .clickable { showZoomMenu = true }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    DropdownMenu(
                                        expanded = showZoomMenu,
                                        onDismissRequest = { showZoomMenu = false }
                                    ) {
                                        listOf(50, 65, 75, 85, 100, 105, 115, 130, 150, 200).forEach { preset ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (preset == defaultZoom) "$preset% (Default)" else "$preset%",
                                                        fontWeight = if (preset == zoomPercent) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 12.sp,
                                                        color = if (preset == zoomPercent) Color(0xFF4F46E5) else Color(0xFF1E293B)
                                                    )
                                                },
                                                onClick = {
                                                    zoomPercent = preset
                                                    webViewInstance?.let { wv ->
                                                        wv.settings.textZoom = 100
                                                        wv.evaluateJavascript("if (typeof setEditorZoom === 'function') { setEditorZoom($zoomPercent); } else if (document.body) { document.body.style.zoom = '${zoomPercent}%'; }", null)
                                                    }
                                                    showZoomMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        zoomPercent = (zoomPercent + 10).coerceAtMost(200)
                                        webViewInstance?.let { wv ->
                                            wv.settings.textZoom = 100
                                            wv.evaluateJavascript("if (typeof setEditorZoom === 'function') { setEditorZoom($zoomPercent); } else if (document.body) { document.body.style.zoom = '${zoomPercent}%'; }", null)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                }
                            }
                        }
                    }
                }

                // Compact Review Metadata Row: Product Selector Chip, Title Input, Author Input
                Surface(
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Product Switcher Pill Button
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .clickable { isProductDropdownOpen = true }
                                    .height(38.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val thumb = selectedProduct?.pictures?.firstOrNull()?.url ?: ""
                                    if (thumb.isNotBlank()) {
                                        AsyncImage(
                                            model = resolveImageModel(context, thumb),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Devices,
                                            contentDescription = null,
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "PRODUCT",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4F46E5),
                                            lineHeight = 10.sp
                                        )
                                        Text(
                                            text = selectedProduct?.name ?: "Select Product",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch product",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isProductDropdownOpen,
                                onDismissRequest = { isProductDropdownOpen = false }
                            ) {
                                products.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text(prod.name, fontSize = 12.sp) },
                                        onClick = {
                                            selectedProduct = prod
                                            isProductDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Title Input inside styled compact Surface
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                BasicTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (title.isEmpty()) {
                                            Text(
                                                "Review title / report name...",
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 3. Author Input inside styled compact Surface
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .width(150.dp)
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                                BasicTextField(
                                    value = authorName,
                                    onValueChange = { authorName = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF0F172A)
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (authorName.isEmpty()) {
                                            Text("Author", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Interactive WYSIWYG Content Area via WebView (contentEditable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val editorHtml = remember(defaultZoom) { buildEditorHtml(initialBodyHtml, initialZoom = defaultZoom) }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                // Prevent GPU MESA rendernode crash in virtualized container
                                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = false
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.textZoom = 100
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.allowFileAccessFromFileURLs = true
                                settings.allowUniversalAccessFromFileURLs = true
                                settings.defaultTextEncodingName = "UTF-8"
                                isVerticalScrollBarEnabled = true
                                isNestedScrollingEnabled = false
                                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

                                // Smooth scrolling inside Compose Dialog
                                setOnTouchListener { v, _ ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                    false
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        // Gracefully handle renderer crash or termination so host app does not terminate
                                        try {
                                            (view?.parent as? ViewGroup)?.removeView(view)
                                            view?.destroy()
                                        } catch (_: Exception) {}
                                        return true
                                    }

                                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                        val reqUrl = request.url ?: return null
                                        val host = reqUrl.host?.lowercase() ?: ""
                                        val path = reqUrl.path ?: ""
                                        val urlStr = reqUrl.toString()

                                        if (host == "local.review" || host == "appassets.local" || path.contains("review_images") || path.contains("pictures") || path.contains("images") || urlStr.contains("review_images") || urlStr.contains("pictures") || urlStr.contains("/images/")) {
                                            try {
                                                val pathParam = reqUrl.getQueryParameter("path")
                                                val filename = reqUrl.lastPathSegment ?: ""
                                                var targetFile: File? = null

                                                if (!pathParam.isNullOrBlank()) {
                                                    val decodedPath = try { URLDecoder.decode(pathParam, "UTF-8") } catch (_: Exception) { pathParam }
                                                    val f = File(decodedPath)
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val f = File(ctx.filesDir, "review_images/$filename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val decodedFilename = try { URLDecoder.decode(filename, "UTF-8") } catch (_: Exception) { filename }
                                                    val f = File(ctx.filesDir, "review_images/$decodedFilename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val f = File(ctx.filesDir, "pictures/$filename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val decodedFilename = try { URLDecoder.decode(filename, "UTF-8") } catch (_: Exception) { filename }
                                                    val f = File(ctx.filesDir, "pictures/$decodedFilename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val f = File(ctx.cacheDir, "review_images/$filename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val f = File(ctx.cacheDir, "pictures/$filename")
                                                    if (f.exists() && f.isFile) targetFile = f
                                                }
                                                if (targetFile == null || !targetFile.exists()) {
                                                    val cleanName = filename.lowercase()
                                                    val cleanNoExt = filename.substringBeforeLast(".").lowercase()
                                                    val decoded = try { URLDecoder.decode(filename, "UTF-8").lowercase() } catch (_: Exception) { cleanName }
                                                    val decodedNoExt = decoded.substringBeforeLast(".")
                                                    val dirs = listOf(
                                                        File(ctx.filesDir, "review_images"),
                                                        File(ctx.filesDir, "pictures"),
                                                        File(ctx.cacheDir, "review_images"),
                                                        File(ctx.cacheDir, "pictures")
                                                    )
                                                    for (dir in dirs) {
                                                        if (dir.exists() && dir.isDirectory) {
                                                            val files = dir.listFiles() ?: continue
                                                            targetFile = files.firstOrNull { f ->
                                                                val fnLower = f.name.lowercase()
                                                                val fnNoExt = f.nameWithoutExtension.lowercase()
                                                                fnLower == cleanName || fnNoExt == cleanName ||
                                                                fnLower == cleanNoExt || fnNoExt == cleanNoExt ||
                                                                fnLower == decoded || fnNoExt == decoded ||
                                                                (cleanName.length >= 3 && (fnLower.startsWith("${cleanName}.") || fnLower.startsWith("${cleanName}_"))) ||
                                                                (cleanNoExt.length >= 3 && (fnLower.startsWith("${cleanNoExt}.") || fnLower.startsWith("${cleanNoExt}_"))) ||
                                                                (cleanName.length >= 4 && (fnLower.contains(cleanName) || cleanName.contains(fnNoExt)))
                                                            }
                                                            if (targetFile != null && targetFile.exists()) break
                                                        }
                                                    }
                                                }

                                                if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                                                    val ext = targetFile.extension.lowercase()
                                                    val mime = when (ext) {
                                                        "png" -> "image/png"
                                                        "webp" -> "image/webp"
                                                        "gif" -> "image/gif"
                                                        "svg" -> "image/svg+xml"
                                                        else -> "image/jpeg"
                                                    }
                                                    val headers = mapOf(
                                                        "Access-Control-Allow-Origin" to "*",
                                                        "Access-Control-Allow-Methods" to "GET",
                                                        "Cache-Control" to "no-cache"
                                                    )
                                                    return WebResourceResponse(mime, null, 200, "OK", headers, targetFile.inputStream())
                                                }

                                                // Clean placeholder fallback instead of broken image
                                                val cleanLabel = (if (filename.isNotBlank() && filename != "review_images") filename.substringBeforeLast(".") else "Review Image").take(24)
                                                val fallbackSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="360" height="220" viewBox="0 0 360 220"><rect width="100%" height="100%" rx="12" fill="#f8fafc" stroke="#cbd5e1" stroke-width="1.5"/><text x="50%" y="44%" text-anchor="middle" font-family="-apple-system, sans-serif" font-size="28" fill="#94a3b8">📷</text><text x="50%" y="64%" text-anchor="middle" font-family="-apple-system, sans-serif" font-size="12" font-weight="600" fill="#64748b">$cleanLabel</text></svg>"""
                                                val headers = mapOf(
                                                    "Access-Control-Allow-Origin" to "*",
                                                    "Access-Control-Allow-Methods" to "GET",
                                                    "Cache-Control" to "no-cache"
                                                )
                                                return WebResourceResponse("image/svg+xml", "UTF-8", 200, "OK", headers, ByteArrayInputStream(fallbackSvg.toByteArray(Charsets.UTF_8)))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.settings?.textZoom = 100
                                        view?.evaluateJavascript("if (typeof setEditorZoom === 'function') { setEditorZoom($zoomPercent); } else if (document.body) { document.body.style.zoom = '${zoomPercent}%'; }", null)
                                    }
                                }

                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun notifyReady() {
                                        // Ready
                                    }

                                    @JavascriptInterface
                                    fun onImageClick(url: String) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            openImageInLightbox(url)
                                        }
                                    }
                                }, "AndroidBridge")

                                loadDataWithBaseURL("https://local.review/", editorHtml, "text/html", "UTF-8", null)
                            }
                        },
                        update = { wv ->
                            wv.settings.textZoom = 100
                            wv.evaluateJavascript("if (typeof setEditorZoom === 'function') { setEditorZoom($zoomPercent); } else if (document.body) { document.body.style.zoom = '${zoomPercent}%'; }", null)
                        },
                        onRelease = { webView ->
                            try {
                                webView.stopLoading()
                                webView.loadUrl("about:blank")
                                webView.destroy()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showAppLibraryDialog) {
        AppPhotoLibraryDialog(
            onDismiss = { showAppLibraryDialog = false },
            onSelectPictures = { pictures ->
                pictures.forEach { pic ->
                    val webUrl = "https://local.review/review_images/${pic.name}"
                    val js = "insertImage('$webUrl');"
                    webViewInstance?.evaluateJavascript(js, null)
                }
            },
            allowMultiple = true
        )
    }

    if (showUrlInsertDialog) {
        AlertDialog(
            onDismissRequest = { showUrlInsertDialog = false },
            title = { Text("Insert Image via URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter image web link (http://, https://, or file://):", fontSize = 12.sp, color = Color(0xFF64748B))
                    OutlinedTextField(
                        value = urlToInsert,
                        onValueChange = { urlToInsert = it },
                        placeholder = { Text("https://example.com/photo.jpg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlToInsert.isNotBlank()) {
                            val js = "insertImage('${urlToInsert.trim()}');"
                            webViewInstance?.evaluateJavascript(js, null)
                            urlToInsert = ""
                            showUrlInsertDialog = false
                        }
                    },
                    enabled = urlToInsert.isNotBlank()
                ) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInsertDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editorLightboxImages?.let { images ->
        FullScreenPhotoLightbox(
            images = images,
            initialIndex = editorLightboxIndex,
            onDismiss = { editorLightboxImages = null }
        )
    }
}

@Composable
private fun ToolbarTextButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .size(width = 30.dp, height = 28.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
        }
    }
}

@Composable
private fun ToolbarIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .size(width = 30.dp, height = 28.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color(0xFF334155), modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * Builds HTML document with rich contentEditable editor & interactive table operations.
 */
private fun buildEditorHtml(initialContent: String, initialZoom: Int = 85): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
          <style>
            * { box-sizing: border-box; }
            html, body {
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
              margin: 0;
              padding: 0;
              color: #1e293b;
              font-size: 14px;
              line-height: 1.6;
              background: #ffffff;
              width: 100%;
              overflow-x: hidden;
            }
            .editor-page-container {
              zoom: ${initialZoom}%;
              padding: 12px 14px 48px 14px;
              max-width: 720px;
              margin: 0 auto;
              box-sizing: border-box;
            }
            #editor {
              min-height: 240px;
              outline: none;
              padding-bottom: 60px;
              word-wrap: break-word;
              overflow-wrap: break-word;
            }
            h1 { font-size: 20px; font-weight: 800; color: #0f172a; margin: 16px 0 8px 0; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }
            h2 { font-size: 17px; font-weight: 700; color: #0f172a; margin: 14px 0 6px 0; }
            h3 { font-size: 15px; font-weight: 600; color: #0f172a; margin: 12px 0 4px 0; }
            p { margin: 8px 0; color: #334155; }
            blockquote {
              border-left: 4px solid #0284c7;
              padding: 8px 12px;
              margin: 12px 0;
              background: #f0f9ff;
              border-radius: 0 6px 6px 0;
              color: #0369a1;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              margin: 12px 0;
              border: 1px solid #cbd5e1;
              background-color: #f8fafc;
            }
            th, td {
              border: 1px solid #cbd5e1;
              padding: 8px 10px;
              text-align: left;
              min-width: 60px;
            }
            th {
              background-color: #f1f5f9;
              font-weight: 700;
            }
            img {
              max-width: 100%;
              object-fit: contain;
              height: auto;
              border-radius: 10px;
              border: 1px solid #e2e8f0;
              margin: 10px auto;
              display: block;
              box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
              cursor: pointer;
              transition: outline 0.15s ease, box-shadow 0.15s ease;
            }
            img.selected-image {
              outline: 3px solid #4f46e5 !important;
              outline-offset: 4px !important;
              box-shadow: 0 4px 16px rgba(79, 70, 229, 0.35) !important;
            }
            figure.review-figure {
              margin: 14px auto;
              text-align: center;
              max-width: 100%;
            }
            figure.review-figure img {
              margin: 0 auto;
            }
            figcaption.review-caption {
              font-size: 12px;
              color: #64748b;
              margin-top: 6px;
              font-style: italic;
              text-align: center;
            }
          </style>
        </head>
        <body>
          <div class="editor-page-container">
            <div id="editor" contenteditable="true">$initialContent</div>
          </div>
          <div id="image-action-bar" style="display: none; position: fixed; z-index: 99999; background: #0f172a; color: white; border-radius: 12px; padding: 6px 10px; box-shadow: 0 8px 24px rgba(0,0,0,0.35); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 12px; gap: 6px; align-items: center; user-select: none;">
            <button type="button" onclick="viewSelectedImageFull()" title="View full screen (Double-click image also works)" style="background: #4f46e5; color: white; border: none; border-radius: 6px; padding: 5px 9px; font-weight: bold; cursor: pointer; font-size: 11px; display: inline-flex; align-items: center; gap: 4px;">
              <span>🔍 Full View</span>
            </button>
            <span style="color: #475569; font-size: 12px;">|</span>
            <button type="button" onclick="resizeSelectedImage(-15)" title="Smaller" style="background: #334155; color: white; border: none; border-radius: 6px; padding: 4px 8px; font-weight: bold; cursor: pointer; font-size: 12px;">➖</button>
            <button type="button" onclick="setImageSize('30%')" title="Small (30%)" style="background: #1e293b; color: #cbd5e1; border: 1px solid #475569; border-radius: 6px; padding: 3px 6px; cursor: pointer; font-size: 11px; font-weight: 600;">30%</button>
            <button type="button" onclick="setImageSize('60%')" title="Medium (60%)" style="background: #1e293b; color: #cbd5e1; border: 1px solid #475569; border-radius: 6px; padding: 3px 6px; cursor: pointer; font-size: 11px; font-weight: 600;">60%</button>
            <button type="button" onclick="setImageSize('100%')" title="Full width (100%)" style="background: #1e293b; color: #cbd5e1; border: 1px solid #475569; border-radius: 6px; padding: 3px 6px; cursor: pointer; font-size: 11px; font-weight: 600;">100%</button>
            <button type="button" onclick="resizeSelectedImage(15)" title="Bigger" style="background: #334155; color: white; border: none; border-radius: 6px; padding: 4px 8px; font-weight: bold; cursor: pointer; font-size: 12px;">➕</button>
            <span style="color: #475569; font-size: 12px;">|</span>
            <button type="button" onclick="deleteSelectedImage()" title="Delete Image" style="background: #dc2626; color: white; border: none; border-radius: 6px; padding: 5px 8px; cursor: pointer; font-size: 11px;">🗑️</button>
          </div>
          <script>
            function setEditorZoom(zoomPct) {
              var style = document.getElementById('dynamic-editor-zoom-style');
              if (!style) {
                style = document.createElement('style');
                style.id = 'dynamic-editor-zoom-style';
                document.head.appendChild(style);
              }
              style.textContent = '.editor-page-container { zoom: ' + zoomPct + '% !important; }';
              if (document.body) { document.body.style.zoom = '100%'; }
              if (document.documentElement) { document.documentElement.style.zoom = '100%'; }
              if (currentSelectedImg) {
                setTimeout(function() { positionActionBar(currentSelectedImg); }, 60);
              }
            }
            function execCommand(cmd, val) {
              document.execCommand(cmd, false, val || null);
            }
            function formatBlock(tag) {
              document.execCommand('formatBlock', false, '<' + tag + '>');
            }
            function insertImage(url) {
              document.execCommand('insertImage', false, url);
              setTimeout(setupImageListeners, 150);
            }
            function insertTable(rows, cols) {
              var html = '<table><tbody>';
              for (var r = 0; r < rows; r++) {
                html += '<tr>';
                for (var c = 0; c < cols; c++) {
                  if (r === 0) {
                    html += '<th>Header ' + (c + 1) + '</th>';
                  } else {
                    html += '<td>Data</td>';
                  }
                }
                html += '</tr>';
              }
              html += '</tbody></table><p></p>';
              document.execCommand('insertHTML', false, html);
            }
            function getCurrentTable() {
              var sel = window.getSelection();
              if (!sel.rangeCount) return null;
              var node = sel.getRangeAt(0).commonAncestorContainer;
              while (node && node.nodeName !== 'TABLE' && node.id !== 'editor') {
                node = node.parentNode;
              }
              return node && node.nodeName === 'TABLE' ? node : null;
            }
            function addRow() {
              var table = getCurrentTable();
              if (table) {
                var cols = table.rows[0] ? table.rows[0].cells.length : 2;
                var row = table.insertRow(-1);
                for (var i = 0; i < cols; i++) {
                  var cell = row.insertCell(-1);
                  cell.innerText = 'New Data';
                }
              }
            }
            function addColumn() {
              var table = getCurrentTable();
              if (table) {
                for (var i = 0; i < table.rows.length; i++) {
                  var cell = table.rows[i].insertCell(-1);
                  cell.innerText = i === 0 ? 'Header' : 'Data';
                }
              }
            }
            function deleteRow() {
              var table = getCurrentTable();
              if (table && table.rows.length > 1) {
                table.deleteRow(table.rows.length - 1);
              }
            }
            function deleteColumn() {
              var table = getCurrentTable();
              if (table && table.rows.length && table.rows[0].cells.length > 1) {
                for (var i = 0; i < table.rows.length; i++) {
                  table.rows[i].deleteCell(table.rows[i].cells.length - 1);
                }
              }
            }
            function deleteTable() {
              var table = getCurrentTable();
              if (table && table.parentNode) {
                table.parentNode.removeChild(table);
              }
            }
            function getEditorContent() {
              var el = document.getElementById('editor');
              return el ? el.innerHTML : '';
            }

            function getZoomFactor() {
              var z = 1;
              try {
                var bz = window.getComputedStyle(document.body).zoom;
                if (bz && !isNaN(parseFloat(bz))) z = parseFloat(bz);
                else {
                  var hz = window.getComputedStyle(document.documentElement).zoom;
                  if (hz && !isNaN(parseFloat(hz))) z = parseFloat(hz);
                }
              } catch(e) {}
              return z > 0 ? z : 1;
            }

            var currentSelectedImg = null;
            var lastClickPos = null;

            function setupImageListeners() {
              var images = document.querySelectorAll('#editor img');
              images.forEach(function(img) {
                if (img.dataset.hasImgListener) return;
                img.dataset.hasImgListener = 'true';

                img.addEventListener('pointerdown', function(e) {
                  lastClickPos = { clientX: e.clientX, clientY: e.clientY };
                });

                // Single click: select image and show floating resize/view action bar
                img.addEventListener('click', function(e) {
                  e.stopPropagation();
                  lastClickPos = { clientX: e.clientX, clientY: e.clientY };
                  selectImage(img, e);
                });

                // Double click: view full image directly
                img.addEventListener('dblclick', function(e) {
                  e.stopPropagation();
                  if (window.AndroidBridge && window.AndroidBridge.onImageClick) {
                    window.AndroidBridge.onImageClick(img.src);
                  }
                });
              });
            }

            function selectImage(img, clickEvent) {
              if (currentSelectedImg && currentSelectedImg !== img) {
                currentSelectedImg.classList.remove('selected-image');
              }
              currentSelectedImg = img;
              img.classList.add('selected-image');
              positionActionBar(img, clickEvent);
            }

            function positionActionBar(img, clickEvent) {
              var bar = document.getElementById('image-action-bar');
              if (!bar || !img) return;

              if (clickEvent && clickEvent.clientX !== undefined) {
                lastClickPos = { clientX: clickEvent.clientX, clientY: clickEvent.clientY };
              }

              var rect = img.getBoundingClientRect();
              var winH = window.innerHeight || document.documentElement.clientHeight || 600;
              var winW = window.innerWidth || document.documentElement.clientWidth || 800;

              // Hide if image is scrolled completely out of view
              if (rect.bottom < 10 || rect.top > winH - 10 || rect.width === 0 || rect.height === 0) {
                bar.style.display = 'none';
                return;
              }

              bar.style.display = 'flex';
              bar.style.position = 'fixed';

              var z = getZoomFactor();
              var barW = bar.offsetWidth || 320;
              var barH = bar.offsetHeight || 38;

              // Determine target Y in viewport coordinates:
              var targetY;
              if (rect.top >= barH + 8) {
                // Top of image is visible and has room above it
                targetY = rect.top - barH - 6;
              } else if (rect.top >= 6) {
                // Top of image is near top of viewport, place just inside top of image
                targetY = rect.top + 6;
              } else {
                // Top of image is scrolled off the top of screen.
                // If user just clicked at a visible point inside the image:
                if (lastClickPos && lastClickPos.clientY >= barH + 10 && lastClickPos.clientY <= rect.bottom) {
                  targetY = lastClickPos.clientY - barH - 8;
                } else {
                  // Fallback: stay visible near top of viewport
                  targetY = 10;
                }
              }

              // Keep targetY clamped within visible viewport
              if (targetY < 8) targetY = 8;
              if (targetY + barH > winH - 8) targetY = winH - barH - 8;

              // Determine target X (centered on image)
              var imgCenter = rect.left + (rect.width / 2);
              var targetX = imgCenter - (barW / 2);

              // Clamp X within viewport
              if (targetX < 8) targetX = 8;
              if (targetX + barW > winW - 8) targetX = winW - barW - 8;

              // Apply position with zoom compensation
              bar.style.top = (targetY / z) + 'px';
              bar.style.left = (targetX / z) + 'px';
            }

            function deselectImage() {
              if (currentSelectedImg) {
                currentSelectedImg.classList.remove('selected-image');
                currentSelectedImg = null;
              }
              var bar = document.getElementById('image-action-bar');
              if (bar) bar.style.display = 'none';
            }

            document.addEventListener('click', function(e) {
              var bar = document.getElementById('image-action-bar');
              if (bar && bar.contains(e.target)) return;
              if (e.target && e.target.tagName !== 'IMG') {
                deselectImage();
              }
            });

            window.addEventListener('scroll', function() {
              if (currentSelectedImg) positionActionBar(currentSelectedImg);
            }, true);

            window.addEventListener('resize', function() {
              if (currentSelectedImg) positionActionBar(currentSelectedImg);
            });

            function viewSelectedImageFull() {
              if (currentSelectedImg && window.AndroidBridge && window.AndroidBridge.onImageClick) {
                window.AndroidBridge.onImageClick(currentSelectedImg.src);
              }
            }

            function resizeSelectedImage(deltaPercent) {
              if (!currentSelectedImg) return;
              var currentWidth = 100;
              var styleWidth = currentSelectedImg.style.width;
              if (styleWidth && styleWidth.indexOf('%') !== -1) {
                currentWidth = parseInt(styleWidth, 10);
              } else if (currentSelectedImg.parentElement && currentSelectedImg.parentElement.tagName === 'FIGURE') {
                var figW = currentSelectedImg.parentElement.style.width;
                if (figW && figW.indexOf('%') !== -1) {
                  currentWidth = parseInt(figW, 10);
                }
              }

              var newWidth = Math.max(20, Math.min(100, currentWidth + deltaPercent));
              setImageSize(newWidth + '%');
            }

            function setImageSize(widthStr) {
              if (!currentSelectedImg) return;
              currentSelectedImg.style.width = widthStr;
              currentSelectedImg.style.maxWidth = '100%';
              currentSelectedImg.style.height = 'auto';

              var fig = currentSelectedImg.closest('figure');
              if (fig) {
                fig.style.width = widthStr;
                fig.style.margin = '14px auto';
                fig.style.maxWidth = '100%';
                fig.style.textAlign = 'center';
              }

              setTimeout(function() {
                if (currentSelectedImg) positionActionBar(currentSelectedImg);
              }, 40);
            }

            function deleteSelectedImage() {
              if (!currentSelectedImg) return;
              var fig = currentSelectedImg.closest('figure');
              if (fig && fig.parentNode) {
                fig.parentNode.removeChild(fig);
              } else if (currentSelectedImg.parentNode) {
                currentSelectedImg.parentNode.removeChild(currentSelectedImg);
              }
              deselectImage();
            }

            var observer = new MutationObserver(function() {
              setupImageListeners();
            });
            var editorEl = document.getElementById('editor');
            if (editorEl) {
              observer.observe(editorEl, { childList: true, subtree: true });
            }
            setupImageListeners();
            document.addEventListener('DOMContentLoaded', setupImageListeners);
            window.addEventListener('load', setupImageListeners);
          </script>
        </body>
        </html>
    """.trimIndent()
}
