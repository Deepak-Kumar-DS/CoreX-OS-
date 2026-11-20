package com.corex.falcon;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FalconReceivedFileProvider extends ContentProvider {

    private File getReceivedFolder() throws IOException {

        Context context = getContext();

        if (context == null) {
            throw new IOException("Application context unavailable.");
        }

        File base = context.getExternalFilesDir(null);

        if (base == null) {
            base = context.getFilesDir();
        }

        File folder = new File(
                base,
                "Falcon Received"
        );

        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Received folder unavailable.");
        }

        return folder.getCanonicalFile();
    }

    private File resolveFile(Uri uri)
            throws IOException, FileNotFoundException {

        if (uri == null) {
            throw new FileNotFoundException("Invalid file URI.");
        }

        if (!"received".equals(uri.getPathSegments().get(0))
                || uri.getPathSegments().size() != 2) {

            throw new FileNotFoundException("Invalid file path.");
        }

        String name = uri.getPathSegments().get(1);

        if (name == null
                || name.isEmpty()
                || name.contains("/")
                || name.contains("\\")
                || name.equals(".")
                || name.equals("..")) {

            throw new FileNotFoundException("Invalid filename.");
        }

        File folder = getReceivedFolder();

        File file = new File(
                folder,
                name
        ).getCanonicalFile();

        if (!folder.equals(file.getParentFile())) {
            throw new FileNotFoundException("Access denied.");
        }

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("File not found.");
        }

        return file;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {

        try {

            File file = resolveFile(uri);

            String name = file.getName();

            int dot = name.lastIndexOf('.');

            if (dot < 0 || dot == name.length() - 1) {
                return "application/octet-stream";
            }

            String extension = name
                    .substring(dot + 1)
                    .toLowerCase();

            String type = MimeTypeMap
                    .getSingleton()
                    .getMimeTypeFromExtension(extension);

            if (type == null) {
                return "application/octet-stream";
            }

            return type;

        } catch (Exception e) {

            return "application/octet-stream";
        }
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {

        try {

            File file = resolveFile(uri);

            String[] columns = projection;

            if (columns == null) {

                columns = new String[]{
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE
                };
            }

            MatrixCursor cursor = new MatrixCursor(columns);

            Object[] values = new Object[columns.length];

            for (int i = 0; i < columns.length; i++) {

                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                    values[i] = file.getName();

                } else if (OpenableColumns.SIZE.equals(columns[i])) {
                    values[i] = file.length();

                } else {
                    values[i] = null;
                }
            }

            cursor.addRow(values);

            return cursor;

        } catch (Exception e) {

            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(
            Uri uri,
            String mode
    ) throws FileNotFoundException {

        if (!"r".equals(mode)) {
            throw new FileNotFoundException(
                    "Read-only access is permitted."
            );
        }

        try {

            File file = resolveFile(uri);

            return ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
            );

        } catch (IOException e) {

            throw new FileNotFoundException(
                    "Unable to open requested file."
            );
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException(
                "Insert not supported."
        );
    }

    @Override
    public int delete(
            Uri uri,
            String selection,
            String[] selectionArgs
    ) {
        throw new UnsupportedOperationException(
                "Delete not supported."
        );
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs
    ) {
        throw new UnsupportedOperationException(
                "Update not supported."
        );
    }
}