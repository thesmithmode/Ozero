@file:Suppress("MatchingDeclarationName")

package ru.ozero.desktop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ru.ozero.desktop.ui.theme.OzeroPalette

enum class OzeroBackgroundState { Off, Connecting, Connected }

@Composable
fun OzeroBackground(
    state: OzeroBackgroundState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val (auraA, auraB) = when (state) {
        OzeroBackgroundState.Off -> OzeroPalette.LakeOffA to OzeroPalette.LakeOffB
        OzeroBackgroundState.Connecting -> OzeroPalette.LakeConnectingA to OzeroPalette.LakeConnectingB
        OzeroBackgroundState.Connected -> OzeroPalette.LakeConnectedA to OzeroPalette.LakeConnectedB
    }
    val baseBrush = Brush.radialGradient(
        colors = listOf(OzeroPalette.Bg2, OzeroPalette.Bg0),
        radius = 1500f,
    )
    val transition = rememberInfiniteTransition(label = "lake-drift")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Box(modifier = modifier.fillMaxSize().background(baseBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraA.copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(w * (0.25f + drift * 0.06f), h * (0.30f + drift * 0.04f)),
                    radius = w * 0.6f,
                ),
                radius = w * 0.6f,
                center = Offset(w * (0.25f + drift * 0.06f), h * (0.30f + drift * 0.04f)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraB.copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(w * (0.78f - drift * 0.05f), h * (0.78f - drift * 0.04f)),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * (0.78f - drift * 0.05f), h * (0.78f - drift * 0.04f)),
            )
        }
        content()
    }
}
