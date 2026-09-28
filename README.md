# App Cloner (honest functional subset)

This is a native Android/Jetpack Compose implementation based on the App Cloner screenshots supplied with the task. The repository's original `duplicate-remover.zip` was inspected: it is a separate Duplicate Remover project and does not contain the App Cloner Stitch export. The supplied screenshots were therefore used as the visual reference.

## What works

- Enumerates installed apps that expose a launcher activity, using a scoped `<queries>` launcher-intent declaration.
- Displays app icon, label, package ID, version, search, and the All Apps / Cloneable / Cloned filters.
- Opens selected original apps through Android's launch intent.
- Persists default-name, numbering, delete-confirmation, and theme preferences locally.
- Includes honest empty, privacy, and unsupported-capability states matching the supplied dark navy/lavender design.
- Provides validation helpers and unit tests for suggested names and package IDs.

## APK-cloning limitation

This version **does not generate, modify, sign, install, share, or claim to have installed cloned APKs**. Android has no general public API that turns arbitrary installed applications into separately installable copies. APK rewriting and re-signing may break signatures, integrity checks, licensing, Google services, or server authentication. A future implementation would need a defined, lawful APK-processing method and end-to-end validation against apps whose licenses permit modification; until then, apps are never marked cloneable.

## Architecture

- `domain/`: conservative compatibility result, an unavailable-by-design `ApkCloneEngine` extension point, and input validation.
- `data/InstalledAppRepository.kt`: launcher-visible package discovery using `PackageManager`.
- `data/PreferencesStore.kt`: local preferences using app-private SharedPreferences.
- `MainActivity.kt` and `ui/Theme.kt`: Compose navigation, screens, and screenshot-inspired theme.

## Requirements / build

- Android Studio with JDK 17 and Android SDK Platform 35.
- Minimum Android 8.0 (API 26); target Android 15 (API 35).
- Open this directory in Android Studio, allow Gradle sync, then run the `app` configuration or choose **Build > Build APK(s)**.
- From a configured command line, run `gradle testDebugUnitTest assembleDebug`.
- The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`. Install it with Android Studio, or with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

No storage, network, install-packages, all-packages, root, accessibility, or elevated permissions are declared. App visibility is limited to apps with a launcher activity. Build/tests could not be executed in the provided coding environment because Java, the Android SDK, and Gradle are not installed.
