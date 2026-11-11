
package com.swiffle.app.ui.canvas

import kotlin.math.abs

object TableExtractor {
    fun extractMarkdownTable(rects: List<CharRect>): String {
        if (rects.isEmpty()) return ""
        
        // 1. Group rects into rows based on Y-coordinate clustering (1D K-Means approx via thresholding)
        // Sort rects by Y descending (since PDF Y is up)
        val sortedByY = rects.sortedByDescending { it.y }
        val rows = mutableListOf<MutableList<CharRect>>()
        
        var currentY = sortedByY.first().y
        val thresholdY = 5.0f // Threshold for considering chars in the same row
        var currentRow = mutableListOf<CharRect>()
        
        for (rect in sortedByY) {
            if (abs(rect.y - currentY) > thresholdY) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentY = rect.y
            }
            currentRow.add(rect)
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        
        // 2. Discover column boundaries (X-coordinates)
        // Group characters into words first (characters separated by small gap <= 5f belong to same word)
        val wordStarts = mutableListOf<Float>()
        for (row in rows) {
            row.sortBy { it.x }
            var lastRight = -100f
            for (rect in row) {
                if (rect.x - lastRight > 8.0f) {
                    wordStarts.add(rect.x)
                }
                lastRight = rect.x + rect.w
            }
        }
        wordStarts.sort()

        val cols = mutableListOf<Float>()
        if (wordStarts.isNotEmpty()) {
            var currentX = wordStarts.first()
            cols.add(currentX)
            val thresholdX = 25.0f // Columns in tables have significant horizontal separation
            for (x in wordStarts) {
                if (abs(x - currentX) > thresholdX) {
                    cols.add(x)
                    currentX = x
                }
            }
        }
        
        // 3. Build Markdown Table
        val sb = java.lang.StringBuilder()
        for ((rowIndex, row) in rows.withIndex()) {
            row.sortBy { it.x }
            val cells = mutableListOf<String>()
            var colIdx = 0
            
            var cellText = java.lang.StringBuilder()
            for (rect in row) {
                // Advance colIdx if rect.x is far enough
                while (colIdx < cols.size - 1 && rect.x > cols[colIdx + 1] - 5.0f) {
                    cells.add(cellText.toString().trim())
                    cellText = java.lang.StringBuilder()
                    colIdx++
                }
                cellText.append(rect.char)
            }
            cells.add(cellText.toString().trim())
            
            // Pad remaining cols
            while (cells.size < cols.size) {
                cells.add("")
            }
            
            sb.append("| ").append(cells.joinToString(" | ")).append(" |\n")
            
            // Add markdown separator after first row
            if (rowIndex == 0) {
                sb.append("|")
                for (i in cols.indices) sb.append("---|")
                sb.append("\n")
            }
        }
        
        return sb.toString()
    }
}

