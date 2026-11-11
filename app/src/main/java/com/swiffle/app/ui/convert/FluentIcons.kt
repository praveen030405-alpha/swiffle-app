package com.swiffle.app.ui.convert

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Windows 11 Segoe Fluent Iconography Suite.
 * Replicates Microsoft Learn Segoe Fluent Icons specification:
 * - Uniform 1.25dp - 1.5dp Monoline geometry
 * - Rounded joints and caps matching 4px/8px design geometry
 * - Clean semantic metaphors without visual clutter
 */
object FluentIcons {

    val DocumentPdf: ImageVector = ImageVector.Builder(
        name = "FluentDocumentPdf",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 3.5f)
            lineTo(14.5f, 3.5f)
            lineTo(19f, 8f)
            lineTo(19f, 20.5f)
            lineTo(6f, 20.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.5f, 3.5f)
            lineTo(14.5f, 8f)
            lineTo(19f, 8f)
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(9f, 12f); lineTo(15.5f, 12f)
            moveTo(9f, 15f); lineTo(14f, 15f)
            moveTo(9f, 18f); lineTo(12f, 18f)
        }
    }.build()

    val DocumentWord: ImageVector = ImageVector.Builder(
        name = "FluentDocumentWord",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 3.5f)
            lineTo(14.5f, 3.5f)
            lineTo(19f, 8f)
            lineTo(19f, 20.5f)
            lineTo(6f, 20.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14.5f, 3.5f); lineTo(14.5f, 8f); lineTo(19f, 8f)
        }
        // 'W' glyph
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 12.5f)
            lineTo(9.8f, 17f)
            lineTo(12f, 13.5f)
            lineTo(14.2f, 17f)
            lineTo(15.5f, 12.5f)
        }
    }.build()

    val DocumentPpt: ImageVector = ImageVector.Builder(
        name = "FluentDocumentPpt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 3.5f)
            lineTo(14.5f, 3.5f)
            lineTo(19f, 8f)
            lineTo(19f, 20.5f)
            lineTo(6f, 20.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14.5f, 3.5f); lineTo(14.5f, 8f); lineTo(19f, 8f)
        }
        // 'P' glyph
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9.5f, 17f); lineTo(9.5f, 12f); lineTo(12.5f, 12f)
            arcTo(1.8f, 1.8f, 0f, true, true, 12.5f, 15f)
            lineTo(9.5f, 15f)
        }
    }.build()

    val DocumentExcel: ImageVector = ImageVector.Builder(
        name = "FluentDocumentExcel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.5f, 5f); lineTo(19.5f, 5f); lineTo(19.5f, 19f); lineTo(4.5f, 19f); close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4.5f, 10f); lineTo(19.5f, 10f)
            moveTo(4.5f, 14.5f); lineTo(19.5f, 14.5f)
            moveTo(10f, 5f); lineTo(10f, 19f)
            moveTo(15f, 5f); lineTo(15f, 19f)
        }
    }.build()

    val DocumentJpg: ImageVector = ImageVector.Builder(
        name = "FluentDocumentJpg",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 3.5f); lineTo(14.5f, 3.5f); lineTo(19f, 8f); lineTo(19f, 20.5f); lineTo(6f, 20.5f); close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14.5f, 3.5f); lineTo(14.5f, 8f); lineTo(19f, 8f)
        }
        // Image landscape inside
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8.5f, 18f); lineTo(11f, 14.5f); lineTo(13f, 16.5f); lineTo(16.5f, 18f)
        }
    }.build()

    val CameraScan: ImageVector = ImageVector.Builder(
        name = "FluentCameraScan",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 8.5f); lineTo(7.5f, 8.5f); lineTo(9f, 6f); lineTo(15f, 6f); lineTo(16.5f, 8.5f); lineTo(20f, 8.5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        }
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(15.5f, 13.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13.5f)
            arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13.5f)
        }
    }.build()

    val IdCard: ImageVector = ImageVector.Builder(
        name = "FluentIdCard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
        }
        // Avatar circle
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f
        ) {
            moveTo(9f, 10f)
            arcTo(2f, 2f, 0f, true, true, 5f, 10f)
            arcTo(2f, 2f, 0f, true, true, 9f, 10f)
        }
        // Avatar bust
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(5f, 15.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 9f, 15.5f)
        }
        // Text lines
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.3f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 9.5f); lineTo(18f, 9.5f)
            moveTo(12f, 12.5f); lineTo(17f, 12.5f)
            moveTo(12f, 15.5f); lineTo(15f, 15.5f)
        }
    }.build()

    val FolderOpen: ImageVector = ImageVector.Builder(
        name = "FluentFolderOpen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8f); lineTo(20.5f, 8f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
        }
    }.build()

    val Printer: ImageVector = ImageVector.Builder(
        name = "FluentPrinter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Top paper
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 8f); lineTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 8f)
        }
        // Body
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 8f); lineTo(20f, 8f); lineTo(20f, 16f); lineTo(4f, 16f); close()
        }
        // Bottom paper tray
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 14f); lineTo(7f, 19.5f); lineTo(17f, 19.5f); lineTo(17f, 14f)
        }
    }.build()

    val MergePdf: ImageVector = ImageVector.Builder(
        name = "FluentMergePdf",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        }
        // Converging arrows to center bottom
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 10f); lineTo(7f, 14f); lineTo(12f, 16f)
            moveTo(17f, 10f); lineTo(17f, 14f); lineTo(12f, 16f)
            moveTo(12f, 16f); lineTo(12f, 20f)
        }
    }.build()

    val Compress: ImageVector = ImageVector.Builder(
        name = "FluentCompress",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Document
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 4f); lineTo(18.5f, 4f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
        }
        // Downward and upward compression arrows
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 7f); lineTo(12f, 11f); moveTo(10f, 9.5f); lineTo(12f, 11.5f); lineTo(14f, 9.5f)
            moveTo(12f, 17f); lineTo(12f, 13f); moveTo(10f, 14.5f); lineTo(12f, 12.5f); lineTo(14f, 14.5f)
        }
    }.build()

    val Doodle: ImageVector = ImageVector.Builder(
        name = "FluentDoodle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(18.5f, 4.5f); lineTo(19.5f, 5.5f); lineTo(9f, 16f); lineTo(5f, 17f); lineTo(6f, 13f); close()
            moveTo(14f, 7f); lineTo(17f, 10f)
            moveTo(4f, 21f); lineTo(20f, 21f)
        }
    }.build()

    val AddText: ImageVector = ImageVector.Builder(
        name = "FluentAddText",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Capital 'T'
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(5f, 6f); lineTo(17f, 6f)
            moveTo(11f, 6f); lineTo(11f, 19f)
            moveTo(9f, 19f); lineTo(13f, 19f)
        }
    }.build()

    val Signature: ImageVector = ImageVector.Builder(
        name = "FluentSignature",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Fountain pen nib
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13f, 5f); lineTo(18.5f, 10.5f); lineTo(8f, 21f); lineTo(3.5f, 21f); lineTo(3.5f, 16.5f); close()
            moveTo(7.5f, 17f); lineTo(10f, 19.5f)
        }
    }.build()

    val Lock: ImageVector = ImageVector.Builder(
        name = "FluentLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Shackle
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 10f); lineTo(8f, 7f)
            arcTo(4f, 4f, 0f, true, true, 16f, 7f)
            lineTo(16f, 10f)
        }
        // Body
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 10f); lineTo(18.5f, 10f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
        }
        // Keyhole
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 14f); lineTo(12f, 16.5f)
        }
    }.build()

    val Unlock: ImageVector = ImageVector.Builder(
        name = "FluentUnlock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Open Shackle
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 10f); lineTo(8f, 7f)
            arcTo(4f, 4f, 0f, true, true, 16f, 7f)
            lineTo(16f, 5f)
        }
        // Body
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 10f); lineTo(18.5f, 10f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
        }
        // Keyhole
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 14f); lineTo(12f, 16.5f)
        }
    }.build()

    val PhotoGallery: ImageVector = ImageVector.Builder(
        name = "FluentPhotoGallery",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 7f); lineTo(18f, 7f); lineTo(18f, 19f); lineTo(4f, 19f); close()
            moveTo(7.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 15.5f)
            moveTo(5f, 17f); lineTo(9.5f, 12.5f); lineTo(13f, 16f); lineTo(14.5f, 14.5f); lineTo(17f, 17f)
        }
    }.build()

    val PrecisionTuning: ImageVector = ImageVector.Builder(
        name = "FluentPrecisionTuning",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 7f); lineTo(20f, 7f); moveTo(8f, 5f); lineTo(8f, 9f)
            moveTo(4f, 12f); lineTo(20f, 12f); moveTo(15f, 10f); lineTo(15f, 14f)
            moveTo(4f, 17f); lineTo(20f, 17f); moveTo(10f, 15f); lineTo(10f, 19f)
        }
    }.build()

    val Crop: ImageVector = ImageVector.Builder(
        name = "FluentCrop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 3f); lineTo(7f, 17f); lineTo(21f, 17f)
            moveTo(17f, 21f); lineTo(17f, 7f); lineTo(3f, 7f)
        }
    }.build()

    val RotateClockwise: ImageVector = ImageVector.Builder(
        name = "FluentRotateClockwise",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, true, true, 16.5f, 6.5f)
            moveTo(16.5f, 3f); lineTo(16.5f, 7f); lineTo(20.5f, 7f)
        }
    }.build()

    val Delete: ImageVector = ImageVector.Builder(
        name = "FluentDelete",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5.5f, 8f); lineTo(6.5f, 19.5f); lineTo(17.5f, 19.5f); lineTo(18.5f, 8f)
            moveTo(4f, 8f); lineTo(20f, 8f); moveTo(9.5f, 8f); lineTo(10f, 5.5f); lineTo(14f, 5.5f); lineTo(14.5f, 8f)
            moveTo(10f, 11f); lineTo(10.2f, 16.5f)
            moveTo(14f, 11f); lineTo(13.8f, 16.5f)
        }
    }.build()

    val Torch: ImageVector = ImageVector.Builder(
        name = "FluentTorch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 4f); lineTo(16f, 4f); lineTo(14.5f, 9.5f); lineTo(14.5f, 19.5f); lineTo(9.5f, 19.5f); lineTo(9.5f, 9.5f); close()
            moveTo(12f, 11.5f); lineTo(12f, 14f)
        }
    }.build()

    val Sparkle: ImageVector = ImageVector.Builder(
        name = "FluentSparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3.5f); lineTo(14f, 9.5f); lineTo(20f, 11.5f); lineTo(14f, 13.5f); lineTo(12f, 19.5f); lineTo(10f, 13.5f); lineTo(4f, 11.5f); lineTo(10f, 9.5f); close()
        }
    }.build()
}
