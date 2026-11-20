package com.corex.pulse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class PulseRelayClient {

    private static volatile String serverUrl =
            "http://10.0.2.2:5001";

    private static volatile String accessToken = "";

    private PulseRelayClient() {
    }

    public static void setServerUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Server URL is required"
            );
        }

        serverUrl = url.trim().replaceAll("/+$", "");
    }

    public static String getServerUrl() {
        return serverUrl;
    }

    public static void setAccessToken(String token) {
        accessToken = token == null ? "" : token.trim();
    }

    public static String getAccessToken() {
        return accessToken;
    }

    private static JSONObject request(
            String method,
            String endpoint,
            JSONObject body,
            boolean authenticated
    ) throws Exception {

        String token = accessToken;

        if (authenticated && token.isEmpty()) {
            throw new IllegalStateException(
                    "Please login to CoreX Pulse first."
            );
        }

        URL url = new URL(serverUrl + endpoint);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        // Authorization must be set before connecting.
        if (authenticated) {
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + token
            );
        }

        if (body != null) {
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            byte[] data = body.toString()
                    .getBytes(StandardCharsets.UTF_8);

            try (OutputStream output =
                         connection.getOutputStream()) {

                output.write(data);
                output.flush();
            }
        }

        try {
            int status = connection.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            StringBuilder response = new StringBuilder();

            if (stream != null) {
                try (BufferedReader reader =
                             new BufferedReader(
                                     new InputStreamReader(
                                             stream,
                                             StandardCharsets.UTF_8
                                     )
                             )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            JSONObject result =
                    response.length() > 0
                            ? new JSONObject(response.toString())
                            : new JSONObject();

            if (status < 200 || status >= 300) {
                String message = result.optString(
                        "error",
                        "Server request failed. HTTP " + status
                );

                throw new Exception(message);
            }

            return result;

        } finally {
            connection.disconnect();
        }
    }

    public static JSONObject health() throws Exception {
        return request(
                "GET",
                "/api/health",
                null,
                false
        );
    }

    public static JSONObject register(
            String displayName,
            String email,
            String phone,
            String password,
            String publicKey
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("displayName", displayName.trim());

        if (email != null && !email.trim().isEmpty()) {
            body.put("email", email.trim());
        }

        if (phone != null && !phone.trim().isEmpty()) {
            body.put("phone", phone.trim());
        }

        body.put("password", password);
        body.put("publicKey", publicKey);

        JSONObject result = request(
                "POST",
                "/api/pulse/register",
                body,
                false
        );

        saveTokenFromResponse(result);

        return result;
    }

    public static JSONObject login(
            String identifier,
            String password
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("identifier", identifier.trim());
        body.put("password", password);

        JSONObject result = request(
                "POST",
                "/api/pulse/login",
                body,
                false
        );

        saveTokenFromResponse(result);

        return result;
    }

    private static void saveTokenFromResponse(
            JSONObject result
    ) {
        String token = result.optString("token", "");

        if (!token.isEmpty()) {
            setAccessToken(token);
        }
    }

    public static JSONObject getMe() throws Exception {
        return request(
                "GET",
                "/api/pulse/me",
                null,
                true
        );
    }

    public static JSONObject logout() throws Exception {
        JSONObject result = request(
                "POST",
                "/api/pulse/logout",
                new JSONObject(),
                true
        );

        setAccessToken("");

        return result;
    }

    public static JSONArray searchUsers(
            String query
    ) throws Exception {

        String encoded = URLEncoder.encode(
                query,
                StandardCharsets.UTF_8.name()
        );

        JSONObject result = request(
                "GET",
                "/api/pulse/users/search?q=" + encoded,
                null,
                true
        );

        JSONArray users = result.optJSONArray("users");

        return users != null ? users : new JSONArray();
    }

    public static JSONObject sendMessage(
            String recipientId,
            String encryptedPayload
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("recipientId", recipientId);

        // Backend expects encryptedPayload as a STRING.
        body.put("encryptedPayload", encryptedPayload);

        return request(
                "POST",
                "/api/pulse/messages",
                body,
                true
        );
    }

    public static JSONArray getInbox() throws Exception {

        JSONObject result = request(
                "GET",
                "/api/pulse/inbox",
                null,
                true
        );

        JSONArray messages = result.optJSONArray("messages");

        return messages != null
                ? messages
                : new JSONArray();
    }

    public static JSONObject acknowledgeMessage(
            String messageId
    ) throws Exception {

        return request(
                "POST",
                "/api/pulse/messages/"
                        + URLEncoder.encode(
                                messageId,
                                StandardCharsets.UTF_8.name()
                        )
                        + "/ack",
                new JSONObject(),
                true
        );
    }
}
