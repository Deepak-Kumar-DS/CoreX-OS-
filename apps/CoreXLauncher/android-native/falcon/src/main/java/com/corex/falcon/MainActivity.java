package com.corex.falcon;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PICK_FILES = 100;
    private static final int RECORD_PERMISSION = 101;

    private EditText recipientInput;
    private EditText messageInput;

    private TextView attachmentList;
    private TextView recordingStatus;
    private TextView encryptionStatus;

    private Button recordButton;
    private Button prepareButton;

    private final ArrayList<Uri> attachments = new ArrayList<>();
    private final ArrayList<String> attachmentNames = new ArrayList<>();
    private final ArrayList<File> encryptedFiles = new ArrayList<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private MediaRecorder recorder;
    private File recordingFile;

    private boolean isRecording = false;
    private boolean isEncrypting = false;

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createInterface();
    }

    private void createInterface() {

        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(0xFFF2F2F2);

        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Falcon");
        title.setTextSize(28);
        title.setTextColor(0xFF202124);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("SECURE FILE TRANSFER");
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFF666666);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        addHeading(root, "Recipient");

        recipientInput = new EditText(this);
        recipientInput.setSingleLine(true);
        recipientInput.setHint("Enter recipient Falcon ID");
        root.addView(recipientInput, matchWidth());

        addHeading(root, "Message (Optional)");

        messageInput = new EditText(this);
        messageInput.setHint("Write a message...");
        messageInput.setMinLines(2);
        messageInput.setGravity(Gravity.TOP);
        root.addView(messageInput, matchWidth());

        addHeading(root, "Attachments");

        Button selectButton =
                createButton("Select Images, Videos & Files");

        root.addView(selectButton, matchWidth());
        selectButton.setOnClickListener(v -> openFilePicker());

        Button removeButton =
                createButton("Remove All Attachments");

        root.addView(removeButton, matchWidth());

        removeButton.setOnClickListener(v -> {

            if (isEncrypting) {
                showToast("Encryption is in progress.");
                return;
            }

            attachments.clear();
            attachmentNames.clear();
            encryptedFiles.clear();

            updateAttachmentList();

            encryptionStatus.setText("No files encrypted.");
        });

        attachmentList = new TextView(this);
        attachmentList.setText("No files selected.");
        attachmentList.setTextColor(0xFF444444);
        attachmentList.setTextSize(14);
        attachmentList.setPadding(dp(10), dp(12), dp(10), dp(12));
        root.addView(attachmentList, matchWidth());

        addHeading(root, "Voice Message");

        recordingStatus = new TextView(this);
        recordingStatus.setText("Not recording");
        recordingStatus.setTextColor(0xFF555555);
        root.addView(recordingStatus);

        recordButton = createButton("Start Voice Recording");
        root.addView(recordButton, matchWidth());

        recordButton.setOnClickListener(v -> {

            if (isRecording) {
                stopVoiceRecording();
            } else {
                startVoiceRecording();
            }
        });

        addHeading(root, "Local Encryption");

        encryptionStatus = new TextView(this);
        encryptionStatus.setText("No files encrypted.");
        encryptionStatus.setTextColor(0xFF555555);
        encryptionStatus.setTextSize(14);
        root.addView(encryptionStatus);

        prepareButton = createButton("Encrypt & Prepare Transfer");
        root.addView(prepareButton, matchWidth());

        prepareButton.setOnClickListener(v -> prepareTransfer());

        // Falcon Vault Button
        Button vaultButton = createButton("Open Falcon Vault");
        root.addView(vaultButton, matchWidth());

        vaultButton.setOnClickListener(v -> openVault());

        TextView securityInfo = new TextView(this);

        securityInfo.setText(
                "\nSecurity Information\n\n" +
                "Local encryption: AES-256-GCM\n" +
                "Key storage: Android Keystore\n" +
                "File authentication: GCM tag\n" +
                "Network transfer: Not configured\n" +
                "Device-to-device E2EE: Not implemented"
        );

        securityInfo.setTextColor(0xFF555555);
        securityInfo.setTextSize(13);
        securityInfo.setPadding(dp(10), dp(16), dp(10), dp(16));

        root.addView(securityInfo, matchWidth());

        TextView footer = new TextView(this);

        footer.setText(
                "Falcon | CoreX OS\n" +
                "Developed by Deepak Kumar Sharma"
        );

        footer.setTextColor(0xFF555555);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(22), 0, dp(12));

        root.addView(footer, matchWidth());

        setContentView(scrollView);
    }

    private void openVault() {

        Intent intent = new Intent(
                MainActivity.this,
                VaultActivity.class
        );

        startActivity(intent);
    }

    private void addHeading(LinearLayout parent, String text) {

        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(17);
        heading.setTextColor(0xFF202124);
        heading.setPadding(0, dp(18), 0, dp(8));

        parent.addView(heading);
    }

    private Button createButton(String text) {

        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);

        return button;
    }

    private LinearLayout.LayoutParams matchWidth() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void showToast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void openFilePicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("*/*");

        intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "image/*",
                        "video/*",
                        "audio/*",
                        "application/pdf",
                        "application/zip",
                        "text/*",
                        "application/*"
                }
        );

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, PICK_FILES);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_FILES ||
                resultCode != RESULT_OK ||
                data == null) {
            return;
        }

        if (isEncrypting) {
            showToast("Please wait for encryption to finish.");
            return;
        }

        if (data.getClipData() != null) {

            int count = data.getClipData().getItemCount();

            for (int i = 0; i < count; i++) {

                Uri uri = data.getClipData()
                        .getItemAt(i)
                        .getUri();

                addAttachment(uri);
            }

        } else if (data.getData() != null) {

            addAttachment(data.getData());
        }

        encryptedFiles.clear();

        encryptionStatus.setText(
                "Files selected. Encryption pending."
        );

        updateAttachmentList();
    }

    private void addAttachment(Uri uri) {

        if (attachments.contains(uri)) {
            return;
        }

        try {

            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

        } catch (Exception ignored) {
        }

        attachments.add(uri);

        String name = uri.getLastPathSegment();

        if (name == null || name.isEmpty()) {
            name = "Selected file";
        }

        attachmentNames.add(name);
    }

    private void updateAttachmentList() {

        if (attachments.isEmpty()) {
            attachmentList.setText("No files selected.");
            return;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("Selected files: ")
                .append(attachments.size())
                .append("\n\n");

        for (int i = 0; i < attachmentNames.size(); i++) {

            builder.append(i + 1)
                    .append(". ")
                    .append(attachmentNames.get(i))
                    .append("\n");
        }

        attachmentList.setText(builder.toString());
    }

    private void startVoiceRecording() {

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_PERMISSION
            );

            return;
        }

        try {

            File directory = new File(
                    getExternalFilesDir(null),
                    "Falcon"
            );

            if (!directory.exists() && !directory.mkdirs()) {
                throw new Exception("Cannot create recording folder");
            }

            recordingFile = new File(
                    directory,
                    "Voice_" + System.currentTimeMillis() + ".m4a"
            );

            recorder = new MediaRecorder();

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recordingFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            isRecording = true;

            recordButton.setText("Stop Voice Recording");
            recordingStatus.setText("Recording in progress...");

        } catch (Exception e) {

            releaseRecorder();

            showToast("Recording could not start.");
        }
    }

    private void stopVoiceRecording() {

        if (recorder == null) {
            return;
        }

        try {

            recorder.stop();

            Uri audioUri = Uri.fromFile(recordingFile);

            attachments.add(audioUri);
            attachmentNames.add(recordingFile.getName());

            updateAttachmentList();

            encryptionStatus.setText(
                    "Voice recording saved. Encryption pending."
            );

            recordingStatus.setText("Voice recording saved.");

            showToast("Voice recording saved.");

        } catch (Exception e) {

            if (recordingFile != null) {
                recordingFile.delete();
            }

            recordingStatus.setText("Recording failed.");

        } finally {

            releaseRecorder();

            isRecording = false;

            recordButton.setText("Start Voice Recording");
        }
    }

    private void releaseRecorder() {

        if (recorder != null) {

            try {
                recorder.reset();
            } catch (Exception ignored) {
            }

            try {
                recorder.release();
            } catch (Exception ignored) {
            }

            recorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == RECORD_PERMISSION) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                showToast("Permission granted. Press Record again.");

            } else {

                showToast("Microphone permission is required.");
            }
        }
    }

    private void prepareTransfer() {

        if (isEncrypting) {
            return;
        }

        String recipient = recipientInput.getText()
                .toString()
                .trim();

        String message = messageInput.getText()
                .toString()
                .trim();

        if (recipient.isEmpty()) {

            recipientInput.setError("Enter recipient Falcon ID");
            recipientInput.requestFocus();
            return;
        }

        if (message.isEmpty() && attachments.isEmpty()) {

            showToast("Write a message or select a file.");
            return;
        }

        if (attachments.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle("Message Prepared")
                    .setMessage(
                            "Recipient: " + recipient +
                            "\n\nText is ready, but network transfer " +
                            "is not configured."
                    )
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        isEncrypting = true;
        prepareButton.setEnabled(false);

        encryptionStatus.setText("Encrypting files...");

        ArrayList<Uri> filesToEncrypt =
                new ArrayList<>(attachments);

        executor.execute(() -> {

            ArrayList<File> results = new ArrayList<>();
            String error = null;

            try {

                for (Uri uri : filesToEncrypt) {

                    File encrypted = FalconCrypto.encrypt(
                            getApplicationContext(),
                            uri
                    );

                    results.add(encrypted);
                }

            } catch (Exception e) {

                error = e.getMessage();

                for (File file : results) {
                    file.delete();
                }
            }

            final String finalError = error;

            runOnUiThread(() -> {

                isEncrypting = false;
                prepareButton.setEnabled(true);

                if (finalError != null) {

                    encryptionStatus.setText("Encryption failed.");

                    new AlertDialog.Builder(this)
                            .setTitle("Encryption Failed")
                            .setMessage(
                                    finalError == null
                                            ? "Unknown error."
                                            : finalError
                            )
                            .setPositiveButton("OK", null)
                            .show();

                    return;
                }

                encryptedFiles.clear();
                encryptedFiles.addAll(results);

                encryptionStatus.setText(
                        encryptedFiles.size() +
                        " file(s) encrypted successfully."
                );

                new AlertDialog.Builder(this)
                        .setTitle("Local Encryption Complete")
                        .setMessage(
                                "Recipient: " + recipient +
                                "\nAttachments encrypted: " +
                                encryptedFiles.size() +
                                "\n\nEncrypted copies are stored in " +
                                "Falcon's private app storage.\n\n" +
                                "They have NOT been sent to the recipient. " +
                                "Network transfer and device-to-device " +
                                "encryption are not implemented."
                        )
                        .setPositiveButton("OK", null)
                        .show();
            });
        });
    }

    @Override
    protected void onDestroy() {

        if (isRecording) {

            try {
                recorder.stop();
            } catch (Exception ignored) {
            }

            releaseRecorder();
        }

        executor.shutdownNow();

        super.onDestroy();
    }
}