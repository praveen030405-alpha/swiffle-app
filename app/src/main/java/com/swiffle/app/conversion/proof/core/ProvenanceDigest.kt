package com.swiffle.app.conversion.proof.core

import java.security.MessageDigest

internal class ProvenanceDigest {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val alphabet = "0123456789abcdef"
    fun hash(value: String): String {
        val bytes = digest.digest(value.toByteArray())
        val result = CharArray(bytes.size * 2)
        for ((i, byte) in bytes.withIndex()) {
            val n = byte.toInt() and 255
            result[2*i] = alphabet[n ushr 4]
            result[2*i + 1] = alphabet[n and 15]
        }
        return String(result)
    }
}
