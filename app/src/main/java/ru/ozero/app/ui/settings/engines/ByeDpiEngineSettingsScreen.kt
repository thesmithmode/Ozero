package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

private val DNS_PRESETS: List<Pair<String, List<String>>> = listOf(
    "Google" to listOf("8.8.8.8", "8.8.4.4"),
    "Cloudflare" to listOf("1.1.1.1", "1.0.0.1"),
    "Quad9" to listOf("9.9.9.9", "149.112.112.112"),
)

@Composable
private fun DnsSection(
    dnsText: String,
    onPreset: (List<String>) -> Unit,
    onDnsChange: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.byedpi_dns_label),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.byedpi_dns_description),
        style = MaterialTheme.typography.bodySmall,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = dnsText.isBlank(),
                onClick = { onPreset(emptyList()) },
                label = { Text(stringResource(R.string.byedpi_dns_auto)) },
                modifier = Modifier.testTag("byedpi_dns_preset_auto"),
            )
        }
        items(DNS_PRESETS) { (label, servers) ->
            FilterChip(
                selected = dnsText.trim() == servers.joinToString(", "),
                onClick = { onPreset(servers) },
                label = { Text(label) },
            )
        }
    }
    OutlinedTextField(
        value = dnsText,
        onValueChange = onDnsChange,
        label = { Text(stringResource(R.string.byedpi_dns_field_label)) },
        modifier = Modifier.fillMaxWidth().testTag("byedpi_dns_field"),
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByeDpiEngineSettingsScreen(
    onBack: () -> Unit,
    onOpenStrategyTest: () -> Unit = {},
    viewModel: ByeDpiEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("byedpi_settings"),
        topBar = {
            TopAppBar(
                title = { Text("ByeDPI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ByeDpiSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ByeDpiSettingsUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.byedpi_args_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = s.args,
                        onValueChange = viewModel::onArgsChange,
                        label = { Text("ByeDPI args") },
                        modifier = Modifier.fillMaxWidth().testTag("byedpi_args_field"),
                        minLines = 2,
                    )
                    Text(
                        text = if (s.usingDefault) {
                            stringResource(R.string.byedpi_using_default_fmt, s.defaultArgs)
                        } else {
                            stringResource(R.string.byedpi_using_override)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::onSave,
                            enabled = s.dirty,
                            modifier = Modifier.weight(1f).testTag("byedpi_save_btn"),
                        ) {
                            Text(stringResource(R.string.byedpi_save))
                        }
                        OutlinedButton(
                            onClick = viewModel::onResetToDefault,
                            enabled = !s.usingDefault,
                            modifier = Modifier.weight(1f).testTag("byedpi_reset_btn"),
                        ) {
                            Text(stringResource(R.string.byedpi_reset))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    DnsSection(
                        dnsText = s.dnsText,
                        onPreset = viewModel::onDnsPreset,
                        onDnsChange = viewModel::onDnsChange,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Button(
                        onClick = onOpenStrategyTest,
                        modifier = Modifier.fillMaxWidth().testTag("byedpi_open_strategy_test_btn"),
                    ) {
                        Text(stringResource(R.string.strategy_test_open))
                    }
                    Text(
                        text = stringResource(R.string.strategy_test_summary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
