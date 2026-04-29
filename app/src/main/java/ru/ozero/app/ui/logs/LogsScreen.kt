package ru.ozero.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.logging.UnifiedLogger
import java.io.File

@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LogsScreenContent(
        state = state,
        onBack = onBack,
        onClear = viewModel::clear,
        onCopy = viewModel::copyAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsScreenContent(
    state: LogsUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> String,
) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val sizeKb = state.fileSize / 1024
                    Text("Логи (${sizeKb}KB)")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { copyToClipboard(ctx, "ozero.log", onCopy()) }) {
                        Text("Копировать")
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить")
                    }
                    IconButton(onClick = { shareLogFile(ctx) }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                },
            )
        },
    ) { padding ->
        LogsBody(state = state, padding = padding)
    }
}

@Composable
private fun LogsBody(state: LogsUiState, padding: PaddingValues) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.tail) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(8.dp)
            .verticalScroll(scrollState),
    ) {
        if (state.filePath.isNotBlank()) {
            Text(
                text = state.filePath,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        Text(
            text = if (state.tail.isBlank()) "(пусто — лог ещё не записан)" else state.tail,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun shareLogFile(ctx: Context) {
    val file: File = UnifiedLogger.file() ?: return
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(
        Intent.createChooser(intent, "Поделиться ozero.log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "Пусто — нечего копировать", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Скопировано (${text.length} симв.)", Toast.LENGTH_SHORT).show()
    }
}
