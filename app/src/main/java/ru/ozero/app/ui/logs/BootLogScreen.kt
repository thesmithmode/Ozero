package ru.ozero.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ru.ozero.app.logging.BootFileLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootLogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var content by remember { mutableStateOf(BootFileLogger.read()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boot log (${content.length}b)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { content = BootFileLogger.read() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = { copyToClipboard(ctx, "boot.log", content) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                    IconButton(onClick = {
                        BootFileLogger.clear()
                        content = ""
                    }) { Icon(Icons.Default.Delete, contentDescription = "Очистить") }
                    IconButton(onClick = {
                        val file = BootFileLogger.file() ?: return@IconButton
                        if (!file.exists()) return@IconButton
                        val uri = FileProvider.getUriForFile(
                            ctx,
                            "${ctx.packageName}.fileprovider",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(
                            Intent.createChooser(intent, "Поделиться boot.log").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Поделиться") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (content.isBlank()) "(пусто)" else content,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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
