package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlin.random.Random

@Composable
fun SharedProductWorkspaceScreen(
    product: ProductItem,
    specs: List<SpecificationItem>,
    reviews: List<ReviewItem>,
    inCompare: Boolean,
    onToggleCompare: () -> Unit,
    onSaveProduct: (ProductItem) -> Unit,
    onSaveSpec: (SpecificationItem) -> Unit,
    onDeleteSpec: (String) -> Unit,
    onDeleteProduct: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Editable product fields
    var isEditingProduct by remember(product.id) { mutableStateOf(false) }
    var editName by remember(product.id) { mutableStateOf(product.name) }
    var editBrand by remember(product.id) { mutableStateOf(product.brand) }
    var editTags by remember(product.id) { mutableStateOf(product.tags.joinToString(", ")) }
    var editOverview by remember(product.id) { mutableStateOf(product.overview) }
    var editPicUrl by remember(product.id) { mutableStateOf(product.pictures.firstOrNull()?.url ?: "") }

    // Add / Edit spec dialog
    var editingSpec by remember { mutableStateOf<SpecificationItem?>(null) }
    var showAddSpecDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val imagePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Single
    ) { file ->
        file?.path?.let { editPicUrl = it }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete product?") },
            text = { Text("'${product.name}' and all its specs will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteProduct() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddSpecDialog || editingSpec != null) {
        SpecEditDialog(
            initial = editingSpec,
            productId = product.id,
            productTitle = product.name,
            nextOrder = (specs.maxOfOrNull { it.effectiveOrder } ?: 0) + 1,
            onSave = { spec ->
                onSaveSpec(spec)
                showAddSpecDialog = false
                editingSpec = null
            },
            onDismiss = {
                showAddSpecDialog = false
                editingSpec = null
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
        // ─── Top Bar ─────────────────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF334155))
                }
                Text(
                    product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onToggleCompare,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (inCompare) Color(0xFFEEF2FF) else Color.Transparent,
                        contentColor = Color(0xFF4F46E5)
                    )
                ) {
                    Icon(
                        if (inCompare) Icons.Default.CheckCircle else Icons.Default.Add,
                        contentDescription = null, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (inCompare) "In compare" else "+ Compare")
                }
                IconButton(onClick = { isEditingProduct = !isEditingProduct }) {
                    Icon(
                        if (isEditingProduct) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = "Edit product",
                        tint = Color(0xFF4F46E5)
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ─── Inline Edit Product Form (when toggled) ─────────────────────────
        if (isEditingProduct) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Edit Product Details", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF4F46E5))
                        OutlinedTextField(
                            value = editName, onValueChange = { editName = it },
                            label = { Text("Product Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = editBrand, onValueChange = { editBrand = it },
                            label = { Text("Brand *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = editTags, onValueChange = { editTags = it },
                            label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = editOverview, onValueChange = { editOverview = it },
                            label = { Text("Overview") }, modifier = Modifier.fillMaxWidth(), minLines = 3
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editPicUrl, onValueChange = { editPicUrl = it },
                                label = { Text("Image URL or local path") },
                                modifier = Modifier.weight(1f), singleLine = true
                            )
                            OutlinedButton(onClick = { imagePicker.launch() }) {
                                Text("Browse…")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val newTags = editTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    val newPics = if (editPicUrl.isNotBlank()) {
                                        listOf(PictureItem(id = "pic_1", name = "$editName.jpg", url = editPicUrl.trim()))
                                    } else product.pictures
                                    onSaveProduct(
                                        product.copy(
                                            name = editName.trim(),
                                            brand = editBrand.trim(),
                                            tags = newTags,
                                            overview = editOverview.trim(),
                                            pictures = newPics
                                        )
                                    )
                                    isEditingProduct = false
                                },
                                enabled = editName.isNotBlank() && editBrand.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Text("Save Changes")
                            }
                            OutlinedButton(onClick = { isEditingProduct = false }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        // ─── Product Image ───────────────────────────────────────────────────
        val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }
        if (imageUrl != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // ─── Brand / Tags / Overview ─────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Brand", fontSize = 12.sp, color = Color(0xFF64748B))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E293B)) {
                            Text(
                                product.brand.uppercase(),
                                fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (product.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            product.tags.forEach { tag ->
                                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
                                    Text(
                                        tag, fontSize = 11.sp, color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (product.overview.isNotBlank()) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Text(product.overview, fontSize = 13.sp, color = Color(0xFF334155), lineHeight = 20.sp)
                    }
                }
            }
        }

        // ─── Specifications Header + Add Spec Button ─────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Specifications (${specs.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
                OutlinedButton(
                    onClick = { showAddSpecDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Spec", fontSize = 12.sp)
                }
            }
        }

        // ─── Specifications List (Editable & Deletable) ──────────────────────
        if (specs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No specifications yet. Tap '+ Add Spec' above.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        specs.forEachIndexed { idx, spec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 0) Color.White else Color(0xFFF8FAFC))
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    spec.specName,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(120.dp)
                                )
                                Text(
                                    buildString {
                                        append(spec.details)
                                        if (!spec.unit.isNullOrBlank()) append(" ${spec.unit}")
                                    },
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { editingSpec = spec }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit spec", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = { onDeleteSpec(spec.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete spec", tint = Color(0xFFEF4444).copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                                }
                            }
                            if (idx < specs.lastIndex) HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }

        // ─── Reviews for this product ────────────────────────────────────────
        if (reviews.isNotEmpty()) {
            item {
                Text("Reviews (${reviews.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
            }
            itemsIndexed(reviews) { _, review ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(review.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "By ${review.authorName}  ·  Rating: ${review.rating}/5",
                            fontSize = 11.sp, color = Color(0xFF64748B)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFF1F5F9))
                        com.example.ui.components.CrossPlatformReviewRenderer(
                            content = review.body,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecEditDialog(
    initial: SpecificationItem?,
    productId: String,
    productTitle: String,
    nextOrder: Int,
    onSave: (SpecificationItem) -> Unit,
    onDismiss: () -> Unit
) {
    var specName by remember { mutableStateOf(initial?.specName ?: "") }
    var details by remember { mutableStateOf(initial?.details ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Specification" else "Edit Specification", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = specName, onValueChange = { specName = it },
                    label = { Text("Spec Name * (e.g. Weight, Battery)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = details, onValueChange = { details = it },
                    label = { Text("Details *") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text("Unit (optional, e.g. g, mAh, mm)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val spec = SpecificationItem(
                        id = initial?.id ?: "spec_${Random.nextLong(100000000, 999999999)}",
                        productId = productId,
                        productTitle = productTitle,
                        specName = specName.trim(),
                        details = details.trim(),
                        unit = unit.trim().ifBlank { null },
                        orderIndex = initial?.effectiveOrder ?: nextOrder
                    )
                    onSave(spec)
                },
                enabled = specName.isNotBlank() && details.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
