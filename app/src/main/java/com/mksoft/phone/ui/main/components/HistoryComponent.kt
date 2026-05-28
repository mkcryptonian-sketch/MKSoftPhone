package com.mksoft.phone.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mksoft.phone.data.CallHistoryEntry
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.DialerEndRed
import com.mksoft.phone.theme.GeminiPrimaryDark
import java.text.SimpleDateFormat
import java.util.*

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Format seconds into "m:ss" (e.g. 127 → "2:07"). */
private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0:00"
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/**
 * Returns a human-readable date bucket label for a given timestamp:
 * "Today", "Yesterday", or "Mon dd" for older dates.
 */
private fun dateBucket(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"

        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(
    history: List<CallHistoryEntry>,
    onClear: () -> Unit,
    onCall: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Calls",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No recent calls",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Group entries by date bucket, preserve desc order within each group
            val grouped: Map<String, List<CallHistoryEntry>> = history
                .sortedByDescending { it.timestamp }
                .groupBy { dateBucket(it.timestamp) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                grouped.forEach { (bucket, entries) ->
                    // Date section header
                    item(key = "header_$bucket") {
                        Text(
                            text = bucket,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(entries, key = { it.id }) { entry ->
                        CallHistoryItem(
                            entry = entry,
                            onCall = { onCall(entry.number) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ─── Single row ──────────────────────────────────────────────────────────────

@Composable
private fun CallHistoryItem(
    entry: CallHistoryEntry,
    onCall: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(entry.timestamp))

    // ── Determine call type ───────────────────────────────────────────────
    val isMissed    = entry.isIncoming && !entry.wasAnswered
    val isOutNoAns  = !entry.isIncoming && !entry.wasAnswered  // outgoing, not answered

    val iconColor = when {
        isMissed   -> DialerEndRed
        isOutNoAns -> MaterialTheme.colorScheme.onSurfaceVariant
        entry.isIncoming -> DialerCallGreen
        else             -> GeminiPrimaryDark  // outgoing answered
    }

    val icon = when {
        isMissed -> Icons.Filled.PhoneMissed
        entry.isIncoming -> Icons.AutoMirrored.Filled.CallReceived
        else -> Icons.AutoMirrored.Filled.CallMade
    }

    // ── Subtitle: time + duration/status ─────────────────────────────────
    val statusText = when {
        entry.wasAnswered && entry.duration > 0 -> "${timeStr} · ${formatDuration(entry.duration)}"
        entry.wasAnswered                        -> "${timeStr} · Connected"
        isMissed                                 -> "${timeStr} · Missed"
        isOutNoAns                               -> "${timeStr} · Not answered"
        else                                     -> timeStr
    }

    Card(
        onClick = onCall,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Show only the user/extension part — omit @domain
                    text = entry.number.substringBefore("@"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isMissed) DialerEndRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action button
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call Back",
                        tint = GeminiPrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
