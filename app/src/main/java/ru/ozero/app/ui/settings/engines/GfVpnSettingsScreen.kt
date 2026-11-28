package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginegfvpn.GfVpnBundledSdk
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.GfVpnOptions
import ru.ozero.enginescore.settings.GfVpnServerCatalog
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
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAdvanced) { showAdvanced = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showAdvanced) stringResource(R.string.gfvpn_advanced_title)
                        else EngineId.GFVPN.displayName,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showAdvanced) showAdvanced = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    if (!showAdvanced) {
                        IconButton(onClick = { showAdvanced = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.gfvpn_advanced_title),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showAdvanced) {
                if (!editable) Text(stringResource(R.string.gfvpn_disconnect_to_edit))
                GfVpnAdvancedOptions(
                    options = model.gfVpnOptions,
                    enabled = editable,
                    update = viewModel::updateOptions,
                )
                Text(stringResource(R.string.gfvpn_trust_warning), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.gfvpn_summary), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (available) R.string.gfvpn_installed else R.string.gfvpn_missing))
                if (!editable) Text(stringResource(R.string.gfvpn_disconnect_to_edit))
                OutlinedButton(onClick = onOpenSplitTunnel, enabled = editable) {
                    Text(stringResource(R.string.settings_split_tunnel_title))
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.gfvpn_server_selection),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GfVpnServerModes(
                    options = model.gfVpnOptions,
                    enabled = editable,
                    update = viewModel::updateOptions,
                )
                Text(
                    stringResource(R.string.gfvpn_mode_country_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.gfvpn_country),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GfVpnServerSelectionList(
                    options = model.gfVpnOptions,
                    enabled = editable,
                    update = viewModel::updateOptions,
                )
            }
        }
    }
}

@Composable
private fun GfVpnAdvancedOptions(
    options: GfVpnOptions,
    enabled: Boolean,
    update: ((GfVpnOptions) -> GfVpnOptions) -> Unit,
) {
    val effective = options.effective()
    val auto = stringResource(R.string.gfvpn_auto)
    val transports = listOf(
        "" to auto, "DNSTT" to "DNSTT", "H2" to "Hysteria 2", "MK" to "Meek",
        "V2H" to "V2Ray HTTP", "V2W" to "V2Ray WebSocket", "VKT" to "VK-TURN",
        "WT" to "WebTunnel", "XRXH" to "XRay XHTTP",
    )
    GfVpnChoice(
        stringResource(R.string.gfvpn_transport),
        effective.transport,
        transports,
        enabled && options.serverSelection != "whitelist-ru",
    ) { value -> update { it.copy(transport = value) } }
    GfVpnChoice(
        stringResource(R.string.gfvpn_connections),
        effective.simultaneousConnections.toString(),
        listOf("0" to auto) + (1..4).map { it.toString() to it.toString() },
        enabled && !options.managedConcurrency,
    ) { value -> update { it.copy(simultaneousConnections = value.toInt()) } }
    GfVpnChoice(
        stringResource(R.string.gfvpn_attempts),
        effective.maxConcurrentAttempts.toString(),
        listOf("0" to auto) + (1..10).map { it.toString() to it.toString() },
        enabled && !options.managedConcurrency,
    ) { value -> update { it.copy(maxConcurrentAttempts = value.toInt()) } }
    GfVpnChoice(
        stringResource(R.string.gfvpn_rotation),
        effective.rotation,
        listOf(
            "" to auto,
            "true" to stringResource(R.string.gfvpn_enabled),
            "false" to stringResource(R.string.gfvpn_disabled),
        ),
        enabled && !options.managedConcurrency,
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
private fun GfVpnServerModes(
    options: GfVpnOptions,
    enabled: Boolean,
    update: ((GfVpnOptions) -> GfVpnOptions) -> Unit,
) {
    val serverModes = listOf(
        "auto" to stringResource(R.string.gfvpn_auto),
        "performance" to stringResource(R.string.gfvpn_performance),
        "reliability" to stringResource(R.string.gfvpn_reliability),
        "whitelist-ru" to stringResource(R.string.gfvpn_whitelist),
    )
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        serverModes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    val selected = options.serverSelection == value
                    val container = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val outline = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(shape)
                            .background(container)
                            .border(1.dp, outline, shape)
                            .clickable(enabled = enabled) { update { it.copy(serverSelection = value) } }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private data class GfVpnCountryItem(
    val code: String,
    val iso: String,
    val flag: String,
    val name: String,
    val groups: List<String>,
)

private const val REGIONAL_INDICATOR_A = 0x1F1E6

@Composable
private fun GfVpnServerSelectionList(
    options: GfVpnOptions,
    enabled: Boolean,
    update: ((GfVpnOptions) -> GfVpnOptions) -> Unit,
) {
    val countries = remember {
        GfVpnServerCatalog.countries.map { (code, groups) ->
            val iso = code.take(2)
            val flag = iso.map { String(Character.toChars(REGIONAL_INDICATOR_A + it.code - 'A'.code)) }
                .joinToString("")
            GfVpnCountryItem(
                code = code,
                iso = iso,
                flag = flag,
                name = Locale("", iso).displayCountry,
                groups = groups,
            )
        }.sortedWith(
            compareByDescending<GfVpnCountryItem> { it.iso == "RU" }
                .thenByDescending { it.groups.size }
                .thenBy { it.name },
        )
    }
    val selectedCountry = GfVpnServerCatalog.countryFor(options.serverSelection)
    countries.forEach { item ->
        val selected = item.code == selectedCountry
        val title = if (item.code.endsWith("0")) {
            stringResource(R.string.gfvpn_proxy_country, item.name)
        } else {
            item.name
        }
        val label = stringResource(R.string.gfvpn_country_entry, item.flag, title, item.groups.size)
        GfVpnCountryRow(
            label = label,
            selected = selected,
            enabled = enabled,
            onClick = { update { it.copy(serverSelection = item.code) } },
        )
        if (selected) {
            GfVpnChoice(
                label = stringResource(R.string.gfvpn_server),
                selected = options.serverSelection,
                choices = listOf(item.code to stringResource(R.string.gfvpn_any_server)) +
                    item.groups.map { it to it },
                enabled = enabled,
                onSelect = { value -> update { it.copy(serverSelection = value) } },
            )
        }
    }
}

@Composable
private fun GfVpnCountryRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
