package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- AUTHORITATIVE NEUTRAL SYSTEM (LIGHT) ---
val AuraMidnight = Color(0xFF0F1117)
val AuraSlate = Color(0xFF374151)
val AuraMutedSlate = Color(0xFF6B7280)
val AuraCrispWhite = Color(0xFFFFFFFF)
val AuraSubtleSurface = Color(0xFFF9FAFB)
val AuraSubtleBorder = Color(0xFFE5E7EB)

// --- DARK NEUTRAL SYSTEM ---
val AuraDarkBackground = Color(0xFF0F1117)       // deep canvas
val AuraDarkSurface = Color(0xFF1A1D24)          // elevated surface
val AuraDarkSurfaceVariant = Color(0xFF252830)
val AuraDarkOnSurface = Color(0xFFF9FAFB)        // high-contrast text
val AuraDarkOnSurfaceVariant = Color(0xFF9CA3AF)
val AuraDarkBorder = Color(0xFF374151)
val AuraDarkSubtleBorder = Color(0xFF2D3139)

// --- DISCOVERY GRADIENT COMPONENTS (shared across themes) ---
val DiscoveryViolet = Color(0xFF8B5CF6)
val DiscoveryMagenta = Color(0xFFD946EF)
val DiscoveryHotPink = Color(0xFFEC4899)

// Official 3-color Discovery Gradient
val DiscoveryGradient = Brush.horizontalGradient(
    colors = listOf(DiscoveryViolet, DiscoveryMagenta, DiscoveryHotPink)
)

val DiscoveryGradientVertical = Brush.verticalGradient(
    colors = listOf(DiscoveryViolet, DiscoveryMagenta, DiscoveryHotPink)
)

// --- LEGACY COMPATIBILITY TOKENS ---
val AuraPurple = DiscoveryViolet
val AuraPurpleLight = DiscoveryViolet.copy(alpha = 0.7f)
val AuraPurpleDark = DiscoveryViolet
val AuraPurpleContainer = Color(0xFFF3E8FF)

val AuraMagenta = DiscoveryMagenta
val AuraMagentaDark = DiscoveryMagenta

val AuraBrandGradient = DiscoveryGradient
val AuraBrandGradientVertical = DiscoveryGradientVertical

// Light-default aliases (prefer MaterialTheme.colorScheme.* in new code)
val AuraBackground = AuraCrispWhite
val AuraSurface = AuraSubtleSurface
val AuraSurfaceVariant = Color(0xFFF3F4F6)
val AuraBorder = AuraSubtleBorder

val AuraOnSurface = AuraMidnight
val AuraOnSurfaceVariant = AuraMutedSlate
val AuraOutline = AuraMutedSlate

// Functional Colors
val AuraStarGold = Color(0xFFF59E0B)
val AuraSuccess = Color(0xFF10B981)
