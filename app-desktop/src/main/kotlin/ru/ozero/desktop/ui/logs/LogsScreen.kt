package ru.ozero.desktop.ui.logs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.logging.DesktopLogEntry
import ru.ozero.desktop.logging.DesktopLogLevel
import ru.ozero.desktop.logging.DesktopLogStore
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.theme.OzeroPalette
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Calendar

private val LEVEL_FILTERS = listOf(Strings.LOGS_FILTER_ALL, "DEBUG", "INFO", "WARN", "ERROR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val allEntries by DesktopLogStore.entries.collectAsState()
    var tagFilter by remember { mutableStateOf(Strings.LOGS_FILTER_ALL) }
    var levelFilter by remember { mutableStateOf(Strings.LOGS_FILTER_ALL) }

    val availableTags = remember(allEntries) {
        listOf(Strings.LOGS_FILTER_ALL) + allEntries.map { it.tag }.distinct().sorted()
    }

    val filtered = remember(allEntries, tagFilter, levelFilter) {
        allEntries.filter { entry ->
            (tagFilter == Strings.LOGS_FILTER_ALL || entry.tag == tagFilter) &&
                (levelFilter == Strings.LOGS_FILTER_ALL || entry.level.name == levelFilter)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.size - 1)
    }

    Scaffold(
        modifier = Modifier.testTag("logs"),
        topBar = {
            TopAppBar(
                title = { Text(Strings.LOGS_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { copyToClipboard(DesktopLogStore.copyAll()) }) {
                        Text(Strings.LOGS_COPY)
                    }
                    IconButton(onClick = { exportLogs() }) {
                        Icon(Icons.Default.Share, contentDescription = Strings.LOGS_EXPORT)
                    }
                    IconButton(onClick = { DesktopLogStore.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = Strings.LOGS_CLEAR)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            FilterChipRow(
                items = availableTags,
                selected = tagFilter,
                onPick = { tagFilter = it },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            FilterChipRow(
                items = LEVEL_FILTERS,
                selected = levelFilter,
                onPick = { levelFilter = it },
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
                            text = Strings.LOGS_EMPTY,
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
                        items(
                            filtered,
                            key = { "${it.timestampMs}_${it.tag}_${it.message.take(20)}" },
                        ) { entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }

            LogFooter(
                filtered = filtered.size,
                total = allEntries.size,
                onExport = { exportLogs() },
                onClear = { DesktopLogStore.clear() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
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
private fun LogEntryRow(entry: DesktopLogEntry) {
    val timeStr = formatLogTime(entry.timestampMs)
    val levelColor = when (entry.level) {
        DesktopLogLevel.ERROR -> OzeroPalette.StateDanger
        DesktopLogLevel.WARN -> OzeroPalette.Amber
        DesktopLogLevel.DEBUG, DesktopLogLevel.TRACE -> OzeroPalette.Text3
        DesktopLogLevel.INFO -> OzeroPalette.Aqua
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
            text = Strings.logsFooter(filtered, total),
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text3,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onExport,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = Strings.LOGS_EXPORT,
                style = MaterialTheme.typography.labelSmall,
                color = OzeroPalette.Text2,
            )
        }
        TextButton(
            onClick = onClear,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = Strings.LOGS_CLEAR,
                style = MaterialTheme.typography.labelSmall,
                color = OzeroPalette.StateDanger,
            )
        }
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

private fun copyToClipboard(text: String) {
    if (text.isBlank()) return
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

private fun exportLogs() {
    val dialog = FileDialog(null as Frame?, Strings.LOGS_EXPORT, FileDialog.SAVE)
    dialog.file = "ozero-logs.txt"
    dialog.isVisible = true
    val dir = dialog.directory ?: return
    val file = dialog.file ?: return
    DesktopLogStore.export(File(dir, file))
}
