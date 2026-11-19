package com.corex.falcon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultActivity extends Activity {

    private static final int CREATE_RECOVERED_FILE = 201;

    private LinearLayout fileContainer;
    private TextView status;

    private File selectedEncryptedFile;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private boolean operationRunning = false;

    private int dp(float value) {

        return (int) (
                value *
                getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        createInterface();
        loadFiles();
    }

    private void createInterface() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(0xFFF2F2F2);

        scroll.addView(root);

        TextView title = new TextView(this);

        title.setText("Falcon Vault");
        title.setTextSize(26);
        title.setTextColor(0xFF202124);

        root.addView(title);

        TextView description = new TextView(this);

        description.setText(
                "Manage your encrypted files."
        );

        description.setTextSize(14);
        description.setTextColor(0xFF666666);
        description.setPadding(0, dp(8), 0, dp(16));

        root.addView(description);

        Button refresh = new Button(this);

        refresh.setText("Refresh Vault");
        refresh.setAllCaps(false);

        root.addView(refresh);

        refresh.setOnClickListener(v -> loadFiles());

        status = new TextView(this);

        status.setTextColor(0xFF444444);
        status.setTextSize(14);
        status.setPadding(0, dp(14), 0, dp(14));

        root.addView(status);

        fileContainer = new LinearLayout(this);

        fileContainer.setOrientation(LinearLayout.VERTICAL);

        root.addView(fileContainer);

        TextView footer = new TextView(this);

        footer.setText(
                "Falcon | CoreX OS\n" +
                "Developed by Deepak Kumar Sharma"
        );

        footer.setTextColor(0xFF666666);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, dp(12));

        root.addView(footer);

        setContentView(scroll);
    }

    private void loadFiles() {

        if (operationRunning) {
            return;
        }

        status.setText("Loading Falcon Vault...");
        fileContainer.removeAllViews();

        executor.execute(() -> {

            File[] files;

            try {

                files = FalconVault.getEncryptedFiles(
                        getApplicationContext()
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        status.setText("Unable to load Vault.")
                );

                return;
            }

            runOnUiThread(() -> {

                status.setText(
                        files.length +
                        " encrypted file(s) found."
                );

                if (files.length == 0) {

                    TextView empty = new TextView(this);

                    empty.setText(
                            "Your Falcon Vault is empty."
                    );

                    empty.setTextColor(0xFF555555);

                    fileContainer.addView(empty);

                    return;
                }

                for (File file : files) {

                    addFileCard(file);
                }
            });
        });
    }

    private void addFileCard(File file) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(12);

        fileContainer.addView(card, cardParams);

        TextView filename = new TextView(this);

        try {

            filename.setText(
                    FalconCrypto.getOriginalFileName(file)
            );

        } catch (Exception e) {

            filename.setText("Unknown encrypted file");
        }

        filename.setTextSize(16);
        filename.setTextColor(0xFF202124);

        card.addView(filename);

        TextView details = new TextView(this);

        details.setText(
                readableSize(file.length()) +
                "\nEncrypted: " +
                android.text.format.DateFormat.format(
                        "dd MMM yyyy, hh:mm a",
                        file.lastModified()
                )
        );

        details.setTextSize(12);
        details.setTextColor(0xFF666666);

        card.addView(details);

        Button recover = new Button(this);

        recover.setText("Recover File");
        recover.setAllCaps(false);

        card.addView(recover);

        recover.setOnClickListener(v ->
                confirmRecovery(file)
        );

        Button delete = new Button(this);

        delete.setText("Delete Encrypted File");
        delete.setAllCaps(false);

        card.addView(delete);

        delete.setOnClickListener(v ->
                confirmDelete(file)
        );
    }

    private String readableSize(long bytes) {

        if (bytes < 1024) {
            return bytes + " bytes";
        }

        if (bytes < 1024 * 1024) {

            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    private void confirmRecovery(File encryptedFile) {

        if (operationRunning) {
            return;
        }

        try {

            String originalName =
                    FalconCrypto.getOriginalFileName(encryptedFile);

            new AlertDialog.Builder(this)
                    .setTitle("Recover File")
                    .setMessage(
                            "Original filename: " + originalName +
                            "\n\nDecrypt and save this file?"
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Recover", (dialog, which) -> {

                        selectedEncryptedFile = encryptedFile;

                        Intent intent = new Intent(
                                Intent.ACTION_CREATE_DOCUMENT
                        );

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        intent.setType("*/*");

                        intent.putExtra(
                                Intent.EXTRA_TITLE,
                                originalName
                        );

                        startActivityForResult(
                                intent,
                                CREATE_RECOVERED_FILE
                        );
                    })
                    .show();

        } catch (Exception e) {

            showToast("Invalid encrypted file.");
        }
    }

    private void confirmDelete(File file) {

        if (operationRunning) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Encrypted File")
                .setMessage(
                        "Are you sure you want to permanently delete " +
                        "this encrypted file?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    operationRunning = true;

                    executor.execute(() -> {

                        boolean deleted = false;

                        try {

                            deleted = FalconVault.deleteEncryptedFile(
                                    getApplicationContext(),
                                    file
                            );

                        } catch (Exception ignored) {
                        }

                        final boolean result = deleted;

                        runOnUiThread(() -> {

                            operationRunning = false;

                            if (result) {

                                showToast("Encrypted file deleted.");

                            } else {

                                showToast("File could not be deleted.");
                            }

                            loadFiles();
                        });
                    });
                })
                .show();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != CREATE_RECOVERED_FILE ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {

            return;
        }

        if (selectedEncryptedFile == null) {
            return;
        }

        Uri destination = data.getData();

        File encryptedFile = selectedEncryptedFile;

        operationRunning = true;

        status.setText("Decrypting file...");

        executor.execute(() -> {

            File temporaryFile = null;

            String error = null;

            try {

                temporaryFile = new File(
                        getCacheDir(),
                        "recovery_" + System.nanoTime() + ".tmp"
                );

                FalconCrypto.decrypt(
                        encryptedFile,
                        temporaryFile
                );

                try (
                        InputStream input =
                                new FileInputStream(temporaryFile);

                        OutputStream output =
                                getContentResolver()
                                        .openOutputStream(destination, "w")
                ) {

                    if (output == null) {
                        throw new Exception(
                                "Cannot open destination."
                        );
                    }

                    byte[] buffer = new byte[8192];

                    int count;

                    while ((count = input.read(buffer)) != -1) {

                        output.write(buffer, 0, count);
                    }

                    output.flush();
                }

            } catch (Exception e) {

                error = e.getMessage();

            } finally {

                if (temporaryFile != null &&
                        temporaryFile.exists()) {

                    temporaryFile.delete();
                }
            }

            final String finalError = error;

            runOnUiThread(() -> {

                operationRunning = false;

                if (finalError == null) {

                    status.setText("Recovery completed.");

                    showToast("File recovered successfully.");

                } else {

                    status.setText("Recovery failed.");

                    new AlertDialog.Builder(this)
                            .setTitle("Recovery Failed")
                            .setMessage(
                                    "File could not be recovered. " +
                                    "It may be damaged or the key unavailable."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void showToast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onDestroy() {

        executor.shutdownNow();

        super.onDestroy();
    }
}