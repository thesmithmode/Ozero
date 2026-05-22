package ru.ozero.app.ui.urnetwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import kotlin.math.min

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
    val displayBalance = snapshot.balanceBytes.coerceAtLeast(0L)
    val displayStart = snapshot.startBalanceBytes.coerceAtLeast(0L)
    val total = snapshot.usedBytes + snapshot.pendingBytes + displayBalance
    TrafficProgressBar(
        usedBytes = snapshot.usedBytes,
        pendingBytes = snapshot.pendingBytes,
        availableBytes = displayBalance,
        totalBytes = total,
    )
    BalanceRow(
        label = stringResource(R.string.urnetwork_balance_available),
        value = formatBytes(displayBalance + snapshot.pendingBytes.coerceAtLeast(0L)),
        highlight = true,
    )
    BalanceRow(
        label = stringResource(R.string.urnetwork_balance_used),
        value = formatBytes(snapshot.usedBytes),
    )
    if (snapshot.pendingBytes > 0L) {
        BalanceRow(
            label = stringResource(R.string.urnetwork_balance_pending),
            value = formatBytes(snapshot.pendingBytes),
        )
    }
    BalanceRow(
        label = stringResource(R.string.urnetwork_daily_allocation),
        value = formatBytes(displayStart),
    )
    if (state.meanReliabilityWeight > 0.0 || state.totalReferrals > 0L) {
        HorizontalDivider(color = OzeroPalette.Line)
        if (state.meanReliabilityWeight > 0.0) {
            val bonusGib = min(state.meanReliabilityWeight * 100.0, 100.0)
            StatRow(
                label = stringResource(
                    R.string.urnetwork_reliability_label,
                    state.meanReliabilityWeight * 100.0,
                ),
                value = stringResource(R.string.urnetwork_reliability_bonus, bonusGib),
            )
        }
        if (state.totalReferrals > 0L) {
            StatRow(
                label = stringResource(R.string.urnetwork_referrals_label, state.totalReferrals),
                value = stringResource(R.string.urnetwork_referral_bonus, state.totalReferrals * 30L),
            )
        }
    }
}

@Composable
private fun TrafficProgressBar(
    usedBytes: Long,
    pendingBytes: Long,
    availableBytes: Long,
    totalBytes: Long,
) {
    val cornerRadius = 4.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(OzeroPalette.Bg2),
    ) {
        if (totalBytes > 0L) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                if (usedBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(usedBytes.toFloat() / totalBytes)
                            .fillMaxHeight()
                            .background(OzeroPalette.Aqua),
                    )
                }
                if (pendingBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(pendingBytes.toFloat() / totalBytes)
                            .fillMaxHeight()
                            .background(OzeroPalette.Amber),
                    )
                }
                if (availableBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(availableBytes.toFloat() / totalBytes)
                            .fillMaxHeight()
                            .background(OzeroPalette.Bg2),
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendDot(color = OzeroPalette.Aqua, label = stringResource(R.string.urnetwork_balance_used))
        LegendDot(color = OzeroPalette.Amber, label = stringResource(R.string.urnetwork_balance_pending))
        LegendDot(color = OzeroPalette.Bg2, label = stringResource(R.string.urnetwork_balance_available))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OzeroPalette.Text2,
        )
    }
}

@Composable
private fun BalanceRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
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
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text2,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text2,
        )
    }
}
