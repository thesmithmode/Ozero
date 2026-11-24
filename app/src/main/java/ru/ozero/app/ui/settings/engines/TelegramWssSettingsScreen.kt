package ru.ozero.app.ui.settings.engines

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramWssSettingsScreen(state: TunnelState, onBack: () -> Unit) {
    val context = LocalContext.current
    val port = (state as? TunnelState.Connected)?.takeIf { it.engineId == EngineId.TGWSS }?.socksPort
    var openFailed by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram WSS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.tgwss_summary), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.tgwss_setup))
            if (port != null && port > 0) {
                Text(stringResource(R.string.tgwss_address, port))
                Button(onClick = {
                    openFailed = try {
                        val uri = Uri.parse("tg://socks?server=127.0.0.1&port=$port")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        false
                    } catch (_: ActivityNotFoundException) {
                        true
                    } catch (_: SecurityException) {
                        true
                    }
                }) {
                    Text(stringResource(R.string.tgwss_open))
                }
                if (openFailed) Text(stringResource(R.string.tgwss_open_failed))
            } else {
                Text(stringResource(R.string.tgwss_not_running))
            }
        }
    }
}
