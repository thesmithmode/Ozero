package ru.ozero.desktop.ui.splittunnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.theme.OzeroPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(onBack: () -> Unit) {
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Split tunnel для Windows использует sing-box process_name правила.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Выберите программы (.exe), которые должны идти через VPN или обходить его.",
                style = MaterialTheme.typography.bodyMedium,
                color = OzeroPalette.Text2,
            )
        }
    }
}
