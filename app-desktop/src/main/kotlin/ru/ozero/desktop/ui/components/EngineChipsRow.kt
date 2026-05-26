package ru.ozero.desktop.ui.components

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
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.strings.Strings

@Composable
fun EngineChipsRow(
    selectedEngine: EngineId?,
    engineOrder: List<EngineId>,
    onSelect: (EngineId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("engine_chips_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = selectedEngine == null,
                onClick = { onSelect(null) },
                label = { Text(Strings.ENGINE_CHIP_AUTO) },
                modifier = Modifier.testTag("engine_chip_AUTO"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        items(engineOrder, key = { it.name }) { engine ->
            FilterChip(
                selected = selectedEngine == engine,
                onClick = { onSelect(engine) },
                label = { Text(engine.displayName) },
                modifier = Modifier.testTag("engine_chip_${engine.name}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
