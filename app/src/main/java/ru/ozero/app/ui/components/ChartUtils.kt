package ru.ozero.app.ui.components

import androidx.compose.ui.graphics.Path
import kotlin.math.roundToLong

private val CHART_NICE_UNITS = floatArrayOf(
    1_024f,
    1_048_576f,
    1_073_741_824f,
    1_099_511_627_776f,
)

private val CHART_NICE_FACTORS = floatArrayOf(
    1f,
    2f,
    2.5f,
    5f,
    10f,
    20f,
    25f,
    30f,
    50f,
    100f,
    200f,
    250f,
    500f,
    1_000f,
)

private val CHART_NICE_LEVELS = CHART_NICE_UNITS
    .flatMap { unit -> CHART_NICE_FACTORS.map { factor -> unit * factor } }
    .toFloatArray()

fun chartNiceMax(bps: Float): Float {
    if (bps <= 0f) return 10_240f
    val maxAllowed = bps * 1.05f
    return CHART_NICE_LEVELS.firstOrNull { it >= bps && it <= maxAllowed } ?: maxAllowed
}

fun chartAxisLabels(max: Float, steps: Int): List<Long> {
    if (max <= 0f || steps <= 0) return emptyList()
    return (steps downTo 0).map { index -> ((max / steps) * index).roundToLong() }
}

fun Path.addPolyline(values: List<Float>, step: Float, height: Float, safeMax: Float) {
    if (values.size < 2) {
        if (values.size == 1) moveTo(0f, height - (values[0] / safeMax) * height)
        return
    }
    val xs = List(values.size) { i -> i * step }
    val ys = values.map { height - (it / safeMax) * height }
    moveTo(xs[0], ys[0])
    for (i in 1 until values.size) {
        lineTo(xs[i], ys[i])
    }
}
