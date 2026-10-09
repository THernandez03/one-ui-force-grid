package com.example.homeupprobe;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.ContentProviderClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

public class MainActivity extends Activity {
    private static final String PERM = "com.samsung.android.app.homestar.permission.HOMESTAR_DATA";
    private static final Uri URI = Uri.parse("content://com.samsung.android.app.homestar.provider");
    private TextView output;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("Home Up provider diagnostic (read-only)");
        title.setTextSize(20);
        root.addView(title);
        Button test = new Button(this);
        test.setText("Test permission and provider");
        root.addView(test);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        root.addView(output, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        test.setOnClickListener(v -> new Thread(() -> {
            StringBuilder result = new StringBuilder();
            boolean granted = checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
            result.append("Permission granted: ").append(granted).append('\n');
            try (ContentProviderClient provider = getContentResolver().acquireContentProviderClient(URI)) {
                if (provider == null) {
                    result.append("Provider unavailable or not visible");
                } else {
                    Bundle extras = new Bundle();
                    extras.putStringArrayList(
                            "item_keys",
                            new java.util.ArrayList<String>()
                    );

                    Bundle response = provider.call("bulk_load", null, extras);

                    result.append("bulk_load response: ")
                            .append(response == null ? "null" : response.toString());
                }
            } catch (Exception e) {
                result.append("Provider call failed: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
            }
            runOnUiThread(() -> output.setText(result.toString()));
        }).start());
    }
}
