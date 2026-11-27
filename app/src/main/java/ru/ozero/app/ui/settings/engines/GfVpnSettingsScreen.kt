package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginegfvpn.GfVpnBundledSdk
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.GfVpnOptions
import ru.ozero.enginescore.settings.GfVpnServerCatalog
import ru.ozero.enginescore.settings.SplitTunnelMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GfVpnSettingsScreen(
    onBack: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    viewModel: GfVpnSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current.applicationContext
    val available = remember(context) { GfVpnBundledSdk.isAvailable(context) }
    val model by viewModel.settings.collectAsStateWithLifecycle()
    val editable by viewModel.editable.collectAsStateWithLifecycle()
    val global = model.splitMode in setOf(SplitTunnelMode.ALL, SplitTunnelMode.BYPASS_LAN)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(EngineId.GFVPN.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.gfvpn_summary), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (available) R.string.gfvpn_installed else R.string.gfvpn_missing))
            if (!editable) Text(stringResource(R.string.gfvpn_disconnect_to_edit))
            GfVpnToggle(
                label = stringResource(R.string.gfvpn_global),
                checked = global,
                enabled = editable,
                onChange = { viewModel.setGlobal(it, onOpenSplitTunnel) },
            )
            OutlinedButton(onClick = onOpenSplitTunnel, enabled = editable) {
                Text(stringResource(R.string.settings_split_tunnel_title))
            }
            HorizontalDivider()
            GfVpnConnectionOptions(
                options = model.gfVpnOptions,
                enabled = editable,
                update = viewModel::updateOptions,
            )
            Text(stringResource(R.string.gfvpn_trust_warning), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GfVpnConnectionOptions(
    options: GfVpnOptions,
    enabled: Boolean,
    update: ((GfVpnOptions) -> GfVpnOptions) -> Unit,
) {
    val effective = options.effective()
    val auto = stringResource(R.string.gfvpn_auto)
    val serverModes = listOf(
        "auto" to auto,
        "performance" to stringResource(R.string.gfvpn_performance),
        "reliability" to stringResource(R.string.gfvpn_reliability),
        "whitelist-ru" to stringResource(R.string.gfvpn_whitelist),
    )
    val transports = listOf(
        "" to auto, "DNSTT" to "DNSTT", "H2" to "Hysteria 2", "MK" to "Meek",
        "V2H" to "V2Ray HTTP", "V2W" to "V2Ray WebSocket", "VKT" to "VK-TURN",
        "WT" to "WebTunnel", "XRXH" to "XRay XHTTP",
    )
    Text(stringResource(R.string.gfvpn_server_selection), style = MaterialTheme.typography.titleSmall)
    serverModes.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (value, label) ->
                FilterChip(
                    selected = options.serverSelection == value,
                    onClick = { update { it.copy(serverSelection = value) } },
                    label = { Text(label) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    GfVpnServerSelection(options, enabled, update)
    GfVpnChoice(
        stringResource(R.string.gfvpn_transport),
        effective.transport,
        transports,
        enabled && options.serverSelection != "whitelist-ru",
    ) { value -> update { it.copy(transport = value) } }
    val concurrencyEnabled = enabled && !options.managedConcurrency
    GfVpnChoice(
        stringResource(R.string.gfvpn_connections),
        effective.simultaneousConnections.toString(),
        listOf("0" to auto) + (1..4).map { it.toString() to it.toString() },
        concurrencyEnabled,
    ) { value -> update { it.copy(simultaneousConnections = value.toInt()) } }
    GfVpnChoice(
        stringResource(R.string.gfvpn_attempts),
        effective.maxConcurrentAttempts.toString(),
        listOf("0" to auto) + (1..10).map { it.toString() to it.toString() },
        concurrencyEnabled,
    ) { value -> update { it.copy(maxConcurrentAttempts = value.toInt()) } }
    GfVpnChoice(
        stringResource(R.string.gfvpn_rotation),
        effective.rotation,
        listOf(
            "" to auto,
            "true" to stringResource(R.string.gfvpn_enabled),
            "false" to stringResource(R.string.gfvpn_disabled),
        ),
        concurrencyEnabled,
    ) { value -> update { it.copy(rotation = value) } }
    if (options.managedConcurrency) Text(stringResource(R.string.gfvpn_managed_options))
    HorizontalDivider()
    GfVpnToggle(stringResource(R.string.gfvpn_update_config), options.autoUpdate, enabled) { value ->
        update { it.copy(autoUpdate = value) }
    }
    GfVpnToggle(stringResource(R.string.gfvpn_cached_nodes), options.cachedNodes, enabled) { value ->
        update { it.copy(cachedNodes = value) }
    }
    GfVpnToggle(stringResource(R.string.gfvpn_bypass_local), options.bypassLocalServices, enabled) { value ->
        update { it.copy(bypassLocalServices = value) }
    }
    Text(stringResource(R.string.gfvpn_bypass_local_summary), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GfVpnToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun GfVpnChoice(
    label: String,
    selected: String,
    choices: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(choices.firstOrNull { it.first == selected }?.second.orEmpty())
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                choices.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GfVpnServerSelection(
    options: GfVpnOptions,
    enabled: Boolean,
    update: ((GfVpnOptions) -> GfVpnOptions) -> Unit,
) {
    val country = GfVpnServerCatalog.countryFor(options.serverSelection)
    val countries = GfVpnServerCatalog.countries.map { (code, groups) ->
        val iso = code.take(2)
        val flag = iso.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
        val name = Locale("", iso).displayCountry
        val title = if (code.endsWith("0")) stringResource(R.string.gfvpn_proxy_country, name) else name
        code to stringResource(R.string.gfvpn_country_entry, flag, title, groups.size)
    }.sortedBy { it.second.substringAfter(' ') }
    val automatic = stringResource(R.string.gfvpn_no_country)
    GfVpnChoice(
        stringResource(R.string.gfvpn_country),
        country.orEmpty(),
        listOf("" to automatic) + countries,
        enabled,
    ) { value -> update { it.copy(serverSelection = value.ifEmpty { "auto" }) } }
    if (country != null) {
        val servers = GfVpnServerCatalog.countries.getValue(country)
        GfVpnChoice(
            stringResource(R.string.gfvpn_server),
            options.serverSelection,
            listOf(country to stringResource(R.string.gfvpn_any_server)) + servers.map { it to it },
            enabled,
        ) { value -> update { it.copy(serverSelection = value) } }
    }
}
