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

@Composable
fun HistoryScreen(
    history: List<CallHistoryEntry>,
    onClear: () -> Unit,
    onCall: (String) -> Unit,
    onChat: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Logs",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { entry ->
                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(entry.timestamp))
                    
                    Card(
                        onClick = { onCall(entry.number) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (entry.isIncoming) {
                                            if (entry.wasAnswered) DialerCallGreen.copy(alpha = 0.1f)
                                            else DialerEndRed.copy(alpha = 0.1f)
                                        } else {
                                            GeminiPrimaryDark.copy(alpha = 0.1f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (entry.isIncoming) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                                    contentDescription = null,
                                    tint = if (entry.isIncoming) {
                                        if (entry.wasAnswered) DialerCallGreen else DialerEndRed
                                    } else {
                                        GeminiPrimaryDark
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.number.substringAfter("sip:"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "$dateStr • ${if (entry.duration > 0) "${entry.duration}s" else if (entry.wasAnswered) "Connected" else "Missed"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onChat(entry.number) }) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = GeminiPrimaryDark)
                                }
                                IconButton(onClick = { onCall(entry.number) }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Call Back", tint = GeminiPrimaryDark)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
