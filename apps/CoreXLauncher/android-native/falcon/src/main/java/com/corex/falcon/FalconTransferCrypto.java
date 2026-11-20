
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
    private static final int AES_KEY_LENGTH = 32;

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final int MAX_WRAPPED_KEY_LENGTH = 1024;
    private static final int BUFFER_SIZE = 32768;

    private static final String TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private FalconTransferCrypto() {
    }

    private static SecretKey generateAESKey()
            throws Exception {

        KeyGenerator generator =
                KeyGenerator.getInstance("AES");

        generator.init(AES_KEY_SIZE, RANDOM);

        return generator.generateKey();
    }

    private static byte[] generateIV() {

        byte[] iv = new byte[IV_LENGTH];

        RANDOM.nextBytes(iv);

        return iv;
    }

    private static void validateFiles(
            File source,
            File destination
    ) throws Exception {

        if (source == null ||
                !source.isFile() ||
                !source.canRead()) {

            throw new Exception(
                    "Source file is unavailable."
            );
        }

        if (destination == null) {
            throw new IllegalArgumentException(
                    "Destination file is missing."
            );
        }

        if (source.getCanonicalPath().equals(
                destination.getCanonicalPath())) {

            throw new IllegalArgumentException(
                    "Source and destination cannot be identical."
            );
        }

        File parent = destination.getAbsoluteFile()
                .getParentFile();

        if (parent == null) {
            throw new Exception(
                    "Invalid destination directory."
            );
        }

        if (!parent.exists() && !parent.mkdirs()) {
            throw new Exception(
                    "Cannot create destination directory."
            );
        }

        if (!parent.isDirectory()) {
            throw new Exception(
                    "Destination parent is not a directory."
            );
        }
    }

    private static byte[] createHeader(
            byte[] wrappedKey,
            byte[] iv
    ) {

        ByteBuffer buffer = ByteBuffer.allocate(
                MAGIC.length +
                4 +
                wrappedKey.length +
                IV_LENGTH
        );

        buffer.put(MAGIC);
        buffer.putInt(wrappedKey.length);
        buffer.put(wrappedKey);
        buffer.put(iv);

        return buffer.array();
    }

    /**
     * Encrypts a file using AES-256-GCM.
     * AES key is protected using recipient RSA public key.
     */
    public static void encryptForRecipient(
            File sourceFile,
            File destinationFile,
            String recipientPublicKey
    ) throws Exception {

        validateFiles(sourceFile, destinationFile);

        if (recipientPublicKey == null ||
                recipientPublicKey.trim().isEmpty()) {

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

        if (wrappedKey == null ||
                wrappedKey.length == 0 ||
                wrappedKey.length > MAX_WRAPPED_KEY_LENGTH) {

            throw new SecurityException(
                    "Invalid encrypted AES key."
            );
        }

        byte[] iv = generateIV();

        byte[] header = createHeader(
                wrappedKey,
                iv
        );

        Cipher cipher = Cipher.getInstance(
                TRANSFORMATION
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                aesKey,
                new GCMParameterSpec(
                        TAG_LENGTH_BITS,
                        iv
                )
        );

        cipher.updateAAD(header);

        File temporary = File.createTempFile(
                "falcon_encrypt_",
                ".tmp",
                destinationFile.getAbsoluteFile().getParentFile()
        );

        boolean success = false;

        try (
                InputStream input =
                        new FileInputStream(sourceFile);

                OutputStream output =
                        new FileOutputStream(temporary)
        ) {

            output.write(header);

            byte[] buffer = new byte[BUFFER_SIZE];

            int count;

            while ((count = input.read(buffer)) != -1) {

                byte[] encrypted = cipher.update(
                        buffer,
                        0,
                        count
                );

                if (encrypted != null &&
                        encrypted.length > 0) {

                    output.write(encrypted);
                }
            }

            byte[] finalData = cipher.doFinal();

            output.write(finalData);

            output.flush();

            success = true;

        } finally {

            if (!success) {
                temporary.delete();
            }
        }

        replaceDestination(
                temporary,
                destinationFile
        );
    }

    /**
     * Decrypts a file using this device's RSA private key.
     *
     * Plaintext is first written to a temporary file.
     * Destination is updated only after GCM authentication succeeds.
     */
    public static void decryptReceivedFile(
            File encryptedFile,
            File destinationFile
    ) throws Exception {

        validateFiles(
                encryptedFile,
                destinationFile
        );

        File temporary = File.createTempFile(
                "falcon_decrypt_",
                ".tmp",
                destinationFile.getAbsoluteFile().getParentFile()
        );

        boolean success = false;

        byte[] rawAESKey = null;

        try (
                InputStream input =
                        new FileInputStream(encryptedFile);

                OutputStream output =
                        new FileOutputStream(temporary)
        ) {

            // Read and validate signature.

            byte[] magic = new byte[MAGIC.length];

            readFully(input, magic);

            if (!Arrays.equals(magic, MAGIC)) {
                throw new SecurityException(
                        "Invalid Falcon transfer file."
                );
            }

            // Read wrapped AES key length.

            byte[] lengthBytes = new byte[4];

            readFully(input, lengthBytes);

            int wrappedKeyLength =
                    ByteBuffer.wrap(lengthBytes).getInt();

            if (wrappedKeyLength <= 0 ||
                    wrappedKeyLength > MAX_WRAPPED_KEY_LENGTH) {

                throw new SecurityException(
                        "Invalid wrapped AES key length."
                );
            }

            // Read wrapped AES key.

            byte[] wrappedKey =
                    new byte[wrappedKeyLength];

            readFully(input, wrappedKey);

            // Read initialization vector.

            byte[] iv = new byte[IV_LENGTH];

            readFully(input, iv);

            // Reconstruct authenticated header.

            byte[] header = createHeader(
                    wrappedKey,
                    iv
            );

            // Decrypt AES key using device identity.

            rawAESKey =
                    FalconIdentity.decryptForSelf(
                            wrappedKey
                    );

            if (rawAESKey == null ||
                    rawAESKey.length != AES_KEY_LENGTH) {

                throw new SecurityException(
                        "Invalid AES-256 key."
                );
            }

            SecretKey aesKey = new SecretKeySpec(
                    rawAESKey,
                    "AES"
            );

            Cipher cipher = Cipher.getInstance(
                    TRANSFORMATION
            );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            cipher.updateAAD(header);

            // Decrypt into temporary file.

            byte[] buffer = new byte[BUFFER_SIZE];

            int count;

            while ((count = input.read(buffer)) != -1) {

                byte[] decrypted = cipher.update(
                        buffer,
                        0,
                        count
                );

                if (decrypted != null &&
                        decrypted.length > 0) {

                    output.write(decrypted);
                }
            }

            // Authentication tag verification.

            byte[] finalData = cipher.doFinal();

            if (finalData.length > 0) {
                output.write(finalData);
            }

            output.flush();

            success = true;

        } finally {

            if (rawAESKey != null) {
                Arrays.fill(rawAESKey, (byte) 0);
            }

            if (!success) {
                temporary.delete();
            }
        }

        // Publish plaintext only after authentication succeeds.

        replaceDestination(
                temporary,
                destinationFile
        );
    }

    private static void replaceDestination(
            File temporary,
            File destination
    ) throws Exception {

        if (destination.exists() &&
                !destination.delete()) {

            temporary.delete();

            throw new Exception(
                    "Cannot replace destination file."
            );
        }

        if (!temporary.renameTo(destination)) {

            temporary.delete();

            throw new Exception(
                    "Cannot save completed transfer file."
            );
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
