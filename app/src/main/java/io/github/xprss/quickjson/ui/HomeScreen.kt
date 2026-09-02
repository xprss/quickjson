package io.github.xprss.quickjson.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xprss.quickjson.R
import io.github.xprss.quickjson.data.DocumentEntity
import io.github.xprss.quickjson.data.TemplateEntity
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCreateObject: () -> Unit,
    onCreateArray: () -> Unit,
    onCreateTemplate: (TemplateEntity) -> Unit,
    onClipboard: () -> Unit,
    onImport: () -> Unit,
    onRename: (DocumentEntity, String) -> Unit,
    onDuplicate: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
    onSettings: () -> Unit,
    onTemplates: () -> Unit,
) {
    var newMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var renameDocument by remember { mutableStateOf<DocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.import_file))
                    }
                    TextButton(onClick = onTemplates, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.templates))
                    }
                    TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(onClick = { newMenu = true }) {
                    Text("＋ ${stringResource(R.string.new_document)}")
                }
                DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_object)) },
                        onClick = { newMenu = false; onCreateObject() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_array)) },
                        onClick = { newMenu = false; onCreateArray() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.from_clipboard)) },
                        onClick = { newMenu = false; onClipboard() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.from_template)) },
                        onClick = { newMenu = false; templateMenu = true },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Text("⌕") },
                )
                Text(
                    stringResource(R.string.recent_documents),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                if (state.visibleDocuments.isEmpty()) {
                    EmptyHome(onCreateObject, onImport)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.visibleDocuments, key = { it.id }) { document ->
                            DocumentRow(
                                document = document,
                                onOpen = { onOpen(document.id) },
                                onRename = { renameDocument = document },
                                onDuplicate = { onDuplicate(document) },
                                onDelete = { onDelete(document) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (templateMenu) {
        TemplatePicker(
            templates = state.templates,
            onPick = { templateMenu = false; onCreateTemplate(it) },
            onDismiss = { templateMenu = false },
        )
    }
    renameDocument?.let { document ->
        RenameDialog(
            initial = document.title,
            onConfirm = { onRename(document, it); renameDocument = null },
            onDismiss = { renameDocument = null },
        )
    }
}

@Composable
private fun EmptyHome(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("{ }", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) { Text(stringResource(R.string.new_object)) }
            TextButton(onClick = onImport) { Text(stringResource(R.string.import_file)) }
        }
    }
}

@Composable
private fun DocumentRow(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.modifiedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TemplatePicker(templates: List<TemplateEntity>, onPick: (TemplateEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.from_template)) },
        text = {
            Column {
                TextButton(onClick = { onPick(TemplateEntity("builtin-object", "Object", "{}", 0, 0)) }) { Text(stringResource(R.string.new_object)) }
                TextButton(onClick = { onPick(TemplateEntity("builtin-array", "Array", "[]", 0, 0)) }) { Text(stringResource(R.string.new_array)) }
                templates.forEach { template -> TextButton(onClick = { onPick(template) }) { Text(template.name) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
