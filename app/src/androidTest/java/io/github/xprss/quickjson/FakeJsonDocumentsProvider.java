package io.github.xprss.quickjson;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.FileNotFoundException;
import java.io.IOException;

/** A provider without Kotlin runtime dependencies, available before test setup runs. */
public final class FakeJsonDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "io.github.xprss.quickjson.test.documents";
    public static volatile byte[] content = new byte[] {0x7B, 0x7D};
    public static volatile long modifiedAt = 1;
    public static volatile boolean allowAccess = true;

    private static final String[] ROOT_COLUMNS = {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
    };
    private static final String[] DOCUMENT_COLUMNS = {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        cursor.newRow()
                .add(Root.COLUMN_ROOT_ID, "root")
                .add(Root.COLUMN_DOCUMENT_ID, "root")
                .add(Root.COLUMN_TITLE, "QuickJSON test provider")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) {
        return documentCursor(documentId, projection);
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        return documentCursor("test", projection);
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId,
            String mode,
            CancellationSignal signal
    ) throws FileNotFoundException {
        if (!allowAccess) {
            throw new FileNotFoundException("Permission revoked");
        }

        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            if (mode.contains("w")) {
                Thread writer = new Thread(() -> {
                    try (ParcelFileDescriptor.AutoCloseInputStream input =
                                 new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                        content = input.readBytes();
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

    private static Cursor documentCursor(String documentId, String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        cursor.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, documentId)
                .add(Document.COLUMN_DISPLAY_NAME, "test.json")
                .add(Document.COLUMN_MIME_TYPE, "application/json")
                .add(Document.COLUMN_SIZE, content.length)
                .add(Document.COLUMN_LAST_MODIFIED, modifiedAt)
                .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE);
        return cursor;
    }
}
