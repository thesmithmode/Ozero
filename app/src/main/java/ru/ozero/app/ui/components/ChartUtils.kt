package ru.ozero.app.ui.components

import androidx.compose.ui.graphics.Path

fun chartNiceMax(bps: Float): Float {
    if (bps <= 0f) return 10_240f
    val units = floatArrayOf(
        1_024f,
        1_048_576f,
        1_073_741_824f,
        1_099_511_627_776f,
    )
    val factors = floatArrayOf(1f, 2f, 2.5f, 5f, 10f)
    val target = bps * 1.03f
    for (unit in units) {
        for (factor in factors) {
            val level = unit * factor
            if (level >= target) return level
        }
    }
    return target
}

fun chartAxisLabels(max: Float, steps: Int): List<Long> {
    if (max <= 0f || steps <= 0) return emptyList()
    return (steps downTo 0).map { index -> ((max / steps) * index).toLong() }
}

fun Path.addSmooth(values: List<Float>, step: Float, height: Float, safeMax: Float) {
    if (values.size < 2) {
        if (values.size == 1) moveTo(0f, height - (values[0] / safeMax) * height)
        return
    }
    val xs = List(values.size) { i -> i * step }
    val ys = values.map { height - (it / safeMax) * height }
    moveTo(xs[0], ys[0])
    val midXs = List(values.size - 1) { i -> (xs[i] + xs[i + 1]) / 2f }
    val midYs = List(values.size - 1) { i -> (ys[i] + ys[i + 1]) / 2f }
    lineTo(midXs[0], midYs[0])
    for (i in 1 until values.size - 1) {
        quadraticBezierTo(xs[i], ys[i], midXs[i], midYs[i])
    }
    lineTo(xs.last(), ys.last())
}
