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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkEngineSettingsScreen(
    onBack: () -> Unit,
    onOpenSharedTraffic: () -> Unit,
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
                        windowType = windowType,
                        fixedIp = fixedIp,
                        provideControlMode = provideControlMode,
                        provideNetworkMode = provideNetworkMode,
                        sharedTrafficBytes = 0L,
                        showProvide = false,
                        onSetProvidePaused = {},
                        onSelectWindowType = viewModel::selectWindowType,
                        onToggleFixedIp = viewModel::toggleFixedIpSize,
                        onSelectProvideControlMode = viewModel::selectProvideControlMode,
                        onSelectProvideNetworkMode = viewModel::selectProvideNetworkMode,
                        onOpenSharedTraffic = onOpenSharedTraffic,
                    )
                }
            }
            is UrnetworkSettingsUiState.Ready -> {
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val peerCount by viewModel.peerCount.collectAsStateWithLifecycle()
                val switchingCountry by viewModel.switchingCountry.collectAsStateWithLifecycle()
                val windowType by viewModel.windowType.collectAsStateWithLifecycle()
                val fixedIp by viewModel.fixedIpSize.collectAsStateWithLifecycle()
                val provideControlMode by viewModel.provideControlMode.collectAsStateWithLifecycle()
                val provideNetworkMode by viewModel.provideNetworkMode.collectAsStateWithLifecycle()
                val sharedTrafficBytes by viewModel.sharedTrafficBytes.collectAsStateWithLifecycle()
                LocationListContent(
                    modifier = Modifier.padding(padding),
                    countries = current.countries,
                    regions = current.regions,
                    cities = current.cities,
                    selectedLocation = current.selectedLocation,
                    providePaused = current.providePaused,
                    peerCount = peerCount,
                    switchingCountry = switchingCountry,
                    searchQuery = searchQuery,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    sharedTrafficBytes = sharedTrafficBytes,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSelect = viewModel::selectLocation,
                    onSetProvidePaused = viewModel::setProvidePaused,
                    onSelectWindowType = viewModel::selectWindowType,
                    onToggleFixedIp = viewModel::toggleFixedIpSize,
                    onSelectProvideControlMode = viewModel::selectProvideControlMode,
                    onSelectProvideNetworkMode = viewModel::selectProvideNetworkMode,
                    onOpenSharedTraffic = onOpenSharedTraffic,
                )
            }
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun LocationListContent(
    modifier: Modifier,
    countries: List<UrnetworkLocationItem>,
    regions: List<UrnetworkLocationItem>,
    cities: List<UrnetworkLocationItem>,
    selectedLocation: UrnetworkSdkBridge.LocationToken?,
    providePaused: Boolean,
    peerCount: Int,
    switchingCountry: Boolean,
    searchQuery: String,
    windowType: UrnetworkWindowType,
    fixedIp: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    sharedTrafficBytes: Long,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (UrnetworkSdkBridge.LocationToken?) -> Unit,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
    onOpenSharedTraffic: () -> Unit,
) {
    val isBestAvailable = selectedLocation == null
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                StatusRow(peerCount = peerCount, switchingCountry = switchingCountry)
                SettingsCard(
                    providePaused = providePaused,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    sharedTrafficBytes = sharedTrafficBytes,
                    showProvide = true,
                    onSetProvidePaused = onSetProvidePaused,
                    onSelectWindowType = onSelectWindowType,
                    onToggleFixedIp = onToggleFixedIp,
                    onSelectProvideControlMode = onSelectProvideControlMode,
                    onSelectProvideNetworkMode = onSelectProvideNetworkMode,
                    onOpenSharedTraffic = onOpenSharedTraffic,
                )
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
        if (countries.isNotEmpty()) {
            items(countries, key = { "c/${it.countryCode.ifEmpty { it.name }}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.region == null &&
                    selectedLocation?.city == null
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
        if (regions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.urnetwork_location_regions),
                    style = MaterialTheme.typography.titleSmall,
                    color = OzeroPalette.Text2,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(regions, key = { "r/${it.countryCode}/${it.name}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.region == item.location.region &&
                    selectedLocation?.city == null
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
        if (cities.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.urnetwork_location_cities),
                    style = MaterialTheme.typography.titleSmall,
                    color = OzeroPalette.Text2,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(cities, key = { "ct/${it.countryCode}/${it.name}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.city == item.location.city
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
}

@Suppress("LongParameterList")
@Composable
private fun SettingsCard(
    providePaused: Boolean,
    windowType: UrnetworkWindowType,
    fixedIp: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    sharedTrafficBytes: Long,
    showProvide: Boolean,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
    onOpenSharedTraffic: () -> Unit,
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
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    onSetProvidePaused = onSetProvidePaused,
                    onSelectProvideControlMode = onSelectProvideControlMode,
                    onSelectProvideNetworkMode = onSelectProvideNetworkMode,
                )
                SharedTrafficSection(
                    sharedTrafficBytes = sharedTrafficBytes,
                    onClick = onOpenSharedTraffic,
                )
                SectionDivider()
            }
            ModeSection(
                selected = windowType,
                fixedIp = fixedIp,
                onSelect = onSelectWindowType,
                onToggleFixedIp = onToggleFixedIp,
            )
            SectionDivider()
            CheckIpRow()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvideSection(
    providePaused: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
) {
    val modes = listOf(
        R.string.urnetwork_provide_mode_never,
        R.string.urnetwork_provide_control_mode_auto,
        R.string.urnetwork_provide_control_mode_always,
    )
    val selectedIndex = when {
        providePaused -> 0
        provideControlMode == UrnetworkProvideControlMode.AUTO -> 1
        else -> 2
    }
    SectionLabel(stringResource(R.string.urnetwork_provide_title))
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        modes.forEachIndexed { index, labelRes ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = {
                    when (index) {
                        0 -> onSetProvidePaused(true)
                        1 -> {
                            onSetProvidePaused(false)
                            onSelectProvideControlMode(UrnetworkProvideControlMode.AUTO)
                        }
                        else -> {
                            onSetProvidePaused(false)
                            onSelectProvideControlMode(UrnetworkProvideControlMode.ALWAYS)
                        }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    Text(
        text = stringResource(R.string.urnetwork_provide_mode_desc),
        style = MaterialTheme.typography.bodySmall,
        color = OzeroPalette.Text3,
        modifier = Modifier.padding(top = 6.dp),
    )
    SectionDivider()
    ProvideNetworkModeSection(
        selected = provideNetworkMode,
        onSelect = onSelectProvideNetworkMode,
    )
    SectionDivider()
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
private fun StatusRow(peerCount: Int, switchingCountry: Boolean = false) {
    val (dot, label, dotColor) = when {
        switchingCountry -> Triple(
            "●",
            stringResource(R.string.urnetwork_country_switching),
            OzeroPalette.StateConnecting,
        )
        peerCount > 0 -> Triple(
            "●",
            stringResource(R.string.urnetwork_peers_connected, peerCount),
            OzeroPalette.StateConnected,
        )
        else -> Triple(
            "●",
            stringResource(R.string.urnetwork_peers_searching),
            OzeroPalette.StateConnecting,
        )
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
private fun CheckIpRow() {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://ur.io/ip") }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.urnetwork_check_ip),
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text,
        )
        Text(text = "›", style = MaterialTheme.typography.bodyLarge, color = OzeroPalette.Text3)
    }
}

@Composable
private fun SharedTrafficSection(sharedTrafficBytes: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.urnetwork_shared_traffic_title),
                style = MaterialTheme.typography.bodyMedium,
                color = OzeroPalette.Text,
            )
            Text(
                text = formatBytes(sharedTrafficBytes),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text2,
            )
        }
        Text(text = "›", style = MaterialTheme.typography.bodyLarge, color = OzeroPalette.Text3)
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
