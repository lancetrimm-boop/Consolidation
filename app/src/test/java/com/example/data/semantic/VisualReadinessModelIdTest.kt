package com.example.data.semantic

import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualReadinessModelIdTest {

    @Test
    fun testObserveVerifiedVisualCount_FiltersByModelId() = runBlocking {
        val fakeDao = FakeSemanticRepresentationDao()
        val tinyClipModelId = "tinyclip-vit-8m-16"
        val legacyModelId = "mobileclip-s0"

        // Insert a tinyclip representation
        val vectorData = ByteArray(2048) { 1 } // 512 floats = 2048 bytes
        fakeDao.upsert(
            SemanticRepresentationEntity(
                id = "sem_item1_visual_${tinyClipModelId}_v1",
                mediaId = "item1",
                representationType = "VISUAL",
                modelId = tinyClipModelId,
                modelVersion = 1,
                documentVersion = 1,
                dimensionality = 512,
                vectorData = vectorData,
                isNormalized = true,
                sourceDataHash = "hash1",
                confidence = 1.0f
            )
        )

        // Querying with tinyclip model ID returns 1
        val tinyClipCount = fakeDao.observeVerifiedVisualCount(modelId = tinyClipModelId).first()
        assertEquals(1, tinyClipCount)

        // Querying with legacy model ID returns 0
        val legacyCount = fakeDao.observeVerifiedVisualCount(modelId = legacyModelId).first()
        assertEquals(0, legacyCount)
    }
}
