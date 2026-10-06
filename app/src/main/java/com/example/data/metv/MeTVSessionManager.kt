package com.example.data.metv

import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.db.ProgrammingFeedbackEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Session Manager for the living Me TV station.
 * Orchestrates endless candidate programming, queue replenishment, upcoming schedule,
 * session lifecycle, and two-signal programming quality feedback isolation.
 */
class MeTVSessionManager(
    private val repository: MediaRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val engine: MeTVProgrammingEngine = MeTVProgrammingEngine()
) {

    private var activeSessionToken: String? = null
    private var replenishmentJob: Job? = null

    private val _currentFilterType = MutableStateFlow("VIDEOS")
    val currentFilterType: StateFlow<String> = _currentFilterType.asStateFlow()

    private val _experienceRequest = MutableStateFlow<ExperienceRequest?>(null)
    val experienceRequest: StateFlow<ExperienceRequest?> = _experienceRequest.asStateFlow()

    private val _scheduleState = MutableStateFlow<ComingUpSchedule?>(null)
    val scheduleState: StateFlow<ComingUpSchedule?> = _scheduleState.asStateFlow()

    private val REPLENISH_THRESHOLD = 5

    /**
     * Starts a new living Me TV station session.
     */
    fun startSession(
        filterType: String = "VIDEOS",
        initialIndex: Int = 0,
        request: ExperienceRequest? = null
    ): String {
        // Stop any previous active session & invalidate token
        stopSession()

        val token = UUID.randomUUID().toString()
        activeSessionToken = token
        _currentFilterType.value = filterType
        _experienceRequest.value = request

        scope.launch {
            val availableMedia = repository.mediaItems.value
            val tasteDNA = repository.tasteDNA.value
            val feedback = repository.getDatabase()?.programmingFeedbackDao()?.getAllFeedback() ?: emptyList()
            val skipEvents = repository.getDatabase()?.aiSkipDao()?.observeAllEvents()?.firstOrNull() ?: emptyList()

            val result = engine.program(
                availableMedia = availableMedia,
                filterType = filterType,
                tasteDNA = tasteDNA,
                programmingFeedback = feedback,
                skipEvents = skipEvents,
                experienceRequest = request,
                targetCount = 30
            )

            // Stale Session Guard
            if (activeSessionToken != token) return@launch

            val meTvChannel = com.example.data.ChannelRegistry.get("ME_TV")!!
            val sourceTitle = if (filterType.equals("PHOTOS", ignoreCase = true)) "Aura Moments" else "Me TV Station"
            withContext(mainDispatcher) {
                repository.setChannelPlaylist(
                    channel = meTvChannel,
                    filterType = filterType,
                    items = result.items,
                    initialIndex = initialIndex,
                    sourceTitle = sourceTitle
                )
            }

            updateScheduleState(result.items, result.explanations)
        }

        return token
    }

    /**
     * Replenishes the station queue asynchronously when approaching the end of current playlist.
     */
    fun checkAndReplenishQueue() {
        val token = activeSessionToken ?: return
        val activePlaylist = repository.activePlaylist.value ?: return

        val remaining = activePlaylist.items.size - activePlaylist.currentIndex
        if (remaining <= REPLENISH_THRESHOLD && replenishmentJob?.isActive != true) {
            replenishmentJob = scope.launch {
                val availableMedia = repository.mediaItems.value
                val tasteDNA = repository.tasteDNA.value
                val feedback = repository.getDatabase()?.programmingFeedbackDao()?.getAllFeedback() ?: emptyList()
                val skipEvents = repository.getDatabase()?.aiSkipDao()?.observeAllEvents()?.firstOrNull() ?: emptyList()

                val result = engine.program(
                    availableMedia = availableMedia,
                    filterType = _currentFilterType.value,
                    tasteDNA = tasteDNA,
                    programmingFeedback = feedback,
                    skipEvents = skipEvents,
                    experienceRequest = _experienceRequest.value,
                    targetCount = 20
                )

                // Stale Session Guard
                if (activeSessionToken != token) return@launch

                withContext(mainDispatcher) {
                    repository.extendActivePlaylist(result.items)
                }

                repository.activePlaylist.value?.let { updated ->
                    updateScheduleState(updated.items, result.explanations)
                }
            }
        }
    }

    /**
     * Submits programming quality feedback (Thumbs Up / Down) without mutating TasteDNA or ratings.
     */
    fun submitProgrammingFeedback(mediaId: String, isGoodProgramming: Boolean) {
        val channelId = if (_currentFilterType.value.equals("PHOTOS", ignoreCase = true)) "metv_photos" else "metv_videos"
        scope.launch {
            val entity = ProgrammingFeedbackEntity(
                id = UUID.randomUUID().toString(),
                mediaId = mediaId,
                channelId = channelId,
                isGoodProgramming = isGoodProgramming,
                timestamp = System.currentTimeMillis()
            )
            repository.getDatabase()?.programmingFeedbackDao()?.insertFeedback(entity)
        }
    }

    /**
     * Applies a session-level Mood Steering request ("Change the Mood").
     * Affects future replenishment cycles without mutating persisted TasteDNA.
     */
    fun changeMood(request: ExperienceRequest) {
        _experienceRequest.value = request
        checkAndReplenishQueue()
    }

    /**
     * Stops active session and invalidates token.
     */
    fun stopSession() {
        replenishmentJob?.cancel()
        replenishmentJob = null
        activeSessionToken = null
        _scheduleState.value = null
    }

    private fun updateScheduleState(items: List<MediaItem>, explanations: Map<String, String>) {
        val currentPlaylist = repository.activePlaylist.value
        val currentIndex = currentPlaylist?.currentIndex ?: 0
        val currentItem = items.getOrNull(currentIndex)
        val upcomingItems = if (currentIndex + 1 < items.size) items.subList(currentIndex + 1, items.size) else emptyList()
        val explanation = currentItem?.let { explanations[it.id] } ?: "Programmed for your Taste DNA baseline."

        _scheduleState.value = ComingUpSchedule(
            currentItem = currentItem,
            upcomingItems = upcomingItems,
            aestheticExplanation = explanation
        )
    }
}
