package io.github.xprss.quickjson.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "raw_content") val rawContent: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
    @ColumnInfo(name = "source_name") val sourceName: String? = null,
    @ColumnInfo(name = "external_hash") val externalHash: String? = null,
    @ColumnInfo(name = "external_modified_at") val externalModifiedAt: Long? = null,
    @ColumnInfo(name = "editor_tab") val editorTab: String = "CODE",
    @ColumnInfo(name = "cursor_start") val cursorStart: Int = 0,
    @ColumnInfo(name = "cursor_end") val cursorEnd: Int = 0,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "json_content") val jsonContent: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
)
