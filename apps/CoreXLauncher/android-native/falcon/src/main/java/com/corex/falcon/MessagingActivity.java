package com.corex.falcon;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessagingActivity extends Activity {

    private EditText server;
    private EditText ownId;
    private EditText recipient;
    private EditText message;
    private EditText recipientPublicKey;

    private TextView status;
    private TextView inbox;
    private TextView nearbyStatus;

    private LinearLayout receivedFilesContainer;

    private Uri selectedFileUri;
    private String selectedFileName = "";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ExecutorService networkExecutor =
            Executors.newCachedThreadPool();

    private volatile boolean taskRunning = false;
    private volatile boolean activityDestroyed = false;

    private static final int PICK_FILE = 4101;
    private static final int REQUEST_LOCATION = 4102;

    private static final long MAX_FILE_SIZE =
            100L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 32768;
    private static final int TRANSFER_PORT = 8988;

    private static final String TRANSFER_MAGIC =
            "FALCON_OFFLINE_1";

    private static final UUID BLUETOOTH_UUID =
            UUID.fromString("8f7a2c10-6b31-4e9d-9c20-4f414c434f4e");

    private WifiP2pManager wifiManager;
    private WifiP2pManager.Channel wifiChannel;

    private BroadcastReceiver wifiReceiver;
    private IntentFilter wifiFilter;

    private final List<WifiP2pDevice> wifiPeers =
            new ArrayList<>();

    private WifiP2pInfo wifiInfo;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket bluetoothServerSocket;

    private volatile boolean bluetoothListening = false;
    private volatile boolean wifiListening = false;

    private String selectedBluetoothAddress = "";

    private Button discoverButton;
    private Button bluetoothDevicesButton;

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        createInterface();
        initializeNearbyTransfer();
        loadPreviouslyReceivedFiles();
    }

    // ==================================================
    // USER INTERFACE
    // ==================================================

    private void createInterface() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(20));
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Falcon Messaging");
        title.setTextSize(27);
        title.setTextColor(Color.rgb(25, 30, 40));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Encrypted Internet & Nearby File Transfer"
        );
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(5), 0, dp(18));
        root.addView(subtitle);

        addHeading(root, "Internet Connection");

        server = field(
                root,
                "Falcon Server URL",
                "https://your-server.example"
        );

        ownId = field(
                root,
                "Your Falcon ID",
                "Enter your Falcon ID"
        );

        Button register = button(root, "Register This Device");
        register.setOnClickListener(v -> registerDevice());

        addHeading(root, "Recipient");

        recipient = field(
                root,
                "Recipient Falcon ID",
                "Enter recipient Falcon ID"
        );

        recipientPublicKey = field(
                root,
                "Recipient Public Key (Offline)",
                "Paste recipient RSA public key"
        );

        recipientPublicKey.setSingleLine(false);
        recipientPublicKey.setMinLines(3);
        recipientPublicKey.setGravity(Gravity.TOP);

        addHeading(root, "Text Messaging");

        message = field(
                root,
                "Message",
                "Write your encrypted message..."
        );

        message.setSingleLine(false);
        message.setMinLines(3);
        message.setGravity(Gravity.TOP);

        Button send = button(root, "Encrypt & Send Message");
        send.setOnClickListener(v -> sendMessage());

        addHeading(root, "Internet File Transfer");

        Button choose = button(
                root,
                "Choose Image / Video / Document / Audio"
        );
        choose.setOnClickListener(v -> chooseFile());

        Button upload = button(
                root,
                "Encrypt & Send Selected File"
        );
        upload.setOnClickListener(v -> sendFile());

        Button clear = button(root, "Remove Selected File");
        clear.setOnClickListener(v -> {
            selectedFileUri = null;
            selectedFileName = "";
            status.setText("Selected file removed.");
        });

        addHeading(root, "Offline Nearby Transfer");

        TextView offlineInfo = new TextView(this);
        offlineInfo.setText(
                "Transfer encrypted files without Internet.\n" +
                "Recipient Public Key is required.\n" +
                "Wi-Fi Direct: discover and connect nearby devices.\n" +
                "Bluetooth: use already paired devices."
        );
        offlineInfo.setTextSize(13);
        offlineInfo.setTextColor(Color.DKGRAY);
        root.addView(offlineInfo);

        discoverButton = button(
                root,
                "Discover Nearby Wi-Fi Direct Devices"
        );
        discoverButton.setOnClickListener(v -> discoverWifiPeers());

        Button connectWifi = button(
                root,
                "Show Nearby Wi-Fi Direct Devices"
        );
        connectWifi.setOnClickListener(v -> showWifiPeers());

        Button wifiListen = button(
                root,
                "Start Wi-Fi Direct Receiving"
        );
        wifiListen.setOnClickListener(v -> startWifiReceiver());

        bluetoothDevicesButton = button(
                root,
                "Select Paired Bluetooth Device"
        );
        bluetoothDevicesButton.setOnClickListener(
                v -> showBluetoothDevices()
        );

        Button bluetoothListen = button(
                root,
                "Start Bluetooth Receiving"
        );
        bluetoothListen.setOnClickListener(
                v -> startBluetoothReceiver()
        );

        Button sendWifi = button(
                root,
                "Send Selected File via Wi-Fi Direct"
        );
        sendWifi.setOnClickListener(v -> sendViaWifi());

        Button sendBluetooth = button(
                root,
                "Send Selected File via Bluetooth"
        );
        sendBluetooth.setOnClickListener(
                v -> sendViaBluetooth()
        );

        nearbyStatus = new TextView(this);
        nearbyStatus.setText(
                "Nearby transfer is ready."
        );
        nearbyStatus.setTextSize(13);
        nearbyStatus.setTextColor(Color.DKGRAY);
        nearbyStatus.setPadding(0, dp(12), 0, dp(12));
        root.addView(nearbyStatus);

        addHeading(root, "Inbox");

        Button receive = button(
                root,
                "Refresh Internet Inbox & Receive Files"
        );
        receive.setOnClickListener(v -> refreshInbox());

        status = new TextView(this);
        status.setText("Ready.");
        status.setTextSize(14);
        status.setTextColor(Color.rgb(50, 60, 75));
        status.setPadding(0, dp(18), 0, dp(14));
        root.addView(status);

        inbox = new TextView(this);
        inbox.setText("No messages loaded.");
        inbox.setTextSize(14);
        inbox.setTextColor(Color.BLACK);
        inbox.setPadding(dp(12), dp(12), dp(12), dp(18));
        root.addView(inbox);

        addHeading(root, "Received Files");

        receivedFilesContainer = new LinearLayout(this);
        receivedFilesContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(receivedFilesContainer);

        addHeading(root, "Security");

        TextView security = new TextView(this);
        security.setText(
                "AES-256-GCM file encryption\n" +
                "RSA recipient-key encryption\n" +
                "Android Keystore integration\n" +
                "Authenticated file encryption\n" +
                "Internet encrypted file transfer\n" +
                "Wi-Fi Direct encrypted file transfer\n" +
                "Bluetooth encrypted file transfer"
        );
        security.setTextSize(13);
        security.setTextColor(Color.DKGRAY);
        root.addView(security);

        TextView footer = new TextView(this);
        footer.setText(
                "\nCoreX OS • Falcon\n" +
                "Developed by Deepak Kumar Sharma"
        );
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(12);
        footer.setTextColor(Color.GRAY);
        footer.setPadding(0, dp(25), 0, dp(10));
        root.addView(footer);

        setContentView(scroll);
    }

    private void addHeading(LinearLayout root, String text) {

        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(18);
        heading.setTextColor(Color.rgb(30, 35, 45));
        heading.setPadding(0, dp(20), 0, dp(8));

        root.addView(heading);
    }

    private EditText field(
            LinearLayout root,
            String label,
            String hint
    ) {

        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextSize(14);
        heading.setTextColor(Color.DKGRAY);
        root.addView(heading);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        root.addView(input);

        return input;
    }

    private Button button(LinearLayout root, String text) {

        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        root.addView(button);

        return button;
    }

    // ==================================================
    // BACKGROUND TASK MANAGEMENT
    // ==================================================

    private interface Task {
        String run() throws Exception;
    }

    private void runTask(String working, Task task) {

        if (taskRunning || activityDestroyed) {
            return;
        }

        taskRunning = true;
        status.setText(working);

        executor.execute(() -> {

            String result;

            try {
                result = task.run();
            } catch (Exception e) {
                result = "Error: " + safeError(e);
            }

            String finalResult = result;

            runOnUiThread(() -> {

                taskRunning = false;

                if (canUpdateUI()) {
                    status.setText(finalResult);
                }
            });
        });
    }

    private boolean canUpdateUI() {
        return !activityDestroyed
                && !isFinishing()
                && !isDestroyed();
    }

    private String safeError(Exception e) {

        String error = e.getMessage();

        if (error == null || error.trim().isEmpty()) {
            return "Operation failed.";
        }

        return error;
    }

    private void configureServer() {

        String url = server.getText().toString().trim();

        if (url.isEmpty()) {
            throw new IllegalArgumentException(
                    "Server URL required."
            );
        }

        if (!url.startsWith("https://")
                && !url.startsWith("http://")) {

            throw new IllegalArgumentException(
                    "Enter a valid HTTP or HTTPS server URL."
            );
        }

        FalconRelayClient.setServerUrl(url);
    }

    private String requireId(EditText input, String label) {

        String value = input.getText().toString().trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    label + " required."
            );
        }

        return value;
    }

    private String requirePublicKey() {

        String key = recipientPublicKey.getText()
                .toString()
                .trim();

        if (key.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recipient Public Key is required for offline transfer."
            );
        }

        return key;
    }

    // ==================================================
    // INTERNET REGISTRATION
    // ==================================================

    private void registerDevice() {

        try {
            configureServer();

            String id = requireId(ownId, "Your Falcon ID");

            runTask("Registering device...", () -> {

                JSONObject result =
                        FalconRelayClient.register(
                                id,
                                FalconIdentity.getPublicKey()
                        );

                return "Device registered successfully.\nFalcon ID: "
                        + result.getString("falconId");
            });

        } catch (Exception e) {
            status.setText("Error: " + safeError(e));
        }
    }

    // ==================================================
    // INTERNET TEXT MESSAGING
    // ==================================================

    private void sendMessage() {

        try {

            configureServer();

            String sender = requireId(ownId, "Your Falcon ID");
            String target = requireId(
                    recipient,
                    "Recipient Falcon ID"
            );

            String text = requireId(message, "Message");

            if (sender.equals(target)) {
                throw new IllegalArgumentException(
                        "Recipient must be another Falcon ID."
                );
            }

            runTask(
                    "Encrypting and sending message...",
                    () -> {

                        JSONObject user =
                                FalconRelayClient.getUser(target);

                        String publicKey =
                                user.getString("publicKey");

                        String encrypted =
                                FalconMessageCrypto.encrypt(
                                        text,
                                        sender,
                                        target,
                                        publicKey
                                );

                        JSONObject result =
                                FalconRelayClient.sendEncryptedMessage(
                                        sender,
                                        target,
                                        encrypted
                                );

                        return "Encrypted message sent.\nMessage ID: "
                                + result.getString("messageId");
                    }
            );

        } catch (Exception e) {
            status.setText("Error: " + safeError(e));
        }
    }

    // ==================================================
    // FILE PICKER
    // ==================================================

    private void chooseFile() {

        if (taskRunning) {
            return;
        }

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

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

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (Exception e) {
            status.setText("Unable to open file picker.");
        }
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

        if (requestCode != PICK_FILE
                || resultCode != RESULT_OK
                || data == null) {
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            return;
        }

        try {

            int flags = data.getFlags()
                    & Intent.FLAG_GRANT_READ_URI_PERMISSION;

            getContentResolver().takePersistableUriPermission(
                    uri,
                    flags
            );

        } catch (Exception ignored) {
        }

        selectedFileUri = uri;
        selectedFileName = getFileName(uri);

        status.setText(
                "Selected file: " + selectedFileName
        );
    }

    private String getFileName(Uri uri) {

        String name = "FalconFile";

        try (
                Cursor cursor = getContentResolver().query(
                        uri, null, null, null, null
                )
        ) {

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

        return sanitizeFileName(name);
    }

    private String sanitizeFileName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return "FalconFile";
        }

        String safe = name.replaceAll(
                "[\\\\/:*?\"<>|\\p{Cntrl}]",
                "_"
        ).trim();

        if (safe.isEmpty()
                || safe.equals(".")
                || safe.equals("..")) {
            return "FalconFile";
        }

        if (safe.length() > 180) {
            safe = safe.substring(0, 180);
        }

        return safe;
    }

    private File copyUriToCache(Uri uri) throws Exception {

        File source = File.createTempFile(
                "falcon_source_",
                ".tmp",
                getCacheDir()
        );

        boolean completed = false;

        try {

            try (
                    InputStream input =
                            getContentResolver().openInputStream(uri);

                    OutputStream output =
                            new FileOutputStream(source)
            ) {

                if (input == null) {
                    throw new IOException(
                            "Cannot read selected file."
                    );
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0;
                int count;

                while ((count = input.read(buffer)) != -1) {

                    total += count;

                    if (total > MAX_FILE_SIZE) {
                        throw new IOException(
                                "Maximum file size is 100 MB."
                        );
                    }

                    output.write(buffer, 0, count);
                }

                output.flush();
            }

            completed = true;
            return source;

        } finally {

            if (!completed && source.exists()) {
                source.delete();
            }
        }
    }

    // ==================================================
    // INTERNET ENCRYPTED FILE SENDING
    // ==================================================

    private void sendFile() {

        try {

            configureServer();

            String sender = requireId(ownId, "Your Falcon ID");
            String target = requireId(
                    recipient,
                    "Recipient Falcon ID"
            );

            if (sender.equals(target)) {
                throw new IllegalArgumentException(
                        "Recipient must be another Falcon ID."
                );
            }

            if (selectedFileUri == null) {
                throw new IllegalArgumentException(
                        "Please choose a file first."
                );
            }

            Uri uri = selectedFileUri;
            String originalName = selectedFileName;

            runTask(
                    "Encrypting and sending file...",
                    () -> {

                        JSONObject user =
                                FalconRelayClient.getUser(target);

                        String publicKey =
                                user.getString("publicKey");

                        File source = null;
                        File encrypted = null;

                        try {

                            source = copyUriToCache(uri);

                            encrypted = File.createTempFile(
                                    "falcon_encrypted_",
                                    ".ftx",
                                    getCacheDir()
                            );

                            FalconTransferCrypto.encryptForRecipient(
                                    source,
                                    encrypted,
                                    publicKey
                            );

                            JSONObject result =
                                    FalconRelayClient.uploadEncryptedFile(
                                            encrypted,
                                            sender,
                                            target,
                                            originalName
                                    );

                            return "Encrypted file uploaded successfully.\n"
                                    + "File ID: "
                                    + result.optString("fileId");

                        } finally {

                            if (source != null) {
                                source.delete();
                            }

                            if (encrypted != null) {
                                encrypted.delete();
                            }
                        }
                    }
            );

        } catch (Exception e) {
            status.setText("Error: " + safeError(e));
        }
    }

    // ==================================================
    // NEARBY TRANSFER INITIALIZATION
    // ==================================================

    private void initializeNearbyTransfer() {

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        wifiManager = (WifiP2pManager)
                getSystemService(Context.WIFI_P2P_SERVICE);

        if (wifiManager != null) {

            wifiChannel = wifiManager.initialize(
                    this,
                    getMainLooper(),
                    null
            );

            registerWifiReceiver();
        }

        if (bluetoothAdapter == null) {
            nearbyStatus.setText(
                    "Bluetooth hardware is unavailable."
            );
        }
    }

    // ==================================================
    // WI-FI DIRECT RECEIVER
    // ==================================================

    private void registerWifiReceiver() {

        wifiReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                        .equals(action)) {

                    if (wifiManager != null) {

                        wifiManager.requestPeers(
                                wifiChannel,
                                peerListListener
                        );
                    }

                } else if (
                        WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
                                .equals(action)
                ) {

                    if (wifiManager != null) {

                        wifiManager.requestConnectionInfo(
                                wifiChannel,
                                info -> {

                                    wifiInfo = info;

                                    if (info != null
                                            && info.groupFormed) {

                                        nearbyStatus.setText(
                                                "Wi-Fi Direct connected. "
                                                        + "Group owner: "
                                                        + info.isGroupOwner
                                        );

                                        if (info.isGroupOwner) {
                                            startWifiReceiver();
                                        }
                                    }
                                }
                        );
                    }

                } else if (
                        WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
                                .equals(action)
                ) {

                    int state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            -1
                    );

                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        nearbyStatus.setText(
                                "Wi-Fi Direct is enabled."
                        );
                    } else {
                        nearbyStatus.setText(
                                "Please enable Wi-Fi Direct/Wi-Fi."
                        );
                    }
                }
            }
        };

        wifiFilter = new IntentFilter();

        wifiFilter.addAction(
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
        );

        wifiFilter.addAction(
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
        );

        wifiFilter.addAction(
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
        );

        wifiFilter.addAction(
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
        );

        registerReceiver(wifiReceiver, wifiFilter);
    }

    private final WifiP2pManager.PeerListListener peerListListener =
            peers -> {

                wifiPeers.clear();
                wifiPeers.addAll(peers.getDeviceList());

                nearbyStatus.setText(
                        "Nearby Wi-Fi Direct devices: "
                                + wifiPeers.size()
                );
            };

    private boolean hasLocationPermission() {

        if (android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.M) {

            return checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void discoverWifiPeers() {

        if (wifiManager == null || wifiChannel == null) {
            nearbyStatus.setText(
                    "Wi-Fi Direct is unavailable."
            );
            return;
        }

        if (!hasLocationPermission()) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION
            );

            return;
        }

        wifiManager.discoverPeers(
                wifiChannel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {

                        nearbyStatus.setText(
                                "Searching for nearby Wi-Fi Direct devices..."
                        );
                    }

                    @Override
                    public void onFailure(int reason) {

                        nearbyStatus.setText(
                                "Wi-Fi discovery failed. Code: " + reason
                        );
                    }
                }
        );
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

        if (requestCode == REQUEST_LOCATION) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                discoverWifiPeers();

            } else {

                nearbyStatus.setText(
                        "Location permission is required for Android 9 Wi-Fi discovery."
                );
            }
        }
    }

    private void showWifiPeers() {

        if (wifiPeers.isEmpty()) {
            discoverWifiPeers();

            nearbyStatus.setText(
                    "No devices loaded. Discovery started."
            );

            return;
        }

        String[] names = new String[wifiPeers.size()];

        for (int i = 0; i < wifiPeers.size(); i++) {

            WifiP2pDevice device = wifiPeers.get(i);

            names[i] = device.deviceName
                    + "\n"
                    + device.deviceAddress
                    + "\n"
                    + device.status;
        }

        new AlertDialog.Builder(this)
                .setTitle("Nearby Wi-Fi Direct Devices")
                .setItems(names, (dialog, which) -> {

                    connectWifiPeer(wifiPeers.get(which));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void connectWifiPeer(WifiP2pDevice device) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        nearbyStatus.setText(
                "Connecting to " + device.deviceName + "..."
        );

        wifiManager.connect(
                wifiChannel,
                config,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {

                        nearbyStatus.setText(
                                "Connection request sent to "
                                        + device.deviceName
                        );
                    }

                    @Override
                    public void onFailure(int reason) {

                        nearbyStatus.setText(
                                "Connection failed. Code: " + reason
                        );
                    }
                }
        );
    }

    // ==================================================
    // WI-FI DIRECT RECEIVING
    // ==================================================

    private synchronized void startWifiReceiver() {

        if (wifiListening || activityDestroyed) {
            return;
        }

        wifiListening = true;

        networkExecutor.execute(() -> {

            try (ServerSocket serverSocket =
                         new ServerSocket(TRANSFER_PORT)) {

                while (wifiListening && !activityDestroyed) {

                    try (Socket socket = serverSocket.accept()) {

                        receiveTransfer(socket);
                    }
                }

            } catch (Exception e) {

                if (!activityDestroyed) {
                    showNearbyMessage(
                            "Wi-Fi receiver stopped: " + safeError(e)
                    );
                }

            } finally {

                wifiListening = false;
            }
        });

        nearbyStatus.setText(
                "Wi-Fi Direct receiver started."
        );
    }

    private void stopWifiReceiver() {
        wifiListening = false;
    }

    // ==================================================
    // BLUETOOTH DEVICES
    // ==================================================

    private void showBluetoothDevices() {

        if (bluetoothAdapter == null) {
            nearbyStatus.setText(
                    "Bluetooth is not available."
            );
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {

            nearbyStatus.setText(
                    "Please enable Bluetooth first."
            );

            return;
        }

        Set<BluetoothDevice> paired =
                bluetoothAdapter.getBondedDevices();

        if (paired == null || paired.isEmpty()) {

            nearbyStatus.setText(
                    "No paired Bluetooth devices. Pair both devices in Android Settings."
            );

            return;
        }

        List<BluetoothDevice> devices =
                new ArrayList<>(paired);

        String[] labels = new String[devices.size()];

        for (int i = 0; i < devices.size(); i++) {

            BluetoothDevice device = devices.get(i);

            labels[i] = device.getName()
                    + "\n"
                    + device.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Paired Bluetooth Devices")
                .setItems(labels, (dialog, which) -> {

                    BluetoothDevice selected =
                            devices.get(which);

                    selectedBluetoothAddress =
                            selected.getAddress();

                    nearbyStatus.setText(
                            "Selected Bluetooth device: "
                                    + selected.getName()
                    );
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ==================================================
    // BLUETOOTH RECEIVING
    // ==================================================

    private synchronized void startBluetoothReceiver() {

        if (bluetoothAdapter == null) {
            nearbyStatus.setText(
                    "Bluetooth hardware unavailable."
            );
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            nearbyStatus.setText(
                    "Enable Bluetooth before receiving."
            );
            return;
        }

        if (bluetoothListening) {
            nearbyStatus.setText(
                    "Bluetooth receiver is already running."
            );
            return;
        }

        bluetoothListening = true;

        networkExecutor.execute(() -> {

            try {

                bluetoothServerSocket =
                        bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                                "Falcon Offline Transfer",
                                BLUETOOTH_UUID
                        );

                showNearbyMessage(
                        "Bluetooth receiver is listening."
                );

                while (bluetoothListening
                        && !activityDestroyed) {

                    BluetoothSocket socket =
                            bluetoothServerSocket.accept();

                    if (socket != null) {

                        try (BluetoothSocket connected = socket) {
                            receiveTransfer(
                                    connected.getInputStream(),
                                    connected.getOutputStream()
                            );
                        }
                    }
                }

            } catch (Exception e) {

                if (!activityDestroyed) {
                    showNearbyMessage(
                            "Bluetooth receiver stopped: "
                                    + safeError(e)
                    );
                }

            } finally {

                bluetoothListening = false;

                try {
                    if (bluetoothServerSocket != null) {
                        bluetoothServerSocket.close();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ==================================================
    // OFFLINE FILE SENDING
    // ==================================================

    private void validateOfflineSelection() {

        if (selectedFileUri == null) {
            throw new IllegalArgumentException(
                    "Choose a file first."
            );
        }

        requirePublicKey();
    }

    private File createEncryptedOfflineFile() throws Exception {

        Uri uri = selectedFileUri;
        String publicKey = requirePublicKey();

        File source = copyUriToCache(uri);

        File encrypted = File.createTempFile(
                "falcon_offline_",
                ".ftx",
                getCacheDir()
        );

        try {

            FalconTransferCrypto.encryptForRecipient(
                    source,
                    encrypted,
                    publicKey
            );

            return encrypted;

        } catch (Exception e) {

            encrypted.delete();
            throw e;

        } finally {

            source.delete();
        }
    }

    private void sendViaWifi() {

        try {

            validateOfflineSelection();

            WifiP2pInfo info = wifiInfo;

            if (info == null
                    || !info.groupFormed
                    || info.groupOwnerAddress == null) {

                throw new IllegalStateException(
                        "Connect to a Wi-Fi Direct device first."
                );
            }

            String host = info.groupOwnerAddress.getHostAddress();
            String fileName = selectedFileName;

            runTask(
                    "Preparing encrypted Wi-Fi Direct transfer...",
                    () -> {

                        File encrypted = null;

                        try {

                            encrypted = createEncryptedOfflineFile();

                            try (Socket socket = new Socket()) {

                                socket.connect(
                                        new InetSocketAddress(
                                                host,
                                                TRANSFER_PORT
                                        ),
                                        15000
                                );

                                sendTransfer(
                                        socket,
                                        encrypted,
                                        fileName
                                );
                            }

                            return "Encrypted file sent over Wi-Fi Direct.";

                        } finally {

                            if (encrypted != null) {
                                encrypted.delete();
                            }
                        }
                    }
            );

        } catch (Exception e) {

            status.setText("Error: " + safeError(e));
        }
    }

    private void sendViaBluetooth() {

        try {

            validateOfflineSelection();

            if (bluetoothAdapter == null
                    || !bluetoothAdapter.isEnabled()) {

                throw new IllegalStateException(
                        "Bluetooth is not enabled."
                );
            }

            if (selectedBluetoothAddress.isEmpty()) {

                throw new IllegalStateException(
                        "Select a paired Bluetooth device first."
                );
            }

            BluetoothDevice device =
                    bluetoothAdapter.getRemoteDevice(
                            selectedBluetoothAddress
                    );

            String fileName = selectedFileName;

            runTask(
                    "Preparing Bluetooth encrypted transfer...",
                    () -> {

                        File encrypted = null;

                        try {

                            encrypted = createEncryptedOfflineFile();

                            bluetoothAdapter.cancelDiscovery();

                            try (BluetoothSocket socket =
                                         device.createRfcommSocketToServiceRecord(
                                                 BLUETOOTH_UUID
                                         )) {

                                socket.connect();

                                sendTransfer(
                                        socket.getOutputStream(),
                                        socket.getInputStream(),
                                        encrypted,
                                        fileName
                                );
                            }

                            return "Encrypted file sent via Bluetooth.";

                        } finally {

                            if (encrypted != null) {
                                encrypted.delete();
                            }
                        }
                    }
            );

        } catch (Exception e) {

            status.setText("Error: " + safeError(e));
        }
    }

    // ==================================================
    // COMMON ENCRYPTED TRANSFER PROTOCOL
    // ==================================================

    private void sendTransfer(
            Socket socket,
            File encrypted,
            String fileName
    ) throws Exception {

        sendTransfer(
                socket.getOutputStream(),
                socket.getInputStream(),
                encrypted,
                fileName
        );
    }

    private void sendTransfer(
            OutputStream output,
            InputStream input,
            File encrypted,
            String fileName
    ) throws Exception {

        DataOutputStream dataOutput =
                new DataOutputStream(
                        new BufferedOutputStream(output)
                );

        DataInputStream dataInput =
                new DataInputStream(
                        new BufferedInputStream(input)
                );

        dataOutput.writeUTF(TRANSFER_MAGIC);
        dataOutput.writeUTF(sanitizeFileName(fileName));
        dataOutput.writeLong(encrypted.length());

        try (InputStream fileInput =
                     new FileInputStream(encrypted)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int count;

            while ((count = fileInput.read(buffer)) != -1) {
                dataOutput.write(buffer, 0, count);
            }
        }

        dataOutput.flush();

        String response = dataInput.readUTF();

        if (!"OK".equals(response)) {
            throw new IOException(
                    "Receiver rejected the encrypted file."
            );
        }
    }

    private void receiveTransfer(Socket socket) throws Exception {

        receiveTransfer(
                socket.getInputStream(),
                socket.getOutputStream()
        );
    }

    private void receiveTransfer(
            InputStream input,
            OutputStream output
    ) throws Exception {

        DataInputStream dataInput =
                new DataInputStream(
                        new BufferedInputStream(input)
                );

        DataOutputStream dataOutput =
                new DataOutputStream(
                        new BufferedOutputStream(output)
                );

        String magic = dataInput.readUTF();

        if (!TRANSFER_MAGIC.equals(magic)) {
            throw new SecurityException(
                    "Invalid Falcon transfer protocol."
            );
        }

        String originalName =
                sanitizeFileName(dataInput.readUTF());

        long length = dataInput.readLong();

        if (length <= 0 || length > MAX_FILE_SIZE + 1024 * 1024) {
            throw new IOException(
                    "Invalid encrypted file size."
            );
        }

        File encrypted = File.createTempFile(
                "falcon_nearby_received_",
                ".ftx",
                getCacheDir()
        );

        File decrypted = null;

        try {

            try (OutputStream fileOutput =
                         new FileOutputStream(encrypted)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = length;

                while (remaining > 0) {

                    int count = dataInput.read(
                            buffer,
                            0,
                            (int) Math.min(
                                    buffer.length,
                                    remaining
                            )
                    );

                    if (count == -1) {
                        throw new EOFException(
                                "Transfer ended unexpectedly."
                        );
                    }

                    fileOutput.write(buffer, 0, count);
                    remaining -= count;
                }

                fileOutput.flush();
            }

            File folder = getReceivedFolder();

            String storedName =
                    System.currentTimeMillis()
                            + "_"
                            + originalName;

            decrypted = new File(folder, storedName);

            FalconTransferCrypto.decryptReceivedFile(
                    encrypted,
                    decrypted
            );

            dataOutput.writeUTF("OK");
            dataOutput.flush();

            File saved = decrypted;

            runOnUiThread(() -> {

                if (canUpdateUI()) {

                    addReceivedFileButton(saved);

                    inbox.setText(
                            "Nearby file received successfully:\n"
                                    + saved.getName()
                    );

                    status.setText(
                            "Nearby encrypted file received and decrypted."
                    );
                }
            });

        } catch (Exception e) {

            if (decrypted != null && decrypted.exists()) {
                decrypted.delete();
            }

            try {
                dataOutput.writeUTF("ERROR");
                dataOutput.flush();
            } catch (Exception ignored) {
            }

            throw e;

        } finally {

            if (encrypted.exists()) {
                encrypted.delete();
            }
        }
    }

    // ==================================================
    // RECEIVED FILE STORAGE
    // ==================================================

    private File getReceivedFolder() throws IOException {

        File base = getExternalFilesDir(null);

        if (base == null) {
            base = getFilesDir();
        }

        File folder = new File(
                base,
                "Falcon Received"
        );

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException(
                    "Cannot create received files folder."
            );
        }

        return folder;
    }

    private String validateFileId(String fileId) {

        if (fileId == null
                || !fileId.matches("[A-Za-z0-9_-]{1,128}")) {

            throw new IllegalArgumentException(
                    "Invalid file identifier."
            );
        }

        return fileId;
    }

    // ==================================================
    // DISPLAY RECEIVED FILES
    // ==================================================

    private void loadPreviouslyReceivedFiles() {

        try {

            File[] files = getReceivedFolder().listFiles();

            if (files == null) {
                return;
            }

            Arrays.sort(
                    files,
                    Comparator.comparingLong(File::lastModified)
                            .reversed()
            );

            for (File file : files) {

                if (file.isFile()) {
                    addReceivedFileButton(file);
                }
            }

        } catch (Exception e) {

            status.setText(
                    "Unable to load previously received files."
            );
        }
    }

    private void addReceivedFileButton(File file) {

        if (!canUpdateUI()
                || receivedFilesContainer == null
                || file == null
                || !file.exists()) {
            return;
        }

        Button open = new Button(this);

        open.setAllCaps(false);
        open.setTextSize(13);
        open.setText("Open: " + file.getName());

        open.setOnClickListener(v -> openReceivedFile(file));

        receivedFilesContainer.addView(open);
    }

    // ==================================================
    // OPEN RECEIVED FILE
    // ==================================================

    private void openReceivedFile(File file) {

        try {

            if (file == null || !file.exists() || !file.isFile()) {
                throw new IOException(
                        "Received file no longer exists."
                );
            }

            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority(
                            getPackageName() + ".falconfiles"
                    )
                    .appendPath("received")
                    .appendPath(file.getName())
                    .build();

            String mimeType =
                    getContentResolver().getType(uri);

            if (mimeType == null) {
                mimeType = "*/*";
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(uri, mimeType);

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            if (intent.resolveActivity(getPackageManager()) == null) {

                status.setText(
                        "No compatible app found to open this file."
                );

                return;
            }

            startActivity(
                    Intent.createChooser(
                            intent,
                            "Open received file"
                    )
            );

        } catch (Exception e) {

            status.setText(
                    "Unable to open file: " + safeError(e)
            );
        }
    }

    // ==================================================
    // INTERNET INBOX
    // ==================================================

    private void refreshInbox() {

        try {

            configureServer();

            String id = requireId(ownId, "Your Falcon ID");

            runTask(
                    "Loading inbox...",
                    () -> {

                        JSONObject result =
                                FalconRelayClient.getInboxResult(id);

                        JSONArray messages =
                                result.optJSONArray("messages");

                        JSONArray files =
                                result.optJSONArray("files");

                        StringBuilder output =
                                new StringBuilder();

                        int messageCount = 0;
                        int receivedCount = 0;

                        if (messages != null) {

                            for (int i = 0;
                                 i < messages.length();
                                 i++) {

                                JSONObject item =
                                        messages.getJSONObject(i);

                                String messageId =
                                        item.getString("messageId");

                                String senderId =
                                        item.getString("senderId");

                                String payload =
                                        item.getString("encryptedPayload");

                                try {

                                    JSONObject envelope =
                                            new JSONObject(payload);

                                    if (!id.equals(
                                            envelope.getString("recipientId")
                                    )) {
                                        throw new SecurityException(
                                                "Recipient mismatch."
                                        );
                                    }

                                    if (!senderId.equals(
                                            envelope.getString("senderId")
                                    )) {
                                        throw new SecurityException(
                                                "Sender mismatch."
                                        );
                                    }

                                    String plaintext =
                                            FalconMessageCrypto.decrypt(
                                                    payload
                                            );

                                    output.append("Message from: ")
                                            .append(senderId)
                                            .append("\n")
                                            .append(plaintext)
                                            .append("\n\n");

                                    messageCount++;

                                    FalconRelayClient.acknowledge(
                                            messageId
                                    );

                                } catch (Exception error) {

                                    output.append(
                                            "Message could not be verified or decrypted.\n\n"
                                    );
                                }
                            }
                        }

                        if (files != null) {

                            File folder = getReceivedFolder();

                            for (int i = 0;
                                 i < files.length();
                                 i++) {

                                JSONObject item =
                                        files.getJSONObject(i);

                                String fileId = validateFileId(
                                        item.getString("fileId")
                                );

                                String originalName =
                                        sanitizeFileName(
                                                item.optString(
                                                        "originalName",
                                                        "ReceivedFile"
                                                )
                                        );

                                File encrypted = null;
                                File decrypted = null;

                                try {

                                    encrypted = File.createTempFile(
                                            "falcon_received_",
                                            ".ftx",
                                            getCacheDir()
                                    );

                                    FalconRelayClient.downloadEncryptedFile(
                                            fileId,
                                            encrypted
                                    );

                                    decrypted = new File(
                                            folder,
                                            System.currentTimeMillis()
                                                    + "_"
                                                    + originalName
                                    );

                                    FalconTransferCrypto.decryptReceivedFile(
                                            encrypted,
                                            decrypted
                                    );

                                    FalconRelayClient.acknowledgeFile(
                                            fileId
                                    );

                                    File saved = decrypted;

                                    output.append("File from: ")
                                            .append(item.optString("senderId"))
                                            .append("\nSaved: ")
                                            .append(saved.getName())
                                            .append("\n\n");

                                    receivedCount++;

                                    runOnUiThread(() -> {

                                        if (canUpdateUI()) {
                                            addReceivedFileButton(saved);
                                        }
                                    });

                                } catch (Exception error) {

                                    if (decrypted != null
                                            && decrypted.exists()) {
                                        decrypted.delete();
                                    }

                                    output.append(
                                            "File could not be received: "
                                    ).append(originalName).append("\n\n");

                                } finally {

                                    if (encrypted != null) {
                                        encrypted.delete();
                                    }
                                }
                            }
                        }

                        if (output.length() == 0) {
                            output.append(
                                    "No pending messages or files."
                            );
                        }

                        String finalOutput = output.toString();

                        runOnUiThread(() -> {

                            if (canUpdateUI()) {
                                inbox.setText(finalOutput);
                            }
                        });

                        return "Inbox checked.\nMessages: "
                                + messageCount
                                + "\nFiles received: "
                                + receivedCount;
                    }
            );

        } catch (Exception e) {

            status.setText("Error: " + safeError(e));
        }
    }

    // ==================================================
    // UI THREAD HELPER
    // ==================================================

    private void showNearbyMessage(String text) {

        runOnUiThread(() -> {

            if (canUpdateUI() && nearbyStatus != null) {
                nearbyStatus.setText(text);
            }
        });
    }

    // ==================================================
    // ACTIVITY CLEANUP
    // ==================================================

    @Override
    protected void onDestroy() {

        activityDestroyed = true;

        wifiListening = false;
        bluetoothListening = false;

        try {

            if (wifiReceiver != null) {
                unregisterReceiver(wifiReceiver);
            }

        } catch (Exception ignored) {
        }

        try {

            if (bluetoothServerSocket != null) {
                bluetoothServerSocket.close();
            }

        } catch (Exception ignored) {
        }

        executor.shutdownNow();
        networkExecutor.shutdownNow();

        super.onDestroy();
    }
}