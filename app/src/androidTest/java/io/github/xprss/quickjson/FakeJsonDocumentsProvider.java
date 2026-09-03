package io.github.xprss.quickjson;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** A provider without Kotlin runtime dependencies, available before test setup runs. */
public final class FakeJsonDocumentsProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.xprss.quickjson.test.documents";
    public static volatile byte[] content = new byte[] {0x7B, 0x7D};
    public static volatile long modifiedAt = 1;
    public static volatile boolean allowAccess = true;

    private static final String[] DOCUMENT_COLUMNS = {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            "last_modified",
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        cursor.newRow()
                .add(OpenableColumns.DISPLAY_NAME, "test.json")
                .add(OpenableColumns.SIZE, content.length)
                .add("last_modified", modifiedAt);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!allowAccess) {
            throw new FileNotFoundException("Permission revoked");
        }

        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            if (mode.contains("w")) {
                Thread writer = new Thread(() -> {
                    try (ParcelFileDescriptor.AutoCloseInputStream input =
                                 new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                        content = readAllBytes(input);
                        modifiedAt++;
                    } catch (IOException ignored) {
                        // The client controls the pipe lifetime in this test provider.
                    }
                }, "fake-json-writer");
                writer.start();
                return pipe[1];
            }

            Thread reader = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(content);
                } catch (IOException ignored) {
                    // The client controls the pipe lifetime in this test provider.
                }
            }, "fake-json-reader");
            reader.start();
            return pipe[0];
        } catch (IOException exception) {
            throw new FileNotFoundException(exception.getMessage());
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("set-state".equals(method) && extras != null) {
            byte[] updatedContent = extras.getByteArray("content");
            if (updatedContent != null) {
                content = updatedContent;
            }
            if (extras.containsKey("modifiedAt")) {
                modifiedAt = extras.getLong("modifiedAt");
            }
            if (extras.containsKey("allowAccess")) {
                allowAccess = extras.getBoolean("allowAccess");
            }
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
