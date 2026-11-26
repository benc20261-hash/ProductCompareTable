package com.example.data

import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.sharedJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path.Companion.toPath

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val products: List<ProductItem> = emptyList(),
    val specifications: List<SpecificationItem> = emptyList(),
    val reviews: List<ReviewItem> = emptyList()
)

object CrossPlatformBackupManager {
    private val fileSystem: FileSystem = FileSystem.SYSTEM

    // ---------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------

    fun exportToJsonFile(
        destFilePath: String,
        products: List<ProductItem>,
        specifications: List<SpecificationItem>,
        reviews: List<ReviewItem>
    ): Result<Unit> = runCatching {
        val jsonString = exportToJsonString(products, specifications, reviews)
        val path = destFilePath.toPath()
        path.parent?.let { parentDir ->
            if (!fileSystem.exists(parentDir)) fileSystem.createDirectories(parentDir)
        }
        fileSystem.write(path) { writeUtf8(jsonString) }
    }

    fun exportToJsonString(
        products: List<ProductItem>,
        specifications: List<SpecificationItem>,
        reviews: List<ReviewItem>
    ): String {
        val payload = BackupPayload(
            version = 1,
            exportedAt = 0L,
            products = products,
            specifications = specifications,
            reviews = reviews
        )
        return sharedJson.encodeToString(BackupPayload.serializer(), payload)
    }

    // ---------------------------------------------------------------------------
    // Import — JSON file on disk
    // ---------------------------------------------------------------------------

    fun importFromJsonFile(sourceFilePath: String): Result<BackupPayload> = runCatching {
        val path = sourceFilePath.toPath()
        val jsonString = fileSystem.read(path) { readUtf8() }
        parseJsonString(jsonString)
    }

    // ---------------------------------------------------------------------------
    // Import — raw JSON string (from file picker bytes or clipboard)
    // Handles:
    //   1. Desktop/iOS BackupPayload format  { version, exportedAt, products, … }
    //   2. Android raw export format          { products, specifications, reviews, users }
    // ---------------------------------------------------------------------------

    fun importFromJsonString(jsonString: String): Result<BackupPayload> = runCatching {
        parseJsonString(jsonString)
    }

    // ---------------------------------------------------------------------------
    // Import — bytes of a file (could be a .json or a .zip containing database.json)
    // ---------------------------------------------------------------------------

    fun importFromBytes(bytes: ByteArray, fileName: String): Result<BackupPayload> = runCatching {
        val nameLower = fileName.lowercase()
        if (nameLower.endsWith(".zip")) {
            // Extract database.json from zip in-memory
            val jsonString = extractDatabaseJsonFromZipBytes(bytes)
                ?: error("ZIP does not contain a recognizable database.json")
            parseJsonString(jsonString)
        } else {
            parseJsonString(bytes.decodeToString())
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Parse either BackupPayload (desktop/iOS) or Android raw export format.
     * Android export: root object has "products" / "specifications" / "reviews" arrays
     * but NO "version" or "exportedAt" fields.
     */
    private fun parseJsonString(jsonString: String): BackupPayload {
        // First, try deserializing directly as BackupPayload (handles both formats
        // because BackupPayload.version defaults to 1 and exportedAt defaults to 0L,
        // and sharedJson has ignoreUnknownKeys = true)
        val direct = runCatching {
            sharedJson.decodeFromString(BackupPayload.serializer(), jsonString)
        }
        if (direct.isSuccess && direct.getOrNull()?.products?.isNotEmpty() == true) {
            return direct.getOrThrow()
        }

        // Fallback: parse as JsonObject and manually reconstruct payload.
        // This handles Android format where root keys are products/specifications/reviews/users
        // and SpecificationItem uses "order" instead of "orderIndex".
        val root = sharedJson.decodeFromString(JsonObject.serializer(), jsonString)

        val productsJson = root["products"]?.jsonArray
            ?: root["product_items"]?.jsonArray
        val specificationsJson = root["specifications"]?.jsonArray
            ?: root["specs"]?.jsonArray
        val reviewsJson = root["reviews"]?.jsonArray

        val products = productsJson?.let {
            sharedJson.decodeFromString<List<ProductItem>>(it.toString())
        } ?: emptyList()

        // For specifications, Android may use "order" instead of "orderIndex".
        // We remap the JSON before parsing to normalise field names.
        val specifications: List<SpecificationItem> = specificationsJson?.let { arr ->
            val remappedArr = arr.map { elem ->
                val obj = elem.jsonObject.toMutableMap()
                // If only "order" is present (Android), copy it to "orderIndex"
                if ("orderIndex" !in obj && "order" in obj) {
                    obj["orderIndex"] = obj["order"]!!
                }
                JsonObject(obj).toString()
            }
            sharedJson.decodeFromString<List<SpecificationItem>>(
                "[${remappedArr.joinToString(",")}]"
            )
        } ?: emptyList()

        val reviews = reviewsJson?.let {
            sharedJson.decodeFromString<List<ReviewItem>>(it.toString())
        } ?: emptyList()

        return BackupPayload(
            version = 1,
            exportedAt = 0L,
            products = products,
            specifications = specifications,
            reviews = reviews
        )
    }
}

expect fun extractDatabaseJsonFromZipBytes(bytes: ByteArray): String?
expect fun platformInflate(data: ByteArray): String?



