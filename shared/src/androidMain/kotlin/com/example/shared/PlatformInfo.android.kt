package com.example.shared

class AndroidPlatformInfo : PlatformInfo {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
    override val isDesktop: Boolean = false
    override val isMobile: Boolean = true
    override val storageDirectory: String
        get() = androidStorageDirectoryOverride ?: "/data/data/com.example/files"

    companion object {
        var androidStorageDirectoryOverride: String? = null
    }
}

actual fun getPlatformInfo(): PlatformInfo = AndroidPlatformInfo()
