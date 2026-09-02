package io.github.xprss.quickjson

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.xprss.quickjson.ui.MainViewModel
import io.github.xprss.quickjson.ui.QuickJsonApp
import io.github.xprss.quickjson.ui.SaveRequest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as QuickJsonApplication).container)
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importUri)
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { lifecycleScope.launch { handleSaveResult(viewModel.exportTo(it)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickJsonApp(
                viewModel = viewModel,
                onImport = { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) },
                onNewFromClipboard = { viewModel.importText(clipboardText()) },
                onCopy = { raw -> copyToClipboard(raw) },
                onSave = { requestSave() },
                onSaveAs = { suggested -> createDocument.launch(suggested) },
                onShare = { share() },
            )
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        viewModel.flush()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let(viewModel::importUri)
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.getItemAt(0)?.uri
                if (uri != null) viewModel.importUri(uri)
                else intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::importText)
            }
        }
    }

    private fun requestSave() = lifecycleScope.launch {
        when (val result = viewModel.requestSave()) {
            SaveRequest.ChooseDestination -> createDocument.launch(viewModel.uiState.value.editor?.document?.title ?: "document.json")
            else -> handleSaveResult(result)
        }
    }

    private fun handleSaveResult(result: SaveRequest) {
        when (result) {
            SaveRequest.Saved -> viewModel.showMessage(getString(R.string.saved))
            SaveRequest.Invalid -> viewModel.showMessage(getString(R.string.fix_before_export))
            is SaveRequest.Failed -> viewModel.showMessage(result.message)
            SaveRequest.ChooseDestination, SaveRequest.Conflict -> Unit
        }
    }

    private fun clipboardText(): String =
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()

    private fun copyToClipboard(raw: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("JSON", raw))
        viewModel.showMessage(getString(R.string.copied))
    }

    private fun share() {
        val uri = viewModel.shareUri() ?: return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(contentResolver, "JSON", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                getString(R.string.share),
            ),
        )
    }
}
