package com.corex.falcon;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.GCMParameterSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.security.KeyStore;
import java.security.SecureRandom;

import java.util.UUID;

public final class FalconCrypto {

    private static final String KEY_ALIAS = "CoreX_Falcon_AES_KEY";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final byte[] MAGIC = {
            'F', 'X', 'E', '1'
    };

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MAX_FILENAME_LENGTH = 4096;

    private FalconCrypto() {
    }

    private static SecretKey getOrCreateKey() throws Exception {

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.Entry entry =
                    keyStore.getEntry(KEY_ALIAS, null);

            if (entry instanceof KeyStore.SecretKeyEntry) {

                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build();

        keyGenerator.init(specification);

        return keyGenerator.generateKey();
    }

    private static String getDisplayName(
            Context context,
            Uri uri
    ) {

        String name = null;

        try (Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {

            if (cursor != null
                    && cursor.moveToFirst()) {

                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }

        } catch (Exception ignored) {
        }

        if (name == null || name.trim().isEmpty()) {
            name = "Falcon_File";
        }

        return sanitizeFileName(name);
    }

    private static String sanitizeFileName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return "Falcon_File";
        }

        String cleaned = name.replaceAll(
                "[\\\\/:*?\"<>|\\p{Cntrl}]",
                "_"
        );

        cleaned = cleaned.trim();

        if (cleaned.isEmpty()) {
            return "Falcon_File";
        }

        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 180);
        }

        return cleaned;
    }

    private static byte[] createAuthenticatedHeader(
            String originalName
    ) {

        byte[] nameBytes = originalName.getBytes(
                StandardCharsets.UTF_8
        );

        ByteBuffer buffer = ByteBuffer.allocate(
                MAGIC.length + 4 + nameBytes.length
        );

        buffer.put(MAGIC);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);

        return buffer.array();
    }

    public static File encrypt(
            Context context,
            Uri sourceUri
    ) throws Exception {

        if (sourceUri == null) {
            throw new IllegalArgumentException(
                    "Source file is missing."
            );
        }

        String originalName = getDisplayName(
                context,
                sourceUri
        );

        byte[] authenticatedHeader =
                createAuthenticatedHeader(originalName);

        byte[] iv = new byte[IV_LENGTH];

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(TAG_LENGTH_BITS, iv)
        );

        // Filename metadata is authenticated by AES-GCM.
        cipher.updateAAD(authenticatedHeader);

        File vaultDirectory =
                FalconVault.getVaultDirectory(context);

        File encryptedFile = new File(
                vaultDirectory,
                "falcon_"
                        + System.currentTimeMillis()
                        + "_"
                        + UUID.randomUUID()
                        + ".fxe"
        );

        boolean success = false;

        try (
                InputStream input =
                        context.getContentResolver()
                                .openInputStream(sourceUri);

                FileOutputStream output =
                        new FileOutputStream(encryptedFile)
        ) {

            if (input == null) {
                throw new Exception(
                        "Unable to open selected file."
                );
            }

            // Header
            output.write(authenticatedHeader);

            // Initialization vector
            output.write(iv);

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {

                byte[] encryptedChunk =
                        cipher.update(buffer, 0, bytesRead);

                if (encryptedChunk != null
                        && encryptedChunk.length > 0) {

                    output.write(encryptedChunk);
                }
            }

            // Writes final encrypted data and GCM authentication tag.
            byte[] finalEncryptedData = cipher.doFinal();

            if (finalEncryptedData.length > 0) {
                output.write(finalEncryptedData);
            }

            output.flush();

            success = true;

            return encryptedFile;

        } finally {

            if (!success && encryptedFile.exists()) {
                encryptedFile.delete();
            }
        }
    }

    public static String getOriginalFileName(
            File encryptedFile
    ) {

        if (encryptedFile == null
                || !encryptedFile.exists()) {

            return "Unknown File";
        }

        try (
                FileInputStream input =
                        new FileInputStream(encryptedFile)
        ) {

            byte[] magic = new byte[MAGIC.length];

            if (input.read(magic) != MAGIC.length) {
                return "Unknown File";
            }

            for (int i = 0; i < MAGIC.length; i++) {

                if (magic[i] != MAGIC[i]) {
                    return "Unknown File";
                }
            }

            byte[] lengthBytes = new byte[4];

            if (input.read(lengthBytes) != 4) {
                return "Unknown File";
            }

            int nameLength = ByteBuffer
                    .wrap(lengthBytes)
                    .getInt();

            if (nameLength <= 0
                    || nameLength > MAX_FILENAME_LENGTH) {

                return "Unknown File";
            }

            byte[] nameBytes = new byte[nameLength];

            int totalRead = 0;

            while (totalRead < nameLength) {

                int count = input.read(
                        nameBytes,
                        totalRead,
                        nameLength - totalRead
                );

                if (count == -1) {
                    return "Unknown File";
                }

                totalRead += count;
            }

            String name = new String(
                    nameBytes,
                    StandardCharsets.UTF_8
            );

            return sanitizeFileName(name);

        } catch (Exception e) {

            return "Unknown File";
        }
    }

    public static void decrypt(
            File encryptedFile,
            File destinationFile
    ) throws Exception {

        if (encryptedFile == null
                || !encryptedFile.exists()) {

            throw new Exception(
                    "Encrypted file does not exist."
            );
        }

        if (destinationFile == null) {
            throw new IllegalArgumentException(
                    "Destination file is missing."
            );
        }

        boolean success = false;

        try (
                FileInputStream input =
                        new FileInputStream(encryptedFile);

                FileOutputStream output =
                        new FileOutputStream(destinationFile)
        ) {

            // Read and verify file signature.
            byte[] magic = new byte[MAGIC.length];

            if (input.read(magic) != MAGIC.length) {
                throw new Exception(
                        "Invalid Falcon encrypted file."
                );
            }

            for (int i = 0; i < MAGIC.length; i++) {

                if (magic[i] != MAGIC[i]) {
                    throw new Exception(
                            "Unsupported Falcon file format."
                    );
                }
            }

            // Read filename length.
            byte[] lengthBytes = new byte[4];

            if (input.read(lengthBytes) != 4) {
                throw new Exception(
                        "Encrypted file header is incomplete."
                );
            }

            int nameLength = ByteBuffer
                    .wrap(lengthBytes)
                    .getInt();

            if (nameLength <= 0
                    || nameLength > MAX_FILENAME_LENGTH) {

                throw new Exception(
                        "Invalid filename metadata."
                );
            }

            // Read original filename metadata.
            byte[] nameBytes = new byte[nameLength];

            int totalRead = 0;

            while (totalRead < nameLength) {

                int count = input.read(
                        nameBytes,
                        totalRead,
                        nameLength - totalRead
                );

                if (count == -1) {
                    throw new Exception(
                            "Incomplete filename metadata."
                    );
                }

                totalRead += count;
            }

            // Reconstruct the exact authenticated header.
            byte[] authenticatedHeader =
                    new byte[
                            MAGIC.length
                                    + 4
                                    + nameBytes.length
                    ];

            System.arraycopy(
                    MAGIC,
                    0,
                    authenticatedHeader,
                    0,
                    MAGIC.length
            );

            System.arraycopy(
                    lengthBytes,
                    0,
                    authenticatedHeader,
                    MAGIC.length,
                    4
            );

            System.arraycopy(
                    nameBytes,
                    0,
                    authenticatedHeader,
                    MAGIC.length + 4,
                    nameBytes.length
            );

            // Read initialization vector.
            byte[] iv = new byte[IV_LENGTH];

            if (input.read(iv) != IV_LENGTH) {
                throw new Exception(
                        "Encryption IV is missing."
                );
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            // Verify filename metadata during GCM authentication.
            cipher.updateAAD(authenticatedHeader);

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {

                byte[] decryptedChunk =
                        cipher.update(buffer, 0, bytesRead);

                if (decryptedChunk != null
                        && decryptedChunk.length > 0) {

                    output.write(decryptedChunk);
                }
            }

            // Authentication tag is verified here.
            // Tampered or corrupted files will fail.
            byte[] finalDecryptedData = cipher.doFinal();

            if (finalDecryptedData.length > 0) {
                output.write(finalDecryptedData);
            }

            output.flush();

            success = true;

        } catch (Exception e) {

            if (e instanceof AEADBadTagException) {

                throw new SecurityException(
                        "Falcon security verification failed. "
                                + "File may have been modified.",
                        e
                );
            }

            throw e;

        } finally {

            if (!success && destinationFile.exists()) {
                destinationFile.delete();
            }
        }
    }
}