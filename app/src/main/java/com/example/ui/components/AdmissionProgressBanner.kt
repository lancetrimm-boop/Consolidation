package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.mediastore.AdmissionProgress
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.DiscoveryViolet

@Composable
fun AdmissionProgressBanner(
    progress: AdmissionProgress,
    modifier: Modifier = Modifier
) {
    if (progress.isAnalysisComplete) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DiscoveryViolet.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Media Admission in Progress",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuraMidnight
            )
            Text(
                text = "${progress.totalAdmitted} Admitted (${progress.totalScanned} Scanned)",
                fontSize = 12.sp,
                color = AuraMutedSlate
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        val fraction = if (progress.totalScanned > 0) {
            (progress.totalAdmitted.toFloat() / progress.totalScanned.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = DiscoveryViolet,
            trackColor = DiscoveryViolet.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun LibraryScanningPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AuraLogoIcon(size = 64.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning & Admitting Media...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Evaluating media compatibility and generating verified items for your library.",
                fontSize = 14.sp,
                color = AuraMutedSlate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
