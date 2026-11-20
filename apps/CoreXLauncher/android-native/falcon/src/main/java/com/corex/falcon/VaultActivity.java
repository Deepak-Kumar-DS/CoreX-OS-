package com.corex.falcon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VaultActivity extends Activity {

    private static final int CREATE_RECOVERED_FILE = 201;

    private static final int BUFFER_SIZE = 8192;

    private LinearLayout fileContainer;
    private TextView status;
    private Button refreshButton;

    private File selectedEncryptedFile;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean operationRunning = false;
    private volatile boolean activityDestroyed = false;

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        createInterface();
        loadFiles();
    }

    // --------------------------------------------------
    // USER INTERFACE
    // --------------------------------------------------

    private void createInterface() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        root.setBackgroundColor(0xFFF2F2F2);

        scroll.addView(root);

        TextView title = new TextView(this);

        title.setText("Falcon Vault");
        title.setTextSize(26);
        title.setTextColor(0xFF202124);

        root.addView(title);

        TextView description = new TextView(this);

        description.setText(
                "Manage your encrypted files securely."
        );

        description.setTextSize(14);
        description.setTextColor(0xFF666666);

        description.setPadding(
                0,
                dp(8),
                0,
                dp(16)
        );

        root.addView(description);

        refreshButton = new Button(this);

        refreshButton.setText("Refresh Vault");
        refreshButton.setAllCaps(false);

        root.addView(refreshButton);

        refreshButton.setOnClickListener(
                view -> loadFiles()
        );

        status = new TextView(this);

        status.setTextColor(0xFF444444);
        status.setTextSize(14);

        status.setPadding(
                0,
                dp(14),
                0,
                dp(14)
        );

        root.addView(status);

        fileContainer = new LinearLayout(this);

        fileContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(fileContainer);

        TextView footer = new TextView(this);

        footer.setText(
                "Falcon | CoreX OS\n"
                        + "Developed by Deepak Kumar Sharma"
        );

        footer.setTextColor(0xFF666666);
        footer.setGravity(Gravity.CENTER);

        footer.setPadding(
                0,
                dp(24),
                0,
                dp(12)
        );

        root.addView(footer);

        setContentView(scroll);
    }

    // --------------------------------------------------
    // ACTIVITY STATE
    // --------------------------------------------------

    private boolean canUpdateUI() {

        return !activityDestroyed
                && !isFinishing()
                && !isDestroyed();
    }

    private void updateStatus(String message) {

        if (canUpdateUI() && status != null) {
            status.setText(message);
        }
    }

    private void showToast(String message) {

        if (!canUpdateUI()) {
            return;
        }

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    // --------------------------------------------------
    // LOAD VAULT FILES
    // --------------------------------------------------

    private void loadFiles() {

        if (operationRunning || activityDestroyed) {
            return;
        }

        operationRunning = true;

        refreshButton.setEnabled(false);

        status.setText("Loading Falcon Vault...");
        fileContainer.removeAllViews();

        executor.execute(() -> {

            File[] files;

            String error = null;

            try {

                files = FalconVault.getEncryptedFiles(
                        getApplicationContext()
                );

            } catch (Exception exception) {

                files = new File[0];
                error = "Unable to load Falcon Vault.";
            }

            File[] finalFiles = files;
            String finalError = error;

            runOnUiThread(() -> {

                operationRunning = false;

                if (!canUpdateUI()) {
                    return;
                }

                refreshButton.setEnabled(true);

                if (finalError != null) {

                    status.setText(finalError);
                    return;
                }

                status.setText(
                        finalFiles.length
                                + " encrypted file(s) found."
                );

                if (finalFiles.length == 0) {

                    TextView empty = new TextView(this);

                    empty.setText(
                            "Your Falcon Vault is empty."
                    );

                    empty.setTextColor(0xFF555555);

                    fileContainer.addView(empty);

                    return;
                }

                for (File file : finalFiles) {

                    addFileCard(file);
                }
            });
        });
    }

    // --------------------------------------------------
    // FILE CARDS
    // --------------------------------------------------

    private void addFileCard(File file) {

        LinearLayout card = new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(12);

        fileContainer.addView(
                card,
                cardParams
        );

        TextView filename = new TextView(this);

        filename.setText(
                FalconCrypto.getOriginalFileName(file)
        );

        filename.setTextSize(16);
        filename.setTextColor(0xFF202124);

        card.addView(filename);

        TextView details = new TextView(this);

        details.setText(
                readableSize(file.length())
                        + "\nEncrypted: "
                        + android.text.format.DateFormat.format(
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

        recover.setOnClickListener(
                view -> confirmRecovery(file)
        );

        Button delete = new Button(this);

        delete.setText("Delete Encrypted File");
        delete.setAllCaps(false);

        card.addView(delete);

        delete.setOnClickListener(
                view -> confirmDelete(file)
        );
    }

    private String readableSize(long bytes) {

        if (bytes < 1024) {
            return bytes + " bytes";
        }

        if (bytes < 1024L * 1024L) {

            return String.format(
                    Locale.US,
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                Locale.US,
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    // --------------------------------------------------
    // RECOVERY CONFIRMATION
    // --------------------------------------------------

    private void confirmRecovery(File encryptedFile) {

        if (operationRunning) {
            return;
        }

        if (encryptedFile == null || !encryptedFile.exists()) {

            showToast("Encrypted file no longer exists.");
            loadFiles();
            return;
        }

        String originalName =
                FalconCrypto.getOriginalFileName(encryptedFile);

        if ("Unknown File".equals(originalName)) {

            showToast("Invalid encrypted file.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Recover File")
                .setMessage(
                        "Original filename: "
                                + originalName
                                + "\n\nDecrypt and save this file?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Recover",
                        (dialog, which) -> {

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

                            try {

                                startActivityForResult(
                                        intent,
                                        CREATE_RECOVERED_FILE
                                );

                            } catch (Exception exception) {

                                selectedEncryptedFile = null;

                                showToast(
                                        "Unable to open file destination."
                                );
                            }
                        }
                )
                .show();
    }

    // --------------------------------------------------
    // DELETE ENCRYPTED FILE
    // --------------------------------------------------

    private void confirmDelete(File file) {

        if (operationRunning) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Encrypted File")
                .setMessage(
                        "Are you sure you want to permanently "
                                + "delete this encrypted file?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> deleteFile(file)
                )
                .show();
    }

    private void deleteFile(File file) {

        if (operationRunning) {
            return;
        }

        operationRunning = true;

        status.setText("Deleting encrypted file...");

        executor.execute(() -> {

            boolean deleted = false;

            try {

                deleted = FalconVault.deleteEncryptedFile(
                        getApplicationContext(),
                        file
                );

            } catch (Exception ignored) {
            }

            boolean result = deleted;

            runOnUiThread(() -> {

                operationRunning = false;

                if (!canUpdateUI()) {
                    return;
                }

                if (result) {

                    showToast("Encrypted file deleted.");

                } else {

                    showToast("File could not be deleted.");
                }

                loadFiles();
            });
        });
    }

    // --------------------------------------------------
    // DOCUMENT PICKER RESULT
    // --------------------------------------------------

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

        if (requestCode != CREATE_RECOVERED_FILE) {
            return;
        }

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {

            selectedEncryptedFile = null;
            return;
        }

        if (selectedEncryptedFile == null) {
            return;
        }

        Uri destination = data.getData();

        File encryptedFile = selectedEncryptedFile;

        selectedEncryptedFile = null;

        recoverToDestination(
                encryptedFile,
                destination
        );
    }

    // --------------------------------------------------
    // SECURE RECOVERY
    // --------------------------------------------------

    private void recoverToDestination(
            File encryptedFile,
            Uri destination
    ) {

        if (operationRunning) {
            return;
        }

        operationRunning = true;

        status.setText("Decrypting and verifying file...");

        executor.execute(() -> {

            File temporaryFile = null;

            String error = null;

            boolean destinationOpened = false;
            boolean recoveryCompleted = false;

            try {

                temporaryFile = File.createTempFile(
                        "falcon_recovery_",
                        ".tmp",
                        getCacheDir()
                );

                // Decrypt and verify AES-GCM authentication.
                FalconCrypto.decrypt(
                        encryptedFile,
                        temporaryFile
                );

                try (
                        InputStream input =
                                new FileInputStream(temporaryFile);

                        OutputStream output =
                                getContentResolver()
                                        .openOutputStream(
                                                destination,
                                                "w"
                                        )
                ) {

                    if (output == null) {

                        throw new Exception(
                                "Cannot open destination."
                        );
                    }

                    destinationOpened = true;

                    byte[] buffer =
                            new byte[BUFFER_SIZE];

                    int count;

                    while ((count = input.read(buffer)) != -1) {

                        output.write(buffer, 0, count);
                    }

                    output.flush();
                }

                recoveryCompleted = true;

            } catch (Exception exception) {

                error = exception.getMessage();

                if (error == null || error.trim().isEmpty()) {
                    error = "File recovery failed.";
                }

            } finally {

                if (temporaryFile != null
                        && temporaryFile.exists()) {

                    temporaryFile.delete();
                }

                // Remove partially written destination on failure.
                if (!recoveryCompleted && destinationOpened) {

                    try {

                        DocumentsContract.deleteDocument(
                                getContentResolver(),
                                destination
                        );

                    } catch (Exception ignored) {
                    }
                }
            }

            String finalError = error;
            boolean finalSuccess = recoveryCompleted;

            runOnUiThread(() -> {

                operationRunning = false;

                if (!canUpdateUI()) {
                    return;
                }

                if (finalSuccess) {

                    status.setText("Recovery completed.");

                    showToast(
                            "File recovered successfully."
                    );

                } else {

                    status.setText("Recovery failed.");

                    new AlertDialog.Builder(this)
                            .setTitle("Recovery Failed")
                            .setMessage(
                                    "The file could not be recovered.\n\n"
                                            + "It may be damaged, "
                                            + "the encryption key may be "
                                            + "unavailable, or the "
                                            + "destination may not be writable."
                            )
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    // --------------------------------------------------
    // ACTIVITY CLEANUP
    // --------------------------------------------------

    @Override
    protected void onDestroy() {

        activityDestroyed = true;

        executor.shutdownNow();

        super.onDestroy();
    }
}