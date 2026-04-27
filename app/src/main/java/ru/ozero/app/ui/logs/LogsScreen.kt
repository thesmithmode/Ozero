package ru.ozero.app.ui.logs

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        onLevelChange = viewModel::setLevel,
        onClear = viewModel::clear,
        onExport = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsScreenContent(
    state: LogsUiState,
    onBack: () -> Unit,
    onLevelChange: (LogLevel) -> Unit,
    onClear: () -> Unit,
    onExport: LogsViewModel,
) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Логи (${state.filtered.size}/${state.totalCaptured})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = state.filtered.joinToString("\n") { e ->
                            "${TS_FMT.format(Date(e.timestampMs))} ${e.level.short} ${e.tag}: ${e.message}"
                        }
                        copyToClipboard(ctx, "logs", text)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val file = onExport.exportToFile()
                            val authority = "${ctx.packageName}.fileprovider"
                            val uri = FileProvider.getUriForFile(ctx, authority, file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(
                                Intent.createChooser(intent, "Сохранить логи").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Сохранить / поделиться")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LevelSelector(
                current = state.minLevel,
                onSelect = onLevelChange,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            HorizontalDivider()
            LogList(entries = state.filtered, padding = PaddingValues(8.dp))
        }
    }
}

@Composable
private fun LevelSelector(
    current: LogLevel,
    onSelect: (LogLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogLevel.values().forEach { level ->
            AssistChip(
                onClick = { onSelect(level) },
                label = { Text(level.name) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (level == current) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            )
        }
    }
}

@Composable
private fun LogList(entries: List<LogEntry>, padding: PaddingValues) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= entries.size - 5) {
                listState.scrollToItem(entries.size - 1)
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = entries, key = { it.timestampMs.toString() + it.tag + it.message.hashCode() }) { entry ->
            LogRow(entry)
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.TRACE -> Color(0xFF888888)
        LogLevel.DEBUG -> Color(0xFF6BA3FF)
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.WARN -> Color(0xFFE0A800)
        LogLevel.ERROR -> Color(0xFFE05A5A)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = "${TS_FMT.format(Date(entry.timestampMs))} ${entry.level.short} ${entry.tag}: ${entry.message}",
            color = color,
            fontFamily = FontFamily.Monospace,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val TS_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
