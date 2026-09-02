package io.github.xprss.quickjson.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xprss.quickjson.R
import io.github.xprss.quickjson.ui.theme.QuickJsonTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickJsonApp(
    viewModel: MainViewModel,
    onImport: () -> Unit,
    onNewFromClipboard: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAs: (String) -> Unit,
    onShare: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.document_deleted)
    val undoLabel = stringResource(R.string.undo)
    val savedMessage = stringResource(R.string.saved)
    var showSettings by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }

    BackHandler(enabled = state.editor != null) { viewModel.close() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.deletedDocument) {
        if (state.deletedDocument != null) {
            val result = snackbar.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoDelete()
            else viewModel.clearDeletedNotice()
        }
    }

    QuickJsonTheme(state.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets.navigationBars,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val editor = state.editor
                if (editor == null) {
                    HomeScreen(
                        state = state,
                        onQuery = viewModel::setQuery,
                        onOpen = viewModel::open,
                        onCreateObject = { viewModel.create("{}") },
                        onCreateArray = { viewModel.create("[]") },
                        onCreateTemplate = viewModel::createFromTemplate,
                        onClipboard = onNewFromClipboard,
                        onImport = onImport,
                        onRename = viewModel::renameFromHome,
                        onDuplicate = viewModel::duplicate,
                        onDelete = viewModel::delete,
                        onSettings = { showSettings = true },
                        onTemplates = { showTemplates = true },
                    )
                } else {
                    EditorScreen(
                        editor = editor,
                        indent = state.settings.indent,
                        onBack = viewModel::close,
                        onRename = viewModel::renameDocument,
                        onTab = viewModel::selectTab,
                        onRaw = { raw, start, end -> viewModel.updateRaw(raw, start, end) },
                        onFormat = viewModel::format,
                        onMinify = viewModel::minify,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onCopy = { onCopy(editor.raw) },
                        onSave = onSave,
                        onSaveAs = { onSaveAs(editor.document.title) },
                        onShare = onShare,
                        onRemoveNode = viewModel::removeNode,
                        onDuplicateNode = viewModel::duplicateNode,
                        onMoveNode = viewModel::moveNode,
                        onAddNode = viewModel::addNode,
                        onRenameKey = viewModel::renameKey,
                        onUpdatePrimitive = viewModel::updatePrimitive,
                        onChangeType = viewModel::changeType,
                        onSaveTemplate = viewModel::createTemplate,
                    )
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                settings = state.settings,
                onTheme = viewModel::setTheme,
                onIndent = viewModel::setIndent,
                onRoot = viewModel::setRootType,
                onDismiss = { showSettings = false },
            )
        }
        if (showTemplates) {
            TemplatesDialog(
                templates = state.templates,
                onCreate = viewModel::createFromTemplate,
                onRename = viewModel::renameTemplate,
                onDuplicate = viewModel::duplicateTemplate,
                onDelete = viewModel::deleteTemplate,
                onDismiss = { showTemplates = false },
            )
        }
        if (state.conflict != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissConflict,
                title = { Text(stringResource(R.string.external_conflict_title)) },
                text = { Text(stringResource(R.string.external_conflict_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            when (val result = viewModel.overwriteConflict()) {
                                SaveRequest.Saved -> viewModel.showMessage(savedMessage)
                                is SaveRequest.Failed -> viewModel.showMessage(result.message)
                                else -> Unit
                            }
                        }
                    }) { Text(stringResource(R.string.overwrite)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::reloadConflict) { Text(stringResource(R.string.reload)) }
                    TextButton(onClick = {
                        viewModel.dismissConflict()
                        onSaveAs(state.editor?.document?.title ?: "document.json")
                    }) { Text(stringResource(R.string.save_as)) }
                },
            )
        }
    }
}
