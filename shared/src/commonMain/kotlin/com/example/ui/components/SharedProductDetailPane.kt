package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel

/**
 * SharedProductDetailPane — KMP equivalent of Android's ProductDetailPaneContent.
 * Read-only Product View screen with:
 * - Top bar: product name, zoom control (- 60% +), Review, Edit, Compare, Delete, Close
 * - Hero image + thumbnail strip (tappable → zoomable/draggable SharedFullScreenPhotoLightbox)
 * - PRODUCT INFORMATION card (name, brand, tags, stats, created date)
 * - PRODUCT OVERVIEW section (rich text)
 * - FULL SPECIFICATIONS SHEET table (spec name, details/value, photos → lightbox)
 * - CUSTOMER REVIEWS section (with photos → lightbox)
 */
@Composable
fun SharedProductDetailPane(
    product: ProductItem,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    isCompared: Boolean,
    onToggleCompare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddReview: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    isTwoPane: Boolean = false
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var activeLightboxImages by remember { mutableStateOf<List<PictureItem>?>(null) }
    var activeLightboxIndex by remember { mutableStateOf(0) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var zoomPercent by remember(product.id) { mutableStateOf(60) }

    val maxContentWidth = (zoomPercent / 100f).let { frac ->
        if (frac >= 1f) 2000.dp else (900.dp * frac).coerceAtLeast(360.dp)
    }

    // Lightbox overlay
    val lbImages = activeLightboxImages
    if (lbImages != null) {
        SharedFullScreenPhotoLightbox(
            images = lbImages,
            initialIndex = activeLightboxIndex,
            onDismiss = { activeLightboxImages = null }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${product.name}\"? This will also remove its associated specifications.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onDismiss?.invoke()
                }) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──────────────────────────────────────────────────────
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Product name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = true).padding(end = 6.dp)
                    ) {
                        if (!isTwoPane && onDismiss != null) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                                    tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Zoom control pill: – 60% +
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { zoomPercent = (zoomPercent - 10).coerceAtLeast(50) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom out",
                                        modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                }
                                Box {
                                    Text(
                                        text = "$zoomPercent%",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .clickable { showZoomMenu = true }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    DropdownMenu(expanded = showZoomMenu, onDismissRequest = { showZoomMenu = false }) {
                                        listOf(50, 60, 65, 75, 85, 100, 115, 130, 150).forEach { preset ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (preset == 60) "$preset% (Default)" else "$preset%",
                                                        fontWeight = if (preset == zoomPercent) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 12.sp,
                                                        color = if (preset == zoomPercent) Color(0xFF4F46E5) else Color(0xFF1E293B)
                                                    )
                                                },
                                                onClick = { zoomPercent = preset; showZoomMenu = false }
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { zoomPercent = (zoomPercent + 10).coerceAtMost(160) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom in",
                                        modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                }
                            }
                        }

                        // Review button (indigo pill)
                        Surface(
                            onClick = onAddReview,
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4F46E5)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, "Review", tint = Color.White, modifier = Modifier.size(15.dp))
                                Text("Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Edit button (dark slate + yellow icon)
                        Surface(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Edit, "Edit", tint = Color(0xFFFACC15), modifier = Modifier.size(15.dp))
                                Text("Edit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Compare icon button
                        Surface(
                            onClick = onToggleCompare,
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCompared) Color(0xFFEEF2FF) else Color.White,
                            border = BorderStroke(1.dp, if (isCompared) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Scale, "Compare",
                                    tint = if (isCompared) Color(0xFF4F46E5) else Color(0xFF64748B),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Delete icon button
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(19.dp))
                        }

                        // Close icon button
                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // ── Scrollable content ───────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Hero image + thumbnail strip ─────────────────────────
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth().height(250.dp)
                        ) {
                            if (product.pictures.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = resolveSharedImageModel(
                                            product.pictures.first().url,
                                            product.pictures.first().name
                                        ),
                                        contentDescription = product.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                activeLightboxImages = product.pictures
                                                activeLightboxIndex = 0
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        if (product.pictures.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                product.pictures.forEachIndexed { pIdx, pPic ->
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                            .clickable {
                                                activeLightboxImages = product.pictures
                                                activeLightboxIndex = pIdx
                                            }
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = resolveSharedImageModel(pPic.url, pPic.name),
                                            contentDescription = pPic.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── PRODUCT INFORMATION ──────────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalOffer, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "PRODUCT INFORMATION",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Product Name
                                    Column {
                                        Text("PRODUCT NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    // Brand
                                    Column {
                                        Text("BRAND", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFEEF2FF),
                                            border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                        ) {
                                            Text(
                                                product.brand.ifBlank { "UNBRANDED" }.uppercase(),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = Color(0xFF4338CA),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    // Tags
                                    Column {
                                        Text("TAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
                                        Spacer(Modifier.height(6.dp))
                                        if (product.tags.isEmpty()) {
                                            Text("None", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        } else {
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                product.tags.forEach { tag ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFF8FAFC),
                                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                                    ) {
                                                        Text(tag, fontSize = 11.sp, color = Color(0xFF334155),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    // Stats
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Total Specifications:", fontSize = 13.sp, color = Color(0xFF334155))
                                        Text("${specifications.size} attributes", fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Community Reviews:", fontSize = 13.sp, color = Color(0xFF334155))
                                        Text("${reviews.size} reviews", fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    }
                                    if (product.createdTime.isNotBlank()) {
                                        Row(modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("Record Created:", fontSize = 13.sp, color = Color(0xFF334155))
                                            Text(product.createdTime, fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { HorizontalDivider(color = Color(0xFFE2E8F0)) }

                    // ── PRODUCT OVERVIEW ─────────────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("PRODUCT OVERVIEW", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                    color = Color(0xFF0F172A), letterSpacing = 0.5.sp)
                            }
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val overviewText = parseSharedRichTextToAnnotatedString(
                                    product.overview.ifBlank { "No overview provided." },
                                    baseColor = Color(0xFF334155),
                                    linkColor = Color(0xFF4F46E5)
                                )
                                Text(
                                    text = overviewText,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // ── FULL SPECIFICATIONS SHEET ────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "FULL SPECIFICATIONS SHEET (${specifications.size})",
                                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                        color = Color(0xFF0F172A), letterSpacing = 0.5.sp
                                    )
                                }
                                TextButton(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Spec", fontSize = 11.sp)
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    // Table header
                                    Surface(color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B), modifier = Modifier.width(22.dp))
                                            Text("SPECIFICATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B), modifier = Modifier.weight(0.28f))
                                            Text("DETAILS / VALUE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B), modifier = Modifier.weight(0.54f))
                                            Text("ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B), textAlign = TextAlign.End,
                                                modifier = Modifier.width(52.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFE2E8F0))

                                    if (specifications.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center) {
                                            Text("No specifications recorded for this product.",
                                                fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        }
                                    } else {
                                        specifications.forEachIndexed { index, spec ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Text("${index + 1}", fontSize = 12.sp, color = Color(0xFF94A3B8),
                                                    modifier = Modifier.width(22.dp).padding(top = 2.dp))
                                                Text(spec.specName, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.weight(0.28f).padding(top = 2.dp))
                                                Column(modifier = Modifier.weight(0.54f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    val richDetails = parseSharedRichTextToAnnotatedString(
                                                        spec.details, Color(0xFF0F172A), Color(0xFF4F46E5)
                                                    )
                                                    Text(richDetails, fontSize = 13.sp, lineHeight = 18.sp)
                                                    val unitVal = spec.unit
                                                    if (!unitVal.isNullOrBlank()) {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF1F5F9)) {
                                                            Text(unitVal, fontSize = 10.sp, color = Color(0xFF64748B),
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                        }
                                                    }
                                                    // Spec pictures
                                                    if (spec.pictures.isNotEmpty()) {
                                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            itemsIndexed(spec.pictures) { picIdx, pic ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(100.dp)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .background(Color(0xFFF8FAFC))
                                                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                                        .clickable {
                                                                            activeLightboxImages = spec.pictures
                                                                            activeLightboxIndex = picIdx
                                                                        }
                                                                ) {
                                                                    AsyncImage(
                                                                        model = resolveSharedImageModel(pic.url, pic.name),
                                                                        contentDescription = pic.name,
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        contentScale = ContentScale.Fit
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(Icons.Default.OpenInFull, null,
                                                                tint = Color(0xFF64748B), modifier = Modifier.size(10.dp))
                                                            Text("Tap photo to open full screen", fontSize = 10.sp,
                                                                color = Color(0xFF64748B), lineHeight = 12.sp)
                                                        }
                                                    }
                                                }
                                                // Edit spec → opens Product Workspace
                                                Row(modifier = Modifier.width(52.dp),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.Top) {
                                                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Edit, "Edit spec", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            if (index < specifications.size - 1) {
                                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── CUSTOMER REVIEWS ─────────────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("CUSTOMER REVIEWS (${reviews.size})", fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp, color = Color(0xFF0F172A), letterSpacing = 0.5.sp)
                                }
                                TextButton(onClick = onAddReview, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                    Icon(Icons.Default.Add, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Review", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
                                }
                            }

                            if (reviews.isEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("No reviews yet for this product.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    reviews.forEach { review ->
                                        val reviewPictures = remember(review) {
                                            val list = mutableListOf<PictureItem>()
                                            if (review.pictures.isNotEmpty()) list.addAll(review.pictures)
                                            val imgRegex = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
                                            imgRegex.findAll(review.body).forEachIndexed { idx, match ->
                                                val attrs = match.groupValues[1]
                                                val src = Regex("""src=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.trim() ?: ""
                                                val alt = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.trim() ?: "Photo ${list.size + 1}"
                                                if (src.isNotBlank() && !src.startsWith("blob:") && list.none { it.url == src }) {
                                                    list.add(PictureItem(id = "rev_img_$idx", name = alt, url = src))
                                                }
                                            }
                                            list
                                        }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        review.authorName.ifBlank { "Verified Reviewer" },
                                                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A)
                                                    )
                                                    Text("Review #${review.reviewNumber}", fontSize = 12.sp,
                                                        color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    repeat(5) { i ->
                                                        Icon(
                                                            if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                            null,
                                                            tint = if (i < review.rating) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                val plainBody = sharedHtmlToPlainText(review.body).take(200)
                                                if (plainBody.isNotBlank()) {
                                                    Text(plainBody, fontSize = 13.sp, lineHeight = 19.sp,
                                                        color = Color(0xFF334155), maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis)
                                                }
                                                if (reviewPictures.isNotEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        reviewPictures.forEachIndexed { picIdx, pic ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(64.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(Color(0xFFF8FAFC))
                                                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                                                    .clickable {
                                                                        activeLightboxImages = reviewPictures
                                                                        activeLightboxIndex = picIdx
                                                                    }
                                                            ) {
                                                                AsyncImage(
                                                                    model = resolveSharedImageModel(pic.url, pic.name),
                                                                    contentDescription = pic.name,
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = ContentScale.Crop
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
            }
        }
    }
}
