package ru.ozero.desktop.ui.components

import androidx.compose.ui.graphics.Path

fun chartNiceMax(bps: Float): Float {
    if (bps <= 0f) return 10_240f
    val levels = floatArrayOf(
        1_024f, 2_048f, 5_120f,
        10_240f, 20_480f, 51_200f,
        102_400f, 204_800f, 512_000f,
        1_048_576f, 2_097_152f, 5_242_880f,
        10_485_760f, 20_971_520f, 52_428_800f,
        104_857_600f, 209_715_200f,
    )
    return levels.firstOrNull { it > bps * 1.1f } ?: (bps * 2f)
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
