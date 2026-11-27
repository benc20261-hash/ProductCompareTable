package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProductItem
import com.example.ui.components.SharedDesktopNavbar
import com.example.ui.components.SharedNavTab
import com.example.ui.components.SharedProductDetailPane
import com.example.ui.screens.SharedCatalogScreen
import com.example.ui.screens.SharedCompareScreen
import com.example.ui.screens.SharedDataBackupScreen
import com.example.ui.screens.SharedProductWorkspaceScreen
import com.example.ui.screens.SharedReviewsScreen
import com.example.ui.screens.SharedSmartCreateScreen
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
    val isLoading by viewModel.isLoading.collectAsState()

    var activeTab by remember { mutableStateOf(SharedNavTab.CATALOG) }
    var selectedProductForWorkspace by remember { mutableStateOf<ProductItem?>(null) }
    var viewingProductDetail by remember { mutableStateOf<ProductItem?>(null) }
    var isCreatingNewProduct by remember { mutableStateOf(false) }
    var reviewPreselectedProductId by remember { mutableStateOf<String?>(null) }
    var fontScalePercent by rememberSaveable { mutableIntStateOf(100) }
    var lastSavedBackupName by remember { mutableStateOf<String?>(null) }

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
            lastSavedBackupName = file.name
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
                        viewingProductDetail = null
                        reviewPreselectedProductId = null
                        activeTab = it
                    },
                    productCount = products.size,
                    compareCount = compareIds.size,
                    reviewCount = reviews.size,
                    onAddProductClick = {
                        selectedProductForWorkspace = null
                        viewingProductDetail = null
                        isCreatingNewProduct = true
                    },
                    fontScalePercent = fontScalePercent,
                    onFontScaleChange = { fontScalePercent = it }
                )
            },
            containerColor = Color(0xFFF1F5F9)
        ) { paddingValues ->
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * (fontScalePercent / 100f)
                )
            ) {

            // Product workspace: either creating a new product OR editing an existing one
            if (isCreatingNewProduct || selectedProductForWorkspace != null) {
                val existingProduct = selectedProductForWorkspace
                val liveProduct = if (existingProduct != null) {
                    products.find { it.id == existingProduct.id } ?: existingProduct
                } else null
                val existingSpecs = if (liveProduct != null) {
                    specifications.filter { it.productId == liveProduct.id }.sortedBy { it.effectiveOrder }
                } else emptyList()

                val allTags = products.flatMap { it.tags }.distinct().sorted()
                val allBrands = products.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted()
                val allPictures = (products.flatMap { it.pictures } +
                        specifications.flatMap { it.pictures }).distinctBy { it.name.ifBlank { it.url } }

                SharedProductWorkspaceScreen(
                    initialProduct = liveProduct,
                    allAvailableTags = allTags,
                    allAvailableBrands = allBrands,
                    allAvailablePictures = allPictures,
                    initialSpecifications = existingSpecs,
                    onSaveProduct = { product, specs, isNew ->
                        viewModel.saveProductWorkspace(product, specs, isNew)
                        isCreatingNewProduct = false
                        selectedProductForWorkspace = null
                        activeTab = SharedNavTab.CATALOG
                    },
                    onDeleteProduct = if (liveProduct != null) {{ id ->
                        viewModel.deleteProduct(id)
                        isCreatingNewProduct = false
                        selectedProductForWorkspace = null
                        activeTab = SharedNavTab.CATALOG
                    }} else null,
                    onNavigateBack = {
                        isCreatingNewProduct = false
                        selectedProductForWorkspace = null
                    },
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
            } else if (viewingProductDetail != null) {
                val target = viewingProductDetail!!
                val liveProduct = products.find { it.id == target.id } ?: target
                val prodSpecs = specifications.filter { it.productId == liveProduct.id || it.productTitle == liveProduct.name }
                    .sortedBy { it.effectiveOrder }
                val prodReviews = reviews.filter { liveProduct.id in it.productIds || it.productTitles.any { t -> t.equals(liveProduct.name, ignoreCase = true) } }
                SharedProductDetailPane(
                    product = liveProduct,
                    specifications = prodSpecs,
                    reviews = prodReviews,
                    isCompared = compareIds.contains(liveProduct.id),
                    onToggleCompare = { viewModel.toggleCompare(liveProduct.id) },
                    onEdit = {
                        selectedProductForWorkspace = liveProduct
                        viewingProductDetail = null
                        isCreatingNewProduct = false
                    },
                    onDelete = {
                        viewModel.deleteProduct(liveProduct.id)
                        viewingProductDetail = null
                    },
                    onAddReview = {
                        reviewPreselectedProductId = liveProduct.id
                        viewingProductDetail = null
                        activeTab = SharedNavTab.REVIEWS
                    },
                    onDismiss = { viewingProductDetail = null },
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
            } else {
            when (activeTab) {
                SharedNavTab.CATALOG -> {
                    SharedCatalogScreen(
                        products = products,
                        specifications = specifications,
                        reviews = reviews,
                        compareIds = compareIds,
                        onToggleCompare = { viewModel.toggleCompare(it) },
                        onAddProductClick = {
                            isCreatingNewProduct = true
                            selectedProductForWorkspace = null
                        },
                        onEditProduct = { selectedProductForWorkspace = it; isCreatingNewProduct = false },
                        onClearCompare = { viewModel.clearCompare() },
                        onSmartAddClick = { activeTab = SharedNavTab.SMART_ADD },
                        onDeleteProduct = { viewModel.deleteProduct(it) },
                        onAddReview = { p -> reviewPreselectedProductId = p.id; activeTab = SharedNavTab.REVIEWS },
                        onNavigateToCompare = { activeTab = SharedNavTab.COMPARE },
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
                        onSetComparedIds = { viewModel.setCompareIds(it) },
                        onClearAll = { viewModel.clearCompare() },
                        onEditProduct = { viewingProductDetail = it },
                        onAddProductClick = { activeTab = SharedNavTab.CATALOG },
                        onNavigateToReviews = { activeTab = SharedNavTab.REVIEWS },
                        onOpenAddReview = { p -> reviewPreselectedProductId = p.id; activeTab = SharedNavTab.REVIEWS },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }

                SharedNavTab.SMART_ADD -> {
                    SharedSmartCreateScreen(
                        products = products,
                        specifications = specifications,
                        compareIds = compareIds,
                        onViewProduct = { viewingProductDetail = it },
                        onCreateAndCompare = { name, brand, orderedSpecNames, newProductId ->
                            viewModel.createAndCompare(name, brand, orderedSpecNames, newProductId)
                        },
                        onAddSpecificationInline = { productId, productTitle, specName, details, unit ->
                            viewModel.addSpecificationInline(productId, productTitle, specName, details, unit)
                        },
                        onUpdateInlineSpec = { productId, productTitle, specName, details, pictures, unit, isNumeric ->
                            viewModel.updateInlineSpec(productId, productTitle, specName, details, pictures, unit, isNumeric)
                        },
                        onDeleteSpecInline = { productId, specName ->
                            viewModel.deleteSpecInline(productId, specName)
                        },
                        onUpdateProductBrand = { productId, brand ->
                            viewModel.updateProductBrand(productId, brand)
                        },
                        onUpdateProductTags = { productId, tags ->
                            viewModel.updateProductTags(productId, tags)
                        },
                        onUpdateSpecsOrder = { productId, orderedSpecs ->
                            viewModel.updateSpecsOrder(productId, orderedSpecs)
                        },
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
                        onProductClick = { viewingProductDetail = it },
                        preselectedProductId = reviewPreselectedProductId,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                    // Clear preselection after composition so it doesn't re-trigger
                    LaunchedEffect(Unit) { reviewPreselectedProductId = null }
                }

                SharedNavTab.DATA_BACKUP -> {
                    SharedDataBackupScreen(
                        products = products,
                        specifications = specifications,
                        reviews = reviews,
                        isProcessing = isLoading,
                        lastSavedLocation = lastSavedBackupName,
                        onExportZip = {
                            val zipBytes = viewModel.exportToZipBytes()
                            if (zipBytes != null) {
                                fileSaver.launch(
                                    bytes = zipBytes,
                                    baseName = "productcompare_backup",
                                    extension = "zip"
                                )
                            } else {
                                viewModel.showSnackbar("ZIP export failed; use JSON export")
                            }
                        },
                        onExportJson = {
                            val bytes = viewModel.exportToJsonString().encodeToByteArray()
                            fileSaver.launch(
                                bytes = bytes,
                                baseName = "productcompare_backup",
                                extension = "json"
                            )
                        },
                        onImport = { filePicker.launch() },
                        onResetToSeedData = { viewModel.resetToSeedData() },
                        onClearAllData = { viewModel.clearAllData() },
                        onFreeUpStorage = { viewModel.freeUpStorage() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
            }
            }
        }
    }
}

// ---------------------------------------------------------------------------
