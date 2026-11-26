package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel

@Composable
fun SharedCatalogScreen(
    products: List<ProductItem>,
    specifications: List<SpecificationItem>,
    compareIds: List<String>,
    onToggleCompare: (String) -> Unit,
    onAddProductClick: () -> Unit,
    onProductClick: (ProductItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(true) }

    // Derived filter data
    val allTags = remember(products) {
        products.flatMap { it.tags }.distinct().sorted()
    }
    val allBrands = remember(products) {
        products.map { it.brand }.distinct().sorted()
    }

    val filtered = remember(products, searchQuery, selectedTag, selectedBrand) {
        products.filter { p ->
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.brand.lowercase().contains(q) ||
                p.tags.any { it.lowercase().contains(q) }
            val matchTag = selectedTag == null || p.tags.contains(selectedTag)
            val matchBrand = selectedBrand == null || p.brand == selectedBrand
            matchSearch && matchTag && matchBrand
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Search + view toggle bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tags, brands, or products", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4F46E5),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )

            // Grid / Table toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF1F5F9)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isGridView) Color.White else Color.Transparent,
                        shadowElevation = if (isGridView) 1.dp else 0.dp,
                        modifier = Modifier.clickable { isGridView = true }
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Grid",
                            modifier = Modifier.padding(6.dp).size(16.dp),
                            tint = if (isGridView) Color(0xFF4F46E5) else Color(0xFF94A3B8)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (!isGridView) Color.White else Color.Transparent,
                        shadowElevation = if (!isGridView) 1.dp else 0.dp,
                        modifier = Modifier.clickable { isGridView = false }
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Table",
                            modifier = Modifier.padding(6.dp).size(16.dp),
                            tint = if (!isGridView) Color(0xFF4F46E5) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // ---- Tag + Brand filter chips ----
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTag == null && selectedBrand == null,
                    onClick = { selectedTag = null; selectedBrand = null },
                    label = { Text("All (${products.size})", fontSize = 12.sp) },
                    leadingIcon = if (selectedTag == null && selectedBrand == null) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
            items(allTags) { tag ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = {
                        selectedTag = if (selectedTag == tag) null else tag
                        selectedBrand = null
                    },
                    label = { Text(tag, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEEF2FF),
                        selectedLabelColor = Color(0xFF4F46E5)
                    )
                )
            }
            items(allBrands) { brand ->
                FilterChip(
                    selected = selectedBrand == brand,
                    onClick = {
                        selectedBrand = if (selectedBrand == brand) null else brand
                        selectedTag = null
                    },
                    label = { Text(brand, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFF7ED),
                        selectedLabelColor = Color(0xFFD97706)
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Product count info ----
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${filtered.size} product${if (filtered.size != 1) "s" else ""}",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            if (compareIds.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEEF2FF)
                ) {
                    Text(
                        "${compareIds.size} in compare",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---- Product Grid or List ----
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
                    Spacer(Modifier.height(8.dp))
                    Text("No products found", color = Color(0xFF94A3B8), fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onAddProductClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Product")
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { product ->
                    val productSpecs = specifications.filter { it.productId == product.id }
                        .sortedBy { it.effectiveOrder }
                    ProductGridCard(
                        product = product,
                        specs = productSpecs,
                        inCompare = compareIds.contains(product.id),
                        onToggleCompare = { onToggleCompare(product.id) },
                        onClick = { onProductClick(product) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { product ->
                    val productSpecs = specifications.filter { it.productId == product.id }
                        .sortedBy { it.effectiveOrder }
                    ProductTableRow(
                        product = product,
                        specs = productSpecs,
                        inCompare = compareIds.contains(product.id),
                        onToggleCompare = { onToggleCompare(product.id) },
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductGridCard(
    product: ProductItem,
    specs: List<SpecificationItem>,
    inCompare: Boolean,
    onToggleCompare: () -> Unit,
    onClick: () -> Unit
) {
    val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // Brand badge + compare button header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        product.brand.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                OutlinedButton(
                    onClick = onToggleCompare,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (inCompare) Color(0xFFEEF2FF) else Color.Transparent,
                        contentColor = Color(0xFF4F46E5)
                    )
                ) {
                    Icon(
                        if (inCompare) Icons.Default.CheckCircle else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (inCompare) "Added" else "Compare", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Tags row
            if (product.tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(product.tags.take(3)) { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                tag,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Product name
            Text(
                product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Product image
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(36.dp))
                }
            }

            // Top specs preview
            if (specs.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = Color(0xFFF1F5F9))
                Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                    specs.take(3).forEach { spec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                spec.specName,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.width(90.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    append(spec.details)
                                    if (!spec.unit.isNullOrBlank()) append(" ${spec.unit}")
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProductTableRow(
    product: ProductItem,
    specs: List<SpecificationItem>,
    inCompare: Boolean,
    onToggleCompare: () -> Unit,
    onClick: () -> Unit
) {
    val imageUrl = product.pictures.firstOrNull()?.let { resolveSharedImageModel(it.url, it.name) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
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
                        Icons.Default.Star, contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E293B)) {
                        Text(
                            product.brand.uppercase(),
                            fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    product.tags.take(2).forEach { tag ->
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
                            Text(tag, fontSize = 10.sp, color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (product.overview.isNotBlank()) {
                    Text(
                        product.overview.take(80),
                        fontSize = 12.sp, color = Color(0xFF64748B),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (specs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        specs.take(2).joinToString("  ·  ") { s ->
                            "${s.specName}: ${s.details}${if (!s.unit.isNullOrBlank()) " ${s.unit}" else ""}"
                        },
                        fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Compare button
            OutlinedButton(
                onClick = onToggleCompare,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (inCompare) Color(0xFFEEF2FF) else Color.Transparent,
                    contentColor = Color(0xFF4F46E5)
                )
            ) {
                Icon(
                    if (inCompare) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = null, modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(if (inCompare) "Added" else "+ Compare", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
