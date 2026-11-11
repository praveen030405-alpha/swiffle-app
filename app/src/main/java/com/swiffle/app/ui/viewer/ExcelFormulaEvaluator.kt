package com.swiffle.app.ui.viewer

import java.util.Locale
import java.util.regex.Pattern

/**
 * Lightweight, high-performance in-memory spreadsheet formula evaluator.
 * Evaluates Excel expressions such as:
 *  - `=SUM(A1:A10)`
 *  - `=AVERAGE(B2:B8)`
 *  - `=MIN(C1:C5)`
 *  - `=MAX(D1:D5)`
 *  - `=COUNT(E1:E20)`
 *  - `=A1+B1` or `=A1*C2`
 */
object ExcelFormulaEvaluator {

    private val CELL_REF_REGEX = Pattern.compile("([A-Za-z]+)([0-9]+)")
    private val RANGE_REGEX = Pattern.compile("([A-Za-z]+[0-9]+):([A-Za-z]+[0-9]+)")

    fun evaluateCell(rawText: String, sheet: MutableExcelSheet): String {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("=")) {
            return rawText
        }

        return try {
            val expr = trimmed.substring(1).trim()
            evalExpression(expr, sheet)
        } catch (_: Exception) {
            "#VALUE!"
        }
    }

    private fun evalExpression(expr: String, sheet: MutableExcelSheet): String {
        val upper = expr.uppercase(Locale.ROOT)

        // Function: SUM(range)
        if (upper.startsWith("SUM(") && upper.endsWith(")")) {
            val inner = upper.substring(4, upper.length - 1).trim()
            val values = resolveRangeOrArgs(inner, sheet)
            val sum = values.sum()
            return formatNumber(sum)
        }

        // Function: AVERAGE(range)
        if (upper.startsWith("AVERAGE(") && upper.endsWith(")")) {
            val inner = upper.substring(8, upper.length - 1).trim()
            val values = resolveRangeOrArgs(inner, sheet)
            if (values.isEmpty()) return "#DIV/0!"
            val avg = values.average()
            return formatNumber(avg)
        }

        // Function: MIN(range)
        if (upper.startsWith("MIN(") && upper.endsWith(")")) {
            val inner = upper.substring(4, upper.length - 1).trim()
            val values = resolveRangeOrArgs(inner, sheet)
            if (values.isEmpty()) return "0"
            return formatNumber(values.minOrNull() ?: 0.0)
        }

        // Function: MAX(range)
        if (upper.startsWith("MAX(") && upper.endsWith(")")) {
            val inner = upper.substring(4, upper.length - 1).trim()
            val values = resolveRangeOrArgs(inner, sheet)
            if (values.isEmpty()) return "0"
            return formatNumber(values.maxOrNull() ?: 0.0)
        }

        // Function: COUNT(range)
        if (upper.startsWith("COUNT(") && upper.endsWith(")")) {
            val inner = upper.substring(6, upper.length - 1).trim()
            val values = resolveRangeOrArgs(inner, sheet)
            return values.size.toString()
        }

        // Simple arithmetic (+, -, *, /) with cell references e.g. A1 + B2
        if (upper.contains("+") || upper.contains("-") || upper.contains("*") || upper.contains("/")) {
            return evalArithmetic(upper, sheet)
        }

        // Direct cell reference e.g. =A1
        val cellMatcher = CELL_REF_REGEX.matcher(upper)
        if (cellMatcher.matches()) {
            val valStr = getCellValue(upper, sheet)
            return valStr
        }

        return expr
    }

    private fun resolveRangeOrArgs(argStr: String, sheet: MutableExcelSheet): List<Double> {
        val list = mutableListOf<Double>()
        val parts = argStr.split(",")

        for (part in parts) {
            val p = part.trim()
            val rangeMatcher = RANGE_REGEX.matcher(p)
            if (rangeMatcher.matches()) {
                val startRef = rangeMatcher.group(1) ?: continue
                val endRef = rangeMatcher.group(2) ?: continue
                val startCoord = parseCellCoord(startRef) ?: continue
                val endCoord = parseCellCoord(endRef) ?: continue

                val minCol = minOf(startCoord.col, endCoord.col)
                val maxCol = maxOf(startCoord.col, endCoord.col)
                val minRow = minOf(startCoord.row, endCoord.row)
                val maxRow = maxOf(startCoord.row, endCoord.row)

                for (r in minRow..maxRow) {
                    val rowData = sheet.rows.getOrNull(r) ?: continue
                    for (c in minCol..maxCol) {
                        val cellStr = rowData.getOrNull(c) ?: continue
                        val cleanNum = cellStr.replace(",", "").replace("$", "").trim()
                        cleanNum.toDoubleOrNull()?.let { list.add(it) }
                    }
                }
            } else {
                // Single cell or constant
                val singleCoord = parseCellCoord(p)
                if (singleCoord != null) {
                    val cellStr = sheet.rows.getOrNull(singleCoord.row)?.getOrNull(singleCoord.col) ?: ""
                    val cleanNum = cellStr.replace(",", "").replace("$", "").trim()
                    cleanNum.toDoubleOrNull()?.let { list.add(it) }
                } else {
                    p.toDoubleOrNull()?.let { list.add(it) }
                }
            }
        }
        return list
    }

    private fun evalArithmetic(expr: String, sheet: MutableExcelSheet): String {
        // Replace all cell tokens with numeric values
        var substituted = expr
        val matcher = CELL_REF_REGEX.matcher(expr)
        val sb = StringBuffer()
        while (matcher.find()) {
            val ref = matcher.group(0) ?: ""
            val raw = getCellValue(ref, sheet)
            val num = raw.replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: 0.0
            matcher.appendReplacement(sb, num.toString())
        }
        matcher.appendTail(sb)
        substituted = sb.toString()

        // Simple two-operand evaluator
        val ops = listOf("+", "-", "*", "/")
        for (op in ops) {
            if (substituted.contains(op)) {
                val parts = substituted.split(op, limit = 2)
                if (parts.size == 2) {
                    val left = parts[0].trim().toDoubleOrNull() ?: 0.0
                    val right = parts[1].trim().toDoubleOrNull() ?: 0.0
                    val res = when (op) {
                        "+" -> left + right
                        "-" -> left - right
                        "*" -> left * right
                        "/" -> if (right != 0.0) left / right else return "#DIV/0!"
                        else -> 0.0
                    }
                    return formatNumber(res)
                }
            }
        }
        return substituted
    }

    private fun getCellValue(ref: String, sheet: MutableExcelSheet): String {
        val coord = parseCellCoord(ref) ?: return ""
        return sheet.rows.getOrNull(coord.row)?.getOrNull(coord.col) ?: ""
    }

    private fun parseCellCoord(ref: String): CellCoord? {
        val m = CELL_REF_REGEX.matcher(ref)
        if (!m.matches()) return null
        val colLetters = m.group(1)?.uppercase(Locale.ROOT) ?: return null
        val rowNumber = m.group(2)?.toIntOrNull() ?: return null

        var colIdx = 0
        for (ch in colLetters) {
            colIdx = colIdx * 26 + (ch - 'A' + 1)
        }
        colIdx -= 1

        val rowIdx = rowNumber - 1
        return CellCoord(row = rowIdx, col = colIdx)
    }

    private fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0) {
            num.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", num)
        }
    }

    private data class CellCoord(val row: Int, val col: Int)
}

