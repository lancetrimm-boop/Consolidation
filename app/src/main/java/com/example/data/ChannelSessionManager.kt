package com.example.data

import com.example.data.intelligence.*
import com.example.data.metv.ExperienceRequest
import com.example.data.semantic.EmbeddingResult
import com.example.data.semantic.SemanticInput
import com.example.data.semantic.SemanticRepresentationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestration boundary for active Channel block playback, queue replenishment, and stale session protection.
 */
class ChannelSessionManager(
    private val programmer: ChannelProgrammer = ChannelProgrammer()
) {

    private val _channelState = MutableStateFlow<ChannelState>(ChannelState.Idle)
    val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    private val _whatsNext = MutableStateFlow<List<MediaItem>>(emptyList())
    val whatsNext: StateFlow<List<MediaItem>> = _whatsNext.asStateFlow()

    @Volatile
    var currentSessionGeneration: Long = 0L
        private set

    @Volatile
    var isReplenishing: Boolean = false
        private set

    private val sessionMutex = Mutex()
    private var activeBlockItems = mutableListOf<MediaItem>()
    private var currentItemIndex = 0

    /**
     * Starts or switches to a channel session.
     */
    suspend fun selectChannel(
        channel: Channel,
        repository: MediaRepository,
        filterType: String = "VIDEOS",
        blockLimit: Int = 20,
        refreshEpoch: Int = 0,
        sessionExposures: Map<String, Int> = emptyMap()
    ) = withContext(Dispatchers.Default) {
        val newGen = sessionMutex.withLock {
            currentSessionGeneration++
            activeBlockItems.clear()
            currentItemIndex = 0
            _whatsNext.value = emptyList()
            currentSessionGeneration
        }

        _channelState.value = ChannelState.Loading(channel)

        try {
            val core = repository.intelligenceCore
            if (core == null) {
                _channelState.value = ChannelState.Error(channel, "Intelligence engine not ready")
                return@withContext
            }

            val availableMedia = repository.mediaItems.value.filter { repository.isItemVisibleInLibrary(it) }
            if (availableMedia.isNotEmpty()) {
                val db = repository.getDatabase()
                val feedback = db?.programmingFeedbackDao()?.getAllFeedback() ?: emptyList()
                val skipEvents = db?.aiSkipDao()?.observeAllEvents()?.firstOrNull() ?: emptyList()

                val context = ChannelProgrammingContext(
                    availableMedia = availableMedia,
                    filterType = filterType,
                    tasteDNA = repository.tasteDNA.value,
                    programmingFeedback = feedback,
                    exposureMap = availableMedia.associate { it.id to it.exposureCount },
                    skipEvents = skipEvents,
                    experienceRequest = ExperienceRequest(channel.title),
                    refreshEpoch = refreshEpoch,
                    sessionExposures = sessionExposures,
                    decayedExposureMap = availableMedia.associate { it.id to it.getDecayedExposure() }
                )

                val programmedItems = programmer.programChannel(channel, context, limit = blockLimit)
                if (programmedItems.isNotEmpty()) {
                    sessionMutex.withLock {
                        if (newGen != currentSessionGeneration) return@withContext
                        activeBlockItems.addAll(programmedItems)
                        updateWhatsNextLocked()
                        _channelState.value = ChannelState.Success(channel, activeBlockItems.toList())
                    }
                    return@withContext
                }
            }

            // Fallback for mocked core requests or empty local cache
            val queryVectors = mutableListOf<FloatArray>()
            if (channel.referenceMediaIds.isNotEmpty()) {
                val semanticRepo = repository.semanticRepresentationRepository
                val provider = repository.mobileCLIPProvider

                for (refId in channel.referenceMediaIds) {
                    val stored = if (provider != null && semanticRepo != null) {
                        semanticRepo.getSpecificRepresentation(refId, SemanticRepresentationType.VISUAL, provider.descriptor)?.vector
                    } else null

                    if (stored != null) {
                        queryVectors.add(stored)
                    } else {
                        val refItem = repository.getMediaItemById(refId)
                        val uri = refItem?.uriPath?.ifEmpty { refItem.imageUrl } ?: ""
                        val ctx = repository.applicationContext
                        if (uri.isNotEmpty() && ctx != null && provider != null && provider.isReady()) {
                            val bitmap = com.example.util.MediaThumbnailFetcher.getThumbnail(ctx, uri)
                            if (bitmap != null) {
                                val result = provider.generateEmbedding(refId, SemanticInput.ExplicitBitmap(bitmap), "channel_ref")
                                if (result is EmbeddingResult.Success) {
                                    queryVectors.add(result.representation.vector)
                                }
                            }
                        }
                    }
                }
            }

            val request = IntelligenceRequest(
                mode = IntelligenceMode.SEARCH,
                filterType = filterType,
                query = channel.query.ifBlank { null },
                queryVectors = queryVectors.ifEmpty { null },
                limit = blockLimit,
                tasteDNA = repository.tasteDNA.value,
                profile = repository.preferenceProfile.value,
                stats = repository.intelligenceStats.value,
                creatorProfiles = repository.creatorProfiles.value,
                comparisonCounts = repository.getComparisonCounts(),
                skipPersistence = true
            )

            DecisionTraceCollector.logEvent(
                request.requestId,
                com.example.ui.models.TraceEventType.CHANNEL_RETRIEVAL_START,
                detail = "Channel: ${channel.title}",
                metadata = mapOf("channel_id" to channel.id, "query" to channel.query)
            )

            val response = core.processRequest(request)
            sessionMutex.withLock {
                if (newGen != currentSessionGeneration) return@withContext

                if (response.isSuccess) {
                    val rankedItems = response.candidates.map { it.item }
                    activeBlockItems.addAll(rankedItems)
                    updateWhatsNextLocked()
                    _channelState.value = ChannelState.Success(channel, activeBlockItems.toList())
                } else {
                    _channelState.value = ChannelState.Error(channel, response.errorMessage ?: "Channel retrieval failed")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (newGen == currentSessionGeneration) {
                _channelState.value = ChannelState.Error(channel, e.message ?: "Channel calculation error")
            }
        }
    }

    /**
     * Replenishes the active channel session with the next programmed block.
     */
    suspend fun replenishNextBlock(
        channel: Channel,
        repository: MediaRepository,
        filterType: String = "VIDEOS",
        blockLimit: Int = 20,
        refreshEpoch: Int = 0,
        sessionExposures: Map<String, Int> = emptyMap()
    ): Boolean = withContext(Dispatchers.Default) {
        sessionMutex.withLock {
            if (isReplenishing) return@withContext false
            isReplenishing = true
        }

        val targetGen = currentSessionGeneration

        try {
            val availableMedia = repository.mediaItems.value.filter { repository.isItemVisibleInLibrary(it) }
            val db = repository.getDatabase()
            val feedback = db?.programmingFeedbackDao()?.getAllFeedback() ?: emptyList()
            val skipEvents = db?.aiSkipDao()?.observeAllEvents()?.firstOrNull() ?: emptyList()

            val context = ChannelProgrammingContext(
                availableMedia = availableMedia,
                filterType = filterType,
                tasteDNA = repository.tasteDNA.value,
                programmingFeedback = feedback,
                exposureMap = availableMedia.associate { it.id to it.exposureCount },
                skipEvents = skipEvents,
                refreshEpoch = refreshEpoch,
                sessionExposures = sessionExposures,
                decayedExposureMap = availableMedia.associate { it.id to it.getDecayedExposure() }
            )

            val nextBlock = programmer.programChannel(channel, context, limit = blockLimit)

            sessionMutex.withLock {
                if (targetGen != currentSessionGeneration) {
                    isReplenishing = false
                    return@withContext false
                }

                // Enforce media type homogeneity
                val isVideoFilter = filterType.equals("VIDEO", ignoreCase = true) || filterType.equals("VIDEOS", ignoreCase = true)
                val validItems = nextBlock.filter { item ->
                    val isVid = item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("MOVIE", ignoreCase = true)
                    if (isVideoFilter) isVid else !isVid
                }

                activeBlockItems.addAll(validItems)
                updateWhatsNextLocked()
                _channelState.value = ChannelState.Success(channel, activeBlockItems.toList())
                isReplenishing = false
                return@withContext true
            }
        } catch (e: Exception) {
            sessionMutex.withLock { isReplenishing = false }
            return@withContext false
        }
    }

    /**
     * Advances playback index and updates the 5-item What's Next guide.
     */
    fun advancePlaybackIndex(newIndex: Int) {
        currentItemIndex = newIndex
        updateWhatsNext()
    }

    /**
     * Updates the What's Next 5-item guide from the active programmed list.
     */
    fun updateWhatsNext() {
        val nextSlice = if (currentItemIndex + 1 < activeBlockItems.size) {
            activeBlockItems.subList(currentItemIndex + 1, minOf(currentItemIndex + 6, activeBlockItems.size)).toList()
        } else {
            emptyList()
        }
        _whatsNext.value = nextSlice
    }

    private fun updateWhatsNextLocked() {
        val nextSlice = if (currentItemIndex + 1 < activeBlockItems.size) {
            activeBlockItems.subList(currentItemIndex + 1, minOf(currentItemIndex + 6, activeBlockItems.size)).toList()
        } else {
            emptyList()
        }
        _whatsNext.value = nextSlice
    }

    /**
     * Cancels active session and resets state.
     */
    fun stopSession() {
        currentSessionGeneration++
        activeBlockItems.clear()
        currentItemIndex = 0
        _whatsNext.value = emptyList()
        _channelState.value = ChannelState.Idle
    }
}
