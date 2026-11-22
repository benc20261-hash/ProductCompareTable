package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.SpecificationItem
import com.example.model.copyPickedUriToPermanentStorage
import com.example.model.resolveImageModel
import com.example.ui.components.AppPhotoLibraryDialog
import com.example.ui.components.DashedDropzoneBox
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.FormattedSpecHtml
import com.example.ui.components.FullScreenPhotoLightbox
import com.example.ui.components.HtmlWysiwygEditor
import com.example.ui.components.PhotoThumbnailItem
import com.example.ui.components.SpecificationFormContent
import com.example.ui.components.SpecUnitBadge
import com.example.ui.components.htmlToPlainText
import com.example.ui.util.UriHelper
import kotlinx.coroutines.launch
import java.util.UUID

val PRESET_QUICK_BRANDS = listOf(
    "Acer", "Adidas", "Anker", "Apple", "Asus", "Bose", "Boya", "Dell", "Dyson", "Google",
    "HP", "Huawei", "JBL", "Lenovo", "LG", "Nike", "OnePlus", "Oppo", "Philips", "Plaud",
    "Puma", "Samsung", "Sennheiser", "Sony", "Under Armour", "Viture", "vivo", "Xiaomi"
)

val PRESET_QUICK_TAGS = listOf(
    "Adidas", "Ai voice recorder", "amazon", "Audio", "Boya", "dress", "Home appliance",
    "IT product", "Kid", "Plaud", "Puma", "Shoe", "Smart wear", "Smartphone", "Sony",
    "Test2", "TV", "Viture", "vivo", "Wearable"
)

val PRESET_QUICK_UNITS = listOf(
    "g", "kg", "inch", "cm", "mm", "year", "month", "mAh", "Hz", "$", "GB"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductWorkspaceScreen(
    initialProduct: ProductItem?,
    allAvailableTags: List<String>,
    allAvailableBrands: List<String> = emptyList(),
    initialSpecifications: List<SpecificationItem> = emptyList(),
    onSaveProduct: (ProductItem, List<SpecificationItem>, Boolean) -> Unit,
    onDeleteProduct: ((String) -> Unit)? = null,
    onNavigateBack: () -> Unit,
    isTabletMode: Boolean = run {
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        cfg.screenWidthDp >= 600 || cfg.smallestScreenWidthDp >= 600
    },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isNew = initialProduct == null
    val coroutineScope = rememberCoroutineScope()

    val defaultZoom = if (isTabletMode) 60 else 100
    var zoomPercent by rememberSaveable(isTabletMode) { mutableIntStateOf(defaultZoom) }
    var showZoomMenu by remember { mutableStateOf(false) }

    val productId = remember { initialProduct?.id ?: ("rec_" + UUID.randomUUID().toString().replace("-", "").take(17)) }
    var productName by remember { mutableStateOf(initialProduct?.name ?: "") }
    var brand by remember { mutableStateOf(initialProduct?.brand ?: "") }
    var selectedTags by remember {
        mutableStateOf(
            if (initialProduct != null && initialProduct.tags.isNotEmpty()) {
                initialProduct.tags.toSet()
            } else {
                emptySet()
            }
        )
    }
    var newTagInput by remember { mutableStateOf("") }
    var brandSearchQuery by remember { mutableStateOf("") }
    val brandScrollState = rememberScrollState()
    var tagSearchQuery by remember { mutableStateOf("") }
    val tagScrollState = rememberScrollState()

    val allBrandsList = remember(allAvailableBrands) {
        (PRESET_QUICK_BRANDS + allAvailableBrands).filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toMutableStateList()
    }
    val allTagsList = remember(allAvailableTags) {
        (PRESET_QUICK_TAGS + allAvailableTags).filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toMutableStateList()
    }

    var overviewText by remember { mutableStateOf(initialProduct?.overview ?: "") }
    val productPictures = remember { mutableStateListOf<PictureItem>().apply { addAll(initialProduct?.pictures ?: emptyList()) } }

    // Specifications draft list: Defaults to exactly ONE specification (Screenshot 3)
    val specsList = remember {
        mutableStateListOf<SpecificationItem>().apply {
            if (initialSpecifications.isNotEmpty()) {
                addAll(initialSpecifications.sortedBy { it.orderIndex })
            } else if (initialProduct != null) {
                // If existing product without explicit passed specs
            } else {
                // Default is exactly ONE specification
                add(
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

    var selectedSpecIndex by remember { mutableStateOf(0) }
    var expandedSpecId by remember { mutableStateOf<String?>(specsList.firstOrNull()?.id) }
    var showDeleteProductConfirm by remember { mutableStateOf(false) }
    var specToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pictureToRemove by remember { mutableStateOf<PictureItem?>(null) }
    var activeLightboxImages by remember { mutableStateOf<List<PictureItem>?>(null) }
    var activeLightboxIndex by remember { mutableIntStateOf(0) }

    // Android Photo Picker for Product Pictures (Copies permanently to disk)
    val productPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val permanentPic = copyPickedUriToPermanentStorage(context, uri, prefix = "prod_pic")
            productPictures.add(permanentPic)
        }
    }

    // Storage / File Picker for Product Pictures (Supports Downloads, BlueStacks Shared Folder, SD Card)
    val productFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val permanentPic = copyPickedUriToPermanentStorage(context, uri, prefix = "prod_pic")
            productPictures.add(permanentPic)
        }
    }

    // Android Photo Picker for Spec Pictures (Copies permanently to disk)
    val specPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (selectedSpecIndex in specsList.indices) {
            val currentSpec = specsList[selectedSpecIndex]
            val updatedPics = currentSpec.pictures.toMutableList()
            uris.forEach { uri ->
                val permanentPic = copyPickedUriToPermanentStorage(context, uri, prefix = "spec_pic")
                updatedPics.add(permanentPic)
            }
            specsList[selectedSpecIndex] = currentSpec.copy(pictures = updatedPics)
        }
    }

    // Storage / File Picker for Spec Pictures (Supports Downloads, BlueStacks Shared Folder, SD Card)
    val specFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (selectedSpecIndex in specsList.indices) {
            val currentSpec = specsList[selectedSpecIndex]
            val updatedPics = currentSpec.pictures.toMutableList()
            uris.forEach { uri ->
                val permanentPic = copyPickedUriToPermanentStorage(context, uri, prefix = "spec_pic")
                updatedPics.add(permanentPic)
            }
            specsList[selectedSpecIndex] = currentSpec.copy(pictures = updatedPics)
        }
    }

    var showAppLibraryForProduct by remember { mutableStateOf(false) }
    var showAppLibraryForSpecIndex by remember { mutableStateOf<Int?>(null) }

    // Save action helper
    fun doSave() {
        if (productName.isBlank()) {
            Toast.makeText(context, "Please enter a product name", Toast.LENGTH_SHORT).show()
            return
        }
        val product = ProductItem(
            id = productId,
            name = productName.trim(),
            brand = brand.trim(),
            tags = selectedTags.toList(),
            overview = overviewText.trim(),
            pictures = productPictures.toList(),
            createdTime = initialProduct?.createdTime ?: System.currentTimeMillis().toString()
        )
        // Update productTitle in all specs
        val finalSpecs = specsList.mapIndexed { idx, s ->
            s.copy(
                productId = productId,
                productTitle = productName.trim(),
                orderIndex = idx + 1
            )
        }
        onSaveProduct(product, finalSpecs, isNew)
        onNavigateBack()
    }

    // Reordering actions
    fun moveSelectedSpecUp() {
        val idx = selectedSpecIndex
        if (idx > 0 && idx < specsList.size) {
            val item = specsList.removeAt(idx)
            specsList.add(idx - 1, item)
            selectedSpecIndex = idx - 1
            expandedSpecId = item.id
        }
    }

    fun moveSelectedSpecDown() {
        val idx = selectedSpecIndex
        if (idx >= 0 && idx < specsList.size - 1) {
            val item = specsList.removeAt(idx)
            specsList.add(idx + 1, item)
            selectedSpecIndex = idx + 1
            expandedSpecId = item.id
        }
    }

    fun sortSpecsAZ() {
        val sorted = specsList.sortedBy { it.specName.lowercase() }
        specsList.clear()
        specsList.addAll(sorted)
    }

    fun sortSpecsZA() {
        val sorted = specsList.sortedByDescending { it.specName.lowercase() }
        specsList.clear()
        specsList.addAll(sorted)
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val maxContentWidth = (screenWidthDp * (zoomPercent / 100f)).coerceAtLeast(360.dp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back button
                        TextButton(
                            onClick = onNavigateBack,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("workspace_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                        }

                        // Title: Add New Product or Edit: [Name]
                        Text(
                            text = if (isNew) "Add New Product" else if (productName.isNotBlank()) "Edit: $productName" else "Create Product",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Right action icons: Zoom, Trash, Publish/Save, Close
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Zoom pill
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
                                                    onClick = {
                                                        zoomPercent = preset
                                                        showZoomMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            zoomPercent = (zoomPercent + 10).coerceAtMost(160)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(13.dp), tint = Color(0xFF334155))
                                    }
                                }
                            }

                            if (!isNew && onDeleteProduct != null) {
                            IconButton(
                                onClick = { showDeleteProductConfirm = true },
                                modifier = Modifier.size(36.dp).testTag("workspace_delete_icon_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Pill Publish/Save button
                        Button(
                            onClick = { doSave() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).testTag("workspace_top_save_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isNew) "Publish" else "Save",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Close 'X'
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(36.dp).testTag("workspace_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Sticky Bottom Action Bar
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxContentWidth)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top stats line: Boya notra        2 tags / 5 specifications ✓
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
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Action buttons: Full-width "Publish product" when new, or Trash + "Save changes" when editing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isNew && onDeleteProduct != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { showDeleteProductConfirm = true }
                                        .testTag("workspace_bottom_delete_btn")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete product",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { doSave() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("workspace_bottom_save_changes_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isNew) "Publish product" else "Save changes",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(com.example.ui.theme.AppColors.CanvasBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxHeight(),
                contentPadding = com.example.ui.theme.AppSpacing.ScreenContentPaddingScrollable,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // Workspace Subheader
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PRODUCT WORKSPACE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Create a product customers can compare",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        lineHeight = 28.sp
                    )
                    Text(
                        text = "Use tags for discovery and add only the specifications that make sense for this product.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            }

            // 1. Product Identity Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section Header: Cube icon + Product identity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Token,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Product identity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
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
                                modifier = Modifier.fillMaxWidth().testTag("workspace_product_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    focusedBorderColor = Color(0xFF0F172A)
                                )
                            )
                        }

                        // Brand Section (Matching recordcompare-1.ai.studio)
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
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF0F172A)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                                Text(text = brand, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                IconButton(
                                                    onClick = { brand = "" },
                                                    modifier = Modifier.size(14.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear brand", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Brand input + Save Brand button
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
                                    modifier = Modifier.weight(1f).testTag("workspace_brand_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedBorderColor = Color(0xFF0F172A)
                                    )
                                )
                                if (brand.trim().isNotBlank() && !allBrandsList.any { it.equals(brand.trim(), ignoreCase = true) }) {
                                    Button(
                                        onClick = {
                                            val trimmed = brand.trim()
                                            if (trimmed.isNotBlank() && !allBrandsList.any { it.equals(trimmed, ignoreCase = true) }) {
                                                allBrandsList.add(trimmed)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF0F172A)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Save Brand", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Quick Brand Picker Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Search brands & scroll arrows
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = brandSearchQuery,
                                            onValueChange = { brandSearchQuery = it },
                                            placeholder = { Text("Search brands...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                            },
                                            trailingIcon = {
                                                if (brandSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { brandSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                                focusedBorderColor = Color(0xFF0F172A),
                                                unfocusedContainerColor = Color.White,
                                                focusedContainerColor = Color.White
                                            )
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.size(36.dp).clickable {
                                                coroutineScope.launch {
                                                    brandScrollState.animateScrollTo((brandScrollState.value - 300).coerceAtLeast(0))
                                                }
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Scroll left", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.size(36.dp).clickable {
                                                coroutineScope.launch {
                                                    brandScrollState.animateScrollTo(brandScrollState.value + 300)
                                                }
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = "Scroll right", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }

                                    // Filtered brands horizontal chips with cross-category matching
                                    val filteredBrands = remember(allBrandsList, allTagsList, brandSearchQuery) {
                                        val q = brandSearchQuery.trim()
                                        if (q.isBlank()) allBrandsList
                                        else {
                                            val bMatches = allBrandsList.filter { it.contains(q, ignoreCase = true) }
                                            val tMatches = allTagsList.filter { it.contains(q, ignoreCase = true) && !allBrandsList.contains(it) }
                                            bMatches + tMatches
                                        }
                                    }

                                    if (filteredBrands.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(brandScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            filteredBrands.forEach { b ->
                                                val isSelected = brand.trim().equals(b.trim(), ignoreCase = true)
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.clickable {
                                                        brand = if (isSelected) "" else b
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                                        }
                                                        Text(
                                                            text = b,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else Color(0xFF334155)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "No brand matching \"$brandSearchQuery\"",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Button(
                                                onClick = {
                                                    val trimmed = brandSearchQuery.trim()
                                                    if (trimmed.isNotBlank()) {
                                                        allBrandsList.add(trimmed)
                                                        brand = trimmed
                                                        brandSearchQuery = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("+ Add \"$brandSearchQuery\"", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Product tags Section (Matching recordcompare-1.ai.studio)
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
                                    Icon(
                                        imageVector = Icons.Default.Sell,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text("Product tags", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                }

                                if (selectedTags.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
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

                            // Tag input + Add Tag button
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
                                    modifier = Modifier.weight(1f).testTag("workspace_new_tag_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedBorderColor = Color(0xFF0F172A)
                                    )
                                )

                                if (newTagInput.trim().isNotBlank()) {
                                    Button(
                                        onClick = {
                                            val trimmed = newTagInput.trim()
                                            if (trimmed.isNotBlank()) {
                                                selectedTags = selectedTags + trimmed
                                                if (!allTagsList.any { it.equals(trimmed, ignoreCase = true) }) {
                                                    allTagsList.add(trimmed)
                                                }
                                                newTagInput = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF0F172A)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("workspace_add_tag_btn")
                                    ) {
                                        Text("Add Tag", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Wrapped row of selected tag pills (with emerald check and close button)
                            if (selectedTags.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    selectedTags.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0F172A)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                                Text(text = tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                IconButton(
                                                    onClick = { selectedTags = selectedTags - tag },
                                                    modifier = Modifier.size(16.dp)
                                                ) {
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
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Search tags & scroll arrows
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = tagSearchQuery,
                                            onValueChange = { tagSearchQuery = it },
                                            placeholder = { Text("Search tags...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                            },
                                            trailingIcon = {
                                                if (tagSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { tagSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                                focusedBorderColor = Color(0xFF0F172A),
                                                unfocusedContainerColor = Color.White,
                                                focusedContainerColor = Color.White
                                            )
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.size(36.dp).clickable {
                                                coroutineScope.launch {
                                                    tagScrollState.animateScrollTo((tagScrollState.value - 300).coerceAtLeast(0))
                                                }
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Scroll left", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.size(36.dp).clickable {
                                                coroutineScope.launch {
                                                    tagScrollState.animateScrollTo(tagScrollState.value + 300)
                                                }
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = "Scroll right", tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }

                                    // Filtered tags horizontal chips with cross-category matching
                                    val filteredTags = remember(allTagsList, allBrandsList, tagSearchQuery) {
                                        val q = tagSearchQuery.trim()
                                        if (q.isBlank()) allTagsList
                                        else {
                                            val tMatches = allTagsList.filter { it.contains(q, ignoreCase = true) }
                                            val bMatches = allBrandsList.filter { it.contains(q, ignoreCase = true) && !allTagsList.contains(it) }
                                            tMatches + bMatches
                                        }
                                    }

                                    if (filteredTags.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(tagScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            filteredTags.forEach { t ->
                                                val isSelected = selectedTags.contains(t)
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.clickable {
                                                        selectedTags = if (isSelected) selectedTags - t else selectedTags + t
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                                        }
                                                        Text(
                                                            text = t,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else Color(0xFF334155)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "No tag matching \"$tagSearchQuery\"",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Button(
                                                onClick = {
                                                    val trimmed = tagSearchQuery.trim()
                                                    if (trimmed.isNotBlank()) {
                                                        allTagsList.add(trimmed)
                                                        selectedTags = selectedTags + trimmed
                                                        tagSearchQuery = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("+ Add \"$tagSearchQuery\"", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Rich Text / Markdown Overview Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Product overview & details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )

                        HtmlWysiwygEditor(
                            value = overviewText,
                            onChange = { overviewText = it },
                            placeholder = "Summarize what the product is, who it is for, and what makes it notable...",
                            minHeight = 120.dp,
                            testTagPrefix = "workspace_overview"
                        )
                    }
                }
            }

            // 3. Product Pictures Card (Dashed Dropzone)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Product pictures",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )

                        // Dashed dropzone card with gallery, file browsing, and app library
                        DashedDropzoneBox(
                            buttonText = "Choose pictures",
                            browseFilesText = "Browse files / storage",
                            helperText = "Select from photo library, downloads, or BlueStacks files",
                            onPickClick = {
                                UriHelper.scanCommonStorageFolders(context)
                                productPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onBrowseFilesClick = {
                                productFilePicker.launch("image/*")
                            },
                            onAppLibraryClick = {
                                showAppLibraryForProduct = true
                            },
                            testTag = "workspace_choose_product_pics_btn"
                        )

                        // Display selected pictures if any
                        if (productPictures.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                itemsIndexed(productPictures) { pIdx, pic ->
                                    PhotoThumbnailItem(
                                        picture = pic,
                                        onPhotoClick = {
                                            activeLightboxImages = productPictures.toList()
                                            activeLightboxIndex = pIdx
                                        },
                                        onRemoveClick = { pictureToRemove = pic },
                                        size = 120.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Flexible Data / Specifications Header & Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FLEXIBLE DATA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Specifications",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Add weight, battery life, material, price, functions, or any specification this product needs.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    // Action Controls row: Move up, Move down, A-Z, Z-A
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionOutlinePill(
                            icon = Icons.Default.ArrowUpward,
                            label = "Move up",
                            onClick = { moveSelectedSpecUp() },
                            enabled = selectedSpecIndex > 0
                        )

                        ActionOutlinePill(
                            icon = Icons.Default.ArrowDownward,
                            label = "Move down",
                            onClick = { moveSelectedSpecDown() },
                            enabled = selectedSpecIndex in 0 until specsList.size - 1
                        )

                        ActionOutlinePill(
                            label = "A-Z",
                            onClick = { sortSpecsAZ() }
                        )

                        ActionOutlinePill(
                            label = "Z-A",
                            onClick = { sortSpecsZA() }
                        )
                    }

                    // + Add specification button
                    Button(
                        onClick = {
                            val newId = "spec_" + UUID.randomUUID().toString().replace("-", "").take(10)
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.height(44.dp).testTag("workspace_add_spec_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add specification", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // 5. Accordion Specifications List
            itemsIndexed(specsList) { index, spec ->
                val isExpanded = expandedSpecId == spec.id
                val isSelected = selectedSpecIndex == index

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 0.dp),
                    modifier = Modifier.fillMaxWidth().testTag("workspace_spec_item_$index")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header row of spec item: Radio circle, Arrow, Index badge, Name, Trash
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
                            // Radio indicator
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

                            // Expand/collapse chevron
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )

                            // Index badge circle: 1, 2, 3...
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }

                            // Spec Name & Unit Badge
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = spec.specName.ifBlank { "Untitled spec" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!spec.unit.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF1F5F9),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                                    ) {
                                        Text(
                                            text = spec.unit.trim(),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            // Delete spec icon
                            IconButton(
                                onClick = { specToDeleteIndex = index },
                                modifier = Modifier.size(28.dp).testTag("delete_spec_${index}_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete spec",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Expanded Form Content
                        if (isExpanded) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SpecificationFormContent(
                                    specName = spec.specName,
                                    onSpecNameChange = { newName ->
                                        specsList[index] = spec.copy(specName = newName)
                                    },
                                    unit = spec.unit ?: "",
                                    onUnitChange = { newUnit ->
                                        specsList[index] = spec.copy(unit = newUnit.ifBlank { null })
                                    },
                                    details = spec.details,
                                    onDetailsChange = { newDetails ->
                                        specsList[index] = spec.copy(details = newDetails)
                                    },
                                    isNumeric = spec.isNumeric,
                                    onIsNumericChange = { newNumeric ->
                                        specsList[index] = spec.copy(isNumeric = newNumeric)
                                    },
                                    pictures = spec.pictures,
                                    onPickPicturesClick = {
                                        selectedSpecIndex = index
                                        UriHelper.scanCommonStorageFolders(context)
                                        specPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onBrowseFilesClick = {
                                        selectedSpecIndex = index
                                        specFilePicker.launch("image/*")
                                    },
                                    onAppLibraryClick = {
                                        showAppLibraryForSpecIndex = index
                                    },
                                    onRemovePictureClick = { spPic ->
                                        val updated = spec.pictures.filter { it.id != spPic.id }
                                        specsList[index] = spec.copy(pictures = updated)
                                    },
                                    onPictureClick = { _, spIdx ->
                                        activeLightboxImages = spec.pictures
                                        activeLightboxIndex = spIdx
                                    },
                                    testTagPrefix = "spec_$index"
                                )

                                // Footer row: Specification #2      [Delete specification]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Specification #${index + 1}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )

                                    OutlinedButton(
                                        onClick = { specToDeleteIndex = index },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(34.dp).testTag("delete_spec_footer_${index}_btn")
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

    // Delete Product Confirmation Dialog
    if (showDeleteProductConfirm && initialProduct != null && onDeleteProduct != null) {
        DeleteConfirmDialog(
            title = "Delete Product?",
            message = "Are you sure you want to delete \"${initialProduct.name}\"? This action cannot be undone.",
            confirmText = "Delete",
            testTagPrefix = "workspace_delete_product",
            onConfirm = {
                showDeleteProductConfirm = false
                onDeleteProduct(initialProduct.id)
                onNavigateBack()
            },
            onDismiss = { showDeleteProductConfirm = false }
        )
    }

    // Delete Specification Confirmation Dialog
    if (specToDeleteIndex != null) {
        val idx = specToDeleteIndex!!
        val specName = specsList.getOrNull(idx)?.specName ?: "this specification"
        DeleteConfirmDialog(
            title = "Delete Specification",
            message = "Are you sure you want to delete specification \"$specName\"?",
            confirmText = "Delete",
            testTagPrefix = "workspace_delete_spec",
            onConfirm = {
                if (idx in specsList.indices) {
                    val removed = specsList.removeAt(idx)
                    if (selectedSpecIndex >= specsList.size) {
                        selectedSpecIndex = (specsList.size - 1).coerceAtLeast(0)
                    }
                    if (expandedSpecId == removed.id) {
                        expandedSpecId = specsList.getOrNull(selectedSpecIndex)?.id
                    }
                }
                specToDeleteIndex = null
            },
            onDismiss = { specToDeleteIndex = null }
        )
    }

    // Remove Picture Confirmation Dialog
    if (pictureToRemove != null) {
        val pic = pictureToRemove!!
        DeleteConfirmDialog(
            title = "Remove Photo",
            message = "Are you sure you want to remove \"${pic.name.ifBlank { "this photo" }}\"?",
            confirmText = "Remove",
            testTagPrefix = "workspace_remove_photo",
            onConfirm = {
                productPictures.remove(pic)
                pictureToRemove = null
            },
            onDismiss = { pictureToRemove = null }
        )
    }

    // Full Screen Zoom Lightbox for pictures
    if (activeLightboxImages != null) {
        FullScreenPhotoLightbox(
            images = activeLightboxImages!!,
            initialIndex = activeLightboxIndex,
            onDismiss = { activeLightboxImages = null }
        )
    }

    if (showAppLibraryForProduct) {
        AppPhotoLibraryDialog(
            onDismiss = { showAppLibraryForProduct = false },
            onSelectPictures = { pics ->
                productPictures.addAll(pics)
            },
            allowMultiple = true
        )
    }

    showAppLibraryForSpecIndex?.let { specIdx ->
        if (specIdx in specsList.indices) {
            AppPhotoLibraryDialog(
                onDismiss = { showAppLibraryForSpecIndex = null },
                onSelectPictures = { pics ->
                    val cur = specsList[specIdx]
                    val updatedPics = cur.pictures + pics
                    specsList[specIdx] = cur.copy(pictures = updatedPics)
                },
                allowMultiple = true
            )
        }
    }
}

// Helper Composable for Toolbar format button
@Composable
private fun ToolbarFormatButton(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strikethrough: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = Color(0xFF334155)
        )
    }
}

// Action outline pill button (Move up, Move down, A-Z, Z-A)
@Composable
private fun ActionOutlinePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color(0xFF0F172A) else Color(0xFFCBD5E1)
            )
        }
    }
}

// Dashed dropzone card with upload icon & choose pictures button
@Composable
private fun DashedDropzoneBox(
    buttonText: String,
    helperText: String,
    onPickClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFAFAFA))
            .border(
                width = 1.dp,
                color = Color(0xFFCBD5E1),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 22.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(30.dp)
            )

            Button(
                onClick = onPickClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Text(
                text = helperText,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

// Tag Cloud component for wrapping chips
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagCloud(
    allTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        allTags.forEach { tag ->
            val isSelected = selectedTags.contains(tag)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onToggleTag(tag) }
                    .testTag("tag_chip_$tag")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = tag,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF334155)
                    )
                }
            }
        }
    }
}
