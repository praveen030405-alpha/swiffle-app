package com.example.origamipdf

/**
 * JNI Bridge for the precompiled liborigami_core.so native library.
 * Package name must remain com.example.origamipdf to match exported JNI symbols.
 */
object NativeEngine {
    private var isLoaded = false

    @Synchronized
    fun load(): Boolean {
        if (isLoaded) return true
        return try {
            System.loadLibrary("origami_core")
            isLoaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    external fun initEngine(platform: String): String
    external fun openPdf(path: String): Long
    external fun closePdf(ptr: Long)
    external fun extractPageTextFromPtr(ptr: Long, pageNum: Int): String
    external fun extractPageGeometryFromPtr(ptr: Long, pageNum: Int): String
    external fun searchPageFromPtr(ptr: Long, pageNum: Int, query: String): String
}
