# Home Up Probe v3 - experimental grid writer

This is an experimental non-root Android utility. It loads Samsung classes from the **installed** Home Up APK, not a replacement launcher. It reads `home_grid` via `bulk_load`, stores the original value in this utility's private SharedPreferences using synchronous `commit()`, and calls Home Up's provider to attempt a write of `5x8`.

## Build
Open this folder in Android Studio, build APK(s) or run `gradlew.bat assembleDebug` if you have a Gradle wrapper. Install with `adb install -r app-debug.apk`. Package name remains `com.example.homeupprobe`.

## Caution
Take screenshots of your home screens before testing. A provider readback of `5x8` does not prove that One UI Home supports or renders eight rows. No launcher data is cleared, no root is used, and the app does not force-stop the launcher. Keep this helper installed until you've restored the saved value. Uninstalling or clearing its data deletes its private backup.

The provider `call()` method is assumed to accept `arg="save"` based on the JADX analysis; check the actual `SAVE` constant in your HomeUp.apk if the write fails. The `method="property"` is intentionally not `bulk_load`; in the observed provider implementation any other method with a SerializableProperty extra goes through the property branch. The `Bundle` key is the Kotlin interface simple name `SerializableProperty`.

If readback is `5x8` but the launcher remains 5x7, do not infer success: restore 5x7 and report results.
