package com.example.model

import com.example.shared.getPlatformInfo
import java.io.File

actual fun resolveSharedImageModel(url: String?, preferredName: String?): Any? {
    val clean = url?.trim().orEmpty()
    if (clean.isBlank() && preferredName.isNullOrBlank()) return null

    val storageDir = getPlatformInfo().storageDirectory
    val urlFilename = clean.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
    val candidates = listOfNotNull(
        urlFilename,
        preferredName?.trim()?.takeIf { it.isNotBlank() }
    ).distinct()

    // 1. Direct local file path
    if (clean.startsWith("/") || clean.startsWith("file://")) {
        val localPath = clean.removePrefix("file://")
        val f = File(localPath)
        if (f.exists() && f.isFile && f.length() > 0L) return f
    }

    // 2. Restored backup / local storage directories (pictures/ and review_images/)
    for (name in candidates) {
        val p1 = File(storageDir, "pictures/$name")
        if (p1.exists() && p1.isFile && p1.length() > 0L) return p1
        val p2 = File(storageDir, "review_images/$name")
        if (p2.exists() && p2.isFile && p2.length() > 0L) return p2
    }

    // 3. Android asset URL -> load from bundled desktop classpath resources
    if (clean.startsWith("file:///android_asset/")) {
        val assetPath = clean.removePrefix("file:///android_asset/")
        val resUrl = object {}::class.java.classLoader?.getResource(assetPath)
        if (resUrl != null) return resUrl.toExternalForm()
    }

    // 4. Fallback: try candidate filenames in bundled pictures/ or review_images/
    val cl = object {}::class.java.classLoader
    for (name in candidates) {
        cl?.getResource("pictures/$name")?.let { return it.toExternalForm() }
        cl?.getResource("review_images/$name")?.let { return it.toExternalForm() }
    }

    return clean.ifBlank { null }
}
