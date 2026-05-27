package ru.ozero.desktop.ui.splittunnel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.model.SplitTunnelMode
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.theme.OzeroPalette
import ru.ozero.desktop.vpn.DesktopSettingsStore
import java.awt.FileDialog
import java.awt.Frame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(
    settingsStore: DesktopSettingsStore,
    onBack: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()

    Scaffold(
        modifier = Modifier.testTag("split_tunnel"),
        topBar = {
            TopAppBar(
                title = { Text(Strings.SPLIT_TUNNEL_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val dialog = FileDialog(null as Frame?, "Выберите программу (.exe)", FileDialog.LOAD)
                        dialog.file = "*.exe"
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val file = dialog.file
                        if (dir != null && file != null) {
                            val exeName = file.removeSuffix(".exe")
                            val current = settings.splitTunnelApps
                            if (exeName !in current) {
                                settingsStore.update { copy(splitTunnelApps = current + exeName) }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить программу")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Text(
                text = "Split tunnel для Windows использует sing-box process_name правила.",
                style = MaterialTheme.typography.bodyMedium,
                color = OzeroPalette.Text2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                text = "Выберите программы (.exe), которые должны идти через VPN или обходить его.",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Режим",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            SplitTunnelMode.entries.forEach { mode ->
                val label = when (mode) {
                    SplitTunnelMode.DISABLED -> "Выключено"
                    SplitTunnelMode.INCLUDE -> "Только выбранные через VPN"
                    SplitTunnelMode.EXCLUDE -> "Всё через VPN, кроме выбранных"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.splitTunnelMode == mode,
                            onClick = { settingsStore.update { copy(splitTunnelMode = mode) } },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.splitTunnelMode == mode,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (settings.splitTunnelApps.isEmpty()) {
                Text(
                    text = "Нет добавленных программ. Нажмите + чтобы добавить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(settings.splitTunnelApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "$app.exe",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(onClick = {
                                settingsStore.update {
                                    copy(splitTunnelApps = splitTunnelApps - app)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = OzeroPalette.StateDanger,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
