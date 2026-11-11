package com.swiffle.app.ai.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaEmbeddingEngineTest {

    private val engine = GemmaEmbeddingEngine()

    @Test
    fun testVectorNormalization() {
        val vector = engine.computeGemmaEmbedding("Swiffle intelligence with Google Gemma embeddings")
        assertEquals(GemmaEmbeddingEngine.VECTOR_DIMENSIONS, vector.size)

        var sumSq = 0f
        for (v in vector) {
            sumSq += v * v
        }
        val norm = kotlin.math.sqrt(sumSq)
        // L2 Norm should be approximately 1.0f
        assertTrue("Norm should be ~1.0 but was $norm", norm in 0.99f..1.01f)
    }

    @Test
    fun testSemanticSimilarityRanking() {
        val docId = "test_doc_1"
        engine.indexPageText(
            docId = docId,
            pageNum = 1,
            pageText = "The automotive vehicle accelerated down the asphalt highway with extreme velocity and speed."
        )
        engine.indexPageText(
            docId = docId,
            pageNum = 2,
            pageText = "Quantum computing uses superposition and entanglement of qubits to solve difficult cryptographic problems."
        )
        engine.indexPageText(
            docId = docId,
            pageNum = 3,
            pageText = "A culinary chef baked fresh sourdough bread and pastries inside a brick oven."
        )

        // Query related to cars / driving
        val results = engine.searchSimilarChunks(
            docId = docId,
            query = "automobile vehicle highway driving speed",
            topK = 2
        )

        assertFalse("Results should not be empty", results.isEmpty())
        val topMatch = results.first()
        assertEquals(1, topMatch.first.pageNum)
        assertTrue("Similarity score should be positive and relevant", topMatch.second > 0.05f)
    }

    @Test
    fun testClearDocumentIndex() {
        val docId = "temp_doc"
        engine.indexPageText(docId, 1, "Temporary text data")
        engine.clearDocumentIndex(docId)

        val results = engine.searchSimilarChunks(docId, "Temporary")
        assertTrue(results.isEmpty())
    }
}
