package com.swiffle.app.ui.canvas.forms

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Enterprise AcroForm Form-Field Engine for Swiffle.
 * 
 * Safely leverages Android 15 (API 35+) native PDF form-filling capabilities
 * while preserving 100% backwards-compatibility and zero crashes on Android 8-14.
 */
enum class FormWidgetType {
    TEXT,
    CHECKBOX,
    RADIO,
    CHOICE,
    SIGNATURE,
    UNKNOWN
}

data class FormWidgetInfo(
    val pageIndex: Int,
    val widgetIndex: Int,
    val fieldName: String,
    val fieldType: FormWidgetType,
    val rect: RectF,
    val textValue: String = "",
    val isChecked: Boolean = false
)

object PdfFormEngine {
    private const val TAG = "PdfFormEngine"

    /**
     * Returns true if the host device runs Android 15 (API 35+) and supports native AcroForms.
     */
    val isFormFillingSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 35

    /**
     * Inspects a specific page for interactive form widgets on Android 15+.
     * Gracefully returns an empty list on Android < 35 with zero reflection overhead.
     */
    fun inspectPageFormWidgets(page: PdfRenderer.Page, pageIndex: Int): List<FormWidgetInfo> {
        if (!isFormFillingSupported) return emptyList()

        val widgets = mutableListOf<FormWidgetInfo>()
        try {
            // Android 15 introduced getFormWidgetInfos() on PdfRenderer.Page
            val getWidgetsMethod = page.javaClass.methods.firstOrNull { it.name == "getFormWidgetInfos" }
            if (getWidgetsMethod != null) {
                val resultList = getWidgetsMethod.invoke(page) as? List<*>
                resultList?.forEachIndexed { idx, rawWidget ->
                    if (rawWidget != null) {
                        val widgetClass = rawWidget.javaClass
                        val nameMethod = widgetClass.methods.firstOrNull { it.name == "getWidgetType" || it.name == "getName" }
                        val boundsMethod = widgetClass.methods.firstOrNull { it.name == "getWidgetRect" || it.name == "getBounds" }
                        val textMethod = widgetClass.methods.firstOrNull { it.name == "getTextValue" || it.name == "getValue" }

                        val fieldName = (nameMethod?.invoke(rawWidget) as? String) ?: "field_$idx"
                        val rect = (boundsMethod?.invoke(rawWidget) as? RectF) ?: RectF(0f, 0f, 0f, 0f)
                        val textValue = (textMethod?.invoke(rawWidget) as? String) ?: ""

                        widgets.add(
                            FormWidgetInfo(
                                pageIndex = pageIndex,
                                widgetIndex = idx,
                                fieldName = fieldName,
                                fieldType = FormWidgetType.TEXT,
                                rect = rect,
                                textValue = textValue
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Form widget extraction skipped or not present on page $pageIndex: ${e.message}")
        }
        return widgets
    }

    /**
     * Applies an updated value to an interactive form field widget on Android 15+.
     */
    fun applyWidgetValue(page: PdfRenderer.Page, widget: FormWidgetInfo, newValue: String): Boolean {
        if (!isFormFillingSupported) return false

        return try {
            val applyMethod = page.javaClass.methods.firstOrNull { 
                it.name == "applyFormWidgetText" || it.name == "setFormWidgetText" 
            }
            if (applyMethod != null) {
                applyMethod.invoke(page, widget.widgetIndex, newValue)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying form value for ${widget.fieldName}: ${e.message}")
            false
        }
    }
}

/**
 * Observable UI state holder for interactive PDF form widgets.
 */
class PdfFormState {
    val pageWidgets = mutableStateMapOf<Int, List<FormWidgetInfo>>()
    var activeEditingWidget by mutableStateOf<FormWidgetInfo?>(null)

    fun setWidgetsForPage(pageIndex: Int, widgets: List<FormWidgetInfo>) {
        if (widgets.isNotEmpty()) {
            pageWidgets[pageIndex] = widgets
        } else {
            pageWidgets.remove(pageIndex)
        }
    }

    fun hasFormFields(pageIndex: Int): Boolean = pageWidgets[pageIndex]?.isNotEmpty() == true
}
