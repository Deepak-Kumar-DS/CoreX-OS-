package com.corex.pulse;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;

import com.corex.falcon.FalconIdentity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PulseMediaCrypto {

    private static final byte[] MAGIC = {
            'C', 'P', 'M', '1'
    };

    private static final int NONCE_SIZE = 12;
    private static final int AES_KEY_SIZE = 32;
    private static final int GCM_TAG_SIZE = 128;

    private static final int MAX_WRAPPED_KEY_SIZE = 1024;

    private PulseMediaCrypto() {
    }

    public static File encryptUri(
            ContentResolver resolver,
            Uri uri,
            String recipientPublicKey,
            File outputFile
    ) throws Exception {

        if (uri == null || outputFile == null) {
            throw new IllegalArgumentException(
                    "Media source or destination is missing."
            );
        }

        KeyGenerator generator =
                KeyGenerator.getInstance("AES");

        generator.init(AES_KEY_SIZE * 8);

        SecretKey aesKey = generator.generateKey();

        byte[] nonce = new byte[NONCE_SIZE];

        new SecureRandom().nextBytes(nonce);

        byte[] wrappedKey =
                FalconIdentity.encryptForRecipient(
                        aesKey.getEncoded(),
                        recipientPublicKey
                );

        Cipher cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                aesKey,
                new GCMParameterSpec(GCM_TAG_SIZE, nonce)
        );

        try (
                InputStream input = new BufferedInputStream(
                        resolver.openInputStream(uri)
                );

                DataOutputStream header =
                        new DataOutputStream(
                                new BufferedOutputStream(
                                        new FileOutputStream(outputFile)
                                )
                        )
        ) {

            if (input == null) {
                throw new IllegalStateException(
                        "Unable to open selected media."
                );
            }

            header.write(MAGIC);
            header.writeByte(nonce.length);
            header.write(nonce);
            header.writeShort(wrappedKey.length);
            header.write(wrappedKey);
            header.flush();

            try (
                    CipherOutputStream encrypted =
                            new CipherOutputStream(
                                    header,
                                    cipher
                            )
            ) {
                byte[] buffer = new byte[64 * 1024];

                int count;

                while ((count = input.read(buffer)) != -1) {
                    encrypted.write(buffer, 0, count);
                }

                encrypted.flush();
            }
        }

        return outputFile;
    }

    public static File decryptFile(
            File encryptedFile,
            File outputFile
    ) throws Exception {

        if (encryptedFile == null || outputFile == null) {
            throw new IllegalArgumentException(
                    "Media file is missing."
            );
        }

        try (
                DataInputStream input =
                        new DataInputStream(
                                new BufferedInputStream(
                                        new FileInputStream(encryptedFile)
                                )
                        )
        ) {

            byte[] magic = new byte[4];
            input.readFully(magic);

            for (int i = 0; i < MAGIC.length; i++) {
                if (magic[i] != MAGIC[i]) {
                    throw new SecurityException(
                            "Invalid CoreX Pulse media format."
                    );
                }
            }

            int nonceLength = input.readUnsignedByte();

            if (nonceLength != NONCE_SIZE) {
                throw new SecurityException(
                        "Invalid encryption nonce."
                );
            }

            byte[] nonce = new byte[nonceLength];
            input.readFully(nonce);

            int wrappedLength = input.readUnsignedShort();

            if (wrappedLength < 1 ||
                    wrappedLength > MAX_WRAPPED_KEY_SIZE) {
                throw new SecurityException(
                        "Invalid encrypted media key."
                );
            }

            byte[] wrappedKey = new byte[wrappedLength];
            input.readFully(wrappedKey);

            byte[] rawKey =
                    FalconIdentity.decryptForSelf(wrappedKey);

            if (rawKey.length != AES_KEY_SIZE) {
                throw new SecurityException(
                        "Invalid AES media key."
                );
            }

            SecretKey aesKey = new SecretKeySpec(
                    rawKey,
                    "AES"
            );

            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding"
            );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(GCM_TAG_SIZE, nonce)
            );

            try (
                    CipherInputStream decrypted =
                            new CipherInputStream(input, cipher);

                    OutputStream output =
                            new BufferedOutputStream(
                                    new FileOutputStream(outputFile)
                            )
            ) {

                byte[] buffer = new byte[64 * 1024];

                int count;

                while ((count = decrypted.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }
        }

        return outputFile;
    }
}
