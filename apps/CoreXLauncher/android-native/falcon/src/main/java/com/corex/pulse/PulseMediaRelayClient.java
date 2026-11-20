package com.corex.pulse;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public final class PulseMediaRelayClient {

    private static final String TAG = "PulseMediaRelay";

    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 120000;

    private PulseMediaRelayClient() {
    }

    private static String baseUrl() {
        String value = PulseRelayClient.getServerUrl();

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CoreX Pulse server URL is missing."
            );
        }

        return value.trim().replaceAll("/+$", "");
    }

    private static String token() {
        String value = PulseRelayClient.getAccessToken();

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Please login to CoreX Pulse first."
            );
        }

        return value.trim();
    }

    private static HttpURLConnection connect(
            String endpoint,
            String method
    ) throws Exception {

        URL url = new URL(baseUrl() + endpoint);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        connection.setRequestProperty(
                "Authorization",
                "Bearer " + token()
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        return connection;
    }

    private static JSONObject readJsonResponse(
            HttpURLConnection connection
    ) throws Exception {

        int status = connection.getResponseCode();

        InputStream stream =
                status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        if (stream == null) {
            throw new IllegalStateException(
                    "Server returned HTTP " + status
            );
        }

        StringBuilder response = new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        stream,
                                        "UTF-8"
                                )
                        )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "Pulse server error (" + status + "): "
                            + response
            );
        }

        return new JSONObject(response.toString());
    }

    public static JSONObject upload(
            File encryptedFile,
            String recipientId,
            String category,
            String originalName,
            String originalMimeType
    ) throws Exception {

        if (encryptedFile == null ||
                !encryptedFile.isFile() ||
                !encryptedFile.canRead()) {

            throw new IllegalArgumentException(
                    "Encrypted media file is unavailable."
            );
        }

        if (recipientId == null || recipientId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Recipient is required."
            );
        }

        if (!category.equals("photo") &&
                !category.equals("video") &&
                !category.equals("document") &&
                !category.equals("voice")) {

            throw new IllegalArgumentException(
                    "Unsupported media category."
            );
        }

        String boundary =
                "----CoreXPulse" + UUID.randomUUID();

        HttpURLConnection connection =
                connect("/api/pulse/files", "POST");

        connection.setDoOutput(true);

        connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=" + boundary
        );

        connection.setChunkedStreamingMode(64 * 1024);

        try {
            try (
                    OutputStream raw =
                            new BufferedOutputStream(
                                    connection.getOutputStream()
                            )
            ) {

                writeField(
                        raw,
                        boundary,
                        "recipientId",
                        recipientId
                );

                writeField(
                        raw,
                        boundary,
                        "category",
                        category
                );

                writeField(
                        raw,
                        boundary,
                        "originalName",
                        originalName == null
                                ? "encrypted-media"
                                : originalName
                );

                writeField(
                        raw,
                        boundary,
                        "originalMimeType",
                        originalMimeType == null
                                ? "application/octet-stream"
                                : originalMimeType
                );

                writeAscii(
                        raw,
                        "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; "
                                + "name=\"file\"; filename=\""
                                + "encrypted-media.bin"
                                + "\"\r\n"
                                + "Content-Type: application/octet-stream\r\n\r\n"
                );

                try (
                        InputStream input =
                                new BufferedInputStream(
                                        new FileInputStream(encryptedFile)
                                )
                ) {
                    byte[] buffer = new byte[64 * 1024];

                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        raw.write(buffer, 0, count);
                    }
                }

                writeAscii(
                        raw,
                        "\r\n--" + boundary + "--\r\n"
                );

                raw.flush();
            }

            return readJsonResponse(connection);

        } finally {
            connection.disconnect();
        }
    }

    private static void writeField(
            OutputStream output,
            String boundary,
            String name,
            String value
    ) throws Exception {

        writeAscii(
                output,
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\""
                        + name + "\"\r\n\r\n"
                        + value + "\r\n"
        );
    }

    private static void writeAscii(
            OutputStream output,
            String value
    ) throws Exception {

        output.write(value.getBytes("UTF-8"));
    }

    public static JSONArray getInbox() throws Exception {

        HttpURLConnection connection =
                connect("/api/pulse/files/inbox", "GET");

        try {
            JSONObject response =
                    readJsonResponse(connection);

            JSONArray files = response.optJSONArray("files");

            return files == null
                    ? new JSONArray()
                    : files;

        } finally {
            connection.disconnect();
        }
    }

    public static File download(
            String fileId,
            File destination
    ) throws Exception {

        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Media file ID is missing."
            );
        }

        if (destination == null) {
            throw new IllegalArgumentException(
                    "Download destination is missing."
            );
        }

        String safeId = fileId.trim();

        if (!safeId.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Invalid media file ID."
            );
        }

        HttpURLConnection connection =
                connect(
                        "/api/pulse/files/" + safeId + "/download",
                        "GET"
                );

        File parent = destination.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File temporary = new File(
                destination.getAbsolutePath() + ".part"
        );

        try {
            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        "Media download failed. HTTP " + status
                );
            }

            try (
                    InputStream input =
                            new BufferedInputStream(
                                    connection.getInputStream()
                            );

                    OutputStream output =
                            new BufferedOutputStream(
                                    new FileOutputStream(temporary)
                            )
            ) {
                byte[] buffer = new byte[64 * 1024];

                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }

            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException(
                        "Unable to replace destination file."
                );
            }

            if (!temporary.renameTo(destination)) {
                throw new IllegalStateException(
                        "Unable to finalize downloaded media."
                );
            }

            return destination;

        } catch (Exception error) {

            if (temporary.exists()) {
                temporary.delete();
            }

            throw error;

        } finally {
            connection.disconnect();
        }
    }

    public static JSONObject acknowledge(
            String fileId
    ) throws Exception {

        if (fileId == null ||
                !fileId.trim().matches("[a-zA-Z0-9_-]+")) {

            throw new IllegalArgumentException(
                    "Invalid media file ID."
            );
        }

        HttpURLConnection connection =
                connect(
                        "/api/pulse/files/"
                                + fileId.trim()
                                + "/ack",
                        "POST"
                );

        connection.setDoOutput(true);
        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        try {
            connection.getOutputStream().close();

            return readJsonResponse(connection);

        } finally {
            connection.disconnect();
        }
    }
}
