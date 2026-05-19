package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod

@Composable
fun ByeDpiUiModeSection(
    settings: ByeDpiUiSettings,
    onChange: (ByeDpiUiSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.byedpi_ui_desync_method),
            style = MaterialTheme.typography.titleSmall,
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

        if (settings.desyncMethod != DesyncMethod.NONE) {
            IntField(
                label = stringResource(R.string.byedpi_ui_split_position),
                value = settings.splitPosition,
                onChange = { onChange(settings.copy(splitPosition = it)) },
                testTag = "byedpi_ui_split_position",
            )
            ToggleRow(
                label = stringResource(R.string.byedpi_ui_split_at_host),
                value = settings.splitAtHost,
                onChange = { onChange(settings.copy(splitAtHost = it)) },
                testTag = "byedpi_ui_split_at_host",
            )
        }

        if (settings.desyncMethod == DesyncMethod.FAKE) {
            IntField(
                label = stringResource(R.string.byedpi_ui_fake_ttl),
                value = settings.fakeTtl,
                onChange = { onChange(settings.copy(fakeTtl = it)) },
                testTag = "byedpi_ui_fake_ttl",
            )
            OutlinedTextField(
                value = settings.fakeSni,
                onValueChange = { onChange(settings.copy(fakeSni = it)) },
                label = { Text(stringResource(R.string.byedpi_ui_fake_sni)) },
                modifier = Modifier.fillMaxWidth().testTag("byedpi_ui_fake_sni"),
                singleLine = true,
            )
            IntField(
                label = stringResource(R.string.byedpi_ui_fake_offset),
                value = settings.fakeOffset,
                onChange = { onChange(settings.copy(fakeOffset = it)) },
                testTag = "byedpi_ui_fake_offset",
            )
        }

        if (settings.desyncMethod == DesyncMethod.OOB ||
            settings.desyncMethod == DesyncMethod.DISOOB
        ) {
            OutlinedTextField(
                value = settings.oobChar,
                onValueChange = { onChange(settings.copy(oobChar = it.take(1))) },
                label = { Text(stringResource(R.string.byedpi_ui_oob_char)) },
                modifier = Modifier.fillMaxWidth().testTag("byedpi_ui_oob_char"),
                singleLine = true,
            )
        }

        Text(
            text = stringResource(R.string.byedpi_ui_protocols),
            style = MaterialTheme.typography.titleSmall,
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_desync_https),
            value = settings.desyncHttps,
            onChange = { onChange(settings.copy(desyncHttps = it)) },
            testTag = "byedpi_ui_desync_https",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_desync_http),
            value = settings.desyncHttp,
            onChange = { onChange(settings.copy(desyncHttp = it)) },
            testTag = "byedpi_ui_desync_http",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_desync_udp),
            value = settings.desyncUdp,
            onChange = { onChange(settings.copy(desyncUdp = it)) },
            testTag = "byedpi_ui_desync_udp",
        )

        Text(
            text = stringResource(R.string.byedpi_ui_http_mod),
            style = MaterialTheme.typography.titleSmall,
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_host_mixed),
            value = settings.hostMixedCase,
            onChange = { onChange(settings.copy(hostMixedCase = it)) },
            testTag = "byedpi_ui_host_mixed",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_domain_mixed),
            value = settings.domainMixedCase,
            onChange = { onChange(settings.copy(domainMixedCase = it)) },
            testTag = "byedpi_ui_domain_mixed",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_host_remove_spaces),
            value = settings.hostRemoveSpaces,
            onChange = { onChange(settings.copy(hostRemoveSpaces = it)) },
            testTag = "byedpi_ui_host_remove_spaces",
        )

        Text(
            text = stringResource(R.string.byedpi_ui_tls_record),
            style = MaterialTheme.typography.titleSmall,
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_tls_record_split),
            value = settings.tlsRecordSplit,
            onChange = { onChange(settings.copy(tlsRecordSplit = it)) },
            testTag = "byedpi_ui_tls_record_split",
        )
        if (settings.tlsRecordSplit) {
            IntField(
                label = stringResource(R.string.byedpi_ui_tls_record_position),
                value = settings.tlsRecordSplitPosition,
                onChange = { onChange(settings.copy(tlsRecordSplitPosition = it)) },
                testTag = "byedpi_ui_tls_record_position",
            )
            ToggleRow(
                label = stringResource(R.string.byedpi_ui_tls_record_at_sni),
                value = settings.tlsRecordSplitAtSni,
                onChange = { onChange(settings.copy(tlsRecordSplitAtSni = it)) },
                testTag = "byedpi_ui_tls_record_at_sni",
            )
        }

        Text(
            text = stringResource(R.string.byedpi_ui_advanced),
            style = MaterialTheme.typography.titleSmall,
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_tcp_fast_open),
            value = settings.tcpFastOpen,
            onChange = { onChange(settings.copy(tcpFastOpen = it)) },
            testTag = "byedpi_ui_tcp_fast_open",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_drop_sack),
            value = settings.dropSack,
            onChange = { onChange(settings.copy(dropSack = it)) },
            testTag = "byedpi_ui_drop_sack",
        )
        ToggleRow(
            label = stringResource(R.string.byedpi_ui_no_domain),
            value = settings.noDomain,
            onChange = { onChange(settings.copy(noDomain = it)) },
            testTag = "byedpi_ui_no_domain",
        )
        IntField(
            label = stringResource(R.string.byedpi_ui_default_ttl),
            value = settings.defaultTtl,
            onChange = { onChange(settings.copy(defaultTtl = it)) },
            testTag = "byedpi_ui_default_ttl",
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = value,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    testTag: String,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val parsed = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
            onChange(parsed)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
