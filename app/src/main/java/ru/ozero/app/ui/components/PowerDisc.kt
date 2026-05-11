@file:Suppress("MatchingDeclarationName")

package ru.ozero.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette

enum class PowerDiscState { Off, Connecting, Switching, Connected }

@Composable
fun PowerDisc(
    state: PowerDiscState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameterDp: Int = POWER_DISC_DIAMETER_DP,
) {
    val haloColor = when (state) {
        PowerDiscState.Off -> Color.Transparent
        PowerDiscState.Connecting -> OzeroPalette.StateConnecting.copy(alpha = 0.50f)
        PowerDiscState.Switching -> OzeroPalette.Amber.copy(alpha = 0.60f)
        PowerDiscState.Connected -> OzeroPalette.StateConnected.copy(alpha = 0.55f)
    }
    val edgeColor = when (state) {
        PowerDiscState.Off -> OzeroPalette.GlassEdge
        PowerDiscState.Connecting -> OzeroPalette.StateConnecting.copy(alpha = 0.40f)
        PowerDiscState.Switching -> OzeroPalette.Amber.copy(alpha = 0.50f)
        PowerDiscState.Connected -> OzeroPalette.StateConnected.copy(alpha = 0.45f)
    }
    val transition = rememberInfiniteTransition(label = "power-disc")
    val breathScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state == PowerDiscState.Off) BREATHE_PEAK else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATHE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val haloPulse by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state == PowerDiscState.Connecting ||
            state == PowerDiscState.Switching) HALO_PULSE_PEAK else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(HALO_PULSE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )
    Box(
        modifier = modifier
            .size(diameterDp.dp)
            .testTag(POWER_DISC_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (state != PowerDiscState.Off) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(haloPulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(haloColor, Color.Transparent),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .scale(if (state == PowerDiscState.Off) breathScale else 1.0f)
                .padding(POWER_DISC_INSET_DP.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.25f),
                        ),
                        radius = diameterDp.dp.value * 1.2f,
                    ),
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(edgeColor, Color.Transparent),
                    ),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ozero_logo_white),
                contentDescription = null,
                modifier = Modifier.size((diameterDp * LOGO_RATIO).toInt().dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private const val POWER_DISC_DIAMETER_DP: Int = 256
private const val POWER_DISC_INSET_DP: Int = 22
private const val LOGO_RATIO: Float = 0.46f
private const val BREATHE_DURATION_MS: Int = 3600
private const val HALO_PULSE_DURATION_MS: Int = 2200
private const val BREATHE_PEAK: Float = 1.06f
private const val HALO_PULSE_PEAK: Float = 1.04f

const val POWER_DISC_TEST_TAG: String = "power_disc"
