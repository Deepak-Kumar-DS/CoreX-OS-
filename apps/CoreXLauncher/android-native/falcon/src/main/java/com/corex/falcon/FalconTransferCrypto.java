package com.corex.falcon;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.ByteBuffer;

import java.security.SecureRandom;

import java.util.Arrays;

public final class FalconTransferCrypto {

    private static final byte[] MAGIC = {
            'F', 'T', 'X', '1'
    };

    private static final int AES_KEY_SIZE = 256;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final int MAX_WRAPPED_KEY_LENGTH = 1024;

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private FalconTransferCrypto() {
    }

    private static SecretKey generateAESKey() throws Exception {

        KeyGenerator generator =
                KeyGenerator.getInstance("AES");

        generator.init(AES_KEY_SIZE);

        return generator.generateKey();
    }

    private static byte[] generateIV() {

        byte[] iv = new byte[IV_LENGTH];

        new SecureRandom().nextBytes(iv);

        return iv;
    }

    /**
     * Encrypts a file for a recipient.
     *
     * The recipient's RSA public key protects
     * the randomly generated AES key.
     */
    public static void encryptForRecipient(
            File sourceFile,
            File destinationFile,
            String recipientPublicKey
    ) throws Exception {

        if (sourceFile == null
                || !sourceFile.isFile()
                || !sourceFile.canRead()) {

            throw new Exception(
                    "Source file is unavailable."
            );
        }

        if (destinationFile == null) {
            throw new IllegalArgumentException(
                    "Destination file is missing."
            );
        }

        if (recipientPublicKey == null
                || recipientPublicKey.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Recipient public key is missing."
            );
        }

        SecretKey aesKey = generateAESKey();

        byte[] wrappedKey =
                FalconIdentity.encryptForRecipient(
                        aesKey.getEncoded(),
                        recipientPublicKey
                );

        if (wrappedKey.length == 0
                || wrappedKey.length > MAX_WRAPPED_KEY_LENGTH) {

            throw new Exception(
                    "Invalid encrypted AES key."
            );
        }

        byte[] iv = generateIV();

        ByteBuffer headerBuffer = ByteBuffer.allocate(
                MAGIC.length
                        + 4
                        + wrappedKey.length
                        + IV_LENGTH
        );

        headerBuffer.put(MAGIC);
        headerBuffer.putInt(wrappedKey.length);
        headerBuffer.put(wrappedKey);
        headerBuffer.put(iv);

        byte[] authenticatedHeader =
                headerBuffer.array();

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                aesKey,
                new GCMParameterSpec(
                        TAG_LENGTH_BITS,
                        iv
                )
        );

        cipher.updateAAD(authenticatedHeader);

        boolean success = false;

        try (
                InputStream input =
                        new FileInputStream(sourceFile);

                OutputStream output =
                        new FileOutputStream(destinationFile)
        ) {

            output.write(authenticatedHeader);

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {

                byte[] encrypted =
                        cipher.update(
                                buffer,
                                0,
                                bytesRead
                        );

                if (encrypted != null
                        && encrypted.length > 0) {

                    output.write(encrypted);
                }
            }

            byte[] finalData = cipher.doFinal();

            if (finalData.length > 0) {
                output.write(finalData);
            }

            output.flush();

            success = true;

        } finally {

            if (!success && destinationFile.exists()) {
                destinationFile.delete();
            }
        }
    }

    /**
     * Decrypts a file addressed to this device's
     * Falcon RSA identity.
     */
    public static void decryptReceivedFile(
            File encryptedFile,
            File destinationFile
    ) throws Exception {

        if (encryptedFile == null
                || !encryptedFile.isFile()
                || !encryptedFile.canRead()) {

            throw new Exception(
                    "Encrypted file is unavailable."
            );
        }

        if (destinationFile == null) {
            throw new IllegalArgumentException(
                    "Destination file is missing."
            );
        }

        boolean success = false;

        try (
                InputStream input =
                        new FileInputStream(encryptedFile);

                OutputStream output =
                        new FileOutputStream(destinationFile)
        ) {

            // Read file signature.
            byte[] magic = new byte[MAGIC.length];

            readFully(input, magic);

            if (!Arrays.equals(magic, MAGIC)) {

                throw new Exception(
                        "Invalid Falcon transfer file."
                );
            }

            // Read wrapped AES key length.
            byte[] lengthBytes = new byte[4];

            readFully(input, lengthBytes);

            int wrappedKeyLength =
                    ByteBuffer.wrap(lengthBytes).getInt();

            if (wrappedKeyLength <= 0
                    || wrappedKeyLength > MAX_WRAPPED_KEY_LENGTH) {

                throw new Exception(
                        "Invalid wrapped AES key."
                );
            }

            // Read RSA encrypted AES key.
            byte[] wrappedKey =
                    new byte[wrappedKeyLength];

            readFully(input, wrappedKey);

            // Read initialization vector.
            byte[] iv = new byte[IV_LENGTH];

            readFully(input, iv);

            // Reconstruct authenticated header.
            ByteBuffer headerBuffer = ByteBuffer.allocate(
                    MAGIC.length
                            + 4
                            + wrappedKey.length
                            + IV_LENGTH
            );

            headerBuffer.put(MAGIC);
            headerBuffer.putInt(wrappedKeyLength);
            headerBuffer.put(wrappedKey);
            headerBuffer.put(iv);

            byte[] authenticatedHeader =
                    headerBuffer.array();

            // Recover AES key using this device's RSA identity.
            byte[] rawAESKey =
                    FalconIdentity.decryptForSelf(wrappedKey);

            if (rawAESKey.length != 32) {

                throw new SecurityException(
                        "Invalid AES-256 key."
                );
            }

            SecretKey aesKey = new SecretKeySpec(
                    rawAESKey,
                    "AES"
            );

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            cipher.updateAAD(authenticatedHeader);

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {

                byte[] decrypted =
                        cipher.update(
                                buffer,
                                0,
                                bytesRead
                        );

                if (decrypted != null
                        && decrypted.length > 0) {

                    output.write(decrypted);
                }
            }

            // GCM authentication is verified here.
            byte[] finalData = cipher.doFinal();

            if (finalData.length > 0) {
                output.write(finalData);
            }

            output.flush();

            success = true;

        } finally {

            if (!success && destinationFile.exists()) {
                destinationFile.delete();
            }
        }
    }

    private static void readFully(
            InputStream input,
            byte[] data
    ) throws Exception {

        int offset = 0;

        while (offset < data.length) {

            int count = input.read(
                    data,
                    offset,
                    data.length - offset
            );

            if (count == -1) {

                throw new Exception(
                        "Incomplete Falcon transfer file."
                );
            }

            offset += count;
        }
    }
}