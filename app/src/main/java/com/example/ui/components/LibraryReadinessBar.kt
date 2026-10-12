package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EnrichmentStatus
import com.example.data.MediaItem
import com.example.data.ScanProgressState
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSpacing

/**
 * Compact status strip: library scan + playable + visual-embedding coverage.
 */
@Composable
fun LibraryReadinessBar(
    mediaItems: List<MediaItem>,
    scanProgress: ScanProgressState,
    modifier: Modifier = Modifier
) {
    val total = mediaItems.size
    val playable = mediaItems.count {
        it.compatibilityStatus.name.startsWith("PLAYABLE") ||
            it.compatibilityStatus == com.example.data.CompatibilityStatus.PLAYABLE ||
            it.compatibilityStatus == com.example.data.CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE
    }
    val embedded = mediaItems.count {
        it.enrichmentStatus == EnrichmentStatus.COMPLETE ||
            it.enrichmentStatus == EnrichmentStatus.VISUAL_ONLY
    }
    val scanning = scanProgress.isScanning
    val scanLabel = if (scanning) {
        val done = scanProgress.processedCount
        val goal = scanProgress.totalCount.coerceAtLeast(done)
        "Scanning $done / $goal"
    } else null

    if (total == 0 && !scanning) return

    val embedFraction = if (playable > 0) embedded.toFloat() / playable.toFloat() else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.M, vertical = AuraSpacing.XXS),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("$total in library")
                        append(" · $playable playable")
                        append(" · $embedded embedded")
                        if (scanLabel != null) append(" · $scanLabel")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            if (playable > 0 && (embedded < playable || scanning)) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { embedFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AuraPurple,
                    trackColor = AuraPurple.copy(alpha = 0.15f),
                )
            }
        }
    }
}
