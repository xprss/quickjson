package io.github.xprss.quickjson

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xprss.quickjson.data.DocumentEntity
import io.github.xprss.quickjson.data.QuickJsonDatabase
import io.github.xprss.quickjson.data.TemplateEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var database: QuickJsonDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QuickJsonDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun crudFlowAndInvalidDraftPersistence() = runBlocking {
        val document = DocumentEntity("d", "draft.json", "{ invalid", 1, 2, 3)
        database.dao().upsertDocument(document)
        assertEquals("{ invalid", database.dao().observeDocuments().first().single().rawContent)
        database.dao().upsertDocument(document.copy(rawContent = "{}", modifiedAt = 4))
        assertEquals("{}", database.dao().document("d")?.rawContent)
        database.dao().deleteDocument(document.copy(rawContent = "{}", modifiedAt = 4))
        assertNull(database.dao().document("d"))

        val template = TemplateEntity("t", "Object", "{}", 1, 1)
        database.dao().upsertTemplate(template)
        assertEquals(template, database.dao().observeTemplates().first().single())
        database.dao().deleteTemplate(template)
        assertEquals(emptyList(), database.dao().observeTemplates().first())
    }
}
