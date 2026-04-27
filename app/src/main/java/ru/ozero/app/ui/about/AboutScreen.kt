package ru.ozero.app.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.BuildConfig
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val uri = LocalUriHandler.current
    val items = listOf(
        AboutLink(stringResource(R.string.about_repo), "https://github.com/thesmithmode/Ozero"),
        AboutLink(
            stringResource(R.string.about_privacy),
            "https://github.com/thesmithmode/Ozero/blob/main/docs/privacy.md",
        ),
        AboutLink(
            stringResource(R.string.about_threat_model),
            "https://github.com/thesmithmode/Ozero/blob/main/docs/threat-model.md",
        ),
        AboutLink(
            stringResource(R.string.about_legal),
            "https://github.com/thesmithmode/Ozero/blob/main/LICENSE",
        ),
    )
    Scaffold(
        modifier = Modifier.testTag("about"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items) { link ->
                    Text(
                        text = link.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uri.openUri(link.url) }
                            .padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private data class AboutLink(val label: String, val url: String)
