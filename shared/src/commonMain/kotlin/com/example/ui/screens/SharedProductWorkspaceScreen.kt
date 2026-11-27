package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel
import com.example.ui.components.SharedFullScreenPhotoLightbox
import com.example.ui.components.SharedHtmlWysiwygEditor
import com.example.ui.components.sharedHtmlToPlainText
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlin.random.Random

// ─── Preset data (mirrors Android ProductWorkspaceScreen) ───────────────────

val SHARED_PRESET_BRANDS = listOf(
    "Acer", "Adidas", "Anker", "Apple", "Asus", "Bose", "Boya", "Dell", "Dyson", "Google",
    "HP", "Huawei", "JBL", "Lenovo", "LG", "Nike", "OnePlus", "Oppo", "Philips", "Plaud",
    "Puma", "Samsung", "Sennheiser", "Sony", "Under Armour", "Viture", "vivo", "Xiaomi"
)

val SHARED_PRESET_TAGS = listOf(
    "Adidas", "Ai voice recorder", "amazon", "Audio", "Boya", "dress", "Home appliance",
    "IT product", "Kid", "Plaud", "Puma", "Shoe", "Smart wear", "Smartphone", "Sony",
    "Test2", "TV", "Viture", "vivo", "Wearable"
)

val SHARED_PRESET_UNITS = listOf(
    "g", "kg", "inch", "cm", "mm", "year", "month", "mAh", "Hz", "$", "GB", "TB", "W", "V", "mA", "K"
)

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SharedProductWorkspaceScreen(
    initialProduct: ProductItem?,
    onSaveProduct: (ProductItem, List<SpecificationItem>, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    allAvailableTags: List<String> = emptyList(),
    allAvailableBrands: List<String> = emptyList(),
    allAvailablePictures: List<PictureItem> = emptyList(),
    initialSpecifications: List<SpecificationItem> = emptyList(),
    onDeleteProduct: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val isNew = initialProduct == null

    // Product fields
    val productId = remember(initialProduct?.id) {
        initialProduct?.id ?: ("rec_" + Random.nextLong(10000000000000000L, 99999999999999999L).toString())
    }
    var productName by remember(productId) { mutableStateOf(initialProduct?.name ?: "") }
    var brand by remember(productId) { mutableStateOf(initialProduct?.brand ?: "") }
    var selectedTags by remember(productId) {
        mutableStateOf(
            if (initialProduct != null && initialProduct.tags.isNotEmpty())
                initialProduct.tags.toSet()
            else emptySet()
        )
    }
    var newTagInput by remember { mutableStateOf("") }
    var brandSearchQuery by remember { mutableStateOf("") }
    val brandScrollState = rememberScrollState()
    var tagSearchQuery by remember { mutableStateOf("") }
    val tagScrollState = rememberScrollState()

    val allBrandsList = remember(allAvailableBrands) {
        (SHARED_PRESET_BRANDS + allAvailableBrands)
            .filter { it.isNotBlank() }.distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toMutableStateList()
    }
    val allTagsList = remember(allAvailableTags) {
        (SHARED_PRESET_TAGS + allAvailableTags)
            .filter { it.isNotBlank() }.distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toMutableStateList()
    }

    var overviewText by remember(productId) { mutableStateOf(initialProduct?.overview ?: "") }
    val productPictures = remember(productId) {
        mutableStateListOf<PictureItem>().apply { addAll(initialProduct?.pictures ?: emptyList()) }
    }

    // Specs list (defaults to 1 "New specification" for new products)
    val specsList = remember(productId) {
        mutableStateListOf<SpecificationItem>().apply {
            when {
                initialSpecifications.isNotEmpty() -> addAll(initialSpecifications.sortedBy { it.effectiveOrder })
                initialProduct != null -> { /* existing product with 0 specs */ }
                else -> add(
                    SpecificationItem(
                        id = "spec_1",
                        productId = productId,
                        productTitle = productName,
                        specName = "New specification",
                        details = "",
                        unit = null,
                        orderIndex = 1
                    )
                )
            }
        }
    }

    var selectedSpecIndex by remember(productId) { mutableStateOf(0) }
    var expandedSpecId by remember(productId) { mutableStateOf<String?>(specsList.firstOrNull()?.id) }
    var showDeleteProductConfirm by remember { mutableStateOf(false) }
    var specToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pictureToRemove by remember { mutableStateOf<PictureItem?>(null) }
    var showAppLibraryForProduct by remember { mutableStateOf(false) }
    var showAppLibraryForSpecIndex by remember { mutableStateOf<Int?>(null) }
    var activeLightboxImages by remember { mutableStateOf<List<PictureItem>?>(null) }
    var activeLightboxIndex by remember { mutableStateOf(0) }

    // Product image picker (FileKit)
    val productImagePicker = rememberFilePickerLauncher<List<PlatformFile>>(
        type = PickerType.Image,
        mode = PickerMode.Multiple()
    ) { files ->
        files?.forEach { file ->
            val path = file.path
            if (path != null) {
                productPictures.add(
                    PictureItem(
                        id = "pic_${Random.nextLong(100000000, 999999999)}",
                        name = file.name,
                        url = path
                    )
                )
            }
        }
    }

    // Spec image picker (FileKit)
    val specImagePicker = rememberFilePickerLauncher<List<PlatformFile>>(
        type = PickerType.Image,
        mode = PickerMode.Multiple()
    ) { files ->
        if (selectedSpecIndex in specsList.indices) {
            val cur = specsList[selectedSpecIndex]
            val newPics = cur.pictures.toMutableList()
            files?.forEach { file ->
                val path = file.path
                if (path != null) {
                    newPics.add(
                        PictureItem(
                            id = "pic_${Random.nextLong(100000000, 999999999)}",
                            name = file.name,
                            url = path
                        )
                    )
                }
            }
            specsList[selectedSpecIndex] = cur.copy(pictures = newPics)
        }
    }

    // Save action
    fun doSave() {
        val product = ProductItem(
            id = productId,
            name = productName.trim().ifBlank { "Untitled product" },
            brand = brand.trim(),
            tags = selectedTags.toList(),
            overview = overviewText.trim(),
            pictures = productPictures.toList(),
            createdTime = initialProduct?.createdTime ?: ""
        )
        val finalSpecs = specsList.mapIndexed { idx, s ->
            s.copy(
                productId = productId,
                productTitle = productName.trim().ifBlank { "Untitled product" },
                orderIndex = idx + 1
            )
        }
        onSaveProduct(product, finalSpecs, isNew)
        onNavigateBack()
    }

    // Reorder helpers
    fun moveUp() {
        val idx = selectedSpecIndex
        if (idx > 0 && idx < specsList.size) {
            val item = specsList.removeAt(idx)
            specsList.add(idx - 1, item)
            selectedSpecIndex = idx - 1
            expandedSpecId = item.id
        }
    }

    fun moveDown() {
        val idx = selectedSpecIndex
        if (idx >= 0 && idx < specsList.size - 1) {
            val item = specsList.removeAt(idx)
            specsList.add(idx + 1, item)
            selectedSpecIndex = idx + 1
            expandedSpecId = item.id
        }
    }

    fun sortAZ() {
        val selectedId = specsList.getOrNull(selectedSpecIndex)?.id
        val sorted = specsList.sortedBy { it.specName.lowercase() }
        specsList.clear(); specsList.addAll(sorted)
        if (selectedId != null) {
            selectedSpecIndex = specsList.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        }
    }

    fun sortZA() {
        val selectedId = specsList.getOrNull(selectedSpecIndex)?.id
        val sorted = specsList.sortedByDescending { it.specName.lowercase() }
        specsList.clear(); specsList.addAll(sorted)
        if (selectedId != null) {
            selectedSpecIndex = specsList.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────
    if (showDeleteProductConfirm && initialProduct != null && onDeleteProduct != null) {
        AlertDialog(
            onDismissRequest = { showDeleteProductConfirm = false },
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${initialProduct.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteProductConfirm = false
                        onDeleteProduct(initialProduct.id)
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProductConfirm = false }) { Text("Cancel") }
            }
        )
    }

    specToDeleteIndex?.let { idx ->
        val specName = specsList.getOrNull(idx)?.specName ?: "this specification"
        AlertDialog(
            onDismissRequest = { specToDeleteIndex = null },
            title = { Text("Delete Specification", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete specification \"$specName\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (idx in specsList.indices) {
                            val removed = specsList.removeAt(idx)
                            if (selectedSpecIndex >= specsList.size)
                                selectedSpecIndex = (specsList.size - 1).coerceAtLeast(0)
                            if (expandedSpecId == removed.id)
                                expandedSpecId = specsList.getOrNull(selectedSpecIndex)?.id
                        }
                        specToDeleteIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { specToDeleteIndex = null }) { Text("Cancel") }
            }
        )
    }

    pictureToRemove?.let { pic ->
        AlertDialog(
            onDismissRequest = { pictureToRemove = null },
            title = { Text("Remove Photo", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove \"${pic.name.ifBlank { "this photo" }}\"?") },
            confirmButton = {
                Button(
                    onClick = { productPictures.remove(pic); pictureToRemove = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pictureToRemove = null }) { Text("Cancel") }
            }
        )
    }

    if (showAppLibraryForProduct) {
        SharedAppPhotoLibraryDialog(
            availablePictures = allAvailablePictures,
            onDismiss = { showAppLibraryForProduct = false },
            onSelectPictures = { pics -> productPictures.addAll(pics) }
        )
    }

    showAppLibraryForSpecIndex?.let { specIdx ->
        if (specIdx in specsList.indices) {
            SharedAppPhotoLibraryDialog(
                availablePictures = allAvailablePictures,
                onDismiss = { showAppLibraryForSpecIndex = null },
                onSelectPictures = { pics ->
                    val cur = specsList[specIdx]
                    specsList[specIdx] = cur.copy(pictures = cur.pictures + pics)
                }
            )
        }
    }

    activeLightboxImages?.let { imgs ->
        SharedFullScreenPhotoLightbox(
            images = imgs,
            initialIndex = activeLightboxIndex,
            onDismiss = { activeLightboxImages = null }
        )
    }

    // ── Responsive Container + Scaffold ────────────────────────────────────
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideMode = maxWidth >= 600.dp
        val defaultZoom = if (isWideMode) 60 else 100
        var zoomPercent by remember(isWideMode) { mutableStateOf(defaultZoom) }
        var showZoomMenu by remember { mutableStateOf(false) }
        val maxContentWidth: Dp = (maxWidth * (zoomPercent / 100f)).coerceAtLeast(360.dp)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Surface(color = Color.White, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back
                        TextButton(
                            onClick = onNavigateBack,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Back", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        }

                        // Title
                        Text(
                            text = if (isNew) "Add New Product"
                            else if (productName.isNotBlank()) "Edit: $productName"
                            else "Create Product",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Right controls: Zoom pill | Delete (edit only) | Publish/Save | X
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                                        Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                    }
                                    Box {
                                        Text(
                                            text = "$zoomPercent%",
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
                                            listOf(50, 60, 65, 75, 85, 100, 115, 130, 150).forEach { preset ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (preset == defaultZoom) "$preset% (Default)" else "$preset%",
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
                                        Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                    }
                                }
                            }

                            if (!isNew && onDeleteProduct != null) {
                                IconButton(
                                    onClick = { showDeleteProductConfirm = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { doSave() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isNew) "Publish" else "Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = maxContentWidth)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = productName.ifBlank { "Untitled product" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${selectedTags.size} tags / ${specsList.size} specifications",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isNew && onDeleteProduct != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFEF2F2),
                                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable { showDeleteProductConfirm = true }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete product", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Button(
                                    onClick = { doSave() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isNew) "Publish product" else "Save changes",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF1F5F9)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {

                    // ── Workspace Subheader ───────────────────────────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "PRODUCT WORKSPACE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "Create a product customers can compare",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                lineHeight = 28.sp
                            )
                            Text(
                                "Use tags for discovery and add only the specifications that make sense for this product.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // ── 1. Product Identity Card ──────────────────────────
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Token, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                                    Text("Product identity", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                }

                                // Product name
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Product name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                    OutlinedTextField(
                                        value = productName,
                                        onValueChange = { productName = it },
                                        placeholder = { Text("e.g. Boya notra") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color(0xFFE2E8F0),
                                            focusedBorderColor = Color(0xFF0F172A)
                                        )
                                    )
                                }

                                // ── Brand Section ─────────────────────────
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Brand", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                        if (brand.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("Selected:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0F172A)) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                                        Text(brand, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        IconButton(onClick = { brand = "" }, modifier = Modifier.size(14.dp)) {
                                                            Icon(Icons.Default.Close, contentDescription = "Clear brand", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Brand input + Save Brand
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = brand,
                                            onValueChange = { brand = it },
                                            placeholder = { Text("Type brand name or choose below...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                                focusedBorderColor = Color(0xFF0F172A)
                                            )
                                        )
                                        if (brand.trim().isNotBlank() &&
                                            !allBrandsList.any { it.equals(brand.trim(), ignoreCase = true) }
                                        ) {
                                            Button(
                                                onClick = {
                                                    val t = brand.trim()
                                                    if (t.isNotBlank() && !allBrandsList.any { it.equals(t, ignoreCase = true) })
                                                        allBrandsList.add(t)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFF1F5F9),
                                                    contentColor = Color(0xFF0F172A)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text("Save Brand", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    // Brand picker card
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Compact Search + scroll arrows (BasicTextField prevents vertical clipping!)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                WsCompactSearchField(
                                                    value = brandSearchQuery,
                                                    onValueChange = { brandSearchQuery = it },
                                                    placeholder = "Search brands...",
                                                    modifier = Modifier.weight(1f)
                                                )
                                                WsScrollArrow(Icons.AutoMirrored.Filled.ArrowBack, "Scroll left") {
                                                    scope.launch { brandScrollState.animateScrollTo((brandScrollState.value - 300).coerceAtLeast(0)) }
                                                }
                                                WsScrollArrow(Icons.Default.ArrowForward, "Scroll right") {
                                                    scope.launch { brandScrollState.animateScrollTo(brandScrollState.value + 300) }
                                                }
                                            }

                                            val currentBrands = allBrandsList.toList()
                                            val currentTags = allTagsList.toList()
                                            val filteredBrands = remember(currentBrands, currentTags, brandSearchQuery) {
                                                val q = brandSearchQuery.trim()
                                                if (q.isBlank()) currentBrands
                                                else {
                                                    val bMatches = currentBrands.filter { it.contains(q, ignoreCase = true) }
                                                    val tMatches = currentTags.filter { t ->
                                                        t.contains(q, ignoreCase = true) && !currentBrands.any { it.equals(t, ignoreCase = true) }
                                                    }
                                                    bMatches + tMatches
                                                }
                                            }

                                            if (filteredBrands.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(brandScrollState),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    filteredBrands.forEach { b ->
                                                        val isSel = brand.trim().equals(b.trim(), ignoreCase = true)
                                                        WsPickerChip(label = b, selected = isSel) {
                                                            brand = if (isSel) "" else b
                                                        }
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text("No brand matching \"$brandSearchQuery\"", fontSize = 12.sp, color = Color(0xFF64748B))
                                                    Button(
                                                        onClick = {
                                                            val t = brandSearchQuery.trim()
                                                            if (t.isNotBlank()) { allBrandsList.add(t); brand = t; brandSearchQuery = "" }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) { Text("+ Add \"$brandSearchQuery\"", fontSize = 11.sp, color = Color.White) }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── Tags Section ──────────────────────────
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Sell, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            Text("Product tags", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                        }
                                        if (selectedTags.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Selected (${selectedTags.size}):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                TextButton(
                                                    onClick = { selectedTags = emptySet() },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                ) {
                                                    Text("Clear all", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                                                }
                                            }
                                        }
                                    }

                                    // Tag input + Add Tag
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newTagInput,
                                            onValueChange = { newTagInput = it },
                                            placeholder = { Text("Type tag name or choose below...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                                focusedBorderColor = Color(0xFF0F172A)
                                            )
                                        )
                                        if (newTagInput.trim().isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    val t = newTagInput.trim()
                                                    if (t.isNotBlank()) {
                                                        selectedTags = selectedTags + t
                                                        if (!allTagsList.any { it.equals(t, ignoreCase = true) }) allTagsList.add(t)
                                                        newTagInput = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF0F172A)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text("Add Tag", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    // Selected tag pills (FlowRow)
                                    if (selectedTags.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            selectedTags.forEach { tag ->
                                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0F172A)) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                                        Text(tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                        IconButton(onClick = { selectedTags = selectedTags - tag }, modifier = Modifier.size(16.dp)) {
                                                            Icon(Icons.Default.Close, contentDescription = "Remove tag", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Quick Tag Picker Card
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                WsCompactSearchField(
                                                    value = tagSearchQuery,
                                                    onValueChange = { tagSearchQuery = it },
                                                    placeholder = "Search tags...",
                                                    modifier = Modifier.weight(1f)
                                                )
                                                WsScrollArrow(Icons.AutoMirrored.Filled.ArrowBack, "Scroll left") {
                                                    scope.launch { tagScrollState.animateScrollTo((tagScrollState.value - 300).coerceAtLeast(0)) }
                                                }
                                                WsScrollArrow(Icons.Default.ArrowForward, "Scroll right") {
                                                    scope.launch { tagScrollState.animateScrollTo(tagScrollState.value + 300) }
                                                }
                                            }

                                            val currentTags = allTagsList.toList()
                                            val currentBrands = allBrandsList.toList()
                                            val filteredTags = remember(currentTags, currentBrands, tagSearchQuery) {
                                                val q = tagSearchQuery.trim()
                                                if (q.isBlank()) currentTags
                                                else {
                                                    val tMatches = currentTags.filter { it.contains(q, ignoreCase = true) }
                                                    val bMatches = currentBrands.filter { b ->
                                                        b.contains(q, ignoreCase = true) && !currentTags.any { it.equals(b, ignoreCase = true) }
                                                    }
                                                    tMatches + bMatches
                                                }
                                            }

                                            if (filteredTags.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(tagScrollState),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    filteredTags.forEach { t ->
                                                        val isSel = selectedTags.contains(t)
                                                        WsPickerChip(label = t, selected = isSel) {
                                                            selectedTags = if (isSel) selectedTags - t else selectedTags + t
                                                        }
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text("No tag matching \"$tagSearchQuery\"", fontSize = 12.sp, color = Color(0xFF64748B))
                                                    Button(
                                                        onClick = {
                                                            val t = tagSearchQuery.trim()
                                                            if (t.isNotBlank()) { allTagsList.add(t); selectedTags = selectedTags + t; tagSearchQuery = "" }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) { Text("+ Add \"$tagSearchQuery\"", fontSize = 11.sp, color = Color.White) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. Product Overview & Details Card (Full WYSIWYG) ──
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Product overview & details", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                    Text("WYSIWYG: Bold, underline, list & links", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }

                                SharedHtmlWysiwygEditor(
                                    value = overviewText,
                                    onChange = { overviewText = it },
                                    placeholder = "Summarize what the product is, who it is for, and what makes it notable...",
                                    minHeight = 120.dp
                                )
                            }
                        }
                    }

                    // ── 3. Product Pictures Card ──────────────────────────
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Product pictures", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))

                                WsDashedDropzone(
                                    onPickClick = { productImagePicker.launch() },
                                    onBrowseClick = { productImagePicker.launch() },
                                    onAppLibraryClick = { showAppLibraryForProduct = true }
                                )

                                if (productPictures.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        itemsIndexed(productPictures) { pIdx, pic ->
                                            WsPhotoThumbnail(
                                                picture = pic,
                                                onClick = {
                                                    activeLightboxImages = productPictures.toList()
                                                    activeLightboxIndex = pIdx
                                                },
                                                onRemove = { pictureToRemove = pic }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 4. FLEXIBLE DATA / Specifications Header ──────────
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("FLEXIBLE DATA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF94A3B8), letterSpacing = 1.2.sp)
                            Text("Specifications", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                            Text(
                                "Add weight, battery life, material, price, functions, or any specification this product needs.",
                                fontSize = 13.sp, color = Color(0xFF64748B)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WsActionPill(Icons.Default.ArrowUpward, "Move up", enabled = selectedSpecIndex > 0) { moveUp() }
                                WsActionPill(Icons.Default.ArrowDownward, "Move down", enabled = selectedSpecIndex in 0 until specsList.size - 1) { moveDown() }
                                WsActionPill(null, "A-Z") { sortAZ() }
                                WsActionPill(null, "Z-A") { sortZA() }
                            }

                            Button(
                                onClick = {
                                    val newId = "spec_${Random.nextLong(100000000, 999999999)}"
                                    val newSpec = SpecificationItem(
                                        id = newId,
                                        productId = productId,
                                        productTitle = productName,
                                        specName = "New Spec",
                                        details = "",
                                        unit = null,
                                        orderIndex = specsList.size + 1
                                    )
                                    specsList.add(newSpec)
                                    selectedSpecIndex = specsList.size - 1
                                    expandedSpecId = newId
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add specification", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // ── 5. Accordion Specification Cards (Keyed by spec.id) ─
                    itemsIndexed(specsList, key = { _, spec -> spec.id }) { index, spec ->
                        val isExpanded = expandedSpecId == spec.id
                        val isSelected = selectedSpecIndex == index

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                            ),
                            elevation = CardDefaults.cardElevation(if (isExpanded) 2.dp else 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Header row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSpecIndex = index
                                            expandedSpecId = if (isExpanded) null else spec.id
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0F172A))
                                            )
                                        }
                                    }

                                    Icon(
                                        if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9), modifier = Modifier.size(24.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            spec.specName.ifBlank { "Untitled spec" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val unit = spec.unit
                                        if (!unit.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFF1F5F9),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    unit.trim(),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF475569),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = { specToDeleteIndex = index }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete spec", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                    }
                                }

                                // Expanded form
                                if (isExpanded) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                        // 1. Specification name
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Specification name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                            OutlinedTextField(
                                                value = spec.specName,
                                                onValueChange = { specsList[index] = spec.copy(specName = it) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                                    focusedBorderColor = Color(0xFF0F172A)
                                                )
                                            )
                                        }

                                        // 2. Unit (optional) + Quick unit pills
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Unit (optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                                Text("e.g. g, inch, cm", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                            }
                                            OutlinedTextField(
                                                value = spec.unit ?: "",
                                                onValueChange = { specsList[index] = spec.copy(unit = it.ifBlank { null }) },
                                                placeholder = { Text("g, inch, cm, year, mAh...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                                    focusedBorderColor = Color(0xFF0F172A)
                                                )
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(top = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("Quick unit:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                                SHARED_PRESET_UNITS.forEach { u ->
                                                    val isSel = spec.unit?.trim().equals(u, ignoreCase = true)
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isSel) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                                        border = BorderStroke(1.dp, if (isSel) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
                                                        modifier = Modifier.clickable {
                                                            specsList[index] = spec.copy(unit = if (isSel) null else u)
                                                        }
                                                    ) {
                                                        Text(
                                                            u,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isSel) Color.White else Color(0xFF334155),
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Specification details (Full WYSIWYG Editor!)
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Specification details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                                Text("WYSIWYG: Bold, underline, list & links", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                            }
                                            SharedHtmlWysiwygEditor(
                                                value = spec.details,
                                                onChange = { newDetails ->
                                                    specsList[index] = specsList[index].copy(details = newDetails)
                                                },
                                                placeholder = "e.g. 60hour, Bluetooth, stick to phone, and ambient",
                                                minHeight = 100.dp
                                            )
                                        }

                                        // 4. Auto-Detect / Numeric / Text Mode selector banner (1:1 Android parity)
                                        val hasNumericCandidate = remember(spec.details) {
                                            val clean = sharedHtmlToPlainText(spec.details)
                                            !clean.contains('\n') && !clean.contains('•') && clean.length <= 45 && Regex("[0-9]").containsMatchIn(clean)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = when (spec.isNumeric) {
                                                            true -> Color(0xFF4F46E5)
                                                            false -> Color(0xFF475569)
                                                            null -> Color(0xFF0F172A)
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = when (spec.isNumeric) {
                                                                    true -> "#"
                                                                    false -> "Aa"
                                                                    null -> "⚡"
                                                                },
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Column {
                                                        Text(
                                                            text = when (spec.isNumeric) {
                                                                true -> "Numeric Comparison (Forced #)"
                                                                false -> "Text Mode (Forced Aa)"
                                                                null -> "Auto-Detect Mode"
                                                            },
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        Text(
                                                            text = when (spec.isNumeric) {
                                                                true -> "Parsed as numeric for matrix sorting & best-value calculation"
                                                                false -> "Treated strictly as text/descriptive without numeric sorting"
                                                                null -> if (hasNumericCandidate) "Auto: Candidate number detected in text" else "Auto: Descriptive text format"
                                                            },
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    listOf(
                                                        "Auto" to null,
                                                        "Number (#)" to true,
                                                        "Text (Aa)" to false
                                                    ).forEach { (label, modeVal) ->
                                                        val isModeSel = spec.isNumeric == modeVal
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (isModeSel) Color(0xFF0F172A) else Color.White,
                                                            border = BorderStroke(1.dp, if (isModeSel) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable {
                                                                    specsList[index] = specsList[index].copy(isNumeric = modeVal)
                                                                }
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                modifier = Modifier.padding(vertical = 6.dp),
                                                                textAlign = TextAlign.Center,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isModeSel) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (isModeSel) Color.White else Color(0xFF334155)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 5. Specification pictures
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Specification pictures", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                            WsDashedDropzone(
                                                onPickClick = { selectedSpecIndex = index; specImagePicker.launch() },
                                                onBrowseClick = { selectedSpecIndex = index; specImagePicker.launch() },
                                                onAppLibraryClick = { showAppLibraryForSpecIndex = index }
                                            )
                                            if (spec.pictures.isNotEmpty()) {
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    itemsIndexed(spec.pictures) { spIdx, spPic ->
                                                        WsPhotoThumbnail(
                                                            picture = spPic,
                                                            size = 96.dp,
                                                            onClick = {
                                                                activeLightboxImages = spec.pictures
                                                                activeLightboxIndex = spIdx
                                                            },
                                                            onRemove = {
                                                                val updated = spec.pictures.filter { it.id != spPic.id }
                                                                specsList[index] = spec.copy(pictures = updated)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Footer row
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Specification #${index + 1}", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                            OutlinedButton(
                                                onClick = { specToDeleteIndex = index },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete specification", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

// ─── Private helper composables ───────────────────────────────────────────────

/**
 * Compact single-line search field built on BasicTextField so placeholder and typed text
 * never get clipped vertically on macOS/iOS/Android.
 */
@Composable
private fun WsCompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 12.5.sp,
                        color = Color(0xFF0F172A),
                        fontFamily = FontFamily.Default
                    ),
                    cursorBrush = SolidColor(Color(0xFF0F172A)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WsScrollArrow(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun WsPickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF0F172A) else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF334155)
            )
        }
    }
}

@Composable
private fun WsActionPill(icon: ImageVector?, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) Color(0xFF0F172A) else Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color(0xFF0F172A) else Color(0xFFCBD5E1))
        }
    }
}

@Composable
private fun WsDashedDropzone(
    onPickClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onAppLibraryClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
            .padding(vertical = 18.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(30.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPickClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose pictures", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onBrowseClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF0F172A)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Browse files / storage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEEF2FF),
                border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                modifier = Modifier.clickable(onClick = onAppLibraryClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(14.dp))
                    Text("Or pick from App Photo Library", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
                }
            }

            Text("Select from photo library, downloads, or local files", fontSize = 11.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun WsPhotoThumbnail(
    picture: PictureItem,
    size: Dp = 120.dp,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        val model = resolveSharedImageModel(picture.url, picture.name)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = picture.name,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF94A3B8))
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .clickable(onClick = onRemove)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun SharedAppPhotoLibraryDialog(
    availablePictures: List<PictureItem>,
    onDismiss: () -> Unit,
    onSelectPictures: (List<PictureItem>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val uniqueImages = remember(availablePictures) {
        availablePictures.distinctBy { it.name.ifBlank { it.url } }
    }
    val filtered = remember(uniqueImages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) uniqueImages
        else uniqueImages.filter { it.name.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
                            Text("App Photo Library", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                        }
                        Text(
                            if (uniqueImages.isNotEmpty()) "${uniqueImages.size} photos in catalog & assets"
                            else "No photos found in catalog yet",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                WsCompactSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search photos by filename...",
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) "No photos matching \"$searchQuery\""
                            else "No photos in app library yet. Use 'Choose pictures' to pick from your computer.",
                            fontSize = 13.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered.size) { idx ->
                            val pic = filtered[idx]
                            val key = pic.id.ifBlank { pic.name }
                            val isSel = selectedIds.contains(key)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(
                                        width = if (isSel) 2.5.dp else 1.dp,
                                        color = if (isSel) Color(0xFF4F46E5) else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (isSel) selectedIds.remove(key) else selectedIds.add(key)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = resolveSharedImageModel(pic.url, pic.name),
                                    contentDescription = pic.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isSel) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF4F46E5),
                                        modifier = Modifier.size(22.dp).align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(2.dp))
                                    }
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                ) {
                                    Text(
                                        pic.name,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF64748B)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val chosen = filtered.filter { (it.id.ifBlank { it.name }) in selectedIds }
                            onSelectPictures(chosen)
                            onDismiss()
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Text("Attach ${if (selectedIds.isNotEmpty()) "(${selectedIds.size})" else ""}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
