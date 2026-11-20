package com.corex.falcon;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class FalconRelayClient {

    private static volatile String serverUrl =
            "http://10.0.2.2:5001";

    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 30000;
    private static final int FILE_TIMEOUT = 120000;

    private static final long MAX_FILE_SIZE =
            100L * 1024L * 1024L;

    private static final int MAX_RESPONSE_SIZE =
            2 * 1024 * 1024;

    private static final int BUFFER_SIZE = 8192;

    private FalconRelayClient() {
    }

    // ==================================================
    // SERVER CONFIGURATION
    // ==================================================

    public static synchronized void setServerUrl(String url) {

        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Server URL is empty."
            );
        }

        String normalized = url.trim()
                .replaceAll("/+$", "");

        try {
            URI uri = URI.create(normalized);

            String scheme = uri.getScheme();

            if (scheme == null
                    || (!scheme.equalsIgnoreCase("https")
                    && !scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {

                throw new IllegalArgumentException(
                        "Invalid Falcon server URL."
                );
            }

        } catch (Exception exception) {

            throw new IllegalArgumentException(
                    "Invalid Falcon server URL.",
                    exception
            );
        }

        serverUrl = normalized;
    }

    public static String getServerUrl() {
        return serverUrl;
    }

    private static URL buildUrl(String endpoint)
            throws Exception {

        if (endpoint == null
                || !endpoint.startsWith("/")
                || endpoint.startsWith("//")) {

            throw new IllegalArgumentException(
                    "Invalid API endpoint."
            );
        }

        return new URL(serverUrl + endpoint);
    }

    private static HttpURLConnection openConnection(
            String method,
            String endpoint,
            int readTimeout
    ) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) buildUrl(endpoint)
                        .openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(readTimeout);

        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        return connection;
    }

    // ==================================================
    // HTTP REQUEST
    // ==================================================

    private static JSONObject request(
            String method,
            String endpoint,
            JSONObject body
    ) throws Exception {

        HttpURLConnection connection =
                openConnection(
                        method,
                        endpoint,
                        READ_TIMEOUT
                );

        try {

            if (body != null) {

                byte[] data = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                if (data.length > MAX_RESPONSE_SIZE) {
                    throw new IllegalArgumentException(
                            "Request body is too large."
                    );
                }

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                connection.setDoOutput(true);

                connection.setFixedLengthStreamingMode(
                        data.length
                );

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(data);
                    output.flush();
                }
            }

            return readJsonResponse(connection);

        } finally {

            connection.disconnect();
        }
    }

    private static JSONObject readJsonResponse(
            HttpURLConnection connection
    ) throws Exception {

        int status = connection.getResponseCode();

        InputStream stream =
                status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String response = readText(
                stream,
                MAX_RESPONSE_SIZE
        );

        JSONObject result;

        try {

            result = new JSONObject(response);

        } catch (Exception exception) {

            throw new Exception(
                    "Server returned an invalid response. HTTP "
                            + status
            );
        }

        if (status < 200 || status >= 300) {

            throw new Exception(
                    result.optString(
                            "error",
                            "Server request failed. HTTP " + status
                    )
            );
        }

        return result;
    }

    private static String readText(
            InputStream input,
            int maximumSize
    ) throws Exception {

        if (input == null) {
            return "";
        }

        try (InputStream stream = input;
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];

            int count;
            long total = 0;

            while ((count = stream.read(buffer)) != -1) {

                total += count;

                if (total > maximumSize) {
                    throw new Exception(
                            "Server response exceeds size limit."
                    );
                }

                output.write(buffer, 0, count);
            }

            return output.toString("UTF-8");
        }
    }

    private static String encodePath(String value) {

        validateId(value, "API identifier");

        return Uri.encode(value, "");
    }

    private static void validateId(
            String value,
            String field
    ) {

        if (value == null
                || value.trim().isEmpty()
                || value.length() > 128) {

            throw new IllegalArgumentException(
                    "Invalid " + field + "."
            );
        }
    }

    // ==================================================
    // HEALTH
    // ==================================================

    public static JSONObject health() throws Exception {

        return request(
                "GET",
                "/api/health",
                null
        );
    }

    // ==================================================
    // REGISTRATION
    // ==================================================

    public static JSONObject register(
            String falconId,
            String publicKey
    ) throws Exception {

        validateId(falconId, "Falcon ID");

        if (publicKey == null
                || publicKey.trim().isEmpty()
                || publicKey.length() > 16384) {

            throw new IllegalArgumentException(
                    "Invalid public key."
            );
        }

        JSONObject body = new JSONObject();

        body.put("falconId", falconId);
        body.put("publicKey", publicKey);

        return request(
                "POST",
                "/api/register",
                body
        );
    }

    // ==================================================
    // USER DIRECTORY
    // ==================================================

    public static JSONObject getUser(
            String falconId
    ) throws Exception {

        validateId(falconId, "Falcon ID");

        return request(
                "GET",
                "/api/users/" + encodePath(falconId),
                null
        );
    }

    // ==================================================
    // ENCRYPTED MESSAGES
    // ==================================================

    public static JSONObject sendEncryptedMessage(
            String senderId,
            String recipientId,
            String encryptedPayload
    ) throws Exception {

        validateId(senderId, "Sender ID");
        validateId(recipientId, "Recipient ID");

        if (encryptedPayload == null
                || encryptedPayload.isEmpty()
                || encryptedPayload.length() > MAX_RESPONSE_SIZE) {

            throw new IllegalArgumentException(
                    "Invalid encrypted message."
            );
        }

        JSONObject body = new JSONObject();

        body.put("senderId", senderId);
        body.put("recipientId", recipientId);
        body.put("encryptedPayload", encryptedPayload);

        return request(
                "POST",
                "/api/messages",
                body
        );
    }

    // ==================================================
    // INBOX
    // ==================================================

    public static JSONObject getInboxResult(
            String falconId
    ) throws Exception {

        validateId(falconId, "Falcon ID");

        return request(
                "GET",
                "/api/inbox/" + encodePath(falconId),
                null
        );
    }

    public static JSONArray getInbox(
            String falconId
    ) throws Exception {

        return getInboxResult(falconId)
                .getJSONArray("messages");
    }

    // ==================================================
    // MESSAGE ACKNOWLEDGEMENT
    // ==================================================

    public static JSONObject acknowledge(
            String messageId
    ) throws Exception {

        validateId(messageId, "Message ID");

        return request(
                "POST",
                "/api/messages/"
                        + encodePath(messageId)
                        + "/ack",
                new JSONObject()
        );
    }

    // ==================================================
    // ENCRYPTED FILE UPLOAD
    // ==================================================

    public static JSONObject uploadEncryptedFile(
            File file,
            String senderId,
            String recipientId,
            String originalName
    ) throws Exception {

        validateId(senderId, "Sender ID");
        validateId(recipientId, "Recipient ID");

        if (file == null
                || !file.exists()
                || !file.isFile()
                || !file.canRead()) {

            throw new IllegalArgumentException(
                    "File does not exist or cannot be read."
            );
        }

        long expectedSize = file.length();

        if (expectedSize <= 0
                || expectedSize > MAX_FILE_SIZE) {

            throw new IllegalArgumentException(
                    "File must be between 1 byte and 100 MB."
            );
        }

        if (originalName == null
                || originalName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Original filename is missing."
            );
        }

        String boundary =
                "FalconBoundary" + UUID.randomUUID();

        HttpURLConnection connection =
                openConnection(
                        "POST",
                        "/api/files",
                        FILE_TIMEOUT
                );

        try {

            connection.setDoOutput(true);

            connection.setChunkedStreamingMode(
                    BUFFER_SIZE
            );

            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
            );

            try (OutputStream output =
                         connection.getOutputStream()) {

                writeField(
                        output,
                        boundary,
                        "senderId",
                        senderId
                );

                writeField(
                        output,
                        boundary,
                        "recipientId",
                        recipientId
                );

                String safeName =
                        sanitizeFilename(originalName);

                writeText(
                        output,
                        "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; "
                                + "name=\"file\"; filename=\""
                                + safeName + ".ftx\"\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "\r\n"
                );

                try (InputStream input =
                             new FileInputStream(file)) {

                    byte[] buffer =
                            new byte[BUFFER_SIZE];

                    int count;
                    long uploaded = 0;

                    while ((count = input.read(buffer)) != -1) {

                        uploaded += count;

                        if (uploaded > MAX_FILE_SIZE
                                || uploaded > expectedSize) {

                            throw new Exception(
                                    "File changed or exceeds upload limit."
                            );
                        }

                        output.write(buffer, 0, count);
                    }

                    if (uploaded != expectedSize) {
                        throw new Exception(
                                "File changed during upload."
                        );
                    }
                }

                writeText(
                        output,
                        "\r\n--" + boundary + "--\r\n"
                );

                output.flush();
            }

            return readJsonResponse(connection);

        } finally {

            connection.disconnect();
        }
    }

    private static String sanitizeFilename(
            String filename
    ) {

        String safe = filename
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_")
                .replace(":", "_")
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();

        if (safe.isEmpty()) {
            return "falcon_file";
        }

        if (safe.length() > 140) {
            safe = safe.substring(0, 140);
        }

        return safe;
    }

    private static void writeField(
            OutputStream output,
            String boundary,
            String name,
            String value
    ) throws Exception {

        writeText(
                output,
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; "
                        + "name=\"" + name + "\"\r\n"
                        + "\r\n"
                        + value
                        + "\r\n"
        );
    }

    private static void writeText(
            OutputStream output,
            String text
    ) throws Exception {

        output.write(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ==================================================
    // ENCRYPTED FILE DOWNLOAD
    // ==================================================

    public static void downloadEncryptedFile(
            String fileId,
            File destination
    ) throws Exception {

        validateId(fileId, "File ID");

        if (destination == null) {
            throw new IllegalArgumentException(
                    "Download destination is missing."
            );
        }

        File absoluteDestination =
                destination.getAbsoluteFile();

        File parent =
                absoluteDestination.getParentFile();

        if (parent == null) {
            throw new Exception(
                    "Invalid download destination."
            );
        }

        if (!parent.exists() && !parent.mkdirs()) {
            throw new Exception(
                    "Cannot create download directory."
            );
        }

        if (absoluteDestination.exists()
                && absoluteDestination.isDirectory()) {

            throw new Exception(
                    "Download destination is a directory."
            );
        }

        File temporaryFile = new File(
                parent,
                absoluteDestination.getName()
                        + ".download-"
                        + UUID.randomUUID()
        );

        File backupFile = new File(
                parent,
                absoluteDestination.getName()
                        + ".backup-"
                        + UUID.randomUUID()
        );

        HttpURLConnection connection =
                openConnection(
                        "GET",
                        "/api/files/" + encodePath(fileId),
                        FILE_TIMEOUT
                );

        boolean completed = false;
        boolean backupCreated = false;

        try {

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {

                String error = readText(
                        connection.getErrorStream(),
                        MAX_RESPONSE_SIZE
                );

                throw new Exception(
                        "File download failed. HTTP "
                                + status + ". " + error
                );
            }

            long contentLength =
                    connection.getContentLengthLong();

            if (contentLength > MAX_FILE_SIZE) {
                throw new Exception(
                        "Downloaded file exceeds 100 MB."
                );
            }

            try (InputStream input =
                         connection.getInputStream();

                 OutputStream output =
                         new FileOutputStream(temporaryFile)) {

                byte[] buffer =
                        new byte[BUFFER_SIZE];

                int count;
                long total = 0;

                while ((count = input.read(buffer)) != -1) {

                    total += count;

                    if (total > MAX_FILE_SIZE) {
                        throw new Exception(
                                "Downloaded file exceeds 100 MB."
                        );
                    }

                    output.write(buffer, 0, count);
                }

                output.flush();
            }

            if (temporaryFile.length() <= 0) {
                throw new Exception(
                        "Downloaded file is empty."
                );
            }

            if (contentLength >= 0
                    && temporaryFile.length() != contentLength) {

                throw new Exception(
                        "Downloaded file size does not match."
                );
            }

            // Preserve an existing destination.
            if (absoluteDestination.exists()) {

                if (!absoluteDestination.renameTo(backupFile)) {
                    throw new Exception(
                            "Cannot safely preserve existing destination."
                    );
                }

                backupCreated = true;
            }

            // Publish downloaded file.
            if (!temporaryFile.renameTo(absoluteDestination)) {

                if (backupCreated) {
                    backupFile.renameTo(absoluteDestination);
                    backupCreated = false;
                }

                throw new Exception(
                        "Cannot finalize downloaded file."
                );
            }

            completed = true;

            if (backupCreated) {
                backupFile.delete();
                backupCreated = false;
            }

        } finally {

            connection.disconnect();

            if (!completed && temporaryFile.exists()) {
                temporaryFile.delete();
            }

            if (backupCreated && backupFile.exists()
                    && !absoluteDestination.exists()) {

                backupFile.renameTo(absoluteDestination);
            }
        }
    }

    // ==================================================
    // FILE ACKNOWLEDGEMENT
    // ==================================================

    public static JSONObject acknowledgeFile(
            String fileId
    ) throws Exception {

        validateId(fileId, "File ID");

        return request(
                "POST",
                "/api/files/"
                        + encodePath(fileId)
                        + "/ack",
                new JSONObject()
        );
    }
}