package ru.ozero.desktop.ui.settings.engines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.ui.theme.OzeroPalette
import ru.ozero.desktop.vpn.DesktopSettingsStore

private val DNS_PRESETS = listOf(
    "Авто" to "",
    "Google" to "8.8.8.8, 8.8.4.4",
    "Cloudflare" to "1.1.1.1, 1.0.0.1",
    "Quad9" to "9.9.9.9, 149.112.112.112",
    "NextDNS" to "45.90.28.0, 45.90.30.0",
    "AdGuard" to "94.140.14.14, 94.140.15.15",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByeDpiDesktopSettingsScreen(
    settingsStore: DesktopSettingsStore,
    onBack: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()
    var args by remember(settings.byedpiArgs) { mutableStateOf(settings.byedpiArgs) }
    var dns by remember(settings.byedpiDns) { mutableStateOf(settings.byedpiDns) }
    val dirty = args != settings.byedpiArgs || dns != settings.byedpiDns

    Scaffold(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Аргументы командной строки",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Аргументы передаются byedpi.exe как есть",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                placeholder = {
                    Text(
                        SettingsModel.DEFAULT_BYEDPI_ARGS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OzeroPalette.Text3,
                    )
                },
            )

            if (args != SettingsModel.DEFAULT_BYEDPI_ARGS) {
                Text(
                    text = "Используются пользовательские аргументы",
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Amber,
                )
            }

            Text(
                text = "DNS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DNS_PRESETS.forEach { (label, value) ->
                    val selected = dns == value
                    FilterChip(
                        selected = selected,
                        onClick = { dns = value },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = dns,
                onValueChange = { dns = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("DNS серверы") },
                placeholder = { Text("1.1.1.1, 8.8.8.8") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        settingsStore.update { copy(byedpiArgs = args, byedpiDns = dns) }
                    },
                    enabled = dirty,
                ) {
                    Text("Сохранить")
                }
                OutlinedButton(
                    onClick = {
                        args = SettingsModel.DEFAULT_BYEDPI_ARGS
                        dns = ""
                    },
                ) {
                    Text("По умолчанию")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
