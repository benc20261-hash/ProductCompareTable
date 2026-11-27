package com.example.shared

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

class IosPlatformInfo : PlatformInfo {
    override val name: String = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
    override val isDesktop: Boolean = false
    override val isMobile: Boolean = true
    override val storageDirectory: String = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String) ?: ""
}

actual fun getPlatformInfo(): PlatformInfo = IosPlatformInfo()
