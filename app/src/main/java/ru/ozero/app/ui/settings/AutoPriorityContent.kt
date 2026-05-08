package ru.ozero.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.enginescore.EngineId

@Composable
internal fun AutoPriorityContent(
    priority: List<EngineId>,
    onMove: (EngineId, Int) -> Unit,
    showHeader: Boolean = true,
) {
    val items = priority.filter { !it.isStub }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.AUTO_PRIORITY_SECTION)
            .padding(top = 8.dp),
    ) {
        if (showHeader) {
            Text(
                text = stringResource(R.string.settings_auto_priority_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_auto_priority_summary),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        items.forEachIndexed { index, engine ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + engine.name),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${engine.displayName}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onMove(engine, -1) },
                    enabled = index > 0,
                    modifier = Modifier.testTag(SettingsTestTags.AUTO_PRIORITY_UP_PREFIX + engine.name),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_auto_priority_move_up),
                    )
                }
                IconButton(
                    onClick = { onMove(engine, 1) },
                    enabled = index < items.lastIndex,
                    modifier = Modifier.testTag(SettingsTestTags.AUTO_PRIORITY_DOWN_PREFIX + engine.name),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_auto_priority_move_down),
                    )
                }
            }
        }
    }
}
