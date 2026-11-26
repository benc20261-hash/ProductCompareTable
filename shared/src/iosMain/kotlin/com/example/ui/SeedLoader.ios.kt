package com.example.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun loadBundledSeedJson(): String? = runCatching {
    val path = NSBundle.mainBundle.pathForResource("seed_database", ofType = "json") ?: return null
    @Suppress("CAST_NEVER_SUCCEEDS")
    NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) as? String
}.getOrNull()

actual fun createZipWithDatabaseJson(jsonString: String): ByteArray? = null
