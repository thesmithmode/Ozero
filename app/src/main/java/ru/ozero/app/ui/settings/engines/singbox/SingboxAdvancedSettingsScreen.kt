package ru.ozero.app.ui.settings.engines.singbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.ozero.enginewarp.DnsPresets
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingboxAdvancedSettingsScreen(
    onBack: () -> Unit,
    viewModel: SingboxEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRestoreDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val restoreError = state.restoreError
    LaunchedEffect(restoreError) {
        if (restoreError != null) {
            snackbarHostState.showSnackbar(restoreError)
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.singbox_restore_defaults_title)) },
            text = { Text(stringResource(R.string.singbox_restore_defaults_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    viewModel.onRestoreDefaults()
                }) {
                    Text(stringResource(R.string.singbox_restore_defaults_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.singbox_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.singbox_advanced_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.singbox_sort_section),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            SortOrderItem(
                label = stringResource(R.string.singbox_sort_by_latency),
                selected = state.sortOrder == SortOrder.BY_LATENCY,
                onClick = { viewModel.onSortOrderChanged(SortOrder.BY_LATENCY) },
            )
            SortOrderItem(
                label = stringResource(R.string.singbox_sort_by_name),
                selected = state.sortOrder == SortOrder.BY_NAME,
                onClick = { viewModel.onSortOrderChanged(SortOrder.BY_NAME) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            DnsSection(
                selectedPresetId = state.dnsPresetId,
                onPresetClick = viewModel::onDnsPresetChanged,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ProbeTimeoutSection(
                probeTimeoutSeconds = state.probeTimeoutSeconds,
                onProbeTimeoutSecondsChange = viewModel::onProbeTimeoutSecondsChange,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.singbox_actions_section),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.onPing() },
                enabled = state.groups.isNotEmpty() && state.isPinging.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("singbox_adv_ping_all"),
            ) {
                Text(stringResource(R.string.singbox_ping_all_button))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.onRefresh() },
                enabled = state.groups.isNotEmpty() && state.isRefreshing.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("singbox_adv_refresh_all"),
            ) {
                Text(stringResource(R.string.singbox_refresh_all_button))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SubscriptionsSection(
                isRestoringDefaults = state.isRestoringDefaults,
                onRestoreClick = { showRestoreDialog = true },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProbeTimeoutSection(
    probeTimeoutSeconds: Int,
    onProbeTimeoutSecondsChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.singbox_probe_timeout_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.singbox_probe_timeout_value, probeTimeoutSeconds),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = probeTimeoutSeconds.toFloat(),
            onValueChange = { onProbeTimeoutSecondsChange(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("singbox_probe_timeout_slider"),
        )
        Text(
            text = stringResource(R.string.singbox_probe_timeout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsSection(
    selectedPresetId: String,
    onPresetClick: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.singbox_dns_section),
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.singbox_dns_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    DnsPresets.ALL.forEach { preset ->
        SortOrderItem(
            label = preset.name,
            selected = preset.id == selectedPresetId,
            onClick = { onPresetClick(preset.id) },
        )
    }
}

@Composable
private fun SubscriptionsSection(
    isRestoringDefaults: Boolean,
    onRestoreClick: () -> Unit,
) {
    Text(
        text = stringResource(R.string.singbox_subscriptions_section),
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.singbox_restore_defaults_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onRestoreClick,
        enabled = !isRestoringDefaults,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("singbox_restore_defaults_button"),
    ) {
        if (isRestoringDefaults) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.singbox_restore_defaults_button))
    }
}

@Composable
private fun SortOrderItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
