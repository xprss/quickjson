package io.github.xprss.quickjson

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xprss.quickjson.data.QuickJsonDatabase
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        QuickJsonDatabase::class.java,
    )

    @Test
    fun migratesDraftAndAddsEditorState() {
        helper.createDatabase(NAME, 1).apply {
            execSQL(
                "INSERT INTO documents (id,title,raw_content,created_at,modified_at,opened_at) VALUES ('d','draft.json','{ invalid',1,2,3)",
            )
            close()
        }
        helper.runMigrationsAndValidate(NAME, 2, true, QuickJsonDatabase.MIGRATION_1_2).use { database ->
            database.query("SELECT raw_content, editor_tab, cursor_start FROM documents WHERE id='d'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("{ invalid", cursor.getString(0))
                assertEquals("CODE", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    companion object { private const val NAME = "migration-test" }
}
