package com.swiffle.app.evidence

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
internal data class EvidenceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val FullPage = EvidenceBounds(0f, 0f, 1f, 1f)

        fun between(x1: Float, y1: Float, x2: Float, y2: Float) = EvidenceBounds(
            min(x1, x2).coerceIn(0f, 1f),
            min(y1, y2).coerceIn(0f, 1f),
            max(x1, x2).coerceIn(0f, 1f),
            max(y1, y2).coerceIn(0f, 1f)
        )
    }
}

@Serializable
internal data class EvidenceSource(
    val uri: String,
    val filename: String,
    val fingerprint: String,
    val retainedCopy: Boolean = false
)

@Serializable
internal data class EvidenceItem(
    val id: String,
    val source: EvidenceSource,
    val page: Int,
    val regions: List<EvidenceBounds>,
    val excerpt: String? = null,
    val group: String = "",
    val note: String = "",
    val reviewed: Boolean = false
)

@Serializable
internal data class EvidencePack(
    val id: String,
    val title: String,
    val items: List<EvidenceItem> = emptyList()
) {
    fun moveWithinGroup(itemId: String, direction: Int): EvidencePack {
        val from = items.indexOfFirst { it.id == itemId }
        if (from < 0 || direction !in listOf(-1, 1)) return this
        val sameGroup = items.indices.filter { items[it].group == items[from].group }
        val to = sameGroup.getOrNull(sameGroup.indexOf(from) + direction) ?: return this
        return copy(items = items.toMutableList().apply {
            this[from] = items[to]
            this[to] = items[from]
        })
    }
}

@Serializable
internal data class EvidenceLibrary(
    val activeId: String? = null,
    val packs: List<EvidencePack> = emptyList()
) {
    val activePack get() = packs.firstOrNull { it.id == activeId }
}

internal data class EvidenceCapture(
    val page: Int,
    val regions: List<EvidenceBounds>,
    val excerpt: String? = null
)
