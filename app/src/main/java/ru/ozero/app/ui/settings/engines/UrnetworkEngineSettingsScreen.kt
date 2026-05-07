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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
        when (state) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InfoBlock()
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.urnetwork_location_connect_first),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            is UrnetworkSettingsUiState.Ready -> {
                val ready = state as UrnetworkSettingsUiState.Ready
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val peerCount by viewModel.peerCount.collectAsStateWithLifecycle()
                LocationListContent(
                    modifier = Modifier.padding(padding),
                    countries = ready.countries,
                    selectedLocation = ready.selectedLocation,
                    providePaused = ready.providePaused,
                    peerCount = peerCount,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSelect = viewModel::selectLocation,
                    onSetProvidePaused = viewModel::setProvidePaused,
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (ConnectLocation?) -> Unit,
    onSetProvidePaused: (Boolean) -> Unit,
) {
    val isBestAvailable = selectedLocation == null || selectedLocation.connectLocationId?.bestAvailable == true
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                PeerCounterRow(peerCount = peerCount)
                InfoBlock()
                AboutBlock()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.urnetwork_provide_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.urnetwork_provide_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = !providePaused,
                        onCheckedChange = { checked -> onSetProvidePaused(!checked) },
                    )
                }
                Text(
                    text = stringResource(R.string.urnetwork_location_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
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
                HorizontalDivider()
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
            HorizontalDivider()
        }
    }
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

@Composable
private fun PeerCounterRow(peerCount: Int) {
    val text = if (peerCount > 0) {
        stringResource(R.string.urnetwork_peers_connected, peerCount)
    } else {
        stringResource(R.string.urnetwork_peers_searching)
    }
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun AboutBlock() {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.urnetwork_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.urnetwork_about_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun InfoBlock() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.urnetwork_consent_warning_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.urnetwork_consent_warning_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
