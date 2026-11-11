package com.swiffle.app.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Universal color tokens for Material 3 selection and TTS read-aloud highlighting.
 */
object SelectionTheme {
    val SelectionLight: Color = Color(0xFFDDD1FF)
    val SelectionDark: Color = Color(0xFFDDD1FF).copy(alpha = 0.35f)
    val TtsSentence: Color = Color(0xFFEDE7FF)
    val TtsWord: Color = Color(0xFFD3C2FF)
    val HandlePurple: Color = Color(0xFF7B61FF)
}

/**
 * A single continuous geometric highlight band across one text line.
 * Guaranteed to cover glyph bodies and bridged word spaces with no seams or gaps.
 */
data class HighlightBand(
    val lineIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Enterprise Single Shared Highlight Renderer.
 * Replaces fragmented per-glyph boxes across portrait and landscape modes with
 * continuous per-line bands rendered into a single Path with BlendMode.Multiply.
 */
object HighlightRenderer {

    /**
     * Builds a single merged Path containing all highlight bands for a page.
     * Reuses the provided [targetPath] to eliminate allocations in the draw pass.
     */
    fun buildPath(
        bands: List<HighlightBand>,
        scaleX: Float,
        scaleY: Float,
        pageLeft: Float,
        pageTop: Float,
        cornerRadiusPx: Float = 6f,
        targetPath: Path = Path()
    ): Path {
        targetPath.reset()
        targetPath.fillType = PathFillType.NonZero

        if (bands.isEmpty()) return targetPath

        val cr = CornerRadius(cornerRadiusPx, cornerRadiusPx)

        for (b in bands) {
            val rx = pageLeft + (b.left * scaleX)
            val ry = pageTop + (b.top * scaleY)
            val rw = (b.right - b.left) * scaleX
            val rh = (b.bottom - b.top) * scaleY

            if (rw > 0f && rh > 0f) {
                targetPath.addRoundRect(
                    RoundRect(
                        left = rx,
                        top = ry,
                        right = rx + rw,
                        bottom = ry + rh,
                        topLeftCornerRadius = cr,
                        topRightCornerRadius = cr,
                        bottomRightCornerRadius = cr,
                        bottomLeftCornerRadius = cr
                    )
                )
            }
        }

        return targetPath
    }

    /**
     * Draws the continuous highlight bands directly onto the Canvas using BlendMode.Multiply.
     * This preserves 100% black text contrast and eliminates washed-out SrcOver overlays.
     */
    fun draw(
        drawScope: DrawScope,
        bands: List<HighlightBand>,
        scaleX: Float,
        scaleY: Float,
        pageLeft: Float,
        pageTop: Float,
        color: Color,
        isDark: Boolean = false,
        blendMode: BlendMode = if (isDark) BlendMode.SrcOver else BlendMode.Multiply,
        cornerRadiusPx: Float = 6f,
        scratchPath: Path = Path()
    ) {
        if (bands.isEmpty()) return

        val path = buildPath(
            bands = bands,
            scaleX = scaleX,
            scaleY = scaleY,
            pageLeft = pageLeft,
            pageTop = pageTop,
            cornerRadiusPx = cornerRadiusPx,
            targetPath = scratchPath
        )

        drawScope.drawPath(
            path = path,
            color = color,
            blendMode = blendMode
        )
    }
}
