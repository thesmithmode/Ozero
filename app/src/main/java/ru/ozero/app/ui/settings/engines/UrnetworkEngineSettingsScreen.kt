package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bringyour.sdk.ConnectLocation
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: UrnetworkEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("urnetwork_settings"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.urnetwork_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        when (current) {
            UrnetworkSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            UrnetworkSettingsUiState.NotConnected -> {
                val windowType by viewModel.windowType.collectAsStateWithLifecycle()
                val fixedIp by viewModel.fixedIpSize.collectAsStateWithLifecycle()
                val provideControlMode by viewModel.provideControlMode.collectAsStateWithLifecycle()
                val provideNetworkMode by viewModel.provideNetworkMode.collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
                    ) {
                        Text(
                            text = stringResource(R.string.urnetwork_location_connect_first),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OzeroPalette.Text2,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    SettingsCard(
                        providePaused = true,
                        unpaidBytes = 0L,
                        windowType = windowType,
                        fixedIp = fixedIp,
                        provideControlMode = provideControlMode,
                        provideNetworkMode = provideNetworkMode,
                        showProvide = false,
                        onSetProvidePaused = {},
                        onSelectWindowType = viewModel::selectWindowType,
                        onToggleFixedIp = viewModel::toggleFixedIpSize,
                        onSelectProvideControlMode = viewModel::selectProvideControlMode,
                        onSelectProvideNetworkMode = viewModel::selectProvideNetworkMode,
                    )
                }
            }
            is UrnetworkSettingsUiState.Ready -> {
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val peerCount by viewModel.peerCount.collectAsStateWithLifecycle()
                val unpaidBytes by viewModel.unpaidBytes.collectAsStateWithLifecycle()
                val balance by viewModel.subscriptionBalance.collectAsStateWithLifecycle()
                val windowType by viewModel.windowType.collectAsStateWithLifecycle()
                val fixedIp by viewModel.fixedIpSize.collectAsStateWithLifecycle()
                val provideControlMode by viewModel.provideControlMode.collectAsStateWithLifecycle()
                val provideNetworkMode by viewModel.provideNetworkMode.collectAsStateWithLifecycle()
                LocationListContent(
                    modifier = Modifier.padding(padding),
                    countries = current.countries,
                    selectedLocation = current.selectedLocation,
                    providePaused = current.providePaused,
                    peerCount = peerCount,
                    unpaidBytes = unpaidBytes,
                    balance = balance,
                    searchQuery = searchQuery,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSelect = viewModel::selectLocation,
                    onSetProvidePaused = viewModel::setProvidePaused,
                    onSelectWindowType = viewModel::selectWindowType,
                    onToggleFixedIp = viewModel::toggleFixedIpSize,
                    onSelectProvideControlMode = viewModel::selectProvideControlMode,
                    onSelectProvideNetworkMode = viewModel::selectProvideNetworkMode,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun LocationListContent(
    modifier: Modifier,
    countries: List<UrnetworkLocationItem>,
    selectedLocation: ConnectLocation?,
    providePaused: Boolean,
    peerCount: Int,
    unpaidBytes: Long,
    balance: ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot?,
    searchQuery: String,
    windowType: UrnetworkWindowType,
    fixedIp: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (ConnectLocation?) -> Unit,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
) {
    val isBestAvailable = selectedLocation == null || selectedLocation.connectLocationId?.bestAvailable == true
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                StatusRow(peerCount = peerCount)
                SettingsCard(
                    providePaused = providePaused,
                    unpaidBytes = unpaidBytes,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    showProvide = true,
                    onSetProvidePaused = onSetProvidePaused,
                    onSelectWindowType = onSelectWindowType,
                    onToggleFixedIp = onToggleFixedIp,
                    onSelectProvideControlMode = onSelectProvideControlMode,
                    onSelectProvideNetworkMode = onSelectProvideNetworkMode,
                )
                ConsumerProgressCard(balance = balance)
                ConsentRow()
                Text(
                    text = stringResource(R.string.urnetwork_location_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = OzeroPalette.Text2,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.urnetwork_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                )
            }
        }
        if (searchQuery.isEmpty()) {
            item {
                LocationRow(
                    name = stringResource(R.string.urnetwork_location_best_available),
                    flag = "",
                    providerCount = 0,
                    selected = isBestAvailable,
                    onClick = { onSelect(null) },
                )
                HorizontalDivider(color = OzeroPalette.Line)
            }
        }
        items(countries, key = { it.countryCode.ifEmpty { it.name } }) { item ->
            val selected = !isBestAvailable &&
                selectedLocation?.countryCode == item.countryCode &&
                selectedLocation.country == item.location.country
            LocationRow(
                name = item.name,
                flag = item.flag,
                providerCount = item.providerCount,
                selected = selected,
                onClick = { onSelect(item.location) },
            )
            HorizontalDivider(color = OzeroPalette.Line)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SettingsCard(
    providePaused: Boolean,
    unpaidBytes: Long,
    windowType: UrnetworkWindowType,
    fixedIp: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    showProvide: Boolean,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showProvide) {
                ProvideSection(
                    providePaused = providePaused,
                    unpaidBytes = unpaidBytes,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    onSetProvidePaused = onSetProvidePaused,
                    onSelectProvideControlMode = onSelectProvideControlMode,
                    onSelectProvideNetworkMode = onSelectProvideNetworkMode,
                )
            }
            ModeSection(
                selected = windowType,
                fixedIp = fixedIp,
                onSelect = onSelectWindowType,
                onToggleFixedIp = onToggleFixedIp,
            )
        }
    }
}

@Composable
private fun ProvideSection(
    providePaused: Boolean,
    unpaidBytes: Long,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
) {
    ProvideToggleSection(
        providePaused = providePaused,
        unpaidBytes = unpaidBytes,
        onSetProvidePaused = onSetProvidePaused,
    )
    SectionDivider()
    ProvideControlModeSection(
        selected = provideControlMode,
        onSelect = onSelectProvideControlMode,
    )
    SectionDivider()
    ProvideNetworkModeSection(
        selected = provideNetworkMode,
        onSelect = onSelectProvideNetworkMode,
    )
    SectionDivider()
}

@Composable
private fun ProvideToggleSection(
    providePaused: Boolean,
    unpaidBytes: Long,
    onSetProvidePaused: (Boolean) -> Unit,
) {
    SectionLabel(stringResource(R.string.urnetwork_provide_title))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.urnetwork_provide_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text2,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
            checked = !providePaused,
            onCheckedChange = { checked -> onSetProvidePaused(!checked) },
        )
    }
    if (!providePaused) {
        val mb = unpaidBytes / 1_000_000.0
        Text(
            text = stringResource(R.string.urnetwork_provider_unpaid, mb),
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Teal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = OzeroPalette.Line,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvideControlModeSection(
    selected: UrnetworkProvideControlMode,
    onSelect: (UrnetworkProvideControlMode) -> Unit,
) {
    val modes = listOf(
        UrnetworkProvideControlMode.AUTO to R.string.urnetwork_provide_control_mode_auto,
        UrnetworkProvideControlMode.ALWAYS to R.string.urnetwork_provide_control_mode_always,
    )
    SectionLabel(stringResource(R.string.urnetwork_provide_control_mode_title))
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        modes.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    Text(
        text = stringResource(R.string.urnetwork_provide_control_mode_desc),
        style = MaterialTheme.typography.bodySmall,
        color = OzeroPalette.Text3,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvideNetworkModeSection(
    selected: UrnetworkProvideNetworkMode,
    onSelect: (UrnetworkProvideNetworkMode) -> Unit,
) {
    val modes = listOf(
        UrnetworkProvideNetworkMode.WIFI to R.string.urnetwork_provide_network_mode_wifi,
        UrnetworkProvideNetworkMode.ALL to R.string.urnetwork_provide_network_mode_all,
    )
    SectionLabel(stringResource(R.string.urnetwork_provide_network_mode_title))
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        modes.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    Text(
        text = stringResource(R.string.urnetwork_provide_network_mode_desc),
        style = MaterialTheme.typography.bodySmall,
        color = OzeroPalette.Text3,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSection(
    selected: UrnetworkWindowType,
    fixedIp: Boolean,
    onSelect: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
) {
    val modes = listOf(
        UrnetworkWindowType.AUTO to R.string.urnetwork_mode_auto,
        UrnetworkWindowType.QUALITY to R.string.urnetwork_mode_quality,
        UrnetworkWindowType.SPEED to R.string.urnetwork_mode_speed,
    )
    SectionLabel(stringResource(R.string.urnetwork_mode_title))
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        modes.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    Text(
        text = stringResource(R.string.urnetwork_mode_hint),
        style = MaterialTheme.typography.bodySmall,
        color = OzeroPalette.Text3,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (selected != UrnetworkWindowType.AUTO) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.urnetwork_fixed_ip_size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OzeroPalette.Text,
                )
                Text(
                    text = stringResource(R.string.urnetwork_fixed_ip_size_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
            }
            Switch(checked = fixedIp, onCheckedChange = onToggleFixedIp)
        }
    }
}

@Composable
private fun StatusRow(peerCount: Int) {
    val (dot, label, dotColor) = if (peerCount > 0) {
        Triple("●", stringResource(R.string.urnetwork_peers_connected, peerCount), OzeroPalette.StateConnected)
    } else {
        Triple("●", stringResource(R.string.urnetwork_peers_searching), OzeroPalette.StateConnecting)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = dot, color = dotColor, style = MaterialTheme.typography.bodySmall)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Text2,
        )
    }
}

@Composable
private fun ConsumerProgressCard(
    balance: ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot?,
) {
    if (balance == null || balance.startBalanceBytes <= 0L) return
    val usedBytes = balance.usedBytes.coerceAtLeast(0L)
    val totalBytes = balance.startBalanceBytes
    val usedMb = usedBytes / 1_000_000.0
    val totalMb = totalBytes / 1_000_000.0
    val pendingMb = balance.pendingBytes / 1_000_000.0
    val progress = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel(stringResource(R.string.urnetwork_consumed_label))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.2f MB", usedMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OzeroPalette.Text,
                )
                Text(
                    text = String.format(java.util.Locale.US, "/ %.2f MB", totalMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OzeroPalette.Text3,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = OzeroPalette.Teal,
                trackColor = OzeroPalette.Bg2,
            )
            if (balance.pendingBytes > 0L) {
                Text(
                    text = stringResource(
                        R.string.urnetwork_pending_label,
                        String.format(java.util.Locale.US, "%.2f", pendingMb),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsentRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = "⚠", style = MaterialTheme.typography.bodySmall, color = OzeroPalette.Amber)
        Text(
            text = stringResource(R.string.urnetwork_consent_short),
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Text3,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = OzeroPalette.Text3,
    )
}

@Composable
private fun LocationRow(
    name: String,
    flag: String,
    providerCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        if (flag.isNotEmpty()) {
            Text(
                text = flag,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (providerCount > 0) {
            Text(
                text = "$providerCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
