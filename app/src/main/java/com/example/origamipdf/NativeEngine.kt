package com.example.origamipdf

/**
 * NativeEngine — The Kotlin-side JNI bridge to the Origami Rust Core.
 * 
 * This object loads the compiled `liborigami_core.so` native library
 * and exposes its functions to the rest of the Android app.
 */
object NativeEngine {

    private var isLoaded = false

    /**
     * Loads the native library. Call this once on app startup.
     * Returns true if successful, false if the library couldn't be loaded.
     */
    fun load(): Boolean {
        return try {
            System.loadLibrary("origami_core")
            isLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("NativeEngine", "Failed to load liborigami_core.so: ${e.message}")
            isLoaded = false
            false
        }
    }

    fun isReady(): Boolean = isLoaded

    // --- JNI Endpoints (implemented in Rust) ---

    /** Initialize the Rust core and return a greeting string. */
    external fun initEngine(input: String): String

    /** Open a PDF file by path and return a native pointer handle. Returns 0 on failure. */
    external fun openPdf(path: String): Long

    /** Get the page count from raw PDF bytes. Returns -1 on failure. */
    external fun getPageCount(pdfBytes: ByteArray): Int

    /** Free a previously opened PDF document handle. */
    external fun closePdf(ptr: Long)

    /** Get the file size of an opened PDF document. Returns -1 on failure. */
    external fun getFileSize(ptr: Long): Long
    
    /** Extract raw text from a specific page using the Rust lopdf parser. */
    external fun extractPageText(pdfBytes: ByteArray, pageNum: Int): String
    
    /** Extract raw text from a specific page using the opened zero-copy PdfDocument pointer. */
    external fun extractPageTextFromPtr(ptr: Long, pageNum: Int): String
    
    /** Extract structured JSON containing characters and bounding boxes. */
    external fun extractPageGeometryFromPtr(ptr: Long, pageNum: Int): String
    
    /** Search entire document and return matching text bounding boxes as JSON array. */
    external fun searchDocumentFromPtr(ptr: Long, query: String): String
    
    /** Search a specific page and return matching text bounding boxes as JSON array. */
    external fun searchPageFromPtr(ptr: Long, pageNum: Int, query: String): String
    
    // --- TTS Engine Endpoints ---
    
    /** Initialize the TTS engine with the path to the ONNX model file. Returns pointer handle. */
    external fun initTts(modelPath: String): Long
    
    /** Feed text to the TTS engine and get raw 16-bit PCM audio bytes back. */
    external fun synthesizeTts(ptr: Long, text: String): ByteArray
}
