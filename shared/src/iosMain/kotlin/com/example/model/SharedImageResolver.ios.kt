package com.example.model

actual fun resolveSharedImageModel(url: String?, preferredName: String?): Any? =
    url?.trim()?.ifBlank { null }
