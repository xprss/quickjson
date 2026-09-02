package io.github.xprss.quickjson.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DocumentEntity::class, TemplateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class QuickJsonDatabase : RoomDatabase() {
    abstract fun dao(): QuickJsonDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN external_modified_at INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN editor_tab TEXT NOT NULL DEFAULT 'CODE'")
                db.execSQL("ALTER TABLE documents ADD COLUMN cursor_start INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN cursor_end INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
