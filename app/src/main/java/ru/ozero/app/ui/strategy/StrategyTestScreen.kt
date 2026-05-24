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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val domainLists by viewModel.domainLists.collectAsStateWithLifecycle()
    val savedStrategies by viewModel.savedStrategies.collectAsStateWithLifecycle()
    val evolutionState by viewModel.evolutionState.collectAsStateWithLifecycle()
    val runSummary by viewModel.runSummary.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val usageHistory by viewModel.usageHistory.collectAsStateWithLifecycle()
    val currentNetworkId by viewModel.currentNetworkId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDomainLists by rememberSaveable { mutableStateOf(false) }
    var showSaved by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val domainSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val savedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
    if (showDomainLists) {
        ModalBottomSheet(
            onDismissRequest = { showDomainLists = false },
            sheetState = domainSheetState,
            modifier = Modifier.testTag("domain_lists_sheet"),
        ) {
            DomainListsSheet(
                lists = domainLists,
                onToggle = viewModel::onToggleDomainList,
                onAdd = viewModel::onAddDomainList,
                onDelete = viewModel::onDeleteDomainList,
                onReset = viewModel::onResetDomainLists,
                enabled = !isRunning,
            )
        }
    }
    if (showSaved) {
        ModalBottomSheet(
            onDismissRequest = { showSaved = false },
            sheetState = savedSheetState,
            modifier = Modifier.testTag("saved_strategies_sheet"),
        ) {
            SavedStrategiesSheet(
                strategies = savedStrategies,
                usageHistory = usageHistory,
                currentNetworkId = currentNetworkId,
                onApply = { cmd ->
                    viewModel.onApply(cmd)
                    Toast.makeText(context, R.string.strategy_test_applied_toast, Toast.LENGTH_SHORT).show()
                },
                onDelete = viewModel::onDeleteSaved,
                onPin = { id, pin -> viewModel.onPinSaved(id, pin) },
                onEdit = viewModel::onEdit,
                onAddManual = viewModel::onAddManual,
            )
        }
    }
    StrategyTestScaffold(
        isRunning = isRunning,
        strategies = strategies,
        savedStrategies = savedStrategies,
        evolutionState = evolutionState,
        runSummary = runSummary,
        settings = settings,
        onBack = onBack,
        onShowSheet = { target ->
            when (target) {
                SheetTarget.Saved -> showSaved = true
                SheetTarget.Settings -> showSettings = true
                SheetTarget.DomainLists -> showDomainLists = true
            }
        },
        onModeChange = { deep ->
            if (!isRunning) viewModel.onSettingsChange(settings.copy(evolutionMode = deep))
        },
        onRunToggle = if (isRunning) viewModel::onStop else viewModel::onStart,
        onApply = viewModel::onApply,
        onToggleSave = viewModel::onToggleSave,
    )
}

private enum class SheetTarget { Saved, Settings, DomainLists }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyTestScaffold(
    isRunning: Boolean,
    strategies: List<StrategyResult>,
    savedStrategies: List<SavedStrategy>,
    evolutionState: EvolutionUiState?,
    runSummary: String,
    settings: StrategyTestSettings,
    onBack: () -> Unit,
    onShowSheet: (SheetTarget) -> Unit,
    onModeChange: (deep: Boolean) -> Unit,
    onRunToggle: () -> Unit,
    onApply: (String) -> Unit,
    onToggleSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val isDeepMode = settings.evolutionMode
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
                actions = { StrategyTopBarActions(onShowSheet) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanModeSelector(
                        isDeep = isDeepMode,
                        enabled = !isRunning,
                        onModeChange = onModeChange,
                    )
                    Button(
                        onClick = onRunToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                if (isRunning) "strategy_test_stop_btn" else "strategy_test_start_btn",
                            ),
                    ) {
                        Text(
                            stringResource(
                                if (isRunning) R.string.strategy_test_stop else R.string.strategy_test_start,
                            ),
                        )
                    }
                    if (isRunning && !isDeepMode && strategies.isEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("strategy_test_running_progress"),
                        )
                    }
                    if (runSummary.isNotBlank()) {
                        Text(
                            text = runSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isDeepMode) {
                        evolutionState?.let { evo ->
                            EvolutionStateCard(
                                state = evo,
                                savedStrategies = savedStrategies,
                                onApply = { cmd ->
                                    onApply(cmd)
                                    Toast.makeText(
                                        context,
                                        R.string.strategy_test_applied_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onToggleSave = onToggleSave,
                            )
                        }
                    }
                }
            }
            if (!isDeepMode) {
                itemsIndexed(
                    items = strategies,
                    key = { index, item -> item.command + "_" + index },
                ) { index, item ->
                    StrategyRow(
                        index = index,
                        item = item,
                        isSaved = savedStrategies.any { it.command == item.command },
                        onApply = {
                            onApply(item.command)
                            Toast.makeText(context, R.string.strategy_test_applied_toast, Toast.LENGTH_SHORT).show()
                        },
                        onToggleSave = { onToggleSave(item.command) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyTopBarActions(onShowSheet: (SheetTarget) -> Unit) {
    IconButton(
        onClick = { onShowSheet(SheetTarget.DomainLists) },
        modifier = Modifier.testTag("domain_lists_btn"),
    ) {
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.domain_lists_title))
    }
    IconButton(
        onClick = { onShowSheet(SheetTarget.Saved) },
        modifier = Modifier.testTag("saved_strategies_btn"),
    ) {
        Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.saved_strategies_title))
    }
    IconButton(
        onClick = { onShowSheet(SheetTarget.Settings) },
        modifier = Modifier.testTag("strategy_settings_btn"),
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanModeSelector(
    isDeep: Boolean,
    enabled: Boolean,
    onModeChange: (deep: Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().testTag("scan_mode_selector"),
    ) {
        SegmentedButton(
            selected = !isDeep,
            onClick = { if (enabled) onModeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("scan_mode_fast"),
        ) {
            Text(stringResource(R.string.scan_mode_fast))
        }
        SegmentedButton(
            selected = isDeep,
            onClick = { if (enabled) onModeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("scan_mode_deep"),
        ) {
            Text(stringResource(R.string.scan_mode_deep))
        }
    }
}

@Composable
private fun DomainListsSheet(
    lists: List<DomainList>,
    onToggle: (String) -> Unit,
    onAdd: (String, List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onReset: () -> Unit,
    enabled: Boolean,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddDialog) {
        AddDomainListDialog(
            onConfirm = { name, sites ->
                onAdd(name, sites)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.domain_lists_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                TextButton(onClick = onReset, enabled = enabled) {
                    Text(stringResource(R.string.domain_list_reset_defaults))
                }
                FloatingActionButton(
                    onClick = { if (enabled) showAddDialog = true },
                    modifier = Modifier.testTag("domain_list_add_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.domain_list_add))
                }
            }
        }
        lists.forEach { list ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("domain_list_card_${list.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = list.isActive,
                        onCheckedChange = { if (enabled) onToggle(list.id) },
                        enabled = enabled,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = list.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.domain_list_sites_count, list.domains.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (list.domains.isNotEmpty()) {
                            Text(
                                text = list.domains.take(3).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!list.isBuiltIn) {
                        TextButton(
                            onClick = { onDelete(list.id) },
                            enabled = enabled,
                            modifier = Modifier.testTag("domain_list_delete_${list.id}"),
                        ) {
                            Text(stringResource(R.string.domain_list_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDomainListDialog(
    onConfirm: (name: String, sites: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var sitesText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.domain_list_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.domain_list_name_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("add_list_name"),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sitesText,
                    onValueChange = { sitesText = it },
                    label = { Text(stringResource(R.string.domain_list_sites_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("add_list_sites"),
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sites = sitesText.lines().map(String::trim).filter(String::isNotEmpty)
                    if (name.isNotBlank() && sites.isNotEmpty()) onConfirm(name.trim(), sites)
                },
                modifier = Modifier.testTag("add_list_confirm"),
            ) {
                Text(stringResource(R.string.domain_list_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.domain_list_cancel))
            }
        },
    )
}

@Composable
private fun EvolutionStateCard(
    state: EvolutionUiState,
    savedStrategies: List<SavedStrategy> = emptyList(),
    onApply: (String) -> Unit = {},
    onToggleSave: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("evolution_state_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.isInitializing) {
                Text(
                    text = stringResource(R.string.evolution_initializing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                if (state.stagnationCount >= 2) {
                    Text(
                        text = stringResource(R.string.evolution_stagnating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.evaluatingCommand != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            if (state.evaluatingTotal > 0) {
                                state.evaluatingIndex.toFloat() / state.evaluatingTotal
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = state.evaluatingCommand,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.topChromosomes.isNotEmpty()) {
                EvolutionTopChromosomes(
                    chromosomes = state.topChromosomes,
                    savedStrategies = savedStrategies,
                    onToggleSave = onToggleSave,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun EvolutionTopChromosomes(
    chromosomes: List<Pair<String, Double>>,
    savedStrategies: List<SavedStrategy>,
    onToggleSave: (String) -> Unit,
    onApply: (String) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    Text(
        text = stringResource(R.string.evolution_population_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    chromosomes.forEachIndexed { idx, (cmd, fitness) ->
        val isSaved = savedStrategies.any { it.command == cmd }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("evolution_top_$idx"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text(
                    text = "${(fitness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Text(
                text = cmd,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onToggleSave(cmd) },
                modifier = Modifier.testTag("evolution_save_$idx"),
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.saved_strategy_save),
                    tint = if (isSaved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
            OutlinedButton(
                onClick = { onApply(cmd) },
                modifier = Modifier.testTag("evolution_apply_$idx"),
            ) {
                Text(stringResource(R.string.strategy_test_apply))
            }
        }
    }
}

@Composable
private fun CustomStrategiesSection(
    settings: StrategyTestSettings,
    onSettingsChange: (StrategyTestSettings) -> Unit,
    enabled: Boolean,
) {
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

@Composable
private fun StrategySettingsSheet(
    settings: StrategyTestSettings,
    onSettingsChange: (StrategyTestSettings) -> Unit,
    enabled: Boolean,
) {
    var requestsPerDomainDraft by rememberSaveable { mutableStateOf(settings.requestsPerDomain.toString()) }
    var concurrentLimitDraft by rememberSaveable { mutableStateOf(settings.concurrentLimit.toString()) }
    var timeoutSecondsDraft by rememberSaveable { mutableStateOf(settings.timeoutSeconds.toString()) }
    var delayBetweenMsDraft by rememberSaveable { mutableStateOf(settings.delayBetweenMs.toString()) }

    var requestsError by rememberSaveable { mutableStateOf(false) }
    var concurrentError by rememberSaveable { mutableStateOf(false) }
    var timeoutError by rememberSaveable { mutableStateOf(false) }
    var delayError by rememberSaveable { mutableStateOf(false) }

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
            value = requestsPerDomainDraft,
            onValueChange = {
                requestsPerDomainDraft = it
                requestsError = it.isBlank()
            },
            enabled = enabled,
            isError = requestsError,
            supportingText = if (requestsError) {
                { Text(stringResource(R.string.strategy_settings_field_empty)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.strategy_settings_requests_per_domain)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_requests_per_domain"),
        )
        OutlinedTextField(
            value = concurrentLimitDraft,
            onValueChange = {
                concurrentLimitDraft = it
                concurrentError = it.isBlank()
            },
            enabled = enabled,
            isError = concurrentError,
            supportingText = if (concurrentError) {
                { Text(stringResource(R.string.strategy_settings_field_empty)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.strategy_settings_concurrent_limit)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_concurrent_limit"),
        )
        OutlinedTextField(
            value = timeoutSecondsDraft,
            onValueChange = {
                timeoutSecondsDraft = it
                timeoutError = it.isBlank()
            },
            enabled = enabled,
            isError = timeoutError,
            supportingText = if (timeoutError) {
                { Text(stringResource(R.string.strategy_settings_field_empty)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.strategy_settings_timeout)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_timeout"),
        )
        OutlinedTextField(
            value = delayBetweenMsDraft,
            onValueChange = {
                delayBetweenMsDraft = it
                delayError = it.isBlank()
            },
            enabled = enabled,
            isError = delayError,
            supportingText = if (delayError) {
                { Text(stringResource(R.string.strategy_settings_field_empty)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.strategy_settings_delay)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_delay"),
        )
        if (!settings.evolutionMode) {
            CustomStrategiesSection(settings = settings, onSettingsChange = onSettingsChange, enabled = enabled)
        }
        Button(
            onClick = {
                val requests = requestsPerDomainDraft.toIntOrNull()?.coerceIn(1, 20)
                val concurrent = concurrentLimitDraft.toIntOrNull()?.coerceIn(1, 50)
                val timeout = timeoutSecondsDraft.toIntOrNull()?.coerceIn(1, 15)
                val delay = delayBetweenMsDraft.toLongOrNull()?.coerceIn(0L, 5000L)
                requestsError = requests == null
                concurrentError = concurrent == null
                timeoutError = timeout == null
                delayError = delay == null
                val allFieldsValid = requests != null && concurrent != null && timeout != null && delay != null
                if (allFieldsValid) {
                    onSettingsChange(
                        settings.copy(
                            requestsPerDomain = requests,
                            concurrentLimit = concurrent,
                            timeoutSeconds = timeout,
                            delayBetweenMs = delay,
                        ),
                    )
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("settings_save_btn"),
        ) {
            Text(stringResource(R.string.strategy_settings_save))
        }
    }
}

private data class EditDialogState(val id: String?, val name: String, val command: String)

@Composable
private fun SavedStrategiesSheet(
    strategies: List<SavedStrategy>,
    usageHistory: List<UsageEntry>,
    currentNetworkId: String,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onAddManual: (String, String) -> Unit,
) {
    var editState by remember { mutableStateOf<EditDialogState?>(null) }
    var savedExpanded by rememberSaveable { mutableStateOf(true) }
    var historyExpanded by rememberSaveable { mutableStateOf(true) }

    editState?.let { state ->
        SavedStrategyEditDialog(
            state = state,
            onDismiss = { editState = null },
            onConfirm = { name, command ->
                if (state.id == null) onAddManual(name, command) else onEdit(state.id, name, command)
                editState = null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = stringResource(R.string.saved_strategies_title),
                expanded = savedExpanded,
                onToggle = { savedExpanded = !savedExpanded },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { editState = EditDialogState(id = null, name = "", command = "") },
                modifier = Modifier.testTag("add_manual_button"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.saved_strategy_add_button))
            }
        }
        if (savedExpanded) {
            if (strategies.isEmpty()) {
                Text(
                    text = stringResource(R.string.saved_strategies_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            val sorted = strategies.sortedWith(
                compareByDescending<SavedStrategy> { it.isPinned }.thenByDescending { it.addedAt },
            )
            sorted.forEach { saved ->
                SavedStrategyCard(
                    saved = saved,
                    currentNetworkId = currentNetworkId,
                    onApply = { onApply(saved.command) },
                    onDelete = { onDelete(saved.id) },
                    onPin = { onPin(saved.id, !saved.isPinned) },
                    onEdit = {
                        editState = EditDialogState(
                            id = saved.id,
                            name = saved.name ?: "",
                            command = saved.command,
                        )
                    },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        SectionHeader(
            title = stringResource(R.string.usage_history_title),
            expanded = historyExpanded,
            onToggle = { historyExpanded = !historyExpanded },
        )
        if (historyExpanded) {
            if (usageHistory.isEmpty()) {
                Text(
                    text = stringResource(R.string.usage_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            usageHistory.forEachIndexed { idx, entry ->
                HistoryEntryRow(
                    entry = entry,
                    index = idx,
                    onApply = { onApply(entry.command) },
                )
            }
        }
    }
}

@Composable
private fun SavedStrategyEditDialog(
    state: EditDialogState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit,
) {
    var name by remember { mutableStateOf(state.name) }
    var command by remember { mutableStateOf(state.command) }
    var commandError by remember { mutableStateOf(false) }
    val commandErrorText: (@Composable () -> Unit)? = if (commandError) {
        { Text(stringResource(R.string.saved_strategy_command_empty_error)) }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.id == null) R.string.saved_strategy_add_title else R.string.saved_strategy_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.saved_strategy_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = {
                        command = it
                        commandError = false
                    },
                    label = { Text(stringResource(R.string.saved_strategy_command_hint)) },
                    isError = commandError,
                    supportingText = commandErrorText,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (command.isBlank()) {
                        commandError = true
                    } else {
                        onConfirm(name, command)
                    }
                },
            ) { Text(stringResource(R.string.saved_strategy_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.saved_strategy_cancel)) }
        },
    )
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onToggle,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (expanded) "▼ $title" else "▶ $title",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SavedStrategyCard(
    saved: SavedStrategy,
    currentNetworkId: String,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("saved_item_${saved.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = saved.name ?: saved.command.take(30),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("saved_edit_${saved.id}"),
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.saved_strategy_edit_title),
                    )
                }
                IconButton(
                    onClick = onPin,
                    modifier = Modifier.testTag("saved_pin_${saved.id}"),
                ) {
                    Icon(
                        if (saved.isPinned) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (saved.isPinned) R.string.saved_strategy_unpin else R.string.saved_strategy_pin,
                        ),
                        tint = if (saved.isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
            Text(
                text = saved.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StalenessLabel(lastVerifiedAtMs = saved.lastVerifiedAtMs)
            NetworkBadge(bestNetworks = saved.bestNetworks, currentNetworkId = currentNetworkId)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier.testTag("saved_apply_${saved.id}"),
                ) { Text(stringResource(R.string.saved_strategy_apply)) }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("saved_delete_${saved.id}"),
                ) { Text(stringResource(R.string.saved_strategy_delete)) }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(entry: UsageEntry, index: Int, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("history_item_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            entry.name?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = entry.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatRelativeTime(entry.appliedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        TextButton(
            onClick = onApply,
            modifier = Modifier.testTag("history_apply_$index"),
        ) { Text(stringResource(R.string.saved_strategy_apply)) }
    }
}

@Composable
private fun StalenessLabel(lastVerifiedAtMs: Long) {
    if (lastVerifiedAtMs <= 0L) {
        Text(
            text = stringResource(R.string.strategy_staleness_unverified),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        return
    }
    val ageMs = System.currentTimeMillis() - lastVerifiedAtMs
    val days = ageMs / (24L * 60L * 60L * 1000L)
    val stale = days >= STALE_THRESHOLD_DAYS
    val text = if (stale) {
        stringResource(R.string.strategy_staleness_stale_fmt, formatRelativeTime(lastVerifiedAtMs))
    } else {
        stringResource(R.string.strategy_staleness_fresh_fmt, formatRelativeTime(lastVerifiedAtMs))
    }
    val color = if (stale) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun NetworkBadge(bestNetworks: Set<String>, currentNetworkId: String) {
    if (bestNetworks.isEmpty()) return
    val onCurrentNetwork = currentNetworkId.isNotBlank() && bestNetworks.contains(currentNetworkId)
    val text = if (onCurrentNetwork) {
        stringResource(R.string.strategy_badge_best_on_network)
    } else {
        stringResource(R.string.strategy_badge_best_other_network)
    }
    val color = if (onCurrentNetwork) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

private const val STALE_THRESHOLD_DAYS: Long = 7L

@Composable
private fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val mins = diffMs / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 1 -> stringResource(R.string.strategy_time_just_now)
        mins < 60 -> stringResource(R.string.strategy_time_min_ago_fmt, mins)
        hours < 24 -> stringResource(R.string.strategy_time_hour_ago_fmt, hours)
        else -> stringResource(R.string.strategy_time_day_ago_fmt, days)
    }
}

@Composable
private fun StrategyRow(
    index: Int,
    item: StrategyResult,
    isSaved: Boolean,
    onApply: () -> Unit,
    onToggleSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strategy_item_$index"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                Row {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier.testTag("strategy_save_$index"),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.saved_strategy_save),
                            tint = if (isSaved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
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
}
