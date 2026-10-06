package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteDNAMutationVerificationTest {

    private fun createMediaItem(
        id: String,
        moodTags: List<String> = emptyList(),
        category: String = "For You",
        mediaType: String = "PHOTO",
    ): MediaItem {
        return MediaItem(
            id = id,
            title = "Title $id",
            uriPath = "path/$id",
            mediaType = mediaType,
            moodTags = moodTags,
            category = category,
        )
    }

    @Test
    fun testTraitAdjustments_VibrantAndCinematicTags_ProducePositiveDirectionalAdjustments() {
        val item = createMediaItem("item1", moodTags = listOf("vibrant", "cinematic"))
        val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)

        assertTrue(adjustments.containsKey("vibrancy"))
        assertTrue(adjustments["vibrancy"]!! > 0.0)

        assertTrue(adjustments.containsKey("depth"))
        assertTrue(adjustments["depth"]!! > 0.0)

        // Verify adjustments are bounded within [-1.0, 1.0]
        adjustments.values.forEach { weight ->
            assertTrue((weight >= -1.0) && (weight <= 1.0))
        }
    }

    @Test
    fun testTasteDNA_FineTuningDisabled_EffectiveValueEqualsManualBaseline() {
        val dna = TasteDNA(
            isFineTuningEnabled = false,
            vibrancy = 0.5,
            learnedVibrancy = 0.9,
            confVibrancy = 1.0,
        )
        // When fine-tuning is disabled, effective vibrancy must equal manual baseline (0.5)
        assertEquals(0.5, dna.effectiveVibrancy, 0.001)
    }

    @Test
    fun testTasteDNA_FineTuningEnabled_LearnedAdjustmentBoundedAndCorrect() {
        var dna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.5,
            confVibrancy = 0.5,
        )

        val item = createMediaItem("vibrant_item", moodTags = listOf("vibrant"))
        val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)

        adjustments.forEach { (dim, mult) ->
            dna = dna.updateLearnedDimension(dim, mult * 0.05, 0.20)
        }

        // Learned vibrancy should increase from 0.5
        assertTrue(dna.learnedVibrancy > 0.5)

        // Effective value should remain strictly bounded within [0.0, 1.0]
        val eff = dna.effectiveVibrancy
        assertTrue((eff >= 0.0) && (eff <= 1.0))
    }

    @Test
    fun testTasteDNA_FavoriteMutationContract_Direction_Magnitude_Bounds_Isolation_And_Persistence() {
        // 1. Start from known deterministic TasteDNA baseline
        val baseline = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.5,
            saturation = 0.5,
            learnedSaturation = 0.5,
            sharpness = 0.5,
            learnedSharpness = 0.5,
            contrast = 0.5,
            learnedContrast = 0.5,
            depth = 0.5,
            learnedDepth = 0.5,
        )

        // 2. Create media item with known mood tag "vibrant"
        val item = createMediaItem("vibrant_photo", moodTags = listOf("vibrant"), mediaType = "PHOTO")
        val adjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(item)

        // 3. Apply the favorite interaction contract (delta = mult * 0.05 with max limit 0.20)
        var dna = baseline
        adjustments.forEach { (dim, mult) ->
            dna = dna.updateLearnedDimension(dim, mult * 0.05, 0.20)
        }

        // 4 & 5. Verify direction and magnitude
        // "vibrant" tag -> vibrancy mult = 1.0, delta = +0.05 -> learnedVibrancy = 0.55
        assertEquals(0.55, dna.learnedVibrancy, 0.0001)

        // "vibrant" tag -> saturation mult = 0.5, delta = +0.025 -> learnedSaturation = 0.525
        assertEquals(0.525, dna.learnedSaturation, 0.0001)

        // 6. Verify values remain within baselineLimit bounds (0.50 + 0.20 = 0.70)
        var cappedDna = baseline
        repeat(10) {
            adjustments.forEach { (dim, mult) ->
                cappedDna = cappedDna.updateLearnedDimension(dim, mult * 0.05, 0.20)
            }
        }
        assertEquals(0.70, cappedDna.learnedVibrancy, 0.0001)
        assertTrue(cappedDna.learnedVibrancy <= 1.0)

        // 7. Verify unrelated dimensions are not corrupted
        assertEquals(0.50, dna.learnedSharpness, 0.0001)
        assertEquals(0.50, dna.learnedContrast, 0.0001)
        assertEquals(0.50, dna.learnedDepth, 0.0001)

        // 8. Verify persistence / Moshi JSON round-trip
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(TasteDNA::class.java)
        val json = adapter.toJson(dna)
        val restored = adapter.fromJson(json)

        assertNotNull(restored)
        assertEquals(dna.isFineTuningEnabled, restored!!.isFineTuningEnabled)
        assertEquals(0.55, restored.learnedVibrancy, 0.0001)
        assertEquals(0.525, restored.learnedSaturation, 0.0001)
        assertEquals(0.50, restored.learnedSharpness, 0.0001)
    }

    @Test
    fun testTasteDNA_MoshiSerialization_RoundTripPreservesState() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(TasteDNA::class.java)

        val original = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.7,
            learnedVibrancy = 0.85,
            confVibrancy = 0.6,
        )

        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        assertNotNull(restored)
        assertEquals(original.isFineTuningEnabled, restored!!.isFineTuningEnabled)
        assertEquals(original.vibrancy, restored.vibrancy, 0.001)
        assertEquals(original.learnedVibrancy, restored.learnedVibrancy, 0.001)
        assertEquals(original.confVibrancy, restored.confVibrancy, 0.001)
    }
}
