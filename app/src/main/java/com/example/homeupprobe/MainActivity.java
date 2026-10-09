package com.example.homeupprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String PERM = "com.samsung.android.app.homestar.permission.HOMESTAR_DATA";
    private static final String HOME_UP = "com.samsung.android.app.homestar";
    private static final String KEY = "home_grid";
    private static final String BACKUP = "original_home_grid";
    private static final Uri URI = Uri.parse("content://com.samsung.android.app.homestar.provider");
    private TextView output;
    private Button applyButton;
    private Button restoreButton;
    private boolean busy;

    void notifyHomeGridChanged() {
        Uri gridUri = Uri.parse(
                "content://com.samsung.android.app.homestar.provider/home/grid"
        );

        getContentResolver().notifyChange(gridUri, null);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("Home Up grid experiment (no root)");
        title.setTextSize(20);
        root.addView(title);
        Button readButton = new Button(this);
        readButton.setText("Read current grid");
        root.addView(readButton);
        applyButton = new Button(this);
        applyButton.setText("Apply 5x8 (experimental)");
        root.addView(applyButton);
        restoreButton = new Button(this);
        restoreButton.setText("Restore saved grid");
        root.addView(restoreButton);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        show("No changes made. Tap Read current grid first.\nSaved backup: " + backupLabel());
        readButton.setOnClickListener(v -> runTask(() -> {
            Bridge bridge = new Bridge();
            return "Provider grid: " + bridge.read() + "\nSaved backup: " + backupLabel();
        }));
        applyButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Attempt 5x8?")
                .setMessage("This writes to Samsung Home Up's grid preference. The launcher may ignore, clamp, or rearrange icons. Your current value will be backed up before writing. Consider screenshots of your home screens first. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply 5x8", (dialog, which) -> runTask(() -> {
                    Bridge bridge = new Bridge();
                    String before = bridge.read();
                    if (!validGrid(before)) throw new IllegalStateException("Unrecognized current grid: " + before);
                    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                    // Never overwrite an earlier restore point with an experimental value.
                    if (!prefs.contains(BACKUP)) {
                        if (!prefs.edit().putString(BACKUP, before).commit())
                            throw new IllegalStateException("Could not persist restore value; write cancelled");
                    }
                    if (!validGrid(prefs.getString(BACKUP, "")))
                        throw new IllegalStateException("Stored restore value is invalid; write cancelled");
                    bridge.write("5x8");
                    notifyHomeGridChanged();
                    String after = bridge.read();
                    return "Before: " + before + "\nRequested: 5x8\nProvider readback: " + after
                            + "\nSaved restore value: " + backupLabel()
                            + "\n\nCheck your home screen. A successful provider readback does NOT prove the launcher applied eight rows.";
                }))
                .show());
        restoreButton.setOnClickListener(v -> {
            String original = getPreferences(MODE_PRIVATE).getString(BACKUP, null);
            if (!validGrid(original)) {
                show("No valid saved backup. Nothing changed.\nUse Read current grid to inspect Home Up.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Restore " + original + "?")
                    .setMessage("Send the saved grid value back to Samsung Home Up? The backup remains saved afterward.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (dialog, which) -> runTask(() -> {
                        Bridge bridge = new Bridge();
                        bridge.write(original);
                        return "Restore requested: " + original + "\nProvider readback: " + bridge.read()
                                + "\nSaved backup retained: " + backupLabel();
                    })).show();
        });
    }

    private interface Work { String execute() throws Exception; }
    private void runTask(Work work) {
        if (busy) return;
        busy = true;
        applyButton.setEnabled(false);
        restoreButton.setEnabled(false);
        show("Working...");
        new Thread(() -> {
            String message;
            try {
                if (checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("HOMESTAR_DATA permission not granted");
                message = work.execute();
            } catch (Throwable e) {
                StringBuilder sb = new StringBuilder("ERROR: ").append(e).append('\n');
                for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause())
                    sb.append("CAUSE: ").append(cause).append('\n');
                for (StackTraceElement frame : e.getStackTrace()) {
                    sb.append("  at ").append(frame).append('\n');
                    if (sb.length() > 4500) break;
                }
                message = sb.toString() + "\nSaved backup: " + backupLabel();
            }
            final String text = message;
            runOnUiThread(() -> {
                show(text);
                busy = false;
                applyButton.setEnabled(true);
                restoreButton.setEnabled(true);
            });
        }).start();
    }
    private void show(String message) { output.setText(message); }
    private String backupLabel() {
        return getPreferences(MODE_PRIVATE).getString(BACKUP, "none");
    }
    private static boolean validGrid(String value) {
        return value != null && value.matches("[1-9][0-9]?x[1-9][0-9]?");
    }

    private final class Bridge {
        final ClassLoader loader;
        final Object gridProperty;
        final Class<?> serialClass;
        final Constructor<?> serialConstructor;
        final Method encoding;
        final Method setValue;
        final Method getValue;

        Bridge() throws Exception {
            Context samsung = createPackageContext(HOME_UP,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            loader = samsung.getClassLoader();
            Class<?> gridClass = Class.forName(
                    "com.sec.android.app.launcher.plugins.v2.HomePlugin$Property$HomeGrid", true, loader);
            gridProperty = gridClass.getDeclaredConstructor().newInstance();
            Class<?> baseProperty = Class.forName(
                    "com.sec.android.app.launcher.plugins.v2.V2Plugin$BaseProperty", false, loader);
            serialClass = Class.forName(
                    "com.samsung.android.app.homestar.v2.service.AbsPlugInService$serializableEncoding$1",
                    true, loader);
            serialConstructor = serialClass.getDeclaredConstructor(baseProperty);
            serialConstructor.setAccessible(true);
            encoding = serialClass.getMethod("encoding");
            setValue = serialClass.getMethod("setValue", Object.class);
            getValue = serialClass.getMethod("getValue");
        }

        private Serializable property(String requestedValue) throws Exception {
            Object serial = serialConstructor.newInstance(gridProperty);
            if (requestedValue != null) setValue.invoke(serial, requestedValue);
            serial = encoding.invoke(serial);
            if (!(serial instanceof Serializable)) throw new IllegalStateException("Not Serializable");
            return (Serializable) serial;
        }

        String read() throws Exception {
            Bundle extras = new Bundle();
            extras.setClassLoader(loader);
            ArrayList<String> keys = new ArrayList<>();
            keys.add(KEY);
            extras.putStringArrayList("item_keys", keys);
            extras.putSerializable(KEY, property(null));
            try (ContentProviderClient provider = getContentResolver().acquireContentProviderClient(URI)) {
                if (provider == null) throw new IllegalStateException("Home Up provider unavailable");
                Bundle response = provider.call("bulk_load", null, extras);
                if (response == null) throw new IllegalStateException("bulk_load returned null");
                response.setClassLoader(loader);
                Serializable returned = response.getSerializable(KEY);
                if (returned == null) throw new IllegalStateException("No home_grid in response");
                Object value = getValue.invoke(returned);
                return String.valueOf(value);
            }
        }

        void write(String grid) throws Exception {
            if (!validGrid(grid)) throw new IllegalArgumentException("Invalid grid: " + grid);
            Bundle extras = new Bundle();
            extras.setClassLoader(loader);
            extras.putSerializable("SerializableProperty", property(grid));
            try (ContentProviderClient provider = getContentResolver().acquireContentProviderClient(URI)) {
                if (provider == null) throw new IllegalStateException("Home Up provider unavailable");
                // HomestarProvider.call expects the Kotlin interface simple name as Bundle key.
                provider.call("property", "SAVE", extras);
            }
        }
    }
}
