package ru.ozero.desktop.ui.settings.engines

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.ui.theme.OzeroPalette
import ru.ozero.desktop.vpn.DesktopSettingsStore
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val SINGBOX_CONFIG_DIR = File(System.getProperty("user.home"), ".ozero")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingboxDesktopSettingsScreen(
    settingsStore: DesktopSettingsStore,
    onBack: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()
    var subscriptionUrl by remember(settings.singboxSubscriptionUrl) {
        mutableStateOf(settings.singboxSubscriptionUrl)
    }
    var customLink by remember(settings.singboxCustomLink) {
        mutableStateOf(settings.singboxCustomLink)
    }
    var configFilePath by remember { mutableStateOf("") }
    val dirty = subscriptionUrl != settings.singboxSubscriptionUrl ||
        customLink != settings.singboxCustomLink

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sing-box") },
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
                text = "URL подписки",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Ссылка на подписку с прокси-профилями",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            OutlinedTextField(
                value = subscriptionUrl,
                onValueChange = { subscriptionUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://example.com/subscription") },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Прокси-ссылка",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "vless://, vmess://, trojan://, ss:// ссылка",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            OutlinedTextField(
                value = customLink,
                onValueChange = { customLink = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                placeholder = { Text("vless://...") },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Файл конфигурации",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Импорт JSON конфигурации sing-box",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = {
                    val dialog = FileDialog(null as Frame?, "Импорт sing-box конфигурации", FileDialog.LOAD)
                    dialog.file = "*.json"
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        val src = File(dir, file)
                        SINGBOX_CONFIG_DIR.mkdirs()
                        val dst = File(SINGBOX_CONFIG_DIR, "singbox-custom.json")
                        src.copyTo(dst, overwrite = true)
                        configFilePath = dst.absolutePath
                    }
                }) {
                    Text("Импорт из файла")
                }
            }

            if (configFilePath.isNotBlank()) {
                Text(
                    text = "Импортировано: $configFilePath",
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.StateConnected,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Button(
                onClick = {
                    settingsStore.update {
                        copy(
                            singboxSubscriptionUrl = subscriptionUrl,
                            singboxCustomLink = customLink,
                        )
                    }
                },
                enabled = dirty,
            ) {
                Text("Сохранить")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
