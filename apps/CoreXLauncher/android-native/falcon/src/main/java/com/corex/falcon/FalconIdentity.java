package com.corex.falcon;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;

import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class FalconIdentity {

    private static final String KEY_ALIAS =
            "CoreX_Falcon_Identity_RSA";

    private static final String KEYSTORE =
            "AndroidKeyStore";

    private FalconIdentity() {
    }

    private static KeyPair getOrCreateIdentity() throws Exception {

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.PrivateKeyEntry entry =
                    (KeyStore.PrivateKeyEntry)
                            keyStore.getEntry(KEY_ALIAS, null);

            return new KeyPair(
                    entry.getCertificate().getPublicKey(),
                    entry.getPrivateKey()
            );
        }

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        KEYSTORE
                );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setKeySize(3072)
                        .setDigests(
                                KeyProperties.DIGEST_SHA256,
                                KeyProperties.DIGEST_SHA512
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build();

        generator.initialize(specification);

        return generator.generateKeyPair();
    }

    public static String getPublicKey() throws Exception {

        PublicKey publicKey =
                getOrCreateIdentity().getPublic();

        return Base64.encodeToString(
                publicKey.getEncoded(),
                Base64.NO_WRAP
        );
    }

    public static String getFingerprint() throws Exception {

        byte[] publicKey =
                getOrCreateIdentity()
                        .getPublic()
                        .getEncoded();

        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(publicKey);

        StringBuilder result = new StringBuilder();

        for (byte value : digest) {

            result.append(
                    String.format("%02X", value & 0xFF)
            );
        }

        return result.toString();
    }

    public static byte[] encryptForRecipient(
            byte[] data,
            String recipientPublicKey
    ) throws Exception {

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException(
                    "Encryption data is empty."
            );
        }

        if (recipientPublicKey == null
                || recipientPublicKey.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Recipient public key is missing."
            );
        }

        byte[] decodedKey = Base64.decode(
                recipientPublicKey,
                Base64.DEFAULT
        );

        PublicKey publicKey = KeyFactory
                .getInstance("RSA")
                .generatePublic(
                        new X509EncodedKeySpec(decodedKey)
                );

        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        );

        OAEPParameterSpec parameters =
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA1,
                        PSource.PSpecified.DEFAULT
                );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                parameters
        );

        return cipher.doFinal(data);
    }

    public static byte[] decryptForSelf(
            byte[] encryptedData
    ) throws Exception {

        if (encryptedData == null
                || encryptedData.length == 0) {

            throw new IllegalArgumentException(
                    "Encrypted data is empty."
            );
        }

        Cipher cipher = Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        );

        OAEPParameterSpec parameters =
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA1,
                        PSource.PSpecified.DEFAULT
                );

        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateIdentity().getPrivate(),
                parameters
        );

        return cipher.doFinal(encryptedData);
    }

    public static boolean verifyFingerprint(
            String publicKeyBase64,
            String expectedFingerprint
    ) throws Exception {

        if (publicKeyBase64 == null
                || expectedFingerprint == null) {

            return false;
        }

        byte[] decodedKey = Base64.decode(
                publicKeyBase64,
                Base64.DEFAULT
        );

        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(decodedKey);

        StringBuilder actual = new StringBuilder();

        for (byte value : digest) {

            actual.append(
                    String.format("%02X", value & 0xFF)
            );
        }

        return MessageDigest.isEqual(
                actual.toString().getBytes(StandardCharsets.UTF_8),
                expectedFingerprint
                        .replace(" ", "")
                        .toUpperCase()
                        .getBytes(StandardCharsets.UTF_8)
        );
    }
}