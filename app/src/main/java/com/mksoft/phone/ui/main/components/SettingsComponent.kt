package com.mksoft.phone.ui.main.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mksoft.phone.data.VoIpSettings
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiPrimaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VoIpSettings,
    onUpdateSettings: (VoIpSettings) -> Unit,
    serviceRunning: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    var localSettings by remember(settings) { mutableStateOf(settings) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("VoIP System Settings", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))

            // Service Status Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SIP Service Background Engine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            if (serviceRunning) "Status: ACTIVE" else "Status: STOPPED",
                            color = if (serviceRunning) DialerCallGreen else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleService(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DialerCallGreen
                        )
                    )
                }
            }

            // Network & Transport Card
            SettingsCard(title = "Network & Transport") {
                DropdownSettingsField(
                    label = "Transport Protocol",
                    options = listOf("TLS", "TCP", "UDP"),
                    selectedOption = localSettings.transportProtocol,
                    onOptionSelected = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        localSettings = localSettings.copy(transportProtocol = it) 
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = localSettings.stunServer,
                    onValueChange = { localSettings = localSettings.copy(stunServer = it) },
                    label = { Text("STUN Server") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = localSettings.turnServer,
                    onValueChange = { localSettings = localSettings.copy(turnServer = it) },
                    label = { Text("TURN Server") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = localSettings.iceEnabled, 
                        onCheckedChange = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localSettings = localSettings.copy(iceEnabled = it) 
                        }
                    )
                    Text("Enable ICE (Interactive Establishment)")
                }

                Text("Keep-Alive Interval: ${localSettings.keepAliveInterval}s")
                Slider(
                    value = localSettings.keepAliveInterval.toFloat(),
                    onValueChange = { 
                        localSettings = localSettings.copy(keepAliveInterval = it.toInt()) 
                    },
                    onValueChangeFinished = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 10f..120f,
                    steps = 11
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = localSettings.rportEnabled, 
                        onCheckedChange = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localSettings = localSettings.copy(rportEnabled = it) 
                        }
                    )
                    Text("Enable RPort (RFC 3581)")
                }
                
                DropdownSettingsField(
                    label = "IPv6 Preference",
                    options = listOf("Force IPv4", "Force IPv6", "Dual-stack"),
                    selectedOption = localSettings.ipv6Preference,
                    onOptionSelected = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        localSettings = localSettings.copy(ipv6Preference = it) 
                    }
                )
            }

            // Audio & Media Controls Card
            SettingsCard(title = "Audio & Media Controls") {
                DropdownSettingsField(
                    label = "DTMF Delivery Method",
                    options = listOf("RFC 2833", "SIP INFO", "In-band"),
                    selectedOption = localSettings.dtmfMethod,
                    onOptionSelected = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        localSettings = localSettings.copy(dtmfMethod = it) 
                    }
                )
                
                DropdownSettingsField(
                    label = "Echo Cancellation",
                    options = listOf("Hardware", "Software"),
                    selectedOption = localSettings.echoCancellationType,
                    onOptionSelected = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        localSettings = localSettings.copy(echoCancellationType = it) 
                    }
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = localSettings.aecEnabled, 
                        onCheckedChange = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localSettings = localSettings.copy(aecEnabled = it) 
                        }
                    )
                    Text("AEC Enabled")
                }
            }

            // Mobile OS Integrations Card
            SettingsCard(title = "Mobile OS Integrations") {
                ToggleRow("Native Call Integration", localSettings.nativeCallIntegrationEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(nativeCallIntegrationEnabled = it)
                }
                ToggleRow("Push Notifications (FCM)", localSettings.pushNotificationsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(pushNotificationsEnabled = it)
                }
                ToggleRow("DND Sync (Reject on Busy)", localSettings.dndSyncEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(dndSyncEnabled = it)
                }
            }

            // Enterprise Features Card
            SettingsCard(title = "Enterprise Features") {
                ToggleRow("Attended Transfer", localSettings.attendedTransferEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(attendedTransferEnabled = it)
                }
                ToggleRow("Presence (BLF)", localSettings.blfEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(blfEnabled = it)
                }
                ToggleRow("Proximity Sensor", localSettings.proximitySensorEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(proximitySensorEnabled = it)
                }
                ToggleRow("SIP Messaging", localSettings.sipMessagingEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(sipMessagingEnabled = it)
                }
            }

            // High-Grade Security Card
            SettingsCard(title = "High-Grade Security") {
                ToggleRow("LIME E2EE Encryption", localSettings.limeEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(limeEnabled = it)
                }
                ToggleRow("ZRTP SAS Display", localSettings.zrtpSasDisplayEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(zrtpSasDisplayEnabled = it)
                }
                ToggleRow("Post-Quantum Encryption", localSettings.postQuantumEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    localSettings = localSettings.copy(postQuantumEnabled = it)
                }
            }

            // Diagnostics Card
            SettingsCard(title = "Diagnostics") {
                Text("Native Log Level: ${localSettings.logLevel}")
                Slider(
                    value = localSettings.logLevel.toFloat(),
                    onValueChange = { 
                        localSettings = localSettings.copy(logLevel = it.toInt()) 
                    },
                    onValueChangeFinished = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 0f..6f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = GeminiPrimaryDark, activeTrackColor = GeminiPrimaryDark)
                )
            }

            Button(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUpdateSettings(localSettings)
                    scope.launch {
                        snackbarHostState.showSnackbar("All changes saved and applied!")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = GeminiPrimaryDark)
            ) {
                Text("Save All Changes", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSettingsField(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
