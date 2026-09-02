package io.github.xprss.quickjson.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.xprss.quickjson.R
import io.github.xprss.quickjson.data.RootType
import io.github.xprss.quickjson.data.Settings
import io.github.xprss.quickjson.data.TemplateEntity
import io.github.xprss.quickjson.data.ThemeMode

@Composable
fun SettingsDialog(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onIndent: (Int) -> Unit,
    onRoot: (RootType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                        FilterChip(selected = settings.theme == mode, onClick = { onTheme(mode) }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.indentation), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 4).forEach { indent ->
                        FilterChip(selected = settings.indent == indent, onClick = { onIndent(indent) }, label = { Text(indent.toString()) })
                    }
                }
                Text(stringResource(R.string.root_type), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = settings.rootType == RootType.OBJECT, onClick = { onRoot(RootType.OBJECT) }, label = { Text(stringResource(R.string.object_type)) })
                    FilterChip(selected = settings.rootType == RootType.ARRAY, onClick = { onRoot(RootType.ARRAY) }, label = { Text(stringResource(R.string.array_type)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun TemplatesDialog(
    templates: List<TemplateEntity>,
    onCreate: (TemplateEntity) -> Unit,
    onRename: (TemplateEntity, String) -> Unit,
    onDuplicate: (TemplateEntity) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var rename by remember { mutableStateOf<TemplateEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BuiltInTemplate(stringResource(R.string.new_object), "{}", onCreate)
                BuiltInTemplate(stringResource(R.string.new_array), "[]", onCreate)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (templates.isEmpty()) Text(stringResource(R.string.no_templates))
                templates.forEach { template ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(template.name, style = MaterialTheme.typography.titleSmall)
                        Text(template.jsonContent.take(120), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Row {
                            TextButton(onClick = { onCreate(template); onDismiss() }) { Text(stringResource(R.string.new_document)) }
                            TextButton(onClick = { rename = template }) { Text(stringResource(R.string.rename)) }
                            TextButton(onClick = { onDuplicate(template) }) { Text(stringResource(R.string.duplicate)) }
                            TextButton(onClick = { onDelete(template) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    rename?.let { template ->
        var value by remember(template.id) { mutableStateOf(template.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value, { value = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(template, value); rename = null }, enabled = value.isNotBlank()) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BuiltInTemplate(name: String, json: String, onCreate: (TemplateEntity) -> Unit) {
    TextButton(
        onClick = { onCreate(TemplateEntity("built-in-$name", name, json, 0, 0)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(name) }
}
