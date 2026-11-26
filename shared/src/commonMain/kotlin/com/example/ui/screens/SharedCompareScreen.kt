package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel

private val COLOR_DIFF_BG = Color(0xFFFEFCE8)
private val COLOR_DIFF_BORDER = Color(0xFFFDE047)
private val COLOR_DIFF_BADGE_BG = Color(0xFFFACC15)
private val COLOR_DIFF_BADGE_TEXT = Color(0xFF451A03)
private val COLOR_SAME_BG = Color.White
private val COLOR_SAME_BORDER = Color(0xFFE2E8F0)
private val COLOR_PRODUCT_NAME = Color(0xFF64748B)
private val COLOR_INDIGO = Color(0xFF4F46E5)
private val COLOR_SLATE_900 = Color(0xFF0F172A)

@Composable
fun SharedCompareScreen(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    compareIds: List<String>,
    onToggleProduct: (String) -> Unit,
    onClearAll: () -> Unit,
    onEditProduct: (ProductItem) -> Unit,
    onAddProductClick: () -> Unit,
    onNavigateToReviews: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeProducts = remember(products, compareIds) {
        compareIds.mapNotNull { id -> products.find { it.id == id } }
    }

    // Product picker state (slot index being filled)
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var pickerSearch by remember { mutableStateOf("") }

    // Slots: always show 2 by default, up to 4
    val slotCount = maxOf(2, compareIds.size)

    if (pickerSlot != null) {
        ProductPickerDialog(
            slotIndex = pickerSlot!!,
            allProducts = products,
            search = pickerSearch,
            onSearchChange = { pickerSearch = it },
            onSelect = { product ->
                // If slot already occupied, remove old, add new
                val currentSlotProduct = activeProducts.getOrNull(pickerSlot!!)
                if (currentSlotProduct != null && currentSlotProduct.id != product.id) {
                    onToggleProduct(currentSlotProduct.id)
                }
                if (!compareIds.contains(product.id)) {
                    onToggleProduct(product.id)
                }
                pickerSlot = null
                pickerSearch = ""
            },
            onDismiss = { pickerSlot = null; pickerSearch = "" }
        )
    }

    val allSpecNames = remember(activeProducts, specifications) {
        activeProducts.flatMap { p ->
            specifications.filter { it.productId == p.id }
                .sortedBy { it.effectiveOrder }
                .map { it.specName }
        }.distinct()
    }

    val diffCount = remember(activeProducts, allSpecNames, specifications) {
        allSpecNames.count { specName ->
            val vals = activeProducts.map { p ->
                specifications.find { it.productId == p.id && it.specName == specName }
                    ?.details?.trim() ?: ""
            }.filter { it.isNotEmpty() }
            vals.distinct().size > 1
        }
    }

    LazyColumn(
        modifier = modifier.background(Color(0xFFF1F5F9)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ─── Record slot cards ───────────────────────────────────────────────
        itemsIndexed(List(slotCount) { it }) { slotIdx, _ ->
            val product = activeProducts.getOrNull(slotIdx)
            RecordSlotCard(
                slotIndex = slotIdx,
                product = product,
                reviews = if (product != null) reviews.filter { it.productIds.contains(product.id) } else emptyList(),
                onSelectSlot = { pickerSlot = slotIdx },
                onRemove = { product?.let { onToggleProduct(it.id) } },
                onEdit = { product?.let { onEditProduct(it) } },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ─── SIDE BY SIDE header ─────────────────────────────────────────────
        item {
            SideBySideHeader(
                selectedCount = activeProducts.size,
                specCount = allSpecNames.size,
                diffCount = diffCount,
                onClear = onClearAll,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (activeProducts.size < 2) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
                        Text("Select at least 2 products to compare", color = Color(0xFF64748B), fontSize = 14.sp)
                        OutlinedButton(onClick = onAddProductClick) {
                            Text("Browse Catalog")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // ─── Spec comparison cards ───────────────────────────────────────────
        items(allSpecNames) { specName ->
            val specPerProduct = activeProducts.map { p ->
                p to specifications.find { it.productId == p.id && it.specName == specName }
            }
            val rawValues = specPerProduct.mapNotNull { (_, s) -> s?.details?.trim()?.takeIf { it.isNotEmpty() } }
            val isDifferent = rawValues.distinct().size > 1

            SpecComparisonCard(
                specName = specName,
                specPerProduct = specPerProduct,
                isDifferent = isDifferent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ─── User Reviews card ───────────────────────────────────────────────
        item {
            ReviewsCompareCard(
                activeProducts = activeProducts,
                reviews = reviews,
                onOpenReviews = onNavigateToReviews,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Record Slot Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RecordSlotCard(
    slotIndex: Int,
    product: ProductItem?,
    reviews: List<ReviewItem>,
    onSelectSlot: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: RECORD N label + Edit + Remove buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        "RECORD ${slotIndex + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (product != null) {
                        TextButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Edit product", fontSize = 11.sp)
                        }
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Product row (clickable) or empty slot
            if (product != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .clickable(onClick = onSelectSlot)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail
                    val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEEF2FF))
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = product.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(22.dp),
                                tint = Color(0xFF818CF8)
                            )
                        }
                    }

                    // Name + subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("✓ Selected")
                                append("  ·  ${product.brand}")
                                val rc = reviews.size
                                if (rc > 0) append("  ·  $rc review${if (rc > 1) "s" else ""}")
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF16A34A)
                        )
                    }

                    Icon(Icons.Default.UnfoldMore, contentDescription = "Change", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                }
            } else {
                // Empty slot — tap to pick
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .clickable(onClick = onSelectSlot)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tap to select a product", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIDE BY SIDE header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SideBySideHeader(
    selectedCount: Int,
    specCount: Int,
    diffCount: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = COLOR_INDIGO),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SIDE BY SIDE  ·  Complete comparison",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    "$specCount fields shown, $diffCount differ",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            BadgeChip("$selectedCount selected", Color.White.copy(alpha = 0.2f), Color.White)
            if (diffCount > 0) BadgeChip("$diffCount diff", Color(0xFFFDE047).copy(alpha = 0.9f), COLOR_DIFF_BADGE_TEXT)
            TextButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))) {
                Text("Clear", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, bgColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = bgColor) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spec comparison card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SpecComparisonCard(
    specName: String,
    specPerProduct: List<Pair<ProductItem, SpecificationItem?>>,
    isDifferent: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDifferent) COLOR_DIFF_BG else COLOR_SAME_BG
    val borderColor = if (isDifferent) COLOR_DIFF_BORDER else COLOR_SAME_BORDER

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Spec header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(specName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                if (isDifferent) {
                    Surface(shape = RoundedCornerShape(10.dp), color = COLOR_DIFF_BADGE_BG) {
                        Text(
                            "DIFFERENT",
                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                            color = COLOR_DIFF_BADGE_TEXT,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Per-product rows — stacked vertically
            specPerProduct.forEachIndexed { idx, (product, spec) ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = if (isDifferent) Color(0xFFFDE047).copy(alpha = 0.5f) else Color(0xFFF1F5F9)
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Product name label
                    Text(
                        product.name.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = COLOR_PRODUCT_NAME,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(3.dp))

                    if (spec != null) {
                        // Spec value
                        Text(
                            buildString {
                                append(spec.details)
                                if (!spec.unit.isNullOrBlank()) append("  ${spec.unit}")
                            },
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B),
                            fontWeight = FontWeight.Medium
                        )

                        // Spec pictures
                        if (spec.pictures.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(spec.pictures) { pic ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFF8FAFC))
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
                        }
                    } else {
                        Text("—", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reviews compare card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReviewsCompareCard(
    activeProducts: List<ProductItem>,
    reviews: List<ReviewItem>,
    onOpenReviews: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, COLOR_SAME_BORDER, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("User reviews", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155))
            Spacer(Modifier.height(8.dp))

            activeProducts.forEachIndexed { idx, product ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                val productReviews = reviews.filter { it.productIds.contains(product.id) }

                Column {
                    Text(
                        product.name.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = COLOR_PRODUCT_NAME,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))

                    if (productReviews.isEmpty()) {
                        // Write review button
                        OutlinedButton(
                            onClick = onOpenReviews,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF4F46E5)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Write review", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("No linked reviews", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    } else {
                        Text("${productReviews.size} review${if (productReviews.size > 1) "s" else ""} available", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onOpenReviews,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Read review", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Product Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProductPickerDialog(
    slotIndex: Int,
    allProducts: List<ProductItem>,
    search: String,
    onSearchChange: (String) -> Unit,
    onSelect: (ProductItem) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(allProducts, search) {
        if (search.isBlank()) allProducts
        else allProducts.filter {
            val q = search.lowercase()
            it.name.lowercase().contains(q) || it.brand.lowercase().contains(q) ||
                it.tags.any { t -> t.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.75f),
        title = { Text("Select product for Record ${slotIndex + 1}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search products…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(product) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(product.brand, fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = Color(0xFFF8FAFC))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
