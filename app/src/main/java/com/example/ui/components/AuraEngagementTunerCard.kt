package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TasteDNA
import com.example.ui.theme.AuraSpacing
import com.example.ui.theme.DiscoveryViolet

@Composable
fun AuraEngagementTunerCard(
    tasteDNA: TasteDNA,
    preferenceProfile: TasteDNA.PreferenceProfile,
    onTasteDnaUpdate: (TasteDNA) -> Unit,
    onPreferenceProfileUpdate: (TasteDNA.PreferenceProfile) -> Unit,
    modifier: Modifier = Modifier,
    aiDescription: String? = null,
    showWeightsAtTop: Boolean = false,
    collapsibleSliders: Boolean = false,
    initialSlidersExpanded: Boolean = false,
    isDiscoverContext: Boolean = false
) {
    val learningStatus = when {
        !tasteDNA.isFineTuningEnabled -> "Learning Paused"
        preferenceProfile.interactionsCount < 5 -> "Preferences Calibrating"
        else -> "Learning Active"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuraSpacing.CornerRadiusMedium)),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(AuraSpacing.L)) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI LEARNING STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = learningStatus.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = if (tasteDNA.isFineTuningEnabled) DiscoveryViolet else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AuraSpacing.M))

            TasteDnaControlPanel(
                tasteDNA = tasteDNA,
                preferenceProfile = preferenceProfile,
                onTasteDnaUpdate = onTasteDnaUpdate,
                onPreferenceProfileUpdate = onPreferenceProfileUpdate,
                modifier = Modifier.padding(0.dp),
                isEmbedded = true,
                aiDescription = aiDescription,
                showWeightsAtTop = showWeightsAtTop,
                collapsibleSliders = collapsibleSliders,
                initialSlidersExpanded = initialSlidersExpanded,
                isDiscoverContext = isDiscoverContext
            )
        }
    }
}
