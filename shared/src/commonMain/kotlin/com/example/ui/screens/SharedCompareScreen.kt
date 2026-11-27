package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.detectDeviceCategory
import com.example.model.resolveSharedImageModel
import com.example.ui.components.SharedFullScreenPhotoLightbox
import com.example.ui.components.parseSharedRichTextToAnnotatedString
import com.example.ui.components.sharedHtmlToPlainText

private val COLOR_CANVAS_BG = Color(0xFFF1F5F9)
private val COLOR_DIFF_BG = Color(0xFFFEFCE8)
private val COLOR_DIFF_BORDER = Color(0xFFFDE047)
private val COLOR_DIFF_DIVIDER = Color(0xFFFEF08A)
private val COLOR_SAME_BG = Color.White
private val COLOR_SAME_BORDER = Color(0xFFE2E8F0)
private val COLOR_SAME_DIVIDER = Color(0xFFF1F5F9)
private val COLOR_INDIGO = Color(0xFF4F46E5)
private val COLOR_SLATE_900 = Color(0xFF0F172A)
private val COLOR_SLATE_600 = Color(0xFF475569)
private val COLOR_SLATE_500 = Color(0xFF64748B)
private val COLOR_SLATE_400 = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCompareScreen(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    compareIds: List<String>,
    onToggleProduct: (String) -> Unit,
    onSetComparedIds: (List<String>) -> Unit = {},
    onClearAll: () -> Unit,
    onEditProduct: (ProductItem) -> Unit,
    onAddProductClick: () -> Unit,
    onNavigateToReviews: () -> Unit = {},
    onOpenAddReview: (ProductItem) -> Unit = { onNavigateToReviews() },
    modifier: Modifier = Modifier
) {
    // Record slot product IDs (nullable: null means empty slot waiting for selection)
    var recordSlots by remember {
        val initialList = mutableListOf<String?>()
        compareIds.forEach { initialList.add(it) }
        while (initialList.size < 2) initialList.add(null)
        mutableStateOf(initialList)
    }

    // Synchronize slots from external compareIds only when active items differ
    LaunchedEffect(compareIds) {
        val activeInSlots = recordSlots.filterNotNull()
        if (activeInSlots != compareIds) {
            val newList = mutableListOf<String?>()
            compareIds.forEach { newList.add(it) }
            while (newList.size < 2) newList.add(null)
            recordSlots = newList
        }
    }

    // Filter state (for TagBrandProductFilter and slot picker)
    var filterSearchQuery by remember { mutableStateOf("") }
    var selectedFilterTag by remember { mutableStateOf("All") }

    // Dialog & Lightbox state
    var slotIndexToPick by remember { mutableStateOf<Int?>(null) }
    var pickerSearchQuery by remember { mutableStateOf("") }
    var lightboxImages by remember { mutableStateOf<List<PictureItem>>(emptyList()) }
    var lightboxIndex by remember { mutableStateOf(0) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var slotToRemoveIndex by remember { mutableStateOf<Int?>(null) }

    // Higher / Lower sorting preferences & manual numeric (#) vs text (Aa) overrides per spec
    val specSortModes = remember { mutableStateMapOf<String, String>() }
    val specNumericOverrides = remember { mutableStateMapOf<String, Boolean?>() }

    val tagList = remember(products) {
        products.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val brandList = remember(products) {
        products.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // Filtered products for slot picker dialog
    val dialogFilteredProducts = remember(products, pickerSearchQuery, filterSearchQuery, selectedFilterTag) {
        val activeSearch = pickerSearchQuery.ifBlank { filterSearchQuery }.trim().lowercase()
        products.filter { p ->
            val matchesTag = selectedFilterTag == "All" ||
                p.tags.any { it.equals(selectedFilterTag, ignoreCase = true) } ||
                p.brand.equals(selectedFilterTag, ignoreCase = true) ||
                p.name.equals(selectedFilterTag, ignoreCase = true)
            val matchesSearch = activeSearch.isBlank() ||
                p.name.lowercase().contains(activeSearch) ||
                p.brand.lowercase().contains(activeSearch) ||
                p.tags.any { it.lowercase().contains(activeSearch) }
            matchesTag && matchesSearch
        }
    }

    // Active products currently chosen in slots
    val activeProducts = remember(recordSlots, products) {
        recordSlots.mapNotNull { id -> id?.let { pid -> products.find { it.id == pid } } }
    }

    // Specifications belonging to the active products
    val activeSpecs = remember(activeProducts, specifications) {
        val activeIds = activeProducts.map { it.id }.toSet()
        val activeTitles = activeProducts.map { it.name }.toSet()
        specifications.filter { it.productId in activeIds || it.productTitle in activeTitles }
    }

    val distinctSpecNames = remember(activeProducts, specifications) {
        computeSharedHierarchicalSpecOrder(activeProducts, specifications)
    }

    fun syncSlots(updated: List<String?>) {
        recordSlots = updated.toMutableList()
        onSetComparedIds(updated.filterNotNull())
    }

    // Full Screen Picture Lightbox
    if (lightboxImages.isNotEmpty()) {
        SharedFullScreenPhotoLightbox(
            images = lightboxImages,
            initialIndex = lightboxIndex,
            onDismiss = { lightboxImages = emptyList() }
        )
    }

    // Slot Record Picker Screen (Full Screen / Modal with Back Button)
    if (slotIndexToPick != null) {
        val slotIndex = slotIndexToPick!!
        Dialog(
            onDismissRequest = { slotIndexToPick = null; pickerSearchQuery = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF8FAFC)
            ) {
                Scaffold(
                    topBar = {
                        Surface(
                            color = Color.White,
                            border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = { slotIndexToPick = null; pickerSearchQuery = "" }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = COLOR_SLATE_900
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Select Record for Slot ${slotIndex + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = COLOR_SLATE_900
                                    )
                                    Text(
                                        text = "Choose a product to populate comparison column",
                                        fontSize = 12.sp,
                                        color = COLOR_SLATE_500
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = pickerSearchQuery,
                            onValueChange = { pickerSearchQuery = it },
                            placeholder = { Text("Search by name, tag, or brand...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = COLOR_SLATE_400) },
                            trailingIcon = {
                                if (pickerSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { pickerSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = COLOR_SLATE_400)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dialogFilteredProducts, key = { it.id }) { prod ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = recordSlots.toMutableList()
                                            while (slotIndex >= updated.size) updated.add(null)
                                            updated[slotIndex] = prod.id
                                            syncSlots(updated)
                                            slotIndexToPick = null
                                            pickerSearchQuery = ""
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF1F5F9),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            SharedProductThumb(prod)
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prod.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = COLOR_SLATE_900
                                            )
                                            Text(
                                                text = buildString {
                                                    append(prod.brand.ifBlank { "Generic" })
                                                    if (prod.tags.isNotEmpty()) {
                                                        append(" • ")
                                                        append(prod.tags.joinToString(", "))
                                                    }
                                                },
                                                fontSize = 12.sp,
                                                color = COLOR_SLATE_500,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = COLOR_INDIGO,
                                            modifier = Modifier.size(18.dp)
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

    // Confirmation dialog: Clear all comparison slots
    if (showClearAllConfirm) {
        val message = if (activeProducts.isNotEmpty()) {
            val namesStr = activeProducts.joinToString(", ") { "\"${it.name}\"" }
            "Are you sure you want to clear ${activeProducts.size} product${if (activeProducts.size > 1) "s" else ""} ($namesStr) from the comparison?"
        } else {
            "Are you sure you want to reset all comparison slots?"
        }
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear Comparison?", fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        recordSlots = mutableListOf(null, null)
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear all", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Confirmation dialog: Remove individual slot
    if (slotToRemoveIndex != null) {
        val index = slotToRemoveIndex!!
        val slotProd = recordSlots.getOrNull(index)?.let { id -> products.find { it.id == id } }
        val title = if (slotProd != null) "Remove from Comparison?" else "Remove Comparison Slot?"
        val message = if (slotProd != null) {
            "Are you sure you want to remove \"${slotProd.name}\" from Slot ${index + 1}?"
        } else {
            "Are you sure you want to remove comparison Slot ${index + 1}?"
        }
        AlertDialog(
            onDismissRequest = { slotToRemoveIndex = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = recordSlots.toMutableList()
                        if (index in updated.indices) {
                            if (updated.size > 2) updated.removeAt(index) else updated[index] = null
                            syncSlots(updated)
                        }
                        slotToRemoveIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { slotToRemoveIndex = null }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(COLOR_CANVAS_BG),
        contentAlignment = Alignment.TopCenter
    ) {
            val isTabletOrDesktop = maxWidth >= 600.dp
            val isLaptop = maxWidth >= 840.dp
            val defaultZoom = if (isTabletOrDesktop) 60 else 100
            var zoomPercent by rememberSaveable(isTabletOrDesktop) { mutableIntStateOf(defaultZoom) }
            var showZoomMenu by remember { mutableStateOf(false) }

            val maxContentWidth = (maxWidth * (zoomPercent / 100f)).coerceAtLeast(360.dp)

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. COMPARE WORKSPACE Header & Action Buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "COMPARE WORKSPACE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = COLOR_SLATE_400,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Choose records",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = COLOR_SLATE_900
                        )
                        Text(
                            text = "Search all 5 fields, compare pictures, and open multiple reviews.",
                            fontSize = 13.sp,
                            color = COLOR_SLATE_500
                        )

                        Spacer(Modifier.height(4.dp))

                        // Actions Row: + Add record, Clear all, Font Size [- 112% +], Zoom [- 60% +]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (recordSlots.size < 5) {
                                            val updated = recordSlots.toMutableList()
                                            updated.add(null)
                                            recordSlots = updated
                                        }
                                    },
                                    enabled = recordSlots.size < 5,
                                    colors = ButtonDefaults.buttonColors(containerColor = COLOR_INDIGO),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add record", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                if (recordSlots.any { it != null }) {
                                    OutlinedButton(
                                        onClick = { showClearAllConfirm = true },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = COLOR_SLATE_500),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = COLOR_SLATE_500)
                                    }
                                }
                            }

                            // Right Controls: Zoom [- 60% +]
                            ScaleStepperPill(
                                percent = zoomPercent,
                                defaultPercent = defaultZoom,
                                presets = listOf(50, 60, 65, 75, 85, 100, 115, 130, 150),
                                showMenu = showZoomMenu,
                                onToggleMenu = { showZoomMenu = it },
                                onDecrease = { zoomPercent = (zoomPercent - 10).coerceAtLeast(50) },
                                onIncrease = { zoomPercent = (zoomPercent + 10).coerceAtMost(160) },
                                onSelectPreset = { zoomPercent = it; showZoomMenu = false },
                                decreaseDescription = "Zoom out",
                                increaseDescription = "Zoom in"
                            )
                        }
                    }
                }

                // 2. Filter products by tag / brand / product Card
                item {
                    SharedTagBrandProductFilter(
                        tags = tagList,
                        brands = brandList,
                        productNames = products.map { it.name },
                        selectedTag = selectedFilterTag,
                        onSelectTag = { selectedFilterTag = it },
                        searchQuery = filterSearchQuery,
                        onSearchQueryChange = { filterSearchQuery = it }
                    )
                }

                // 3. Record Slots Cards (horizontal row on laptop/desktop, stacked on phone)
                if (isLaptop) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            recordSlots.indices.forEach { index ->
                                val slotProd = recordSlots[index]?.let { id -> products.find { it.id == id } }
                                val revCount = slotProd?.let { countProductReviews(it, reviews) } ?: 0
                                RecordSlotCardView(
                                    index = index,
                                    slotProd = slotProd,
                                    recordSlotsCount = recordSlots.size,
                                    reviewsCount = revCount,
                                    onSelectSlot = { slotIndexToPick = index },
                                    onRemoveSlot = { slotToRemoveIndex = index },
                                    onViewProduct = onEditProduct,
                                    onMoveUp = if (index > 0 && slotProd != null) {
                                        { syncSlots(swapSlot(recordSlots, index, index - 1)) }
                                    } else null,
                                    onMoveDown = if (index < recordSlots.size - 1 && slotProd != null) {
                                        { syncSlots(swapSlot(recordSlots, index, index + 1)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    items(recordSlots.indices.toList()) { index ->
                        val slotProd = recordSlots[index]?.let { id -> products.find { it.id == id } }
                        val revCount = slotProd?.let { countProductReviews(it, reviews) } ?: 0
                        RecordSlotCardView(
                            index = index,
                            slotProd = slotProd,
                            recordSlotsCount = recordSlots.size,
                            reviewsCount = revCount,
                            onSelectSlot = { slotIndexToPick = index },
                            onRemoveSlot = { slotToRemoveIndex = index },
                            onViewProduct = onEditProduct,
                            onMoveUp = if (index > 0 && slotProd != null) {
                                { syncSlots(swapSlot(recordSlots, index, index - 1)) }
                            } else null,
                            onMoveDown = if (index < recordSlots.size - 1 && slotProd != null) {
                                { syncSlots(swapSlot(recordSlots, index, index + 1)) }
                            } else null
                        )
                    }
                }

                // =================================================================
                // 4. SIDE BY SIDE COMPARISON SECTION (Shown when 2+ products chosen)
                // =================================================================
                if (activeProducts.size >= 2) {
                    val isBrandDiff = activeProducts.map { it.brand.trim().lowercase() }.distinct().size > 1
                    val isTagsDiff = activeProducts.map { it.tags.sorted().joinToString(",").lowercase() }.distinct().size > 1
                    val isOverviewDiff = activeProducts.map { it.overview.trim().lowercase() }.distinct().size > 1
                    val isPicturesDiff = activeProducts.map { p -> p.pictures.map { it.url }.sorted().joinToString(",") }.distinct().size > 1
                    val diffSpecsCount = distinctSpecNames.count { specName ->
                        val vals = activeProducts.map { p ->
                            findSpecForProduct(activeSpecs, p, specName)?.details.orEmpty().trim().lowercase()
                        }.distinct()
                        vals.size > 1
                    }
                    val totalDifferCount = (if (isOverviewDiff) 1 else 0) +
                        (if (isPicturesDiff) 1 else 0) +
                        (if (isBrandDiff) 1 else 0) +
                        (if (isTagsDiff) 1 else 0) +
                        diffSpecsCount
                    val totalFieldsCount = 4 + distinctSpecNames.size

                    // Selected Products Reorder Card
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(COLOR_SAME_BORDER)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Selected products (${activeProducts.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = COLOR_SLATE_900
                                    )
                                    Text(
                                        "Reorder comparison",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = COLOR_SLATE_500
                                    )
                                }

                                activeProducts.forEachIndexed { pIdx, product ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF1F5F9),
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                SharedDeviceGraphicPlaceholder(
                                                    title = product.name,
                                                    brand = product.brand,
                                                    tags = product.tags,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "${pIdx + 1}. ${product.name}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = COLOR_SLATE_900,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    product.brand.ifBlank { "Generic" },
                                                    fontSize = 12.sp,
                                                    color = COLOR_SLATE_500,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val activeList = recordSlots.filterNotNull().toMutableList()
                                                        if (pIdx < activeList.size - 1) {
                                                            val swapped = swapSlot(activeList, pIdx, pIdx + 1)
                                                            val updated = swapped.toMutableList<String?>()
                                                            while (updated.size < recordSlots.size) updated.add(null)
                                                            syncSlots(updated)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp),
                                                    enabled = pIdx < activeProducts.size - 1
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowDownward,
                                                        contentDescription = "Down",
                                                        tint = if (pIdx < activeProducts.size - 1) COLOR_INDIGO else Color(0xFFCBD5E1),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val activeList = recordSlots.filterNotNull().toMutableList()
                                                        if (pIdx > 0 && pIdx < activeList.size) {
                                                            val swapped = swapSlot(activeList, pIdx, pIdx - 1)
                                                            val updated = swapped.toMutableList<String?>()
                                                            while (updated.size < recordSlots.size) updated.add(null)
                                                            syncSlots(updated)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp),
                                                    enabled = pIdx > 0
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowUpward,
                                                        contentDescription = "Up",
                                                        tint = if (pIdx > 0) COLOR_INDIGO else Color(0xFFCBD5E1),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFEEF2FF),
                                                    modifier = Modifier
                                                        .height(28.dp)
                                                        .clickable { onEditProduct(product) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Visibility,
                                                            contentDescription = null,
                                                            tint = COLOR_INDIGO,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = COLOR_INDIGO)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SIDE BY SIDE Header & Summary Badges
                    item {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SIDE BY SIDE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = COLOR_SLATE_400,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Complete comparison",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = COLOR_SLATE_900
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, COLOR_SAME_BORDER)
                                ) {
                                    Text(
                                        text = "${activeProducts.size} selected",
                                        color = COLOR_SLATE_900,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, COLOR_DIFF_BORDER)
                                ) {
                                    Text(
                                        text = "$totalDifferCount differences",
                                        color = Color(0xFFB45309),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "$totalFieldsCount fields shown, $totalDifferCount differ",
                                fontSize = 13.sp,
                                color = COLOR_SLATE_500
                            )
                        }
                    }

                    // 1. Overview Comparison Card
                    item {
                        CompareFieldCard(
                            title = "Overview",
                            icon = Icons.Default.Description,
                            isDifferent = isOverviewDiff
                        ) {
                            activeProducts.forEachIndexed { idx, p ->
                                if (idx > 0) Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ProductNameLabel(p.name)
                                    if (p.overview.isNotBlank()) {
                                        Text(
                                            text = parseSharedRichTextToAnnotatedString(
                                                p.overview,
                                                baseColor = Color(0xFF1E293B),
                                                linkColor = COLOR_INDIGO
                                            ),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    } else {
                                        Text(
                                            text = "No overview provided",
                                            fontSize = 12.sp,
                                            color = COLOR_SLATE_400,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Pictures Comparison Card
                    item {
                        CompareFieldCard(
                            title = "Pictures",
                            icon = Icons.Default.PhotoLibrary,
                            isDifferent = isPicturesDiff
                        ) {
                            activeProducts.forEachIndexed { idx, p ->
                                if (idx > 0) Spacer(Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProductNameLabel(p.name)
                                    if (p.pictures.isEmpty()) {
                                        Text(
                                            text = "No pictures available",
                                            fontSize = 12.sp,
                                            color = COLOR_SLATE_400,
                                            fontStyle = FontStyle.Italic
                                        )
                                    } else {
                                        p.pictures.forEachIndexed { picIdx, pic ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                                border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(200.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = resolveSharedImageModel(pic.url, pic.name),
                                                        contentDescription = pic.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit
                                                    )

                                                    Surface(
                                                        onClick = {
                                                            lightboxImages = p.pictures
                                                            lightboxIndex = picIdx
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xD90F172A),
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(8.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.OpenInFull,
                                                                contentDescription = "View full screen",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = "View full screen",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    if (pic.name.isNotBlank()) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xF2FFFFFF),
                                                            border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(8.dp)
                                                        ) {
                                                            Text(
                                                                text = pic.name,
                                                                fontSize = 11.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = Color(0xFF334155),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

                    // 3. Brand Comparison Card
                    item {
                        CompareFieldCard(
                            title = "Brand",
                            icon = Icons.Default.Sell,
                            isDifferent = isBrandDiff
                        ) {
                            activeProducts.forEachIndexed { idx, p ->
                                if (idx > 0) Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ProductNameLabel(p.name)
                                    Text(
                                        text = p.brand.ifBlank { "Generic" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = COLOR_SLATE_900
                                    )
                                }
                            }
                        }
                    }

                    // 4. Tags Comparison Card
                    item {
                        CompareFieldCard(
                            title = "Tags",
                            icon = Icons.Default.Sell,
                            isDifferent = isTagsDiff,
                            diffBadgeText = "DIFFERENT"
                        ) {
                            activeProducts.forEachIndexed { idx, p ->
                                if (idx > 0) Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    ProductNameLabel(p.name)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (p.tags.isEmpty()) {
                                            Text("No tags", fontSize = 12.sp, color = COLOR_SLATE_400, maxLines = 1, softWrap = false)
                                        } else {
                                            p.tags.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFF1F5F9)
                                                ) {
                                                    Text(
                                                        tag,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        color = Color(0xFF334155),
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. User Reviews Comparison Card
                    val isReviewsDiff = activeProducts.map { countProductReviews(it, reviews) }.distinct().size > 1
                    item {
                        CompareFieldCard(
                            title = "User reviews",
                            icon = Icons.Default.ChatBubbleOutline,
                            isDifferent = isReviewsDiff
                        ) {
                            activeProducts.forEachIndexed { idx, p ->
                                if (idx > 0) Spacer(Modifier.height(10.dp))
                                val linkedReviews = reviews.filter { r ->
                                    p.id in r.productIds || r.productTitles.any { it.equals(p.name, ignoreCase = true) }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ProductNameLabel(p.name)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (linkedReviews.isNotEmpty()) {
                                            Text(
                                                text = "${linkedReviews.size} review${if (linkedReviews.size == 1) "" else "s"} available",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = COLOR_SLATE_900
                                            )
                                            OutlinedButton(
                                                onClick = onNavigateToReviews,
                                                shape = RoundedCornerShape(20.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    tint = COLOR_INDIGO,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Read review", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = COLOR_SLATE_900)
                                            }
                                        } else {
                                            Text(
                                                text = "No linked reviews",
                                                fontSize = 13.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = COLOR_SLATE_400
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color(0xFFEEF2FF).copy(alpha = 0.6f),
                                                border = BorderStroke(1.dp, Color(0xFF818CF8)),
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .clickable { onOpenAddReview(p) }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = COLOR_INDIGO, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Write review", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = COLOR_INDIGO)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Specifications Comparison Cards (Numeric Bars, Higher/Lower, Number/Text Toggle, Spec Pictures)
                    items(distinctSpecNames) { specName ->
                        val values = activeProducts.map { p -> findSpecForProduct(activeSpecs, p, specName) }
                        val isSpecDiff = values.map { (it?.details ?: "").trim().lowercase() }.distinct().size > 1
                        val rowOverride = specNumericOverrides[specName]

                        val parsedList = remember(values, specName, rowOverride) {
                            values.map { s ->
                                parseSharedNumericSpec(
                                    details = s?.details,
                                    specUnit = s?.unit,
                                    specName = specName,
                                    isNumeric = rowOverride ?: s?.isNumeric
                                )
                            }
                        }

                        val isNumericSpec = remember(parsedList, values, rowOverride) {
                            when {
                                rowOverride != null -> rowOverride
                                values.any { it?.isNumeric == true } -> true
                                values.any { it?.isNumeric == false } && values.none { it?.isNumeric == true } -> false
                                else -> parsedList.count { it.hasNumeric && !it.rawText.contains('\n') && it.rawText.length <= 45 } >= 2
                            }
                        }

                        val hasAnyNumeric = isNumericSpec && parsedList.any { it.hasNumeric }

                        val detectedUnit = remember(parsedList, values, specName) {
                            parsedList.firstOrNull { it.displayUnit.isNotBlank() }?.displayUnit
                                ?: values.firstOrNull { !it?.unit.isNullOrBlank() }?.unit
                                ?: specifications.find { it.specName.equals(specName, ignoreCase = true) && !it.unit.isNullOrBlank() }?.unit.orEmpty()
                        }

                        val specKey = specName.trim().lowercase()
                        val currentMode = getSharedSpecBestValueMode(specName, detectedUnit, specSortModes)
                        val numericItems = parsedList.filter { it.hasNumeric }
                        val maxNumeric = remember(numericItems) {
                            if (numericItems.isNotEmpty()) numericItems.maxOf { it.value } else 1f
                        }
                        val bestNumeric = remember(numericItems, currentMode) {
                            if (numericItems.isNotEmpty()) {
                                if (currentMode == "higher") numericItems.maxOf { it.value } else numericItems.minOf { it.value }
                            } else null
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSpecDiff) COLOR_DIFF_BG else COLOR_SAME_BG),
                            border = BorderStroke(1.dp, if (isSpecDiff) COLOR_DIFF_BORDER else COLOR_SAME_BORDER),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Card Header Row: Spec Name, Unit, Number ON/OFF Toggle, DIFFERENT Badge, Higher/Lower Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (hasAnyNumeric) Icons.Default.BarChart else Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = if (hasAnyNumeric) Color(0xFF6366F1) else COLOR_INDIGO,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = specName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = COLOR_SLATE_900,
                                        maxLines = 1,
                                        softWrap = false
                                    )

                                    if (detectedUnit.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, COLOR_SAME_BORDER)
                                        ) {
                                            Text(
                                                text = detectedUnit,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Interactive Number ON/OFF Pill Toggle
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.clickable {
                                            specNumericOverrides[specName] = when (rowOverride) {
                                                null -> !isNumericSpec
                                                true -> false
                                                false -> true
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isNumericSpec) COLOR_INDIGO else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = if (isNumericSpec) "#" else "Aa",
                                                        color = if (isNumericSpec) Color.White else Color(0xFF334155),
                                                        fontSize = if (isNumericSpec) 10.sp else 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (isNumericSpec) "Number ON" else "Number OFF",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isNumericSpec) COLOR_INDIGO else Color(0xFF334155)
                                            )
                                            Text(
                                                text = if (isNumericSpec) "⇄ Text" else "⇄ Number",
                                                fontSize = 9.sp,
                                                color = COLOR_SLATE_500
                                            )
                                        }
                                    }

                                    if (isSpecDiff) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFACC15),
                                            border = BorderStroke(1.dp, Color(0xFFEAB308))
                                        ) {
                                            Text(
                                                text = "DIFFERENT",
                                                color = Color(0xFF451A03),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    if (hasAnyNumeric) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("higher" to "Higher", "lower" to "Lower").forEach { (mode, label) ->
                                                    val selected = currentMode == mode
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (selected) Color.White else Color.Transparent,
                                                        shadowElevation = if (selected) 1.dp else 0.dp,
                                                        modifier = Modifier.clickable { specSortModes[specKey] = mode }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (selected) COLOR_SLATE_900 else COLOR_SLATE_500,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = if (isSpecDiff) COLOR_DIFF_DIVIDER else COLOR_SAME_DIVIDER)

                                // Product Rows stacked vertically inside Card
                                activeProducts.forEachIndexed { i, p ->
                                    if (i > 0) {
                                        HorizontalDivider(
                                            color = if (isSpecDiff) COLOR_DIFF_DIVIDER.copy(alpha = 0.7f) else COLOR_SAME_DIVIDER,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                    val s = values[i]
                                    val parsed = parsedList[i]
                                    val isBest = parsed.hasNumeric && parsed.value == bestNumeric && numericItems.size > 1

                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProductNameLabel(p.name)
                                            if (isBest) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFECFDF5),
                                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                                                ) {
                                                    Text(
                                                        text = "🏆 Best",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF047857),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (parsed.hasNumeric && isNumericSpec) {
                                            val displayNum = if (parsed.value % 1f == 0f) {
                                                parsed.value.toLong().toString()
                                            } else {
                                                parsed.value.toString()
                                            }
                                            Text(
                                                text = if (parsed.displayUnit.isNotBlank()) "$displayNum ${parsed.displayUnit}" else displayNum,
                                                fontSize = 15.sp,
                                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isBest) COLOR_SLATE_900 else Color(0xFF334155)
                                            )

                                            if (maxNumeric > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFFE2E8F0).copy(alpha = 0.7f))
                                                ) {
                                                    val ratio = (parsed.value / maxNumeric).coerceIn(0.04f, 1f)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction = ratio)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isBest) Color(0xFF10B981) else COLOR_SLATE_600)
                                                    )
                                                }
                                            }
                                        } else if (parsed.rawText.isNotBlank()) {
                                            val displayContent = s?.details?.ifBlank { null } ?: parsed.rawText
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(modifier = Modifier.weight(1f, fill = false)) {
                                                    Text(
                                                        text = parseSharedRichTextToAnnotatedString(
                                                            displayContent,
                                                            baseColor = if (isBest) COLOR_SLATE_900 else Color(0xFF334155),
                                                            linkColor = COLOR_INDIGO
                                                        ),
                                                        fontSize = 14.sp,
                                                        lineHeight = 19.sp
                                                    )
                                                }
                                                SharedSpecUnitBadge(
                                                    unit = s?.unit?.ifBlank { null } ?: detectedUnit.ifBlank { null },
                                                    details = displayContent
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "No data",
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 13.sp,
                                                color = COLOR_SLATE_400
                                            )
                                        }

                                        // Spec Image Thumbnails
                                        val specPics = s?.pictures ?: emptyList()
                                        if (specPics.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "SPECIFICATION PICTURES (${specPics.size})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = COLOR_SLATE_500,
                                                letterSpacing = 0.5.sp
                                            )
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                items(specPics.size) { picIdx ->
                                                    val pic = specPics[picIdx]
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 170.dp, height = 115.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color(0xFFF1F5F9))
                                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                                            .clickable {
                                                                lightboxImages = specPics
                                                                lightboxIndex = picIdx
                                                            }
                                                    ) {
                                                        AsyncImage(
                                                            model = resolveSharedImageModel(pic.url, pic.name),
                                                            contentDescription = pic.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )

                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color.Black.copy(alpha = 0.7f),
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(6.dp)
                                                                .clickable {
                                                                    lightboxImages = specPics
                                                                    lightboxIndex = picIdx
                                                                }
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.OpenInFull,
                                                                    contentDescription = "Full screen",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(10.dp)
                                                                )
                                                                Spacer(Modifier.width(3.dp))
                                                                Text("Full screen", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        Surface(
                                                            color = Color.Black.copy(alpha = 0.55f),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .align(Alignment.BottomCenter)
                                                        ) {
                                                            Text(
                                                                text = pic.name.ifBlank { "IMG_${(s?.id ?: "0000").takeLast(4)}.png" },
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
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

// ─────────────────────────────────────────────────────────────────────────────
// Reusable UI Building Blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScaleStepperPill(
    percent: Int,
    defaultPercent: Int,
    presets: List<Int>,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    decreaseDescription: String,
    increaseDescription: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            IconButton(onClick = onDecrease, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, contentDescription = decreaseDescription, modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
            }
            Box {
                Text(
                    text = "$percent%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clickable { onToggleMenu(true) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { onToggleMenu(false) }) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (preset == defaultPercent) "$preset% (Default)" else "$preset%",
                                    fontWeight = if (preset == percent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (preset == percent) COLOR_INDIGO else Color(0xFF1E293B)
                                )
                            },
                            onClick = { onSelectPreset(preset) }
                        )
                    }
                }
            }
            IconButton(onClick = onIncrease, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = increaseDescription, modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
            }
        }
    }
}

@Composable
private fun CompareFieldCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDifferent: Boolean,
    diffBadgeText: String = "Different",
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDifferent) COLOR_DIFF_BG else COLOR_SAME_BG),
        border = BorderStroke(1.dp, if (isDifferent) COLOR_DIFF_BORDER else COLOR_SAME_BORDER),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = COLOR_INDIGO, modifier = Modifier.size(16.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = COLOR_SLATE_900)
                }
                if (isDifferent) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, COLOR_DIFF_BORDER)
                    ) {
                        Text(
                            diffBadgeText,
                            color = Color(0xFFB45309),
                            fontSize = if (diffBadgeText == "DIFFERENT") 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = if (isDifferent) COLOR_DIFF_DIVIDER else COLOR_SAME_DIVIDER)
            content()
        }
    }
}

@Composable
private fun ProductNameLabel(name: String) {
    Text(
        text = name.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = COLOR_SLATE_500,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SharedProductThumb(product: ProductItem) {
    val pic = product.pictures.firstOrNull()
    val model = pic?.let { resolveSharedImageModel(it.url, it.name) }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = product.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(3.dp)
        )
    } else {
        SharedDeviceGraphicPlaceholder(
            title = product.name,
            brand = product.brand,
            tags = product.tags,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SharedDeviceGraphicPlaceholder(
    title: String,
    brand: String,
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    val icon = when (detectDeviceCategory(title, brand, tags)) {
        "mic" -> Icons.Default.Mic
        "glasses" -> Icons.Default.Visibility
        "laptop" -> Icons.Default.LaptopMac
        "watch" -> Icons.Default.Watch
        "headphones" -> Icons.Default.Headphones
        "phone" -> Icons.Default.Smartphone
        else -> Icons.Default.Inventory2
    }
    Box(
        modifier = modifier.background(Color(0xFFEEF2FF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SharedSpecUnitBadge(unit: String?, details: String) {
    val cleanUnit = unit?.trim().orEmpty()
    if (cleanUnit.isEmpty()) return
    val plain = sharedHtmlToPlainText(details).lowercase()
    if (plain.contains(cleanUnit.lowercase())) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, COLOR_SAME_BORDER),
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(
            text = cleanUnit,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF334155),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Record Slot Card View (Matches Android CompareScreen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RecordSlotCardView(
    index: Int,
    slotProd: ProductItem?,
    recordSlotsCount: Int,
    reviewsCount: Int = 0,
    onSelectSlot: () -> Unit,
    onRemoveSlot: () -> Unit,
    onViewProduct: (ProductItem) -> Unit = {},
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, COLOR_SAME_BORDER),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECORD ${index + 1}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = COLOR_SLATE_400,
                    letterSpacing = 0.8.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (slotProd != null) {
                        if (onMoveUp != null) {
                            IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move slot up", tint = COLOR_INDIGO, modifier = Modifier.size(15.dp))
                            }
                        }
                        if (onMoveDown != null) {
                            IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move slot down", tint = COLOR_INDIGO, modifier = Modifier.size(15.dp))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Transparent,
                            modifier = Modifier.clickable { onViewProduct(slotProd) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = "View product", tint = COLOR_INDIGO, modifier = Modifier.size(14.dp))
                                Text("View product", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = COLOR_INDIGO)
                            }
                        }
                    }

                    if (recordSlotsCount > 2 || slotProd != null) {
                        IconButton(onClick = onRemoveSlot, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = if (recordSlotsCount > 2) "Remove slot" else "Clear slot",
                                tint = COLOR_SLATE_400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (slotProd == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFAFAFA),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectSlot() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = COLOR_SLATE_500, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Search and select a record", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = COLOR_SLATE_900)
                            Text("Tap to filter and choose a product to compare", fontSize = 12.sp, color = COLOR_SLATE_500)
                        }
                        Icon(Icons.Default.UnfoldMore, contentDescription = "Select", tint = COLOR_SLATE_400, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectSlot() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, COLOR_SAME_BORDER),
                            modifier = Modifier.size(48.dp)
                        ) {
                            SharedProductThumb(slotProd)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slotProd.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = COLOR_SLATE_900,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text("✓ Selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                Text(
                                    text = "/ ${slotProd.brand.ifBlank { "Generic" }} / $reviewsCount reviews",
                                    fontSize = 12.sp,
                                    color = COLOR_SLATE_500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(Icons.Default.UnfoldMore, contentDescription = "Change product", tint = COLOR_SLATE_400, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pure KMP Helpers (Spec Ordering, Numeric Parsing, Reviews Count, Slot Swap)
// ─────────────────────────────────────────────────────────────────────────────

private fun swapSlot(list: List<String?>, from: Int, to: Int): List<String?> {
    val updated = list.toMutableList()
    val temp = updated[from]
    updated[from] = updated[to]
    updated[to] = temp
    return updated
}

private fun countProductReviews(product: ProductItem, reviews: List<ReviewItem>): Int =
    reviews.count { product.id in it.productIds || it.productTitles.any { t -> t.equals(product.name, ignoreCase = true) } }

private fun findSpecForProduct(
    specs: List<SpecificationItem>,
    product: ProductItem,
    specName: String
): SpecificationItem? = specs.find {
    (it.productId == product.id || it.productTitle == product.name) &&
        it.specName.equals(specName, ignoreCase = true)
}

private fun computeSharedHierarchicalSpecOrder(
    orderedProducts: List<ProductItem>,
    allSpecifications: List<SpecificationItem>
): List<String> {
    val result = mutableListOf<String>()
    val seenLower = mutableSetOf<String>()
    orderedProducts.forEach { prod ->
        val pSpecs = allSpecifications
            .filter { s ->
                (s.productId.isNotBlank() && s.productId == prod.id) ||
                    (s.productTitle.isNotBlank() && s.productTitle.trim().equals(prod.name.trim(), ignoreCase = true))
            }
            .sortedBy { it.effectiveOrder }
        pSpecs.forEach { s ->
            val trimmed = s.specName.trim()
            if (trimmed.isEmpty()) return@forEach
            val lower = trimmed.lowercase()
            if (seenLower.add(lower)) {
                result.add(trimmed)
            }
        }
    }
    return result
}

private data class SharedParsedNumericSpec(
    val hasNumeric: Boolean,
    val value: Float,
    val unit: String,
    val displayUnit: String,
    val rawText: String
)

private fun parseSharedNumericSpec(
    details: String?,
    specUnit: String?,
    specName: String,
    isNumeric: Boolean? = null
): SharedParsedNumericSpec {
    if (details.isNullOrBlank()) {
        return SharedParsedNumericSpec(false, 0f, specUnit ?: "", specUnit ?: "", "")
    }
    val clean = sharedHtmlToPlainText(details)
    if (clean.isBlank()) {
        return SharedParsedNumericSpec(false, 0f, specUnit ?: "", specUnit ?: "", "")
    }
    val preferredUnit = specUnit?.trim() ?: ""
    if (isNumeric == false) {
        return SharedParsedNumericSpec(false, 0f, preferredUnit, preferredUnit, clean)
    }

    val currencyMatch = Regex("""^([$€£¥₹]|Rm|RM)\s*([0-9,]+(?:\.[0-9]+)?)""").find(clean)
    if (currencyMatch != null) {
        val sym = currencyMatch.groupValues[1]
        val num = currencyMatch.groupValues[2].replace(",", "").toFloatOrNull()
        if (num != null) {
            val u = if (preferredUnit.isNotBlank()) preferredUnit else sym
            return SharedParsedNumericSpec(true, num, u, u, clean)
        }
    }

    if (isNumeric != true && (clean.contains('\n') || clean.contains('•') || clean.length > 45)) {
        return SharedParsedNumericSpec(false, 0f, preferredUnit, preferredUnit, clean)
    }

    val regexPattern = if (isNumeric == true) {
        Regex("""([0-9,]+(?:\.[0-9]+)?)\s*([a-zA-Z$€£%°/"″]+)?""")
    } else {
        Regex("""^\s*([0-9,]+(?:\.[0-9]+)?)\s*([a-zA-Z$€£%°/"″]+)?""")
    }

    val generalMatch = regexPattern.find(clean)
    if (generalMatch != null) {
        val num = generalMatch.groupValues[1].replace(",", "").toFloatOrNull()
        if (num != null) {
            var rawUnit = if (preferredUnit.isNotBlank()) preferredUnit else generalMatch.groupValues[2]
            if (rawUnit.isBlank()) {
                val bracketMatch = Regex("""\(([^)]+)\)""").find(specName)
                if (bracketMatch != null) rawUnit = bracketMatch.groupValues[1]
            }
            rawUnit = rawUnit.trim()
            val lower = rawUnit.lowercase()
            val normalizedUnit = when {
                lower in listOf("g", "gram", "grams") -> "g"
                lower == "kg" -> "kg"
                lower in listOf("inch", "inches", "\"", "″") -> "inch"
                lower == "cm" -> "cm"
                lower == "mm" -> "mm"
                lower in listOf("year", "years") -> "year"
                lower in listOf("month", "months") -> "month"
                lower in listOf("hour", "hours", "hrs", "hr") -> "hrs"
                lower == "mah" -> "mAh"
                lower == "hz" -> "Hz"
                else -> rawUnit
            }
            return SharedParsedNumericSpec(true, num, normalizedUnit, normalizedUnit, clean)
        }
    }

    return SharedParsedNumericSpec(false, 0f, preferredUnit, preferredUnit, clean)
}

private fun getSharedSpecBestValueMode(
    specName: String,
    unit: String,
    specSortModes: Map<String, String>
): String {
    val specKey = specName.trim().lowercase()
    specSortModes[specKey]?.let { return it }
    val lowerName = specName.lowercase()
    val lowerUnit = unit.lowercase()
    return if (
        lowerName.contains("weight") ||
        lowerName.contains("weigh") ||
        lowerName.contains("gram") ||
        lowerName.contains("price") ||
        lowerName.contains("cost") ||
        lowerName.contains("thickness") ||
        lowerName.contains("latency") ||
        lowerName.contains("delay") ||
        lowerUnit == "g" ||
        lowerUnit == "kg" ||
        lowerUnit == "$" ||
        lowerUnit == "€" ||
        lowerUnit == "£"
    ) "lower" else "higher"
}
