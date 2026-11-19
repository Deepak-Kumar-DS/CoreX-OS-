package com.corex.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BLUE = Color.rgb(38, 110, 180);
    private final int BACKGROUND = Color.rgb(246, 247, 249);
    private final int WHITE = Color.WHITE;
    private final int BLACK = Color.rgb(35, 35, 35);
    private final int GRAY = Color.rgb(115, 115, 115);

    private TextView timeText;
    private TextView dayText;
    private TextView dateText;
    private TextView batteryText;
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
            "CoreX AI"
    };

    private final String[] appIcons = {
            "☎", "✉", "📷",
            "▣", "⚙", "▤",
            "🌐", "➤", "✦"
    };

    private final int[] iconColors = {
            0xFF4CAF50,
            0xFF2196F3,
            0xFF607D8B,
            0xFF9C27B0,
            0xFF78909C,
            0xFFFF9800,
            0xFF03A9F4,
            0xFF455A64,
            0xFF3F51B5
    };

    private final Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateInformation();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BLUE);
        getWindow().setNavigationBarColor(Color.BLACK);

        buildHomeScreen();
        updateInformation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(clockUpdater);
        handler.post(clockUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockUpdater);
    }

    private void buildHomeScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        // HEADER

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(15), dp(20), dp(18));
        header.setBackgroundColor(WHITE);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);

        timeText = new TextView(this);
        timeText.setTextColor(BLACK);
        timeText.setTextSize(30);
        timeText.setTypeface(null, Typeface.BOLD);

        dayText = new TextView(this);
        dayText.setTextColor(GRAY);
        dayText.setTextSize(13);

        clockBox.addView(timeText);
        clockBox.addView(dayText);

        topRow.addView(clockBox,
                new LinearLayout.LayoutParams(0, -2, 1));

        batteryText = new TextView(this);
        batteryText.setTextColor(BLACK);
        batteryText.setTextSize(13);
        batteryText.setGravity(Gravity.CENTER_VERTICAL);

        topRow.addView(batteryText);

        header.addView(topRow);

        dateText = new TextView(this);
        dateText.setTextColor(GRAY);
        dateText.setTextSize(13);
        dateText.setPadding(0, dp(8), 0, 0);

        header.addView(dateText);

        TextView title = new TextView(this);
        title.setText("CoreX");
        title.setTextColor(BLACK);
        title.setTextSize(27);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(20), 0, 0);

        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Beyond the Core");
        subtitle.setTextColor(GRAY);
        subtitle.setTextSize(13);

        header.addView(subtitle);

        root.addView(header);

        // SEARCH

        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setPadding(
                dp(15), dp(12), dp(15), dp(5));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setHint("Search applications...");
        searchInput.setPadding(
                dp(14), dp(10), dp(14), dp(10));
        searchInput.setBackground(roundRect(WHITE, 8));
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        searchContainer.addView(searchInput,
                new LinearLayout.LayoutParams(-1, dp(46)));

        root.addView(searchContainer);

        // APPLICATIONS

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(12));

        TextView heading = new TextView(this);
        heading.setText("Applications");
        heading.setTextColor(BLACK);
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(5), dp(10), 0, dp(12));

        content.addView(heading);

        appGrid = new GridLayout(this);
        appGrid.setColumnCount(3);
        appGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        content.addView(appGrid);
        scroll.addView(content);

        root.addView(scroll,
                new LinearLayout.LayoutParams(0, 0, 1));

        // FOOTER

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE0E0E0);

        root.addView(divider,
                new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(9), dp(8), dp(9));
        footer.setBackgroundColor(WHITE);

        TextView credit = new TextView(this);
        credit.setText(
                "CoreX OS  |  Genesis 1.0\n" +
                "Developed by Deepak Kumar Sharma"
        );
        credit.setTextColor(GRAY);
        credit.setTextSize(11);
        credit.setGravity(Gravity.CENTER);

        footer.addView(credit);
        root.addView(footer);

        setContentView(root);

        renderApps("");

        searchInput.addTextChangedListener(
                new android.text.TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start,
                            int count, int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s, int start,
                            int before, int count) {
                        renderApps(s.toString());
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s) {
                    }
                }
        );
    }

    // APPLICATION GRID

    private void renderApps(String filter) {

        appGrid.removeAllViews();

        for (int i = 0; i < appNames.length; i++) {

            final String name = appNames[i];

            if (!name.toLowerCase(Locale.ROOT)
                    .contains(filter.toLowerCase(Locale.ROOT))) {
                continue;
            }

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5), dp(12), dp(5), dp(12));

            TextView icon = new TextView(this);
            icon.setText(appIcons[i]);
            icon.setTextColor(WHITE);
            icon.setTextSize(25);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(roundRect(iconColors[i], 12));

            item.addView(icon,
                    new LinearLayout.LayoutParams(dp(58), dp(58)));

            TextView label = new TextView(this);
            label.setText(name);
            label.setTextColor(BLACK);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(7), 0, 0);

            item.addView(label);

            item.setOnClickListener(v -> openApp(name));

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width = 0;
            params.height = -2;
            params.columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED, 1f);

            appGrid.addView(item, params);
        }
    }

    // OPEN APPLICATIONS

    private void openApp(String name) {

        try {

            Intent intent;

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
                    intent = Intent.createChooser(intent, "Choose Image");
                    break;

                case "Settings":
                    intent = new Intent(
                            android.provider.Settings.ACTION_SETTINGS);
                    break;

                case "Files":
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent = Intent.createChooser(intent, "Choose File");
                    break;

                case "Browser":
                    openBrowser();
                    return;

                case "Falcon":
                    openFalcon();
                    return;

                case "CoreX AI":
                    openCoreXAI();
                    return;

                default:
                    return;
            }

            startActivity(intent);

        } catch (ActivityNotFoundException e) {

            Toast.makeText(this,
                    name + " is not available",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {

            Toast.makeText(this,
                    "Unable to open " + name,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // FALCON - SAME APK

    private void openFalcon() {

        try {
            Intent intent = new Intent(
                    MainActivity.this,
                    com.corex.falcon.MainActivity.class
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(this,
                    "Unable to open Falcon",
                    Toast.LENGTH_LONG).show();
        }
    }

    // BROWSER

    private void openBrowser() {

        try {

            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com"));

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(this,
                    "No browser available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // COREX AI

    private void openCoreXAI() {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(5));

        TextView description = new TextView(this);
        description.setText(
                "Hello! I am CoreX AI.\n\n" +
                "You can type a command or use voice input."
        );
        description.setTextColor(BLACK);
        description.setTextSize(14);

        box.addView(description);

        EditText commandInput = new EditText(this);
        commandInput.setHint("Enter your command...");
        commandInput.setSingleLine(true);

        box.addView(commandInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CoreX AI")
                .setView(box)
                .setPositiveButton("Run", null)
                .setNeutralButton("Voice", null)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(v -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(button -> {

                        String command = commandInput
                                .getText().toString().trim();

                        if (!command.isEmpty()) {
                            dialog.dismiss();
                            executeAICommand(command);
                        }
                    });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(button -> {

                        dialog.dismiss();
                        startVoiceRecognition();
                    });
        });

        dialog.show();
    }

    // VOICE INPUT

    private void startVoiceRecognition() {

        try {

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault());

            intent.putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak your command");

            startActivityForResult(intent, 101);

        } catch (Exception e) {

            Toast.makeText(this,
                    "Voice recognition is not available",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101 &&
                resultCode == RESULT_OK &&
                data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {
                executeAICommand(results.get(0));
            }
        }
    }

    // AI COMMANDS

    private void executeAICommand(String command) {

        String c = command.toLowerCase(Locale.ROOT);

        if (c.contains("setting")) {
            openApp("Settings");

        } else if (c.contains("phone") || c.contains("call")) {
            openApp("Phone");

        } else if (c.contains("message") || c.contains("sms")) {
            openApp("Messages");

        } else if (c.contains("camera")) {
            openApp("Camera");

        } else if (c.contains("gallery") || c.contains("photo")) {
            openApp("Gallery");

        } else if (c.contains("file")) {
            openApp("Files");

        } else if (c.contains("browser") || c.contains("internet")) {
            openApp("Browser");

        } else if (c.contains("falcon")) {
            openApp("Falcon");

        } else if (c.contains("time") || c.contains("samay")) {

            String time = new SimpleDateFormat(
                    "hh:mm a", Locale.getDefault())
                    .format(new Date());

            showAIResult("Current time: " + time);

        } else if (c.contains("date") || c.contains("tarikh")) {

            String date = new SimpleDateFormat(
                    "dd MMMM yyyy", Locale.getDefault())
                    .format(new Date());

            showAIResult("Today's date: " + date);

        } else if (c.contains("battery")) {

            updateInformation();
            showAIResult("Battery: " + batteryText.getText());

        } else {

            showAIResult(
                    "I could not understand this command.\n\n" +
                    "Try: Open Settings, Open Phone, " +
                    "Open Camera or What is the time?\n\n" +
                    "Online AI integration is not available yet."
            );
        }
    }

    private void showAIResult(String message) {

        new AlertDialog.Builder(this)
                .setTitle("CoreX AI")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    // LIVE TIME, DATE AND BATTERY

    private void updateInformation() {

        Date now = new Date();

        if (timeText != null) {
            timeText.setText(
                    new SimpleDateFormat(
                            "hh:mm a",
                            Locale.getDefault()).format(now)
            );
        }

        if (dayText != null) {
            dayText.setText(
                    new SimpleDateFormat(
                            "EEEE",
                            Locale.getDefault()).format(now)
            );
        }

        if (dateText != null) {
            dateText.setText(
                    new SimpleDateFormat(
                            "dd MMMM yyyy",
                            Locale.getDefault()).format(now)
            );
        }

        Intent status = registerReceiver(
                null,
                new android.content.IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED)
        );

        if (status != null && batteryText != null) {

            int level = status.getIntExtra(
                    BatteryManager.EXTRA_LEVEL, -1);

            int scale = status.getIntExtra(
                    BatteryManager.EXTRA_SCALE, -1);

            int batteryStatus = status.getIntExtra(
                    BatteryManager.EXTRA_STATUS, -1);

            if (level >= 0 && scale > 0) {

                int percentage = level * 100 / scale;

                boolean charging =
                        batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                        batteryStatus == BatteryManager.BATTERY_STATUS_FULL;

                String batteryInfo = percentage + "%";

                if (charging) {
                    batteryInfo += "  ⚡ Charging";
                }

                batteryText.setText(batteryInfo);
            }
        }
    }

    // SIMPLE ROUNDED BACKGROUND

    private GradientDrawable roundRect(
            int color, int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f);
    }
}