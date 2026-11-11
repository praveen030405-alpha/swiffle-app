package com.swiffle.app.ui.canvas.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.UUID

/**
 * Type of editable element on a PDF page.
 */
enum class EditObjectType {
    TEXT,
    IMAGE,
    SHAPE
}

/**
 * Handle on the transform bounding box gizmo.
 */
enum class GizmoHandle {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    ROTATE,
    BODY // Translating the whole object
}

/**
 * Realism & Optical Ink Configuration:
 * Replaces synthetic vector edges with imperceptible, scanned/printed optical fidelity.
 */
data class RealismConfig(
    val edgeSofteningPx: Float = 0.45f,      // Sub-pixel point-spread blur matching print anti-aliasing (0.0f to 1.5f)
    val paperGrainIntensity: Float = 0.08f,  // Micro-luminance noise injected into ink to blend with paper grain (0.0f to 0.35f)
    val inkDilationPx: Float = 0.0f,         // Micro-thickness stroke expansion (-0.5f to +1.0f) for exact weight matching
    val preserveBackgroundTexture: Boolean = true // Whether to inpaint surrounding paper fibers instead of flat white
)

/**
 * Typography & Styling configuration for editable text.
 */
data class TypographyConfig(
    val fontName: String = "Roboto",
    val fontFamily: FontFamily = FontFamily.Default,
    val fontSizePt: Float = 12.0f,
    val fontWeight: FontWeight = FontWeight.Normal,
    val fontWeightValue: Int = 400,          // 100 to 900
    val letterSpacingPt: Float = 0.0f,       // Tracking / Kerning micro-adjustments
    val lineSpacingMultiplier: Float = 1.15f,
    val textColor: Color = Color(0xFF1E1E1E), // Natural printed dark charcoal instead of harsh #000000
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isSourceMatched: Boolean = false     // True if auto-matched from the original PDF text descriptor
)

/**
 * Inpainted patch covering the vacated area of an edited or moved element.
 * Generated dynamically by sampling the surrounding paper texture and gradient.
 */
data class InpaintedPatch(
    val id: String = UUID.randomUUID().toString(),
    val pageNum: Int,
    val pdfRect: RectF,                      // Bounding box in PDF point space
    val patchBitmap: Bitmap? = null,         // Inpainted paper texture tile
    val averageColor: Color = Color.White
)

/**
 * An individual editable object on a PDF page (Text line, Paragraph, or Image).
 */
data class EditableObject(
    val id: String = UUID.randomUUID().toString(),
    val pageNum: Int,                        // 1-based page index
    val type: EditObjectType,
    val originalPdfRect: RectF,              // Original position in PDF point space
    var currentPdfRect: RectF,               // Current (possibly moved/resized) position in PDF points
    var text: String = "",                   // Current text content (if TEXT)
    var originalText: String = "",           // Original text content before editing
    var typography: TypographyConfig = TypographyConfig(),
    var realism: RealismConfig = RealismConfig(),
    var rotationDegrees: Float = 0.0f,
    var imageBitmap: Bitmap? = null,         // Source bitmap (if IMAGE)
    var isSelected: Boolean = false,
    var isEditingText: Boolean = false,      // Currently receiving keyboard input
    var isMovedOrModified: Boolean = false   // True if position, content, or style differs from original
) {
    fun createCopy(): EditableObject {
        return this.copy(
            originalPdfRect = RectF(originalPdfRect),
            currentPdfRect = RectF(currentPdfRect),
            typography = typography.copy(),
            realism = realism.copy()
        )
    }
}

/**
 * Action command for Undo / Redo history.
 */
sealed class EditorHistoryAction {
    data class ModifyObject(
        val before: EditableObject,
        val after: EditableObject
    ) : EditorHistoryAction()

    data class AddObject(
        val obj: EditableObject
    ) : EditorHistoryAction()

    data class DeleteObject(
        val obj: EditableObject
    ) : EditorHistoryAction()
}

