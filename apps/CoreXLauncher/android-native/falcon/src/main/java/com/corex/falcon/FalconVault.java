
package com.corex.falcon;

import android.content.Context;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

public final class FalconVault {

    private static final String VAULT_DIRECTORY =
            "falcon_vault";

    private static final String RECOVERY_DIRECTORY =
            "falcon_recovered";

    private static final String ENCRYPTED_EXTENSION =
            ".fxe";

    private FalconVault() {
    }

    // --------------------------------------------------
    // DIRECTORY MANAGEMENT
    // --------------------------------------------------

    private static void validateContext(Context context) {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context cannot be null."
            );
        }
    }

    private static File createPrivateDirectory(
            Context context,
            String directoryName
    ) throws Exception {

        validateContext(context);

        File directory = new File(
                context.getFilesDir(),
                directoryName
        );

        if (!directory.exists() && !directory.mkdirs()) {

            if (!directory.isDirectory()) {
                throw new Exception(
                        "Directory could not be created: "
                                + directoryName
                );
            }
        }

        if (!directory.isDirectory()) {
            throw new SecurityException(
                    "Invalid directory."
            );
        }

        return directory.getCanonicalFile();
    }

    public static File getVaultDirectory(
            Context context
    ) throws Exception {

        return createPrivateDirectory(
                context,
                VAULT_DIRECTORY
        );
    }

    public static File getRecoveryDirectory(
            Context context
    ) throws Exception {

        return createPrivateDirectory(
                context,
                RECOVERY_DIRECTORY
        );
    }

    // --------------------------------------------------
    // FILE VALIDATION
    // --------------------------------------------------

    private static File validateVaultFile(
            Context context,
            File encryptedFile
    ) throws Exception {

        if (encryptedFile == null
                || !encryptedFile.exists()
                || !encryptedFile.isFile()) {

            throw new IllegalArgumentException(
                    "Encrypted file does not exist."
            );
        }

        File vault = getVaultDirectory(context);

        File canonicalFile =
                encryptedFile.getCanonicalFile();

        File parent =
                canonicalFile.getParentFile();

        if (parent == null || !parent.equals(vault)) {

            throw new SecurityException(
                    "File is outside Falcon Vault."
            );
        }

        if (!canonicalFile.getName()
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(ENCRYPTED_EXTENSION)) {

            throw new SecurityException(
                    "Invalid encrypted file type."
            );
        }

        return canonicalFile;
    }

    // --------------------------------------------------
    // ENCRYPTED FILE LIST
    // --------------------------------------------------

    public static File[] getEncryptedFiles(
            Context context
    ) throws Exception {

        File vault = getVaultDirectory(context);

        File[] files = vault.listFiles(
                (directory, name) ->
                        name.toLowerCase(
                                java.util.Locale.ROOT
                        ).endsWith(ENCRYPTED_EXTENSION)
        );

        if (files == null || files.length == 0) {
            return new File[0];
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .filter(file -> {
                    try {
                        return file.getCanonicalFile()
                                .getParentFile()
                                .equals(vault);
                    } catch (Exception exception) {
                        return false;
                    }
                })
                .sorted(
                        Comparator.comparingLong(
                                File::lastModified
                        ).reversed()
                )
                .toArray(File[]::new);
    }

    // --------------------------------------------------
    // FILE RECOVERY
    // --------------------------------------------------

    public static File recover(
            Context context,
            File encryptedFile
    ) throws Exception {

        File source = validateVaultFile(
                context,
                encryptedFile
        );

        File recoveryDirectory =
                getRecoveryDirectory(context);

        String uniqueId = UUID.randomUUID().toString();

        File temporaryFile = new File(
                recoveryDirectory,
                ".recovery_" + uniqueId + ".tmp"
        );

        File recoveredFile = new File(
                recoveryDirectory,
                "recovered_" + uniqueId + ".bin"
        );

        boolean completed = false;

        try {

            // Decrypt into a temporary file first.
            FalconCrypto.decrypt(
                    source,
                    temporaryFile
            );

            if (!temporaryFile.exists()
                    || !temporaryFile.isFile()
                    || temporaryFile.length() == 0) {

                throw new Exception(
                        "Recovered file is invalid or empty."
                );
            }

            // Publish only after successful decryption.
            if (!temporaryFile.renameTo(recoveredFile)) {

                throw new Exception(
                        "Could not finalize recovered file."
                );
            }

            completed = true;

            return recoveredFile;

        } finally {

            if (!completed) {

                if (temporaryFile.exists()) {
                    temporaryFile.delete();
                }

                if (recoveredFile.exists()) {
                    recoveredFile.delete();
                }
            }
        }
    }

    // --------------------------------------------------
    // DELETE ENCRYPTED FILE
    // --------------------------------------------------

    public static boolean deleteEncryptedFile(
            Context context,
            File encryptedFile
    ) throws Exception {

        if (encryptedFile == null
                || !encryptedFile.exists()) {

            return false;
        }

        File validatedFile = validateVaultFile(
                context,
                encryptedFile
        );

        return validatedFile.delete();
    }
}
