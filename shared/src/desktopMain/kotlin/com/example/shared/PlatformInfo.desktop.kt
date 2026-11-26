package com.example.shared

class DesktopPlatformInfo : PlatformInfo {
    override val name: String = "${System.getProperty("os.name")} (${System.getProperty("os.arch")})"
    override val isDesktop: Boolean = true
    override val isMobile: Boolean = false
    override val storageDirectory: String = "${System.getProperty("user.home")}/.productcompare"
}

actual fun getPlatformInfo(): PlatformInfo = DesktopPlatformInfo()
