package com.example.data

enum class ExposureEventType {
    PREVIEW_SEEN,
    PLAYBACK_STARTED,
    PLAYBACK_PROGRESS,
    PLAYBACK_COMPLETED
}

enum class ExposureProgressMilestone(val percentage: Int) {
    PERCENT_25(25),
    PERCENT_50(50),
    PERCENT_75(75);

    companion object {
        fun fromPercentage(percent: Int): ExposureProgressMilestone? {
            return entries.find { it.percentage == percent }
        }
    }
}
