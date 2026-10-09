# Home Up Probe

A minimal **read-only** Android app. It does not modify One UI Home, Home Up settings, or the grid.

- Requests Home Up's `HOMESTAR_DATA` normal permission.
- Checks whether Android granted the permission.
- Calls `content://com.samsung.android.app.homestar.provider` with `version_info`.
- Displays the returned bundle or exception.

## Build

Open the folder in Android Studio with Android SDK 35 and JDK 17; build the debug APK. Alternatively use Gradle 8.9+ and Android SDK 35, and run `gradle assembleDebug` from this folder.

The output is `app/build/outputs/apk/debug/app-debug.apk`.

Install with `adb install -r app-debug.apk`, open **Home Up Probe**, and tap **Test permission and provider**.

This is a diagnostic only: there is no grid read/write yet. A successful `version_info` call does not imply 5x8 support.
