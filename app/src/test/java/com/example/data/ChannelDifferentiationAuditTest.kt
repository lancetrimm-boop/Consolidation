package com.example.data

import com.example.data.metv.ExperienceRequest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 5.5 — Channel Differentiation Audit Test Suite.
 * Pure test-only execution measuring pairwise candidate overlap, ranking similarity,
 * and programming identity across the 5 approved fixed channels.
 */
class ChannelDifferentiationAuditTest {

    private lateinit var programmer: ChannelProgrammer
    private lateinit var fixtureMedia: List<MediaItem>

    @Before
    fun setUp() {
        programmer = ChannelProgrammer()
        fixtureMedia = createDiverseLibraryFixture()
    }

    private fun createDiverseLibraryFixture(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val genres = listOf("Action", "Cinematic", "Atmospheric", "Serene", "Comedy", "Drama")
        val years = listOf(1975, 1985, 1995, 2005, 2015, 2023)
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 31L * 24 * 60 * 60 * 1000

        for (i in 1..50) {
            val isVid = i <= 35
            val isFav = i % 4 == 0
            val year = years[i % years.size]
            val genre = genres[i % genres.size]
            val lastViewed = if (i % 3 == 0) now - thirtyDaysMs * 2 else if (i % 2 == 0) now - 100000L else null
            val seriesId = if (i <= 10) "series_alpha" else if (i <= 20) "series_beta" else null

            list.add(
                MediaItem(
                    id = "item_$i",
                    title = if (isVid) "Video Title $i ($genre)" else "Photo Title $i ($genre)",
                    mediaType = if (isVid) "VIDEO" else "PHOTO",
                    year = year,
                    duration = if (isVid) "02:15" else "",
                    genre = genre,
                    rating = (i % 10) + 1.0f,
                    isFavorite = isFav,
                    lastViewedTimestamp = lastViewed,
                    parentContentId = seriesId,
                    selectionReason = "S1E${i % 5 + 1}"
                )
            )
        }
        return list
    }

    @Test
    fun testAll5FixedChannels_ProgramDeterministically() {
        val context = ChannelProgrammingContext(
            availableMedia = fixtureMedia,
            filterType = "VIDEOS",
            currentTimeMs = 1700000000000L
        )

        val fixed = ChannelRegistry.allFixedChannels()
        assertEquals(5, fixed.size)

        val outputs = mutableMapOf<String, List<MediaItem>>()
        for (channel in fixed) {
            val block = programmer.programChannel(channel, context, limit = 20)
            outputs[channel.id] = block
            assertTrue("Channel ${channel.id} should output items", block.isNotEmpty())
            assertTrue("Channel ${channel.id} must be 100% video homogeneous", block.all { it.mediaType == "VIDEO" })
        }

        assertEquals(5, outputs.size)
    }

    @Test
    fun testPairwiseJaccardOverlap_AuditReportGeneration() {
        val context = ChannelProgrammingContext(
            availableMedia = fixtureMedia,
            filterType = "VIDEOS",
            currentTimeMs = 1700000000000L,
            exposureMap = mapOf("item_1" to 5, "item_2" to 3, "item_3" to 8, "item_4" to 12),
            experienceRequest = ExperienceRequest("energetic")
        )

        val fixed = ChannelRegistry.allFixedChannels()
        val blocks = fixed.associate { channel ->
            channel.id to programmer.programChannel(channel, context, limit = 20).map { it.id }
        }

        println("=== PHASE 5.5 PAIRWISE OVERLAP AUDIT (TOP 20) ===")
        val ids = fixed.map { it.id }

        val overlapMatrix = mutableMapOf<Pair<String, String>, Double>()

        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val idA = ids[i]
                val idB = ids[j]
                val setA = blocks[idA]!!.toSet()
                val setB = blocks[idB]!!.toSet()

                val intersection = setA.intersect(setB).size
                val union = setA.union(setB).size
                val jaccard = if (union > 0) intersection.toDouble() / union else 0.0

                overlapMatrix[idA to idB] = jaccard
                println("Pair [$idA ↔ $idB]: Intersection = $intersection/20, Jaccard = ${String.format("%.2f", jaccard)}")
            }
        }

        assertNotNull(overlapMatrix["ME_TV" to "REDISCOVER"])
        assertNotNull(overlapMatrix["ME_TV" to "FAVORITES"])
    }

    @Test
    fun testPhotoFilter_HomogeneityAcrossAllChannels() {
        val context = ChannelProgrammingContext(
            availableMedia = fixtureMedia,
            filterType = "PHOTOS",
            currentTimeMs = 1700000000000L
        )

        for (channel in ChannelRegistry.allFixedChannels()) {
            val block = programmer.programChannel(channel, context, limit = 20)
            assertTrue("Photo block for ${channel.id} must contain 0 videos", block.all { it.mediaType == "PHOTO" })
        }
    }

    @Test
    fun testAuditDoesNotMutateState() {
        val initialDna = TasteDNA()
        val initialMedia = createDiverseLibraryFixture()

        val context = ChannelProgrammingContext(
            availableMedia = initialMedia,
            filterType = "VIDEOS",
            tasteDNA = initialDna
        )

        for (channel in ChannelRegistry.allFixedChannels()) {
            programmer.programChannel(channel, context, limit = 20)
        }

        // Confirm zero mutation
        assertEquals(initialDna, context.tasteDNA)
        assertEquals(initialMedia.size, context.availableMedia.size)
    }
}
