package com.corex.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FilesActivity extends Activity {

    private static final int PICK_FOLDER = 7001;
    private static final String PREFS = "corex_file_manager";

    private LinearLayout list;
    private TextView location;
    private EditText search;
    private DocumentFile current;
    private final ArrayList<DocumentFile> history = new ArrayList<>();

    private int bg = Color.rgb(247, 248, 250);
    private int fg = Color.rgb(25, 30, 38);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        buildUI();

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String saved = prefs.getString("tree", null);

        if (saved != null) {
            Uri uri = Uri.parse(saved);
            current = DocumentFile.fromTreeUri(this, uri);
            showFiles();
        } else {
            showWelcome();
        }
    }

    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(fg);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setPadding(12, 10, 12, 10);
        return v;
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(35, 45, 60)));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14, 12, 14, 8);
        root.setBackgroundColor(bg);

        TextView title = text("CoreX Files", 25, true);
        root.addView(title);

        root.addView(text(
                "Your files. One place.",
                13, false));

        LinearLayout actions = new LinearLayout(this);

        actions.addView(button("Downloads", () -> chooseFolder("Downloads")),
                new LinearLayout.LayoutParams(0, -2, 1));

        actions.addView(button("Internal Storage", () -> chooseFolder("Storage")),
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(actions);

        LinearLayout navigation = new LinearLayout(this);

        navigation.addView(button("← Back", this::goBack),
                new LinearLayout.LayoutParams(0, -2, 1));

        navigation.addView(button("Refresh", this::showFiles),
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(navigation);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search files in this folder...");
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                showFiles();
            }
            public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(search);

        location = text("Choose a folder to begin", 13, false);
        root.addView(location);

        ScrollView scroll = new ScrollView(this);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(list);

        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        root.addView(text(
                "Developed by Deepak Kumar Sharma",
                11, false));

        setContentView(root);
    }

    private void showWelcome() {
        list.removeAllViews();

        list.addView(text(
                "Welcome to CoreX Files",
                20, true));

        list.addView(text(
                "Select your Downloads folder or Internal Storage. " +
                "Allow access once to browse your files.",
                15, false));

        list.addView(button(
                "Open Downloads Folder",
                () -> chooseFolder("Downloads")));

        list.addView(button(
                "Browse Internal Storage",
                () -> chooseFolder("Storage")));

        location.setText("No folder selected");
    }

    private void chooseFolder(String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FOLDER &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri uri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString("tree", uri.toString())
                    .apply();

            current = DocumentFile.fromTreeUri(this, uri);
            history.clear();
            showFiles();
        }
    }

    private void showFiles() {
        if (list == null) return;

        list.removeAllViews();

        if (current == null || !current.exists()) {
            showWelcome();
            return;
        }

        location.setText("Location: " + current.getName());

        String query = search.getText().toString()
                .trim().toLowerCase(Locale.ROOT);

        List<DocumentFile> files = new ArrayList<>();

        DocumentFile[] children = current.listFiles();

        if (children != null) {
            Collections.addAll(files, children);
        }

        files.sort(Comparator
                .comparing((DocumentFile f) -> !f.isDirectory())
                .thenComparing(f -> f.getName() == null
                        ? "" : f.getName().toLowerCase(Locale.ROOT)));

        if (files.isEmpty()) {
            list.addView(text("This folder is empty.", 16, false));
            return;
        }

        int shown = 0;

        for (DocumentFile file : files) {
            String name = file.getName();

            if (name == null) continue;

            if (!name.toLowerCase(Locale.ROOT).contains(query))
                continue;

            shown++;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(8, 8, 8, 8);
            row.setBackgroundColor(Color.WHITE);

            String icon = file.isDirectory() ? "📁 " : getIcon(name);

            TextView nameView = text(icon + name, 16, true);
            row.addView(nameView);

            String info = file.isDirectory()
                    ? "Folder"
                    : readableSize(file.length());

            row.addView(text(info, 12, false));

            row.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    history.add(current);
                    current = file;
                    showFiles();
                } else {
                    openFile(file);
                }
            });

            row.setOnLongClickListener(v -> {
                showFileOptions(file);
                return true;
            });

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(-1, -2);

            params.setMargins(0, 4, 0, 4);
            list.addView(row, params);
        }

        if (shown == 0) {
            list.addView(text("No matching files found.", 15, false));
        }
    }

    private String getIcon(String name) {
        String n = name.toLowerCase(Locale.ROOT);

        if (n.endsWith(".pdf")) return "📕 ";
        if (n.endsWith(".apk")) return "📦 ";
        if (n.matches(".*\\.(jpg|jpeg|png|webp|gif)$")) return "🖼 ";
        if (n.matches(".*\\.(mp4|mkv|3gp|avi)$")) return "🎬 ";
        if (n.matches(".*\\.(mp3|wav|aac|m4a)$")) return "🎵 ";
        if (n.matches(".*\\.(doc|docx|txt|xls|xlsx)$")) return "📄 ";

        return "📃 ";
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);

        return String.format(Locale.US, "%.2f MB",
                bytes / (1024.0 * 1024.0));
    }

    private void openFile(DocumentFile file) {
        try {
            String name = file.getName().toLowerCase(Locale.ROOT);
            String mime = getContentResolver().getType(file.getUri());

            if (name.endsWith(".apk"))
                mime = "application/vnd.android.package-archive";

            if (mime == null) mime = "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(file.getUri(), mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Open with"));

        } catch (Exception e) {
            Toast.makeText(this,
                    "No application available to open this file",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileOptions(DocumentFile file) {
        String[] options = {"Open", "Share", "Delete"};

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openFile(file);
                    if (which == 1) shareFile(file);
                    if (which == 2) confirmDelete(file);
                })
                .show();
    }

    private void shareFile(DocumentFile file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, file.getUri());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share file"));
    }

    private void confirmDelete(DocumentFile file) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage("Delete " + file.getName() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                    showFiles();
                })
                .show();
    }

    private void goBack() {
        if (!history.isEmpty()) {
            current = history.remove(history.size() - 1);
            showFiles();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }
}
