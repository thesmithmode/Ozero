package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod

@Composable
@Suppress("LongMethod")
fun ByeDpiUiModeSection(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProxyCategoryCard(settings, onChange)
        DesyncCategoryCard(settings, onChange)
        ProtocolsCategoryCard(settings, onChange)
        HttpDesyncCategoryCard(settings, onChange)
        HttpsDesyncCategoryCard(settings, onChange)
        UdpDesyncCategoryCard(settings, onChange)
    }
}

@Composable
private fun CategoryCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ProxyCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_proxy)) {
        IntFieldWithHint(
            label = stringResource(R.string.byedpi_ui_max_connections),
            hint = stringResource(R.string.byedpi_ui_max_connections_hint),
            value = settings.maxConnections,
            onChange = { onChange(settings.copy(maxConnections = it)) },
            testTag = "byedpi_ui_max_connections",
        )
        IntFieldWithHint(
            label = stringResource(R.string.byedpi_ui_buffer_size),
            hint = stringResource(R.string.byedpi_ui_buffer_size_hint),
            value = settings.bufferSize,
            onChange = { onChange(settings.copy(bufferSize = it)) },
            testTag = "byedpi_ui_buffer_size",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_no_domain),
            hint = stringResource(R.string.byedpi_ui_no_domain_hint),
            value = settings.noDomain,
            onChange = { onChange(settings.copy(noDomain = it)) },
            testTag = "byedpi_ui_no_domain",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_tcp_fast_open),
            hint = stringResource(R.string.byedpi_ui_tcp_fast_open_hint),
            value = settings.tcpFastOpen,
            onChange = { onChange(settings.copy(tcpFastOpen = it)) },
            testTag = "byedpi_ui_tcp_fast_open",
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun DesyncCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_desync)) {
        Text(
            text = stringResource(R.string.byedpi_ui_desync_method),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.byedpi_ui_desync_method_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DesyncMethod.values().toList()) { method ->
                AssistChip(
                    onClick = { onChange(settings.copy(desyncMethod = method)) },
                    label = { Text(method.name) },
                    modifier = Modifier.testTag("byedpi_ui_method_${method.name}"),
                    enabled = settings.desyncMethod != method,
                )
            }
        }
        Text(
            text = stringResource(R.string.byedpi_ui_current_method_fmt, settings.desyncMethod.name),
            style = MaterialTheme.typography.bodySmall,
        )

        IntFieldWithHint(
            label = stringResource(R.string.byedpi_ui_default_ttl),
            hint = stringResource(R.string.byedpi_ui_default_ttl_hint),
            value = settings.defaultTtl,
            onChange = { onChange(settings.copy(defaultTtl = it)) },
            testTag = "byedpi_ui_default_ttl",
        )

        if (settings.desyncMethod != DesyncMethod.NONE) {
            IntFieldWithHint(
                label = stringResource(R.string.byedpi_ui_split_position),
                hint = stringResource(R.string.byedpi_ui_split_position_hint),
                value = settings.splitPosition,
                onChange = { onChange(settings.copy(splitPosition = it)) },
                testTag = "byedpi_ui_split_position",
                allowNegative = true,
            )
            ToggleRowWithHint(
                label = stringResource(R.string.byedpi_ui_split_at_host),
                hint = stringResource(R.string.byedpi_ui_split_at_host_hint),
                value = settings.splitAtHost,
                onChange = { onChange(settings.copy(splitAtHost = it)) },
                testTag = "byedpi_ui_split_at_host",
            )
            ToggleRowWithHint(
                label = stringResource(R.string.byedpi_ui_drop_sack),
                hint = stringResource(R.string.byedpi_ui_drop_sack_hint),
                value = settings.dropSack,
                onChange = { onChange(settings.copy(dropSack = it)) },
                testTag = "byedpi_ui_drop_sack",
            )
        }

        if (settings.desyncMethod == DesyncMethod.FAKE) {
            IntFieldWithHint(
                label = stringResource(R.string.byedpi_ui_fake_ttl),
                hint = stringResource(R.string.byedpi_ui_fake_ttl_hint),
                value = settings.fakeTtl,
                onChange = { onChange(settings.copy(fakeTtl = it)) },
                testTag = "byedpi_ui_fake_ttl",
            )
            IntFieldWithHint(
                label = stringResource(R.string.byedpi_ui_fake_offset),
                hint = stringResource(R.string.byedpi_ui_fake_offset_hint),
                value = settings.fakeOffset,
                onChange = { onChange(settings.copy(fakeOffset = it)) },
                testTag = "byedpi_ui_fake_offset",
            )
            TextFieldWithHint(
                label = stringResource(R.string.byedpi_ui_fake_sni),
                hint = stringResource(R.string.byedpi_ui_fake_sni_hint),
                value = settings.fakeSni,
                onChange = { onChange(settings.copy(fakeSni = it)) },
                testTag = "byedpi_ui_fake_sni",
            )
        }

        if (settings.desyncMethod == DesyncMethod.OOB ||
            settings.desyncMethod == DesyncMethod.DISOOB
        ) {
            TextFieldWithHint(
                label = stringResource(R.string.byedpi_ui_oob_char),
                hint = stringResource(R.string.byedpi_ui_oob_char_hint),
                value = settings.oobChar,
                onChange = { onChange(settings.copy(oobChar = it.take(1))) },
                testTag = "byedpi_ui_oob_char",
            )
        }
    }
}

@Composable
private fun ProtocolsCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_protocols)) {
        Text(
            text = stringResource(R.string.byedpi_ui_protocols_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_desync_http),
            hint = stringResource(R.string.byedpi_ui_desync_http_hint),
            value = settings.desyncHttp,
            onChange = { onChange(settings.copy(desyncHttp = it)) },
            testTag = "byedpi_ui_desync_http",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_desync_https),
            hint = stringResource(R.string.byedpi_ui_desync_https_hint),
            value = settings.desyncHttps,
            onChange = { onChange(settings.copy(desyncHttps = it)) },
            testTag = "byedpi_ui_desync_https",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_desync_udp),
            hint = stringResource(R.string.byedpi_ui_desync_udp_hint),
            value = settings.desyncUdp,
            onChange = { onChange(settings.copy(desyncUdp = it)) },
            testTag = "byedpi_ui_desync_udp",
        )
    }
}

@Composable
private fun HttpDesyncCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_http_desync)) {
        Text(
            text = stringResource(R.string.byedpi_ui_http_desync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_host_mixed),
            hint = stringResource(R.string.byedpi_ui_host_mixed_hint),
            value = settings.hostMixedCase,
            onChange = { onChange(settings.copy(hostMixedCase = it)) },
            testTag = "byedpi_ui_host_mixed",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_domain_mixed),
            hint = stringResource(R.string.byedpi_ui_domain_mixed_hint),
            value = settings.domainMixedCase,
            onChange = { onChange(settings.copy(domainMixedCase = it)) },
            testTag = "byedpi_ui_domain_mixed",
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_host_remove_spaces),
            hint = stringResource(R.string.byedpi_ui_host_remove_spaces_hint),
            value = settings.hostRemoveSpaces,
            onChange = { onChange(settings.copy(hostRemoveSpaces = it)) },
            testTag = "byedpi_ui_host_remove_spaces",
        )
    }
}

@Composable
private fun HttpsDesyncCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_https_desync)) {
        Text(
            text = stringResource(R.string.byedpi_ui_https_desync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRowWithHint(
            label = stringResource(R.string.byedpi_ui_tls_record_split),
            hint = stringResource(R.string.byedpi_ui_tls_record_split_hint),
            value = settings.tlsRecordSplit,
            onChange = { onChange(settings.copy(tlsRecordSplit = it)) },
            testTag = "byedpi_ui_tls_record_split",
        )
        if (settings.tlsRecordSplit) {
            IntFieldWithHint(
                label = stringResource(R.string.byedpi_ui_tls_record_position),
                hint = stringResource(R.string.byedpi_ui_tls_record_position_hint),
                value = settings.tlsRecordSplitPosition,
                onChange = { onChange(settings.copy(tlsRecordSplitPosition = it)) },
                testTag = "byedpi_ui_tls_record_position",
                allowNegative = true,
            )
            ToggleRowWithHint(
                label = stringResource(R.string.byedpi_ui_tls_record_at_sni),
                hint = stringResource(R.string.byedpi_ui_tls_record_at_sni_hint),
                value = settings.tlsRecordSplitAtSni,
                onChange = { onChange(settings.copy(tlsRecordSplitAtSni = it)) },
                testTag = "byedpi_ui_tls_record_at_sni",
            )
        }
    }
}

@Composable
private fun UdpDesyncCategoryCard(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    CategoryCard(title = stringResource(R.string.byedpi_ui_cat_udp_desync)) {
        Text(
            text = stringResource(R.string.byedpi_ui_udp_desync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IntFieldWithHint(
            label = stringResource(R.string.byedpi_ui_udp_fake_count),
            hint = stringResource(R.string.byedpi_ui_udp_fake_count_hint),
            value = settings.udpFakeCount,
            onChange = { onChange(settings.copy(udpFakeCount = it)) },
            testTag = "byedpi_ui_udp_fake_count",
        )
    }
}

@Composable
private fun ToggleRowWithHint(
    label: String,
    hint: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun IntFieldWithHint(
    label: String,
    hint: String,
    value: Int,
    onChange: (Int) -> Unit,
    testTag: String,
    allowNegative: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { raw ->
                val cleaned = if (allowNegative) {
                    raw.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }
                } else {
                    raw.filter { it.isDigit() }
                }
                val parsed = cleaned.toIntOrNull() ?: 0
                onChange(parsed)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Number,
            ),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextFieldWithHint(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            singleLine = true,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
