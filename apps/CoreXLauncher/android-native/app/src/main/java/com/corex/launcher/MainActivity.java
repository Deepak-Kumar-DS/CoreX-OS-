package com.corex.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Handler;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(8, 11, 18);
    private final int CARD = Color.rgb(20, 27, 40);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(160, 170, 185);
    private final int ACCENT = Color.rgb(118, 87, 255);

    private TextView clock;
    private TextView dayDate;
    private TextView battery;
    private GridLayout appGrid;
    private EditText searchInput;

    private final Handler handler = new Handler();

    private final String[] appNames = {
            "Phone",
            "Messages",
            "Camera",
            "Gallery",
            "Settings",
            "Files",
            "Browser",
            "Falcon",
            "CoreX AI",
            "CoreX Pulse"
    };

    private final String[] appIcons = {
            "☎",
            "✉",
            "◎",
            "▣",
            "⚙",
            "▤",
            "◉",
            "➤",
            "✦",
            "◌"
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateInformation();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        buildHomeScreen();
        handler.post(clockRunnable);
    }

    private void buildHomeScreen() {

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(15), dp(22), dp(12));

        scroll.addView(root);

        // TOP STATUS INFORMATION

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);

        clock = new TextView(this);
        clock.setTextColor(WHITE);
        clock.setTextSize(14);
        clock.setTypeface(null, Typeface.BOLD);

        battery = new TextView(this);
        battery.setTextColor(MUTED);
        battery.setTextSize(12);
        battery.setGravity(Gravity.END);

        status.addView(clock,
                new LinearLayout.LayoutParams(0, -2, 1));

        status.addView(battery);

        root.addView(status);

        dayDate = new TextView(this);
        dayDate.setTextColor(MUTED);
        dayDate.setTextSize(12);
        dayDate.setPadding(0, dp(5), 0, 0);

        root.addView(dayDate);

        // COREX BRANDING

        TextView title = new TextView(this);
        title.setText("CoreX");
        title.setTextColor(WHITE);
        title.setTextSize(42);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(35), 0, 0);

        root.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText("Beyond the Core");
        tagline.setTextColor(ACCENT);
        tagline.setTextSize(14);
        tagline.setPadding(0, dp(3), 0, dp(25));

        root.addView(tagline);

        // SEARCH BAR

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint("⌕   Search applications...");
        searchInput.setPadding(dp(16), dp(13), dp(16), dp(13));
        searchInput.setBackground(rounded(CARD, 14));

        root.addView(searchInput,
                new LinearLayout.LayoutParams(-1, dp(50)));

        // APPLICATION HEADING

        TextView heading = new TextView(this);
        heading.setText("YOUR APPLICATIONS");
        heading.setTextColor(MUTED);
        heading.setTextSize(11);
        heading.setLetterSpacing(0.12f);
        heading.setPadding(0, dp(30), 0, dp(15));

        root.addView(heading);

        // APPLICATION GRID

        appGrid = new GridLayout(this);
        appGrid.setColumnCount(3);
        appGrid.setUseDefaultMargins(false);

        root.addView(appGrid);

        displayApps("");

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                displayApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // FOOTER

        TextView footer = new TextView(this);
        footer.setText(
                "COREX OS  •  BEYOND THE CORE\n\n" +
                "Developed by Deepak Kumar Sharma"
        );

        footer.setTextColor(MUTED);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(30), 0, dp(15));

        root.addView(footer);

        setContentView(scroll);
        updateInformation();
    }

    // DISPLAY APPLICATIONS

    private void displayApps(String query) {

        appGrid.removeAllViews();

        int count = 0;

        for (int i = 0; i < appNames.length; i++) {

            final String name = appNames[i];

            if (!name.toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT))) {
                continue;
            }

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(3), dp(12), dp(3), dp(16));
            item.setClickable(true);
            item.setFocusable(true);

            TextView icon = new TextView(this);
            icon.setText(appIcons[i]);
            icon.setTextColor(WHITE);
            icon.setTextSize(25);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(rounded(CARD, 18));

            item.addView(icon,
                    new LinearLayout.LayoutParams(dp(62), dp(62)));

            TextView label = new TextView(this);
            label.setText(name);
            label.setTextColor(WHITE);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(9), 0, 0);

            item.addView(label);

            item.setOnClickListener(v -> openApp(name));

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width = 0;
            params.columnSpec =
                    GridLayout.spec(GridLayout.UNDEFINED, 1f);

            appGrid.addView(item, params);
            count++;
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("No applications found");
            empty.setTextColor(MUTED);
            empty.setTextSize(13);
            empty.setPadding(0, dp(20), 0, dp(20));
            appGrid.addView(empty);
        }
    }

    // OPEN APPLICATIONS

    private void openApp(String name) {

        Intent intent;

        try {

            switch (name) {

                case "Phone":
                    intent = new Intent(Intent.ACTION_DIAL);
                    break;

                case "Messages":
                    intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("smsto:"));
                    break;

                case "Camera":
                    intent = new Intent(
                            android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    break;

                case "Gallery":
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent = Intent.createChooser(intent, "Gallery");
                    break;

                case "Settings":
                    intent = new Intent(
                            android.provider.Settings.ACTION_SETTINGS);
                    break;

                case "Files":
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent = Intent.createChooser(intent, "Files");
                    break;

                case "Browser":
                    intent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com"));
                    break;

                case "Falcon":
                    intent = new Intent(
                            MainActivity.this,
                            com.corex.falcon.MainActivity.class);
                    break;

                case "CoreX AI":
                    openCoreXAI();
                    return;

                case "CoreX Pulse":
                    intent = new Intent(
                            MainActivity.this,
                            com.corex.pulse.PulseActivity.class);
                    break;

                default:
                    return;
            }

            startActivity(intent);

        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    name + " is not available on this device",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Unable to open " + name,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // COREX AI

    private void openCoreXAI() {

        EditText input = new EditText(this);
        input.setHint("Ask CoreX AI...");
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(CARD, 12));

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("CoreX AI")
                .setMessage("Your intelligent assistant")
                .setView(layout)
                .setNegativeButton("Close", null)
                .setPositiveButton("Ask", (dialog, which) -> {

                    String question =
                            input.getText().toString().trim();

                    if (question.isEmpty()) return;

                    String answer;

                    if (question.toLowerCase().contains("time")) {
                        answer = new SimpleDateFormat(
                                "hh:mm a", Locale.getDefault())
                                .format(new Date());

                    } else if (question.toLowerCase().contains("date")) {
                        answer = new SimpleDateFormat(
                                "EEEE, dd MMMM yyyy",
                                Locale.getDefault()).format(new Date());

                    } else if (question.toLowerCase().contains("hello")) {
                        answer = "Hello! Welcome to CoreX.";

                    } else {
                        answer = "CoreX AI assistant is currently running in local mode.";
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("CoreX AI")
                            .setMessage(answer)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .show();
    }

    // LIVE CLOCK AND BATTERY

    private void updateInformation() {

        Date now = new Date();

        clock.setText(
                new SimpleDateFormat(
                        "hh:mm:ss a", Locale.getDefault())
                        .format(now));

        dayDate.setText(
                new SimpleDateFormat(
                        "EEEE  •  dd MMMM yyyy",
                        Locale.getDefault())
                        .format(now));

        Intent batteryIntent = registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryIntent != null) {

            int level = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_LEVEL, -1);

            int scale = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_SCALE, -1);

            int status = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_STATUS, -1);

            int percentage = (level >= 0 && scale > 0)
                    ? (level * 100 / scale) : 0;

            boolean charging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            battery.setText(
                    "▣ " + percentage + "%" +
                    (charging ? "  ⚡ Charging" : ""));
        }
    }

    // DESIGN HELPERS

    private GradientDrawable rounded(int color, int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockRunnable);
        super.onDestroy();
    }
}