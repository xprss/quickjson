package io.github.xprss.quickjson.ui

import android.net.Uri
import java.nio.charset.CharacterCodingException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.xprss.quickjson.AppContainer
import io.github.xprss.quickjson.R
import io.github.xprss.quickjson.data.DocumentEntity
import io.github.xprss.quickjson.data.ExternalJson
import io.github.xprss.quickjson.data.RootType
import io.github.xprss.quickjson.data.SaveCheck
import io.github.xprss.quickjson.data.Settings
import io.github.xprss.quickjson.data.TemplateEntity
import io.github.xprss.quickjson.data.ThemeMode
import io.github.xprss.quickjson.domain.JsonEngine
import io.github.xprss.quickjson.domain.JsonPath
import io.github.xprss.quickjson.domain.JsonTree
import io.github.xprss.quickjson.domain.JsonType
import io.github.xprss.quickjson.domain.JsonValidation
import io.github.xprss.quickjson.domain.UndoHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

enum class EditorTab { VISUAL, CODE }

data class EditorState(
    val document: DocumentEntity,
    val raw: String,
    val validation: JsonValidation,
    val tab: EditorTab,
    val cursorStart: Int,
    val cursorEnd: Int,
)

data class MainUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val settings: Settings = Settings(),
    val query: String = "",
    val editor: EditorState? = null,
    val deletedDocument: DocumentEntity? = null,
    val conflict: ExternalJson? = null,
    val message: String? = null,
) {
    val visibleDocuments get() = documents.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true)
    }
}

sealed interface SaveRequest {
    data object Saved : SaveRequest
    data object ChooseDestination : SaveRequest
    data object Invalid : SaveRequest
    data object Conflict : SaveRequest
    data class Failed(val message: String) : SaveRequest
}

class MainViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val documents = container.documents.documents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val templates = container.documents.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val settings = container.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val query = MutableStateFlow("")
    private val editor = MutableStateFlow<EditorState?>(null)
    private val deleted = MutableStateFlow<DocumentEntity?>(null)
    private val conflict = MutableStateFlow<ExternalJson?>(null)
    private val message = MutableStateFlow<String?>(null)
    private var history: UndoHistory<String>? = null
    private var autosaveJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        documents, templates, settings, query, editor, deleted, conflict, message,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MainUiState(
            documents = values[0] as List<DocumentEntity>,
            templates = values[1] as List<TemplateEntity>,
            settings = values[2] as Settings,
            query = values[3] as String,
            editor = values[4] as EditorState?,
            deletedDocument = values[5] as DocumentEntity?,
            conflict = values[6] as ExternalJson?,
            message = values[7] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        viewModelScope.launch {
            val restored = savedState.get<String>(CURRENT_DOCUMENT) ?: container.preferences.settings.first().lastDocumentId
            if (restored != null) open(restored)
        }
    }

    fun setQuery(value: String) { query.value = value }

    fun create(raw: String? = null) = viewModelScope.launch {
        val initial = raw ?: if (settings.value.rootType == RootType.ARRAY) "[]" else "{}"
        val document = container.documents.create(initial)
        open(document.id, preferredTab = if (JsonEngine.validate(initial) is JsonValidation.Valid) EditorTab.VISUAL else EditorTab.CODE)
    }

    fun createFromTemplate(template: TemplateEntity) = create(template.jsonContent)

    fun importUri(uri: Uri) = viewModelScope.launch {
        container.files.takeReadWritePermission(uri)
        container.files.read(uri).onSuccess { external ->
            val document = container.documents.create(
                raw = external.content,
                title = external.displayName,
                sourceUri = uri.toString(),
                sourceName = external.displayName,
                externalHash = external.hash,
                externalModifiedAt = external.modifiedAt,
            )
            open(document.id, preferredTab = if (JsonEngine.validate(external.content) is JsonValidation.Valid) EditorTab.VISUAL else EditorTab.CODE)
        }.onFailure { error ->
            showMessage(
                when {
                    error is SecurityException || error is java.io.FileNotFoundException -> container.text(R.string.permission_revoked)
                    error is CharacterCodingException -> container.text(R.string.invalid_utf8)
                    error.message?.contains("5 MiB") == true -> container.text(R.string.file_too_large)
                    else -> container.text(R.string.import_failed)
                },
            )
        }
    }

    fun importText(text: String) {
        if (text.isBlank()) showMessage(container.text(R.string.clipboard_empty)) else create(text)
    }

    fun open(id: String, preferredTab: EditorTab? = null) = viewModelScope.launch {
        flush()
        val document = container.documents.document(id) ?: return@launch
        val tab = preferredTab ?: runCatching { EditorTab.valueOf(document.editorTab) }.getOrDefault(EditorTab.CODE)
        val opened = document.copy(openedAt = System.currentTimeMillis(), editorTab = tab.name)
        editor.value = EditorState(
            opened, opened.rawContent, JsonEngine.validate(opened.rawContent), tab,
            opened.cursorStart.coerceIn(0, opened.rawContent.length),
            opened.cursorEnd.coerceIn(0, opened.rawContent.length),
        )
        history = UndoHistory(opened.rawContent)
        savedState[CURRENT_DOCUMENT] = id
        container.preferences.setLastDocument(id)
        container.documents.update(opened)
    }

    fun close() = viewModelScope.launch {
        flush()
        editor.value = null
        history = null
        savedState[CURRENT_DOCUMENT] = null
        container.preferences.setLastDocument(null)
    }

    fun updateRaw(raw: String, cursorStart: Int = raw.length, cursorEnd: Int = cursorStart, record: Boolean = true) {
        val old = editor.value ?: return
        if (record) history?.push(raw)
        editor.value = old.copy(
            raw = raw,
            validation = JsonEngine.validate(raw),
            cursorStart = cursorStart.coerceIn(0, raw.length),
            cursorEnd = cursorEnd.coerceIn(0, raw.length),
        )
        scheduleAutosave()
    }

    fun selectTab(tab: EditorTab) {
        val old = editor.value ?: return
        if (tab == EditorTab.VISUAL && old.validation !is JsonValidation.Valid) {
            showMessage(container.text(R.string.invalid_visual))
            return
        }
        editor.value = old.copy(tab = tab)
        scheduleAutosave()
    }

    fun format() {
        val state = editor.value ?: return
        JsonEngine.format(state.raw, settings.value.indent)
            .onSuccess { updateRaw(it) }
            .onFailure { showMessage(it.message ?: "Cannot format invalid JSON") }
    }

    fun minify() {
        val state = editor.value ?: return
        JsonEngine.minify(state.raw)
            .onSuccess { updateRaw(it) }
            .onFailure { showMessage(it.message ?: "Cannot minify invalid JSON") }
    }

    fun undo() { history?.takeIf { it.canUndo }?.undo()?.let { updateRaw(it, record = false) } }
    fun redo() { history?.takeIf { it.canRedo }?.redo()?.let { updateRaw(it, record = false) } }

    fun updateTree(transform: (JsonElement) -> JsonElement) {
        val state = editor.value ?: return
        val valid = state.validation as? JsonValidation.Valid ?: return
        val changed = transform(valid.element)
        updateRaw(JsonEngine.format(changed.toString(), settings.value.indent).getOrDefault(changed.toString()))
    }

    fun replaceNode(path: JsonPath, value: JsonElement) = updateTree { JsonTree.replace(it, path, value) }
    fun removeNode(path: JsonPath) = updateTree { JsonTree.remove(it, path) }
    fun duplicateNode(path: JsonPath) = updateTree { JsonTree.duplicate(it, path) }
    fun moveNode(path: JsonPath, delta: Int) = updateTree { JsonTree.move(it, path, delta) }

    fun addNode(path: JsonPath, key: String, value: String, type: JsonType) {
        val element = primitiveFor(value, type)
        if (element == null) {
            showMessage(container.text(R.string.invalid_value))
            return
        }
        updateTree { JsonTree.addChild(it, path, element, key) }
    }
    fun renameKey(path: JsonPath, value: String) = updateTree { root ->
        JsonTree.renameKey(root, path, value).getOrElse { showMessage(container.text(R.string.duplicate_key_error)); root }
    }

    fun updatePrimitive(path: JsonPath, value: String, type: JsonType) {
        val element = primitiveFor(value, type)
        if (element == null) showMessage(container.text(R.string.invalid_value)) else replaceNode(path, element)
    }

    private fun primitiveFor(value: String, type: JsonType): JsonElement? =
        when (type) {
            JsonType.STRING -> JsonPrimitive(value)
            JsonType.NUMBER -> value.toLongOrNull()?.let(::JsonPrimitive)
                ?: value.toDoubleOrNull()?.let(::JsonPrimitive)
            JsonType.BOOLEAN -> value.toBooleanStrictOrNull()?.let(::JsonPrimitive)
            JsonType.NULL -> kotlinx.serialization.json.JsonNull
            JsonType.OBJECT, JsonType.ARRAY -> JsonTree.default(type)
        }

    fun changeType(path: JsonPath, type: JsonType) = replaceNode(path, JsonTree.default(type))

    fun renameDocument(value: String) = viewModelScope.launch {
        val state = editor.value ?: return@launch
        val updated = state.document.copy(title = value, rawContent = state.raw, modifiedAt = System.currentTimeMillis())
        editor.value = state.copy(document = updated)
        container.documents.rename(updated, value)
    }

    fun renameFromHome(document: DocumentEntity, value: String) = viewModelScope.launch { container.documents.rename(document, value) }
    fun duplicate(document: DocumentEntity) = viewModelScope.launch { container.documents.duplicate(document) }
    fun delete(document: DocumentEntity) = viewModelScope.launch { container.documents.delete(document); deleted.value = document }
    fun undoDelete() = viewModelScope.launch { deleted.value?.let { container.documents.restore(it) }; deleted.value = null }
    fun clearDeletedNotice() { deleted.value = null }

    suspend fun requestSave(): SaveRequest {
        val state = editor.value ?: return SaveRequest.Failed(container.text(R.string.no_document))
        if (state.validation !is JsonValidation.Valid) return SaveRequest.Invalid
        flush()
        if (state.document.sourceUri == null) return SaveRequest.ChooseDestination
        return when (val check = container.files.check(state.document)) {
            SaveCheck.Unchanged -> writeLinked(state.document)
            is SaveCheck.Conflict -> { conflict.value = check.current; SaveRequest.Conflict }
            is SaveCheck.Unavailable -> SaveRequest.Failed(container.text(R.string.permission_revoked))
        }
    }

    suspend fun exportTo(uri: Uri): SaveRequest {
        val state = editor.value ?: return SaveRequest.Failed(container.text(R.string.no_document))
        if (state.validation !is JsonValidation.Valid) return SaveRequest.Invalid
        return container.files.write(uri, state.raw).fold(
            onSuccess = { external ->
                val updated = state.document.copy(
                    sourceUri = uri.toString(), sourceName = external.displayName,
                    externalHash = external.hash, externalModifiedAt = external.modifiedAt,
                )
                container.documents.update(updated)
                editor.value = state.copy(document = updated)
                SaveRequest.Saved
            },
            onFailure = { SaveRequest.Failed(container.text(R.string.export_failed)) },
        )
    }

    fun reloadConflict() = viewModelScope.launch {
        val state = editor.value ?: return@launch
        val external = conflict.value ?: return@launch
        val updated = state.document.copy(
            rawContent = external.content, externalHash = external.hash,
            externalModifiedAt = external.modifiedAt, modifiedAt = System.currentTimeMillis(),
        )
        container.documents.update(updated)
        history = UndoHistory(external.content)
        editor.value = state.copy(document = updated, raw = external.content, validation = JsonEngine.validate(external.content))
        conflict.value = null
    }

    suspend fun overwriteConflict(): SaveRequest {
        conflict.value = null
        return editor.value?.document?.let { writeLinked(it) } ?: SaveRequest.Failed(container.text(R.string.no_document))
    }

    fun dismissConflict() { conflict.value = null }

    fun shareUri(): Uri? {
        val state = editor.value ?: return null
        if (state.validation !is JsonValidation.Valid) { showMessage(container.text(R.string.fix_before_export)); return null }
        return runCatching { container.files.createShareFile(state.document.title, state.raw) }
            .onFailure { showMessage(container.text(R.string.share_failed)) }.getOrNull()
    }

    fun createTemplate(name: String) = viewModelScope.launch {
        val state = editor.value ?: return@launch
        if (state.validation !is JsonValidation.Valid) { showMessage(container.text(R.string.template_invalid)); return@launch }
        container.documents.createTemplate(name, state.raw)
    }
    fun renameTemplate(template: TemplateEntity, name: String) = viewModelScope.launch { container.documents.renameTemplate(template, name) }
    fun duplicateTemplate(template: TemplateEntity) = viewModelScope.launch { container.documents.duplicateTemplate(template) }
    fun deleteTemplate(template: TemplateEntity) = viewModelScope.launch { container.documents.deleteTemplate(template) }

    fun setTheme(value: ThemeMode) = viewModelScope.launch { container.preferences.setTheme(value) }
    fun setIndent(value: Int) = viewModelScope.launch { container.preferences.setIndent(value) }
    fun setRootType(value: RootType) = viewModelScope.launch { container.preferences.setRootType(value) }
    fun showMessage(value: String) { message.value = value }
    fun consumeMessage() { message.value = null }

    fun flush() {
        autosaveJob?.cancel()
        persistEditor()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch { delay(AUTOSAVE_DELAY); persistEditor() }
    }

    private fun persistEditor() {
        val state = editor.value ?: return
        viewModelScope.launch {
            val updated = state.document.copy(
                rawContent = state.raw,
                modifiedAt = System.currentTimeMillis(),
                editorTab = state.tab.name,
                cursorStart = state.cursorStart,
                cursorEnd = state.cursorEnd,
            )
            container.documents.update(updated)
            editor.value = editor.value?.takeIf { it.document.id == updated.id }?.copy(document = updated)
        }
    }

    private suspend fun writeLinked(document: DocumentEntity): SaveRequest {
        val state = editor.value ?: return SaveRequest.Failed(container.text(R.string.no_document))
        val uri = document.sourceUri?.let(Uri::parse) ?: return SaveRequest.ChooseDestination
        return container.files.write(uri, state.raw).fold(
            onSuccess = { external ->
                val updated = state.document.copy(externalHash = external.hash, externalModifiedAt = external.modifiedAt)
                container.documents.update(updated)
                editor.value = state.copy(document = updated)
                SaveRequest.Saved
            },
            onFailure = { SaveRequest.Failed(container.text(R.string.save_failed)) },
        )
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(container, extras.createSavedStateHandle()) as T
        }
    }

    companion object {
        private const val CURRENT_DOCUMENT = "current_document"
        private const val AUTOSAVE_DELAY = 500L
    }
}
