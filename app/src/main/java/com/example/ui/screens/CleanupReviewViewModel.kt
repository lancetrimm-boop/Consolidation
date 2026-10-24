package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.cleanup.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CleanupReviewUiState(
    val recommendations: List<CleanupRecommendation> = emptyList(),
    val filteredRecommendations: List<CleanupRecommendation> = emptyList(),
    val mediaItems: Map<String, MediaItem> = emptyMap(),
    val selectedCategory: CleanupCategory = CleanupCategory.DELETE_RECOMMENDATIONS,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isLocked: Boolean = false,
    val isDeleting: Boolean = false,
    val requiresInternalConfirmation: Boolean = false,
    val storageRecoveryEstimate: Long = 0L,
    val categoryStats: Map<CleanupCategory, CategoryStat> = emptyMap(),
    val currentSort: ReviewSort = ReviewSort.LOWEST_KEEP_SCORE
)

data class CategoryStat(
    val count: Int,
    val storageBytes: Long,
    val averageKeepScore: Float
)

enum class ReviewSort {
    LOWEST_KEEP_SCORE,
    HIGHEST_CONFIDENCE,
    LARGEST_STORAGE,
    MOST_EXPOSED
}

class CleanupReviewViewModel(
    private val repository: MediaRepository,
    private val entitlementRepository: com.example.data.entitlement.EntitlementRepository,
    private val backgroundDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    private val diagnosticMediaStore: com.example.data.mediastore.DiagnosticMediaStore = repository.diagnosticMediaStore ?: com.example.data.mediastore.DefaultDiagnosticMediaStore()
) : ViewModel() {

    val deleteManager = repository.safeDeleteManager

    private val _uiState = MutableStateFlow(CleanupReviewUiState())
    val uiState: StateFlow<CleanupReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.databaseState,
                repository.mediaItems,
                diagnosticMediaStore.ineligibleItems,
                entitlementRepository.proState
            ) { dbState, _, _, proState ->
                Pair(dbState, proState)
            }.collect { (dbState, proState) ->
                if (dbState != com.example.data.DatabaseState.READY) {
                    _uiState.update { it.copy(isLoading = true, isLocked = false) }
                } else if (!proState.isPro) {
                    _uiState.update { it.copy(isLoading = false, isLocked = true) }
                } else {
                    loadRecommendations()
                }
            }
        }
        
        viewModelScope.launch {
            deleteManager?.deletionState?.collect { state ->
                when (state) {
                    DeletionState.CONFIRMED, DeletionState.CANCELLED, DeletionState.FAILED -> {
                        _uiState.update { it.copy(isDeleting = false, requiresInternalConfirmation = false) }
                        if (state == DeletionState.CONFIRMED) {
                            loadRecommendations() // Refresh
                        }
                    }
                    DeletionState.PENDING -> {
                        _uiState.update { it.copy(isDeleting = true, requiresInternalConfirmation = false) }
                    }
                    DeletionState.AURA_CONFIRMATION_REQUIRED -> {
                        _uiState.update { it.copy(isDeleting = false, requiresInternalConfirmation = true) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            val isPro = entitlementRepository.proState.value.isPro
            if (!isPro) {
                _uiState.update { it.copy(isLoading = false, isLocked = true) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, isLocked = false) }
            try {
                val ineligibleEntries = diagnosticMediaStore.ineligibleItems.value
                val items = repository.mediaItems.value
                if (items.isEmpty() && ineligibleEntries.isEmpty() && repository.databaseState.value != com.example.data.DatabaseState.READY) {
                    _uiState.update { it.copy(isLoading = true) }
                    return@launch
                }
                val tasteDNA = repository.tasteDNA.value
                val stats = repository.intelligenceStats.value

                kotlinx.coroutines.withContext(backgroundDispatcher) {
                    val ineligibleRecs = ineligibleEntries.map { entry ->
                        val reasonText = entry.reasons.joinToString { it.name }
                        CleanupRecommendation(
                            mediaId = entry.id,
                            keepScore = 0.0f,
                            confidenceScore = 1.0f,
                            category = CleanupCategory.UNPLAYABLE_FILES,
                            reasons = listOf(CleanupReason.UNPLAYABLE_MEDIA),
                            storageSize = entry.sizeBytes,
                            exposureCount = 0,
                            createdTimestamp = entry.detectedAtEpochMs,
                            explanation = "Ineligible: $reasonText"
                        )
                    }

                    val items = repository.mediaItems.value
                    val skipCounts = try { repository.getSkipCounts(items.map { it.id }) } catch (_: Exception) { emptyMap() }
                    
                    val inputs = items.map { item ->
                        KeepScoreInput(
                            mediaId = item.id,
                            fileSize = item.sizeBytes,
                            dateAdded = item.dateAdded,
                            exposureCount = item.exposureCount,
                            lastExposedTimestamp = item.lastExposedTimestamp,
                            viewCount = item.viewCount,
                            playCount = item.viewCount,
                            averageWatchDuration = if (item.durationMs > 0) (item.lastViewedTimestamp ?: 0L).toFloat() / item.durationMs else 0f,
                            completionPercentage = item.progress,
                            skipCount = skipCounts[item.id] ?: 0,
                            rating = item.rating,
                            isFavorite = item.isFavorite,
                            tasteAlignmentScore = 0.5f,
                            rarityScore = 1.0f,
                            contentHash = item.contentHash
                        )
                    }
                    val results = inputs.map { KeepScoreEngine.calculateScore(it) }
                    val metadata = items.associate { item ->
                        item.id to CleanupItemMetadata(
                            mediaId = item.id,
                            title = item.title,
                            sizeBytes = item.sizeBytes,
                            exposureCount = item.exposureCount,
                            viewCount = item.viewCount,
                            mediaType = item.mediaType,
                            contentHash = item.contentHash,
                            isFavorite = item.isFavorite,
                            width = item.width,
                            height = item.height,
                            durationMs = item.durationMs,
                            dateAdded = item.dateAdded
                        )
                    }
                    val generatedRecs = CleanupRecommendationEngine.generateRecommendations(results, metadata)
                    val recommendations = ineligibleRecs + generatedRecs

                    val statsMap = CleanupCategory.entries.associateWith { cat ->
                        val catRecs = recommendations.filter { it.category == cat }
                        CategoryStat(
                            count = catRecs.size,
                            storageBytes = catRecs.sumOf { it.storageSize },
                            averageKeepScore = if (catRecs.isNotEmpty()) catRecs.map { it.keepScore }.average().toFloat() else 0f
                        )
                    }

                    val itemMap = repository.mediaItems.value.associateBy { it.id }
                    _uiState.update { state ->
                        val newSelectedIds = state.selectedIds.intersect(recommendations.map { it.mediaId }.toSet())
                        val targetCategoryCount = statsMap[state.selectedCategory]?.count ?: 0
                        val bestCategory = if (targetCategoryCount == 0) {
                            CleanupCategory.entries.firstOrNull { (statsMap[it]?.count ?: 0) > 0 } ?: state.selectedCategory
                        } else {
                            state.selectedCategory
                        }
                        val newState = state.copy(
                            recommendations = recommendations,
                            mediaItems = itemMap,
                            categoryStats = statsMap,
                            selectedCategory = bestCategory,
                            isLoading = false,
                            selectedIds = newSelectedIds
                        )
                        applyFiltersAndSort(newState)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("CleanupReviewViewModel", "Failed to load cleanup recommendations", e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectCategory(category: CleanupCategory) {
        _uiState.update { state ->
            val newState = state.copy(selectedCategory = category, selectedIds = emptySet())
            applyFiltersAndSort(newState)
        }
    }

    fun toggleSelection(mediaId: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(mediaId)) {
                state.selectedIds - mediaId
            } else {
                state.selectedIds + mediaId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun selectAllInCategory() {
        _uiState.update { state ->
            val allIds = state.filteredRecommendations.map { it.mediaId }.toSet()
            state.copy(selectedIds = allIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun updateSort(sort: ReviewSort) {
        _uiState.update { state ->
            val newState = state.copy(currentSort = sort)
            applyFiltersAndSort(newState)
        }
    }

    fun requestDeleteSelected(
        context: android.content.Context,
        launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
    ) {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val items = selectedIds.mapNotNull { repository.getMediaItemById(it) }
        val recs = _uiState.value.recommendations.filter { selectedIds.contains(it.mediaId) }

        deleteManager.requestDeletion(context, items, recs, launcher)
    }

    fun keepSelected() {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val items = selectedIds.mapNotNull { repository.getMediaItemById(it) }
        val recs = _uiState.value.recommendations.filter { selectedIds.contains(it.mediaId) }

        items.forEach { item ->
            recs.find { it.mediaId == item.id }?.let { rec ->
                deleteManager.markAsKept(item, rec)
            }
        }
        
        clearSelection()
        loadRecommendations()
    }

    fun keepItem(mediaId: String) {
        val item = repository.getMediaItemById(mediaId) ?: return
        val rec = _uiState.value.recommendations.find { it.mediaId == mediaId } ?: return
        deleteManager.markAsKept(item, rec)
        _uiState.update { state ->
            val updatedRecs = state.recommendations.filterNot { it.mediaId == mediaId }
            val updatedSelection = state.selectedIds - mediaId
            val statsMap = CleanupCategory.entries.associateWith { cat ->
                val catRecs = updatedRecs.filter { it.category == cat }
                CategoryStat(
                    count = catRecs.size,
                    storageBytes = catRecs.sumOf { it.storageSize },
                    averageKeepScore = if (catRecs.isNotEmpty()) catRecs.map { it.keepScore }.average().toFloat() else 0f
                )
            }
            val newState = state.copy(
                recommendations = updatedRecs,
                selectedIds = updatedSelection,
                categoryStats = statsMap
            )
            applyFiltersAndSort(newState)
        }
    }

    fun confirmInternalDeletion() {
        deleteManager.confirmInternalDeletion()
    }

    fun cancelDeletion() {
        deleteManager.cancelDeletion()
    }

    private fun applyFiltersAndSort(state: CleanupReviewUiState): CleanupReviewUiState {
        val effectiveCategory = if (state.recommendations.none { it.category == state.selectedCategory }) {
            state.recommendations.map { it.category }.firstOrNull { it != CleanupCategory.NONE } ?: state.selectedCategory
        } else {
            state.selectedCategory
        }
        val filtered = state.recommendations.filter { it.category == effectiveCategory }
        
        val sorted = when (state.currentSort) {
            ReviewSort.LOWEST_KEEP_SCORE -> filtered.sortedWith(compareBy<CleanupRecommendation> { it.keepScore }.thenBy { it.mediaId })
            ReviewSort.HIGHEST_CONFIDENCE -> filtered.sortedWith(compareByDescending<CleanupRecommendation> { it.confidenceScore }.thenBy { it.mediaId })
            ReviewSort.LARGEST_STORAGE -> filtered.sortedWith(compareByDescending<CleanupRecommendation> { it.storageSize }.thenBy { it.mediaId })
            ReviewSort.MOST_EXPOSED -> filtered.sortedWith(compareByDescending<CleanupRecommendation> { it.exposureCount }.thenBy { it.mediaId })
        }
        
        return state.copy(
            selectedCategory = effectiveCategory,
            filteredRecommendations = sorted,
            storageRecoveryEstimate = sorted.sumOf { it.storageSize }
        )
    }
}
