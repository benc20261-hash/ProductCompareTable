package com.example.ui

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual fun loadBundledSeedJson(): String? = runCatching {
    val resourceStream = object {}::class.java.classLoader
        ?.getResourceAsStream("seed_database.json")
    resourceStream?.bufferedReader(Charsets.UTF_8)?.readText()
}.getOrNull()

actual fun createZipWithDatabaseJson(jsonString: String): ByteArray? = runCatching {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
        zos.putNextEntry(ZipEntry("database.json"))
        zos.write(jsonString.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
    baos.toByteArray()
}.getOrNull()
