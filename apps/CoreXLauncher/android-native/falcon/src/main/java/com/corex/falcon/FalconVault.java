package com.corex.falcon;

import android.content.Context;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class FalconVault {

    private FalconVault() {
    }

    public static File getVaultDirectory(Context context) throws Exception {

        File directory = new File(
                context.getFilesDir(),
                "falcon_vault"
        );

        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Falcon Vault could not be created.");
        }

        return directory.getCanonicalFile();
    }

    public static File getRecoveryDirectory(Context context) throws Exception {

        File directory = new File(
                context.getFilesDir(),
                "falcon_recovered"
        );

        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Recovery directory could not be created.");
        }

        return directory.getCanonicalFile();
    }

    public static File[] getEncryptedFiles(Context context) throws Exception {

        File[] files = getVaultDirectory(context).listFiles(
                (dir, name) -> name.endsWith(".fxe")
        );

        if (files == null) {
            return new File[0];
        }

        Arrays.sort(
                files,
                Comparator.comparingLong(File::lastModified).reversed()
        );

        return files;
    }

    public static File recover(
            Context context,
            File encryptedFile
    ) throws Exception {

        if (encryptedFile == null || !encryptedFile.exists()) {
            throw new Exception("Encrypted file does not exist.");
        }

        File vault = getVaultDirectory(context);

        if (!encryptedFile.getCanonicalFile().getParentFile()
                .equals(vault)) {
            throw new SecurityException("File is outside Falcon Vault.");
        }

        File recoveredFile = new File(
                getRecoveryDirectory(context),
                "recovered_" + System.currentTimeMillis() + ".bin"
        );

        try {

            FalconCrypto.decrypt(encryptedFile, recoveredFile);

            return recoveredFile;

        } catch (Exception e) {

            if (recoveredFile.exists()) {
                recoveredFile.delete();
            }

            throw e;
        }
    }

    public static boolean deleteEncryptedFile(
            Context context,
            File encryptedFile
    ) throws Exception {

        if (encryptedFile == null || !encryptedFile.exists()) {
            return false;
        }

        File vault = getVaultDirectory(context);

        if (!encryptedFile.getCanonicalFile().getParentFile()
                .equals(vault)) {
            throw new SecurityException("Invalid Falcon Vault file.");
        }

        if (!encryptedFile.getName().endsWith(".fxe")) {
            throw new SecurityException("Invalid encrypted file type.");
        }

        return encryptedFile.delete();
    }
}