package com.lemon.echo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Draws four L-shaped corner brackets using a Canvas. */
@Composable
internal fun CornerBrackets(
    modifier: Modifier = Modifier,
    cornerLength: Dp,
    strokeWidth: Dp,
    color: Color
) {
    Canvas(modifier = modifier) {
        val cLen = cornerLength.toPx()
        val sw = strokeWidth.toPx()
        val path = Path().apply {
            // Top-left
            moveTo(0f, cLen)
            lineTo(0f, 0f)
            lineTo(cLen, 0f)
            // Top-right
            moveTo(size.width - cLen, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, cLen)
            // Bottom-right
            moveTo(size.width, size.height - cLen)
            lineTo(size.width, size.height)
            lineTo(size.width - cLen, size.height)
            // Bottom-left
            moveTo(cLen, size.height)
            lineTo(0f, size.height)
            lineTo(0f, size.height - cLen)
        }
        drawPath(path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}
