package com.example.model

/**
 * Resolves an image URL (which may be an http/https URL, a local file path, or
 * a bundled `file:///android_asset/...` path from seed_database.json) into a
 * Coil-compatible model for the current platform.
 */
expect fun resolveSharedImageModel(url: String?, preferredName: String? = null): Any?
