package com.example.core.mediamodel

/**
 * Represents media that has successfully passed eligibility gating.
 * Only :core:media-gate (or media-gate package) is allowed to call createInstance.
 */
class PresentationEligibleMedia private constructor(
    val id: String,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val dateAddedEpochMs: Long,
    val snapshotHash: String,
    val gateVersion: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PresentationEligibleMedia) return false
        return id == other.id && snapshotHash == other.snapshotHash
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + snapshotHash.hashCode()
        return result
    }

    companion object {
        internal fun createInstance(
            id: String,
            uri: String,
            title: String,
            durationMs: Long,
            mimeType: String,
            width: Int,
            height: Int,
            dateAddedEpochMs: Long,
            snapshotHash: String,
            gateVersion: Int
        ): PresentationEligibleMedia {
            return PresentationEligibleMedia(
                id, uri, title, durationMs, mimeType, width, height, dateAddedEpochMs, snapshotHash, gateVersion
            )
        }
    }
}
