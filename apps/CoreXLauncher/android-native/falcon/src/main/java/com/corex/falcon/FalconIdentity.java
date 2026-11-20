package com.corex.falcon;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class FalconIdentity {

    private static final String KEY_ALIAS =
            "CoreX_Falcon_Identity_RSA";

    private static final String KEYSTORE =
            "AndroidKeyStore";

    private static final int RSA_KEY_SIZE = 3072;

    private static final int MAX_RSA_KEY_BYTES = 8192;

    // RSA-3072 + OAEP SHA-256 + MGF1 SHA-1
    // Maximum plaintext size = 318 bytes.
    private static final int MAX_RSA_PLAINTEXT_BYTES = 318;

    private static final String RSA_TRANSFORMATION =
            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private static final OAEPParameterSpec OAEP_PARAMETERS =
            new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT
            );

    private FalconIdentity() {
    }

    private static KeyPair getOrCreateIdentity() throws Exception {

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.Entry storedEntry =
                    keyStore.getEntry(KEY_ALIAS, null);

            if (!(storedEntry instanceof KeyStore.PrivateKeyEntry)) {
                throw new InvalidKeyException(
                        "Falcon identity entry is invalid."
                );
            }

            KeyStore.PrivateKeyEntry entry =
                    (KeyStore.PrivateKeyEntry) storedEntry;

            PublicKey publicKey =
                    entry.getCertificate().getPublicKey();

            PrivateKey privateKey =
                    entry.getPrivateKey();

            validateRSAKey(publicKey);
            validateRSAKey(privateKey);

            return new KeyPair(publicKey, privateKey);
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
                        .setKeySize(RSA_KEY_SIZE)
                        .setDigests(
                                KeyProperties.DIGEST_SHA256,
                                KeyProperties.DIGEST_SHA512
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
                        )
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();

        generator.initialize(specification);

        KeyPair keyPair = generator.generateKeyPair();

        validateRSAKey(keyPair.getPublic());
        validateRSAKey(keyPair.getPrivate());

        return keyPair;
    }

    private static void validateRSAKey(java.security.Key key)
            throws Exception {

        if (key == null || !"RSA".equalsIgnoreCase(key.getAlgorithm())) {
            throw new InvalidKeyException(
                    "Invalid RSA key."
            );
        }

        if (!(key instanceof RSAKey)) {
            throw new InvalidKeyException(
                    "Unsupported RSA key implementation."
            );
        }

        int keySize =
                ((RSAKey) key).getModulus().bitLength();

        if (keySize < 2048 || keySize > 4096) {
            throw new InvalidKeyException(
                    "RSA key size is not supported."
            );
        }
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

        return createFingerprint(
                getOrCreateIdentity().getPublic().getEncoded()
        );
    }

    private static String createFingerprint(byte[] encodedKey)
            throws Exception {

        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(encodedKey);

        StringBuilder result =
                new StringBuilder(digest.length * 2);

        for (byte value : digest) {

            result.append(
                    String.format(
                            Locale.US,
                            "%02X",
                            value & 0xFF
                    )
            );
        }

        return result.toString();
    }

    private static PublicKey decodePublicKey(String publicKeyBase64)
            throws Exception {

        if (publicKeyBase64 == null
                || publicKeyBase64.trim().isEmpty()
                || publicKeyBase64.length() > MAX_RSA_KEY_BYTES) {

            throw new InvalidKeyException(
                    "Recipient public key is invalid."
            );
        }

        byte[] decodedKey = Base64.decode(
                publicKeyBase64,
                Base64.DEFAULT
        );

        if (decodedKey.length == 0
                || decodedKey.length > MAX_RSA_KEY_BYTES) {

            throw new InvalidKeyException(
                    "Recipient public key size is invalid."
            );
        }

        PublicKey publicKey = KeyFactory
                .getInstance("RSA")
                .generatePublic(
                        new X509EncodedKeySpec(decodedKey)
                );

        validateRSAKey(publicKey);

        return publicKey;
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

        if (data.length > MAX_RSA_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Data is too large for RSA encryption. "
                            + "Use AES encryption for larger data."
            );
        }

        PublicKey publicKey =
                decodePublicKey(recipientPublicKey);

        Cipher cipher = Cipher.getInstance(
                RSA_TRANSFORMATION
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEP_PARAMETERS
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

        KeyPair identity = getOrCreateIdentity();

        int expectedCiphertextSize =
                (((RSAKey) identity.getPublic())
                        .getModulus().bitLength() + 7) / 8;

        if (encryptedData.length != expectedCiphertextSize) {
            throw new InvalidKeyException(
                    "Encrypted data has an invalid RSA block size."
            );
        }

        Cipher cipher = Cipher.getInstance(
                RSA_TRANSFORMATION
        );

        cipher.init(
                Cipher.DECRYPT_MODE,
                identity.getPrivate(),
                OAEP_PARAMETERS
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

        String normalizedFingerprint =
                expectedFingerprint
                        .replace(" ", "")
                        .replace(":", "")
                        .replace("-", "")
                        .trim()
                        .toUpperCase(Locale.US);

        if (!normalizedFingerprint.matches("[0-9A-F]{64}")) {
            return false;
        }

        PublicKey publicKey;

        try {
            publicKey = decodePublicKey(publicKeyBase64);
        } catch (Exception exception) {
            return false;
        }

        String actualFingerprint =
                createFingerprint(publicKey.getEncoded());

        return MessageDigest.isEqual(
                actualFingerprint.getBytes(StandardCharsets.US_ASCII),
                normalizedFingerprint.getBytes(StandardCharsets.US_ASCII)
        );
    }
}