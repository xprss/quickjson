package io.github.xprss.quickjson.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val dao: QuickJsonDao) {
    val documents: Flow<List<DocumentEntity>> = dao.observeDocuments()
    val templates: Flow<List<TemplateEntity>> = dao.observeTemplates()

    fun observeDocument(id: String) = dao.observeDocument(id)
    suspend fun document(id: String) = dao.document(id)

    suspend fun create(
        raw: String,
        title: String = automaticName(),
        sourceUri: String? = null,
        sourceName: String? = null,
        externalHash: String? = null,
        externalModifiedAt: Long? = null,
    ): DocumentEntity {
        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            id = UUID.randomUUID().toString(), title = title, rawContent = raw,
            createdAt = now, modifiedAt = now, openedAt = now,
            sourceUri = sourceUri, sourceName = sourceName,
            externalHash = externalHash, externalModifiedAt = externalModifiedAt,
        )
        dao.upsertDocument(document)
        return document
    }

    suspend fun update(document: DocumentEntity) = dao.upsertDocument(document)

    suspend fun rename(document: DocumentEntity, title: String) {
        val clean = title.trim().ifEmpty { automaticName() }
        dao.upsertDocument(document.copy(title = clean, modifiedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(document: DocumentEntity): DocumentEntity {
        val base = document.title.removeSuffix(".json")
        return create(document.rawContent, "$base copy.json")
    }

    suspend fun delete(document: DocumentEntity) = dao.deleteDocument(document)
    suspend fun restore(document: DocumentEntity) = dao.upsertDocument(document)

    suspend fun createTemplate(name: String, json: String): TemplateEntity {
        val now = System.currentTimeMillis()
        val template = TemplateEntity(UUID.randomUUID().toString(), name.trim(), json, now, now)
        dao.upsertTemplate(template)
        return template
    }

    suspend fun renameTemplate(template: TemplateEntity, name: String) =
        dao.upsertTemplate(template.copy(name = name.trim(), modifiedAt = System.currentTimeMillis()))

    suspend fun duplicateTemplate(template: TemplateEntity) =
        createTemplate("${template.name} copy", template.jsonContent)

    suspend fun deleteTemplate(template: TemplateEntity) = dao.deleteTemplate(template)

    companion object {
        fun automaticName(now: Date = Date()): String =
            "untitled-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now)}.json"

        fun ensureJsonExtension(name: String) = if (name.endsWith(".json", true)) name else "$name.json"
    }
}
