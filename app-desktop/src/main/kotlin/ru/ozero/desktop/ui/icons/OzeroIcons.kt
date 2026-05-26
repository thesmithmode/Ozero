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
