package com.corex.pulse;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.media.MediaRecorder;
import android.content.pm.PackageManager;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import com.corex.falcon.FalconIdentity;
import com.corex.falcon.FalconMessageCrypto;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PulseActivity extends Activity {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SharedPreferences preferences;

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout authPanel;
    private LinearLayout messagePanel;

    private EditText serverInput;
    private EditText nameInput;
    private EditText identifierInput;
    private EditText passwordInput;

    private EditText searchInput;
    private EditText messageInput;

    private TextView statusText;
    private TextView accountText;
    private TextView conversationText;

    private String userId = "";
    private String displayName = "";
    private String selectedRecipientId = "";
    private String selectedRecipientName = "";
    private String selectedRecipientPublicKey = "";

    private boolean registerMode = false;
    private boolean isLoggedIn = false;

    private LinearLayout mediaInboxPanel;

    private static final int REQUEST_PICK_MEDIA = 4101;
    private static final int REQUEST_SAVE_MEDIA = 4102;

    private String pendingMediaCategory = "";
    private File pendingDecryptedFile;
    private String pendingMediaFileId = "";

    private MediaRecorder voiceRecorder;
    private File recordedVoiceFile;
    private static final int REQUEST_RECORD_AUDIO = 4103;
    private boolean voiceRecording = false;


    private final int BACKGROUND = Color.rgb(247, 248, 250);
    private final int SURFACE = Color.WHITE;
    private final int PRIMARY = Color.rgb(28, 91, 180);
    private final int TEXT = Color.rgb(25, 32, 44);
    private final int MUTED = Color.rgb(112, 121, 135);

    private final Runnable inboxPoller = new Runnable() {
        @Override
        public void run() {
            if (isLoggedIn) {
                loadInbox();
                handler.postDelayed(this, 12000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(
                "CoreXPulse",
                MODE_PRIVATE
        );

        PulseRelayClient.setServerUrl(
                preferences.getString(
                        "serverUrl",
                        "http://10.0.2.2:5001"
                )
        );

        PulseRelayClient.setAccessToken(
                preferences.getString("token", "")
        );

        userId = preferences.getString("userId", "");
        displayName = preferences.getString("displayName", "");

        buildBaseUI();

        if (!PulseRelayClient.getAccessToken().isEmpty()) {
            restoreSession();
        } else {
            showAuthentication();
        }
    }

    private void buildBaseUI() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        TextView header = new TextView(this);
        header.setText("CoreX Pulse");
        header.setTextSize(25);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(TEXT);
        header.setPadding(22, 22, 22, 4);

        TextView tagline = new TextView(this);
        tagline.setText("Connect Beyond Limits.");
        tagline.setTextSize(13);
        tagline.setTextColor(MUTED);
        tagline.setPadding(23, 0, 22, 17);

        root.addView(header);
        root.addView(tagline);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(18, 8, 18, 24);

        scrollView.addView(content);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        TextView footer = new TextView(this);
        footer.setText("Developed by Deepak Kumar Sharma");
        footer.setTextColor(MUTED);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(10, 12, 10, 14);

        root.addView(footer);

        setContentView(root);
    }

    private GradientDrawable roundedBackground(
            int color,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, Color.rgb(230, 233, 238));
        return drawable;
    }

    private TextView label(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(14);
        view.setTextColor(TEXT);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, 13, 0, 7);
        return view;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(14);
        editText.setSingleLine(true);
        editText.setPadding(14, 10, 14, 10);
        editText.setBackground(
                roundedBackground(SURFACE, 12)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.bottomMargin = 9;

        editText.setLayoutParams(params);

        return editText;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(
                roundedBackground(PRIMARY, 12)
        );

        button.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.bottomMargin = 9;

        button.setLayoutParams(params);

        return button;
    }

    private void showAuthentication() {

        isLoggedIn = false;
        handler.removeCallbacks(inboxPoller);

        content.removeAllViews();

        authPanel = new LinearLayout(this);
        authPanel.setOrientation(LinearLayout.VERTICAL);
        authPanel.setPadding(17, 17, 17, 17);
        authPanel.setBackground(
                roundedBackground(SURFACE, 18)
        );

        content.addView(authPanel);

        TextView title = label("Welcome to CoreX Pulse");
        title.setTextSize(20);

        authPanel.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "Connect with people using the internet. " +
                "A SIM card is not required for app-to-app messaging."
        );
        description.setTextColor(MUTED);
        description.setTextSize(13);

        authPanel.addView(description);

        authPanel.addView(label("Server URL"));

        serverInput = input("Communication server URL");
        serverInput.setText(
                PulseRelayClient.getServerUrl()
        );

        authPanel.addView(serverInput);

        nameInput = input("Your display name");

        identifierInput = input(
                "Email address or phone number"
        );

        passwordInput = input("Password");

        passwordInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        authPanel.addView(label("Account details"));

        authPanel.addView(nameInput);
        authPanel.addView(identifierInput);
        authPanel.addView(passwordInput);

        statusText = new TextView(this);
        statusText.setTextColor(MUTED);
        statusText.setTextSize(12);
        statusText.setPadding(0, 8, 0, 8);

        authPanel.addView(statusText);

        authPanel.addView(
                button("Create Account", this::register)
        );

        authPanel.addView(
                button("Login", this::login)
        );

        authPanel.addView(
                button("Check Server Connection", this::checkServer)
        );

        TextView note = new TextView(this);
        note.setText(
                "Note: Account verification by email/SMS OTP " +
                "is not configured yet."
        );
        note.setTextColor(MUTED);
        note.setTextSize(11);
        note.setPadding(0, 12, 0, 0);

        authPanel.addView(note);
    }

    private boolean applyServerUrl() {

        String url = serverInput.getText()
                .toString()
                .trim();

        if (url.isEmpty()) {
            showToast("Please enter server URL");
            return false;
        }

        try {
            PulseRelayClient.setServerUrl(url);

            preferences.edit()
                    .putString("serverUrl", url)
                    .apply();

            return true;

        } catch (Exception e) {
            showToast("Invalid server URL");
            return false;
        }
    }

    private void checkServer() {

        if (!applyServerUrl()) return;

        statusText.setText("Checking server...");

        executor.execute(() -> {
            try {
                JSONObject result = PulseRelayClient.health();

                runOnUiThread(() ->
                        statusText.setText(
                                "Server: "
                                        + result.optString("status", "Unknown")
                        )
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        statusText.setText(
                                "Connection failed: " + e.getMessage()
                        )
                );
            }
        });
    }

    private void register() {

        if (!applyServerUrl()) return;

        String name = nameInput.getText().toString().trim();
        String identifier = identifierInput.getText()
                .toString().trim();

        String password = passwordInput.getText()
                .toString();

        if (name.isEmpty() || identifier.isEmpty()
                || password.isEmpty()) {
            showToast("Please fill all account details");
            return;
        }

        if (password.length() < 10) {
            showToast("Password must contain at least 10 characters");
            return;
        }

        boolean isEmail = identifier.contains("@");

        String email = isEmail ? identifier : "";
        String phone = isEmail ? "" : identifier;

        statusText.setText("Creating your account...");

        executor.execute(() -> {
            try {
                String publicKey = FalconIdentity.getPublicKey();

                JSONObject result = PulseRelayClient.register(
                        name,
                        email,
                        phone,
                        password,
                        publicKey
                );

                JSONObject user = result.optJSONObject("user");

                if (user == null) {
                    throw new Exception(
                            "Server did not return account information"
                    );
                }

                saveAccount(user, result);

                runOnUiThread(() -> {
                    showToast(
                            "Account created. Verification is pending."
                    );
                    showMessaging();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(e.getMessage());
                    showToast(e.getMessage());
                });
            }
        });
    }

    private void login() {

        if (!applyServerUrl()) return;

        String identifier = identifierInput.getText()
                .toString().trim();

        String password = passwordInput.getText()
                .toString();

        if (identifier.isEmpty() || password.isEmpty()) {
            showToast("Enter email/phone and password");
            return;
        }

        statusText.setText("Signing in...");

        executor.execute(() -> {
            try {
                JSONObject result = PulseRelayClient.login(
                        identifier,
                        password
                );

                JSONObject user = result.optJSONObject("user");

                if (user == null) {
                    throw new Exception(
                            "Server did not return account information"
                    );
                }

                saveAccount(user, result);

                runOnUiThread(() -> {
                    showToast("Login successful");
                    showMessaging();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(e.getMessage());
                    showToast(e.getMessage());
                });
            }
        });
    }

    private void saveAccount(
            JSONObject user,
            JSONObject response
    ) {

        userId = user.optString(
                "userId",
                user.optString("id", "")
        );

        displayName = user.optString(
                "displayName",
                "CoreX User"
        );

        String token = response.optString("token", "");

        if (userId.isEmpty() || token.isEmpty()) {
            throw new IllegalStateException(
                    "Account ID or access token is missing"
            );
        }

        preferences.edit()
                .putString("userId", userId)
                .putString("displayName", displayName)
                .putString("token", token)
                .apply();
    }

    private void restoreSession() {

        executor.execute(() -> {
            try {
                JSONObject result = PulseRelayClient.getMe();
                JSONObject user = result.optJSONObject("user");

                if (user == null) {
                    throw new Exception("Invalid session");
                }

                saveAccount(user, new JSONObject()
                        .put("token", PulseRelayClient.getAccessToken()));

                runOnUiThread(this::showMessaging);

            } catch (Exception e) {
                PulseRelayClient.setAccessToken("");

                preferences.edit()
                        .remove("token")
                        .remove("userId")
                        .remove("displayName")
                        .apply();

                runOnUiThread(this::showAuthentication);
            }
        });
    }

    private void showMessaging() {

        isLoggedIn = true;
        content.removeAllViews();

        messagePanel = new LinearLayout(this);
        messagePanel.setOrientation(LinearLayout.VERTICAL);
        messagePanel.setPadding(16, 16, 16, 16);
        messagePanel.setBackground(
                roundedBackground(SURFACE, 18)
        );

        content.addView(messagePanel);

        TextView welcome = label(
                "Hello, " + displayName
        );
        welcome.setTextSize(20);

        messagePanel.addView(welcome);

        accountText = new TextView(this);
        accountText.setText("Your Pulse ID: " + userId);
        accountText.setTextColor(MUTED);
        accountText.setTextSize(12);

        messagePanel.addView(accountText);

        messagePanel.addView(label("Find people"));

        searchInput = input("Search by name, email or phone");

        messagePanel.addView(searchInput);

        messagePanel.addView(
                button("Search Users", this::searchUsers)
        );

        messagePanel.addView(label("Selected recipient"));

        TextView selected = new TextView(this);
        selected.setText("No recipient selected");
        selected.setTextColor(MUTED);
        selected.setTextSize(13);
        selected.setPadding(0, 5, 0, 12);

        messagePanel.addView(selected);

        searchInput.setTag(selected);

        messagePanel.addView(label("Message"));

        messageInput = input("Write your message");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(3);
        messageInput.setGravity(Gravity.TOP);

        messagePanel.addView(messageInput);

        messagePanel.addView(
                button("Send Encrypted Message", this::sendMessage)
        );

        messagePanel.addView(label("Send encrypted media"));

        messagePanel.addView(
                button("Choose Photo", () ->
                        launchMediaPicker("photo", "image/*"))
        );

        messagePanel.addView(
                button("Choose Video", () ->
                        launchMediaPicker("video", "video/*"))
        );

        messagePanel.addView(
                button("Choose Document", () ->
                        launchMediaPicker("document", "*/*"))
        );

        messagePanel.addView(
                button("Choose Voice / Audio File", () ->
                        launchMediaPicker("voice", "audio/*"))
        );

        messagePanel.addView(
                button("Record Voice Message", this::requestVoiceRecording)
        );

        messagePanel.addView(
                button("Stop Recording & Send", this::stopAndSendVoice)
        );

        messagePanel.addView(label("Inbox"));

        conversationText = new TextView(this);
        conversationText.setText("Loading messages...");
        conversationText.setTextColor(TEXT);
        conversationText.setTextSize(13);
        conversationText.setPadding(12, 12, 12, 12);
        conversationText.setBackground(
                roundedBackground(BACKGROUND, 12)
        );

        messagePanel.addView(conversationText);

        messagePanel.addView(label("Received media"));

        mediaInboxPanel = new LinearLayout(this);
        mediaInboxPanel.setOrientation(LinearLayout.VERTICAL);
        messagePanel.addView(mediaInboxPanel);

        messagePanel.addView(
                button("Refresh Inbox", this::loadInbox)
        );

        messagePanel.addView(
                button("Logout", this::logout)
        );

        handler.removeCallbacks(inboxPoller);
        handler.post(inboxPoller);
    }

    private void searchUsers() {

        String query = searchInput.getText()
                .toString().trim();

        if (query.length() < 2) {
            showToast("Enter at least 2 characters");
            return;
        }

        executor.execute(() -> {
            try {
                JSONArray users =
                        PulseRelayClient.searchUsers(query);

                runOnUiThread(() -> {
                    if (users.length() == 0) {
                        showToast("No users found");
                        return;
                    }

                    String[] names = new String[users.length()];
                    String[] ids = new String[users.length()];
                    String[] publicKeys = new String[users.length()];

                    for (int i = 0; i < users.length(); i++) {
                        JSONObject user = users.optJSONObject(i);

                        names[i] = user.optString(
                                "displayName",
                                "CoreX User"
                        );

                        ids[i] = user.optString("userId", "");
                        publicKeys[i] = user.optString("publicKey", "");
                    }

                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Select a user")
                            .setItems(names, (dialog, which) -> {
                                selectedRecipientId = ids[which];
                                selectedRecipientName = names[which];
                                selectedRecipientPublicKey = publicKeys[which];

                                TextView selected =
                                        (TextView) searchInput.getTag();

                                selected.setText(
                                        selectedRecipientName
                                                + " • "
                                                + selectedRecipientId
                                );
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        showToast(e.getMessage())
                );
            }
        });
    }

    private void sendMessage() {

        if (selectedRecipientId.isEmpty()) {
            showToast("Select a recipient first");
            return;
        }

        String message = messageInput.getText()
                .toString().trim();

        if (message.isEmpty()) {
            showToast("Write a message first");
            return;
        }

        messageInput.setEnabled(false);

        executor.execute(() -> {
            try {
                String recipientPublicKey =
                        selectedRecipientPublicKey;

                if (recipientPublicKey.isEmpty()) {
                    throw new Exception(
                            "Please search and select the recipient again."
                    );
                }

                if (recipientPublicKey.isEmpty()) {
                    throw new Exception(
                            "Recipient public key is unavailable"
                    );
                }

                String encryptedPayload =
                        FalconMessageCrypto.encrypt(
                                message,
                                userId,
                                selectedRecipientId,
                                recipientPublicKey
                        );

                PulseRelayClient.sendMessage(
                        selectedRecipientId,
                        encryptedPayload
                );

                runOnUiThread(() -> {
                    messageInput.setText("");
                    messageInput.setEnabled(true);
                    showToast("Encrypted message sent");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    messageInput.setEnabled(true);
                    showToast(e.getMessage());
                });
            }
        });
    }


    private void requestVoiceRecording() {
        if (!isLoggedIn) {
            showToast("Please login first");
            return;
        }

        if (selectedRecipientId.isEmpty() ||
                selectedRecipientPublicKey.isEmpty()) {
            showToast("Search and select a recipient first");
            return;
        }

        if (voiceRecording) {
            showToast("A voice recording is already in progress");
            return;
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
            return;
        }

        startVoiceRecording();
    }

    private void startVoiceRecording() {
        try {
            if (voiceRecorder != null) {
                releaseVoiceRecorder();
            }

            recordedVoiceFile = new File(
                    getCacheDir(),
                    "corex-voice-" + java.util.UUID.randomUUID() + ".m4a"
            );

            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            voiceRecorder.setAudioEncodingBitRate(96000);
            voiceRecorder.setAudioSamplingRate(44100);
            voiceRecorder.setMaxDuration(5 * 60 * 1000);
            voiceRecorder.setMaxFileSize(20L * 1024L * 1024L);
            voiceRecorder.setOutputFile(recordedVoiceFile.getAbsolutePath());

            voiceRecorder.prepare();
            voiceRecorder.start();

            voiceRecording = true;
            showToast("Recording started. Maximum duration: 5 minutes.");

        } catch (Exception e) {
            voiceRecording = false;
            releaseVoiceRecorder();

            if (recordedVoiceFile != null && recordedVoiceFile.exists()) {
                recordedVoiceFile.delete();
            }

            showToast("Could not start recording: " + e.getMessage());
        }
    }

    private void stopAndSendVoice() {
        if (!voiceRecording || voiceRecorder == null) {
            showToast("No voice recording is active");
            return;
        }

        try {
            voiceRecorder.stop();
            voiceRecording = false;
            releaseVoiceRecorder();

            if (recordedVoiceFile == null ||
                    !recordedVoiceFile.exists() ||
                    recordedVoiceFile.length() < 1000) {
                if (recordedVoiceFile != null) recordedVoiceFile.delete();
                showToast("Recording is too short or empty");
                return;
            }

            pendingMediaCategory = "voice";
            Uri voiceUri = Uri.fromFile(recordedVoiceFile);
            uploadSelectedMedia(voiceUri);
            showToast("Voice message is being encrypted and sent.");

        } catch (Exception e) {
            voiceRecording = false;
            releaseVoiceRecorder();

            if (recordedVoiceFile != null && recordedVoiceFile.exists()) {
                recordedVoiceFile.delete();
            }

            showToast("Could not finish recording: " + e.getMessage());
        }
    }

    private void releaseVoiceRecorder() {
        if (voiceRecorder != null) {
            try {
                voiceRecorder.reset();
            } catch (Exception ignored) {
            }

            try {
                voiceRecorder.release();
            } catch (Exception ignored) {
            }

            voiceRecorder = null;
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

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                showToast("Microphone permission is required for voice messages.");
            }
        }
    }

    private void launchMediaPicker(String category, String mimeType) {
        if (!isLoggedIn) {
            showToast("Please login first");
            return;
        }

        if (selectedRecipientId.isEmpty() ||
                selectedRecipientPublicKey.isEmpty()) {
            showToast("Search and select a recipient first");
            return;
        }

        pendingMediaCategory = category;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(intent, REQUEST_PICK_MEDIA);
        } catch (Exception e) {
            showToast("Unable to open file picker");
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "media-file";

        if (uri != null && "file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                name = new File(path).getName();
            }
            return name.replaceAll("[\\\\/:*?\"<>|]", "_");
        }

        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        name = value;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void uploadSelectedMedia(Uri uri) {
        if (uri == null) return;

        final String recipientId = selectedRecipientId;
        final String recipientKey = selectedRecipientPublicKey;
        final String category = pendingMediaCategory;
        final String originalName = getDisplayName(uri);

        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        final String originalMime = mime;

        showToast("Encrypting media...");

        executor.execute(() -> {
            File encrypted = new File(
                    getCacheDir(),
                    "pulse-" + java.util.UUID.randomUUID() + ".cpm"
            );

            try {
                PulseMediaCrypto.encryptUri(
                        getContentResolver(),
                        uri,
                        recipientKey,
                        encrypted
                );

                PulseMediaRelayClient.upload(
                        encrypted,
                        recipientId,
                        category,
                        originalName,
                        originalMime
                );

                runOnUiThread(() ->
                        showToast("Encrypted media sent successfully")
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        showToast("Media send failed: " + e.getMessage())
                );
            } finally {
                if (encrypted.exists()) encrypted.delete();
            }
        });
    }

    private void downloadAndDecryptMedia(
            String fileId,
            String originalName,
            String mimeType
    ) {
        if (fileId == null || fileId.isEmpty()) return;

        showToast("Downloading encrypted media...");

        executor.execute(() -> {
            File encrypted = new File(
                    getCacheDir(),
                    "pulse-download-" + java.util.UUID.randomUUID() + ".cpm"
            );

            File decrypted = new File(
                    getCacheDir(),
                    "pulse-open-" + java.util.UUID.randomUUID()
            );

            try {
                PulseMediaRelayClient.download(fileId, encrypted);

                PulseMediaCrypto.decryptFile(encrypted, decrypted);

                runOnUiThread(() -> {
                    pendingDecryptedFile = decrypted;
                    pendingMediaFileId = fileId;

                    Intent saveIntent = new Intent(
                            Intent.ACTION_CREATE_DOCUMENT
                    );

                    saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    saveIntent.setType(
                            mimeType == null ||
                                    mimeType.isEmpty()
                                    ? "application/octet-stream"
                                    : mimeType
                    );

                    saveIntent.putExtra(
                            Intent.EXTRA_TITLE,
                            originalName == null ||
                                    originalName.isEmpty()
                                    ? "CoreX-Pulse-File"
                                    : originalName
                    );

                    try {
                        startActivityForResult(
                                saveIntent,
                                REQUEST_SAVE_MEDIA
                        );
                    } catch (Exception e) {
                        pendingDecryptedFile = null;
                        showToast("Unable to open save dialog");
                    }
                });

            } catch (Exception e) {
                if (decrypted.exists()) decrypted.delete();

                runOnUiThread(() ->
                        showToast("Download/decryption failed: "
                                + e.getMessage())
                );
            } finally {
                if (encrypted.exists()) encrypted.delete();
            }
        });
    }

    private void saveDecryptedMedia(Uri destination) {
        File source = pendingDecryptedFile;
        String fileId = pendingMediaFileId;

        pendingDecryptedFile = null;
        pendingMediaFileId = "";

        if (source == null || !source.exists() || destination == null) {
            if (source != null && source.exists()) source.delete();
            showToast("Save cancelled");
            return;
        }

        executor.execute(() -> {
            boolean saved = false;

            try (
                    FileInputStream input = new FileInputStream(source);
                    OutputStream output =
                            getContentResolver().openOutputStream(destination)
            ) {
                if (output == null) {
                    throw new IllegalStateException(
                            "Unable to open destination"
                    );
                }

                byte[] buffer = new byte[64 * 1024];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
                saved = true;

            } catch (Exception e) {
                String error = e.getMessage();
                runOnUiThread(() ->
                        showToast("Save failed: " + error)
                );
            } finally {
                source.delete();
            }

            if (saved && fileId != null && !fileId.isEmpty()) {
                try {
                    PulseMediaRelayClient.acknowledge(fileId);
                } catch (Exception ignored) {
                }

                runOnUiThread(() ->
                        showToast("File decrypted and saved successfully")
                );
            }
        });
    }

    private void loadMediaInbox() {
        if (!isLoggedIn || mediaInboxPanel == null) return;

        executor.execute(() -> {
            try {
                JSONArray files = PulseMediaRelayClient.getInbox();

                runOnUiThread(() -> {
                    if (mediaInboxPanel == null) return;

                    mediaInboxPanel.removeAllViews();

                    if (files.length() == 0) {
                        mediaInboxPanel.addView(
                                label("No new media files.")
                        );
                        return;
                    }

                    for (int i = 0; i < files.length(); i++) {
                        JSONObject item = files.optJSONObject(i);
                        if (item == null) continue;

                        String id = item.optString(
                                "fileId",
                                item.optString("id", "")
                        );

                        String name = item.optString(
                                "originalName",
                                "Encrypted media"
                        );

                        String category = item.optString(
                                "category",
                                "file"
                        );

                        String mime = item.optString(
                                "originalMimeType",
                                "application/octet-stream"
                        );

                        String sender = item.optString(
                                "senderId",
                                "Unknown sender"
                        );

                        if (id.isEmpty()) continue;

                        mediaInboxPanel.addView(
                                button(
                                        category + " • " + name
                                                + " • From " + sender,
                                        () -> downloadAndDecryptMedia(
                                                id,
                                                name,
                                                mime
                                        )
                                )
                        );
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (mediaInboxPanel != null) {
                        mediaInboxPanel.removeAllViews();
                        mediaInboxPanel.addView(
                                label("Media inbox error: " + e.getMessage())
                        );
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_SAVE_MEDIA &&
                    pendingDecryptedFile != null) {
                pendingDecryptedFile.delete();
                pendingDecryptedFile = null;
                pendingMediaFileId = "";
            }
            return;
        }

        if (requestCode == REQUEST_PICK_MEDIA) {
            uploadSelectedMedia(data.getData());
        } else if (requestCode == REQUEST_SAVE_MEDIA) {
            saveDecryptedMedia(data.getData());
        }
    }

    private void loadInbox() {

        if (!isLoggedIn) return;

        executor.execute(() -> {
            try {
                JSONArray messages =
                        PulseRelayClient.getInbox();

                StringBuilder output = new StringBuilder();

                for (int i = 0; i < messages.length(); i++) {
                    JSONObject item = messages.optJSONObject(i);

                    if (item == null) continue;

                    JSONObject envelope =
                            item.optJSONObject("encryptedPayload");

                    if (envelope == null) continue;

                    try {
                        String decrypted =
                                FalconMessageCrypto.decrypt(
                                        envelope.toString()
                                );

                        String sender = item.optString(
                                "senderId",
                                "Unknown sender"
                        );

                        output.append(sender)
                                .append(":\n")
                                .append(decrypted)
                                .append("\n\n");

                        String messageId =
                                item.optString("messageId", "");

                        if (!messageId.isEmpty()) {
                            PulseRelayClient.acknowledgeMessage(
                                    messageId
                            );
                        }

                    } catch (Exception decryptError) {
                        output.append(
                                "An encrypted message could not be decrypted.\n\n"
                        );
                    }
                }

                String finalOutput = output.length() == 0
                        ? "No new messages."
                        : output.toString();

                runOnUiThread(() ->
                        conversationText.setText(finalOutput)
                );

                loadMediaInbox();

            } catch (Exception e) {
                runOnUiThread(() ->
                        conversationText.setText(
                                "Inbox error: " + e.getMessage()
                        )
                );
            }
        });
    }

    private void logout() {

        handler.removeCallbacks(inboxPoller);

        executor.execute(() -> {
            try {
                PulseRelayClient.logout();
            } catch (Exception ignored) {
            }

            PulseRelayClient.setAccessToken("");

            preferences.edit()
                    .remove("token")
                    .remove("userId")
                    .remove("displayName")
                    .apply();

            userId = "";
            displayName = "";
            selectedRecipientId = "";
            selectedRecipientName = "";

            runOnUiThread(() -> {
                showToast("Logged out");
                showAuthentication();
            });
        });
    }

    private void showToast(String message) {
        Toast.makeText(
                this,
                message == null ? "Something went wrong" : message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(inboxPoller);
        executor.shutdownNow();
                if (voiceRecording && voiceRecorder != null) {
            try {
                voiceRecorder.stop();
            } catch (Exception ignored) {
            }
        }

        voiceRecording = false;
        releaseVoiceRecorder();

        if (recordedVoiceFile != null && recordedVoiceFile.exists()) {
            recordedVoiceFile.delete();
        }

        handler.removeCallbacks(inboxPoller);
        executor.shutdownNow();

super.onDestroy();
    }
}