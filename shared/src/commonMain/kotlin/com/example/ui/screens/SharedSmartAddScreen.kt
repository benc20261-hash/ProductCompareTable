package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.SpecificationItem
import com.example.model.resolveSharedImageModel
import com.example.model.sharedJson
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

@Composable
fun SharedSmartAddScreen(
    onProductAdded: (ProductItem, List<SpecificationItem>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeTab by remember { mutableStateOf(0) } // 0 = Manual Form, 1 = Paste JSON / Spec Text

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // Header + mode tabs
        Surface(color = Color.White, tonalElevation = 1.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Add Product", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Fill in details manually or paste raw JSON / key-value specs to auto-populate",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
                Spacer(Modifier.height(10.dp))
                TabRow(
                    selectedTabIndex = modeTab,
                    containerColor = Color(0xFFF1F5F9),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(selected = modeTab == 0, onClick = { modeTab = 0 }, text = { Text("Manual Form", fontWeight = FontWeight.SemiBold) })
                    Tab(selected = modeTab == 1, onClick = { modeTab = 1 }, text = { Text("Smart Paste (JSON / Specs)", fontWeight = FontWeight.SemiBold) })
                }
            }
        }

        if (modeTab == 0) {
            ManualAddForm(onProductAdded = onProductAdded, onCancel = onCancel)
        } else {
            SmartPasteForm(onProductAdded = onProductAdded, onCancel = onCancel)
        }
    }
}

@Composable
private fun ManualAddForm(
    onProductAdded: (ProductItem, List<SpecificationItem>) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    val specsList = remember { mutableStateListOf<Triple<String, String, String>>() } // name, details, unit
    var newSpecKey by remember { mutableStateOf("") }
    var newSpecVal by remember { mutableStateOf("") }
    var newSpecUnit by remember { mutableStateOf("") }

    val filePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        mode = PickerMode.Single
    ) { file ->
        file?.path?.let { imageUrl = it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Product Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = brand, onValueChange = { brand = it },
                        label = { Text("Brand *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = tagsInput, onValueChange = { tagsInput = it },
                        label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = overview, onValueChange = { overview = it },
                        label = { Text("Overview / Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = imageUrl, onValueChange = { imageUrl = it },
                            label = { Text("Image URL or Local Path") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedButton(onClick = { filePicker.launch() }) { Text("Browse…") }
                    }
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = resolveSharedImageModel(imageUrl),
                            contentDescription = "Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Specifications (${specsList.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    specsList.forEachIndexed { idx, (k, v, u) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$k: $v${if (u.isNotBlank()) " $u" else ""}",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { specsList.removeAt(idx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newSpecKey, onValueChange = { newSpecKey = it },
                            label = { Text("Spec Name") }, modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            value = newSpecVal, onValueChange = { newSpecVal = it },
                            label = { Text("Details") }, modifier = Modifier.weight(1.2f), singleLine = true
                        )
                        OutlinedTextField(
                            value = newSpecUnit, onValueChange = { newSpecUnit = it },
                            label = { Text("Unit") }, modifier = Modifier.weight(0.6f), singleLine = true
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (newSpecKey.isNotBlank() && newSpecVal.isNotBlank()) {
                                specsList.add(Triple(newSpecKey.trim(), newSpecVal.trim(), newSpecUnit.trim()))
                                newSpecKey = ""; newSpecVal = ""; newSpecUnit = ""
                            }
                        },
                        enabled = newSpecKey.isNotBlank() && newSpecVal.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Spec")
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val prodId = "prod_${Random.nextLong(100000000, 999999999)}"
                        val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val pics = if (imageUrl.isNotBlank()) {
                            listOf(PictureItem(id = "pic_1", name = "$name.jpg", url = imageUrl.trim()))
                        } else emptyList()

                        val product = ProductItem(
                            id = prodId,
                            name = name.trim(),
                            brand = brand.trim(),
                            tags = tags,
                            overview = overview.trim(),
                            pictures = pics
                        )
                        val specs = specsList.mapIndexed { idx, (k, v, u) ->
                            SpecificationItem(
                                id = "spec_${prodId}_$idx",
                                productId = prodId,
                                productTitle = name.trim(),
                                specName = k,
                                details = v,
                                unit = u.ifBlank { null },
                                orderIndex = idx
                            )
                        }
                        onProductAdded(product, specs)
                    },
                    enabled = name.isNotBlank() && brand.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Product")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SmartPasteForm(
    onProductAdded: (ProductItem, List<SpecificationItem>) -> Unit,
    onCancel: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var parsedPreview by remember { mutableStateOf<Pair<ProductItem, List<SpecificationItem>>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste JSON or Key: Value Spec Lines", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Supports a JSON object (with name, brand, tags, overview, specifications/specs) or plain text lines like:\nName: MacBook Pro 14\nBrand: Apple\nWeight: 1.55 kg\nRAM: 16 GB",
                        fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it; parseError = null },
                        label = { Text("Paste product data here…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 7
                    )
                    if (parseError != null) {
                        Text(parseError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            parseSmartText(rawText)
                                .onSuccess { parsedPreview = it; parseError = null }
                                .onFailure { parseError = it.message ?: "Could not parse input" }
                        },
                        enabled = rawText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Parse & Preview")
                    }
                }
            }
        }

        if (parsedPreview != null) {
            val (product, specs) = parsedPreview!!
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ready to Import", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontSize = 13.sp)
                        Text("${product.name} — ${product.brand}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (product.tags.isNotEmpty()) {
                            Text("Tags: ${product.tags.joinToString(", ")}", fontSize = 12.sp, color = Color(0xFF475569))
                        }
                        Text("${specs.size} specification${if (specs.size != 1) "s" else ""} detected:", fontSize = 12.sp, color = Color(0xFF475569))
                        specs.forEach { s ->
                            Text("  • ${s.specName}: ${s.details}", fontSize = 12.sp, color = Color(0xFF334155))
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onProductAdded(product, specs) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Text("Confirm & Add Product")
                            }
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

private fun parseSmartText(raw: String): Result<Pair<ProductItem, List<SpecificationItem>>> = runCatching {
    val trimmed = raw.trim()
    val prodId = "prod_${Random.nextLong(100000000, 999999999)}"

    if (trimmed.startsWith("{")) {
        val obj = sharedJson.decodeFromString(JsonObject.serializer(), trimmed)
        val name = obj["name"]?.jsonPrimitive?.content ?: error("JSON missing 'name'")
        val brand = obj["brand"]?.jsonPrimitive?.content ?: "Generic"
        val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
        val tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

        val specsArr = obj["specifications"]?.jsonArray ?: obj["specs"]?.jsonArray
        val specs = specsArr?.mapIndexed { idx, elem ->
            val so = elem.jsonObject
            SpecificationItem(
                id = "spec_${prodId}_$idx",
                productId = prodId,
                productTitle = name,
                specName = so["specName"]?.jsonPrimitive?.content ?: so["name"]?.jsonPrimitive?.content ?: "Spec $idx",
                details = so["details"]?.jsonPrimitive?.content ?: so["value"]?.jsonPrimitive?.content ?: "",
                unit = so["unit"]?.jsonPrimitive?.content,
                orderIndex = idx
            )
        } ?: emptyList()

        ProductItem(id = prodId, name = name, brand = brand, tags = tags, overview = overview) to specs
    } else {
        // Parse key: value lines
        var name = ""
        var brand = "Generic"
        val tags = mutableListOf<String>()
        var overview = ""
        val specs = mutableListOf<SpecificationItem>()

        trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val sep = line.indexOf(':')
            if (sep > 0) {
                val key = line.substring(0, sep).trim()
                val value = line.substring(sep + 1).trim()
                when (key.lowercase()) {
                    "name", "product", "title" -> name = value
                    "brand", "manufacturer" -> brand = value
                    "tags", "tag", "category" -> tags.addAll(value.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    "overview", "description" -> overview = value
                    else -> specs.add(
                        SpecificationItem(
                            id = "spec_${prodId}_${specs.size}",
                            productId = prodId,
                            productTitle = name.ifBlank { "New Product" },
                            specName = key,
                            details = value,
                            orderIndex = specs.size
                        )
                    )
                }
            }
        }
        if (name.isBlank()) error("Could not find 'Name: ...' line or valid JSON")
        ProductItem(id = prodId, name = name, brand = brand, tags = tags, overview = overview) to
            specs.map { it.copy(productTitle = name) }
    }
}
