package com.example.model

import com.example.shared.getPlatformInfo
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

actual fun resolveSharedImageModel(url: String?, preferredName: String?): Any? {
    val clean = url?.trim().orEmpty()
    if (clean.isBlank() && preferredName.isNullOrBlank()) return null

    val fm = NSFileManager.defaultManager
    val storageDir = getPlatformInfo().storageDirectory
    val urlFilename = clean.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
    val candidates = listOfNotNull(
        urlFilename,
        preferredName?.trim()?.takeIf { it.isNotBlank() }
    ).distinct()

    // 1. Direct local file path
    if (clean.startsWith("/") || clean.startsWith("file://")) {
        val localPath = clean.removePrefix("file://")
        if (localPath.isNotBlank() && fm.fileExistsAtPath(localPath)) {
            return "file://$localPath"
        }
    }

    // 2. Restored backup / local storage directories (pictures/ and review_images/)
    if (storageDir.isNotBlank()) {
        for (name in candidates) {
            val p1 = "$storageDir/pictures/$name"
            if (fm.fileExistsAtPath(p1)) return "file://$p1"
            val p2 = "$storageDir/review_images/$name"
            if (fm.fileExistsAtPath(p2)) return "file://$p2"
        }
    }

    // 3. Bundled iOS resources fallback
    for (name in candidates) {
        val base = name.substringBeforeLast(".", name)
        val ext = name.substringAfterLast(".", "")
        val bundlePath = if (ext.isNotEmpty() && ext != name) {
            NSBundle.mainBundle.pathForResource(base, ofType = ext)
        } else null
        if (!bundlePath.isNullOrBlank()) return "file://$bundlePath"
    }

    return clean.ifBlank { null }
}
