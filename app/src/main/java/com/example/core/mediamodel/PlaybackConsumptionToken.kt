package com.example.core.mediamodel

class PlaybackConsumptionToken internal constructor(
    val mediaId: String,
    val uri: String,
    val validatedTimestampEpochMs: Long,
    val snapshotHash: String
) {
    val isFresh: Boolean
        get() = (System.currentTimeMillis() - validatedTimestampEpochMs) < 15_000L
}
