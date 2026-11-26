package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.ui.components.CrossPlatformReviewRenderer
import kotlin.random.Random

@Composable
fun SharedReviewsScreen(
    reviews: List<ReviewItem>,
    products: List<ProductItem>,
    onAddReview: (ReviewItem) -> Unit,
    onDeleteReview: (String) -> Unit,
    preselectedProductId: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedReview by remember { mutableStateOf<ReviewItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(preselectedProductId != null) }
    var searchQuery by remember { mutableStateOf("") }

    if (showCreateDialog) {
        CreateReviewDialog(
            products = products,
            nextReviewNumber = (reviews.maxOfOrNull { it.reviewNumber } ?: 0) + 1,
            preselectedProductId = preselectedProductId,
            onSave = { review ->
                onAddReview(review)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (selectedReview != null) {
        val review = reviews.find { it.id == selectedReview!!.id } ?: selectedReview!!
        var showDeleteConfirm by remember { mutableStateOf(false) }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete review?") },
                text = { Text("'${review.title}' will be permanently deleted.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDeleteReview(review.id)
                        selectedReview = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }

        LazyColumn(
            modifier = modifier
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { selectedReview = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(review.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEEF2FF)) {
                                Text(
                                    "#${review.reviewNumber}",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text("By ${review.authorName}", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text("★ ${review.rating}/5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                        }
                        if (review.productTitles.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                review.productTitles.forEach { pt ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
                                        Text(
                                            pt, fontSize = 11.sp, color = Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        CrossPlatformReviewRenderer(
                            content = review.body,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }

    val filtered = remember(reviews, searchQuery) {
        if (searchQuery.isBlank()) reviews
        else {
            val q = searchQuery.trim().lowercase()
            reviews.filter {
                it.title.lowercase().contains(q) ||
                    it.authorName.lowercase().contains(q) ||
                    it.productTitles.any { t -> t.lowercase().contains(q) } ||
                    it.body.lowercase().contains(q)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search + Write Review header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search reviews, authors, or products…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Write Review", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
                    Text(
                        if (reviews.isEmpty()) "No reviews yet" else "No matching reviews",
                        fontSize = 15.sp, color = Color(0xFF64748B)
                    )
                    OutlinedButton(onClick = { showCreateDialog = true }) {
                        Text("+ Write First Review")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { review ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedReview = review },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFFEEF2FF)) {
                                    Text(
                                        "#${review.reviewNumber}",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(review.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("★ ${review.rating}/5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                            Text("By ${review.authorName}", fontSize = 11.sp, color = Color(0xFF64748B))
                            if (review.productTitles.isNotEmpty()) {
                                Text(review.productTitles.joinToString("  ·  "), fontSize = 11.sp, color = Color(0xFF4F46E5))
                            }
                            if (review.body.isNotBlank()) {
                                Text(
                                    review.body.take(140).replace("<[^>]*>".toRegex(), ""),
                                    fontSize = 12.sp, color = Color(0xFF475569),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateReviewDialog(
    products: List<ProductItem>,
    nextReviewNumber: Int,
    preselectedProductId: String?,
    onSave: (ReviewItem) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }
    var body by remember { mutableStateOf("") }
    val selectedProductIds = remember {
        mutableStateListOf<String>().apply {
            if (preselectedProductId != null) add(preselectedProductId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f),
        title = { Text("Write Review #$nextReviewNumber", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Review Title *") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = authorName, onValueChange = { authorName = it },
                            label = { Text("Author") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                IconButton(onClick = { rating = star }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "$star stars",
                                        tint = if (star <= rating) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = body, onValueChange = { body = it },
                        label = { Text("Review Content * (Markdown or HTML supported)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 5
                    )
                }
                if (products.isNotEmpty()) {
                    item {
                        Text("Link to products:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                    }
                    items(products) { product ->
                        val checked = selectedProductIds.contains(product.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedProductIds.remove(product.id)
                                    else selectedProductIds.add(product.id)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedProductIds.add(product.id)
                                    else selectedProductIds.remove(product.id)
                                }
                            )
                            Text("${product.name} (${product.brand})", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val linkedProducts = products.filter { selectedProductIds.contains(it.id) }
                    val review = ReviewItem(
                        id = "rev_${Random.nextLong(100000000, 999999999)}",
                        reviewNumber = nextReviewNumber,
                        title = title.trim(),
                        body = body.trim(),
                        authorName = authorName.trim().ifBlank { "Anonymous" },
                        rating = rating,
                        productIds = linkedProducts.map { it.id },
                        productTitles = linkedProducts.map { it.name }
                    )
                    onSave(review)
                },
                enabled = title.isNotBlank() && body.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Publish Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
