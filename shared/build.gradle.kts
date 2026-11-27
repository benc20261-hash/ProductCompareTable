plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.example.shared"
        compileSdk = 36
        minSdk = 24
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedApp"
            isStatic = true
            binaryOption("bundleId", "com.example.SharedApp")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)
            implementation(libs.filekit.compose)
        }
        val desktopMain by getting {
            resources.srcDir("../app/src/main/assets")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

fun findJdkWithJpackage(): String? {
    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("win")
    val isMac = osName.contains("mac")
    val isLinux = osName.contains("nux") || osName.contains("nix")
    val jpackageRelPath = if (isWindows) "bin/jpackage.exe" else "bin/jpackage"

    fun isValidJdk(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val home = file(path)
        return home.isDirectory && file("$home/$jpackageRelPath").exists()
    }

    val envJdkHome = System.getenv("JDK_HOME")
    if (isValidJdk(envJdkHome)) return envJdkHome

    val envJavaHome = System.getenv("JAVA_HOME")
    if (isValidJdk(envJavaHome)) return envJavaHome

    val sysJavaHome = System.getProperty("java.home")
    if (isValidJdk(sysJavaHome)) return sysJavaHome

    if (isMac) {
        val macJdksDir = file("/Library/Java/JavaVirtualMachines")
        if (macJdksDir.exists() && macJdksDir.isDirectory) {
            macJdksDir.listFiles()?.sortedByDescending { it.name }?.forEach { jdkDir ->
                val homePath = "${jdkDir.absolutePath}/Contents/Home"
                if (isValidJdk(homePath)) return homePath
            }
        }
    }

    if (isLinux) {
        val linuxJdksDir = file("/usr/lib/jvm")
        if (linuxJdksDir.exists() && linuxJdksDir.isDirectory) {
            linuxJdksDir.listFiles()?.sortedByDescending { it.name }?.forEach { jdkDir ->
                if (isValidJdk(jdkDir.absolutePath)) return jdkDir.absolutePath
            }
        }
    }

    val userHome = System.getProperty("user.home")
    val gradleJdksDir = file("$userHome/.gradle/jdks")
    if (gradleJdksDir.exists()) {
        gradleJdksDir.walkTopDown().firstOrNull { it.name == "jpackage" || it.name == "jpackage.exe" }?.let { jpackageFile ->
            val binDir = jpackageFile.parentFile
            val homeDir = binDir?.parentFile
            if (homeDir != null && homeDir.isDirectory) {
                return homeDir.absolutePath
            }
        }
    }

    return null
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"
        findJdkWithJpackage()?.let { javaHome = it }
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "ProductCompare"
            packageVersion = "1.0.0"
        }
    }
}
