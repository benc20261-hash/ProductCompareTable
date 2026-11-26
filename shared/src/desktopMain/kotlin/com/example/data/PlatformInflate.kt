package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

actual fun platformInflate(data: ByteArray): String? = try {
    val inflater = InflaterInputStream(ByteArrayInputStream(data), java.util.zip.Inflater(true))
    val out = ByteArrayOutputStream()
    inflater.copyTo(out)
    inflater.close()
    out.toString(Charsets.UTF_8.name())
} catch (_: Exception) {
    // Try without nowrap
    try {
        val inflater = InflaterInputStream(ByteArrayInputStream(data))
        val out = ByteArrayOutputStream()
        inflater.copyTo(out)
        inflater.close()
        out.toString(Charsets.UTF_8.name())
    } catch (_: Exception) { null }
}

actual fun extractDatabaseJsonFromZipBytes(bytes: ByteArray): String? {
    try {
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val nameLower = name.lowercase()
                if ((nameLower == "database.json" || nameLower.endsWith("/database.json")) && !nameLower.contains("__MACOSX")) {
                    val text = zis.bufferedReader(Charsets.UTF_8).readText()
                    zis.closeEntry()
                    if (text.isNotBlank()) return text
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    } catch (_: Exception) {}

    try {
        val tempFile = java.io.File.createTempFile("backup_extract", ".zip")
        tempFile.writeBytes(bytes)
        java.util.zip.ZipFile(tempFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                val nameLower = name.lowercase()
                if ((nameLower == "database.json" || nameLower.endsWith("/database.json")) && !nameLower.contains("__MACOSX")) {
                    val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    tempFile.delete()
                    if (text.isNotBlank()) return text
                }
            }
        }
        tempFile.delete()
    } catch (_: Exception) {}

    try {
        val text = bytes.decodeToString()
        if (text.trimStart().startsWith("{")) {
            return text
        }
    } catch (_: Exception) {}

    return null
}

