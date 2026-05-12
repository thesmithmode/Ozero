package ru.ozero.app.ui.strategy

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyTestScreen(
    onBack: () -> Unit,
    viewModel: StrategyTestViewModel = hiltViewModel(),
) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val strategies by viewModel.strategies.collectAsStateWithLifecycle()
    val sitesText by viewModel.sitesText.collectAsStateWithLifecycle()
    val runSummary by viewModel.runSummary.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler {
        if (isRunning) viewModel.onStop() else onBack()
    }
    val errorText = when (errorMessage) {
        StrategyTestError.VpnRunning -> stringResource(R.string.strategy_test_vpn_running_error)
        StrategyTestError.NoSites -> stringResource(R.string.strategy_test_no_sites)
        null -> null
    }
    if (errorText != null) {
        AlertDialog(
            modifier = Modifier.testTag("strategy_test_error_dialog"),
            onDismissRequest = viewModel::onErrorDismiss,
            title = { Text(stringResource(R.string.error_generic)) },
            text = { Text(errorText) },
            confirmButton = {
                TextButton(onClick = viewModel::onErrorDismiss) {
                    Text(stringResource(R.string.strategy_test_error_close))
                }
            },
        )
    }
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            modifier = Modifier.testTag("strategy_settings_sheet"),
        ) {
            StrategySettingsSheet(
                settings = settings,
                onSettingsChange = viewModel::onSettingsChange,
                enabled = !isRunning,
            )
        }
    }
    Scaffold(
        modifier = Modifier.testTag("strategy_test_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.strategy_test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("strategy_settings_btn"),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = sitesText,
                        onValueChange = viewModel::onSitesTextChange,
                        enabled = !isRunning,
                        minLines = 3,
                        maxLines = 6,
                        label = { Text(stringResource(R.string.strategy_test_sites_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("strategy_sites_input"),
                    )
                    Button(
                        onClick = if (isRunning) viewModel::onStop else viewModel::onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (isRunning) "strategy_test_stop_btn" else "strategy_test_start_btn"),
                    ) {
                        Text(
                            stringResource(
                                if (isRunning) R.string.strategy_test_stop else R.string.strategy_test_start,
                            ),
                        )
                    }
                    if (runSummary.isNotBlank()) {
                        Text(
                            text = runSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(
                items = strategies,
                key = { index, item -> item.command + "_" + index },
            ) { index, item ->
                StrategyRow(
                    index = index,
                    item = item,
                    onApply = {
                        viewModel.onApply(item.command)
                        Toast.makeText(
                            context,
                            R.string.strategy_test_applied_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun StrategySettingsSheet(
    settings: StrategyTestSettings,
    onSettingsChange: (StrategyTestSettings) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.strategy_settings_title),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = settings.requestsPerDomain.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.coerceIn(1, 20)?.let { onSettingsChange(settings.copy(requestsPerDomain = it)) }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.strategy_settings_requests_per_domain)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_requests_per_domain"),
        )
        OutlinedTextField(
            value = settings.concurrentLimit.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.coerceIn(1, 50)?.let { onSettingsChange(settings.copy(concurrentLimit = it)) }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.strategy_settings_concurrent_limit)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_concurrent_limit"),
        )
        OutlinedTextField(
            value = settings.timeoutSeconds.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.coerceIn(1, 15)?.let { onSettingsChange(settings.copy(timeoutSeconds = it)) }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.strategy_settings_timeout)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_timeout"),
        )
        OutlinedTextField(
            value = settings.delayBetweenMs.toString(),
            onValueChange = { v ->
                v.toLongOrNull()?.coerceIn(0L, 5000L)?.let { onSettingsChange(settings.copy(delayBetweenMs = it)) }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.strategy_settings_delay)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_delay"),
        )
        OutlinedTextField(
            value = settings.sniDomain,
            onValueChange = { onSettingsChange(settings.copy(sniDomain = it)) },
            enabled = enabled,
            label = { Text(stringResource(R.string.strategy_settings_sni)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_sni"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.strategy_settings_use_custom),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = settings.useCustomStrategies,
                onCheckedChange = { onSettingsChange(settings.copy(useCustomStrategies = it)) },
                enabled = enabled,
                modifier = Modifier.testTag("settings_use_custom_toggle"),
            )
        }
        if (settings.useCustomStrategies) {
            OutlinedTextField(
                value = settings.customStrategies,
                onValueChange = { onSettingsChange(settings.copy(customStrategies = it)) },
                enabled = enabled,
                minLines = 4,
                maxLines = 10,
                label = { Text(stringResource(R.string.strategy_settings_custom_strategies)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_custom_strategies"),
            )
        }
    }
}

@Composable
private fun StrategyRow(
    index: Int,
    item: StrategyResult,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strategy_item_$index"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.totalRequests > 0 && !item.isCompleted) {
                val progress = item.currentProgress.toFloat() / item.totalRequests.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = buildString {
                    append("${item.currentProgress}/${item.totalRequests}")
                    if (item.avgDurationMs > 0) append(" · avg ${item.avgDurationMs} ms")
                    item.lastSite?.let { append(" · $it") }
                    item.lastError?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (item.isCompleted) {
                        "${item.successCount}/${item.totalRequests} · ${item.successPercentage}%"
                    } else if (item.totalRequests > 0) {
                        "${item.currentProgress}/${item.totalRequests}"
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onApply,
                    modifier = Modifier.testTag("strategy_apply_$index"),
                ) {
                    Text(stringResource(R.string.strategy_test_apply))
                }
            }
        }
    }
}
