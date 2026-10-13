package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraSpacing

@Composable
fun AuraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showLogo: Boolean = true,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subtitle != null) 64.dp else 56.dp)
                .padding(horizontal = AuraSpacing.XS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(AuraSpacing.XS))
            } else if (showLogo) {
                AuraLogoIcon(size = 24.dp)
                Spacer(modifier = Modifier.width(AuraSpacing.S))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showLogo) title else title.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = if (showLogo) (-0.2).sp else 1.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            } else {
                IconButton(onClick = { /* Default search action */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // Authoritative Bottom Border
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
fun VisualIndexReadinessPill(
    readiness: com.example.data.VisualIndexReadiness,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, dotColor) = when (readiness.state) {
        com.example.data.VisualIndexState.GREEN_READY -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            androidx.compose.ui.graphics.Color(0xFF2E7D32)
        )
        com.example.data.VisualIndexState.YELLOW_INDEXING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            androidx.compose.ui.graphics.Color(0xFFF57F17)
        )
        com.example.data.VisualIndexState.RED_INCOMPLETE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        com.example.data.VisualIndexState.NEUTRAL_EMPTY -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            androidx.compose.ui.graphics.Color.Gray
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .then(
                if (readiness.state == com.example.data.VisualIndexState.RED_INCOMPLETE) {
                    Modifier.foundationClickable(onRetryClick)
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                text = readiness.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Modifier.foundationClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)
