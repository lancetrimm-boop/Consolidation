package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.VisualIndexReadiness
import com.example.ui.theme.DiscoveryViolet
import java.util.Locale

@Composable
fun VisualSearchWarningDialog(
    readiness: VisualIndexReadiness,
    onDismiss: () -> Unit,
    onContinue: (suppress: Boolean) -> Unit
) {
    var doNotRemindAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Preparing Visual Search", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val pctFloat = if (readiness.totalEligible > 0) (readiness.verifiedVisualCount * 100.0f) / readiness.totalEligible else 0f
                val pctString = if (readiness.verifiedVisualCount > 0 && pctFloat < 1.0f) {
                    String.format(Locale.US, "%.1f%%", pctFloat)
                } else {
                    "${pctFloat.toInt()}%"
                }
                Text(
                    text = "Current progress: ${readiness.verifiedVisualCount} of ${readiness.totalEligible} items indexed ($pctString).",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DiscoveryViolet
                )
                Text(
                    text = "Why this matters: Visual indexing creates the visual representations Aura uses to compare images and discover visually similar media. More eligible items indexed generally gives Aura a broader candidate pool for these searches.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Performance notice: See Similar and Visual Search can run before indexing completes, but results may be limited because unindexed media cannot yet participate fully. Results can improve as indexing progresses.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { doNotRemindAgain = !doNotRemindAgain }
                ) {
                    Checkbox(
                        checked = doNotRemindAgain,
                        onCheckedChange = { doNotRemindAgain = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Do not remind me again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onContinue(doNotRemindAgain) }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
