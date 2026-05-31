package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import ru.ozero.app.ui.urnetwork.UrnetworkBalanceCard
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkEngineSettingsScreen(
    onBack: () -> Unit,
    onOpenSharedTraffic: () -> Unit,
    settingsVm: UrnetworkEngineSettingsViewModel = hiltViewModel(),
    locationsVm: UrnetworkLocationsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by locationsVm.uiState.collectAsStateWithLifecycle()
    val insufficientBalance by settingsVm.insufficientBalance.collectAsStateWithLifecycle()
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
                val windowType by settingsVm.windowType.collectAsStateWithLifecycle()
                val fixedIp by settingsVm.fixedIpSize.collectAsStateWithLifecycle()
                val allowDirect by settingsVm.allowDirect.collectAsStateWithLifecycle()
                val provideControlMode by settingsVm.provideControlMode.collectAsStateWithLifecycle()
                val provideNetworkMode by settingsVm.provideNetworkMode.collectAsStateWithLifecycle()
                val providePaused by settingsVm.providePaused.collectAsStateWithLifecycle()
                val balanceState by settingsVm.balanceState.collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (insufficientBalance) {
                        InsufficientBalanceBanner()
                    }
                    UrnetworkBalanceCard(state = balanceState)
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
                        providePaused = providePaused,
                        windowType = windowType,
                        fixedIp = fixedIp,
                        allowDirect = allowDirect,
                        provideControlMode = provideControlMode,
                        provideNetworkMode = provideNetworkMode,
                        sharedTrafficBytes = 0L,
                        onSetProvidePaused = locationsVm::setProvidePaused,
                        onSelectWindowType = settingsVm::selectWindowType,
                        onToggleFixedIp = settingsVm::toggleFixedIpSize,
                        onToggleAllowDirect = settingsVm::toggleAllowDirect,
                        onSelectProvideControlMode = settingsVm::selectProvideControlMode,
                        onSelectProvideNetworkMode = settingsVm::selectProvideNetworkMode,
                        onOpenSharedTraffic = onOpenSharedTraffic,
                    )
                }
            }
            is UrnetworkSettingsUiState.Ready -> {
                val searchQuery by locationsVm.searchQuery.collectAsStateWithLifecycle()
                val windowType by settingsVm.windowType.collectAsStateWithLifecycle()
                val fixedIp by settingsVm.fixedIpSize.collectAsStateWithLifecycle()
                val allowDirect by settingsVm.allowDirect.collectAsStateWithLifecycle()
                val provideControlMode by settingsVm.provideControlMode.collectAsStateWithLifecycle()
                val provideNetworkMode by settingsVm.provideNetworkMode.collectAsStateWithLifecycle()
                val sharedTrafficBytes by settingsVm.sharedTrafficBytes.collectAsStateWithLifecycle()
                val balanceState by settingsVm.balanceState.collectAsStateWithLifecycle()
                LocationListContent(
                    modifier = Modifier.padding(padding),
                    countries = current.countries,
                    regions = current.regions,
                    cities = current.cities,
                    bestMatches = current.bestMatches,
                    selectedLocation = current.selectedLocation,
                    providePaused = current.providePaused,
                    searchQuery = searchQuery,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    allowDirect = allowDirect,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    sharedTrafficBytes = sharedTrafficBytes,
                    balanceState = balanceState,
                    insufficientBalance = insufficientBalance,
                    onSearchQueryChange = locationsVm::setSearchQuery,
                    onSelect = locationsVm::selectLocation,
                    onSetProvidePaused = locationsVm::setProvidePaused,
                    onSelectWindowType = settingsVm::selectWindowType,
                    onToggleFixedIp = settingsVm::toggleFixedIpSize,
                    onToggleAllowDirect = settingsVm::toggleAllowDirect,
                    onSelectProvideControlMode = settingsVm::selectProvideControlMode,
                    onSelectProvideNetworkMode = settingsVm::selectProvideNetworkMode,
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
    bestMatches: List<UrnetworkLocationItem>,
    selectedLocation: UrnetworkSdkBridge.LocationToken?,
    providePaused: Boolean,
    searchQuery: String,
    windowType: UrnetworkWindowType,
    fixedIp: Boolean,
    allowDirect: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    sharedTrafficBytes: Long,
    balanceState: UrnetworkBalanceState,
    insufficientBalance: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (UrnetworkSdkBridge.LocationToken?) -> Unit,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onToggleAllowDirect: (Boolean) -> Unit,
    onSelectProvideControlMode: (UrnetworkProvideControlMode) -> Unit,
    onSelectProvideNetworkMode: (UrnetworkProvideNetworkMode) -> Unit,
    onOpenSharedTraffic: () -> Unit,
) {
    val isBestAvailable = selectedLocation == null || selectedLocation.bestAvailable
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (insufficientBalance) {
            item {
                InsufficientBalanceBanner(modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            UrnetworkBalanceCard(
                state = balanceState,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                SettingsCard(
                    providePaused = providePaused,
                    windowType = windowType,
                    fixedIp = fixedIp,
                    allowDirect = allowDirect,
                    provideControlMode = provideControlMode,
                    provideNetworkMode = provideNetworkMode,
                    sharedTrafficBytes = sharedTrafficBytes,
                    onSetProvidePaused = onSetProvidePaused,
                    onSelectWindowType = onSelectWindowType,
                    onToggleFixedIp = onToggleFixedIp,
                    onToggleAllowDirect = onToggleAllowDirect,
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
        if (bestMatches.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.urnetwork_location_best_matches),
                    style = MaterialTheme.typography.titleSmall,
                    color = OzeroPalette.Text2,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(bestMatches, key = { "bm/${System.identityHashCode(it.location)}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.region == item.location.region &&
                    selectedLocation?.city == item.location.city
                LocationRow(
                    name = item.name,
                    flag = item.flag,
                    providerCount = item.providerCount,
                    selected = selected,
                    isStable = item.isStable,
                    isStrongPrivacy = item.isStrongPrivacy,
                    onClick = { onSelect(item.location) },
                )
                HorizontalDivider(color = OzeroPalette.Line)
            }
        }
        if (countries.isNotEmpty()) {
            items(countries, key = { "c/${System.identityHashCode(it.location)}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.region == null &&
                    selectedLocation?.city == null
                LocationRow(
                    name = item.name,
                    flag = item.flag,
                    providerCount = item.providerCount,
                    selected = selected,
                    isStable = item.isStable,
                    isStrongPrivacy = item.isStrongPrivacy,
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
            items(regions, key = { "r/${System.identityHashCode(it.location)}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.region == item.location.region &&
                    selectedLocation?.city == null
                LocationRow(
                    name = item.name,
                    flag = item.flag,
                    providerCount = item.providerCount,
                    selected = selected,
                    isStable = item.isStable,
                    isStrongPrivacy = item.isStrongPrivacy,
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
            items(cities, key = { "ct/${System.identityHashCode(it.location)}" }) { item ->
                val selected = !isBestAvailable &&
                    selectedLocation?.countryCode == item.location.countryCode &&
                    selectedLocation?.city == item.location.city
                LocationRow(
                    name = item.name,
                    flag = item.flag,
                    providerCount = item.providerCount,
                    selected = selected,
                    isStable = item.isStable,
                    isStrongPrivacy = item.isStrongPrivacy,
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
    allowDirect: Boolean,
    provideControlMode: UrnetworkProvideControlMode,
    provideNetworkMode: UrnetworkProvideNetworkMode,
    sharedTrafficBytes: Long,
    onSetProvidePaused: (Boolean) -> Unit,
    onSelectWindowType: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onToggleAllowDirect: (Boolean) -> Unit,
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
            ModeSection(
                selected = windowType,
                fixedIp = fixedIp,
                allowDirect = allowDirect,
                onSelect = onSelectWindowType,
                onToggleFixedIp = onToggleFixedIp,
                onToggleAllowDirect = onToggleAllowDirect,
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
    allowDirect: Boolean,
    onSelect: (UrnetworkWindowType) -> Unit,
    onToggleFixedIp: (Boolean) -> Unit,
    onToggleAllowDirect: (Boolean) -> Unit,
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
    CompactToggleRow(
        label = stringResource(R.string.urnetwork_fixed_ip_size),
        checked = fixedIp,
        onCheckedChange = onToggleFixedIp,
        testTag = "urnetwork_toggle_fixed_ip",
    )
    CompactToggleRow(
        label = stringResource(R.string.urnetwork_enhanced_anonymization),
        checked = !allowDirect,
        onCheckedChange = { onToggleAllowDirect(!it) },
        testTag = "urnetwork_toggle_enhanced_anonymization",
    )
}

@Composable
private fun CompactToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
private fun InsufficientBalanceBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("urnetwork_insufficient_balance_banner"),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
        border = BorderStroke(1.dp, OzeroPalette.StateDanger),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.urnetwork_insufficient_balance_title),
                style = MaterialTheme.typography.titleSmall,
                color = OzeroPalette.StateDanger,
            )
            Text(
                text = stringResource(R.string.urnetwork_insufficient_balance_body),
                style = MaterialTheme.typography.bodyMedium,
                color = OzeroPalette.Text2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
    isStable: Boolean = true,
    isStrongPrivacy: Boolean = false,
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
        if (!isStable) {
            Text(
                text = "☁️",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (isStrongPrivacy) {
            Text(
                text = "🕶️",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (providerCount > 0) {
            Text(
                text = "$providerCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
