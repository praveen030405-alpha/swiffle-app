package com.swiffle.app.conversion.proof.excel

import com.swiffle.app.conversion.proof.core.*
import java.io.File
import java.io.PushbackReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

// CSV has no declared numeric/date schema. Preserve leading zeros and date spellings as text.
internal fun csvRows(source: File): Sequence<List<Cell>> = sequence {
    PushbackReader(InputStreamReader(source.inputStream(),StandardCharsets.UTF_8.newDecoder()).buffered(),2).use { input ->
        val first = input.read()
        if (first >= 0 && first != 0xfeff) input.unread(first)
        var rowNumber = 1L
        var row = mutableListOf<Cell>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var inQuotes = false
        fun finish() {
            var index = row.size + 1
            var col = ""
            while (index > 0) { index--; col = ('A'.code + index % 26).toChar() + col; index /= 26 }
            val address = col + rowNumber
            val raw = field.toString()
            val empty = !quoted && raw.isEmpty()
            val hash = MessageDigest.getInstance("SHA-256").digest((address + ":" + quoted + ":" + raw).toByteArray())
                .joinToString("") { "%02x".format(it) }
            row.add(Cell(address,"CSV",address,"records","",col,if(empty) null else raw,if(empty) null else raw,
                if(empty) "∅" else raw,if(empty) CellType.EMPTY else CellType.TEXT,hash))
            field.setLength(0); quoted = false; closed = false
        }
        while (true) {
            val code = input.read()
            if (code < 0) {
                require(!inQuotes) { "Unclosed quote at CSV row " + rowNumber }
                if (quoted || field.isNotEmpty() || row.isNotEmpty()) { finish(); yield(row) }
                break
            }
            val c = code.toChar()
            when {
                inQuotes && c == '"' -> {
                    val after = input.read()
                    if (after == '"'.code) field.append('"') else {
                        inQuotes = false; closed = true
                        if (after >= 0) input.unread(after)
                    }
                }
                inQuotes -> field.append(c)
                c == ',' -> finish()
                c == '\r' || c == '\n' -> {
                    if (c == '\r') { val next = input.read(); if (next >= 0 && next != '\n'.code) input.unread(next) }
                    finish(); yield(row); row = mutableListOf(); rowNumber++
                }
                !closed && c == '"' && field.isEmpty() -> { quoted = true; inQuotes = true }
                closed || c == '"' -> error("Invalid quote at CSV row " + rowNumber)
                else -> field.append(c)
            }
        }
    }
}
