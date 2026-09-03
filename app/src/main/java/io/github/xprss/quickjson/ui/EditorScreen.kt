package io.github.xprss.quickjson.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xprss.quickjson.R
import io.github.xprss.quickjson.domain.JsonPath
import io.github.xprss.quickjson.domain.JsonTree
import io.github.xprss.quickjson.domain.JsonType
import io.github.xprss.quickjson.domain.JsonValidation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    editor: EditorState,
    indent: Int,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onTab: (EditorTab) -> Unit,
    onRaw: (String, Int, Int) -> Unit,
    onFormat: () -> Unit,
    onMinify: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onRemoveNode: (JsonPath) -> Unit,
    onDuplicateNode: (JsonPath) -> Unit,
    onMoveNode: (JsonPath, Int) -> Unit,
    onAddValue: (JsonPath, String, String, JsonType) -> Unit,
    onRenameKey: (JsonPath, String) -> Unit,
    onUpdatePrimitive: (JsonPath, String, JsonType) -> Unit,
    onChangeType: (JsonPath, JsonType) -> Unit,
    onSaveTemplate: (String) -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    var templateDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val backDescription = stringResource(R.string.back)
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.width(44.dp).heightIn(min = 48.dp).semantics { contentDescription = backDescription },
                    ) { Text("‹", fontSize = 28.sp) }
                    OutlinedTextField(
                        value = editor.document.title,
                        onValueChange = onRename,
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).widthIn(min = 0.dp).heightIn(min = 52.dp, max = 52.dp),
                        textStyle = MaterialTheme.typography.titleSmall,
                    )
                    Box {
                        TextButton(onClick = { more = true }, modifier = Modifier.width(44.dp).heightIn(min = 48.dp)) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.save)) }, onClick = { more = false; onSave() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_as)) }, onClick = { more = false; onSaveAs() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = { more = false; onShare() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_template)) }, onClick = { more = false; templateDialog = true })
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = editor.tab == EditorTab.VISUAL,
                        onClick = { onTab(EditorTab.VISUAL) },
                        label = { Text(stringResource(R.string.visual)) },
                        enabled = editor.validation is JsonValidation.Valid,
                    )
                    FilterChip(
                        selected = editor.tab == EditorTab.CODE,
                        onClick = { onTab(EditorTab.CODE) },
                        label = { Text(stringResource(R.string.code)) },
                    )
                }
            }
        }

        when (editor.tab) {
            EditorTab.CODE -> CodeEditor(
                raw = editor.raw,
                selectionStart = editor.cursorStart,
                selectionEnd = editor.cursorEnd,
                validation = editor.validation,
                indent = indent,
                onRaw = onRaw,
                onFormat = onFormat,
                onMinify = onMinify,
                onUndo = onUndo,
                onRedo = onRedo,
                onCopy = onCopy,
            )
            EditorTab.VISUAL -> {
                val root = (editor.validation as? JsonValidation.Valid)?.element
                if (root == null) {
                    Text(stringResource(R.string.invalid_visual), modifier = Modifier.padding(24.dp))
                } else {
                    VisualEditor(
                        root = root,
                        onRemove = onRemoveNode,
                        onDuplicate = onDuplicateNode,
                        onMove = onMoveNode,
                        onAddValue = onAddValue,
                        onRenameKey = onRenameKey,
                        onUpdatePrimitive = onUpdatePrimitive,
                        onChangeType = onChangeType,
                    )
                }
            }
        }
    }

    if (templateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { templateDialog = false },
            title = { Text(stringResource(R.string.save_template)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.template_name)) }) },
            confirmButton = {
                TextButton(onClick = { onSaveTemplate(name); templateDialog = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { templateDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CodeEditor(
    raw: String,
    selectionStart: Int,
    selectionEnd: Int,
    validation: JsonValidation,
    indent: Int,
    onRaw: (String, Int, Int) -> Unit,
    onFormat: () -> Unit,
    onMinify: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
) {
    var value by remember {
        mutableStateOf(TextFieldValue(raw, androidx.compose.ui.text.TextRange(selectionStart, selectionEnd)))
    }
    LaunchedEffect(raw) {
        if (raw != value.text) value = TextFieldValue(raw, androidx.compose.ui.text.TextRange(selectionStart, selectionEnd))
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = onRedo) { Text(stringResource(R.string.redo)) }
            TextButton(onClick = onFormat) { Text("${stringResource(R.string.format)} ($indent)") }
            TextButton(onClick = onMinify) { Text(stringResource(R.string.minify)) }
            TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
        }
        HorizontalDivider()
        if (validation is JsonValidation.Invalid) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(validation.error.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        stringResource(R.string.line_column, validation.error.line, validation.error.column),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    validation.error.path?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Row(
            Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(vertical = 12.dp),
        ) {
            Text(
                text = (1..maxOf(1, value.text.count { it == '\n' } + 1)).joinToString("\n"),
                modifier = Modifier.padding(horizontal = 8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onRaw(it.text, it.selection.start, it.selection.end)
                },
                modifier = Modifier.fillMaxHeight().widthIn(min = 600.dp).padding(end = 16.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                visualTransformation = JsonSyntaxTransformation(
                    stringColor = MaterialTheme.colorScheme.tertiary,
                    numberColor = MaterialTheme.colorScheme.primary,
                    keywordColor = MaterialTheme.colorScheme.secondary,
                    nullColor = MaterialTheme.colorScheme.error,
                ),
            )
        }
    }
}

private class JsonSyntaxTransformation(
    private val stringColor: Color,
    private val numberColor: Color,
    private val keywordColor: Color,
    private val nullColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            append(text.text)
            TOKEN.findAll(text.text).forEach { match ->
                val color = when {
                    match.value.startsWith('"') -> stringColor
                    match.value == "true" || match.value == "false" -> keywordColor
                    match.value == "null" -> nullColor
                    else -> numberColor
                }
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    companion object {
        private val TOKEN = Regex("\"(?:\\\\.|[^\"\\\\])*\"|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null")
    }
}

@Composable
private fun VisualEditor(
    root: JsonElement,
    onRemove: (JsonPath) -> Unit,
    onDuplicate: (JsonPath) -> Unit,
    onMove: (JsonPath, Int) -> Unit,
    onAddValue: (JsonPath, String, String, JsonType) -> Unit,
    onRenameKey: (JsonPath, String) -> Unit,
    onUpdatePrimitive: (JsonPath, String, JsonType) -> Unit,
    onChangeType: (JsonPath, JsonType) -> Unit,
) {
    val expanded = remember { mutableStateMapOf("$" to true) }
    val adding = remember { mutableStateMapOf<String, Boolean>() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally)) {
                JsonNode(
                    element = root,
                    path = JsonPath(),
                    label = "$",
                    depth = 0,
                    expanded = expanded,
                    adding = adding,
                    onRemove = onRemove,
                    onDuplicate = onDuplicate,
                    onMove = onMove,
                    onAddValue = onAddValue,
                    onRenameKey = onRenameKey,
                    onUpdatePrimitive = onUpdatePrimitive,
                    onChangeType = onChangeType,
                )
            }
        }
    }
}

@Composable
private fun JsonNode(
    element: JsonElement,
    path: JsonPath,
    label: String,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    adding: MutableMap<String, Boolean>,
    onRemove: (JsonPath) -> Unit,
    onDuplicate: (JsonPath) -> Unit,
    onMove: (JsonPath, Int) -> Unit,
    onAddValue: (JsonPath, String, String, JsonType) -> Unit,
    onRenameKey: (JsonPath, String) -> Unit,
    onUpdatePrimitive: (JsonPath, String, JsonType) -> Unit,
    onChangeType: (JsonPath, JsonType) -> Unit,
) {
    val isContainer = element is JsonObject || element is JsonArray
    val isEmptyContainer = (element as? JsonObject)?.isEmpty() == true || (element as? JsonArray)?.isEmpty() == true
    val pathText = path.toString()
    val isExpanded = expanded[pathText] ?: (depth < 2)
    val expansionDescription = stringResource(if (isExpanded) R.string.collapse else R.string.expand)
    val addDescription = stringResource(R.string.add_value)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 10).dp, bottom = 4.dp)
            .semantics { contentDescription = pathText },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isContainer) {
                    TextButton(
                        onClick = { expanded[pathText] = !isExpanded },
                        modifier = Modifier.semantics {
                            contentDescription = expansionDescription
                        },
                    ) {
                        Text(if (isExpanded) "▾" else "▸")
                    }
                } else Spacer(Modifier.width(8.dp))
                Text(
                    if (isContainer) {
                        val summary = if (element is JsonObject) "object · ${element.size}" else "array · ${(element as JsonArray).size}"
                        if (depth == 0) summary else "$label · $summary"
                    } else label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TypeMenu(JsonTree.typeOf(element)) { onChangeType(path, it) }
                if (isContainer) {
                    TextButton(
                        onClick = { adding[pathText] = !(adding[pathText] ?: false) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = addDescription },
                    ) { Text("＋") }
                }
            }
            if (path.parts.lastOrNull() is io.github.xprss.quickjson.domain.PathPart.Key) {
                var key by remember(label) { mutableStateOf(label) }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.key)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRenameKey(path, key) }),
                    trailingIcon = { TextButton(onClick = { onRenameKey(path, key) }) { Text("✓") } },
                )
            }
            if (!isContainer) {
                PrimitiveEditor(element, path, onUpdatePrimitive, Modifier.fillMaxWidth())
            }
            if (depth > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    NodeAction("↑", stringResource(R.string.move_up)) { onMove(path, -1) }
                    NodeAction("↓", stringResource(R.string.move_down)) { onMove(path, 1) }
                    NodeAction("⧉", stringResource(R.string.duplicate)) { onDuplicate(path) }
                    NodeAction("×", stringResource(R.string.delete)) { onRemove(path) }
                }
            }
            if (isContainer && ((adding[pathText] ?: false) || (depth == 0 && isEmptyContainer))) {
                InlineAddRow(
                    parent = element,
                    path = path,
                    onAdd = { key, value, type ->
                        onAddValue(path, key, value, type)
                        adding[pathText] = true
                    },
                    showCancel = adding[pathText] ?: false,
                    onDismiss = { adding[pathText] = false },
                )
            }
            if (isContainer && isExpanded) {
                when (element) {
                    is JsonObject -> element.entries.forEach { (key, value) ->
                        JsonNode(value, path.key(key), key, depth + 1, expanded, adding, onRemove, onDuplicate, onMove, onAddValue, onRenameKey, onUpdatePrimitive, onChangeType)
                    }
                    is JsonArray -> element.forEachIndexed { index, value ->
                        JsonNode(value, path.index(index), "[$index]", depth + 1, expanded, adding, onRemove, onDuplicate, onMove, onAddValue, onRenameKey, onUpdatePrimitive, onChangeType)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun InlineAddRow(
    parent: JsonElement,
    path: JsonPath,
    onAdd: (String, String, JsonType) -> Unit,
    showCancel: Boolean,
    onDismiss: () -> Unit,
) {
    val isObject = parent is JsonObject
    var key by remember(path) { mutableStateOf("") }
    var type by remember(path) { mutableStateOf(JsonType.STRING) }
    var value by remember(path, type) { mutableStateOf(defaultInput(type)) }
    val keyFocus = remember { FocusRequester() }
    val valueFocus = remember { FocusRequester() }
    val needsValue = type !in setOf(JsonType.OBJECT, JsonType.ARRAY, JsonType.NULL)
    LaunchedEffect(path) { if (isObject) keyFocus.requestFocus() }
    fun submit() {
        onAdd(key, value, type)
        key = ""
        value = defaultInput(type)
        if (isObject) keyFocus.requestFocus()
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (isObject) R.string.add_property else R.string.add_item),
                style = MaterialTheme.typography.labelLarge,
            )
            if (isObject) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.key)) },
                    placeholder = { Text("name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(keyFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (needsValue) valueFocus.requestFocus() }),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.type_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TypeMenu(type, compact = false) { type = it }
            }
            if (needsValue) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.value)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(valueFocus),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { submit() },
                    enabled = !isObject || key.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.add)) }
                if (showCancel) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                }
            }
    }
}

private fun defaultInput(type: JsonType) = when (type) {
    JsonType.NUMBER -> "0"
    JsonType.BOOLEAN -> "true"
    JsonType.NULL -> "null"
    else -> ""
}

@Composable
private fun PrimitiveEditor(
    element: JsonElement,
    path: JsonPath,
    onUpdate: (JsonPath, String, JsonType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = JsonTree.typeOf(element)
    val initial = when (element) {
        JsonNull -> "null"
        is JsonPrimitive -> element.content
        else -> element.toString()
    }
    var value by remember(element) { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = modifier,
        singleLine = true,
        enabled = type != JsonType.NULL,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onUpdate(path, value, type) }),
        trailingIcon = { TextButton(onClick = { onUpdate(path, value, type) }) { Text("✓") } },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun NodeAction(symbol: String, description: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)) {
        Text(symbol, modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun TypeMenu(current: JsonType, compact: Boolean = true, onType: (JsonType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val typeDescription = stringResource(R.string.type, current.name.lowercase())
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = typeDescription
            },
        ) {
            Text(if (compact) current.name.lowercase().take(3) else current.name.lowercase())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            JsonType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.name.lowercase()) }, onClick = { open = false; onType(type) })
            }
        }
    }
}
