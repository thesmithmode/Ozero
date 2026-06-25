@file:Suppress("MatchingDeclarationName")

package ru.ozero.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import ru.ozero.app.ui.theme.OzeroPalette

data class DockTab(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun BottomDock(
    tabs: List<DockTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag(BOTTOM_DOCK_TEST_TAG)
            .clip(RoundedCornerShape(DOCK_RADIUS_DP.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.03f),
                    ),
                ),
            )
            .border(
                width = 0.5.dp,
                color = OzeroPalette.GlassEdge,
                shape = RoundedCornerShape(DOCK_RADIUS_DP.dp),
            )
            .padding(horizontal = DOCK_PADDING_HORIZONTAL_DP.dp, vertical = DOCK_PADDING_VERTICAL_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(DOCK_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            DockButton(
                tab = tab,
                isActive = tab.id == activeTabId,
                onClick = { onTabSelected(tab.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("$BOTTOM_DOCK_TAB_TEST_TAG_PREFIX${tab.id}"),
            )
        }
    }
}

@Composable
private fun DockButton(
    tab: DockTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (isActive) MaterialTheme.colorScheme.onSurface else OzeroPalette.Text2
    val background = if (isActive) Color.White.copy(alpha = 0.10f) else Color.Transparent
    val density = LocalDensity.current
    val verticalScale = min(1f, 1.3f / density.fontScale)
    val iconSize = (DOCK_ICON_SIZE_DP * verticalScale).dp
    val verticalPadding = (DOCK_BUTTON_VERTICAL_PADDING_DP * verticalScale).dp
    val labelSize = (DOCK_LABEL_SIZE_SP * verticalScale).sp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DOCK_BUTTON_RADIUS_DP.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = isActive
            }
            .padding(vertical = verticalPadding, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = labelSize,
                ),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DOCK_RADIUS_DP: Int = 28
private const val DOCK_BUTTON_RADIUS_DP: Int = 22
private const val DOCK_PADDING_HORIZONTAL_DP: Int = 8
private const val DOCK_PADDING_VERTICAL_DP: Int = 6
private const val DOCK_GAP_DP: Int = 2
private const val DOCK_ICON_SIZE_DP: Int = 21
private const val DOCK_BUTTON_VERTICAL_PADDING_DP: Int = 8
private const val DOCK_LABEL_SIZE_SP: Float = 10.5f

const val BOTTOM_DOCK_TEST_TAG: String = "bottom_dock"
const val BOTTOM_DOCK_TAB_TEST_TAG_PREFIX: String = "bottom_dock_tab_"
