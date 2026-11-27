package com.example.data

import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.shared.getPlatformInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath

class CrossPlatformProductRepository(
    private val databaseFilePath: String = "${getPlatformInfo().storageDirectory}/products_database.json"
) {
    private val fileSystem: FileSystem = FileSystem.SYSTEM
    private val mutex = Mutex()

    private val _products = MutableStateFlow<List<ProductItem>>(emptyList())
    val products: StateFlow<List<ProductItem>> = _products.asStateFlow()

    private val _specifications = MutableStateFlow<List<SpecificationItem>>(emptyList())
    val specifications: StateFlow<List<SpecificationItem>> = _specifications.asStateFlow()

    private val _reviews = MutableStateFlow<List<ReviewItem>>(emptyList())
    val reviews: StateFlow<List<ReviewItem>> = _reviews.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        runCatching {
            val path = databaseFilePath.toPath()
            if (fileSystem.exists(path)) {
                val result = CrossPlatformBackupManager.importFromJsonFile(databaseFilePath)
                result.getOrNull()?.let { payload ->
                    _products.value = payload.products
                    _specifications.value = payload.specifications
                    _reviews.value = payload.reviews
                }
            }
        }
    }

    suspend fun saveToDisk(): Result<Unit> = mutex.withLock {
        CrossPlatformBackupManager.exportToJsonFile(
            destFilePath = databaseFilePath,
            products = _products.value,
            specifications = _specifications.value,
            reviews = _reviews.value
        )
    }

    suspend fun insertProduct(product: ProductItem) = mutex.withLock {
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == product.id }
        if (index != -1) {
            current[index] = product
        } else {
            current.add(product)
        }
        _products.value = current
        saveToDiskInternal()
    }

    suspend fun deleteProduct(productId: String) = mutex.withLock {
        _products.value = _products.value.filter { it.id != productId }
        _specifications.value = _specifications.value.filter { it.productId != productId }
        _reviews.value = _reviews.value.filter { !it.productIds.contains(productId) }
        saveToDiskInternal()
    }

    suspend fun insertSpecification(specification: SpecificationItem) = mutex.withLock {
        val current = _specifications.value.toMutableList()
        val index = current.indexOfFirst { it.id == specification.id }
        if (index != -1) {
            current[index] = specification
        } else {
            current.add(specification)
        }
        _specifications.value = current
        saveToDiskInternal()
    }

    suspend fun deleteSpecification(specId: String) = mutex.withLock {
        _specifications.value = _specifications.value.filter { it.id != specId }
        saveToDiskInternal()
    }

    suspend fun insertReview(review: ReviewItem) = mutex.withLock {
        val current = _reviews.value.toMutableList()
        val index = current.indexOfFirst { it.id == review.id }
        if (index != -1) {
            current[index] = review
        } else {
            current.add(0, review)
        }
        _reviews.value = current
        saveToDiskInternal()
    }

    suspend fun deleteReview(reviewId: String) = mutex.withLock {
        _reviews.value = _reviews.value.filter { it.id != reviewId }
        saveToDiskInternal()
    }

    suspend fun importPayload(payload: BackupPayload) = mutex.withLock {
        _products.value = payload.products
        _specifications.value = payload.specifications
        _reviews.value = payload.reviews
        saveToDiskInternal()
    }

    private fun saveToDiskInternal() {
        CrossPlatformBackupManager.exportToJsonFile(
            destFilePath = databaseFilePath,
            products = _products.value,
            specifications = _specifications.value,
            reviews = _reviews.value
        )
    }
}
