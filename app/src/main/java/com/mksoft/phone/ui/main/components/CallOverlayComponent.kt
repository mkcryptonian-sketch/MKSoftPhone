package com.mksoft.phone.ui.main.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mksoft.phone.core.sip.CallStats
import com.mksoft.phone.core.sip.CallWrapper
import com.mksoft.phone.core.sip.SipCallState
import com.mksoft.phone.ui.main.MainScreenViewModel
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.DialerEndRed
import com.mksoft.phone.theme.GeminiPrimaryDark
import kotlinx.coroutines.delay

@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = GeminiPrimaryDark,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (isActive) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = if (isActive) BorderStroke(1.dp, activeColor) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WaveformVisualizer(
    isCalling: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bars = 8
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index * 100),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp * (if (isCalling) heightScale else 0.2f))
                    .background(
                        color = if (isCalling) GeminiPrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun ActiveCallOverlay(
    activeCalls: Map<Int, CallWrapper>,
    isConferenceActive: Boolean,
    callStats: Map<Int, CallStats> = emptyMap(),
    transferSession: MainScreenViewModel.TransferSession? = null,
    onAnswer: (Int) -> Unit,
    onHangup: (Int) -> Unit,
    onHold: (Int, Boolean) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onToggleSpeaker: (Int, Boolean) -> Unit,
    onToggleBluetooth: (Int, Boolean) -> Unit,
    onToggleRecord: (Int, Boolean) -> Unit,
    onSendDtmf: (Int, String) -> Unit,
    onAddCall: () -> Unit,
    onConference: () -> Unit,
    onTransferCall: (Int, String) -> Unit,
    onCompleteTransfer: () -> Unit,
    onCancelTransfer: () -> Unit
) {
    var showDialpad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCallStats by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferDestination by remember { mutableStateOf("") }
    var selectedCallId by remember(activeCalls) { 
        mutableStateOf(activeCalls.keys.firstOrNull() ?: -1) 
    }

    LaunchedEffect(activeCalls) {
        if (selectedCallId == -1 || !activeCalls.containsKey(selectedCallId)) {
            selectedCallId = activeCalls.keys.firstOrNull() ?: -1
        }
    }

    LaunchedEffect(transferSession) {
        transferSession?.targetCallId?.let { 
            selectedCallId = it
        }
    }
    
    val call = activeCalls[selectedCallId] ?: activeCalls.values.firstOrNull() ?: return
    var durationText by remember { mutableStateOf("00:00") }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(call.connectTimestamp, call.callState) {
        if (call.callState == SipCallState.Confirmed && call.connectTimestamp != null) {
            while (true) {
                val elapsedMs = System.currentTimeMillis() - call.connectTimestamp
                val totalSecs = elapsedMs / 1000
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                durationText = String.format("%02d:%02d", mins, secs)
                delay(1000)
            }
        } else {
            durationText = "00:00"
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale1 by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse1"
        )
        val pulseAlpha1 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha1"
        )

        val pulseScale2 by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, delayMillis = 600, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse2"
        )
        val pulseAlpha2 by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, delayMillis = 600, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha2"
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- TOP SECTION: Glassmorphic (Active Call Info & Icons) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Call Details Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val displayText = if (isConferenceActive) {
                                val numbers = activeCalls.values.map { it.peerUri.substringAfter("sip:") }
                                "Conference: " + numbers.joinToString(", ")
                            } else {
                                call.peerUri.substringAfter("sip:")
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (call.callState == SipCallState.Confirmed) durationText else when (call.callState) {
                                    SipCallState.Idle -> "Idle"
                                    SipCallState.Incoming -> "Incoming Call..."
                                    SipCallState.Outgoing -> "Dialing..."
                                    SipCallState.Connecting -> "Connecting..."
                                    SipCallState.Confirmed -> "Active Call"
                                    SipCallState.Disconnected -> "Disconnected"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (call.callState == SipCallState.Confirmed) DialerCallGreen else GeminiPrimaryDark
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Secure",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.NetworkCell,
                                contentDescription = "Signal",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 3x3 Action Grid wrapped in a translucent card dock
                    if (call.callState == SipCallState.Confirmed) {
                        Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 1. Mute
                                val isMuted = call.isMuted
                                CallActionButton(
                                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    label = "Mute",
                                    isActive = isMuted,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMute(call.callId, !isMuted)
                                    }
                                )

                                // 2. Keypad (DTMF)
                                CallActionButton(
                                    icon = Icons.Filled.Dialpad,
                                    label = "Keypad",
                                    isActive = showDialpad,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showDialpad = !showDialpad
                                    }
                                )

                                // 3. Speakerphone
                                val isSpeakerOn = call.isSpeakerphoneOn
                                CallActionButton(
                                    icon = Icons.Filled.VolumeUp,
                                    label = "Speaker",
                                    isActive = isSpeakerOn,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSpeaker(call.callId, !isSpeakerOn)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 4. Hold
                                val isOnHold = call.isLocalHold
                                CallActionButton(
                                    icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    label = if (isOnHold) "Resume" else "Hold",
                                    isActive = isOnHold,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onHold(call.callId, !isOnHold)
                                    }
                                )

                                // 5. Add Call or Merge Calls
                                if (activeCalls.size > 1) {
                                    CallActionButton(
                                        icon = Icons.Filled.CallMerge,
                                        label = "Merge",
                                        isActive = false,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onConference()
                                        }
                                    )
                                } else {
                                    CallActionButton(
                                        icon = Icons.Filled.Add,
                                        label = "Add Call",
                                        isActive = false,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAddCall()
                                        }
                                    )
                                }

                                // 6. Record
                                val isRecording = call.isRecording
                                CallActionButton(
                                    icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                    label = if (isRecording) "Recording" else "Record",
                                    isActive = isRecording,
                                    activeColor = Color.Red,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleRecord(call.callId, !isRecording)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 7. Bluetooth
                                val isBluetoothOn = call.isBluetoothOn
                                val isBluetoothAvailable = call.isBluetoothAvailable
                                Box(modifier = if (!isBluetoothAvailable) Modifier.graphicsLayer { alpha = 0.5f } else Modifier) {
                                    CallActionButton(
                                        icon = Icons.Filled.Bluetooth,
                                        label = "Bluetooth",
                                        isActive = isBluetoothOn && isBluetoothAvailable,
                                        onClick = {
                                            if (isBluetoothAvailable) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleBluetooth(call.callId, !isBluetoothOn)
                                            } else {
                                                android.widget.Toast.makeText(context, "No Bluetooth device available", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }

                                // 8. Transfer Call
                                CallActionButton(
                                    icon = Icons.Filled.PhoneForwarded,
                                    label = "Transfer",
                                    isActive = showTransferDialog,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTransferDialog = true
                                    }
                                )

                                // 9. Call Stats
                                CallActionButton(
                                    icon = Icons.Filled.Analytics,
                                    label = "Statistics",
                                    isActive = showCallStats,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCallStats = true
                                    }
                                )
                            }
                        }
                    }
                    }
                }

                // --- BOTTOM SECTION: 72% Height (Remaining) ---
                if (showDialpad && call.callState == SipCallState.Confirmed) {
                    // DTMF dialpad overlay (Dark space background, glassmorphic layout)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Display typed DTMF digits & Backspace
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dtmfDigits,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (dtmfDigits.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dtmfDigits = dtmfDigits.dropLast(1)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Grid
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            val rows = listOf(
                                listOf("1" to "oo", "2" to "ABC", "3" to "DEF"),
                                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                                listOf("*" to "", "0" to "+", "#" to "")
                            )
                            rows.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    row.forEach { (digit, letters) ->
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onSendDtmf(call.callId, digit)
                                                    dtmfDigits += digit
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = digit,
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Normal,
                                                        fontSize = 26.sp,
                                                        lineHeight = 26.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (letters.isNotEmpty()) {
                                                    Text(
                                                        text = letters,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Normal,
                                                            fontSize = 9.sp,
                                                            letterSpacing = 0.5.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    // Default active call screen (Subtle dark background with pulsing voice activity avatar)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Animating avatar silhouette in center representing call activity
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Pulsing outer ring 2
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .graphicsLayer {
                                            scaleX = pulseScale2
                                            scaleY = pulseScale2
                                            alpha = pulseAlpha2
                                        }
                                        .background(GeminiPrimaryDark.copy(alpha = 0.6f), CircleShape)
                                )
                                
                                // Pulsing outer ring 1
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .graphicsLayer {
                                            scaleX = pulseScale1
                                            scaleY = pulseScale1
                                            alpha = pulseAlpha1
                                        }
                                        .background(GeminiPrimaryDark.copy(alpha = 0.8f), CircleShape)
                                )

                                // Solid core avatar container
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(2.dp, GeminiPrimaryDark.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = "Profile",
                                        tint = GeminiPrimaryDark.copy(alpha = 0.7f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }

                        // Hangup / Answer Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (transferSession != null && transferSession.targetCallId == call.callId && call.callState == SipCallState.Confirmed) {
                                // Transfer Confirmation UI
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        "Call Answered. Complete Transfer?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                        Button(
                                            onClick = onCompleteTransfer,
                                            colors = ButtonDefaults.buttonColors(containerColor = DialerCallGreen),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Confirm")
                                        }
                                        Button(
                                            onClick = onCancelTransfer,
                                            colors = ButtonDefaults.buttonColors(containerColor = DialerEndRed),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Cancel")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onHangup(call.callId) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DialerEndRed,
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.size(80.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CallEnd,
                                        contentDescription = "End Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                if (call.callState == SipCallState.Incoming) {
                                    Button(
                                        onClick = { onAnswer(call.callId) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DialerCallGreen,
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier.size(80.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Answer Call",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (showCallStats) {
                    val currentStats = callStats[call.callId] ?: CallStats(callId = call.callId)
                    CallStatsDialog(
                        stats = currentStats,
                        onDismiss = { showCallStats = false }
                    )
                }
                if (showTransferDialog) {
                    AlertDialog(
                        onDismissRequest = { showTransferDialog = false },
                        title = { Text("Transfer Call", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter the extension number or SIP URI to transfer this call to.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = transferDestination,
                                    onValueChange = { transferDestination = it },
                                    label = { Text("Destination") },
                                    placeholder = { Text("e.g. 1002 or sip:1002@domain.com") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (transferDestination.isNotEmpty()) {
                                        onTransferCall(call.callId, transferDestination)
                                        showTransferDialog = false
                                        transferDestination = ""
                                    }
                                }
                            ) {
                                Text("Transfer", color = GeminiPrimaryDark, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showTransferDialog = false
                                    transferDestination = ""
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveCallBar(
    activeCalls: Map<Int, CallWrapper>,
    isConferenceActive: Boolean,
    onClick: () -> Unit
) {
    val call = activeCalls.values.firstOrNull() ?: return
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = GeminiPrimaryDark,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val displayText = if (isConferenceActive) {
                    val numbers = activeCalls.values.map { it.peerUri.substringAfter("sip:") }
                    "Conference: " + numbers.joinToString(", ")
                } else {
                    call.peerUri.substringAfter("sip:")
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to return to call",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            WaveformVisualizer(isCalling = call.callState == SipCallState.Confirmed)
        }
    }
}

@Composable
fun CallStatsDialog(
    stats: CallStats,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Call Quality Statistics",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quality score badge
                val qualityColor = when {
                    stats.qualityScore >= 80 -> DialerCallGreen
                    stats.qualityScore >= 50 -> Color(0xFFFFB300)  // Amber
                    else                     -> DialerEndRed
                }
                val qualityLabel = when {
                    stats.qualityScore >= 80 -> "Good"
                    stats.qualityScore >= 50 -> "Fair"
                    else                     -> "Poor"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quality:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = qualityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$qualityLabel (${stats.qualityScore}%)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = qualityColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        val codecDisplay = if (stats.clockRate > 0L)
                            "${stats.codecName} / ${stats.clockRate / 1000}kHz"
                        else
                            stats.codecName
                        StatRow("Codec", codecDisplay)
                    }
                    item {
                        StatRow("Security", stats.securityProto)
                    }
                    item {
                        StatRow("RTT (Latency)", "${stats.rttMs} ms")
                    }
                    item {
                        StatRow("RX Jitter", "${stats.rxJitterMs} ms")
                    }
                    item {
                        StatRow("TX Jitter", "${stats.txJitterMs} ms")
                    }
                    item {
                        // rxLoss is stored as a percentage integer (0-100)
                        StatRow("RX Packet Loss", "${stats.rxLoss}%")
                    }
                    item {
                        StatRow("TX Packet Loss", "${stats.txLoss}%")
                    }
                    item {
                        StatRow("Jitter Buffer", "${stats.jbCurrentSize} ms")
                    }
                    item {
                        // txBytes here is bytes-per-second from bandwidth reading
                        StatRow("Upload", "${formatBps(stats.txBytes)}")
                    }
                    item {
                        StatRow("Download", "${formatBps(stats.rxBytes)}")
                    }
                    if (stats.localRtpAddress.isNotBlank()) {
                        item {
                            StatRow("Local RTP", stats.localRtpAddress)
                        }
                    }
                    if (stats.remoteRtpAddress.isNotBlank()) {
                        item {
                            StatRow("Remote RTP", stats.remoteRtpAddress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Format bytes/second as a human-readable bitrate string. */
fun formatBps(bytesPerSec: Long): String {
    val bps = bytesPerSec * 8L  // convert to bits/s
    return when {
        bps < 1_000      -> "${bps} bps"
        bps < 1_000_000  -> String.format("%.1f kbps", bps / 1_000.0)
        else             -> String.format("%.2f Mbps", bps / 1_000_000.0)
    }
}
