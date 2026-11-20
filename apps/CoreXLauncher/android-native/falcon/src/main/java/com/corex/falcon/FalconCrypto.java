package com.corex.falcon;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.security.KeyStore;
import java.security.SecureRandom;

import java.util.UUID;

public final class FalconCrypto {

    private static final String KEY_ALIAS =
            "CoreX_Falcon_AES_KEY";

    private static final String ANDROID_KEYSTORE =
            "AndroidKeyStore";

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final byte[] MAGIC = {
            'F', 'X', 'E', '1'
    };

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MAX_FILENAME_LENGTH = 4096;
    private static final int BUFFER_SIZE = 32768;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private FalconCrypto() {
    }

    // ==================================================
    // ANDROID KEYSTORE
    // ==================================================

    private static SecretKey getOrCreateKey() throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(ANDROID_KEYSTORE);

        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            KeyStore.Entry entry =
                    keyStore.getEntry(KEY_ALIAS, null);

            if (!(entry instanceof KeyStore.SecretKeyEntry)) {

                throw new SecurityException(
                        "Falcon encryption key is invalid."
                );
            }

            SecretKey key =
                    ((KeyStore.SecretKeyEntry) entry)
                            .getSecretKey();

            if (key == null
                    || !"AES".equalsIgnoreCase(key.getAlgorithm())) {

                throw new SecurityException(
                        "Falcon AES key is unavailable."
                );
            }

            return key;
        }

        KeyGenerator generator =
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
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build();

        // Correct KeyGenerator API method.
        generator.init(specification);

        return generator.generateKey();
    }

    // ==================================================
    // FILE NAME HANDLING
    // ==================================================

    private static String getDisplayName(
            Context context,
            Uri uri
    ) {

        String name = null;

        try (Cursor cursor =
                     context.getContentResolver().query(
                             uri,
                             null,
                             null,
                             null,
                             null
                     )) {

            if (cursor != null && cursor.moveToFirst()) {

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
        ).trim();

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

        byte[] nameBytes =
                originalName.getBytes(StandardCharsets.UTF_8);

        if (nameBytes.length <= 0
                || nameBytes.length > MAX_FILENAME_LENGTH) {

            throw new IllegalArgumentException(
                    "Invalid filename metadata."
            );
        }

        ByteBuffer buffer = ByteBuffer.allocate(
                MAGIC.length + 4 + nameBytes.length
        );

        buffer.put(MAGIC);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);

        return buffer.array();
    }

    // ==================================================
    // FILE UTILITIES
    // ==================================================

    private static void readFully(
            InputStream input,
            byte[] buffer
    ) throws Exception {

        int offset = 0;

        while (offset < buffer.length) {

            int count = input.read(
                    buffer,
                    offset,
                    buffer.length - offset
            );

            if (count == -1) {
                throw new SecurityException(
                        "Encrypted file is incomplete."
                );
            }

            if (count == 0) {
                continue;
            }

            offset += count;
        }
    }

    private static File createTemporaryFile(
            File destination
    ) throws Exception {

        File absoluteDestination =
                destination.getAbsoluteFile();

        File parent =
                absoluteDestination.getParentFile();

        if (parent == null) {
            throw new Exception(
                    "Invalid destination directory."
            );
        }

        if (!parent.exists() && !parent.mkdirs()) {

            if (!parent.isDirectory()) {
                throw new Exception(
                        "Cannot create destination directory."
                );
            }
        }

        File canonicalParent =
                parent.getCanonicalFile();

        File canonicalDestination =
                absoluteDestination.getCanonicalFile();

        if (!canonicalDestination.getParentFile()
                .equals(canonicalParent)) {

            throw new SecurityException(
                    "Invalid destination path."
            );
        }

        return File.createTempFile(
                ".falcon_",
                ".tmp",
                canonicalParent
        );
    }

    private static void publishFile(
            File temporaryFile,
            File destinationFile
    ) throws Exception {

        if (destinationFile.exists()) {

            throw new Exception(
                    "Destination file already exists."
            );
        }

        if (!temporaryFile.renameTo(destinationFile)) {

            throw new Exception(
                    "Could not finalize Falcon file."
            );
        }
    }

    // ==================================================
    // ENCRYPT FILE
    // ==================================================

    public static File encrypt(
            Context context,
            Uri sourceUri
    ) throws Exception {

        if (context == null || sourceUri == null) {

            throw new IllegalArgumentException(
                    "Source file is missing."
            );
        }

        String originalName =
                getDisplayName(context, sourceUri);

        byte[] authenticatedHeader =
                createAuthenticatedHeader(originalName);

        byte[] iv = new byte[IV_LENGTH];

        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher =
                Cipher.getInstance(TRANSFORMATION);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(
                        TAG_LENGTH_BITS,
                        iv
                )
        );

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

        File temporaryFile =
                createTemporaryFile(encryptedFile);

        boolean completed = false;

        try {

            InputStream source =
                    context.getContentResolver()
                            .openInputStream(sourceUri);

            if (source == null) {
                throw new Exception(
                        "Unable to open selected file."
                );
            }

            try (InputStream input = source;
                 OutputStream output =
                         new FileOutputStream(temporaryFile)) {

                // Authenticated header.
                output.write(authenticatedHeader);

                // Initialization vector.
                output.write(iv);

                byte[] buffer =
                        new byte[BUFFER_SIZE];

                int count;

                while ((count = input.read(buffer)) != -1) {

                    if (count == 0) {
                        continue;
                    }

                    byte[] encrypted =
                            cipher.update(
                                    buffer,
                                    0,
                                    count
                            );

                    if (encrypted != null
                            && encrypted.length > 0) {

                        output.write(encrypted);
                    }
                }

                byte[] finalEncrypted =
                        cipher.doFinal();

                if (finalEncrypted.length > 0) {
                    output.write(finalEncrypted);
                }

                output.flush();
            }

            // Header + IV + GCM authentication tag.
            // This also permits an empty source file.
            if (temporaryFile.length() <
                    authenticatedHeader.length + IV_LENGTH + 16) {

                throw new Exception(
                        "Encrypted file is incomplete."
                );
            }

            publishFile(
                    temporaryFile,
                    encryptedFile
            );

            completed = true;

            return encryptedFile;

        } finally {

            if (!completed && temporaryFile.exists()) {
                temporaryFile.delete();
            }
        }
    }

    // ==================================================
    // READ ORIGINAL FILE NAME
    // ==================================================

    public static String getOriginalFileName(
            File encryptedFile
    ) {

        if (encryptedFile == null
                || !encryptedFile.exists()
                || !encryptedFile.isFile()) {

            return "Unknown File";
        }

        try (InputStream input =
                     new FileInputStream(encryptedFile)) {

            byte[] magic = new byte[MAGIC.length];

            readFully(input, magic);

            for (int i = 0; i < MAGIC.length; i++) {

                if (magic[i] != MAGIC[i]) {
                    return "Unknown File";
                }
            }

            byte[] lengthBytes = new byte[4];

            readFully(input, lengthBytes);

            int nameLength =
                    ByteBuffer.wrap(lengthBytes).getInt();

            if (nameLength <= 0
                    || nameLength > MAX_FILENAME_LENGTH) {

                return "Unknown File";
            }

            byte[] nameBytes =
                    new byte[nameLength];

            readFully(input, nameBytes);

            String name = new String(
                    nameBytes,
                    StandardCharsets.UTF_8
            );

            return sanitizeFileName(name);

        } catch (Exception exception) {

            return "Unknown File";
        }
    }

    // ==================================================
    // DECRYPT FILE
    // ==================================================

    public static void decrypt(
            File encryptedFile,
            File destinationFile
    ) throws Exception {

        if (encryptedFile == null
                || !encryptedFile.exists()
                || !encryptedFile.isFile()) {

            throw new IllegalArgumentException(
                    "Encrypted file does not exist."
            );
        }

        if (destinationFile == null) {

            throw new IllegalArgumentException(
                    "Destination file is missing."
            );
        }

        File source =
                encryptedFile.getCanonicalFile();

        File destination =
                destinationFile.getAbsoluteFile();

        if (source.equals(destination.getCanonicalFile())) {

            throw new SecurityException(
                    "Source and destination cannot be the same."
            );
        }

        File temporaryFile =
                createTemporaryFile(destination);

        boolean completed = false;

        try {

            try (InputStream input =
                         new FileInputStream(source);

                 OutputStream output =
                         new FileOutputStream(temporaryFile)) {

                // Verify file signature.
                byte[] magic = new byte[MAGIC.length];

                readFully(input, magic);

                for (int i = 0; i < MAGIC.length; i++) {

                    if (magic[i] != MAGIC[i]) {

                        throw new SecurityException(
                                "Unsupported Falcon file format."
                        );
                    }
                }

                // Read filename length.
                byte[] lengthBytes = new byte[4];

                readFully(input, lengthBytes);

                int nameLength =
                        ByteBuffer.wrap(lengthBytes).getInt();

                if (nameLength <= 0
                        || nameLength > MAX_FILENAME_LENGTH) {

                    throw new SecurityException(
                            "Invalid filename metadata."
                    );
                }

                // Read original filename.
                byte[] nameBytes =
                        new byte[nameLength];

                readFully(input, nameBytes);

                // Reconstruct authenticated header.
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

                // Read IV.
                byte[] iv = new byte[IV_LENGTH];

                readFully(input, iv);

                Cipher cipher =
                        Cipher.getInstance(TRANSFORMATION);

                cipher.init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateKey(),
                        new GCMParameterSpec(
                                TAG_LENGTH_BITS,
                                iv
                        )
                );

                cipher.updateAAD(authenticatedHeader);

                byte[] buffer =
                        new byte[BUFFER_SIZE];

                int count;

                while ((count = input.read(buffer)) != -1) {

                    if (count == 0) {
                        continue;
                    }

                    byte[] decrypted =
                            cipher.update(
                                    buffer,
                                    0,
                                    count
                            );

                    if (decrypted != null
                            && decrypted.length > 0) {

                        output.write(decrypted);
                    }
                }

                // GCM authentication is verified here.
                byte[] finalDecrypted =
                        cipher.doFinal();

                if (finalDecrypted.length > 0) {
                    output.write(finalDecrypted);
                }

                output.flush();
            }

            // Only publish after successful GCM authentication.
            publishFile(
                    temporaryFile,
                    destination
            );

            completed = true;

        } catch (AEADBadTagException exception) {

            throw new SecurityException(
                    "Falcon security verification failed. "
                            + "File may have been modified or corrupted.",
                    exception
            );

        } finally {

            if (!completed && temporaryFile.exists()) {
                temporaryFile.delete();
            }
        }
    }
}