package com.example.data

import kotlinx.cinterop.*
import platform.zlib.*

@OptIn(ExperimentalForeignApi::class)
actual fun platformInflate(data: ByteArray): String? {
    val outputChunkSize = 65536
    var result = ByteArray(0)

    data.usePinned { pinned ->
        memScoped {
            val outBuf = allocArray<UByteVar>(outputChunkSize)
            val strm = alloc<z_stream>()
            strm.next_in = pinned.addressOf(0).reinterpret()
            strm.avail_in = data.size.toUInt()

            var ret = inflateInit2(strm.ptr, -15)
            if (ret != Z_OK) return null

            do {
                strm.next_out = outBuf
                strm.avail_out = outputChunkSize.toUInt()
                ret = inflate(strm.ptr, Z_NO_FLUSH)
                val have = outputChunkSize - strm.avail_out.toInt()
                if (have > 0) {
                    result += ByteArray(have) { outBuf[it].toByte() }
                }
            } while (ret == Z_OK)

            inflateEnd(strm.ptr)
        }
    }

    return if (result.isNotEmpty()) result.decodeToString() else null
}

actual fun extractDatabaseJsonFromZipBytes(bytes: ByteArray): String? {
    try {
        val text = bytes.decodeToString()
        if (text.trimStart().startsWith("{")) {
            return text
        }
    } catch (_: Exception) {}
    return null
}

