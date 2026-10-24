package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.ExposureEventType

@Entity(
    tableName = "exposure_events",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["timestamp"]),
        Index(value = ["eventType"]),
        Index(value = ["mediaId", "sessionId", "milestone"])
    ]
)
data class ExposureEventEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val eventType: ExposureEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val milestone: Int? = null,
    val sessionId: String? = null,
    val durationMs: Long? = null
)
