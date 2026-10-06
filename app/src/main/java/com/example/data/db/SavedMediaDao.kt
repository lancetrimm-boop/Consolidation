package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedia(item: SavedMediaEntity)

    @Query("DELETE FROM saved_media WHERE mediaId = :mediaId")
    suspend fun deleteMedia(mediaId: String)

    @Query("SELECT COUNT(*) > 0 FROM saved_media WHERE mediaId = :mediaId")
    suspend fun isSaved(mediaId: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM saved_media WHERE mediaId = :mediaId")
    fun observeIsSaved(mediaId: String): Flow<Boolean>

    @Query("SELECT * FROM saved_media ORDER BY savedAt DESC")
    fun getAllSaved(): Flow<List<SavedMediaEntity>>

    @Query("SELECT * FROM saved_media ORDER BY savedAt DESC")
    suspend fun getAllSavedSync(): List<SavedMediaEntity>

    @Query("SELECT COUNT(*) FROM saved_media")
    suspend fun getSavedCount(): Int
}
