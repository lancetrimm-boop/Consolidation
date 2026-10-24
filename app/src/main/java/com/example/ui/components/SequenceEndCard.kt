package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Channel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Overlay End Card displayed upon channel block exhaustion.
 * Features a 10-second countdown with Continue and Switch Channel actions.
 */
@Composable
fun SequenceEndCard(
    channel: Channel,
    onContinue: () -> Unit,
    onSwitchChannel: () -> Unit,
    modifier: Modifier = Modifier,
    initialCountdownSeconds: Int = 10
) {
    var secondsRemaining by remember(channel.id) { mutableStateOf(initialCountdownSeconds) }

    LaunchedEffect(channel.id) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
        onContinue()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(AuraSpacing.L),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(AuraSpacing.CornerRadiusMedium),
            color = AuraCrispWhite,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(AuraSpacing.L)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "End of Block",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiscoveryViolet,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(AuraSpacing.XS))

                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AuraSpacing.M))

                // Circular Countdown Progress Indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { secondsRemaining / initialCountdownSeconds.toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        color = DiscoveryViolet,
                        strokeWidth = 6.dp,
                        trackColor = AuraSubtleBorder
                    )
                    Text(
                        text = "$secondsRemaining",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = DiscoveryViolet
                    )
                }

                Spacer(modifier = Modifier.height(AuraSpacing.M))

                Text(
                    text = "Up Next: Next programmed block for ${channel.title} in ${secondsRemaining}s...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraMutedSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(AuraSpacing.L))

                // Action Buttons
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = "Continue playing ${channel.title}" }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(AuraSpacing.XS))
                    Text("Continue Channel", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(AuraSpacing.S))

                OutlinedButton(
                    onClick = onSwitchChannel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = "Switch to another channel" }
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(AuraSpacing.XS))
                    Text("Switch Channel", color = AuraMidnight)
                }
            }
        }
    }
}
