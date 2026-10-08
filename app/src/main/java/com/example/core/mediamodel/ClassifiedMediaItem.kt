package com.example.core.mediamodel

data class ClassifiedMediaItem(
    val id: String,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val dateAddedEpochMs: Long,
    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val isAnalyzed: Boolean,
    val testStatus: TestStatus,
    val isCodecSupported: Boolean,
    val isCorrupt: Boolean,
    val fileExists: Boolean,
    val hasReadPermission: Boolean
)
