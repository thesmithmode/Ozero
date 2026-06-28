package ru.ozero.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.app.ui.components.addSmooth
import ru.ozero.app.ui.components.chartNiceMax
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.corestorage.entity.SessionStatsEntity
import ru.ozero.enginescore.EngineId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ENGINE_LINE_PALETTE = listOf(
    OzeroPalette.Aqua,
    OzeroPalette.Amber,
    OzeroPalette.Violet,
    OzeroPalette.Teal,
    OzeroPalette.StateConnected,
    OzeroPalette.StateDanger,
)
private val ALL_LINE_COLOR = OzeroPalette.Text

private fun engineLineColor(engineId: String): Color {
    val idx = EngineId.entries.indexOfFirst {
        it.name.equals(engineId, ignoreCase = true)
    }.takeIf { it >= 0 } ?: engineId.hashCode().and(0x7FFFFFFF).rem(ENGINE_LINE_PALETTE.size)
    return ENGINE_LINE_PALETTE[idx % ENGINE_LINE_PALETTE.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficStatsScreen(
    onBack: () -> Unit,
    viewModel: TrafficStatsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val timeframe by viewModel.timeframe.collectAsStateWithLifecycle()
    val engineFilter by viewModel.engineFilter.collectAsStateWithLifecycle()
    val availableEngines by viewModel.availableEngines.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val engineSummaries by viewModel.engineSummaries.collectAsStateWithLifecycle()
    val chartData by viewModel.chartData.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionsExpanded by viewModel.sessionsExpanded.collectAsStateWithLifecycle()
    val sessionSort by viewModel.sessionSort.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.testTag("traffic_stats"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_traffic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TimeframeRow(
                    current = timeframe,
                    onSelect = viewModel::setTimeframe,
                )
            }
            if (availableEngines.size > 1) {
                item {
                    EngineFilterRow(
                        engines = availableEngines,
                        selected = engineFilter,
                        onToggle = viewModel::toggleEngineFilter,
                        onClear = viewModel::clearEngineFilter,
                    )
                }
            }
            item {
                SummaryRow(summary = summary)
            }
            if (chartData.buckets.size >= 2) {
                item {
                    TrafficLineChart(
                        data = chartData,
                        timeframe = timeframe,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("traffic_chart"),
                    )
                }
                item {
                    ChartLegend(
                        engineIds = chartData.lines.keys
                            .filter { it != ENGINE_ID_ALL }
                            .sorted(),
                        showAll = chartData.lines.size > 2,
                    )
                }
            }
            if (engineSummaries.size > 1) {
                item {
                    EngineBreakdown(engineSummaries = engineSummaries)
                }
            }
            item {
                SessionsDrillDown(
                    sessions = sessions,
                    expanded = sessionsExpanded,
                    sort = sessionSort,
                    onToggle = { viewModel.setSessionsExpanded(!sessionsExpanded) },
                    onSortSelect = viewModel::setSessionSort,
                    onDeleteSession = viewModel::deleteSession,
                    onClearSessions = viewModel::clearSessions,
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeRow(
    current: TrafficTimeframe,
    onSelect: (TrafficTimeframe) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrafficTimeframe.entries.forEach { tf ->
            FilterChip(
                selected = tf == current,
                onClick = { onSelect(tf) },
                label = { Text(stringResource(tf.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.testTag("tf_${tf.name.lowercase()}"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineFilterRow(
    engines: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        engines.forEach { engineId ->
            val color = engineLineColor(engineId)
            FilterChip(
                selected = engineId in selected,
                onClick = { onToggle(engineId) },
                label = { Text(engineId) },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = color,
                        content = {},
                    )
                },
                modifier = Modifier.testTag("engine_filter_$engineId"),
            )
        }
        if (selected.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.stats_history_filter_clear))
            }
        }
    }
}

@Composable
private fun SummaryRow(summary: TrafficSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "↓ ${BytesFormatter.humanReadable(summary.totalRx)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Aqua,
        )
        Text(
            text = "↑ ${BytesFormatter.humanReadable(summary.totalTx)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Amber,
        )
        Text(
            text = "${summary.sessionCount} ${stringResource(R.string.stats_traffic_sessions)}",
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Text3,
        )
    }
}

@Composable
private fun TrafficLineChart(
    data: TrafficChartData,
    timeframe: TrafficTimeframe,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gridColor = OzeroPalette.Text3.copy(alpha = 0.2f)
    val borderColor = OzeroPalette.Text3.copy(alpha = 0.3f)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = OzeroPalette.Text3,
        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
    )

    val allValues = data.lines.values.flatten()
    val niceMax = remember(data) {
        val raw = if (allValues.isEmpty()) 0f else allValues.maxOf { it }.toFloat()
        chartNiceMax(raw)
    }
    val maxLabel = BytesFormatter.humanReadable(niceMax.toLong())
    val midLabel = BytesFormatter.humanReadable((niceMax / 2).toLong())

    val bucketLabels = remember(data.buckets, timeframe) {
        buildBucketLabels(data.buckets, timeframe)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = modifier.padding(8.dp)) {
            Row(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(maxLabel, style = axisStyle)
                    Text(midLabel, style = axisStyle)
                    Text("0", style = axisStyle)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val w = size.width
                    val h = size.height
                    val linePx = with(density) { 1.dp.toPx() }
                    for (i in 1..3) {
                        val y = h * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), linePx)
                    }
                    drawRect(borderColor, style = Stroke(width = linePx))
                    if (data.buckets.size < 2 || niceMax <= 0f) return@Canvas
                    val step = w / (data.buckets.size - 1)
                    val curvePx = with(density) { 2.dp.toPx() }
                    val stroke = Stroke(
                        width = curvePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    data.lines.forEach { (engineId, values) ->
                        val color = if (engineId == ENGINE_ID_ALL) {
                            ALL_LINE_COLOR
                        } else {
                            engineLineColor(engineId)
                        }
                        val path = Path()
                        path.addSmooth(values.map { it.toFloat() }, step, h, niceMax)
                        drawPath(path, color, style = stroke)
                    }
                }
            }
            if (bucketLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    bucketLabels.forEach { label ->
                        Text(label, style = axisStyle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(engineIds: List<String>, showAll: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAll) {
            LegendItem(color = ALL_LINE_COLOR, label = stringResource(R.string.stats_traffic_all_engines))
        }
        engineIds.forEach { engineId ->
            LegendItem(color = engineLineColor(engineId), label = engineId)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = color,
            content = {},
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text2,
        )
    }
}

@Composable
private fun EngineBreakdown(engineSummaries: List<EngineSummary>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            engineSummaries.forEachIndexed { index, es ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = OzeroPalette.Line,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = engineLineColor(es.engineId),
                            content = {},
                        )
                        Text(
                            text = es.engineId,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "↓ ${BytesFormatter.humanReadable(es.rx)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OzeroPalette.Aqua,
                        )
                        Text(
                            text = "↑ ${BytesFormatter.humanReadable(es.tx)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OzeroPalette.Amber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsDrillDown(
    sessions: List<SessionStatsEntity>,
    expanded: Boolean,
    sort: SessionSort,
    onToggle: () -> Unit,
    onSortSelect: (SessionSort) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearSessions: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.stats_traffic_sessions) +
                        if (sessions.isNotEmpty()) " (${sessions.size})" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sessions.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("clear_sessions"),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.stats_history_clear_cd),
                            )
                        }
                    }
                    SessionSortMenu(current = sort, onSelect = onSortSelect)
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = sessions,
                    key = { it.id },
                ) { session ->
                    SessionCard(
                        session = session,
                        onDelete = { onDeleteSession(session.id) },
                    )
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.stats_history_clear_title)) },
            text = { Text(stringResource(R.string.stats_history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearSessions()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = OzeroPalette.StateDanger),
                ) {
                    Text(stringResource(R.string.stats_history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.stats_history_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun SessionSortMenu(current: SessionSort, onSelect: (SessionSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("sort_menu"),
        ) {
            Text("↕", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                SessionSort.TIME_DESC to R.string.stats_history_sort_time_desc,
                SessionSort.TIME_ASC to R.string.stats_history_sort_time_asc,
                SessionSort.TRAFFIC_DESC to R.string.stats_history_sort_traffic_desc,
                SessionSort.DURATION_DESC to R.string.stats_history_sort_duration_desc,
            ).forEach { (sort, labelRes) ->
                DropdownMenuItem(
                    text = {
                        Text((if (sort == current) "✓ " else "    ") + stringResource(labelRes))
                    },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionStatsEntity,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val started = dateFormat.format(Date(session.startedAt))
    val durationStr = BytesFormatter.durationHms(session.durationMs)
    val rxStr = BytesFormatter.humanReadable(session.rxBytes)
    val txStr = BytesFormatter.humanReadable(session.txBytes)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "$started · ${session.engineId} · $durationStr",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.stats_history_traffic, rxStr, txStr),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_session_${session.id}"),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.stats_history_delete_cd),
                    tint = OzeroPalette.StateDanger,
                )
            }
        }
    }
}

private fun buildBucketLabels(
    buckets: List<Long>,
    timeframe: TrafficTimeframe,
): List<String> {
    if (buckets.isEmpty()) return emptyList()
    val targetCount = 5
    val step = (buckets.size / targetCount).coerceAtLeast(1)
    val indices = (0 until buckets.size step step).take(targetCount)
    val fmt = when (timeframe) {
        TrafficTimeframe.DAY -> SimpleDateFormat("HH:mm", Locale.getDefault())
        TrafficTimeframe.WEEK, TrafficTimeframe.MONTH -> SimpleDateFormat("dd.MM", Locale.getDefault())
        TrafficTimeframe.YEAR, TrafficTimeframe.ALL -> SimpleDateFormat("MM.yy", Locale.getDefault())
    }
    return indices.map { i -> fmt.format(Date(buckets[i])) }
}
