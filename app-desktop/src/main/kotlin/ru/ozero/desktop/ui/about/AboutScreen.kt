package ru.ozero.desktop.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.model.AppVersion
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.icons.OzeroIcons
import java.awt.Desktop
import java.net.URI

private const val TELEGRAM_URL = "https://t.me/vpn_ozero"
private val TELEGRAM_BRAND_COLOR = Color(0xFF2AABEE)

private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.testTag("about"),
        topBar = {
            TopAppBar(
                title = { Text(Strings.ABOUT_TITLE) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = Strings.ABOUT_VERSION.format(AppVersion.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.testTag("about_telegram"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TELEGRAM_BRAND_COLOR)
                        .clickable { openUrl(TELEGRAM_URL) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = OzeroIcons.Telegram,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Strings.ABOUT_TELEGRAM_CHANNEL,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TELEGRAM_BRAND_COLOR,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.clickable { openUrl(TELEGRAM_URL) },
                )
            }
        }
    }
}
