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
import androidx.compose.material.icons.filled.Add
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
    val domainLists by viewModel.domainLists.collectAsStateWithLifecycle()
    val savedStrategies by viewModel.savedStrategies.collectAsStateWithLifecycle()
    val evolutionState by viewModel.evolutionState.collectAsStateWithLifecycle()
    val runSummary by viewModel.runSummary.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
                onApply = { cmd ->
                    viewModel.onApply(cmd)
                    Toast.makeText(context, R.string.strategy_test_applied_toast, Toast.LENGTH_SHORT).show()
                },
                onDelete = viewModel::onDeleteSaved,
                onPin = { id, pin -> viewModel.onPinSaved(id, pin) },
            )
        }
    }
    StrategyTestScaffold(
        isRunning = isRunning,
        strategies = strategies,
        domainLists = domainLists,
        savedStrategies = savedStrategies,
        evolutionState = evolutionState,
        runSummary = runSummary,
        onBack = onBack,
        onShowSaved = { showSaved = true },
        onShowSettings = { showSettings = true },
        onShowDomainLists = { showDomainLists = true },
        onToggleDomainList = viewModel::onToggleDomainList,
        onRunToggle = if (isRunning) viewModel::onStop else viewModel::onStart,
        onApply = viewModel::onApply,
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyTestScaffold(
    isRunning: Boolean,
    strategies: List<StrategyResult>,
    domainLists: List<DomainList>,
    savedStrategies: List<SavedStrategy>,
    evolutionState: EvolutionUiState?,
    runSummary: String,
    onBack: () -> Unit,
    onShowSaved: () -> Unit,
    onShowSettings: () -> Unit,
    onShowDomainLists: () -> Unit,
    onToggleDomainList: (String) -> Unit,
    onRunToggle: () -> Unit,
    onApply: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
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
                        onClick = onShowSaved,
                        modifier = Modifier.testTag("saved_strategies_btn"),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.saved_strategies_title),
                        )
                    }
                    IconButton(
                        onClick = onShowSettings,
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
                    DomainListsHeader(
                        lists = domainLists,
                        onToggle = onToggleDomainList,
                        onManageClick = onShowDomainLists,
                        enabled = !isRunning,
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
                    if (runSummary.isNotBlank()) {
                        Text(
                            text = runSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    evolutionState?.let { evo ->
                        EvolutionStateCard(
                            state = evo,
                            savedStrategies = savedStrategies,
                            onApply = { cmd ->
                                onApply(cmd)
                                Toast.makeText(context, R.string.strategy_test_applied_toast, Toast.LENGTH_SHORT).show()
                            },
                            onSave = onSave,
                        )
                    }
                }
            }
            if (evolutionState == null) {
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
                        onSave = { onSave(item.command) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainListsHeader(
    lists: List<DomainList>,
    onToggle: (String) -> Unit,
    onManageClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().testTag("domain_lists_header"),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.domain_lists_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onManageClick,
                    modifier = Modifier.testTag("domain_lists_manage_btn"),
                ) {
                    Text(stringResource(R.string.domain_lists_manage))
                }
            }
            lists.forEach { list ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("domain_list_row_${list.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = list.isActive,
                        onCheckedChange = { if (enabled) onToggle(list.id) },
                        enabled = enabled,
                        modifier = Modifier.testTag("domain_list_check_${list.id}"),
                    )
                    Text(
                        text = "${list.name} (${list.domains.size})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
    onSave: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("evolution_state_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.isInitializing) {
                Text(
                    text = stringResource(R.string.evolution_initializing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = stringResource(R.string.evolution_generation_label, state.generation, state.maxGenerations),
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { if (state.maxGenerations > 0) state.generation.toFloat() / state.maxGenerations else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.bestFitness > 0.0) {
                    Text(
                        text = stringResource(R.string.evolution_fitness_label, (state.bestFitness * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.evaluatingCommand != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.evolution_evaluating_label, state.evaluatingIndex, state.evaluatingTotal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    LinearProgressIndicator(
                        progress = { if (state.evaluatingTotal > 0) state.evaluatingIndex.toFloat() / state.evaluatingTotal else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = state.evaluatingCommand,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (state.topChromosomes.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Text(
                    text = stringResource(R.string.evolution_population_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                state.topChromosomes.forEachIndexed { idx, (cmd, fitness) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("evolution_top_$idx"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${(fitness * 100).toInt()}% — $cmd",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onSave(cmd) },
                            modifier = Modifier.testTag("evolution_save_$idx"),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.saved_strategy_save),
                                tint = if (savedStrategies.any { it.command == cmd }) {
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
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.evolution_mode_toggle),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = settings.evolutionMode,
                onCheckedChange = { onSettingsChange(settings.copy(evolutionMode = it)) },
                enabled = enabled,
                modifier = Modifier.testTag("settings_evolution_toggle"),
            )
        }
    }
}

@Composable
private fun SavedStrategiesSheet(
    strategies: List<SavedStrategy>,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.saved_strategies_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (strategies.isEmpty()) {
            Text(
                text = stringResource(R.string.saved_strategies_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        val sorted = strategies.sortedWith(
            compareByDescending<SavedStrategy> { it.isPinned }.thenByDescending { it.addedAt },
        )
        sorted.forEach { saved ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_item_${saved.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    saved.name?.let {
                        Text(text = it, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = saved.command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = { onPin(saved.id, !saved.isPinned) },
                            modifier = Modifier.testTag("saved_pin_${saved.id}"),
                        ) {
                            Icon(
                                Icons.Filled.Star,
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
                        TextButton(
                            onClick = { onApply(saved.command) },
                            modifier = Modifier.testTag("saved_apply_${saved.id}"),
                        ) {
                            Text(stringResource(R.string.saved_strategy_apply))
                        }
                        TextButton(
                            onClick = { onDelete(saved.id) },
                            modifier = Modifier.testTag("saved_delete_${saved.id}"),
                        ) {
                            Text(stringResource(R.string.saved_strategy_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyRow(
    index: Int,
    item: StrategyResult,
    isSaved: Boolean,
    onApply: () -> Unit,
    onSave: () -> Unit,
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
                Row {
                    IconButton(
                        onClick = onSave,
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
