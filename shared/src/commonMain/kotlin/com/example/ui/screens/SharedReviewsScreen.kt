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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.CrossPlatformBackupManager
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.detectDeviceCategory
import com.example.model.resolveSharedImageModel
import com.example.ui.components.CrossPlatformReviewRenderer
import com.example.ui.components.SharedBlockReviewEditor
import com.example.ui.components.SharedDraggableTwoPaneLayout
import com.example.ui.components.SharedFullScreenPhotoLightbox
import com.example.ui.components.sharedHtmlToPlainText
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReviewsScreen(
    reviews: List<ReviewItem>,
    products: List<ProductItem>,
    onAddReview: (ReviewItem) -> Unit,
    onDeleteReview: (String) -> Unit,
    onProductClick: (ProductItem) -> Unit = {},
    preselectedProductId: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedReview by remember { mutableStateOf<ReviewItem?>(null) }
    var editingReview by remember { mutableStateOf<ReviewItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(preselectedProductId != null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFilterTag by rememberSaveable { mutableStateOf("All") }
    var isProductDropdownOpen by remember { mutableStateOf(false) }
    var reviewToDelete by remember { mutableStateOf<ReviewItem?>(null) }

    var lightboxImages by remember { mutableStateOf<List<PictureItem>>(emptyList()) }
    var lightboxInitialIndex by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    // Single .HTML note import via FileKit
    val htmlImportPicker = rememberFilePickerLauncher(
        type = PickerType.File(listOf("html", "htm", "md", "txt")),
        mode = PickerMode.Single
    ) { file ->
        file?.let { pf ->
            scope.launch {
                runCatching {
                    val content = pf.readBytes().decodeToString()
                    val title = pf.name.substringBeforeLast('.')
                    val nextNum = (reviews.maxOfOrNull { it.reviewNumber } ?: 0) + 1
                    val newRev = ReviewItem(
                        id = "rev_${Random.nextLong(100000000, 999999999)}",
                        reviewNumber = nextNum,
                        title = title,
                        body = content,
                        authorName = "UpNote Import",
                        rating = 5
                    )
                    onAddReview(newRev)
                    selectedReview = newRev
                }
            }
        }
    }

    if (lightboxImages.isNotEmpty()) {
        SharedFullScreenPhotoLightbox(
            images = lightboxImages,
            initialIndex = lightboxInitialIndex,
            onDismiss = { lightboxImages = emptyList() }
        )
    }

    if (reviewToDelete != null) {
        val target = reviewToDelete!!
        AlertDialog(
            onDismissRequest = { reviewToDelete = null },
            title = { Text("Delete Review?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${target.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReview(target.id)
                        if (selectedReview?.id == target.id) selectedReview = null
                        reviewToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { reviewToDelete = null }) { Text("Cancel") } }
        )
    }

    // Full-screen Edit / Write Review mode (Word-style Fixed Toolbar + Inline Blocks)
    if (isCreatingNew || editingReview != null) {
        val nextNum = (reviews.maxOfOrNull { it.reviewNumber } ?: 0) + 1
        val allLibraryPictures = remember(products, reviews) {
            (products.flatMap { it.pictures } + reviews.flatMap { it.pictures })
                .distinctBy { it.name.ifBlank { it.url } }
        }
        SharedBlockReviewEditor(
            existingReview = editingReview,
            products = products,
            allLibraryPictures = allLibraryPictures,
            nextReviewNumber = nextNum,
            preselectedProductId = preselectedProductId,
            onSave = { saved ->
                onAddReview(saved)
                selectedReview = saved
                editingReview = null
                isCreatingNew = false
            },
            onCancel = {
                editingReview = null
                isCreatingNew = false
            },
            onOpenLightbox = { imgs, idx ->
                lightboxImages = imgs
                lightboxInitialIndex = idx
            },
            modifier = modifier
        )
        return
    }

    val allTags = remember(products) {
        products.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val allBrands = remember(products) {
        products.map { it.brand }.filter { it.isNotBlank() && !it.equals("Generic", ignoreCase = true) }.distinct().sorted()
    }

    val filteredReviews = remember(reviews, searchQuery, selectedProductId, selectedFilterTag, products) {
        val isAll = selectedFilterTag.equals("All", ignoreCase = true)
        reviews.filter { r ->
            val matchesProduct = selectedProductId == null ||
                selectedProductId in r.productIds ||
                products.find { it.id == selectedProductId }?.name in r.productTitles

            val matchesFilterTag = isAll || run {
                val matchedProds = products.filter { p -> p.id in r.productIds || p.name in r.productTitles }
                matchedProds.any { p ->
                    p.tags.any { it.equals(selectedFilterTag, ignoreCase = true) } ||
                        p.brand.equals(selectedFilterTag, ignoreCase = true) ||
                        p.name.equals(selectedFilterTag, ignoreCase = true)
                } || r.title.contains(selectedFilterTag, ignoreCase = true) ||
                    r.body.contains(selectedFilterTag, ignoreCase = true)
            }

            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                r.title.lowercase().contains(q) ||
                r.body.lowercase().contains(q) ||
                r.authorName.lowercase().contains(q) ||
                r.productTitles.any { it.lowercase().contains(q) }

            matchesProduct && matchesFilterTag && matchesQuery
        }
    }

    val selectedProductName = remember(selectedProductId, products) {
        selectedProductId?.let { id -> products.find { it.id == id }?.name }
    }

    val reviewListContent: @Composable (Modifier) -> Unit = { listMod ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = listMod.fillMaxSize().background(Color(0xFFF1F5F9)),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header: Community User Reviews
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Community User Reviews",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Real feedback and benchmark impressions",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFE0E7FF))
                        ) {
                            Text(
                                text = "${reviews.size} Total Reviews",
                                color = Color(0xFF4F46E5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Actions Row: Import HTML & Write a Review
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { htmlImportPicker.launch() },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import HTML Note", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { isCreatingNew = true; editingReview = null },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Write a Review", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    // Search Bar + Product Filter Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search reviews...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFFCBD5E1),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Box {
                            OutlinedButton(
                                onClick = { isProductDropdownOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = selectedProductName ?: "All (${reviews.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                            }
                            DropdownMenu(expanded = isProductDropdownOpen, onDismissRequest = { isProductDropdownOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("All (${reviews.size})", fontWeight = FontWeight.Bold) },
                                    onClick = { selectedProductId = null; isProductDropdownOpen = false }
                                )
                                products.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text(prod.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = { selectedProductId = prod.id; isProductDropdownOpen = false }
                                    )
                                }
                            }
                        }
                    }

                    // TagBrandProductFilter
                    SharedTagBrandProductFilter(
                        tags = allTags,
                        brands = allBrands,
                        productNames = products.map { it.name },
                        selectedTag = selectedFilterTag,
                        onSelectTag = { selectedFilterTag = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it }
                    )
                }
            }

            if (filteredReviews.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(44.dp), tint = Color(0xFFCBD5E1))
                            Text(if (reviews.isEmpty()) "No reviews yet" else "No matching reviews", color = Color(0xFF64748B), fontSize = 15.sp)
                        }
                    }
                }
            } else {
                items(filteredReviews, key = { it.id }) { review ->
                    val linkedProduct = products.find { p ->
                        p.id in review.productIds || review.productTitles.any { t -> t.equals(p.name, ignoreCase = true) }
                    }
                    val isSelected = selectedReview?.id == review.id
                    val allReviewPics = remember(review) { extractReviewImages(review) }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().clickable { selectedReview = review }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Product badge pill
                            if (linkedProduct != null || review.productTitles.isNotEmpty()) {
                                val label = linkedProduct?.name ?: review.productTitles.first()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEEF2FF),
                                    border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
                                    modifier = Modifier.clickable(enabled = linkedProduct != null) {
                                        linkedProduct?.let(onProductClick)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(12.dp))
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(11.dp))
                                    }
                                }
                            }

                            // Review Title
                            Text(
                                text = review.title.ifBlank { "Untitled Review" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Author + Rating + View / Edit / Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(shape = CircleShape, color = Color(0xFF0F172A), modifier = Modifier.size(22.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = review.authorName.firstOrNull()?.uppercase() ?: "U",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = review.authorName.ifBlank { "Verified Reviewer" },
                                        fontSize = 12.sp,
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                                    Text("${review.rating}.0", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selectedReview = review }.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = "View", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("View", fontSize = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(onClick = { editingReview = review }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { reviewToDelete = review }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            // Plain-text snippet
                            val plainSnippet = remember(review.body) {
                                sharedHtmlToPlainText(review.body).take(150)
                            }
                            if (plainSnippet.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = plainSnippet,
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // Thumbnail strip
                            if (allReviewPics.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(allReviewPics.take(5).size) { pIdx ->
                                        val pic = allReviewPics[pIdx]
                                        AsyncImage(
                                            model = resolveSharedImageModel(pic.url, pic.name),
                                            contentDescription = pic.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    lightboxImages = allReviewPics
                                                    lightboxInitialIndex = pIdx
                                                }
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        val activeReview = selectedReview?.let { sel -> reviews.find { it.id == sel.id } ?: sel }

        if (isWide && activeReview != null) {
            SharedDraggableTwoPaneLayout(
                firstPane = { mod -> reviewListContent(mod) },
                secondPane = { mod ->
                    SharedReviewViewerPane(
                        review = activeReview,
                        products = products,
                        onEdit = { editingReview = activeReview },
                        onDelete = { reviewToDelete = activeReview },
                        onClose = { selectedReview = null },
                        onProductClick = onProductClick,
                        onOpenLightbox = { imgs, idx ->
                            lightboxImages = imgs
                            lightboxInitialIndex = idx
                        },
                        modifier = mod
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (activeReview != null) {
            SharedReviewViewerPane(
                review = activeReview,
                products = products,
                onEdit = { editingReview = activeReview },
                onDelete = { reviewToDelete = activeReview },
                onClose = { selectedReview = null },
                onProductClick = onProductClick,
                onOpenLightbox = { imgs, idx ->
                    lightboxImages = imgs
                    lightboxInitialIndex = idx
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            reviewListContent(Modifier.fillMaxSize())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// View Review Pane (Matches Android RichReviewViewer)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SharedReviewViewerPane(
    review: ReviewItem,
    products: List<ProductItem>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onProductClick: (ProductItem) -> Unit,
    onOpenLightbox: (List<PictureItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomPercent by rememberSaveable { mutableIntStateOf(105) }
    val linkedProduct = products.find { p ->
        p.id in review.productIds || review.productTitles.any { t -> t.equals(p.name, ignoreCase = true) }
    }
    val allPics = remember(review) { extractReviewImages(review) }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Title + subtitle, Zoom [- 105% +], Edit dark button, Delete, Close
            Surface(color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = review.title.ifBlank { "Untitled Review" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Review #${review.reviewNumber} • ${linkedProduct?.name ?: review.productTitles.joinToString(", ").ifBlank { "General" }}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Zoom pill [- 105% +]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                IconButton(onClick = { zoomPercent = (zoomPercent - 10).coerceAtLeast(60) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(13.dp))
                                }
                                Text("$zoomPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), modifier = Modifier.padding(horizontal = 4.dp))
                                IconButton(onClick = { zoomPercent = (zoomPercent + 10).coerceAtMost(150) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        Button(
                            onClick = onEdit,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.TopCenter
            ) {
                val pageWidth = (maxWidth * (zoomPercent / 100f)).coerceAtLeast(360.dp)
                LazyColumn(
                    modifier = Modifier.widthIn(max = pageWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Header Summary Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEEF2FF)) {
                                Text(
                                    text = "REVIEW #${review.reviewNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = review.title.ifBlank { "Untitled Review" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFAFAFA),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    repeat(5) { idx ->
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (idx < review.rating) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("${review.rating}.0", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                }
                            }

                            if (linkedProduct != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.size(44.dp)) {
                                            val pPic = linkedProduct.pictures.firstOrNull()
                                            val pModel = pPic?.let { resolveSharedImageModel(it.url, it.name) }
                                            if (pModel != null) {
                                                AsyncImage(model = pModel, contentDescription = linkedProduct.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF6366F1))
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("PRODUCT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                            Text(linkedProduct.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                        }
                                        OutlinedButton(
                                            onClick = { onProductClick(linkedProduct) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                                        ) {
                                            Text("View Product", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF0F172A))
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = CircleShape, color = Color(0xFF0F172A), modifier = Modifier.size(26.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(review.authorName.firstOrNull()?.uppercase() ?: "U", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(review.authorName.ifBlank { "Verified Reviewer" }, fontSize = 13.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                                if (review.createdTime.isNotBlank()) {
                                    Text("• ${review.createdTime.take(10)}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }

                // Rich Body Content (tables, links, images, headings) inside clean white document card
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CrossPlatformReviewRenderer(
                            content = review.body,
                            zoomPercent = zoomPercent,
                            onImageClick = { clickedUrl ->
                                val idx = allPics.indexOfFirst { it.url == clickedUrl || it.name == clickedUrl.substringAfterLast('/') }.coerceAtLeast(0)
                                val imgs = if (allPics.isNotEmpty()) allPics else listOf(PictureItem("img", clickedUrl.substringAfterLast('/'), clickedUrl))
                                onOpenLightbox(imgs, idx)
                            },
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        )
                    }
                }

                // Extra attached pictures not already inline
                if (review.pictures.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Attached Photos (${review.pictures.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(review.pictures.size) { pIdx ->
                                    val pic = review.pictures[pIdx]
                                    AsyncImage(
                                        model = resolveSharedImageModel(pic.url, pic.name),
                                        contentDescription = pic.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .clickable { onOpenLightbox(review.pictures, pIdx) }
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

private fun extractReviewImages(review: ReviewItem): List<PictureItem> {
    val list = mutableListOf<PictureItem>()
    val seen = mutableSetOf<String>()
    review.pictures.forEach { pic ->
        val key = pic.url.substringAfterLast('/').lowercase()
        if (seen.add(key)) list.add(pic)
    }
    val imgRegex = """<img[^>]+src=["']([^"']+)["'][^>]*>""".toRegex(RegexOption.IGNORE_CASE)
    imgRegex.findAll(review.body).forEachIndexed { idx, m ->
        val url = m.groupValues[1]
        val key = url.substringAfterLast('/').lowercase()
        if (seen.add(key)) {
            list.add(PictureItem(id = "inline_$idx", name = url.substringAfterLast('/'), url = url))
        }
    }
    return list
}
