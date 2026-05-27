package ru.ozero.desktop.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object OzeroIcons {

    val CallSplit: ImageVector by lazy {
        ImageVector.Builder(
            name = "OzeroCallSplit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(14f, 4f)
            lineToRelative(2.29f, 2.29f)
            lineToRelative(-2.88f, 2.88f)
            lineToRelative(1.42f, 1.42f)
            lineToRelative(2.88f, -2.88f)
            lineTo(20f, 10f)
            verticalLineTo(4f)
            horizontalLineTo(14f)
            close()
            moveTo(10f, 4f)
            horizontalLineTo(4f)
            verticalLineToRelative(6f)
            lineToRelative(2.29f, -2.29f)
            lineToRelative(4.71f, 4.7f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-8.41f)
            lineToRelative(-5.29f, -5.3f)
            lineTo(10f, 4f)
            close()
        }.build()
    }

    val Telegram: ImageVector by lazy {
        ImageVector.Builder(
            name = "OzeroTelegram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = SolidColor(Color.White),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(9.78f, 18.65f)
            lineToRelative(0.28f, -4.23f)
            lineToRelative(7.68f, -6.92f)
            curveToRelative(0.34f, -0.31f, -0.07f, -0.46f, -0.52f, -0.19f)
            lineTo(7.74f, 13.3f)
            lineTo(3.64f, 12f)
            curveToRelative(-0.88f, -0.25f, -0.89f, -0.86f, 0.2f, -1.3f)
            lineToRelative(15.97f, -6.16f)
            curveToRelative(0.73f, -0.33f, 1.43f, 0.18f, 1.15f, 1.3f)
            lineToRelative(-2.72f, 12.81f)
            curveToRelative(-0.19f, 0.91f, -0.74f, 1.13f, -1.5f, 0.71f)
            lineTo(12.6f, 16.3f)
            lineToRelative(-1.99f, 1.93f)
            curveToRelative(-0.23f, 0.23f, -0.42f, 0.42f, -0.83f, 0.42f)
            close()
        }.build()
    }

    val Power: ImageVector by lazy {
        ImageVector.Builder(
            name = "OzeroPower",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(13f, 3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(10f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            close()
            moveTo(17.83f, 5.17f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(19.06f, 7.89f, 20f, 9.82f, 20f, 12f)
            curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
            curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
            curveTo(4f, 9.82f, 4.94f, 7.89f, 6.59f, 6.59f)
            lineTo(5.17f, 5.17f)
            curveTo(3.19f, 7.19f, 2f, 9.44f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 9.44f, 20.81f, 7.19f, 17.83f, 5.17f)
            close()
        }.build()
    }
}
