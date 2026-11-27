package com.example.shared

interface PlatformInfo {
    val name: String
    val isDesktop: Boolean
    val isMobile: Boolean
    val storageDirectory: String
}

expect fun getPlatformInfo(): PlatformInfo
