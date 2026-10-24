package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoScrollSpeed(val label: String, val pixelsPerSecond: Int) {
    SLOW("Slow", 50),
    MEDIUM("Medium", 120),
    FAST("Fast", 270)
}

enum class FirstRunScanState {
    NEVER_REQUESTED,
    DENIED,
    GRANTED_SCAN_PENDING,
    INITIAL_SCAN_COMPLETE,
    PERMISSION_REVOKED
}

class LibraryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("aura_library_prefs", Context.MODE_PRIVATE)

    private val _gridDensity = MutableStateFlow(prefs.getFloat(KEY_GRID_DENSITY, 160f))
    val gridDensity: StateFlow<Float> = _gridDensity.asStateFlow()

    private val _autoScrollSpeed = MutableStateFlow(
        AutoScrollSpeed.entries.getOrNull(prefs.getInt(KEY_AUTOSCROLL_SPEED, 1)) ?: AutoScrollSpeed.MEDIUM
    )
    val autoScrollSpeed: StateFlow<AutoScrollSpeed> = _autoScrollSpeed.asStateFlow()

    private val _firstRunScanState = MutableStateFlow(
        try {
            FirstRunScanState.valueOf(
                prefs.getString(KEY_FIRST_RUN_SCAN_STATE, FirstRunScanState.NEVER_REQUESTED.name)
                    ?: FirstRunScanState.NEVER_REQUESTED.name
            )
        } catch (e: Exception) {
            FirstRunScanState.NEVER_REQUESTED
        }
    )
    val firstRunScanState: StateFlow<FirstRunScanState> = _firstRunScanState.asStateFlow()

    fun setGridDensity(density: Float) {
        val clamped = density.coerceIn(80f, 360f)
        if (_gridDensity.value != clamped) {
            _gridDensity.value = clamped
            prefs.edit().putFloat(KEY_GRID_DENSITY, clamped).apply()
        }
    }

    fun setAutoScrollSpeed(speed: AutoScrollSpeed) {
        if (_autoScrollSpeed.value != speed) {
            _autoScrollSpeed.value = speed
            prefs.edit().putInt(KEY_AUTOSCROLL_SPEED, speed.ordinal).apply()
        }
    }

    fun setFirstRunScanState(state: FirstRunScanState) {
        if (_firstRunScanState.value != state) {
            _firstRunScanState.value = state
            prefs.edit().putString(KEY_FIRST_RUN_SCAN_STATE, state.name).apply()
        }
    }

    fun getVisualIndexPipelineVersion(): Int = prefs.getInt(KEY_VISUAL_INDEX_PIPELINE_VERSION, 0)

    fun setVisualIndexPipelineVersion(version: Int) {
        prefs.edit().putInt(KEY_VISUAL_INDEX_PIPELINE_VERSION, version).apply()
    }

    fun isVisualSearchWarningSuppressed(): Boolean = prefs.getBoolean(KEY_VISUAL_SEARCH_WARNING_SUPPRESSED, false)

    fun setVisualSearchWarningSuppressed(suppressed: Boolean) {
        prefs.edit().putBoolean(KEY_VISUAL_SEARCH_WARNING_SUPPRESSED, suppressed).apply()
    }

    companion object {
        private const val KEY_GRID_DENSITY = "library_grid_density"
        private const val KEY_AUTOSCROLL_SPEED = "library_autoscroll_speed"
        private const val KEY_FIRST_RUN_SCAN_STATE = "first_run_scan_state"
        private const val KEY_VISUAL_INDEX_PIPELINE_VERSION = "visual_index_pipeline_version"
        private const val KEY_VISUAL_SEARCH_WARNING_SUPPRESSED = "visual_search_warning_suppressed"
    }
}
