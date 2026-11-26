package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.model.PictureItem
import com.example.model.resolveSharedImageModel
import kotlinx.coroutines.delay

/**
 * SharedFullScreenPhotoLightbox — KMP equivalent of Android's FullScreenPhotoLightbox.
 * Features:
 * - Drag/pan with mouse or finger when zoomed in (scale > 1f)
 * - Pinch-to-zoom, mouse wheel / trackpad scroll zoom, double-tap zoom (1× ↔ 2.5×), and +/- zoom buttons
 * - Clickable % pill to reset zoom to 100%
 * - Left / Right navigation arrows to switch images
 * - Bottom thumbnail strip to click and switch to any other image
 * - Full Res badge, image counter, copy/save URL button, red Close button
 */
@Composable
fun SharedFullScreenPhotoLightbox(
    images: List<PictureItem>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, images.lastIndex)) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var copiedBanner by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Reset zoom & pan when switching images
    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    LaunchedEffect(copiedBanner) {
        if (copiedBanner) {
            delay(1800)
            copiedBanner = false
        }
    }

    fun applyScaleDelta(newScale: Float, viewportW: Float = 1200f, viewportH: Float = 800f) {
        val clamped = newScale.coerceIn(1f, 5f)
        scale = clamped
        if (clamped <= 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val maxOX = (viewportW * (clamped - 1f)) / 2f
            val maxOY = (viewportH * (clamped - 1f)) / 2f
            offsetX = offsetX.coerceIn(-maxOX, maxOX)
            offsetY = offsetY.coerceIn(-maxOY, maxOY)
        }
    }

    val currentImage = images[currentIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            // ── Main image viewport with double-tap, pinch/drag pan, and scroll-wheel zoom ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    val targetScale = 2.5f
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val maxOX = (size.width * (targetScale - 1f)) / 2f
                                    val maxOY = (size.height * (targetScale - 1f)) / 2f
                                    offsetX = ((cx - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxOX, maxOX)
                                    offsetY = ((cy - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxOY, maxOY)
                                    scale = targetScale
                                }
                            }
                        )
                    }
                    .pointerInput(currentIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                val maxOX = (size.width * (newScale - 1f)) / 2f
                                val maxOY = (size.height * (newScale - 1f)) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOX, maxOX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOY, maxOY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(currentIndex) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (scrollY != 0f) {
                                        val factor = if (scrollY < 0f) 1.12f else 0.89f
                                        applyScaleDelta(scale * factor, size.width.toFloat(), size.height.toFloat())
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = resolveSharedImageModel(currentImage.url, currentImage.name),
                    contentDescription = currentImage.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 80.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }

            // ── Left navigation arrow ────────────────────────────────────────
            if (currentIndex > 0) {
                IconButton(
                    onClick = { currentIndex-- },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000))
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            // ── Right navigation arrow ───────────────────────────────────────
            if (currentIndex < images.lastIndex) {
                IconButton(
                    onClick = { currentIndex++ },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000))
                ) {
                    Icon(Icons.Default.ChevronRight, "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            // ── Top header bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0xCC0F172A))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title + counter + Full Res badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = currentImage.name.ifBlank { "photo_${currentIndex + 1}.jpg" },
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E293B)) {
                        Text(
                            "${currentIndex + 1} / ${images.size}",
                            color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1B4B),
                        border = BorderStroke(1.dp, Color(0xFF6366F1))
                    ) {
                        Text(
                            "Full Res", color = Color(0xFFA5B4FC), fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Zoom controls + copy URL + close
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { applyScaleDelta(scale - 0.25f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, "Zoom out", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        "${(scale * 100).toInt()}%",
                        color = if (scale > 1f) Color(0xFFA5B4FC) else Color(0xFF94A3B8),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { scale = 1f; offsetX = 0f; offsetY = 0f }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    IconButton(
                        onClick = { applyScaleDelta(scale + 0.25f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, "Zoom in", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            val target = currentImage.url.ifBlank { currentImage.name }
                            clipboardManager.setText(AnnotatedString(target))
                            copiedBanner = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Download, "Copy image reference", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    // Red close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE11D48))
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (copiedBanner) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF6366F1)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp)
                ) {
                    Text(
                        "Image reference copied to clipboard",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Bottom thumbnail strip ───────────────────────────────────────
            if (images.size > 1) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = Color(0xCC0F172A),
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        images.forEachIndexed { idx, pic ->
                            val isSelected = idx == currentIndex
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF6366F1) else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { currentIndex = idx }
                            ) {
                                AsyncImage(
                                    model = resolveSharedImageModel(pic.url, pic.name),
                                    contentDescription = pic.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
