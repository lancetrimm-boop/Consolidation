package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutoScrollSpeed
import com.example.ui.theme.*

/**
 * Shared Sort Selector component used in Library and Compare.
 */
@Composable
fun <T> AuraSortSelector(
    label: String,
    currentOption: String,
    isSelected: Boolean,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    getDisplayName: (T) -> String,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = DiscoveryViolet
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { 
                onPillClick()
                expanded = true 
            },
            shape = RoundedCornerShape(AuraSpacing.S),
            modifier = Modifier.height(32.dp),
            color = if (isSelected) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = if (isSelected) DiscoveryGradient else SolidColor(MaterialTheme.colorScheme.outlineVariant)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AuraSpacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$label: $currentOption",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                val name = getDisplayName(option)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            color = if (currentOption == name) selectedColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (currentOption == name) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Shared Filter Chip component used in Library and Compare.
 */
@Composable
fun AuraFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = DiscoveryViolet
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AuraSpacing.S),
        modifier = modifier.height(32.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = if (isSelected) DiscoveryGradient else SolidColor(MaterialTheme.colorScheme.outlineVariant)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = AuraSpacing.M),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Auto-Scroll Toggle for Library
 */
@Composable
fun AutoScrollToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) DiscoveryViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        label = "auto_scroll_bg"
    )
    val borderColor = if (isEnabled) DiscoveryGradient else SolidColor(MaterialTheme.colorScheme.outlineVariant)
    val textColor = if (isEnabled) DiscoveryViolet else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = { onToggle(!isEnabled) },
        shape = CircleShape,
        modifier = modifier.height(28.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(if (isEnabled) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AuraSpacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = textColor
            )
            Spacer(modifier = Modifier.width(AuraSpacing.XXS))
            Text(
                text = if (isEnabled) "AUTO-SCROLL ON" else "AUTO-SCROLL OFF",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Auto-Scroll Speed Selector for Library
 */
@Composable
fun AutoScrollSpeedSelector(
    currentSpeed: AutoScrollSpeed,
    onSpeedSelected: (AutoScrollSpeed) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            modifier = Modifier.height(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AuraSpacing.XS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentSpeed.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            AutoScrollSpeed.entries.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = speed.label,
                            color = if (currentSpeed == speed) DiscoveryViolet else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    }
                )
            }
        }
    }
}
