package com.example.core.mediagate

import com.example.core.mediamodel.ClassifiedMediaItem
import java.security.MessageDigest

interface SnapshotHasher {
    fun compute(item: ClassifiedMediaItem): String
}

class DefaultSnapshotHasher : SnapshotHasher {
    override fun compute(item: ClassifiedMediaItem): String {
        val raw = "${item.id}|${item.uri}|${item.fileSizeBytes}|${item.lastModifiedEpochMs}|${item.isCodecSupported}|${item.isCorrupt}|${item.isAnalyzed}|${item.testStatus}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
