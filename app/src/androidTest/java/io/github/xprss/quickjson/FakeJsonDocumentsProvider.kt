package io.github.xprss.quickjson

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.FileNotFoundException
import kotlin.concurrent.thread

class FakeJsonDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: ROOT_COLUMNS,
    ).apply {
        newRow().add(Root.COLUMN_ROOT_ID, "root")
            .add(Root.COLUMN_DOCUMENT_ID, "root")
            .add(Root.COLUMN_TITLE, "QuickJSON test provider")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documentCursor(documentId, projection)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        documentCursor("test", projection)

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (!allowAccess) throw FileNotFoundException("Permission revoked")
        return if (mode.contains('w')) {
            val pipe = ParcelFileDescriptor.createPipe()
            thread(name = "fake-json-writer") {
                ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { content = it.readBytes() }
                modifiedAt++
            }
            pipe[1]
        } else {
            val pipe = ParcelFileDescriptor.createPipe()
            thread(name = "fake-json-reader") {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(content) }
            }
            pipe[0]
        }
    }

    private fun documentCursor(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: DOCUMENT_COLUMNS,
    ).apply {
        newRow().add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, "test.json")
            .add(Document.COLUMN_MIME_TYPE, "application/json")
            .add(Document.COLUMN_SIZE, content.size)
            .add(Document.COLUMN_LAST_MODIFIED, modifiedAt)
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE)
    }

    companion object {
        const val AUTHORITY = "io.github.xprss.quickjson.test.documents"
        @Volatile var content: ByteArray = "{}".encodeToByteArray()
        @Volatile var modifiedAt: Long = 1
        @Volatile var allowAccess: Boolean = true

        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )
    }
}
