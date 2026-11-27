package com.example.ui

import com.example.data.BackupPayload
import com.example.data.CrossPlatformBackupManager
import com.example.data.CrossPlatformProductRepository
import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SharedViewModel(
    private val repository: CrossPlatformProductRepository = CrossPlatformProductRepository()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Exposed state ----
    val products: StateFlow<List<ProductItem>> = repository.products
    val specifications: StateFlow<List<SpecificationItem>> = repository.specifications
    val reviews: StateFlow<List<ReviewItem>> = repository.reviews

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Compare tray — set of product IDs to compare
    private val _compareIds = MutableStateFlow<List<String>>(emptyList())
    val compareIds: StateFlow<List<String>> = _compareIds.asStateFlow()

    init {
        // Seed from bundled JSON if the DB is empty on first launch
        scope.launch {
            if (repository.products.value.isEmpty()) {
                seedFromBundledJson()
            }
        }
    }

    // ----------------------- Helpers -----------------------

    fun specsForProduct(productId: String): List<SpecificationItem> =
        specifications.value.filter { it.productId == productId }
            .sortedBy { it.effectiveOrder }

    fun reviewsForProduct(productId: String): List<ReviewItem> =
        reviews.value.filter { it.productIds.contains(productId) }

    // ----------------------- Compare tray -----------------------

    fun toggleCompare(productId: String) {
        val current = _compareIds.value.toMutableList()
        if (current.contains(productId)) {
            current.remove(productId)
        } else if (current.size < 5) {
            current.add(productId)
        }
        _compareIds.value = current
    }

    fun setCompareIds(ids: List<String>) {
        _compareIds.value = ids.distinct().take(5)
    }

    fun clearCompare() { _compareIds.value = emptyList() }

    // ----------------------- CRUD -----------------------

    fun addProduct(product: ProductItem, specs: List<SpecificationItem> = emptyList()) {
        scope.launch {
            repository.insertProduct(product)
            specs.forEach { repository.insertSpecification(it) }
            showSnackbar("'${product.name}' added successfully")
        }
    }

    /**
     * Saves the full product workspace: upserts the product, replaces all its specs
     * (deletes removed ones and upserts the current list with updated orderIndex).
     */
    fun saveProductWorkspace(product: ProductItem, newSpecs: List<SpecificationItem>, isNew: Boolean) {
        scope.launch {
            repository.insertProduct(product)
            if (!isNew) {
                val existingSpecIds = repository.specifications.value
                    .filter { it.productId == product.id }.map { it.id }
                existingSpecIds.forEach { repository.deleteSpecification(it) }
            }
            newSpecs.forEach { repository.insertSpecification(it) }
            showSnackbar(if (isNew) "'${product.name}' published" else "'${product.name}' saved")
        }
    }

    fun updateProduct(product: ProductItem) {
        scope.launch { repository.insertProduct(product) }
    }

    fun deleteProduct(productId: String) {
        scope.launch {
            val name = repository.products.value.find { it.id == productId }?.name ?: ""
            repository.deleteProduct(productId)
            showSnackbar("'$name' deleted")
        }
    }

    fun addSpecification(spec: SpecificationItem) {
        scope.launch { repository.insertSpecification(spec) }
    }

    fun updateSpecification(spec: SpecificationItem) {
        scope.launch { repository.insertSpecification(spec) }
    }

    fun deleteSpecification(specId: String) {
        scope.launch { repository.deleteSpecification(specId) }
    }

    fun addReview(review: ReviewItem) {
        scope.launch { repository.insertReview(review) }
    }

    fun deleteReview(reviewId: String) {
        scope.launch { repository.deleteReview(reviewId) }
    }

    // ----------------------- Smart Create / Inline Matrix -----------------------

    fun createAndCompare(
        name: String,
        brand: String,
        orderedSpecNames: List<String>,
        newProductId: String
    ) {
        scope.launch {
            val allSpecs = repository.specifications.value
            val newProduct = ProductItem(
                id = newProductId,
                name = name.trim(),
                brand = brand.trim().ifBlank { "Generic" },
                tags = listOf("Smart Create"),
                overview = "Created with ${orderedSpecNames.size} specifications extracted from comparison matrix.",
                pictures = emptyList()
            )
            repository.insertProduct(newProduct)

            orderedSpecNames.forEachIndexed { idx, specName ->
                val existingRef = allSpecs.find { s ->
                    s.specName.trim().equals(specName.trim(), ignoreCase = true) &&
                        (!s.unit.isNullOrBlank() || s.isNumeric != null)
                }
                val blankSpec = SpecificationItem(
                    id = "spec_${newProductId}_${idx}_${Random.nextInt(1000, 9999)}",
                    productId = newProductId,
                    productTitle = newProduct.name,
                    specName = specName,
                    details = "",
                    unit = existingRef?.unit?.trim()?.ifBlank { null },
                    orderIndex = idx + 1,
                    isNumeric = existingRef?.isNumeric
                )
                repository.insertSpecification(blankSpec)
            }

            val updatedCompare = (_compareIds.value + newProductId).distinct().take(5)
            _compareIds.value = updatedCompare
            showSnackbar("Created '${newProduct.name}' with ${orderedSpecNames.size} specifications")
        }
    }

    fun addSpecificationInline(
        productId: String,
        productTitle: String,
        specName: String,
        details: String,
        unit: String?
    ) {
        scope.launch {
            val existing = repository.specifications.value.find {
                it.productId == productId && it.specName.equals(specName.trim(), ignoreCase = true)
            }
            if (existing != null) {
                repository.insertSpecification(
                    existing.copy(
                        details = details,
                        unit = unit ?: existing.unit
                    )
                )
            } else {
                val maxOrder = repository.specifications.value
                    .filter { it.productId == productId }
                    .maxOfOrNull { it.effectiveOrder } ?: 0
                repository.insertSpecification(
                    SpecificationItem(
                        id = "spec_${Random.nextLong(100000000, 999999999)}",
                        productId = productId,
                        productTitle = productTitle,
                        specName = specName.trim(),
                        details = details,
                        unit = unit?.ifBlank { null },
                        orderIndex = maxOrder + 1
                    )
                )
            }
            showSnackbar("Added '$specName' to $productTitle")
        }
    }

    fun updateInlineSpec(
        productId: String,
        productTitle: String,
        specName: String,
        details: String,
        pictures: List<PictureItem>,
        unit: String?,
        isNumeric: Boolean?
    ) {
        scope.launch {
            val existing = repository.specifications.value.find {
                (it.productId == productId || it.productTitle == productTitle) &&
                    it.specName.equals(specName.trim(), ignoreCase = true)
            }
            if (existing != null) {
                repository.insertSpecification(
                    existing.copy(
                        specName = specName.trim(),
                        details = details,
                        pictures = pictures,
                        unit = unit?.ifBlank { null },
                        isNumeric = isNumeric
                    )
                )
            } else {
                val maxOrder = repository.specifications.value
                    .filter { it.productId == productId }
                    .maxOfOrNull { it.effectiveOrder } ?: 0
                repository.insertSpecification(
                    SpecificationItem(
                        id = "spec_${Random.nextLong(100000000, 999999999)}",
                        productId = productId,
                        productTitle = productTitle,
                        specName = specName.trim(),
                        details = details,
                        unit = unit?.ifBlank { null },
                        orderIndex = maxOrder + 1,
                        pictures = pictures,
                        isNumeric = isNumeric
                    )
                )
            }
            showSnackbar("Saved '$specName'")
        }
    }

    fun deleteSpecInline(productId: String, specName: String) {
        scope.launch {
            val matches = repository.specifications.value.filter {
                it.productId == productId && it.specName.equals(specName.trim(), ignoreCase = true)
            }
            matches.forEach { repository.deleteSpecification(it.id) }
            showSnackbar("Removed '$specName'")
        }
    }

    fun updateProductBrand(productId: String, brand: String) {
        scope.launch {
            val prod = repository.products.value.find { it.id == productId } ?: return@launch
            repository.insertProduct(prod.copy(brand = brand.trim()))
            showSnackbar("Updated brand for '${prod.name}'")
        }
    }

    fun updateProductTags(productId: String, tags: List<String>) {
        scope.launch {
            val prod = repository.products.value.find { it.id == productId } ?: return@launch
            repository.insertProduct(prod.copy(tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()))
            showSnackbar("Updated tags for '${prod.name}'")
        }
    }

    fun updateSpecsOrder(productId: String, orderedSpecs: List<SpecificationItem>) {
        scope.launch {
            orderedSpecs.forEachIndexed { index, spec ->
                repository.insertSpecification(spec.copy(orderIndex = index + 1, order = index + 1))
            }
            showSnackbar("Updated specification order")
        }
    }

    // ----------------------- Backup -----------------------

    fun importFromBytes(bytes: ByteArray, fileName: String) {
        scope.launch {
            _isLoading.value = true
            CrossPlatformBackupManager.importFromBytes(bytes, fileName)
                .onSuccess { payload ->
                    repository.importPayload(payload)
                    showSnackbar(
                        "Restored ${payload.products.size} products, " +
                        "${payload.specifications.size} specs, " +
                        "${payload.reviews.size} reviews"
                    )
                }
                .onFailure { showSnackbar("Import failed: ${it.message}") }
            _isLoading.value = false
        }
    }

    fun exportToJsonString(): String =
        CrossPlatformBackupManager.exportToJsonString(
            products = products.value,
            specifications = specifications.value,
            reviews = reviews.value
        )

    fun exportToZipBytes(): ByteArray? =
        CrossPlatformBackupManager.exportToFullZipBytes(
            products = products.value,
            specifications = specifications.value,
            reviews = reviews.value
        ) ?: createZipWithDatabaseJson(exportToJsonString())

    fun clearAllData() {
        scope.launch {
            _isLoading.value = true
            repository.importPayload(BackupPayload())
            _compareIds.value = emptyList()
            showSnackbar("Cleared all catalog data")
            _isLoading.value = false
        }
    }

    fun freeUpStorage() {
        scope.launch {
            val removed = CrossPlatformBackupManager.freeUpOrphanImages(
                products = products.value,
                specifications = specifications.value,
                reviews = reviews.value
            )
            showSnackbar(
                if (removed > 0) "Removed $removed unused image file(s)"
                else "Storage is already clean — no orphan images found"
            )
        }
    }

    fun resetToSeedData() {
        scope.launch {
            _isLoading.value = true
            val json = loadBundledSeedJson()
            if (json != null) {
                CrossPlatformBackupManager.importFromJsonString(json)
                    .onSuccess { payload ->
                        repository.importPayload(payload)
                        showSnackbar("Restored ${payload.products.size} seed products")
                    }
                    .onFailure { showSnackbar("Failed to load seed data: ${it.message}") }
            } else {
                showSnackbar("No bundled seed_database.json found")
            }
            _isLoading.value = false
        }
    }

    // ----------------------- Snackbar -----------------------

    fun showSnackbar(msg: String) { _snackbarMessage.value = msg }
    fun clearSnackbar() { _snackbarMessage.value = null }

    // ----------------------- Seed -----------------------

    /** Loads the bundled seed_database.json from resources (desktop JVM classpath). */
    private suspend fun seedFromBundledJson() {
        runCatching {
            val json = loadBundledSeedJson() ?: return
            CrossPlatformBackupManager.importFromJsonString(json)
                .getOrNull()
                ?.takeIf { it.products.isNotEmpty() }
                ?.let { payload ->
                    repository.importPayload(payload)
                }
        }
    }
}

/** expect/actual: each platform provides access to the bundled seed JSON. */
expect fun loadBundledSeedJson(): String?

/** expect/actual: creates a .zip archive containing database.json. */
expect fun createZipWithDatabaseJson(jsonString: String): ByteArray?
