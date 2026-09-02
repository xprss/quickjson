package io.github.xprss.quickjson.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class RootType { OBJECT, ARRAY }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val indent: Int = 2,
    val rootType: RootType = RootType.OBJECT,
    val lastDocumentId: String? = null,
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val indent = intPreferencesKey("indent")
        val root = stringPreferencesKey("root_type")
        val lastDocument = stringPreferencesKey("last_document")
    }

    val settings: Flow<Settings> = context.preferencesDataStore.data.map { values ->
        Settings(
            theme = values[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            indent = values[Keys.indent]?.takeIf { it == 2 || it == 4 } ?: 2,
            rootType = values[Keys.root]?.let { runCatching { RootType.valueOf(it) }.getOrNull() } ?: RootType.OBJECT,
            lastDocumentId = values[Keys.lastDocument],
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.preferencesDataStore.edit { it[Keys.theme] = value.name }
    suspend fun setIndent(value: Int) = context.preferencesDataStore.edit { it[Keys.indent] = if (value == 4) 4 else 2 }
    suspend fun setRootType(value: RootType) = context.preferencesDataStore.edit { it[Keys.root] = value.name }
    suspend fun setLastDocument(id: String?) = context.preferencesDataStore.edit {
        if (id == null) it.remove(Keys.lastDocument) else it[Keys.lastDocument] = id
    }
}
