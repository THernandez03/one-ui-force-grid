# Home Up Probe — Custom Samsung One UI Home Grid (No Root)

Enable a **5×8 home-screen grid** on the **Samsung Galaxy S24 Ultra**, using the original Samsung One UI Home launcher, **without root, a custom launcher, or modifying Samsung system APKs**.

This project was developed through reverse engineering of Samsung Good Lock's **Home Up** module and its exported `ContentProvider`.

## Confirmed result

**Successfully tested:**

| Item                          | Result                       |
| ----------------------------- | ---------------------------- |
| Device                        | Samsung Galaxy S24 Ultra     |
| Launcher                      | Original Samsung One UI Home |
| Target grid                   | **5 columns × 8 rows**       |
| Root required                 | No                           |
| Custom launcher required      | No                           |
| Modified Samsung APK required | No                           |
| Home Up preference write      | Successful                   |
| One UI Home layout update     | Successful                   |
| Original grid backup          | `5x7`                        |

The actual Samsung launcher was visually confirmed to display eight rows of icons.

**Compatibility warning:** This is an experimental technique relying on undocumented Samsung interfaces. It is confirmed for the tested setup, not every Samsung phone or One UI version. Future Samsung updates may change or disable the behavior.

## How it works

The solution requires two distinct operations:

1. Save the new grid value into Home Up's preferences using its exported `ContentProvider`.
2. Notify observers of the home-grid URI so One UI Home can apply the updated configuration.

**Writing the preference alone is not enough.**

During development, Home Up successfully stored and displayed `5x8`, even after a device reboot, while One UI Home continued to display `5x7`. Sending the grid-change notification was the missing step.

### Architecture

```text
Home Up Probe
    |
    | 1. SAVE home_grid = "5x8"
    v
Samsung Home Up ContentProvider
    |
    v
PlugInPropertyDataSource
    |
    v
Home Up SharedPreferences
    |
    | 2. notifyChange(.../home/grid)
    v
Grid-change observers
    |
    v
Samsung One UI Home
    |
    v
5 columns × 8 rows
```

## Requirements

- Samsung device with the original One UI Home launcher
- Samsung Good Lock and Home Up installed
- Android Studio for building the project
- Android SDK and ADB
- A Windows, macOS, or Linux computer for development
- USB or wireless debugging for installation
- No root access

For reverse engineering or further development:

- JADX GUI
- The installed version of Samsung Home Up's APK

## Building the APK

1. Clone or download this repository.
2. Open the Android project in Android Studio.
3. Allow Gradle synchronization to finish.
4. Build the debug APK using **Build → Build APK(s)**.

The output is normally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Alternatively, if the repository includes the Gradle wrapper:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The Gradle commands require a configured Android SDK and a project containing the corresponding wrapper scripts.

### Download from GitHub Actions

Every push to `main` and every pull request builds the APK. Open the repository's Actions tab, pick the run, and download the `home-up-probe-apk` artifact (a zip containing the APK).

Pushing a tag such as `v0.3` also attaches the APK to a GitHub Release for that tag (marked as a prerelease).

The workflow is `.github/workflows/build-apk.yml` and can also be run manually (`workflow_dispatch`).

### Optional: stable signing key

A fixed signing key lets each new build install over the previous one as an update. Generate a keystore and store it as repository secrets:

```bash
keytool -genkeypair -v -keystore release.jks -alias homeup -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks | gh secret set SIGNING_KEYSTORE_BASE64
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS   # value: homeup
gh secret set SIGNING_KEY_PASSWORD
```

All four secrets must be set. Local builds can use the same variables, with `SIGNING_KEYSTORE_PATH` pointing at the `.jks` file instead of the base64 secret: `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

### Without secrets, or if the key is lost

Builds still succeed but are signed with the debug key, which is generated per runner and can change between runs. The run shows a warning.

Installing over a build signed with a different key fails. Uninstalling to fix that deletes the app's saved grid backup, so restore the original grid first (see [Restoring the original grid](#restoring-the-original-grid)).

Back up `release.jks` somewhere safe and never commit it (`*.jks`, `*.keystore`, and `*.apk` are gitignored).

## Installing with ADB

Enable Developer options and USB or wireless debugging on your Samsung device.

Verify the connection:

```powershell
.\adb.exe devices
```

For wireless debugging, connect using the current device address and port:

```powershell
.\adb.exe connect PHONE_IP:PORT
```

Example:

```powershell
.\adb.exe connect 192.168.0.119:40523
```

The IP address and port above are examples. Wireless debugging ports may change between sessions.

Install or update the APK:

```powershell
.\adb.exe -s PHONE_IP:PORT install -r "app\build\outputs\apk\debug\app-debug.apk"
```

If only one device is connected, `-s PHONE_IP:PORT` can be omitted.

## Using Home Up Probe

1. Open Home Up Probe.
2. Tap **Read current grid** to confirm the current Home Up value.
3. Tap **Apply 5×8** and confirm the operation.
4. Verify that the provider reads back `5x8`.
5. Ensure the grid-change notification is sent.
6. Return to the Samsung home screen and visually verify the layout.

A successful provider readback proves that Home Up stored the value. It does **not**, by itself, prove that One UI Home applied it.

### Restoring the original grid

The app saves the original grid value before the first modification.

For example:

```text
Before:              5x7
Requested:           5x8
Provider readback:   5x8
Saved restore value: 5x7
```

**`Saved restore value: 5x7` is intentional.** It is a backup, not an indication that the modification failed.

To restore the original layout, use **Restore saved grid**. The restoration should perform both the preference write and the grid-change notification.

The backup is stored in Home Up Probe's private app data. **Uninstalling the app or clearing its data removes that backup.** Keep a separate record of your original grid configuration.

## Technical details

### Home Up package

```text
com.samsung.android.app.homestar
```

### Exported ContentProvider

```text
content://com.samsung.android.app.homestar.provider
```

Provider class:

```text
com.samsung.android.app.homestar.common.HomestarProvider
```

### Required permission

```xml
<uses-permission
    android:name="com.samsung.android.app.homestar.permission.HOMESTAR_DATA" />
```

The permission was identified as a normal-protection permission in the tested Home Up version.

The helper APK can request it, whereas attempting to invoke the provider directly from the ADB shell encountered a permission error.

### Home-grid property

```text
Key:   home_grid
Type:  GRID
Value: 5x8
```

Home Up stores the property in its private `SharedPreferences` file:

```text
com.samsung.android.app.homestar.v2.preferences
```

The app does not directly edit this private file. It communicates with Home Up through its provider.

## Reading the current grid

The provider supports a `bulk_load` operation.

A request includes:

- An `item_keys` list containing `home_grid`
- A serializable Home Up property object under the `home_grid` key

Conceptually:

```java
Bundle extras = new Bundle();

ArrayList<String> keys = new ArrayList<>();
keys.add("home_grid");

extras.putStringArrayList("item_keys", keys);
extras.putSerializable("home_grid", property);

Bundle response = provider.call(
    "bulk_load",
    null,
    extras
);
```

**Important:** The `property` object must be compatible with Samsung's `V2Plugin.SerializableProperty` interface. It is not an arbitrary Java string.

Home Up Probe uses reflection and the installed Home Up package's class loader to construct compatible property objects.

After setting the correct class loader on the response, the returned property's `getValue()` provides the stored grid string.

## Writing the grid — the case-sensitive SAVE operation

The critical provider call is:

```java
Bundle extras = new Bundle();
extras.setClassLoader(loader);

extras.putSerializable(
    "SerializableProperty",
    property
);

provider.call(
    "property",
    "SAVE",
    extras
);
```

Here, `property` is a compatible serialized property whose key is `home_grid`, type is `GRID`, and value is `"5x8"`.

**The second argument must be uppercase `SAVE`.**

The Home Up APK defines:

```java
private static final String SAVE = "SAVE";
```

An earlier implementation used:

```java
provider.call("property", "save", extras);
```

That did not work.

The provider compares the argument using a case-sensitive string comparison. Lowercase `"save"` does not match `"SAVE"`, causing the request to enter a read-oriented branch rather than the intended save branch.

This was a major debugging breakthrough.

### Why a provider call can appear successful without saving

The provider logs incoming requests, and its property-handling code can return `null` after attempting an operation.

Therefore, receiving no exception from `provider.call()` is not sufficient evidence that the write succeeded.

**Always perform a separate readback after writing.**

## Applying the grid — the critical notification

Even after successfully saving `5x8`, the original Samsung launcher initially continued to display `5x7`.

The missing operation was a change notification for the home-grid property.

The URI is constructed from:

```text
V2Plugin.BASE_URI
    + /home
    + /grid
```

The resulting URI is:

```text
content://com.samsung.android.app.homestar.provider/home/grid
```

Notify the change with:

```java
Uri gridUri = Uri.parse(
    "content://com.samsung.android.app.homestar.provider/home/grid"
);

getContentResolver().notifyChange(
    gridUri,
    null
);
```

### Correct operation sequence

```java
// 1. Save the requested grid.
write("5x8");

// 2. Notify observers that the grid changed.
Uri gridUri = Uri.parse(
    "content://com.samsung.android.app.homestar.provider/home/grid"
);

getContentResolver().notifyChange(gridUri, null);

// 3. Read back the stored preference.
String storedGrid = read();
```

In production-quality code, handle failures and verify that the stored value matches the requested value before reporting success.

The notification is not itself a command to set eight rows. It signals a change to interested observers. The tested Samsung launcher responded by applying the updated configuration.

### Why the notification was missing

Home Up's normal property-saving implementation resembles:

```java
public void save(V2Plugin.BaseProperty property, boolean z) {
    if (!saveSubItems(property)
            || property.getUri().equals(Uri.EMPTY)
            || z) {
        return;
    }

    // Notify change using the property's URI.
}
```

However, `HomestarProvider` reconstructs the incoming property with:

```java
this.uri = Uri.EMPTY;
```

That means saving through the provider updates the preference but skips the usual URI notification.

The helper must send the notification separately.

## Why GRID strings work

Home Up's property validation accepts either a grid object or a string for properties of type `GRID`:

```java
return value instanceof PropertyData.Grid
    || value instanceof String;
```

Its preference-writing implementation also supports string values:

```java
if (value instanceof String) {
    editor.putString(property.getKey(), (String) value);
}
```

This allows the helper to use `"5x8"` as the serialized value without needing to serialize Samsung's full grid object.

## Samsung class loading and serialization

The project uses the installed Home Up package rather than bundling modified Samsung classes.

A compatible class loader can be obtained using:

```java
Context homeUpContext = createPackageContext(
    "com.samsung.android.app.homestar",
    Context.CONTEXT_INCLUDE_CODE
        | Context.CONTEXT_IGNORE_SECURITY
);

ClassLoader loader = homeUpContext.getClassLoader();
```

The project uses reflection to interact with Home Up's internal property types.

During development, serializing a full `HomeGrid` object failed because it contained additional fields, including a non-serializable default grid object.

The working approach used Home Up's lightweight serializable property implementation:

```text
com.samsung.android.app.homestar.v2.service.AbsPlugInService$serializableEncoding$1
```

This class implements Samsung's `V2Plugin.SerializableProperty` interface and can be constructed reflectively from a compatible base property.

The implementation is internal and undocumented, so its name or behavior may change in future Home Up releases.

## Useful ADB debugging commands

All examples below assume Windows PowerShell and `adb.exe` in the current directory.

### List devices

```powershell
.\adb.exe devices
```

### Get the Home Up process ID

```powershell
.\adb.exe -s PHONE_IP:PORT shell pidof com.samsung.android.app.homestar
```

### Clear the log buffer before a test

```powershell
.\adb.exe -s PHONE_IP:PORT logcat -c
```

### Search for provider operations

```powershell
.\adb.exe -s PHONE_IP:PORT logcat -d -v time |
    findstr /i /c:"HomestarProvider" /c:"home_grid"
```

### Search for save failures

```powershell
.\adb.exe -s PHONE_IP:PORT logcat -d -v time |
    findstr /i /c:"property is not correct" /c:"Error on call" /c:"PlugInProperty"
```

### Capture logs for the Home Up process

First retrieve its current PID:

```powershell
.\adb.exe -s PHONE_IP:PORT shell pidof com.samsung.android.app.homestar
```

Then:

```powershell
.\adb.exe -s PHONE_IP:PORT logcat -d -v time --pid=PID
```

Replace `PID` with the returned process ID.

Process IDs and wireless-debugging ports can change. Do not hardcode them.

## Troubleshooting

### Provider readback stays at 5×7

Check that the write uses:

```java
provider.call("property", "SAVE", extras);
```

**Not** lowercase `"save"`.

Also verify that the serialized property has:

```text
Key: home_grid
Type: GRID
Value: 5x8
```

### Provider readback is 5×8, but One UI Home remains 5×7

Verify that the app sends:

```java
getContentResolver().notifyChange(
    Uri.parse(
        "content://com.samsung.android.app.homestar.provider/home/grid"
    ),
    null
);
```

This exact issue occurred during development. The preference survived a reboot, but the launcher did not update until the change notification was sent.

### Home Up shows 5×8, but Samsung's native grid settings show 5×7

These interfaces can temporarily disagree because storing the Home Up preference and applying the setting to the launcher are separate operations.

Check the actual home-screen layout after the notification.

### Saved restore value remains 5×7

This is expected.

It represents the original configuration saved for rollback, not the current configuration.

### Provider permission denied

Verify that the manifest requests:

```text
com.samsung.android.app.homestar.permission.HOMESTAR_DATA
```

Also verify that the installed Home Up version still exports the provider and uses compatible permissions.

### A Samsung update breaks the app

Use JADX to recheck:

- Provider authority and exported status
- Required permission
- `SAVE` operation name
- Serializable property interface
- Internal serializable-property implementation
- Home-grid property key
- Grid notification URI
- Launcher integration behavior

Do not assume undocumented Samsung APIs remain stable across versions.

## Reverse-engineering notes

The relevant components discovered during investigation were:

| Component                                 | Purpose                                          |
| ----------------------------------------- | ------------------------------------------------ |
| `HomestarProvider`                        | Receives property read/write requests            |
| `PlugInPropertyDataSource`                | Reads and writes Home Up preferences             |
| `V2Plugin.SerializableProperty`           | Serialized property interface                    |
| `V2Plugin.BaseProperty`                   | Base property implementation                     |
| `HomePlugin.Property.HomeGrid`            | Home-screen grid property                        |
| `AbsPlugInService$serializableEncoding$1` | Lightweight serializable property implementation |

The save path was traced as:

```text
HomestarProvider.call()
    ↓
PlugInPropertyOperator.DefaultImpls.save$default()
    ↓
PlugInPropertyDataSource.save()
    ↓
saveSubItems()
    ↓
saveToPrefs()
    ↓
SharedPreferences.Editor.putString()
    ↓
SharedPreferences.Editor.apply()
```

The read path was traced as:

```text
HomestarProvider.bulkLoad()
    ↓
PlugInPropertyDataSource.get(property, true)
    ↓
SharedPreferences.getString("home_grid", ...)
    ↓
SerializableProperty.setValue(...)
```

The key insight is that the preference write and the launcher notification are **separate operations**.

## Safety and limitations

- This project uses undocumented Samsung interfaces.
- The behavior is device- and software-version-dependent.
- Grid changes may rearrange icons or widgets.
- Back up your home-screen arrangement before experimenting.
- Do not clear One UI Home's data as a troubleshooting step; that can remove your launcher layout.
- Avoid uninstalling Home Up Probe before restoring the original grid if you rely on its saved backup.
- Keep the original grid value recorded separately.
- A successful provider write does not guarantee that every requested grid dimension is supported.
- Do not assume the same result on other Samsung devices.

This project does not require root or modification of One UI Home's APK.

## Acknowledgments

Built through iterative experimentation, JADX analysis, Android reflection, ADB debugging, and testing against Samsung Home Up and the original One UI Home launcher.

The most important discoveries were:

1. Home Up exposes a usable property provider.
2. The save operation is case-sensitive: `"SAVE"`.
3. `home_grid` accepts a serialized string such as `"5x8"`.
4. Saving through the provider does not automatically notify the launcher.
5. Notifying `/home/grid` allows the tested One UI Home installation to apply the new layout.

## Disclaimer

This is an independent experimental project and is not affiliated with, endorsed by, or supported by Samsung.

Samsung, Galaxy, One UI, Good Lock, and Home Up are trademarks of their respective owners.

Use at your own risk.
