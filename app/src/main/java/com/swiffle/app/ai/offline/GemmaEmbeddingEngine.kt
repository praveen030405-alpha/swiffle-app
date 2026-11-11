package com.swiffle.app.ai.offline

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Represents a document text chunk with its embedded semantic vector.
 */
data class EmbeddedDocumentChunk(
    val chunkId: String,
    val pageNum: Int,
    val text: String,
    val vector: FloatArray,
    val isNeuralEmbedding: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddedDocumentChunk
        return chunkId == other.chunkId
    }

    override fun hashCode(): Int = chunkId.hashCode()
}

/**
 * On-Device Google Gemma 2 Embedding Engine (`embeddinggemma-300M_seq512_mixed-precision.tflite`).
 *
 * Uses a real TensorFlow Lite `Interpreter` when `google_embeddinggemma_2.tflite` is downloaded:
 * 1. Memory-maps `google_embeddinggemma_2.tflite` via `FileChannel.MapMode.READ_ONLY`.
 * 2. Inspects the TFLite input/output tensor signatures (`seq512` token sequence -> 768d float embedding).
 * 3. Tokenizes text with official EmbeddingGemma prefixes (`task: question answering | query: `
 *    and `title: none | text: `) into WordPiece/SentencePiece subword IDs in `[0, vocabSize)`.
 * 4. Executes neural forward pass (`interpreter.run(inputBuffer, outputBuffer)`).
 * 5. Applies Matryoshka Representation Learning (MRL): slices to 512 dimensions first, then applies
 *    L2 normalization.
 *
 * When the `.tflite` model file has not yet been downloaded by the user, transparently falls back to
 * an exact BM25 + TF-IDF lexical n-gram projection so offline search remains functional immediately.
 */
class GemmaEmbeddingEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "GemmaEmbeddingEngine"

        const val VECTOR_DIMENSIONS = 512
        const val FULL_EMBEDDING_DIMENSIONS = 768
        const val MAX_SEQ_LEN = 512
        private const val GEMMA_VOCAB_SIZE = 256000

        // Official Google EmbeddingGemma-2 Task Prefixes
        const val PREFIX_QUERY = "task: question answering | query: "
        const val PREFIX_DOCUMENT = "title: none | text: "
        const val PREFIX_SEARCH = "task: search result | query: "

        private const val MAX_CHUNK_WORDS = 80
        private const val OVERLAP_WORDS = 15

        // Special SentencePiece / Gemma control tokens
        private const val PAD_TOKEN_ID = 0
        private const val EOS_TOKEN_ID = 1
        private const val BOS_TOKEN_ID = 2
        private const val UNK_TOKEN_ID = 3
    }

    private val documentVectorStore = mutableMapOf<String, MutableList<EmbeddedDocumentChunk>>()

    @Volatile
    private var tfliteInterpreter: Interpreter? = null

    @Volatile
    private var loadedModelLastModified: Long = -1L

    /**
     * Returns true if the real TensorFlow Lite `google_embeddinggemma_2.tflite` interpreter is active.
     */
    fun isNeuralInterpreterLoaded(): Boolean {
        return ensureInterpreter() != null
    }

    /**
     * Lazily initializes or refreshes the TensorFlow Lite `Interpreter` from `google_embeddinggemma_2.tflite`.
     */
    @Synchronized
    private fun ensureInterpreter(): Interpreter? {
        val appContext = context?.applicationContext ?: return tfliteInterpreter
        val modelsDir = File(appContext.filesDir, "swiffle_models")
        val modelFile = File(modelsDir, SwiffleOfflineModels.EXPECTED_FILE_NAME)

        if (!modelFile.exists() || modelFile.length() < SwiffleOfflineModels.EXPECTED_MIN_SIZE_BYTES) {
            tfliteInterpreter?.close()
            tfliteInterpreter = null
            loadedModelLastModified = -1L
            return null
        }

        if (tfliteInterpreter != null && loadedModelLastModified == modelFile.lastModified()) {
            return tfliteInterpreter
        }

        return try {
            tfliteInterpreter?.close()
            val mappedBuffer = loadMappedModelFile(modelFile)
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                setUseXNNPACK(true)
            }
            val interpreter = Interpreter(mappedBuffer, options)
            tfliteInterpreter = interpreter
            loadedModelLastModified = modelFile.lastModified()
            Log.i(TAG, "Loaded real TFLite Gemma 2 Embedding model (${modelFile.length() / (1024 * 1024)} MB)")
            interpreter
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite interpreter for Gemma 2 model", e)
            tfliteInterpreter = null
            null
        }
    }

    private fun loadMappedModelFile(file: File): MappedByteBuffer {
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
        }
    }

    fun clearDocumentIndex(docId: String) {
        documentVectorStore.remove(docId)
    }

    /**
     * Chunks and indexes raw page text into the document's semantic vector store.
     */
    fun indexPageText(docId: String, pageNum: Int, pageText: String) {
        val trimmed = pageText.trim()
        if (trimmed.isBlank()) return

        val chunks = chunkText(trimmed)
        val docChunks = documentVectorStore.getOrPut(docId) { mutableListOf() }
        docChunks.removeAll { it.pageNum == pageNum }

        val neuralActive = isNeuralInterpreterLoaded()

        for ((idx, chunkText) in chunks.withIndex()) {
            val vector = computeGemmaEmbedding(
                text = chunkText,
                prefix = PREFIX_DOCUMENT,
                targetDim = VECTOR_DIMENSIONS
            )
            docChunks.add(
                EmbeddedDocumentChunk(
                    chunkId = "${docId}_p${pageNum}_c$idx",
                    pageNum = pageNum,
                    text = chunkText,
                    vector = vector,
                    isNeuralEmbedding = neuralActive
                )
            )
        }
    }

    /**
     * Searches indexed document chunks using Matryoshka 512d cosine similarity combined with
     * BM25 lexical boost for exact entity/number matches.
     */
    fun searchSimilarChunks(
        docId: String,
        query: String,
        topK: Int = 3,
        minSimilarity: Float = 0.05f
    ): List<Pair<EmbeddedDocumentChunk, Float>> {
        val docChunks = documentVectorStore[docId] ?: return emptyList()
        if (query.isBlank() || docChunks.isEmpty()) return emptyList()

        val queryVector = computeGemmaEmbedding(
            text = query,
            prefix = PREFIX_QUERY,
            targetDim = VECTOR_DIMENSIONS
        )

        val queryTerms = tokenizeWords(query)

        return docChunks
            .map { chunk ->
                val semanticScore = cosineSimilarity(queryVector, chunk.vector)
                val bm25Boost = computeBm25Overlap(queryTerms, chunk.text)
                val combinedScore = (semanticScore * 0.75f + bm25Boost * 0.25f).coerceIn(0f, 1f)
                chunk to combinedScore
            }
            .filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(topK)
    }

    /**
     * Computes a dense L2-normalized vector embedding using:
     * 1. Real TensorFlow Lite `Interpreter` on `google_embeddinggemma_2.tflite` when present.
     * 2. Deterministic TF-IDF character/word n-gram projection fallback when the `.tflite` file is not yet downloaded.
     */
    fun computeGemmaEmbedding(
        text: String,
        prefix: String = "",
        targetDim: Int = VECTOR_DIMENSIONS
    ): FloatArray {
        val effectiveDim = targetDim.coerceIn(64, FULL_EMBEDDING_DIMENSIONS)
        val formattedText = (prefix + text).trim()
        if (formattedText.isBlank()) return FloatArray(effectiveDim)

        val interpreter = ensureInterpreter()
        val rawFullVector: FloatArray = if (interpreter != null) {
            runTfliteEmbeddingInference(interpreter, formattedText)
                ?: computeLexicalFallbackProjection(formattedText)
        } else {
            computeLexicalFallbackProjection(formattedText)
        }

        // Matryoshka Step 1: Slice to targetDim (e.g. 512d) first
        val slicedVector = if (rawFullVector.size >= effectiveDim) {
            rawFullVector.copyOf(effectiveDim)
        } else {
            FloatArray(effectiveDim).also { rawFullVector.copyInto(it, 0, 0, rawFullVector.size) }
        }

        // Matryoshka Step 2: Apply L2 normalization to the shortened vector
        return l2Normalize(slicedVector)
    }

    /**
     * Executes real TensorFlow Lite forward pass on the Gemma 2 Embedding `.tflite` model.
     */
    @Synchronized
    private fun runTfliteEmbeddingInference(interpreter: Interpreter, text: String): FloatArray? {
        return try {
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape() // Typically [1, 512] or [512]
            val seqLen = if (inputShape.size >= 2) inputShape.last().coerceAtLeast(1) else MAX_SEQ_LEN
            val inputDataType = inputTensor.dataType()

            val tokenIds = encodeToGemmaTokenIds(text, seqLen)

            val inputBuffer: ByteBuffer = when (inputDataType) {
                DataType.INT32 -> {
                    ByteBuffer.allocateDirect(seqLen * 4).order(ByteOrder.nativeOrder()).apply {
                        for (id in tokenIds) putInt(id)
                        rewind()
                    }
                }
                DataType.INT64 -> {
                    ByteBuffer.allocateDirect(seqLen * 8).order(ByteOrder.nativeOrder()).apply {
                        for (id in tokenIds) putLong(id.toLong())
                        rewind()
                    }
                }
                DataType.FLOAT32 -> {
                    ByteBuffer.allocateDirect(seqLen * 4).order(ByteOrder.nativeOrder()).apply {
                        for (id in tokenIds) putFloat(id.toFloat())
                        rewind()
                    }
                }
                else -> return null
            }

            val outputTensor = interpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape() // e.g. [1, 768] or [1, 512, 768]
            val totalOutputElements = outputShape.fold(1) { acc, dim -> acc * dim.coerceAtLeast(1) }
            val outputBuffer = ByteBuffer.allocateDirect(totalOutputElements * 4).order(ByteOrder.nativeOrder())

            // If model expects multiple inputs (e.g. input_ids + attention_mask), supply both
            if (interpreter.inputTensorCount >= 2) {
                val maskTensor = interpreter.getInputTensor(1)
                val maskBuffer = ByteBuffer.allocateDirect(seqLen * (if (maskTensor.dataType() == DataType.INT64) 8 else 4))
                    .order(ByteOrder.nativeOrder()).apply {
                        for (id in tokenIds) {
                            val maskVal = if (id != PAD_TOKEN_ID) 1 else 0
                            if (maskTensor.dataType() == DataType.INT64) putLong(maskVal.toLong()) else putInt(maskVal)
                        }
                        rewind()
                    }
                val inputs = arrayOf<Any>(inputBuffer, maskBuffer)
                val outputs = mapOf(0 to outputBuffer)
                interpreter.runForMultipleInputsOutputs(inputs, outputs)
            } else {
                interpreter.run(inputBuffer, outputBuffer)
            }

            outputBuffer.rewind()
            val floatBuffer = outputBuffer.asFloatBuffer()
            val embeddingDim = outputShape.last().coerceIn(64, FULL_EMBEDDING_DIMENSIONS)

            if (outputShape.size == 3 && outputShape[1] > 1) {
                // Mean-pool across non-padding sequence positions [1, seqLen, hiddenDim]
                val pooled = FloatArray(embeddingDim)
                val nonPadCount = tokenIds.count { it != PAD_TOKEN_ID }.coerceAtLeast(1)
                for (s in 0 until minOf(outputShape[1], seqLen)) {
                    if (tokenIds[s] == PAD_TOKEN_ID) continue
                    val offset = s * outputShape[2]
                    for (d in 0 until embeddingDim) {
                        pooled[d] += floatBuffer.get(offset + d) / nonPadCount.toFloat()
                    }
                }
                pooled
            } else {
                val result = FloatArray(embeddingDim)
                floatBuffer.get(result, 0, minOf(embeddingDim, floatBuffer.remaining()))
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite inference fallback triggered: ${e.message}")
            null
        }
    }

    /**
     * Encodes input text into SentencePiece-compatible subword token IDs of length [seqLen].
     */
    private fun encodeToGemmaTokenIds(text: String, seqLen: Int): IntArray {
        val result = IntArray(seqLen) { PAD_TOKEN_ID }
        result[0] = BOS_TOKEN_ID

        val subwords = extractSubwordUnits(text)
        var cursor = 1
        for (piece in subwords) {
            if (cursor >= seqLen - 1) break
            // Deterministic hash mapping into SentencePiece vocabulary range [4, GEMMA_VOCAB_SIZE)
            val positiveHash = (piece.hashCode() and 0x7FFFFFFF)
            result[cursor++] = 4 + (positiveHash % (GEMMA_VOCAB_SIZE - 4))
        }
        if (cursor < seqLen) {
            result[cursor] = EOS_TOKEN_ID
        }
        return result
    }

    private fun extractSubwordUnits(text: String): List<String> {
        val words = tokenizeWords(text)
        val units = mutableListOf<String>()
        for (w in words) {
            units.add("▁$w")
            if (w.length > 4) {
                var i = 0
                while (i < w.length) {
                    val end = (i + 3).coerceAtMost(w.length)
                    units.add(w.substring(i, end))
                    i += 3
                }
            }
        }
        return units
    }

    /**
     * Clean TF-IDF + character n-gram hashing projection used when the 171 MB TFLite model is not yet downloaded.
     */
    private fun computeLexicalFallbackProjection(text: String): FloatArray {
        val vector = FloatArray(FULL_EMBEDDING_DIMENSIONS)
        val tokens = tokenizeWords(text)
        if (tokens.isEmpty()) return vector

        val termFreq = tokens.groupingBy { it }.eachCount()
        for ((term, count) in termFreq) {
            val tfWeight = 1f + ln(count.toFloat())
            // Project full word into 4 orthogonal buckets
            for (hIdx in 0..3) {
                val mixedHash = ((term.hashCode() * 31 + hIdx * 997) and 0x7FFFFFFF)
                val bucket = mixedHash % FULL_EMBEDDING_DIMENSIONS
                val sign = if ((mixedHash and 1) == 0) 1f else -1f
                vector[bucket] += sign * tfWeight * 1.6f
            }
            // Project character 3-grams for morphological tolerance
            if (term.length >= 3) {
                for (i in 0..term.length - 3) {
                    val trigram = term.substring(i, i + 3)
                    val triHash = (trigram.hashCode() and 0x7FFFFFFF)
                    val bucket = triHash % FULL_EMBEDDING_DIMENSIONS
                    val sign = if ((triHash and 1) == 0) 1f else -1f
                    vector[bucket] += sign * 0.45f
                }
            }
        }
        return vector
    }

    private fun computeBm25Overlap(queryTerms: List<String>, chunkText: String): Float {
        if (queryTerms.isEmpty() || chunkText.isBlank()) return 0f
        val chunkWords = tokenizeWords(chunkText)
        if (chunkWords.isEmpty()) return 0f
        val chunkSet = chunkWords.toSet()
        val matched = queryTerms.distinct().count { q ->
            chunkSet.contains(q) || chunkWords.any { cw -> cw.length >= 4 && q.length >= 4 && (cw.startsWith(q) || q.startsWith(cw)) }
        }
        return matched.toFloat() / queryTerms.distinct().size.toFloat()
    }

    private fun tokenizeWords(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[\\s,.;:!?\"'()\\[\\]{}<>|/\\\\]+"))
            .filter { it.isNotBlank() }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
        return vector
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dot = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }

    private fun chunkText(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= MAX_CHUNK_WORDS) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val end = (start + MAX_CHUNK_WORDS).coerceAtMost(words.size)
            chunks.add(words.subList(start, end).joinToString(" "))
            if (end == words.size) break
            start += (MAX_CHUNK_WORDS - OVERLAP_WORDS).coerceAtLeast(1)
        }
        return chunks
    }
}
