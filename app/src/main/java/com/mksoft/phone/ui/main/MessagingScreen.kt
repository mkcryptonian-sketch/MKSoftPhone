package com.mksoft.phone.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mksoft.phone.core.sip.SipChatMessage
import com.mksoft.phone.theme.GeminiPrimaryDark
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    messages: List<SipChatMessage>,
    onChatSelected: (String) -> Unit
) {
    val conversations = remember(messages) {
        messages.groupBy { it.peerUri }
            .mapValues { it.value.sortedByDescending { msg -> msg.timestamp } }
            .toList()
            .sortedByDescending { it.second.first().timestamp }
    }

    var showNewChatDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO: Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = GeminiPrimaryDark,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = GeminiPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No messages yet", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations) { (peerUri, chatMessages) ->
                        ConversationItem(
                            peerUri = peerUri,
                            lastMessage = chatMessages.first(),
                            unreadCount = chatMessages.count { !it.isRead && it.isIncoming },
                            onClick = { onChatSelected(peerUri) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            onDismiss = { showNewChatDialog = false },
            onConfirm = { peerUri ->
                showNewChatDialog = false
                val formattedUri = if (peerUri.startsWith("sip:")) peerUri else "sip:$peerUri"
                onChatSelected(formattedUri)
            }
        )
    }
}

@Composable
fun NewChatDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var peerUri by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Chat") },
        text = {
            Column {
                Text("Enter the SIP URI or number to chat with.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = peerUri,
                    onValueChange = { peerUri = it },
                    label = { Text("SIP URI or Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. 1001 or sip:1001@domain") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (peerUri.isNotBlank()) onConfirm(peerUri) },
                enabled = peerUri.isNotBlank()
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConversationItem(
    peerUri: String,
    lastMessage: SipChatMessage,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val timeStr = if (isToday(lastMessage.timestamp)) {
        timeFormat.format(Date(lastMessage.timestamp))
    } else {
        dateFormat.format(Date(lastMessage.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Placeholder
        Surface(
            modifier = Modifier.size(50.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    peerUri.substringAfter(":").take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = peerUri.substringAfter(":").substringBefore("@"),
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lastMessage.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (unreadCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

fun isToday(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance()
    cal2.timeInMillis = timestamp
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
