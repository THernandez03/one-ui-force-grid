# Home Up Probe v2 - read-only grid test

Open this folder in Android Studio and build an APK with Build APK(s), or run `gradlew.bat assembleDebug` if a Gradle wrapper is available.

Install the debug APK using `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Open **Home Up Probe** and tap **Read current grid**. Copy the full diagnostic text.

This app requests Samsung's normal HOMESTAR_DATA permission and attempts to instantiate the `HomeGrid` class from the *installed* Home Up package via `createPackageContext`. It does not package or modify One UI Home, and performs only a `bulk_load` read.

**Experimental:** Android class-loading rules or Samsung's dependencies may prevent this approach. If it fails, report the exact error. Do not grant additional permissions or alter launcher data.
