package ru.ozero.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.corestorage.entity.SessionStatsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PERIOD_DAY_MS: Long = 24L * 60L * 60L * 1000L
private const val PERIOD_WEEK_MS: Long = 7L * PERIOD_DAY_MS
private const val PERIOD_MONTH_MS: Long = 30L * PERIOD_DAY_MS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHistoryScreen(
    onBack: () -> Unit,
    viewModel: StatsHistoryViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val engines by viewModel.availableEngines.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("stats_history"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    SortMenu(current = sort, onSelect = viewModel::setSort)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterControls(
                engines = engines,
                filter = filter,
                onEngineToggle = viewModel::toggleEngineFilter,
                onPeriodChange = viewModel::setPeriod,
                onClear = viewModel::clearFilters,
            )
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.stats_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = sessions, key = { it.id }) { session ->
                        SessionCard(session)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterControls(
    engines: List<String>,
    filter: SessionFilter,
    onEngineToggle: (String) -> Unit,
    onPeriodChange: (Long?) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodChip(
                labelRes = R.string.stats_history_period_all,
                selected = filter.periodMs == null,
                onClick = { onPeriodChange(null) },
            )
            PeriodChip(
                labelRes = R.string.stats_history_period_day,
                selected = filter.periodMs == PERIOD_DAY_MS,
                onClick = { onPeriodChange(PERIOD_DAY_MS) },
            )
            PeriodChip(
                labelRes = R.string.stats_history_period_week,
                selected = filter.periodMs == PERIOD_WEEK_MS,
                onClick = { onPeriodChange(PERIOD_WEEK_MS) },
            )
            PeriodChip(
                labelRes = R.string.stats_history_period_month,
                selected = filter.periodMs == PERIOD_MONTH_MS,
                onClick = { onPeriodChange(PERIOD_MONTH_MS) },
            )
        }
        if (engines.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                engines.forEach { engineId ->
                    FilterChip(
                        selected = engineId in filter.engines,
                        onClick = { onEngineToggle(engineId) },
                        label = { Text(engineId) },
                        modifier = Modifier.testTag("engine_filter_$engineId"),
                    )
                }
            }
        }
        if (filter.engines.isNotEmpty() || filter.periodMs != null) {
            TextButton(onClick = onClear, modifier = Modifier.testTag("clear_filters")) {
                Text(stringResource(R.string.stats_history_filter_clear))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun SortMenu(current: SessionSort, onSelect: (SessionSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("sort_menu")) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.stats_history_sort_cd))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortOption(SessionSort.TIME_DESC, current, R.string.stats_history_sort_time_desc, onSelect) {
            expanded = false
        }
        SortOption(SessionSort.TIME_ASC, current, R.string.stats_history_sort_time_asc, onSelect) {
            expanded = false
        }
        SortOption(SessionSort.TRAFFIC_DESC, current, R.string.stats_history_sort_traffic_desc, onSelect) {
            expanded = false
        }
        SortOption(SessionSort.DURATION_DESC, current, R.string.stats_history_sort_duration_desc, onSelect) {
            expanded = false
        }
    }
}

@Composable
private fun SortOption(
    value: SessionSort,
    current: SessionSort,
    labelRes: Int,
    onSelect: (SessionSort) -> Unit,
    onClose: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            val prefix = if (value == current) "✓ " else "    "
            Text(prefix + stringResource(labelRes))
        },
        onClick = {
            onSelect(value)
            onClose()
        },
    )
}

@Composable
private fun SessionCard(session: SessionStatsEntity) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val started = dateFormat.format(Date(session.startedAt))
    val durationStr = BytesFormatter.durationHms(session.durationMs)
    val rxStr = BytesFormatter.humanReadable(session.rxBytes)
    val txStr = BytesFormatter.humanReadable(session.txBytes)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("session_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
    }
}
