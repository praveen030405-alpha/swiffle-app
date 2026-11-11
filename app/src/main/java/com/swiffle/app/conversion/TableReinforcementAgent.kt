package com.swiffle.app.conversion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hyperparameters chosen by the RL Policy Agent for table lattice extraction.
 */
data class TableExtractionPolicyAction(
    val sigmaFactor: Float,      // Factor of avg token width for Gaussian spread (e.g. 0.25f .. 0.55f)
    val valleyThreshold: Float,  // Relative energy cutoff for column gutter (e.g. 0.08f .. 0.22f)
    val rowToleranceFactor: Float,// Factor of avg token height for baseline hysteresis (e.g. 0.40f .. 0.70f)
    val minCellOverlap: Float     // Overlap ratio for token-to-column containment (e.g. 0.25f .. 0.45f)
)

/**
 * 6-dimensional State Feature Vector characterizing the document's geometric layout.
 */
data class DocumentLayoutState(
    val tokenDensity: Float,       // Area coverage of tokens over total page
    val medianAspectRatio: Float,  // Median width/height of tokens
    val numericRatio: Float,       // Ratio of numeric/currency tokens to total
    val gutterSpread: Float,       // Horizontal gutter spread estimate
    val baselineJitter: Float,     // Standard deviation of line heights
    val tokenCountNormalized: Float// Normalized token count
)

/**
 * On-Device Adaptive Reinforcement Learning Table Policy Engine.
 * 
 * Uses an on-device Contextual Actor-Critic Policy Gradient that adjusts extraction
 * hyperparameters based on layout geometry and optimizes an intrinsic reward function.
 * Every conversion improves future extractions for similar layouts.
 */
object TableReinforcementAgent {

    private const val PREFS_NAME = "swiffle_table_rl_memory"
    private const val KEY_POLICY_WEIGHTS = "policy_weights"
    private const val KEY_EXPERIENCE_COUNT = "experience_count"
    private const val LEARNING_RATE = 0.04f

    // Default Baseline Action
    val DEFAULT_ACTION = TableExtractionPolicyAction(
        sigmaFactor = 0.35f,
        valleyThreshold = 0.12f,
        rowToleranceFactor = 0.55f,
        minCellOverlap = 0.35f
    )

    /**
     * Extracts 6D layout feature vector from OCR tokens.
     */
    fun extractState(tokens: List<OcrToken>, pageWidth: Float, pageHeight: Float): DocumentLayoutState {
        if (tokens.isEmpty() || pageWidth <= 0f || pageHeight <= 0f) {
            return DocumentLayoutState(0f, 1f, 0f, 0f, 0f, 0f)
        }

        val pageArea = pageWidth * pageHeight
        val totalTokenArea = tokens.sumOf { (it.width * it.height).toDouble() }.toFloat()
        val density = (totalTokenArea / pageArea).coerceIn(0f, 1f)

        val aspectRatios = tokens.map { if (it.height > 0) it.width / it.height else 1f }.sorted()
        val medianAspect = aspectRatios[aspectRatios.size / 2].coerceIn(0.2f, 10f)

        val numPattern = Regex("^[+-]?\\$?[0-9,]+(\\.[0-9]+)?%?$")
        val numTokens = tokens.count { numPattern.matches(it.text.trim()) }
        val numRatio = (numTokens.toFloat() / tokens.size.toFloat()).coerceIn(0f, 1f)

        // Horizontal gutter variance
        val sortedLefts = tokens.map { it.bounds.left }.sorted()
        val diffs = mutableListOf<Float>()
        for (i in 0 until sortedLefts.size - 1) {
            val d = sortedLefts[i + 1] - sortedLefts[i]
            if (d > 5f) diffs.add(d)
        }
        val avgDiff = if (diffs.isNotEmpty()) diffs.average().toFloat() else 20f
        val diffVariance = if (diffs.size > 1) {
            val mean = avgDiff
            sqrt(diffs.map { (it - mean) * (it - mean) }.average()).toFloat()
        } else 0f

        val heights = tokens.map { it.height }
        val avgHeight = heights.average().toFloat()
        val heightVariance = if (heights.size > 1) {
            sqrt(heights.map { (it - avgHeight) * (it - avgHeight) }.average()).toFloat()
        } else 0f

        return DocumentLayoutState(
            tokenDensity = density,
            medianAspectRatio = medianAspect,
            numericRatio = numRatio,
            gutterSpread = (diffVariance / 100f).coerceIn(0f, 2f),
            baselineJitter = (heightVariance / 20f).coerceIn(0f, 2f),
            tokenCountNormalized = (tokens.size.toFloat() / 500f).coerceIn(0f, 2f)
        )
    }

    /**
     * Predicts optimal extraction hyperparameters for the given document state.
     */
    fun predictAction(context: Context?, state: DocumentLayoutState): TableExtractionPolicyAction {
        if (context == null) return DEFAULT_ACTION

        val weights = loadPolicyWeights(context)

        // Linear policy inference: a = a_base + W * S
        val deltaSigma = dot(weights.sigmaWeights, state)
        val deltaValley = dot(weights.valleyWeights, state)
        val deltaRow = dot(weights.rowWeights, state)
        val deltaOverlap = dot(weights.overlapWeights, state)

        return TableExtractionPolicyAction(
            sigmaFactor = (DEFAULT_ACTION.sigmaFactor + deltaSigma).coerceIn(0.20f, 0.55f),
            valleyThreshold = (DEFAULT_ACTION.valleyThreshold + deltaValley).coerceIn(0.06f, 0.25f),
            rowToleranceFactor = (DEFAULT_ACTION.rowToleranceFactor + deltaRow).coerceIn(0.38f, 0.72f),
            minCellOverlap = (DEFAULT_ACTION.minCellOverlap + deltaOverlap).coerceIn(0.20f, 0.50f)
        )
    }

    /**
     * Evaluates intrinsic geometric reward score R ∈ [0.0, 1.0] from reconstructed grid.
     */
    fun evaluateReward(grid: ReconstructedTableGrid): Float {
        if (grid.rowCount == 0 || grid.columnCount == 0) return 0.0f

        // 1. Column Orthogonality (penalty for huge variance in column cell counts)
        val cellsPerRow = grid.rows.map { it.cells.size }
        val avgCells = cellsPerRow.average().toFloat()
        val cellCountVariance = if (cellsPerRow.size > 1) {
            sqrt(cellsPerRow.map { (it - avgCells) * (it - avgCells) }.average()).toFloat()
        } else 0f
        val rCol = (1.0f - (cellCountVariance / max(avgCells, 1f))).coerceIn(0f, 1f)

        // 2. Type Homogeneity (cells in the same column should predominantly share data types)
        var colTypeScoreSum = 0f
        for (c in 0 until grid.columnCount) {
            val colCells = grid.rows.mapNotNull { r -> r.cells.find { it.columnIndex == c } }
            if (colCells.isNotEmpty()) {
                val dominantTypeCount = colCells.groupBy { it.dataType }.values.maxOf { it.size }
                colTypeScoreSum += dominantTypeCount.toFloat() / colCells.size.toFloat()
            }
        }
        val rType = (colTypeScoreSum / max(1, grid.columnCount)).coerceIn(0f, 1f)

        // 3. Row Monotonicity (non-overlapping row vertical intervals)
        var rowOverlapCount = 0
        for (i in 0 until grid.rows.size - 1) {
            if (grid.rows[i].bottomY > grid.rows[i + 1].topY) {
                rowOverlapCount++
            }
        }
        val rMono = (1.0f - (rowOverlapCount.toFloat() / max(1, grid.rows.size))).coerceIn(0f, 1f)

        // 4. Fill Density
        val totalPossibleCells = grid.rowCount * grid.columnCount
        val actualCells = grid.rows.sumOf { it.cells.size }
        val rFill = (actualCells.toFloat() / max(1, totalPossibleCells)).coerceIn(0.1f, 1f)

        // Weighted Composite Intrinsic Reward
        return (0.35f * rCol) + (0.30f * rType) + (0.20f * rMono) + (0.15f * rFill)
    }

    // Cache of the most recent conversion state & action for RLHF closed-loop update
    private var lastState: DocumentLayoutState? = null
    private var lastAction: TableExtractionPolicyAction? = null

    fun setLastConversionContext(state: DocumentLayoutState, action: TableExtractionPolicyAction) {
        lastState = state
        lastAction = action
    }

    /**
     * Applies closed-loop Human Feedback (RLHF).
     * Called when the user rates or provides feedback upon closing/viewing the converted Excel file.
     */
    fun applyHumanFeedback(
        context: Context,
        stars: Int, // 1 to 5
        feedbackTags: Set<String> = emptySet(),
        stateOverride: DocumentLayoutState? = null
    ) {
        val state = stateOverride ?: lastState ?: return
        val action = lastAction ?: predictAction(context, state)

        // Map 1..5 stars to reward R ∈ [-1.0, +1.0]
        val userReward = when (stars) {
            5 -> 1.0f
            4 -> 0.6f
            3 -> 0.1f
            2 -> -0.5f
            else -> -1.0f
        }

        val weights = loadPolicyWeights(context)

        // Adjust based on explicit tags
        var targetSigma = action.sigmaFactor
        var targetValley = action.valleyThreshold
        var targetRow = action.rowToleranceFactor

        if (feedbackTags.contains("COLUMNS_MERGED")) {
            // Need sharper Gaussian spread and higher valley threshold to separate columns
            targetSigma = (targetSigma - 0.08f).coerceAtLeast(0.20f)
            targetValley = (targetValley + 0.05f).coerceAtMost(0.25f)
        } else if (feedbackTags.contains("COLUMNS_SPLIT")) {
            // Need broader spread to avoid oversegmentation
            targetSigma = (targetSigma + 0.08f).coerceAtMost(0.55f)
            targetValley = (targetValley - 0.04f).coerceAtLeast(0.06f)
        }

        if (feedbackTags.contains("ROWS_MISALIGNED")) {
            targetRow = (targetRow + 0.08f).coerceAtMost(0.70f)
        }

        val advantage = userReward
        updateWeightsArray(weights.sigmaWeights, state, (targetSigma - DEFAULT_ACTION.sigmaFactor) * advantage)
        updateWeightsArray(weights.valleyWeights, state, (targetValley - DEFAULT_ACTION.valleyThreshold) * advantage)
        updateWeightsArray(weights.rowWeights, state, (targetRow - DEFAULT_ACTION.rowToleranceFactor) * advantage)

        savePolicyWeights(context, weights)
    }

    /**
     * Updates policy gradient weights based on experience reward.
     */
    fun recordExperienceAndUpdate(
        context: Context,
        state: DocumentLayoutState,
        action: TableExtractionPolicyAction,
        reward: Float
    ) {
        val baseline = 0.65f
        val advantage = reward - baseline

        val weights = loadPolicyWeights(context)

        // Policy gradient step: W_i <- W_i + alpha * A * S_i
        updateWeightsArray(weights.sigmaWeights, state, (action.sigmaFactor - DEFAULT_ACTION.sigmaFactor) * advantage)
        updateWeightsArray(weights.valleyWeights, state, (action.valleyThreshold - DEFAULT_ACTION.valleyThreshold) * advantage)
        updateWeightsArray(weights.rowWeights, state, (action.rowToleranceFactor - DEFAULT_ACTION.rowToleranceFactor) * advantage)
        updateWeightsArray(weights.overlapWeights, state, (action.minCellOverlap - DEFAULT_ACTION.minCellOverlap) * advantage)

        savePolicyWeights(context, weights)
    }

    private data class PolicyWeights(
        val sigmaWeights: FloatArray,
        val valleyWeights: FloatArray,
        val rowWeights: FloatArray,
        val overlapWeights: FloatArray
    )

    private fun dot(weights: FloatArray, state: DocumentLayoutState): Float {
        return (weights[0] * state.tokenDensity) +
               (weights[1] * state.medianAspectRatio) +
               (weights[2] * state.numericRatio) +
               (weights[3] * state.gutterSpread) +
               (weights[4] * state.baselineJitter) +
               (weights[5] * state.tokenCountNormalized)
    }

    private fun updateWeightsArray(weights: FloatArray, state: DocumentLayoutState, factor: Float) {
        val step = LEARNING_RATE * factor
        weights[0] = (weights[0] + step * state.tokenDensity).coerceIn(-0.15f, 0.15f)
        weights[1] = (weights[1] + step * (state.medianAspectRatio / 5f)).coerceIn(-0.15f, 0.15f)
        weights[2] = (weights[2] + step * state.numericRatio).coerceIn(-0.15f, 0.15f)
        weights[3] = (weights[3] + step * state.gutterSpread).coerceIn(-0.15f, 0.15f)
        weights[4] = (weights[4] + step * state.baselineJitter).coerceIn(-0.15f, 0.15f)
        weights[5] = (weights[5] + step * state.tokenCountNormalized).coerceIn(-0.15f, 0.15f)
    }

    private fun loadPolicyWeights(context: Context): PolicyWeights {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = sp.getString(KEY_POLICY_WEIGHTS, null) ?: return defaultWeights()

        return try {
            val obj = JSONObject(jsonStr)
            PolicyWeights(
                sigmaWeights = jsonToFloatArray(obj.getJSONArray("sigma")),
                valleyWeights = jsonToFloatArray(obj.getJSONArray("valley")),
                rowWeights = jsonToFloatArray(obj.getJSONArray("row")),
                overlapWeights = jsonToFloatArray(obj.getJSONArray("overlap"))
            )
        } catch (_: Exception) {
            defaultWeights()
        }
    }

    private fun savePolicyWeights(context: Context, weights: PolicyWeights) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val obj = JSONObject().apply {
                put("sigma", floatArrayToJson(weights.sigmaWeights))
                put("valley", floatArrayToJson(weights.valleyWeights))
                put("row", floatArrayToJson(weights.rowWeights))
                put("overlap", floatArrayToJson(weights.overlapWeights))
            }
            val count = sp.getInt(KEY_EXPERIENCE_COUNT, 0)
            sp.edit()
                .putString(KEY_POLICY_WEIGHTS, obj.toString())
                .putInt(KEY_EXPERIENCE_COUNT, count + 1)
                .apply()
        } catch (_: Exception) {}
    }

    private fun defaultWeights(): PolicyWeights {
        return PolicyWeights(
            sigmaWeights = FloatArray(6) { 0.0f },
            valleyWeights = FloatArray(6) { 0.0f },
            rowWeights = FloatArray(6) { 0.0f },
            overlapWeights = FloatArray(6) { 0.0f }
        )
    }

    private fun floatArrayToJson(arr: FloatArray): JSONArray {
        val j = JSONArray()
        for (v in arr) j.put(v.toDouble())
        return j
    }

    private fun jsonToFloatArray(j: JSONArray): FloatArray {
        val arr = FloatArray(j.length())
        for (i in 0 until j.length()) arr[i] = j.getDouble(i).toFloat()
        return arr
    }
}

