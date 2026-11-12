package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class ProductCompareApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Critical for BlueStacks Air and emulators: software bitmap decoding avoids
            // virtual GPU / Metal driver crashes with HardwareBitmaps
            .allowHardware(false)
            // Uses 50% less RAM per decoded image to prevent Out-Of-Memory on low-heap emulators
            .allowRgb565(true)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
