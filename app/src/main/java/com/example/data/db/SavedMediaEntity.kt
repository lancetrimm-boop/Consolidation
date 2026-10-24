package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_media",
    indices = [
        Index(value = ["savedAt"], orders = [Index.Order.DESC])
    ]
)
data class SavedMediaEntity(
    @PrimaryKey val mediaId: String,
    val savedAt: Long
)
