package io.github.xprss.quickjson.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class ExternalJson(
    val uri: Uri,
    val displayName: String,
    val content: String,
    val hash: String,
    val modifiedAt: Long?,
)

sealed interface SaveCheck {
    data object Unchanged : SaveCheck
    data class Conflict(val current: ExternalJson) : SaveCheck
    data class Unavailable(val reason: String) : SaveCheck
}

class FileGateway(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun read(uri: Uri): Result<ExternalJson> = runCatching {
        val metadata = metadata(uri)
        val declaredSize = metadata.second
        require(declaredSize == null || declaredSize <= MAX_BYTES) { "File exceeds 5 MiB" }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "File exceeds 5 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot open document")
        val content = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        ExternalJson(uri, metadata.first ?: "imported.json", content, sha256(bytes), lastModified(uri))
    }

    fun takeReadWritePermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun check(document: DocumentEntity): SaveCheck {
        val uri = document.sourceUri?.let(Uri::parse) ?: return SaveCheck.Unavailable("No linked document")
        val current = read(uri).getOrElse { return SaveCheck.Unavailable(it.message ?: "Cannot read linked document") }
        return if (externalChanged(document.externalHash, document.externalModifiedAt, current.hash, current.modifiedAt)) {
            SaveCheck.Conflict(current)
        } else SaveCheck.Unchanged
    }

    fun write(uri: Uri, content: String): Result<ExternalJson> = runCatching {
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
            ?: error("Cannot write document")
        read(uri).getOrElse {
            ExternalJson(uri, metadata(uri).first ?: "document.json", content, sha256(content.toByteArray()), lastModified(uri))
        }
    }

    fun createShareFile(title: String, content: String): Uri {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = DocumentRepository.ensureJsonExtension(title).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(directory, safeName).apply { writeText(content, Charsets.UTF_8) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun metadata(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                size = cursor.longOrNull(OpenableColumns.SIZE)
            }
        }
        return name to size
    }

    private fun lastModified(uri: Uri): Long? {
        resolver.query(uri, arrayOf("last_modified"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.longOrNull("last_modified")
        }
        return null
    }

    private fun Cursor.stringOrNull(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)
    private fun Cursor.longOrNull(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024L
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        fun externalChanged(savedHash: String?, savedModified: Long?, currentHash: String, currentModified: Long?): Boolean =
            (savedHash != null && savedHash != currentHash) ||
                (savedModified != null && currentModified != null && savedModified != currentModified)
    }
}
