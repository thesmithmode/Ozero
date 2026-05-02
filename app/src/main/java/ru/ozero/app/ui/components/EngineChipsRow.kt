package ru.ozero.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.app.ui.MainScreenTestTags
import ru.ozero.enginescore.EngineId

@Composable
fun EngineChipsRow(
    selectedEngine: EngineId?,
    onSelect: (EngineId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engines = EngineId.entries.filter { !it.isStub }
    LazyRow(
        modifier = modifier.testTag(MainScreenTestTags.ENGINE_CHIPS_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = selectedEngine == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.engine_chip_auto)) },
                modifier = Modifier.testTag("${MainScreenTestTags.ENGINE_CHIP_PREFIX}AUTO"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        items(engines, key = { it.name }) { engine ->
            FilterChip(
                selected = selectedEngine == engine,
                onClick = { onSelect(engine) },
                label = { Text(engine.displayName) },
                modifier = Modifier.testTag("${MainScreenTestTags.ENGINE_CHIP_PREFIX}${engine.name}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
