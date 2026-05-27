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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.ui.theme.OzeroPalette
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val WARP_CONFIG_DIR = File(System.getProperty("user.home"), ".ozero")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpDesktopSettingsScreen(onBack: () -> Unit) {
    val configFile = File(WARP_CONFIG_DIR, "warp.conf")
    var configText by remember {
        mutableStateOf(if (configFile.exists()) configFile.readText() else "")
    }
    var saved by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WARP") },
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
                text = "Конфигурация WireGuard",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Вставьте конфигурацию WARP или импортируйте из файла",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {
                    val dialog = FileDialog(null as Frame?, "Импорт WARP конфигурации", FileDialog.LOAD)
                    dialog.file = "*.conf"
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        configText = File(dir, file).readText()
                        saved = false
                    }
                }) {
                    Text("Импорт из файла")
                }

                if (configText.isNotBlank()) {
                    OutlinedButton(onClick = {
                        configText = ""
                        saved = false
                    }) {
                        Text("Очистить")
                    }
                }
            }

            OutlinedTextField(
                value = configText,
                onValueChange = {
                    configText = it
                    saved = false
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 15,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                placeholder = {
                    Text(
                        "[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...",
                        style = MaterialTheme.typography.bodySmall,
                        color = OzeroPalette.Text3,
                    )
                },
            )

            if (configText.isNotBlank()) {
                WarpConfigPreview(configText)
            }

            Button(
                onClick = {
                    WARP_CONFIG_DIR.mkdirs()
                    configFile.writeText(configText)
                    saved = true
                },
                enabled = !saved,
            ) {
                Text("Сохранить")
            }

            if (saved && configText.isNotBlank()) {
                Text(
                    text = "Конфигурация сохранена",
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.StateConnected,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WarpConfigPreview(configText: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.GlassFill),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val lines = configText.lines()
            val endpoint = lines.find { it.trimStart().startsWith("Endpoint", ignoreCase = true) }
            val address = lines.find { it.trimStart().startsWith("Address", ignoreCase = true) }
            if (endpoint != null) {
                Text(
                    text = endpoint.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = OzeroPalette.Aqua,
                )
            }
            if (address != null) {
                Text(
                    text = address.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = OzeroPalette.Text2,
                )
            }
        }
    }
}
