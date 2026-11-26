package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProductItem
import com.example.ui.components.SharedDesktopNavbar
import com.example.ui.components.SharedNavTab
import com.example.ui.screens.SharedCatalogScreen
import com.example.ui.screens.SharedCompareScreen
import com.example.ui.screens.SharedProductWorkspaceScreen
import com.example.ui.screens.SharedReviewsScreen
import com.example.ui.screens.SharedSmartAddScreen
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch

@Composable
fun ProductCompareSharedApp() {
    val viewModel = remember { SharedViewModel() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val products by viewModel.products.collectAsState()
    val specifications by viewModel.specifications.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val compareIds by viewModel.compareIds.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var activeTab by remember { mutableStateOf(SharedNavTab.CATALOG) }
    var selectedProductForWorkspace by remember { mutableStateOf<ProductItem?>(null) }

    // Forward ViewModel snackbar messages to the host
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // File picker for backup import
    val filePicker = rememberFilePickerLauncher(
        type = PickerType.File(listOf("json", "zip")),
        mode = PickerMode.Single
    ) { file ->
        file?.let { platformFile ->
            scope.launch {
                runCatching {
                    val bytes = platformFile.readBytes()
                    viewModel.importFromBytes(bytes, platformFile.name)
                }
            }
        }
    }

    // File saver for JSON / ZIP export
    val fileSaver = rememberFileSaverLauncher { file ->
        if (file != null) {
            viewModel.showSnackbar("Saved backup to ${file.name}")
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F46E5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEEF2FF),
            secondary = Color(0xFF64748B),
            surface = Color(0xFFF8FAFC),
            background = Color(0xFFF1F5F9)
        )
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                SharedDesktopNavbar(
                    activeTab = activeTab,
                    onTabSelected = {
                        selectedProductForWorkspace = null
                        activeTab = it
                    },
                    productCount = products.size,
                    compareCount = compareIds.size,
                    reviewCount = reviews.size,
                    onAddProductClick = {
                        selectedProductForWorkspace = null
                        activeTab = SharedNavTab.SMART_ADD
                    }
                )
            },
            containerColor = Color(0xFFF1F5F9)
        ) { paddingValues ->

            // Product workspace detail panel (when a product is clicked)
            if (selectedProductForWorkspace != null) {
                val product = selectedProductForWorkspace!!
                val liveProduct = products.find { it.id == product.id } ?: product
                SharedProductWorkspaceScreen(
                    product = liveProduct,
                    specs = specifications.filter { it.productId == liveProduct.id }
                        .sortedBy { it.effectiveOrder },
                    reviews = reviews.filter { it.productIds.contains(liveProduct.id) },
                    inCompare = compareIds.contains(liveProduct.id),
                    onToggleCompare = { viewModel.toggleCompare(liveProduct.id) },
                    onSaveProduct = { viewModel.updateProduct(it) },
                    onSaveSpec = { viewModel.updateSpecification(it) },
                    onDeleteSpec = { viewModel.deleteSpecification(it) },
                    onDeleteProduct = {
                        viewModel.deleteProduct(liveProduct.id)
                        selectedProductForWorkspace = null
                    },
                    onClose = { selectedProductForWorkspace = null },
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
                return@Scaffold
            }

            when (activeTab) {
                SharedNavTab.CATALOG -> {
                    SharedCatalogScreen(
                        products = products,
                        specifications = specifications,
                        compareIds = compareIds,
                        onToggleCompare = { viewModel.toggleCompare(it) },
                        onAddProductClick = { activeTab = SharedNavTab.SMART_ADD },
                        onProductClick = { selectedProductForWorkspace = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                SharedNavTab.COMPARE -> {
                    SharedCompareScreen(
                        products = products,
                        specifications = specifications,
                        reviews = reviews,
                        compareIds = compareIds,
                        onToggleProduct = { viewModel.toggleCompare(it) },
                        onClearAll = { viewModel.clearCompare() },
                        onEditProduct = { selectedProductForWorkspace = it },
                        onAddProductClick = { activeTab = SharedNavTab.CATALOG },
                        onNavigateToReviews = { activeTab = SharedNavTab.REVIEWS },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                SharedNavTab.SMART_ADD -> {
                    SharedSmartAddScreen(
                        onProductAdded = { product, specs ->
                            viewModel.addProduct(product, specs)
                            activeTab = SharedNavTab.CATALOG
                        },
                        onCancel = { activeTab = SharedNavTab.CATALOG },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                SharedNavTab.REVIEWS -> {
                    SharedReviewsScreen(
                        reviews = reviews,
                        products = products,
                        onAddReview = { viewModel.addReview(it) },
                        onDeleteReview = { viewModel.deleteReview(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                SharedNavTab.DATA_BACKUP -> {
                    SharedBackupPanel(
                        productCount = products.size,
                        specCount = specifications.size,
                        reviewCount = reviews.size,
                        onExportJson = {
                            val bytes = viewModel.exportToJsonString().encodeToByteArray()
                            fileSaver.launch(
                                bytes = bytes,
                                baseName = "productcompare_backup",
                                extension = "json"
                            )
                        },
                        onExportZip = {
                            val zipBytes = viewModel.exportToZipBytes()
                            if (zipBytes != null) {
                                fileSaver.launch(
                                    bytes = zipBytes,
                                    baseName = "productcompare_backup",
                                    extension = "zip"
                                )
                            } else {
                                viewModel.showSnackbar("ZIP export not available on this platform; use JSON export")
                            }
                        },
                        onImport = { filePicker.launch() },
                        onResetToSeed = { viewModel.resetToSeedData() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Backup Panel (Phase 7)
// ---------------------------------------------------------------------------
@Composable
fun SharedBackupPanel(
    productCount: Int,
    specCount: Int,
    reviewCount: Int,
    onExportJson: () -> Unit,
    onExportZip: () -> Unit,
    onImport: () -> Unit,
    onResetToSeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSeedConfirm by remember { mutableStateOf(false) }

    if (showSeedConfirm) {
        AlertDialog(
            onDismissRequest = { showSeedConfirm = false },
            title = { Text("Load seed database?") },
            text = { Text("This will replace the current database with the bundled sample catalog.") },
            confirmButton = {
                TextButton(onClick = { showSeedConfirm = false; onResetToSeed() }) {
                    Text("Load Seed Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeedConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Data & Backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current Database", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                HorizontalDivider(color = Color(0xFFF1F5F9))
                StatRow("Products", productCount.toString())
                StatRow("Specifications", specCount.toString())
                StatRow("Reviews", reviewCount.toString())
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export & Import (Okio + FileKit)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Supports full .ZIP archive backups from Android and cross-platform .json files. " +
                    "Interoperable across macOS, Windows, Android, and iOS.",
                    fontSize = 13.sp, color = Color(0xFF64748B), lineHeight = 18.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onExportZip,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Export ZIP")
                    }
                    OutlinedButton(onClick = onExportJson) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Export JSON")
                    }
                    OutlinedButton(onClick = onImport) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Import (.ZIP / .JSON)")
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bundled Seed Catalog", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Reload the bundled sample database", fontSize = 12.sp, color = Color(0xFF64748B))
                }
                OutlinedButton(onClick = { showSeedConfirm = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Load Seed")
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
    }
}
