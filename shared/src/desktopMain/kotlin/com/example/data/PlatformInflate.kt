package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun platformInflate(data: ByteArray): String? = try {
    val inflater = InflaterInputStream(ByteArrayInputStream(data), java.util.zip.Inflater(true))
    val out = ByteArrayOutputStream()
    inflater.copyTo(out)
    inflater.close()
    out.toString(Charsets.UTF_8.name())
} catch (_: Exception) {
    try {
        val inflater = InflaterInputStream(ByteArrayInputStream(data))
        val out = ByteArrayOutputStream()
        inflater.copyTo(out)
        inflater.close()
        out.toString(Charsets.UTF_8.name())
    } catch (_: Exception) { null }
}

actual fun extractDatabaseJsonFromZipBytes(bytes: ByteArray): String? {
    val entries = extractZipEntries(bytes)
    entries.entries.firstOrNull { (name, _) ->
        val lower = name.lowercase()
        (lower == "database.json" || lower.endsWith("/database.json")) && !lower.contains("__macosx")
    }?.let { return it.value.decodeToString() }

    return runCatching {
        val text = bytes.decodeToString()
        if (text.trimStart().startsWith("{")) text else null
    }.getOrNull()
}

actual fun extractZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    val ok = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX") && !name.substringAfterLast('/').startsWith("._")) {
                    result[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        result.isNotEmpty()
    }.getOrDefault(false)

    if (ok) return result

    runCatching {
        val tempFile = java.io.File.createTempFile("backup_extract", ".zip")
        tempFile.writeBytes(bytes)
        ZipFile(tempFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX") && !name.substringAfterLast('/').startsWith("._")) {
                    result[name] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
        tempFile.delete()
    }
    return result
}

actual fun createZipFromEntries(entries: Map<String, ByteArray>): ByteArray? = runCatching {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
        entries.forEach { (name, data) ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(data)
            zos.closeEntry()
        }
    }
    baos.toByteArray()
}.getOrNull()
