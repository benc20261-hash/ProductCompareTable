package com.example.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PictureItem(
    val id: String,
    val name: String,
    val url: String
)

@Serializable
data class ProductItem(
    val id: String,
    val name: String,
    val brand: String,
    val tags: List<String> = emptyList(),
    val overview: String = "",
    val pictures: List<PictureItem> = emptyList(),
    val createdTime: String = ""
)

@Serializable
data class SpecificationItem(
    val id: String,
    val productId: String,
    val productTitle: String,
    val specName: String,
    val details: String,
    val unit: String? = null,
    // Android export uses "order", desktop/iOS uses "orderIndex" — both are accepted via ignoreUnknownKeys + lenient JSON
    val orderIndex: Int = 0,
    val order: Int = 0,
    val pictures: List<PictureItem> = emptyList(),
    val isNumeric: Boolean? = null
) {
    // Canonical order value: whichever is set
    val effectiveOrder: Int get() = if (orderIndex != 0) orderIndex else order
}

@Serializable
data class ReviewItem(
    val id: String,
    val reviewNumber: Int,
    val title: String,
    val body: String,
    val authorName: String,
    val rating: Int = 5,
    val productIds: List<String> = emptyList(),
    val productTitles: List<String> = emptyList(),
    val pictures: List<PictureItem> = emptyList(),
    val createdTime: String = ""
)

val sharedJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

fun parsePicturesJson(json: String?): List<PictureItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        sharedJson.decodeFromString<List<PictureItem>>(json)
    } catch (_: Exception) {
        emptyList()
    }
}

fun parseStringArrayJson(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        sharedJson.decodeFromString<List<String>>(json)
    } catch (_: Exception) {
        emptyList()
    }
}

fun detectDeviceCategory(title: String, brand: String, tags: List<String>): String {
    val query = "$title $brand ${tags.joinToString(" ")}".lowercase()
    return when {
        "mic" in query || "boya" in query || "dji" in query -> "mic"
        "glasses" in query || "viture" in query || "ar" in query || "xr" in query -> "glasses"
        "laptop" in query || "macbook" in query || "asus" in query || "pc" in query || "notebook" in query -> "laptop"
        "watch" in query || "wearable" in query -> "watch"
        "headphone" in query || "headset" in query || "earphone" in query || "wh-" in query -> "headphones"
        "phone" in query || "iphone" in query || "samsung" in query || "fold" in query || "galaxy" in query || "mobile" in query -> "phone"
        else -> "general"
    }
}
