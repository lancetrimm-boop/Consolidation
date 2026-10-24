package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.ui.theme.*

enum class MyStuffTab(val title: String) {
    PROFILE("Profile"),
    COLLECTIONS("Collections"),
    SAVED("Saved"),
    RECENTLY_WATCHED("Recently Watched"),
    SETTINGS("Settings")
}

@Composable
fun MyStuffScreen(
    repository: MediaRepository,
    mediaItems: List<MediaItem>,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onNavigateToFavorites: () -> Unit,
    onNavigateToCleanup: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onLaunchAuraMoments: () -> Unit,
    onMediaSelect: (MediaItem, List<MediaItem>) -> Unit,
    onCollectionSelect: (MediaItem, List<MediaItem>) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onSearch: (String) -> Unit,
    initialTab: MyStuffTab = MyStuffTab.PROFILE,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // My Stuff Top Bar Selector
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.M, vertical = AuraSpacing.S)
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your Intelligence Profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AuraSpacing.S))

                // Primary Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.S)
                ) {
                    MyStuffTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(AuraSpacing.S),
                            color = if (isSelected) DiscoveryViolet.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                brush = if (isSelected) DiscoveryGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (tab == MyStuffTab.SAVED) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Saved",
                                            tint = if (isSelected) DiscoveryViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else if (tab == MyStuffTab.RECENTLY_WATCHED) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.History else Icons.Outlined.History,
                                            contentDescription = "Recently Watched",
                                            tint = if (isSelected) DiscoveryViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) DiscoveryViolet else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.XS))

                // Second Navigation Row: Liked and Aura Moments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuraSpacing.S)
                ) {
                    Surface(
                        onClick = { onNavigateToFavorites() },
                        shape = RoundedCornerShape(AuraSpacing.S),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Liked",
                                tint = DiscoveryViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Liked",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        onClick = { onLaunchAuraMoments() },
                        shape = RoundedCornerShape(AuraSpacing.S),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Aura Moments",
                                tint = DiscoveryViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Aura Moments",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Move Visual Index indicator directly below the Profile button and above the Profile text
                val visualReadiness by repository.observeVisualIndexReadiness().collectAsStateWithLifecycle(null)
                visualReadiness?.let { readiness ->
                    Spacer(modifier = Modifier.height(AuraSpacing.S))
                    com.example.ui.components.VisualIndexReadinessPill(
                        readiness = readiness,
                        onRetryClick = { repository.retryFailedEnrichment() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val scanProgress by repository.scanProgress.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                Spacer(modifier = Modifier.height(AuraSpacing.S))

                Button(
                    onClick = {
                        if (!scanProgress.isScanning) {
                            coroutineScope.launch {
                                repository.scanLocalMedia(context, isManual = true)
                            }
                        }
                    },
                    enabled = !scanProgress.isScanning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AuraSpacing.S),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiscoveryViolet,
                        contentColor = AuraCrispWhite
                    )
                ) {
                    if (scanProgress.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AuraCrispWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Scanning Media...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(text = "Scan New Media", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (scanProgress.isComplete && scanProgress.isManual) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan complete: ${scanProgress.discoveredCount} new items discovered.",
                        fontSize = 11.sp,
                        color = AuraMutedSlate
                    )
                } else if (scanProgress.errorCode != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan failed: ${scanProgress.errorCode}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Selected Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                MyStuffTab.PROFILE -> {
                    ProfileScreen(
                        repository = repository,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onNavigateToFavorites = onNavigateToFavorites,
                        onNavigateToCleanup = onNavigateToCleanup,
                        onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
                        onNavigateToDiagnostics = onNavigateToDiagnostics,
                        onLaunchAuraMoments = onLaunchAuraMoments,
                        onMediaSelect = onMediaSelect
                    )
                }
                MyStuffTab.COLLECTIONS -> {
                    CollectionsScreen(
                        mediaItems = mediaItems,
                        repository = repository,
                        onCollectionSelect = onCollectionSelect,
                        onFavoriteToggle = onFavoriteToggle,
                        onLaunchAuraMoments = onLaunchAuraMoments,
                        onSearch = onSearch
                    )
                }
                MyStuffTab.SAVED -> {
                    SavedScreen(
                        repository = repository,
                        onMediaSelect = onMediaSelect,
                        onLike = onFavoriteToggle
                    )
                }
                MyStuffTab.RECENTLY_WATCHED -> {
                    RecentlyViewedScreen(
                        repository = repository,
                        onMediaSelect = { item, list -> onMediaSelect(item, list) },
                        onLike = onFavoriteToggle
                    )
                }
                MyStuffTab.SETTINGS -> {
                    ThemeSettingsContent(
                        currentMode = themeMode,
                        onModeSelected = onThemeModeChange
                    )
                }
            }
        }
    }
}
