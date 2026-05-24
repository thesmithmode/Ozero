package ru.ozero.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogLevel
import ru.ozero.app.logging.UnifiedLogger
import ru.ozero.app.ui.theme.OzeroPalette
import java.io.File
import java.util.Calendar

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
        onTagFilter = viewModel::onTagFilter,
        onLevelFilter = viewModel::onLevelFilter,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsScreenContent(
    state: LogsUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> String,
    onTagFilter: (String) -> Unit,
    onLevelFilter: (String) -> Unit,
) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.logs_back_cd),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { copyToClipboard(ctx, "ozero.log", onCopy()) }) {
                        Text(stringResource(R.string.logs_copy))
                    }
                    IconButton(onClick = { shareLogFile(ctx) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.logs_share_cd))
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.logs_clear_cd))
                    }
                },
            )
        },
    ) { padding ->
        LogsBody(
            state = state,
            padding = padding,
            onTagFilter = onTagFilter,
            onLevelFilter = onLevelFilter,
            onExport = { shareLogFile(ctx) },
            onClear = onClear,
        )
    }
}

private val LEVEL_FILTERS = listOf(FILTER_ALL, "DEBUG", "INFO", "WARN", "ERROR")

@Composable
private fun LogsBody(
    state: LogsUiState,
    padding: PaddingValues,
    onTagFilter: (String) -> Unit,
    onLevelFilter: (String) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()
    val filtered = state.filteredEntries

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        FilterChipRow(
            items = state.availableTags,
            selected = state.tagFilter,
            onPick = onTagFilter,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        FilterChipRow(
            items = LEVEL_FILTERS,
            selected = state.levelFilter,
            onPick = onLevelFilter,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF04091A)),
            border = BorderStroke(1.dp, OzeroPalette.GlassEdge),
        ) {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.logs_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = OzeroPalette.Text3,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(filtered, key = { "${it.timestampMs}_${it.tag}_${it.message.take(20)}" }) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }

        LogFooter(
            filtered = filtered.size,
            total = state.entries.size,
            onExport = onExport,
            onClear = onClear,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FilterChipRow(
    items: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            LogFilterChip(label = item, selected = item == selected, onClick = { onPick(item) })
        }
    }
}

@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) OzeroPalette.StateConnected else Color.Transparent
    val textColor = if (selected) OzeroPalette.Ink else OzeroPalette.Text2
    val shape = RoundedCornerShape(8.dp)
    val borderMod = if (selected) {
        Modifier
    } else {
        Modifier.border(1.dp, OzeroPalette.GlassEdge, shape)
    }
    Box(
        modifier = Modifier
            .background(bg, shape)
            .then(borderMod)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val timeStr = formatLogTime(entry.timestampMs)
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> OzeroPalette.StateDanger
        LogLevel.WARN -> OzeroPalette.Amber
        LogLevel.DEBUG, LogLevel.TRACE -> OzeroPalette.Text3
        LogLevel.INFO -> OzeroPalette.Aqua
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeStr,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text3,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
        )
        Text(
            text = entry.tag.take(12),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Violet,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
        )
        Text(
            text = entry.level.name.take(5),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = levelColor,
            modifier = Modifier.width(40.dp),
            maxLines = 1,
        )
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
    }
}

private fun formatLogTime(timestampMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestampMs
    return "%02d:%02d:%02d.%03d".format(
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
        cal.get(Calendar.MILLISECOND),
    )
}

@Composable
private fun LogFooter(
    filtered: Int,
    total: Int,
    onExport: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.logs_footer_fmt, filtered, total),
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text3,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.logs_export),
                style = MaterialTheme.typography.labelSmall,
                color = OzeroPalette.Text2,
            )
        }
        TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.logs_clear_cd),
                style = MaterialTheme.typography.labelSmall,
                color = OzeroPalette.StateDanger,
            )
        }
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
        Intent.createChooser(intent, ctx.getString(R.string.logs_share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, context.getString(R.string.logs_copy_empty_toast), Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(
            context,
            context.getString(R.string.logs_copy_done_toast_fmt, text.length),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
