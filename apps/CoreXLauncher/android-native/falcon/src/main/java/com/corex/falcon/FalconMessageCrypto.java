package com.corex.falcon;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class FalconMessageCrypto {

    private static final int VERSION = 2;

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int AES_KEY_LENGTH = 32;

    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private static final int MAX_ENVELOPE_BYTES = 2 * 1024 * 1024;

    private static final String ALGORITHM = "AES-256-GCM";
    private static final String KEY_ALGORITHM = "RSA-OAEP-SHA256";

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private FalconMessageCrypto() {
    }

    public static String encrypt(
            String message,
            String senderId,
            String recipientId,
            String recipientPublicKey
    ) throws Exception {

        validateIdentity(senderId, "Sender ID");
        validateIdentity(recipientId, "Recipient ID");

        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Message is empty."
            );
        }

        byte[] plaintext =
                message.getBytes(StandardCharsets.UTF_8);

        if (plaintext.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "Message exceeds the 1 MB limit."
            );
        }

        KeyGenerator generator =
                KeyGenerator.getInstance("AES");

        generator.init(256);

        SecretKey aesKey = generator.generateKey();

        byte[] aesKeyBytes = aesKey.getEncoded();

        byte[] iv = new byte[IV_LENGTH];

        SECURE_RANDOM.nextBytes(iv);

        byte[] ciphertext;

        try {
            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding"
            );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            cipher.updateAAD(
                    createAAD(senderId, recipientId)
            );

            ciphertext = cipher.doFinal(plaintext);

        } finally {
            Arrays.fill(aesKeyBytes, (byte) 0);
        }

        byte[] wrappedKey = FalconIdentity.encryptForRecipient(
                aesKey.getEncoded(),
                recipientPublicKey
        );

        JSONObject envelope = new JSONObject();

        envelope.put("version", VERSION);
        envelope.put("algorithm", ALGORITHM);
        envelope.put("keyAlgorithm", KEY_ALGORITHM);

        envelope.put("senderId", senderId);
        envelope.put("recipientId", recipientId);

        envelope.put(
                "wrappedKey",
                Base64.encodeToString(
                        wrappedKey,
                        Base64.NO_WRAP
                )
        );

        envelope.put(
                "iv",
                Base64.encodeToString(
                        iv,
                        Base64.NO_WRAP
                )
        );

        envelope.put(
                "ciphertext",
                Base64.encodeToString(
                        ciphertext,
                        Base64.NO_WRAP
                )
        );

        return envelope.toString();
    }

    public static String decrypt(
            String envelopeJson
    ) throws Exception {

        if (envelopeJson == null
                || envelopeJson.isEmpty()) {

            throw new IllegalArgumentException(
                    "Encrypted message is empty."
            );
        }

        if (envelopeJson.length() > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException(
                    "Encrypted message exceeds the size limit."
            );
        }

        JSONObject envelope =
                new JSONObject(envelopeJson);

        int version = envelope.getInt("version");

        if (version != 1 && version != VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported message version."
            );
        }

        String senderId =
                envelope.getString("senderId");

        String recipientId =
                envelope.getString("recipientId");

        validateIdentity(senderId, "Sender ID");
        validateIdentity(recipientId, "Recipient ID");

        if (version == VERSION) {

            if (!ALGORITHM.equals(
                    envelope.getString("algorithm"))) {

                throw new IllegalArgumentException(
                        "Unsupported encryption algorithm."
                );
            }

            if (!KEY_ALGORITHM.equals(
                    envelope.getString("keyAlgorithm"))) {

                throw new IllegalArgumentException(
                        "Unsupported key encryption algorithm."
                );
            }

        } else {

            if (!"AES-256-GCM".equals(
                    envelope.getString("algorithm"))) {

                throw new IllegalArgumentException(
                        "Unsupported legacy encryption algorithm."
                );
            }

            if (!"RSA-OAEP-SHA256".equals(
                    envelope.getString("keyAlgorithm"))) {

                throw new IllegalArgumentException(
                        "Unsupported legacy key algorithm."
                );
            }
        }

        byte[] wrappedKey = decodeField(
                envelope,
                "wrappedKey",
                1024
        );

        byte[] iv = decodeField(
                envelope,
                "iv",
                IV_LENGTH
        );

        byte[] ciphertext = decodeField(
                envelope,
                "ciphertext",
                MAX_MESSAGE_BYTES + 16
        );

        if (iv.length != IV_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid encryption IV."
            );
        }

        if (ciphertext.length < 16) {
            throw new IllegalArgumentException(
                    "Encrypted message is incomplete."
            );
        }

        byte[] aesKeyBytes =
                FalconIdentity.decryptForSelf(wrappedKey);

        if (aesKeyBytes.length != AES_KEY_LENGTH) {

            Arrays.fill(aesKeyBytes, (byte) 0);

            throw new IllegalArgumentException(
                    "Invalid AES key length."
            );
        }

        try {

            SecretKey aesKey = new SecretKeySpec(
                    aesKeyBytes,
                    "AES"
            );

            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding"
            );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] aad;

            if (version == 1) {

                // Backward compatibility with previous messages.
                aad = (senderId + "|" + recipientId)
                        .getBytes(StandardCharsets.UTF_8);

            } else {

                aad = createAAD(senderId, recipientId);
            }

            cipher.updateAAD(aad);

            byte[] plaintext = cipher.doFinal(ciphertext);

            if (plaintext.length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException(
                        "Decrypted message exceeds the size limit."
                );
            }

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8
            );

        } finally {

            Arrays.fill(aesKeyBytes, (byte) 0);
        }
    }

    private static byte[] createAAD(
            String senderId,
            String recipientId
    ) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        DataOutputStream data =
                new DataOutputStream(output);

        data.writeInt(VERSION);

        writeField(data, ALGORITHM);
        writeField(data, KEY_ALGORITHM);

        writeField(data, senderId);
        writeField(data, recipientId);

        data.flush();

        return output.toByteArray();
    }

    private static void writeField(
            DataOutputStream output,
            String value
    ) throws Exception {

        byte[] bytes =
                value.getBytes(StandardCharsets.UTF_8);

        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] decodeField(
            JSONObject envelope,
            String field,
            int maximumLength
    ) throws Exception {

        String value = envelope.getString(field);

        if (value == null || value.isEmpty()
                || value.length() > maximumLength * 2) {

            throw new IllegalArgumentException(
                    "Invalid encrypted field: " + field
            );
        }

        byte[] decoded = Base64.decode(
                value,
                Base64.DEFAULT
        );

        if (decoded.length == 0
                || decoded.length > maximumLength) {

            throw new IllegalArgumentException(
                    "Invalid encrypted field size: " + field
            );
        }

        return decoded;
    }

    private static void validateIdentity(
            String identity,
            String fieldName
    ) {

        if (identity == null
                || identity.trim().isEmpty()
                || identity.length() > 128) {

            throw new IllegalArgumentException(
                    "Invalid " + fieldName + "."
            );
        }
    }
}