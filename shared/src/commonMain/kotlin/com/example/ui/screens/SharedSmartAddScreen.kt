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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.data.CrossPlatformBackupManager
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.SpecificationItem
import com.example.model.detectDeviceCategory
import com.example.model.resolveSharedImageModel
import com.example.ui.components.SharedFullScreenPhotoLightbox
import com.example.ui.components.SharedHtmlWysiwygEditor
import com.example.ui.components.parseSharedRichTextToAnnotatedString
import com.example.ui.components.sharedHtmlToPlainText
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSmartCreateScreen(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    compareIds: List<String> = emptyList(),
    onViewProduct: (ProductItem) -> Unit = {},
    onCreateAndCompare: (name: String, brand: String, orderedSpecNames: List<String>, newProductId: String) -> Unit,
    onAddSpecificationInline: (productId: String, productTitle: String, specName: String, details: String, unit: String?) -> Unit,
    onUpdateInlineSpec: (productId: String, productTitle: String, specName: String, details: String, pictures: List<PictureItem>, unit: String?, isNumeric: Boolean?) -> Unit,
    onDeleteSpecInline: (productId: String, specName: String) -> Unit,
    onUpdateProductBrand: (productId: String, brand: String) -> Unit,
    onUpdateProductTags: (productId: String, tags: List<String>) -> Unit,
    onUpdateSpecsOrder: (productId: String, orderedSpecs: List<SpecificationItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var filterSearchQuery by remember { mutableStateOf("") }
    var slotSearchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf("All") }
    var slotPickerIndex by remember { mutableStateOf<Int?>(null) }
    var slotToRemoveIndex by remember { mutableStateOf<Int?>(null) }
    var showResetSlotsConfirm by remember { mutableStateOf(false) }
    var specToDeleteInline by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Slots tracking
    val productSlots = remember {
        mutableStateListOf<String?>().apply {
            if (compareIds.isNotEmpty()) {
                compareIds.forEach { add(it) }
                if (size == 1) add(null)
            } else if (products.size >= 2) {
                add(products[0].id)
                add(products[1].id)
            } else if (products.isNotEmpty()) {
                add(products[0].id)
                add(null)
            } else {
                add(null)
                add(null)
            }
        }
    }

    val newlyCreatedIds = remember { mutableStateListOf<String>() }
    val localNewProducts = remember { mutableStateListOf<ProductItem>() }
    var selectedProductForSpecOrder by remember { mutableStateOf<ProductItem?>(null) }

    // Quick intake inputs
    var newProductName by remember { mutableStateOf("") }
    var targetProductForSpecId by remember { mutableStateOf(products.firstOrNull()?.id ?: "") }
    var newSpecName by remember { mutableStateOf("") }
    var newSpecDetails by remember { mutableStateOf("") }

    val allAvailableProducts = remember(products, localNewProducts.toList()) {
        val existingIds = products.map { it.id }.toSet()
        products + localNewProducts.filter { it.id !in existingIds }
    }

    // Inline spec editor state
    var activeInlineEditKey by remember { mutableStateOf<String?>(null) }
    var inlineSpecName by remember { mutableStateOf("") }
    var inlineSpecDetails by remember { mutableStateOf("") }
    var inlineSpecUnit by remember { mutableStateOf("") }
    var inlineIsNumeric by remember { mutableStateOf(false) }
    val inlineSpecPictures = remember { mutableStateListOf<PictureItem>() }
    var modalSpecItem by remember { mutableStateOf<Triple<ProductItem, String, SpecificationItem?>?>(null) }
    var showAppLibraryDialog by remember { mutableStateOf(false) }

    // Inline brand / tags editing state
    var editingBrandProductId by remember { mutableStateOf<String?>(null) }
    var editingBrandText by remember { mutableStateOf("") }
    var editingTagsProductId by remember { mutableStateOf<String?>(null) }
    var editingTagsText by remember { mutableStateOf("") }

    // Lightbox state
    var specLightboxImages by remember { mutableStateOf<List<PictureItem>>(emptyList()) }
    var specLightboxIndex by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val filePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Multiple()
    ) { files ->
        files?.forEach { file ->
            coroutineScope.launch {
                runCatching {
                    val bytes = file.readBytes()
                    val pic = CrossPlatformBackupManager.savePictureBytesToStorage(bytes, file.name)
                    inlineSpecPictures.add(pic)
                }
            }
        }
    }

    val selectedProducts = remember(productSlots.toList(), allAvailableProducts) {
        productSlots.filterNotNull().mapNotNull { id -> allAvailableProducts.find { it.id == id } }
    }

    val matrixSpecs = remember(selectedProducts, specifications) {
        computeSmartHierarchicalSpecOrder(selectedProducts, specifications)
    }

    val allTags = remember(allAvailableProducts) {
        allAvailableProducts.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val allBrands = remember(allAvailableProducts) {
        allAvailableProducts.map { it.brand }.filter { it.isNotBlank() && !it.equals("Generic", ignoreCase = true) }.distinct().sorted()
    }

    val slotMatchingProducts = remember(allAvailableProducts, slotSearchQuery, filterSearchQuery, selectedTagFilter, specifications) {
        val q = slotSearchQuery.ifBlank { filterSearchQuery }.trim().lowercase()
        allAvailableProducts.filter { p ->
            val matchesTag = selectedTagFilter == "All" ||
                p.tags.any { it.equals(selectedTagFilter, ignoreCase = true) } ||
                p.brand.equals(selectedTagFilter, ignoreCase = true) ||
                p.name.equals(selectedTagFilter, ignoreCase = true)
            val matchesSearch = q.isBlank() ||
                p.name.lowercase().contains(q) ||
                p.brand.lowercase().contains(q) ||
                p.tags.any { it.lowercase().contains(q) } ||
                specifications.filter { it.productId == p.id || it.productTitle == p.name }
                    .any { it.specName.lowercase().contains(q) || it.details.lowercase().contains(q) }
            matchesTag && matchesSearch
        }
    }

    val filledSlotsCount = productSlots.filterNotNull().size
    val diffCount = remember(selectedProducts, matrixSpecs, specifications) {
        if (selectedProducts.size < 2) 0
        else {
            val brandDiff = if (selectedProducts.map { it.brand.trim().lowercase() }.distinct().size > 1) 1 else 0
            val tagsDiff = if (selectedProducts.map { it.tags.sorted().joinToString(",").lowercase() }.distinct().size > 1) 1 else 0
            val specsDiff = matrixSpecs.count { specName ->
                selectedProducts.map { p ->
                    sharedHtmlToPlainText(
                        specifications.find { (it.productId == p.id || it.productTitle == p.name) && it.specName.equals(specName, ignoreCase = true) }?.details.orEmpty()
                    ).trim().lowercase()
                }.distinct().size > 1
            }
            brandDiff + tagsDiff + specsDiff
        }
    }

    val allAppLibraryPictures = remember(allAvailableProducts, specifications) {
        (allAvailableProducts.flatMap { it.pictures } + specifications.flatMap { it.pictures })
            .distinctBy { it.name.ifBlank { it.url } }
    }

    // Lightbox
    if (specLightboxImages.isNotEmpty()) {
        SharedFullScreenPhotoLightbox(
            images = specLightboxImages,
            initialIndex = specLightboxIndex,
            onDismiss = { specLightboxImages = emptyList() }
        )
    }

    // App Photo Library Dialog
    if (showAppLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showAppLibraryDialog = false },
            title = { Text("App Photo Library", fontWeight = FontWeight.Bold) },
            text = {
                if (allAppLibraryPictures.isEmpty()) {
                    Text("No pictures found in catalog.", color = Color(0xFF64748B))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allAppLibraryPictures) { pic ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        inlineSpecPictures.add(pic)
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

    // Specs Order Modal
    if (selectedProductForSpecOrder != null) {
        val prod = selectedProductForSpecOrder!!
        val prodSpecs = remember(prod, specifications) {
            mutableStateListOf<SpecificationItem>().apply {
                addAll(specifications.filter { it.productId == prod.id || it.productTitle == prod.name }.sortedBy { it.effectiveOrder })
            }
        }
        AlertDialog(
            onDismissRequest = { selectedProductForSpecOrder = null },
            title = { Text("Specifications Order — ${prod.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (prodSpecs.isEmpty()) {
                    Text("No specifications yet for this product.", color = Color(0xFF64748B))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(prodSpecs.size) { idx ->
                            val s = prodSpecs[idx]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${idx + 1}. ${s.specName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) {
                                                    val tmp = prodSpecs[idx]
                                                    prodSpecs[idx] = prodSpecs[idx - 1]
                                                    prodSpecs[idx - 1] = tmp
                                                }
                                            },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx < prodSpecs.size - 1) {
                                                    val tmp = prodSpecs[idx]
                                                    prodSpecs[idx] = prodSpecs[idx + 1]
                                                    prodSpecs[idx + 1] = tmp
                                                }
                                            },
                                            enabled = idx < prodSpecs.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSpecsOrder(prod.id, prodSpecs.toList())
                        selectedProductForSpecOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Save Order", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { selectedProductForSpecOrder = null }) { Text("Cancel") } }
        )
    }

    // Modal Spec Editor (from "Modal" button inside inline editor)
    if (modalSpecItem != null) {
        val (prod, _, _) = modalSpecItem!!
        AlertDialog(
            onDismissRequest = { modalSpecItem = null },
            title = { Text("Edit ${inlineSpecName.ifBlank { "Specification" }} — ${prod.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = inlineSpecName,
                        onValueChange = { inlineSpecName = it },
                        label = { Text("Specification") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inlineSpecUnit,
                        onValueChange = { inlineSpecUnit = it },
                        label = { Text("Unit (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    SharedHtmlWysiwygEditor(
                        value = inlineSpecDetails,
                        onChange = { inlineSpecDetails = it },
                        minHeight = 140.dp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateInlineSpec(
                            prod.id,
                            prod.name,
                            inlineSpecName.trim(),
                            inlineSpecDetails,
                            inlineSpecPictures.toList(),
                            inlineSpecUnit.trim().ifBlank { null },
                            inlineIsNumeric
                        )
                        modalSpecItem = null
                        activeInlineEditKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { modalSpecItem = null }) { Text("Cancel") } }
        )
    }

    // Confirmations
    if (showResetSlotsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetSlotsConfirm = false },
            title = { Text("Reset Product Slots?", fontWeight = FontWeight.Bold) },
            text = { Text("Reset all slots back to 2 empty slots?") },
            confirmButton = {
                Button(
                    onClick = {
                        productSlots.clear()
                        productSlots.add(null)
                        productSlots.add(null)
                        showResetSlotsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Reset", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showResetSlotsConfirm = false }) { Text("Cancel") } }
        )
    }

    if (slotToRemoveIndex != null) {
        val idx = slotToRemoveIndex!!
        AlertDialog(
            onDismissRequest = { slotToRemoveIndex = null },
            title = { Text("Remove Slot ${idx + 1}?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove this product from Smart Create slot ${idx + 1}?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (idx in productSlots.indices) {
                            if (productSlots.size > 2) productSlots.removeAt(idx) else productSlots[idx] = null
                        }
                        slotToRemoveIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Remove", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { slotToRemoveIndex = null }) { Text("Cancel") } }
        )
    }

    if (specToDeleteInline != null) {
        val (prodId, sName) = specToDeleteInline!!
        AlertDialog(
            onDismissRequest = { specToDeleteInline = null },
            title = { Text("Delete Specification?", fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"$sName\" from this product?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSpecInline(prodId, sName)
                        specToDeleteInline = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { specToDeleteInline = null }) { Text("Cancel") } }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 600.dp
        val defaultZoom = if (isWide) 60 else 100
        var zoomPercent by rememberSaveable(isWide) { mutableIntStateOf(defaultZoom) }
        var showZoomMenu by remember { mutableStateOf(false) }

        val maxContentWidth = (maxWidth * (zoomPercent / 100f)).coerceAtLeast(360.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxHeight(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. SMART CREATE & SPECIFICATION WORKSPACE HEADER
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "SMART CREATE & SPECIFICATION WORKSPACE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Choose products to compare & manage specs",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Copy specs from 2 or more products in programmed order, edit inline, or create a new product from the matrix.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { productSlots.add(null) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add product slot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Zoom pill [- 60% +]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.height(32.dp)
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
                                        DropdownMenu(expanded = showZoomMenu, onDismissRequest = { showZoomMenu = false }) {
                                            listOf(50, 60, 65, 75, 85, 100, 115, 130, 150).forEach { preset ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (preset == defaultZoom) "$preset% (Default)" else "$preset%",
                                                            fontWeight = if (preset == zoomPercent) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 12.sp
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
                        }
                    }
                }
            }

            // 2. Filter Card
            item {
                SharedTagBrandProductFilter(
                    tags = allTags,
                    brands = allBrands,
                    productNames = allAvailableProducts.map { it.name },
                    selectedTag = selectedTagFilter,
                    onSelectTag = { selectedTagFilter = it },
                    searchQuery = filterSearchQuery,
                    onSearchQueryChange = { filterSearchQuery = it }
                )
            }

            // 3. Dynamic Product Slots
            items(productSlots.size) { index ->
                val slotProdId = productSlots[index]
                val slotProduct = slotProdId?.let { id -> allAvailableProducts.find { it.id == id } }
                val isSlotOpen = slotPickerIndex == index

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Slot ${index + 1} of ${productSlots.size}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                if (slotProduct != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFDCFCE7),
                                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                                    ) {
                                        Text(
                                            text = "✓ ${slotProduct.name}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (slotProduct != null) {
                                    Text(
                                        text = "Change",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5),
                                        modifier = Modifier.clickable {
                                            slotPickerIndex = if (isSlotOpen) null else index
                                            slotSearchQuery = ""
                                        }
                                    )
                                }
                                if (productSlots.size > 2 || slotProduct != null) {
                                    IconButton(
                                        onClick = { slotToRemoveIndex = index },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove slot", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (slotProduct != null) {
                            val prodSpecCount = specifications.count { it.productId == slotProduct.id || it.productTitle == slotProduct.name }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFAFAFA),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF1F5F9),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        SmartProductThumb(slotProduct)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = slotProduct.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFEF3C7)) {
                                                Text(
                                                    "✓ Active",
                                                    color = Color(0xFF92400E),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${slotProduct.brand.ifBlank { "No brand" }} • $prodSpecCount specifications",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onViewProduct(slotProduct) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View product", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        slotPickerIndex = if (isSlotOpen) null else index
                                        slotSearchQuery = ""
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(shape = CircleShape, color = Color(0xFFEEF2FF), modifier = Modifier.size(36.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column {
                                        Text("Search and select a product", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                        Text("Tap to search and pick from your catalog", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }
                                }
                            }
                        }

                        // Inline Fast Popover Product Picker
                        if (isSlotOpen) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = slotSearchQuery,
                                        onValueChange = { slotSearchQuery = it },
                                        placeholder = { Text("Search product name, brand, tags, or specs...", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        trailingIcon = {
                                            if (slotSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { slotSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (slotMatchingProducts.isEmpty()) {
                                            Text("No products found.", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(8.dp))
                                        } else {
                                            slotMatchingProducts.take(30).forEach { itemProduct ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.White,
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        productSlots[index] = itemProduct.id
                                                        slotPickerIndex = null
                                                        slotSearchQuery = ""
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = Color(0xFFF1F5F9),
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            SmartProductThumb(itemProduct)
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(itemProduct.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(itemProduct.brand.ifBlank { "Generic" }, fontSize = 11.sp, color = Color(0xFF64748B))
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

            // 4. Slots Summary & Jump Bar
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
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
                                text = "$filledSlotsCount of ${productSlots.size} slots filled • $diffCount differences detected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Reset slots",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.clickable { showResetSlotsConfirm = true }
                            )
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index = productSlots.size + 3)
                                }
                            },
                            enabled = selectedProducts.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Go to matrix ($diffCount differences)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // 5. Matrix Section (when 1+ products selected)
            if (selectedProducts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SELECTED PRODUCTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "All details and specifications",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "${matrixSpecs.size} fields shown across ${selectedProducts.size} products",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF0F172A)) {
                                Text(
                                    "${selectedProducts.size} selected",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFE0F2FE),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Text(
                                    "$diffCount differences",
                                    color = Color(0xFF0284C7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Smart Create Card: Create new product from matrix specs in programmed order
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("New product name", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            OutlinedTextField(
                                value = newProductName,
                                onValueChange = { newProductName = it },
                                placeholder = { Text("Enter product name", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                    Text("${matrixSpecs.size} blank specifications", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                                }
                            }
                            Button(
                                onClick = {
                                    if (newProductName.isNotBlank()) {
                                        val nameToCreate = newProductName.trim()
                                        val newId = "rec_${Random.nextLong(100000000, 999999999)}"
                                        val tempProd = ProductItem(
                                            id = newId,
                                            name = nameToCreate,
                                            brand = "Generic",
                                            tags = listOf("Smart Create"),
                                            overview = "Created with ${matrixSpecs.size} specifications extracted from comparison matrix."
                                        )
                                        localNewProducts.add(tempProd)
                                        newlyCreatedIds.add(newId)
                                        val emptyIdx = productSlots.indexOfFirst { it == null }
                                        if (emptyIdx != -1) productSlots[emptyIdx] = newId else productSlots.add(newId)
                                        targetProductForSpecId = newId
                                        newProductName = ""
                                        onCreateAndCompare(nameToCreate, "Generic", matrixSpecs, newId)
                                    }
                                },
                                enabled = newProductName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create and compare", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Add Specification Inline Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Product", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                            var isTargetPickerOpen by remember { mutableStateOf(false) }
                            val currentTargetProd = selectedProducts.find { it.id == targetProductForSpecId }
                                ?: allAvailableProducts.find { it.id == targetProductForSpecId }
                                ?: selectedProducts.firstOrNull()
                            val currentTargetLabel = currentTargetProd?.let {
                                "${it.name}${if (it.id in newlyCreatedIds) " (New)" else ""}"
                            } ?: "Select product"

                            Box {
                                OutlinedButton(
                                    onClick = { isTargetPickerOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(currentTargetLabel, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                                    }
                                }
                                DropdownMenu(expanded = isTargetPickerOpen, onDismissRequest = { isTargetPickerOpen = false }) {
                                    selectedProducts.forEach { p ->
                                        val isNew = p.id in newlyCreatedIds
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(p.name, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal)
                                                    if (isNew) {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF3C7)) {
                                                            Text("(New)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = { targetProductForSpecId = p.id; isTargetPickerOpen = false }
                                        )
                                    }
                                }
                            }

                            Text("New specification", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            OutlinedTextField(
                                value = newSpecName,
                                onValueChange = { newSpecName = it },
                                placeholder = { Text("Specification name", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Text("Details", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            OutlinedTextField(
                                value = newSpecDetails,
                                onValueChange = { newSpecDetails = it },
                                placeholder = { Text("Optional value", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (newSpecName.isNotBlank() && currentTargetProd != null) {
                                        onAddSpecificationInline(currentTargetProd.id, currentTargetProd.name, newSpecName.trim(), newSpecDetails.trim(), null)
                                        newSpecName = ""
                                        newSpecDetails = ""
                                    }
                                },
                                enabled = newSpecName.isNotBlank() && currentTargetProd != null,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B), contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add specification", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Selected Products Reorder & Specs Order Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Selected products", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))

                            selectedProducts.forEachIndexed { index, product ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F5F9), modifier = Modifier.size(40.dp)) {
                                            SmartProductThumb(product)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(product.brand.ifBlank { "Generic" }, fontSize = 12.sp, color = Color(0xFF64748B))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val sIdx = productSlots.indexOf(product.id)
                                                    if (sIdx != -1 && sIdx < productSlots.size - 1) {
                                                        val tmp = productSlots[sIdx]
                                                        productSlots[sIdx] = productSlots[sIdx + 1]
                                                        productSlots[sIdx + 1] = tmp
                                                    }
                                                },
                                                enabled = index < selectedProducts.size - 1,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    val sIdx = productSlots.indexOf(product.id)
                                                    if (sIdx > 0) {
                                                        val tmp = productSlots[sIdx]
                                                        productSlots[sIdx] = productSlots[sIdx - 1]
                                                        productSlots[sIdx - 1] = tmp
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF1F5F9),
                                                modifier = Modifier.clickable { selectedProductForSpecOrder = product }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                                    Text("Specs", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    val sIdx = productSlots.indexOf(product.id)
                                                    if (sIdx != -1) {
                                                        if (productSlots.size > 2) productSlots.removeAt(sIdx) else productSlots[sIdx] = null
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Brand Card (Inline Editable)
                val isBrandDiff = selectedProducts.size > 1 && selectedProducts.map { it.brand.trim().lowercase() }.distinct().size > 1
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Sell, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Text("Brand", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                }
                                if (isBrandDiff) {
                                    DifferentBluePill()
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            selectedProducts.forEach { p ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                    if (editingBrandProductId == p.id) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = editingBrandText,
                                                onValueChange = { editingBrandText = it },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    onUpdateProductBrand(p.id, editingBrandText)
                                                    editingBrandProductId = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                            ) { Text("Save", color = Color.White, fontSize = 12.sp) }
                                            TextButton(onClick = { editingBrandProductId = null }) { Text("Cancel", fontSize = 12.sp) }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(p.brand.ifBlank { "No brand" }, fontSize = 14.sp, color = Color(0xFF334155))
                                            IconButton(
                                                onClick = { editingBrandProductId = p.id; editingBrandText = p.brand },
                                                modifier = Modifier.size(24.dp)
                                            ) { Icon(Icons.Default.Edit, contentDescription = "Edit brand", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp)) }
                                            IconButton(
                                                onClick = { onUpdateProductBrand(p.id, "") },
                                                modifier = Modifier.size(24.dp)
                                            ) { Icon(Icons.Default.Delete, contentDescription = "Clear brand", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tags Card (Inline Editable)
                val isTagsDiff = selectedProducts.size > 1 && selectedProducts.map { it.tags.sorted().joinToString(",").lowercase() }.distinct().size > 1
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Label, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                                    Text("Tags", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                }
                                if (isTagsDiff) DifferentBluePill()
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            selectedProducts.forEach { p ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                    if (editingTagsProductId == p.id) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = editingTagsText,
                                                onValueChange = { editingTagsText = it },
                                                placeholder = { Text("Comma-separated tags") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    onUpdateProductTags(p.id, editingTagsText.split(",").map { it.trim() }.filter { it.isNotBlank() })
                                                    editingTagsProductId = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                            ) { Text("Save", color = Color.White, fontSize = 12.sp) }
                                            TextButton(onClick = { editingTagsProductId = null }) { Text("Cancel", fontSize = 12.sp) }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (p.tags.isEmpty()) {
                                                    Text("No tags", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                                } else {
                                                    p.tags.forEach { tag ->
                                                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9)) {
                                                            Text(tag, fontSize = 11.sp, color = Color(0xFF334155), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = { editingTagsProductId = p.id; editingTagsText = p.tags.joinToString(", ") },
                                                modifier = Modifier.size(24.dp)
                                            ) { Icon(Icons.Default.Edit, contentDescription = "Edit tags", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Specification Comparison & Inline Editor Cards
                items(matrixSpecs) { specName ->
                    val values = selectedProducts.map { p ->
                        specifications.find { (it.productId == p.id || it.productTitle == p.name) && it.specName.equals(specName, ignoreCase = true) }
                    }
                    val isSpecDiff = selectedProducts.size > 1 && values.map { sharedHtmlToPlainText(it?.details.orEmpty()).trim().lowercase() }.distinct().size > 1
                    val specUnitBadge = values.firstOrNull { !it?.unit.isNullOrBlank() }?.unit.orEmpty()

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    Text(specName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                    if (specUnitBadge.isNotBlank()) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                            Text(specUnitBadge, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                if (isSpecDiff) DifferentBluePill()
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            selectedProducts.forEachIndexed { idx, p ->
                                val s = values[idx]
                                val editKey = "${p.id}|$specName"
                                val isEditing = activeInlineEditKey == editKey

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(p.name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))

                                    if (isEditing) {
                                        // Blue-bordered Inline Spec Editor (Matching Screenshots 1, 7)
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text("Specification", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                OutlinedTextField(
                                                    value = inlineSpecName,
                                                    onValueChange = { inlineSpecName = it },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp),
                                                    singleLine = true
                                                )

                                                Text("Unit (optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                OutlinedTextField(
                                                    value = inlineSpecUnit,
                                                    onValueChange = { inlineSpecUnit = it },
                                                    placeholder = { Text("e.g. g, mAh, mm", color = Color(0xFF94A3B8)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp),
                                                    singleLine = true
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("Quick unit:", fontSize = 11.sp, color = Color(0xFF64748B))
                                                    listOf("g", "kg", "inch", "cm", "mm", "year", "month", "mAh", "Hz", "$", "€").forEach { unitOpt ->
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (inlineSpecUnit == unitOpt) Color(0xFFEEF2FF) else Color.White,
                                                            border = BorderStroke(1.dp, if (inlineSpecUnit == unitOpt) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                                                            modifier = Modifier.clickable { inlineSpecUnit = unitOpt }
                                                        ) {
                                                            Text(unitOpt, fontSize = 11.sp, color = Color(0xFF334155), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                        }
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF8FAFC),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4F46E5), modifier = Modifier.size(32.dp)) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text("#", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                            }
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Text("Numeric Comparison", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE2E8F0)) {
                                                                    Text(
                                                                        if (inlineIsNumeric) "Number ON" else "Number OFF",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF334155),
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                            Text("Enables numerical comparison bar and Best value logic", fontSize = 11.sp, color = Color(0xFF64748B))
                                                        }
                                                        Switch(checked = inlineIsNumeric, onCheckedChange = { inlineIsNumeric = it })
                                                    }
                                                }

                                                Text("Specification details", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                SharedHtmlWysiwygEditor(
                                                    value = inlineSpecDetails,
                                                    onChange = { inlineSpecDetails = it },
                                                    minHeight = 110.dp
                                                )

                                                Text("Specification pictures", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                if (inlineSpecPictures.isNotEmpty()) {
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        items(inlineSpecPictures.size) { pIdx ->
                                                            val pic = inlineSpecPictures[pIdx]
                                                            Box(modifier = Modifier.size(72.dp)) {
                                                                AsyncImage(
                                                                    model = resolveSharedImageModel(pic.url, pic.name),
                                                                    contentDescription = pic.name,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9))
                                                                )
                                                                Surface(
                                                                    shape = CircleShape,
                                                                    color = Color.Black.copy(alpha = 0.7f),
                                                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clickable {
                                                                        inlineSpecPictures.removeAt(pIdx)
                                                                    }
                                                                ) {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(11.dp))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = Color(0xFFFAFAFA),
                                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(14.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                                                        OutlinedButton(onClick = { filePicker.launch() }, shape = RoundedCornerShape(10.dp)) {
                                                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Choose pictures", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { showAppLibraryDialog = true },
                                                            shape = RoundedCornerShape(10.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFEEF2FF), contentColor = Color(0xFF4F46E5))
                                                        ) {
                                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Or pick from App Photo Library", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Button(
                                                            onClick = {
                                                                onUpdateInlineSpec(
                                                                    p.id,
                                                                    p.name,
                                                                    inlineSpecName.trim().ifBlank { specName },
                                                                    inlineSpecDetails,
                                                                    inlineSpecPictures.toList(),
                                                                    inlineSpecUnit.trim().ifBlank { null },
                                                                    inlineIsNumeric
                                                                )
                                                                activeInlineEditKey = null
                                                            },
                                                            shape = RoundedCornerShape(10.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                                        ) {
                                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(Modifier.width(5.dp))
                                                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                        TextButton(onClick = { activeInlineEditKey = null }) {
                                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF475569))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Cancel", color = Color(0xFF475569))
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = { modalSpecItem = Triple(p, specName, s) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                                    ) {
                                                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF4F46E5))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Modal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
                                                    }
                                                }
                                            }
                                        }
                                    } else if (s != null && (s.details.isNotBlank() || s.pictures.isNotEmpty())) {
                                        Text(
                                            text = parseSharedRichTextToAnnotatedString(
                                                s.details,
                                                baseColor = Color(0xFF1E293B),
                                                linkColor = Color(0xFF4F46E5)
                                            ),
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp
                                        )
                                        if (s.pictures.isNotEmpty()) {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(s.pictures.size) { picIdx ->
                                                    val pic = s.pictures[picIdx]
                                                    AsyncImage(
                                                        model = resolveSharedImageModel(pic.url, pic.name),
                                                        contentDescription = pic.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(80.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFFF8FAFC))
                                                            .clickable {
                                                                specLightboxImages = s.pictures
                                                                specLightboxIndex = picIdx
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.clickable {
                                                    activeInlineEditKey = editKey
                                                    inlineSpecName = s.specName
                                                    inlineSpecDetails = s.details
                                                    inlineSpecUnit = s.unit.orEmpty()
                                                    inlineIsNumeric = s.isNumeric == true
                                                    inlineSpecPictures.clear()
                                                    inlineSpecPictures.addAll(s.pictures)
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                                Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.clickable { specToDeleteInline = p.id to specName }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                                Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                                            }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("No data", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF1F5F9),
                                                modifier = Modifier.clickable {
                                                    activeInlineEditKey = editKey
                                                    inlineSpecName = specName
                                                    inlineSpecDetails = ""
                                                    inlineSpecUnit = specUnitBadge
                                                    inlineIsNumeric = s?.isNumeric == true
                                                    inlineSpecPictures.clear()
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                                                    Text("Add data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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

@Composable
private fun DifferentBluePill() {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0284C7)) {
        Text(
            "Different",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmartProductThumb(product: ProductItem) {
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
        val icon = when (detectDeviceCategory(product.name, product.brand, product.tags)) {
            "mic" -> Icons.Default.Mic
            "glasses" -> Icons.Default.Visibility
            "laptop" -> Icons.Default.LaptopMac
            "watch" -> Icons.Default.Watch
            "headphones" -> Icons.Default.Headphones
            "phone" -> Icons.Default.Smartphone
            else -> Icons.Default.Inventory2
        }
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEEF2FF)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
        }
    }
}

private fun computeSmartHierarchicalSpecOrder(
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
