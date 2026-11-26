package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel
import com.example.ui.components.SharedDraggableTwoPaneLayout
import com.example.ui.components.SharedProductDetailPane
import com.example.ui.components.sharedHtmlToPlainText

// ─────────────────────────────────────────────────────────────────────────────
// SharedCatalogScreen — 1:1 parity with Android CatalogScreen
//
// • Wide (≥ 768dp) + product selected: SharedDraggableTwoPaneLayout with
//   CatalogListPane (left) + SharedProductDetailPane (right, Product View).
// • Narrow + product selected: SharedProductDetailPane full screen.
// • Clicking a product card or "Details >" opens Product View (NOT Edit).
// • Clicking "Edit" inside Product View opens SharedProductWorkspaceScreen.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SharedCatalogScreen(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    compareIds: List<String>,
    onToggleCompare: (String) -> Unit,
    onAddProductClick: () -> Unit,
    onEditProduct: (ProductItem) -> Unit,
    modifier: Modifier = Modifier,
    onClearCompare: () -> Unit = {},
    onSmartAddClick: () -> Unit = {},
    onDeleteProduct: (String) -> Unit = {},
    onAddReview: (ProductItem) -> Unit = {},
    onNavigateToCompare: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTagOrBrand by remember { mutableStateOf<String?>("All") }
    var catalogViewMode by remember { mutableStateOf("grid") }   // "grid" | "table"
    var selectedProduct by remember { mutableStateOf<ProductItem?>(null) }

    val allTags = remember(products) {
        val set = linkedSetOf<String>()
        products.forEach { p -> p.tags.forEach { t -> if (t.isNotBlank()) set.add(t) } }
        set.toList()
    }
    val allBrands = remember(products) {
        val set = linkedSetOf<String>()
        products.forEach { p -> if (p.brand.isNotBlank() && !p.brand.equals("Generic", ignoreCase = true)) set.add(p.brand) }
        set.toList()
    }
    val allProductNames = remember(products) { products.map { it.name } }

    val filteredProducts = remember(products, searchQuery, selectedTagOrBrand, specifications) {
        val q = searchQuery.trim().lowercase()
        val isAll = selectedTagOrBrand.isNullOrBlank() || selectedTagOrBrand.equals("All", ignoreCase = true)
        products.filter { p ->
            val matchesTag = isAll ||
                    p.tags.any { it.equals(selectedTagOrBrand, ignoreCase = true) } ||
                    p.brand.equals(selectedTagOrBrand, ignoreCase = true) ||
                    p.name.equals(selectedTagOrBrand, ignoreCase = true)
            val matchesQuery = q.isBlank() || p.name.lowercase().contains(q) ||
                    p.brand.lowercase().contains(q) ||
                    p.overview.lowercase().contains(q) ||
                    p.tags.any { it.lowercase().contains(q) } ||
                    specifications.filter { it.productId == p.id || it.productTitle == p.name }
                        .any { it.specName.lowercase().contains(q) || sharedHtmlToPlainText(it.details).lowercase().contains(q) }
            matchesTag && matchesQuery
        }
    }

    // Keep selectedProduct synced with live updates / deletions
    LaunchedEffect(products) {
        val cur = selectedProduct
        if (cur != null) {
            selectedProduct = products.find { it.id == cur.id }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFFF1F5F9))) {
        val showTwoPane = maxWidth >= 768.dp
        val activeSelected = selectedProduct

        if (showTwoPane && activeSelected != null) {
            val prodSpecs = specifications
                .filter { it.productId == activeSelected.id || it.productTitle == activeSelected.name }
                .sortedBy { it.effectiveOrder }
            val prodReviews = reviews.filter { activeSelected.id in it.productIds || activeSelected.name in it.productTitles }

            SharedDraggableTwoPaneLayout(
                modifier = Modifier.fillMaxSize(),
                defaultSplitFraction = 0.45f,
                minSplitFraction = 0.20f,
                maxSplitFraction = 0.80f,
                firstPane = { paneModifier ->
                    CatalogListPane(
                        modifier = paneModifier,
                        allTags = allTags,
                        allBrands = allBrands,
                        allProductNames = allProductNames,
                        selectedTagOrBrand = selectedTagOrBrand,
                        onSelectTagOrBrand = { selectedTagOrBrand = it },
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        catalogViewMode = catalogViewMode,
                        onCatalogViewModeChange = { catalogViewMode = it },
                        onSmartAddClick = onSmartAddClick,
                        onAddProductClick = onAddProductClick,
                        filteredProducts = filteredProducts,
                        specifications = specifications,
                        reviews = reviews,
                        compareIds = compareIds,
                        onToggleCompare = onToggleCompare,
                        selectedProduct = activeSelected,
                        showTwoPane = true,
                        onProductClick = { p -> selectedProduct = p }
                    )
                },
                secondPane = { paneModifier ->
                    SharedProductDetailPane(
                        product = activeSelected,
                        specifications = prodSpecs,
                        reviews = prodReviews,
                        isCompared = compareIds.contains(activeSelected.id),
                        onToggleCompare = { onToggleCompare(activeSelected.id) },
                        onEdit = { onEditProduct(activeSelected) },
                        onDelete = {
                            onDeleteProduct(activeSelected.id)
                            selectedProduct = null
                        },
                        onAddReview = { onAddReview(activeSelected) },
                        onDismiss = { selectedProduct = null },
                        isTwoPane = true,
                        modifier = paneModifier
                    )
                }
            )
        } else if (!showTwoPane && activeSelected != null) {
            val prodSpecs = specifications
                .filter { it.productId == activeSelected.id || it.productTitle == activeSelected.name }
                .sortedBy { it.effectiveOrder }
            val prodReviews = reviews.filter { activeSelected.id in it.productIds || activeSelected.name in it.productTitles }

            SharedProductDetailPane(
                product = activeSelected,
                specifications = prodSpecs,
                reviews = prodReviews,
                isCompared = compareIds.contains(activeSelected.id),
                onToggleCompare = { onToggleCompare(activeSelected.id) },
                onEdit = { onEditProduct(activeSelected) },
                onDelete = {
                    onDeleteProduct(activeSelected.id)
                    selectedProduct = null
                },
                onAddReview = { onAddReview(activeSelected) },
                onDismiss = { selectedProduct = null },
                isTwoPane = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CatalogListPane(
                modifier = Modifier.fillMaxSize(),
                allTags = allTags,
                allBrands = allBrands,
                allProductNames = allProductNames,
                selectedTagOrBrand = selectedTagOrBrand,
                onSelectTagOrBrand = { selectedTagOrBrand = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                catalogViewMode = catalogViewMode,
                onCatalogViewModeChange = { catalogViewMode = it },
                onSmartAddClick = onSmartAddClick,
                onAddProductClick = onAddProductClick,
                filteredProducts = filteredProducts,
                specifications = specifications,
                reviews = reviews,
                compareIds = compareIds,
                onToggleCompare = onToggleCompare,
                selectedProduct = null,
                showTwoPane = false,
                onProductClick = { p -> selectedProduct = p }
            )
        }

        // Bottom Floating Comparison Tray (when products selected and not full-screen phone detail)
        if (compareIds.isNotEmpty() && (showTwoPane || activeSelected == null)) {
            val comparedProducts = remember(compareIds, products) {
                compareIds.mapNotNull { id -> products.find { it.id == id } }
            }
            if (comparedProducts.isNotEmpty()) {
                SharedComparisonTray(
                    selectedProducts = comparedProducts,
                    onRemoveProduct = onToggleCompare,
                    onClearAll = onClearCompare,
                    onCompareNow = onNavigateToCompare,
                    onOpenSmartAdd = onSmartAddClick,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CatalogListPane — left/main list pane matching Android CatalogListPane
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CatalogListPane(
    modifier: Modifier = Modifier,
    allTags: List<String>,
    allBrands: List<String>,
    allProductNames: List<String>,
    selectedTagOrBrand: String?,
    onSelectTagOrBrand: (String?) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    catalogViewMode: String,
    onCatalogViewModeChange: (String) -> Unit,
    onSmartAddClick: () -> Unit,
    onAddProductClick: () -> Unit,
    filteredProducts: List<ProductItem>,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    compareIds: List<String>,
    onToggleCompare: (String) -> Unit,
    selectedProduct: ProductItem?,
    showTwoPane: Boolean,
    onProductClick: (ProductItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp,
            bottom = if (compareIds.isNotEmpty()) 110.dp else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. TagBrandProductFilter Card ────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            SharedTagBrandProductFilter(
                tags = allTags,
                brands = allBrands,
                productNames = allProductNames,
                selectedTag = selectedTagOrBrand ?: "All",
                onSelectTag = { tag ->
                    onSelectTagOrBrand(if (tag.equals("All", ignoreCase = true)) "All" else tag)
                },
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 2. Actions Row: Grid/Table + Smart Add Product + Add Product ─────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grid / Table toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (catalogViewMode == "grid") Color.White else Color.Transparent,
                            shadowElevation = if (catalogViewMode == "grid") 1.dp else 0.dp,
                            modifier = Modifier.fillMaxHeight().clickable { onCatalogViewModeChange("grid") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.GridView, "Grid view",
                                    tint = if (catalogViewMode == "grid") Color(0xFF4F46E5) else Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    "Grid", fontSize = 11.sp,
                                    fontWeight = if (catalogViewMode == "grid") FontWeight.Bold else FontWeight.Medium,
                                    color = if (catalogViewMode == "grid") Color(0xFF0F172A) else Color(0xFF64748B)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (catalogViewMode == "table") Color.White else Color.Transparent,
                            shadowElevation = if (catalogViewMode == "table") 1.dp else 0.dp,
                            modifier = Modifier.fillMaxHeight().clickable { onCatalogViewModeChange("table") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.FormatListBulleted, "Table view",
                                    tint = if (catalogViewMode == "table") Color(0xFF4F46E5) else Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    "Table", fontSize = 11.sp,
                                    fontWeight = if (catalogViewMode == "table") FontWeight.Bold else FontWeight.Medium,
                                    color = if (catalogViewMode == "table") Color(0xFF0F172A) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                // Smart Add Product (dark slate pill with single-line marquee text)
                Button(
                    onClick = onSmartAddClick,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFACC15), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    SharedButtonText(
                        text = "Smart Add Product",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Add Product (indigo pill with single-line marquee text)
                Button(
                    onClick = onAddProductClick,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    SharedButtonText(
                        text = "Add Product",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // ── 3. Product grid / table / empty state ────────────────────────────
        if (filteredProducts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SearchOff, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(40.dp))
                        Text(
                            "No products matching \"${selectedTagOrBrand ?: searchQuery}\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else if (catalogViewMode == "table") {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogTableView(
                    products = filteredProducts,
                    specifications = specifications,
                    reviews = reviews,
                    compareIds = compareIds,
                    onToggleCompare = onToggleCompare,
                    onProductClick = onProductClick
                )
            }
        } else {
            items(filteredProducts, key = { it.id }) { product ->
                val isCompared = compareIds.contains(product.id)
                val isSelected = showTwoPane && selectedProduct?.id == product.id
                val prodSpecs = specifications
                    .filter { it.productId == product.id || it.productTitle == product.name }
                    .sortedBy { it.effectiveOrder }
                val prodReviews = reviews.filter { product.id in it.productIds || product.name in it.productTitles }

                CatalogProductCard(
                    product = product,
                    prodSpecs = prodSpecs,
                    prodReviews = prodReviews,
                    isCompared = isCompared,
                    isSelected = isSelected,
                    onToggleCompare = { onToggleCompare(product.id) },
                    onClick = { onProductClick(product) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Product Grid Card — matches Android's card layout
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CatalogProductCard(
    product: ProductItem,
    prodSpecs: List<SpecificationItem>,
    prodReviews: List<ReviewItem>,
    isCompared: Boolean,
    isSelected: Boolean,
    onToggleCompare: () -> Unit,
    onClick: () -> Unit
) {
    val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFF8FAFC) else Color.White),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Brand badge + Compare button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF334155)) {
                    Text(
                        product.brand.uppercase().ifBlank { "GENERIC" },
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), letterSpacing = 0.5.sp
                    )
                }
                OutlinedButton(
                    onClick = onToggleCompare,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isCompared) Color(0xFFEEF2FF) else Color.White,
                        contentColor = if (isCompared) Color(0xFF4F46E5) else Color(0xFF0F172A)
                    ),
                    border = BorderStroke(1.dp, if (isCompared) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(if (isCompared) Icons.Default.Check else Icons.Default.Add, null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isCompared) "Compared" else "Compare", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Photo container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFAFAFA),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth().height(140.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = product.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    } else {
                        Icon(Icons.Default.Devices, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(48.dp))
                    }
                }
            }

            // Tags row
            if (product.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    product.tags.forEach { tag ->
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9)) {
                            Text(
                                tag, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Product title
            Text(product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

            // Spec preview block (top 2 specs)
            if (prodSpecs.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        prodSpecs.take(2).forEach { spec ->
                            val plainDetails = sharedHtmlToPlainText(spec.details)
                            val unitText = spec.unit
                            val isLong = plainDetails.contains('\n') || plainDetails.contains('-') ||
                                    plainDetails.contains('•') || plainDetails.length > 25

                            if (isLong) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(spec.specName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                                        if (!unitText.isNullOrBlank()) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEEF2FF)) {
                                                Text(unitText, fontSize = 10.sp, color = Color(0xFF4F46E5),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    Text(plainDetails, fontSize = 12.sp, color = Color(0xFF0F172A), lineHeight = 17.sp)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(spec.specName, fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(end = 8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(plainDetails, fontSize = 12.sp, color = Color(0xFF0F172A))
                                        if (!unitText.isNullOrBlank()) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEEF2FF)) {
                                                Text(unitText, fontSize = 10.sp, color = Color(0xFF4F46E5),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card footer: reviews · specs + Details >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                    Text("${prodReviews.size} reviews", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text("•", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                    Text("${prodSpecs.size} specs", fontSize = 11.sp, color = Color(0xFF64748B))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
                    Text("Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CatalogTableView — Android-parity horizontal scrollable table view
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CatalogTableView(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    compareIds: List<String>,
    onToggleCompare: (String) -> Unit,
    onProductClick: (ProductItem) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(min = 900.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compare", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(68.dp), textAlign = TextAlign.Center)
                Text("Product", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(220.dp))
                Text("Brand", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(120.dp))
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(150.dp))
                Text("Overview", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(200.dp))
                Text("Specs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                Text("Reviews", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                Text("Action", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569),
                    modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

            products.forEachIndexed { index, product ->
                val isCompared = compareIds.contains(product.id)
                val prodSpecs = specifications.filter { it.productId == product.id || it.productTitle == product.name }
                val prodReviews = reviews.filter { product.id in it.productIds || product.name in it.productTitles }
                val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }

                Row(
                    modifier = Modifier
                        .background(if (index % 2 == 1) Color(0xFFFAFAFA) else Color.White)
                        .clickable { onProductClick(product) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .widthIn(min = 900.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(68.dp), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = isCompared,
                            onCheckedChange = { onToggleCompare(product.id) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4F46E5), uncheckedColor = Color(0xFFCBD5E1))
                        )
                    }
                    Row(
                        modifier = Modifier.width(220.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F5F9), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (imageUrl != null) {
                                    AsyncImage(model = imageUrl, contentDescription = product.name,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else {
                                    Icon(Icons.Default.Devices, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(product.brand.ifBlank { "—" }, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155), modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        modifier = Modifier.width(150.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (product.tags.isEmpty()) {
                            Text("—", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        } else {
                            product.tags.forEach { tag ->
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF1F5F9)) {
                                    Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                    Text(
                        sharedHtmlToPlainText(product.overview).ifBlank { "No overview provided." },
                        fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.width(200.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${prodSpecs.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155), modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                    Text("${prodReviews.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155), modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                    Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { onProductClick(product) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4F46E5))
                        ) {
                            Text("Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (index < products.lastIndex) {
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SharedTagBrandProductFilter — 1:1 parity with Android TagBrandProductFilter
// ─────────────────────────────────────────────────────────────────────────────
private enum class WsFilterType { TAG, BRAND, PRODUCT }
private data class WsFilterOption(val label: String, val type: WsFilterType)

@Composable
private fun SharedTagBrandProductFilter(
    tags: List<String>,
    brands: List<String>,
    productNames: List<String>,
    selectedTag: String,
    onSelectTag: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeCategory by remember { mutableStateOf("all") }

    val (sortedAll, tagList, brandList, productList) = remember(tags, brands, productNames) {
        val tagSet = linkedSetOf<String>()
        val brandSet = linkedSetOf<String>()
        val productSet = linkedSetOf<String>()

        tags.forEach { val t = it.trim(); if (t.isNotBlank() && !t.equals("All", true)) tagSet.add(t) }
        brands.forEach { val b = it.trim(); if (b.isNotBlank() && !b.equals("All", true)) brandSet.add(b) }
        productNames.forEach { val p = it.trim(); if (p.isNotBlank() && !p.equals("All", true)) productSet.add(p) }

        val sTags = tagSet.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val sBrands = brandSet.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val sProducts = productSet.sortedWith(String.CASE_INSENSITIVE_ORDER)

        val combined = buildList {
            sTags.forEach { add(WsFilterOption(it, WsFilterType.TAG)) }
            sBrands.forEach { add(WsFilterOption(it, WsFilterType.BRAND)) }
            sProducts.forEach { add(WsFilterOption(it, WsFilterType.PRODUCT)) }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        listOf(combined, sTags, sBrands, sProducts)
    }

    @Suppress("UNCHECKED_CAST")
    val allOptions = sortedAll as List<WsFilterOption>
    @Suppress("UNCHECKED_CAST")
    val tList = tagList as List<String>
    @Suppress("UNCHECKED_CAST")
    val bList = brandList as List<String>
    @Suppress("UNCHECKED_CAST")
    val pList = productList as List<String>

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty() && activeCategory != "all") {
            val tm = tList.count { it.lowercase().contains(q) }
            val bm = bList.count { it.lowercase().contains(q) }
            val pm = pList.count { it.lowercase().contains(q) }
            val cur = when (activeCategory) {
                "tag" -> tm; "brand" -> bm; "product" -> pm; else -> 0
            }
            if (cur == 0) {
                activeCategory = when {
                    tm > 0 -> "tag"
                    bm > 0 -> "brand"
                    pm > 0 -> "product"
                    else -> "all"
                }
            }
        }
    }

    val displayedItems = remember(allOptions, activeCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            if (activeCategory == "all") {
                allOptions.filter { it.label.lowercase().contains(q) }
            } else {
                val cur = allOptions.filter {
                    val m = when (activeCategory) {
                        "tag" -> it.type == WsFilterType.TAG
                        "brand" -> it.type == WsFilterType.BRAND
                        "product" -> it.type == WsFilterType.PRODUCT
                        else -> true
                    }
                    m && it.label.lowercase().contains(q)
                }
                val other = allOptions.filter {
                    val o = when (activeCategory) {
                        "tag" -> it.type != WsFilterType.TAG
                        "brand" -> it.type != WsFilterType.BRAND
                        "product" -> it.type != WsFilterType.PRODUCT
                        else -> false
                    }
                    o && it.label.lowercase().contains(q)
                }
                cur + other
            }
        } else {
            when (activeCategory) {
                "tag" -> allOptions.filter { it.type == WsFilterType.TAG }
                "brand" -> allOptions.filter { it.type == WsFilterType.BRAND }
                "product" -> allOptions.filter { it.type == WsFilterType.PRODUCT }
                else -> allOptions
            }
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(Icons.Default.Sell, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(15.dp))
                    Text("Filter products by tag / brand / product",
                        fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF0F172A),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("(${allOptions.size} total options)", fontSize = 11.sp, color = Color(0xFF64748B))
            }

            // Category Tabs + Compact Inline Search Box + Filtered badge
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WsCategoryTabPill(
                        icon = Icons.Default.AutoAwesome, label = "All", count = allOptions.size,
                        isSelected = activeCategory == "all", selectedBgColor = Color(0xFF0F172A),
                        selectedIconTint = Color.White, unselectedIconTint = Color(0xFF64748B),
                        onClick = { activeCategory = "all" }
                    )
                    WsCategoryTabPill(
                        icon = Icons.Default.Sell, label = "Tags", count = tList.size,
                        isSelected = activeCategory == "tag", selectedBgColor = Color(0xFF4F46E5),
                        selectedIconTint = Color.White, unselectedIconTint = Color(0xFF818CF8),
                        onClick = { activeCategory = "tag" }
                    )
                    WsCategoryTabPill(
                        icon = Icons.Default.Storefront, label = "Brands", count = bList.size,
                        isSelected = activeCategory == "brand", selectedBgColor = Color(0xFFD97706),
                        selectedIconTint = Color.White, unselectedIconTint = Color(0xFFF59E0B),
                        onClick = { activeCategory = "brand" }
                    )
                    WsCategoryTabPill(
                        icon = Icons.Default.Inventory2, label = "Products", count = pList.size,
                        isSelected = activeCategory == "product", selectedBgColor = Color(0xFF059669),
                        selectedIconTint = Color.White, unselectedIconTint = Color(0xFF10B981),
                        onClick = { activeCategory = "product" }
                    )

                    // Compact Search Input (no text clipping!)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                        modifier = Modifier.height(28.dp).widthIn(min = 95.dp, max = 160.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Search, "Search",
                                tint = if (searchQuery.isNotEmpty()) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp))
                            Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search...", fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium),
                                    cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                    modifier = Modifier.widthIn(min = 48.dp, max = 110.dp)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(Icons.Default.Close, "Clear", tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp).clickable { onSearchQueryChange("") })
                            }
                        }
                    }
                }

                // Active Filter Badge
                val isFiltered = selectedTag.isNotBlank() && !selectedTag.equals("All", ignoreCase = true)
                if (isFiltered) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("Filtered:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(selectedTag, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            Icon(Icons.Default.Close, "Clear filter", tint = Color(0xFF2563EB),
                                modifier = Modifier.size(13.dp).clickable { onSelectTag("All") })
                        }
                    }
                }
            }

            // Dynamic Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSelected = selectedTag.equals("All", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllSelected) Color(0xFF4F46E5) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isAllSelected) Color(0xFF4338CA) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { onSelectTag("All") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isAllSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text("All", fontSize = 11.5.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAllSelected) Color.White else Color(0xFF334155))
                    }
                }

                if (displayedItems.isEmpty() && searchQuery.isNotBlank()) {
                    Text(
                        "No matching options for \"$searchQuery\"",
                        fontSize = 11.5.sp, fontStyle = FontStyle.Italic, color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                displayedItems.forEach { item ->
                    val isSelected = selectedTag.equals(item.label, ignoreCase = true)
                    val icon = when (item.type) {
                        WsFilterType.TAG -> Icons.Default.Sell
                        WsFilterType.BRAND -> Icons.Default.Storefront
                        WsFilterType.PRODUCT -> Icons.Default.Inventory2
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF4338CA) else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { onSelectTag(if (isSelected) "All" else item.label) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Icon(
                                icon, null,
                                tint = if (isSelected) Color.White else when (item.type) {
                                    WsFilterType.TAG -> Color(0xFF818CF8)
                                    WsFilterType.BRAND -> Color(0xFFF59E0B)
                                    WsFilterType.PRODUCT -> Color(0xFF10B981)
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Text(item.label, fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF334155))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WsCategoryTabPill(
    icon: ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    selectedBgColor: Color,
    selectedIconTint: Color,
    unselectedIconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) selectedBgColor else Color(0xFFF1F5F9),
        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(onClick = onClick).height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = if (isSelected) selectedIconTint else unselectedIconTint, modifier = Modifier.size(11.dp))
            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color(0xFF475569))
            Text("($count)", fontSize = 10.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF94A3B8))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single-line marquee button text (matches Android ButtonText)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SharedButtonText(
    text: String,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight? = FontWeight.SemiBold,
    color: Color = Color.Unspecified
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 800,
            repeatDelayMillis = 800,
            velocity = 35.dp
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SharedComparisonTray — floating bottom compare bar (matches Android ComparisonTray)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SharedComparisonTray(
    selectedProducts: List<ProductItem>,
    onRemoveProduct: (String) -> Unit,
    onClearAll: () -> Unit,
    onCompareNow: () -> Unit,
    onOpenSmartAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCollapsed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isCollapsed,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color(0xF20F172A),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color(0xFF4F46E5)),
                modifier = Modifier.wrapContentWidth().clickable { isCollapsed = false }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Scale, null, tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                    Text("${selectedProducts.size} Selected", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF475569)))
                    Text(
                        "Compare Now", color = Color(0xFF818CF8), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            isCollapsed = false
                            if (selectedProducts.size >= 2) onCompareNow()
                        }
                    )
                    Icon(Icons.Default.KeyboardArrowUp, "Expand", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF20F172A),
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E293B)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Scale, null, tint = Color(0xFF818CF8), modifier = Modifier.size(13.dp))
                                Text("${selectedProducts.size} Selected", color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        selectedProducts.forEach { p ->
                            val picUrl = p.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF8FAFC)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (picUrl != null) {
                                            AsyncImage(model = picUrl, contentDescription = p.name,
                                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                        } else {
                                            Icon(Icons.Default.Devices, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Text(p.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                                    Icon(Icons.Default.Close, "Remove", tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp).clickable { onRemoveProduct(p.id) })
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { isCollapsed = true }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenSmartAdd,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFFACC15), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Smart Add", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onCompareNow,
                            enabled = selectedProducts.size >= 2,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Compare Now (${selectedProducts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
