package com.example.model

import java.io.File

actual fun resolveSharedImageModel(url: String?, preferredName: String?): Any? {
    val clean = url?.trim().orEmpty()
    if (clean.isBlank() && preferredName.isNullOrBlank()) return null

    // 1. Android asset URL -> load from bundled desktop classpath resources
    if (clean.startsWith("file:///android_asset/")) {
        val assetPath = clean.removePrefix("file:///android_asset/")
        val resUrl = object {}::class.java.classLoader?.getResource(assetPath)
        if (resUrl != null) return resUrl.toExternalForm()
    }

    // 2. Local file path
    if (clean.startsWith("/") || clean.startsWith("file://")) {
        val f = File(clean.removePrefix("file://"))
        if (f.exists() && f.isFile) return f
    }

    // 3. Fallback: try preferredName in bundled pictures/ or review_images/
    val name = preferredName?.trim()?.takeIf { it.isNotBlank() }
        ?: clean.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
    if (name != null) {
        val cl = object {}::class.java.classLoader
        cl?.getResource("pictures/$name")?.let { return it.toExternalForm() }
        cl?.getResource("review_images/$name")?.let { return it.toExternalForm() }
    }

    return clean.ifBlank { null }
}
