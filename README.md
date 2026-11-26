# ProductCompare — Kotlin Multiplatform (KMP)

A cross-platform product comparison and specification analysis application built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**, targeting:
- **Android** (Phones & Tablets)
- **Desktop** (Windows, macOS, Linux)
- **iOS & iPadOS** (iPhone & iPad)

---

## Architecture Overview

```
ProductCompare/
├── shared/                             # KMP Shared Module
│   ├── src/commonMain/                 # Shared UI (Compose MP), Models, Business Logic
│   │   ├── kotlin/com/example/model/   # Serializable Domain Models (ProductItem, SpecItem, etc.)
│   │   ├── kotlin/com/example/ui/      # Shared Compose Multiplatform UI
│   │   └── kotlin/com/example/shared/  # Expect/Actual platform definitions
│   ├── src/androidMain/                # Android-specific platform adapters
│   ├── src/desktopMain/                # Desktop entry point (JVM) & distribution
│   └── src/iosMain/                    # iOS/iPad MainViewController & UIKit bridges
├── app/                                # Android Application Module
│   └── src/main/                       # Android Room Database, Services, Workspaces
└── iosApp/                             # iOS / iPad SwiftUI Application
    └── iosApp/                         # SwiftUI entry point integrating SharedApp.framework
```

---

## How to Build & Run on Each Platform

### 1. Android
Open the project in **Android Studio** and run the `:app` configuration, or via command line:
```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```
Run unit tests:
```bash
./gradlew :app:testDebugUnitTest
```

---

### 2. Desktop (Windows, macOS, Linux)
Run the desktop app directly:
```bash
./gradlew :shared:run
```

Package distribution:
- **Executable JAR**:
  ```bash
  ./gradlew :shared:packageUberJarForCurrentOS
  ```
- **Native Installer** (`.dmg` on macOS, `.msi` on Windows, `.deb` on Linux):
  ```bash
  ./gradlew :shared:packageDistributionForCurrentOS
  ```

---

### 3. iOS & iPadOS (iPhone / iPad)
1. Generate the iOS frameworks:
   ```bash
   # For iOS Simulator (Apple Silicon or Intel):
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   # (Optional fat binary if running on x86_64 simulator):
   ./gradlew :shared:linkDebugFrameworkIosX64

   # For physical iPhone / iPad devices:
   ./gradlew :shared:linkDebugFrameworkIosArm64
   ```
2. Open the Xcode project in `iosApp/`:
   ```bash
   open iosApp/iosApp.xcodeproj
   ```
3. Select your target (e.g. Any iOS Device, connected iPhone/iPad, or Simulator) and press **⌘ + B** to build or **⌘ + R** to run.
