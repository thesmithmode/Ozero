package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.app.urnetwork.DayBytes
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkSharedTrafficScreen(
    onBack: () -> Unit,
    viewModel: UrnetworkSharedTrafficViewModel = hiltViewModel(),
) {
    val unpaidBytes by viewModel.unpaidBytes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val dailyBytes by viewModel.dailyBytes.collectAsStateWithLifecycle()
    UrnetworkSharedTrafficContent(
        state = UrnetworkSharedTrafficUiState(
            unpaidBytes = unpaidBytes,
            isLoading = isLoading,
            dailyBytes = dailyBytes,
        ),
        onBack = onBack,
    )
}

internal data class UrnetworkSharedTrafficUiState(
    val unpaidBytes: Long,
    val isLoading: Boolean,
    val dailyBytes: List<DayBytes>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UrnetworkSharedTrafficContent(
    state: UrnetworkSharedTrafficUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.urnetwork_shared_traffic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.dailyBytes.all { it.bytes == 0L } && state.unpaidBytes == 0L) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(UrnetworkSharedTrafficTestTags.LOADING),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(UrnetworkSharedTrafficTestTags.LOADING_INDICATOR),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag(UrnetworkSharedTrafficTestTags.CONTENT),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SharedTrafficChartCard(dailyBytes = state.dailyBytes)
                SharedTrafficTotalCard(totalBytes = state.unpaidBytes)
            }
        }
    }
}

object UrnetworkSharedTrafficTestTags {
    const val LOADING = "urnetwork_shared_traffic_loading"
    const val LOADING_INDICATOR = "urnetwork_shared_traffic_loading_indicator"
    const val CONTENT = "urnetwork_shared_traffic_content"
}

@Composable
private fun SharedTrafficChartCard(dailyBytes: List<DayBytes>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("urnetwork_shared_traffic_chart"),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.urnetwork_shared_traffic_chart_title),
                style = MaterialTheme.typography.titleSmall,
                color = OzeroPalette.Text,
                fontWeight = FontWeight.SemiBold,
            )
            val maxBytes = dailyBytes.maxOfOrNull { it.bytes } ?: 0L
            if (maxBytes == 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.urnetwork_shared_traffic_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = OzeroPalette.Text2,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                BarChart(dailyBytes = dailyBytes, maxBytes = maxBytes)
                ChartXAxisLabels(dailyBytes = dailyBytes)
                Text(
                    text = stringResource(R.string.urnetwork_shared_traffic_chart_peak, formatBytes(maxBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text2,
                )
            }
        }
    }
}

@Composable
private fun BarChart(dailyBytes: List<DayBytes>, maxBytes: Long) {
    val bars = dailyBytes.size.coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        drawChart(dailyBytes = dailyBytes, maxBytes = maxBytes, bars = bars)
    }
}

private fun DrawScope.drawChart(dailyBytes: List<DayBytes>, maxBytes: Long, bars: Int) {
    val barColor = OzeroPalette.Aqua
    val gapPx = 4f
    val totalGap = gapPx * (bars - 1).coerceAtLeast(0)
    val barWidth = ((size.width - totalGap) / bars).coerceAtLeast(1f)
    dailyBytes.forEachIndexed { index, entry ->
        val ratio = if (maxBytes <= 0L) 0f else entry.bytes.toFloat() / maxBytes
        val barHeight = (size.height * ratio).coerceAtLeast(1f)
        val x = index * (barWidth + gapPx)
        val y = size.height - barHeight
        drawRect(
            color = barColor,
            topLeft = Offset(x = x, y = y),
            size = Size(width = barWidth, height = barHeight),
        )
    }
}

@Composable
private fun ChartXAxisLabels(dailyBytes: List<DayBytes>) {
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val first = dailyBytes.firstOrNull()?.date
    val last = dailyBytes.lastOrNull()?.date
    if (first == null || last == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = first.format(fmt),
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text2,
        )
        Text(
            text = last.format(fmt),
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text2,
        )
    }
}

@Composable
private fun SharedTrafficTotalCard(totalBytes: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("urnetwork_shared_traffic_total"),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1f
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SharedTrafficTotal(labelPadding = Modifier, totalBytes = totalBytes)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharedTrafficTotal(labelPadding = Modifier.padding(end = 8.dp), totalBytes = totalBytes)
                }
            }
        }
    }
}

@Composable
private fun SharedTrafficTotal(labelPadding: Modifier, totalBytes: Long) {
    Text(
        text = stringResource(R.string.urnetwork_shared_traffic_provided),
        style = MaterialTheme.typography.bodyMedium,
        color = OzeroPalette.Text2,
        modifier = labelPadding,
    )
    Text(
        text = formatBytes(totalBytes),
        style = MaterialTheme.typography.titleMedium,
        color = OzeroPalette.Text,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
    )
}
