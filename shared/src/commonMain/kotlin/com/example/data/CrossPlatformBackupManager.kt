package com.example.data

import com.example.model.PictureItem
import com.example.model.ProductItem
import com.example.model.ReviewItem
import com.example.model.SpecificationItem
import com.example.model.sharedJson
import com.example.shared.getPlatformInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    /**
     * Builds a full .zip archive containing database.json, manifest.json,
     * and all local pictures/ and review_images/ referenced by products, specs, and reviews.
     */
    fun exportToFullZipBytes(
        products: List<ProductItem>,
        specifications: List<SpecificationItem>,
        reviews: List<ReviewItem>
    ): ByteArray? {
        val entries = linkedMapOf<String, ByteArray>()
        entries["database.json"] = exportToJsonString(products, specifications, reviews).encodeToByteArray()
        val totalPics = products.sumOf { it.pictures.size } +
            specifications.sumOf { it.pictures.size } +
            reviews.sumOf { it.pictures.size }
        val manifest = """
            {
              "appName": "ProductCompare",
              "version": "1.0",
              "productCount": ${products.size},
              "specCount": ${specifications.size},
              "reviewCount": ${reviews.size},
              "pictureCount": $totalPics
            }
        """.trimIndent()
        entries["manifest.json"] = manifest.encodeToByteArray()

        val storageDir = getPlatformInfo().storageDirectory
        fun addLocalPicture(pic: PictureItem, targetFolder: String) {
            val fileBytes = readPictureBytes(pic, storageDir) ?: return
            val filename = pic.url.substringAfterLast("/").substringBefore("?")
                .ifBlank { pic.name.ifBlank { "${pic.id}.jpg" } }
            val entryKey = "$targetFolder/$filename"
            if (entryKey !in entries) {
                entries[entryKey] = fileBytes
            }
        }

        products.forEach { p -> p.pictures.forEach { addLocalPicture(it, "pictures") } }
        specifications.forEach { s -> s.pictures.forEach { addLocalPicture(it, "pictures") } }
        reviews.forEach { r -> r.pictures.forEach { addLocalPicture(it, "review_images") } }

        // Also include any files already stored in storageDir/pictures and storageDir/review_images
        if (storageDir.isNotBlank()) {
            listOf("pictures", "review_images").forEach { folder ->
                val dirPath = "$storageDir/$folder".toPath()
                if (fileSystem.exists(dirPath)) {
                    runCatching {
                        fileSystem.list(dirPath).forEach { filePath ->
                            val name = filePath.name
                            val entryKey = "$folder/$name"
                            if (!name.startsWith(".") && entryKey !in entries) {
                                runCatching { entries[entryKey] = fileSystem.read(filePath) { readByteArray() } }
                            }
                        }
                    }
                }
            }
        }

        return createZipFromEntries(entries)
    }

    private fun readPictureBytes(pic: PictureItem, storageDir: String): ByteArray? {
        val cleanUrl = pic.url.trim()
        val candidates = buildList {
            if (cleanUrl.startsWith("/") || cleanUrl.startsWith("file://")) {
                add(cleanUrl.removePrefix("file://"))
            }
            val urlName = cleanUrl.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
            if (storageDir.isNotBlank()) {
                if (urlName != null) {
                    add("$storageDir/pictures/$urlName")
                    add("$storageDir/review_images/$urlName")
                }
                if (pic.name.isNotBlank()) {
                    add("$storageDir/pictures/${pic.name}")
                    add("$storageDir/review_images/${pic.name}")
                }
            }
        }
        for (pathStr in candidates) {
            val p = pathStr.toPath()
            if (fileSystem.exists(p)) {
                val data = runCatching { fileSystem.read(p) { readByteArray() } }.getOrNull()
                if (data != null && data.isNotEmpty()) return data
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------
    // Import — JSON file on disk
    // ---------------------------------------------------------------------------

    fun importFromJsonFile(sourceFilePath: String): Result<BackupPayload> = runCatching {
        val path = sourceFilePath.toPath()
        val jsonString = fileSystem.read(path) { readUtf8() }
        parseJsonString(jsonString)
    }

    fun importFromJsonString(jsonString: String): Result<BackupPayload> = runCatching {
        parseJsonString(jsonString)
    }

    // ---------------------------------------------------------------------------
    // Import — bytes of a file (.json or .zip containing database.json + images)
    // ---------------------------------------------------------------------------

    fun importFromBytes(bytes: ByteArray, fileName: String): Result<BackupPayload> = runCatching {
        val isZipSignature = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (fileName.lowercase().endsWith(".zip") || isZipSignature) {
            val entries = extractZipEntries(bytes)
            val jsonBytes = entries.entries.firstOrNull { (name, _) ->
                val lower = name.lowercase()
                (lower == "database.json" || lower.endsWith("/database.json")) && !lower.contains("__macosx")
            }?.value ?: error("ZIP does not contain a recognizable database.json")

            val restoredFileMap = persistExtractedImages(entries)
            val rawPayload = parseJsonString(jsonBytes.decodeToString())
            relinkPayloadImages(rawPayload, restoredFileMap)
        } else {
            parseJsonString(bytes.decodeToString())
        }
    }

    /**
     * Extracts pictures/ and review_images/ entries from the ZIP into
     * `${storageDirectory}/pictures` and `${storageDirectory}/review_images`,
     * returning a lookup map from filename (lowercase) -> absolute local path.
     */
    private fun persistExtractedImages(entries: Map<String, ByteArray>): Map<String, String> {
        val storageDir = getPlatformInfo().storageDirectory
        if (storageDir.isBlank()) return emptyMap()

        val picturesDir = "$storageDir/pictures".toPath()
        val reviewImagesDir = "$storageDir/review_images".toPath()
        if (!fileSystem.exists(picturesDir)) fileSystem.createDirectories(picturesDir)
        if (!fileSystem.exists(reviewImagesDir)) fileSystem.createDirectories(reviewImagesDir)

        val restoredPaths = linkedMapOf<String, String>()
        entries.forEach { (rawName, data) ->
            if (data.isEmpty()) return@forEach
            val lower = rawName.lowercase()
            if (lower.contains("__macosx")) return@forEach
            val fileName = rawName.substringAfterLast('/')
            if (fileName.isBlank() || fileName.startsWith(".")) return@forEach

            val isPictures = lower.startsWith("pictures/") || lower.contains("/pictures/")
            val isReviewImages = lower.startsWith("review_images/") || lower.contains("/review_images/")
            if (!isPictures && !isReviewImages) return@forEach

            val destDir = if (isReviewImages) reviewImagesDir else picturesDir
            val destPath = "$destDir/$fileName".toPath()
            runCatching {
                fileSystem.write(destPath) { write(data) }
                val destStr = destPath.toString()
                restoredPaths[fileName.lowercase()] = destStr
                // Also index by stem (without extension) for id-based matching
                val stem = fileName.substringBeforeLast('.', fileName).lowercase()
                if (stem.isNotBlank() && stem !in restoredPaths) {
                    restoredPaths[stem] = destStr
                }
            }
        }
        return restoredPaths
    }

    private fun relinkPayloadImages(
        payload: BackupPayload,
        restoredPaths: Map<String, String>
    ): BackupPayload {
        if (restoredPaths.isEmpty()) return payload

        fun relinkPicture(pic: PictureItem): PictureItem {
            val urlFile = pic.url.substringAfterLast('/').substringBefore('?').trim().lowercase()
            val nameFile = pic.name.trim().lowercase()
            val idLower = pic.id.trim().lowercase()

            val matched = restoredPaths[urlFile]
                ?: restoredPaths[nameFile]
                ?: restoredPaths[urlFile.substringBeforeLast('.', urlFile)]
                ?: restoredPaths[idLower]
            return if (matched != null) pic.copy(url = matched) else pic
        }

        val updatedProducts = payload.products.map { p ->
            p.copy(pictures = p.pictures.map(::relinkPicture))
        }
        val updatedSpecs = payload.specifications.map { s ->
            s.copy(pictures = s.pictures.map(::relinkPicture))
        }
        val updatedReviews = payload.reviews.map { r ->
            var updatedBody = r.body
            restoredPaths.forEach { (key, localPath) ->
                if (key.contains('.') && updatedBody.contains(key, ignoreCase = true)) {
                    val imgPattern = Regex("""src=["'][^"']*${Regex.escape(key)}["']""", RegexOption.IGNORE_CASE)
                    updatedBody = updatedBody.replace(imgPattern, """src="file://$localPath"""")
                }
            }
            r.copy(
                body = updatedBody,
                pictures = r.pictures.map(::relinkPicture)
            )
        }
        return payload.copy(
            products = updatedProducts,
            specifications = updatedSpecs,
            reviews = updatedReviews
        )
    }

    fun savePictureBytesToStorage(bytes: ByteArray, originalName: String): PictureItem {
        val storageDir = getPlatformInfo().storageDirectory
        val cleanName = originalName.ifBlank { "spec_pic_${kotlin.random.Random.nextInt(10000, 99999)}.jpg" }
        val uniqueId = "pic_${kotlin.random.Random.nextLong(100000000, 999999999)}"
        if (storageDir.isBlank()) {
            return PictureItem(id = uniqueId, name = cleanName, url = cleanName)
        }
        val picturesDir = "$storageDir/pictures".toPath()
        if (!fileSystem.exists(picturesDir)) fileSystem.createDirectories(picturesDir)
        val ext = cleanName.substringAfterLast('.', "jpg")
        val destFileName = "${uniqueId}.$ext"
        val destPath = "$picturesDir/$destFileName".toPath()
        runCatching {
            fileSystem.write(destPath) { write(bytes) }
        }
        return PictureItem(id = uniqueId, name = cleanName, url = destPath.toString())
    }

    /**
     * Deletes image files in storageDirectory/pictures and storageDirectory/review_images
     * that are not referenced by any product, specification, or review.
     */
    fun freeUpOrphanImages(
        products: List<ProductItem>,
        specifications: List<SpecificationItem>,
        reviews: List<ReviewItem>
    ): Int {
        val storageDir = getPlatformInfo().storageDirectory
        if (storageDir.isBlank()) return 0

        val referencedNames = buildSet {
            fun collect(pics: List<PictureItem>) {
                pics.forEach { pic ->
                    val u = pic.url.substringAfterLast('/').substringBefore('?').trim().lowercase()
                    val n = pic.name.trim().lowercase()
                    if (u.isNotBlank()) add(u)
                    if (n.isNotBlank()) add(n)
                }
            }
            products.forEach { collect(it.pictures) }
            specifications.forEach { collect(it.pictures) }
            reviews.forEach { r ->
                collect(r.pictures)
                Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(r.body).forEach { m ->
                    val fn = m.groupValues[1].substringAfterLast('/').substringBefore('?').trim().lowercase()
                    if (fn.isNotBlank()) add(fn)
                }
            }
        }

        var deleted = 0
        listOf("pictures", "review_images").forEach { folder ->
            val dirPath = "$storageDir/$folder".toPath()
            if (fileSystem.exists(dirPath)) {
                runCatching {
                    fileSystem.list(dirPath).forEach { filePath ->
                        val fn = filePath.name.lowercase()
                        if (!fn.startsWith(".") && fn !in referencedNames) {
                            if (runCatching { fileSystem.delete(filePath) }.isSuccess) {
                                deleted++
                            }
                        }
                    }
                }
            }
        }
        return deleted
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun parseJsonString(jsonString: String): BackupPayload {
        val direct = runCatching {
            sharedJson.decodeFromString(BackupPayload.serializer(), jsonString)
        }
        if (direct.isSuccess && direct.getOrNull()?.products?.isNotEmpty() == true) {
            return direct.getOrThrow()
        }

        val root = sharedJson.decodeFromString(JsonObject.serializer(), jsonString)

        val productsJson = root["products"]?.jsonArray
            ?: root["product_items"]?.jsonArray
        val specificationsJson = root["specifications"]?.jsonArray
            ?: root["specs"]?.jsonArray
        val reviewsJson = root["reviews"]?.jsonArray

        val products = productsJson?.let {
            sharedJson.decodeFromString<List<ProductItem>>(it.toString())
        } ?: emptyList()

        val specifications: List<SpecificationItem> = specificationsJson?.let { arr ->
            val remappedArr = arr.map { elem ->
                val obj = elem.jsonObject.toMutableMap()
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
expect fun extractZipEntries(bytes: ByteArray): Map<String, ByteArray>
expect fun createZipFromEntries(entries: Map<String, ByteArray>): ByteArray?
expect fun platformInflate(data: ByteArray): String?
