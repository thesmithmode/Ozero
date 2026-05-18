package ru.ozero.app.ui.urnetwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.app.urnetwork.UrnetworkBalanceState

@Composable
fun UrnetworkBalanceCard(
    state: UrnetworkBalanceState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.urnetwork_balance_title),
                style = MaterialTheme.typography.titleSmall,
                color = OzeroPalette.Text,
                fontWeight = FontWeight.SemiBold,
            )
            val snapshot = state.snapshot
            when {
                state.lastError != null -> Text(
                    text = stringResource(R.string.urnetwork_balance_error, state.lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text2,
                )
                snapshot == null -> Text(
                    text = stringResource(R.string.urnetwork_balance_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text2,
                )
                else -> BalanceDetails(state = state)
            }
        }
    }
}

@Composable
private fun BalanceDetails(state: UrnetworkBalanceState) {
    val snapshot = state.snapshot ?: return
    val available = state.availableBytes
    val total = snapshot.balanceBytes.coerceAtLeast(0L)
    val progress = if (total > 0L) (snapshot.usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    BalanceRow(
        labelRes = R.string.urnetwork_balance_available,
        value = formatBytes(available),
        highlight = true,
    )
    BalanceProgressBar(progress = progress)
    BalanceRow(
        labelRes = R.string.urnetwork_balance_used,
        value = formatBytes(snapshot.usedBytes),
    )
    BalanceRow(
        labelRes = R.string.urnetwork_balance_total,
        value = formatBytes(total),
    )
    if (snapshot.pendingBytes > 0L) {
        BalanceRow(
            labelRes = R.string.urnetwork_balance_pending,
            value = formatBytes(snapshot.pendingBytes),
        )
    }
    val planLabel = snapshot.plan?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.urnetwork_balance_plan_unknown)
    Text(
        text = stringResource(R.string.urnetwork_balance_plan_label, planLabel),
        style = MaterialTheme.typography.bodySmall,
        color = OzeroPalette.Text2,
    )
}

@Composable
private fun BalanceRow(
    labelRes: Int,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text2,
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BalanceProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(OzeroPalette.Bg2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OzeroPalette.Aqua),
        )
    }
}
