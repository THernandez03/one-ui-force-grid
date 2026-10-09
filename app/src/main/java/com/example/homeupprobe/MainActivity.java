
package com.example.homeupprobe;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String PERM =
            "com.samsung.android.app.homestar.permission.HOMESTAR_DATA";

    private static final String HOME_UP =
            "com.samsung.android.app.homestar";

    private static final Uri URI = Uri.parse(
            "content://com.samsung.android.app.homestar.provider"
    );

    private TextView output;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int pad = (int) (
                20 * getResources().getDisplayMetrics().density
        );

        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Home Up grid reader (read-only)");
        title.setTextSize(20);
        root.addView(title);

        Button test = new Button(this);
        test.setText("Read current grid");
        root.addView(test);

        output = new TextView(this);
        output.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        test.setOnClickListener(v -> new Thread(() -> {

            StringBuilder result = new StringBuilder();

            try {

                // STEP 1: Verify permission
                boolean granted =
                        checkSelfPermission(PERM)
                                == PackageManager.PERMISSION_GRANTED;

                result.append("Permission granted: ")
                        .append(granted)
                        .append('\n');

                if (!granted) {
                    result.append("STOP: Required permission missing\n");
                    showResult(result);
                    return;
                }

                // STEP 2: Load Samsung classes from installed Home Up
                Context samsungContext = createPackageContext(
                        HOME_UP,
                        Context.CONTEXT_INCLUDE_CODE
                                | Context.CONTEXT_IGNORE_SECURITY
                );

                ClassLoader samsungLoader =
                        samsungContext.getClassLoader();

                result.append("Samsung class loader: ")
                        .append(samsungLoader)
                        .append('\n');

                // STEP 3: Instantiate Samsung's HomeGrid property
                Class<?> gridClass = Class.forName(
                        "com.sec.android.app.launcher.plugins.v2."
                                + "HomePlugin$Property$HomeGrid",
                        true,
                        samsungLoader
                );

                Object gridProperty =
                        gridClass.getDeclaredConstructor().newInstance();


                result.append("\n--- Samsung serialization class ---\n");

                String className =
                        "com.samsung.android.app.homestar.v2.service."
                                + "AbsPlugInService$serializableEncoding$1";

                try {
                    Class<?> serialClass = Class.forName(
                            className,
                            false,
                            samsungLoader
                    );

                    result.append("Found: ")
                            .append(serialClass.getName())
                            .append('\n');

                    for (java.lang.reflect.Constructor<?> constructor :
                            serialClass.getDeclaredConstructors()) {

                        result.append("Constructor: ")
                                .append(constructor.toGenericString())
                                .append('\n');
                    }

                    for (java.lang.reflect.Field field :
                            serialClass.getDeclaredFields()) {

                        result.append("Field: ")
                                .append(field.getName())
                                .append(" : ")
                                .append(field.getType().getName())
                                .append('\n');
                    }

                } catch (ClassNotFoundException e) {
                    result.append("Class not found: ")
                            .append(className)
                            .append('\n');
                }

                result.append("--- End serialization inspection ---\n\n");

                result.append("Property class: ")
                        .append(gridProperty.getClass().getName())
                        .append('\n');

                // STEP 4: Obtain the property key
                String key = String.valueOf(
                        gridClass.getMethod("getKey")
                                .invoke(gridProperty)
                );

                result.append("Property key: ")
                        .append(key)
                        .append('\n');

                // STEP 5: Encode the property

                // STEP 5: Create Samsung's lightweight SerializableProperty
                Class<?> basePropertyClass = Class.forName(
                        "com.sec.android.app.launcher.plugins.v2.V2Plugin$BaseProperty",
                        false,
                        samsungLoader
                );

                Class<?> serialClass = Class.forName(
                        "com.samsung.android.app.homestar.v2.service."
                                + "AbsPlugInService$serializableEncoding$1",
                        true,
                        samsungLoader
                );

                java.lang.reflect.Constructor<?> constructor =
                        serialClass.getDeclaredConstructor(basePropertyClass);

                constructor.setAccessible(true);

                Object encodedProperty = constructor.newInstance(gridProperty);

                // Convert the Grid value into a String.
                Method encoding = serialClass.getMethod("encoding");
                encodedProperty = encoding.invoke(encodedProperty);

                result.append("Encoded property class: ")
                        .append(encodedProperty.getClass().getName())
                        .append('\n');

                Method getValue = encodedProperty.getClass()
                        .getMethod("getValue");

                Object encodedValue = getValue.invoke(encodedProperty);

                result.append("Encoded value: ")
                        .append(String.valueOf(encodedValue))
                        .append('\n');

                result.append("Encoded value type: ")
                        .append(encodedValue == null
                                ? "null"
                                : encodedValue.getClass().getName())
                        .append('\n');

                if (!(encodedProperty instanceof Serializable)) {
                    result.append("ERROR: Property is not Serializable\n");
                    showResult(result);
                    return;
                }

                // Verify serialization locally before IPC.
                try {
                    java.io.ByteArrayOutputStream bytes =
                            new java.io.ByteArrayOutputStream();

                    java.io.ObjectOutputStream stream =
                            new java.io.ObjectOutputStream(bytes);

                    stream.writeObject(encodedProperty);
                    stream.close();

                    result.append("Local serialization: SUCCESS (")
                            .append(bytes.size())
                            .append(" bytes)\n");

                } catch (Exception e) {
                    result.append("Local serialization: FAILED\n")
                            .append(e)
                            .append('\n');
                    showResult(result);
                    return;
                }

                // STEP 8: Prepare read-only bulk_load request
                Bundle extras = new Bundle();

                extras.setClassLoader(samsungLoader);

                ArrayList<String> keys = new ArrayList<>();
                keys.add(key);

                extras.putStringArrayList("item_keys", keys);

                // IMPORTANT:
                // Send the ENCODED property, not the original.
                extras.putSerializable(
                        key,
                        (Serializable) encodedProperty
                );

                result.append("Sending bulk_load request...\n");

                // STEP 9: Call Samsung Home Up provider
                try (ContentProviderClient provider =
                             getContentResolver()
                                     .acquireContentProviderClient(URI)) {

                    if (provider == null) {

                        result.append(
                                "ERROR: Provider unavailable\n"
                        );

                    } else {

                        result.append("Provider acquired successfully\n");

                        Bundle response = provider.call(
                                "bulk_load",
                                null,
                                extras
                        );

                        if (response == null) {

                            result.append(
                                    "ERROR: Provider returned null\n"
                            );

                        } else {

                            response.setClassLoader(samsungLoader);

                            result.append("Response keys: ")
                                    .append(response.keySet())
                                    .append('\n');

                            // STEP 10: Read returned property
                            Serializable returned =
                                    response.getSerializable(key);

                            if (returned == null) {

                                result.append(
                                        "No grid property returned\n"
                                );

                            } else {

                                result.append("Returned class: ")
                                        .append(returned.getClass().getName())
                                        .append('\n');

                                Method returnedGetValue =
                                        returned.getClass()
                                                .getMethod("getValue");

                                Object value =
                                        returnedGetValue.invoke(returned);

                                result.append("Current grid value: ")
                                        .append(String.valueOf(value))
                                        .append('\n');

                                result.append("Current value type: ")
                                        .append(
                                                value == null
                                                        ? "null"
                                                        : value.getClass()
                                                        .getName()
                                        )
                                        .append('\n');
                            }
                        }
                    }
                }

            } catch (Throwable e) {

                result.append("ERROR: ")
                        .append(e)
                        .append('\n');

                Throwable cause = e.getCause();

                if (cause != null) {
                    result.append("CAUSE: ")
                            .append(cause)
                            .append('\n');
                }

                for (StackTraceElement frame : e.getStackTrace()) {

                    result.append("  at ")
                            .append(frame)
                            .append('\n');

                    if (result.length() > 6500) {
                        break;
                    }
                }
            }

            showResult(result);

        }).start());
    }

    private void showResult(StringBuilder result) {
        runOnUiThread(() ->
                output.setText(result.toString())
        );
    }
}
