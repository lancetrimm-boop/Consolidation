package com.example.data.intelligence

import androidx.media3.common.PlaybackException
import com.example.data.ConversionEligibility
import com.example.data.db.PlaybackErrorLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuraConversionAdvisorTest {

    @Test
    fun testEligibility_CodecError() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1",
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )
        
        val result = AuraConversionAdvisor.evaluateEligibility(error)
        assertEquals(ConversionEligibility.CONVERTIBLE, result)
    }

    @Test
    fun testEligibility_UnrelatedError() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1",
            errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        
        val result = AuraConversionAdvisor.evaluateEligibility(error)
        assertEquals(ConversionEligibility.NOT_RECOMMENDED, result)
    }

    @Test
    fun testEligibility_NoUri() {
        val error = PlaybackErrorLogEntity(
            mediaUri = null,
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )
        
        val result = AuraConversionAdvisor.evaluateEligibility(error)
        assertEquals(ConversionEligibility.UNAVAILABLE, result)
    }

    @Test
    fun testRecommendation_CodecError() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1",
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            mimeType = "video/hevc",
            codecName = "hevc-decoder"
        )
        
        val recommendation = AuraConversionAdvisor.createRecommendation(error)
        assertEquals(ConversionEligibility.CONVERTIBLE, recommendation.eligibility)
        assertEquals("hevc", recommendation.sourceContainer)
        assertEquals("hevc-decoder", recommendation.sourceVideoCodec)
        assertEquals("H.264/AVC", recommendation.targetVideoCodec)
    }

    @Test
    fun testRecommendation_NotRecommended() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1",
            errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        
        val recommendation = AuraConversionAdvisor.createRecommendation(error)
        assertEquals(ConversionEligibility.NOT_RECOMMENDED, recommendation.eligibility)
        assert(recommendation.explanation.contains("not appear to be caused by a codec"))
    }

    @Test
    fun testRecommendation_UnsupportedContainer_Unavailable() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1000014688",
            errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        )

        val recommendation = AuraConversionAdvisor.createRecommendation(error)
        assertEquals(ConversionEligibility.UNAVAILABLE, recommendation.eligibility)
        assertEquals("Unreadable or unsupported container format", recommendation.reason)
    }

    @Test
    fun testRecommendation_ZeroMetadataAlone_DoesNotForceUnavailable() {
        val error = PlaybackErrorLogEntity(
            mediaUri = "content://media/1",
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            durationMs = 0L
        )

        val recommendation = AuraConversionAdvisor.createRecommendation(error)
        assertEquals(ConversionEligibility.CONVERTIBLE, recommendation.eligibility)
    }
}
