package com.example.data

import kotlinx.cinterop.*
import platform.zlib.*

@OptIn(ExperimentalForeignApi::class)
private fun inflateRawBytes(data: ByteArray): ByteArray? {
    val outputChunkSize = 65536
    var result = ByteArray(0)

    data.usePinned { pinned ->
        memScoped {
            val outBuf = allocArray<UByteVar>(outputChunkSize)
            val strm = alloc<z_stream>()
            strm.next_in = pinned.addressOf(0).reinterpret()
            strm.avail_in = data.size.toUInt()

            val retInit = inflateInit2(strm.ptr, -15)
            if (retInit != Z_OK) return null

            var ret: Int
            do {
                strm.next_out = outBuf
                strm.avail_out = outputChunkSize.toUInt()
                ret = inflate(strm.ptr, Z_NO_FLUSH)
                val have = outputChunkSize - strm.avail_out.toInt()
                if (have > 0) {
                    val chunk = ByteArray(have) { outBuf[it].toByte() }
                    result += chunk
                }
            } while (ret == Z_OK)

            inflateEnd(strm.ptr)
        }
    }
    return if (result.isNotEmpty()) result else null
}

actual fun platformInflate(data: ByteArray): String? =
    inflateRawBytes(data)?.decodeToString()

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

private fun ByteArray.u16(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(offset: Int): Long =
    (this[offset].toLong() and 0xFFL) or
        ((this[offset + 1].toLong() and 0xFFL) shl 8) or
        ((this[offset + 2].toLong() and 0xFFL) shl 16) or
        ((this[offset + 3].toLong() and 0xFFL) shl 24)

actual fun extractZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    if (bytes.size < 22) return result

    // Locate End of Central Directory (PK\x05\x06)
    val minSearch = (bytes.size - 65557).coerceAtLeast(0)
    var eocdOffset = -1
    for (i in (bytes.size - 22) downTo minSearch) {
        if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() &&
            bytes[i + 2] == 0x05.toByte() && bytes[i + 3] == 0x06.toByte()
        ) {
            eocdOffset = i
            break
        }
    }
    if (eocdOffset == -1) return result

    val totalEntries = bytes.u16(eocdOffset + 10)
    var cdOffset = bytes.u32(eocdOffset + 16).toInt()

    for (idx in 0 until totalEntries) {
        if (cdOffset + 46 > bytes.size) break
        if (bytes.u32(cdOffset) != 0x02014B50L) break

        val method = bytes.u16(cdOffset + 10)
        val compressedSize = bytes.u32(cdOffset + 20).toInt()
        val nameLen = bytes.u16(cdOffset + 28)
        val extraLen = bytes.u16(cdOffset + 30)
        val commentLen = bytes.u16(cdOffset + 32)
        val localHeaderOffset = bytes.u32(cdOffset + 42).toInt()

        val nameStart = cdOffset + 46
        if (nameStart + nameLen > bytes.size) break
        val entryName = bytes.copyOfRange(nameStart, nameStart + nameLen).decodeToString()
        cdOffset = nameStart + nameLen + extraLen + commentLen

        if (entryName.endsWith("/") || entryName.contains("__MACOSX") || entryName.substringAfterLast('/').startsWith("._")) {
            continue
        }
        if (localHeaderOffset + 30 > bytes.size) continue
        val lfhNameLen = bytes.u16(localHeaderOffset + 26)
        val lfhExtraLen = bytes.u16(localHeaderOffset + 28)
        val dataStart = localHeaderOffset + 30 + lfhNameLen + lfhExtraLen
        val dataEnd = (dataStart + compressedSize).coerceAtMost(bytes.size)
        if (dataStart < 0 || dataStart > dataEnd) continue

        val rawData = bytes.copyOfRange(dataStart, dataEnd)
        val extracted = when (method) {
            0 -> rawData
            8 -> inflateRawBytes(rawData)
            else -> null
        }
        if (extracted != null) {
            result[entryName] = extracted
        }
    }
    return result
}

private val crcTable: IntArray by lazy {
    IntArray(256) { n ->
        var c = n
        repeat(8) {
            c = if ((c and 1) != 0) (c ushr 1) xor 0xEDB88320.toInt() else (c ushr 1)
        }
        c
    }
}

private fun crc32(data: ByteArray): Long {
    var c = 0xFFFFFFFF.toInt()
    for (b in data) {
        c = crcTable[(c xor (b.toInt() and 0xFF)) and 0xFF] xor (c ushr 8)
    }
    return (c xor 0xFFFFFFFF.toInt()).toLong() and 0xFFFFFFFFL
}

private fun MutableList<Byte>.putU16(v: Int) {
    add((v and 0xFF).toByte())
    add(((v ushr 8) and 0xFF).toByte())
}

private fun MutableList<Byte>.putU32(v: Long) {
    add((v and 0xFF).toByte())
    add(((v ushr 8) and 0xFF).toByte())
    add(((v ushr 16) and 0xFF).toByte())
    add(((v ushr 24) and 0xFF).toByte())
}

actual fun createZipFromEntries(entries: Map<String, ByteArray>): ByteArray? = runCatching {
    val out = ArrayList<Byte>(65536)
    val centralDir = ArrayList<Byte>(4096)
    var count = 0

    entries.forEach { (name, data) ->
        val nameBytes = name.encodeToByteArray()
        val crc = crc32(data)
        val localOffset = out.size.toLong()

        // Local file header (STORED, method 0)
        out.putU32(0x04034B50L)
        out.putU16(20) // version needed
        out.putU16(0x0800) // UTF-8 flag
        out.putU16(0) // compression method: STORED
        out.putU16(0) // mod time
        out.putU16(0) // mod date
        out.putU32(crc)
        out.putU32(data.size.toLong())
        out.putU32(data.size.toLong())
        out.putU16(nameBytes.size)
        out.putU16(0) // extra len
        nameBytes.forEach { out.add(it) }
        data.forEach { out.add(it) }

        // Central directory header
        centralDir.putU32(0x02014B50L)
        centralDir.putU16(20)
        centralDir.putU16(20)
        centralDir.putU16(0x0800)
        centralDir.putU16(0)
        centralDir.putU16(0)
        centralDir.putU16(0)
        centralDir.putU32(crc)
        centralDir.putU32(data.size.toLong())
        centralDir.putU32(data.size.toLong())
        centralDir.putU16(nameBytes.size)
        centralDir.putU16(0)
        centralDir.putU16(0)
        centralDir.putU16(0)
        centralDir.putU16(0)
        centralDir.putU32(0)
        centralDir.putU32(localOffset)
        nameBytes.forEach { centralDir.add(it) }
        count++
    }

    val cdOffset = out.size.toLong()
    out.addAll(centralDir)

    // End of central directory
    out.putU32(0x06054B50L)
    out.putU16(0)
    out.putU16(0)
    out.putU16(count)
    out.putU16(count)
    out.putU32(centralDir.size.toLong())
    out.putU32(cdOffset)
    out.putU16(0)

    out.toByteArray()
}.getOrNull()
