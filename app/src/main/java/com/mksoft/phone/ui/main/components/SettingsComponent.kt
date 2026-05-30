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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.mksoft.phone.data.SipCodecConfig
import com.mksoft.phone.data.VoIpSettings
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiPrimaryDark
import com.mksoft.phone.theme.SettingsAccentPurple
import com.mksoft.phone.theme.SettingsAccentPurpleLight
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Settings navigation sections
// ─────────────────────────────────────────────────────────────
enum class SettingsSection(val label: String, val icon: ImageVector) {
    Identity("Identity", Icons.Default.Person),
    Server("Server", Icons.Default.Dns),
    Audio("Audio", Icons.Default.Headphones),
    Network("Network", Icons.Default.Wifi),
    Security("Security", Icons.Default.Shield),
    Advanced("Advanced", Icons.Default.Tune)
}

// ─────────────────────────────────────────────────────────────
//  Main Entry Point — signature updated to take selectedSection
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VoIpSettings,
    onUpdateSettings: (VoIpSettings) -> Unit,
    serviceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
    selectedSection: SettingsSection
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedSection) {
                SettingsSection.Identity -> IdentityPanel(
                    localSettings = localSettings,
                    serviceRunning = serviceRunning,
                    onToggleService = { running ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleService(running)
                    },
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Identity settings saved!") }
                    }
                )

                SettingsSection.Server -> ServerPanel(
                    localSettings = localSettings,
                    onUpdate = { localSettings = it },
                    haptic = haptic,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Server settings saved!") }
                    }
                )

                SettingsSection.Audio -> AudioPanel(
                    localSettings = localSettings,
                    onUpdate = { localSettings = it },
                    haptic = haptic,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Audio settings saved!") }
                    }
                )

                SettingsSection.Network -> NetworkPanel(
                    localSettings = localSettings,
                    onUpdate = { localSettings = it },
                    haptic = haptic,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Network settings saved!") }
                    }
                )

                SettingsSection.Security -> SecurityPanel(
                    localSettings = localSettings,
                    onUpdate = { localSettings = it },
                    haptic = haptic,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Security settings saved!") }
                    }
                )

                SettingsSection.Advanced -> AdvancedPanel(
                    localSettings = localSettings,
                    onUpdate = { localSettings = it },
                    haptic = haptic,
                    onSave = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpdateSettings(localSettings)
                        scope.launch { snackbarHostState.showSnackbar("Advanced settings saved!") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════
//  IDENTITY PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun IdentityPanel(
    localSettings: VoIpSettings,
    serviceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    // Status bar
    SettingsStatusBar(
        dotColor = if (serviceRunning) Color(0xFF639922) else Color(0xFFE24B4A),
        text = if (serviceRunning) "SIP Service — ACTIVE" else "SIP Service — STOPPED",
        badgeText = if (serviceRunning) "Connected" else "Offline",
        badgeType = if (serviceRunning) BadgeType.Ok else BadgeType.Error
    )

    Spacer(modifier = Modifier.height(16.dp))

    SectionHeader(
        title = "SIP Service",
        subtitle = "Background engine and service status"
    )

    SettingsToggleRow(
        label = "SIP Service Engine",
        description = "Keep the SIP stack running in background",
        checked = serviceRunning,
        onCheckedChange = onToggleService
    )

    SettingsDivider()

    SectionHeader(
        title = "Identity",
        subtitle = "Your SIP account credentials and display information"
    )

    // Display-only info about the current settings state
    SettingsInfoRow(label = "Auto-start on boot", value = if (localSettings.autoStartOnBoot) "Enabled" else "Disabled")
    SettingsInfoRow(label = "Registration expiry", value = "${localSettings.registrationExpiry}s")
    SettingsInfoRow(label = "Outbound proxy", value = localSettings.outboundProxy.ifBlank { "Not set" })
    SettingsInfoRow(label = "Local domain", value = localSettings.localDomain.ifBlank { "Not set" })

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}

// ═════════════════════════════════════════════════════════════
//  SERVER & TRANSPORT PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun ServerPanel(
    localSettings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSave: () -> Unit
) {
    SectionHeader(
        title = "Server & Transport",
        subtitle = "Transport protocol, proxy, and connection settings"
    )

    DropdownSettingsField(
        label = "Transport Protocol",
        options = listOf("TLS", "TCP", "UDP"),
        selectedOption = localSettings.transportProtocol,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(transportProtocol = it))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = localSettings.stunServer,
        onValueChange = { onUpdate(localSettings.copy(stunServer = it)) },
        label = { Text("STUN Server") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
    SettingsFieldHint("Used for NAT traversal and public IP discovery")

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = localSettings.turnServer,
        onValueChange = { onUpdate(localSettings.copy(turnServer = it)) },
        label = { Text("TURN Server") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
    SettingsFieldHint("Relay server for symmetric NAT scenarios")

    SettingsDivider()

    SettingsToggleRow(
        label = "Keep-alive",
        description = "Send OPTIONS ping every ${localSettings.keepAliveInterval}s to keep NAT alive",
        checked = localSettings.keepAliveInterval > 0,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // toggle between 0 (disabled) and 30 (default)
            onUpdate(localSettings.copy(keepAliveInterval = if (it) 30 else 0))
        }
    )

    // Keep-alive interval slider
    if (localSettings.keepAliveInterval > 0) {
        Text(
            "Interval: ${localSettings.keepAliveInterval}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Slider(
            value = localSettings.keepAliveInterval.toFloat(),
            onValueChange = { onUpdate(localSettings.copy(keepAliveInterval = it.toInt())) },
            onValueChangeFinished = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            valueRange = 10f..120f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = SettingsAccentPurple,
                activeTrackColor = SettingsAccentPurple
            )
        )
    }

    SettingsToggleRow(
        label = "RPort (RFC 3581)",
        description = "Include rport in Via header for NAT",
        checked = localSettings.rportEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(rportEnabled = it))
        }
    )

    SettingsDivider()

    DropdownSettingsField(
        label = "IPv6 Preference",
        options = listOf("Force IPv4", "Force IPv6", "Dual-stack"),
        selectedOption = localSettings.ipv6Preference,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(ipv6Preference = it))
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}

// ═════════════════════════════════════════════════════════════
//  AUDIO & CODECS PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun AudioPanel(
    localSettings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSave: () -> Unit
) {
    SectionHeader(
        title = "Audio & Codecs",
        subtitle = "Echo cancellation, DTMF delivery, and codec quality"
    )

    DropdownSettingsField(
        label = "Echo Cancellation",
        options = listOf("Hardware", "Software"),
        selectedOption = localSettings.echoCancellationType,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(echoCancellationType = it))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    DropdownSettingsField(
        label = "DTMF Delivery Method",
        options = listOf("RFC 2833", "SIP INFO", "In-band"),
        selectedOption = localSettings.dtmfMethod,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(dtmfMethod = it))
        }
    )

    SettingsDivider()

    SettingsToggleRow(
        label = "AEC Enabled",
        description = "Hardware or software acoustic echo cancellation",
        checked = localSettings.aecEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(aecEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "AGC Enabled",
        description = "Automatic gain control for microphone levels",
        checked = localSettings.agcEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(agcEnabled = it))
        }
    )

    SettingsDivider()

    // ── Global Codec Priority Manager ─────────────────────────────
    Text(
        "Global Codec Priority",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        "Drag to reorder codecs. Toggle to enable or disable. This applies to all accounts unless overridden per-account.",
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    GlobalCodecManager(
        settings = localSettings,
        onUpdate = onUpdate,
        haptic = haptic
    )

    SettingsDivider()

    // ── Opus Quality Tuning ───────────────────────────────────────
    Text(
        "Opus Quality Settings",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        "Fine-tune Opus parameters. These affect voice quality on mobile networks.",
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // ptime
    Text(
        "Packet Time (ptime): ${localSettings.opusPtime}ms",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "20ms = standard latency · 40ms = lower CPU, higher latency",
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Slider(
        value = localSettings.opusPtime.toFloat(),
        onValueChange = {
            onUpdate(localSettings.copy(opusPtime = it.toInt()))
        },
        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        valueRange = 10f..60f,
        steps = 4, // 10, 20, 30, 40, 50, 60
        colors = SliderDefaults.colors(
            thumbColor = SettingsAccentPurple,
            activeTrackColor = SettingsAccentPurple
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Bitrate
    Text(
        "Opus Bitrate: ${if (localSettings.opusBitrate == 0) "Auto (recommended)" else "${localSettings.opusBitrate} kbps"}",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "Auto lets the encoder adapt per-frame · 16–24 kbps is ideal for voice",
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Slider(
        value = localSettings.opusBitrate.toFloat(),
        onValueChange = {
            onUpdate(localSettings.copy(opusBitrate = it.toInt()))
        },
        onValueChangeFinished = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        valueRange = 0f..64f,
        steps = 7, // 0(auto), 8, 16, 24, 32, 40, 48, 56, 64
        colors = SliderDefaults.colors(
            thumbColor = SettingsAccentPurple,
            activeTrackColor = SettingsAccentPurple
        )
    )

    Spacer(modifier = Modifier.height(4.dp))

    SettingsToggleRow(
        label = "Opus FEC (Forward Error Correction)",
        description = "Recovers from packet loss — strongly recommended on mobile",
        checked = localSettings.opusFecEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(opusFecEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Opus DTX (Silence Suppression)",
        description = "Saves bandwidth during silence and hold — safe to enable",
        checked = localSettings.opusDtxEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(opusDtxEnabled = it))
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}

/** Interactive drag-and-drop codec list backed by VoIpSettings.globalCodecs. */
@Composable
private fun GlobalCodecManager(
    settings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    // Populate from globalCodecs if already set; otherwise build the default ordered list
    val defaultList = listOf(
        SipCodecConfig("opus/48000/2",   enabled = true,  priority = 255),
        SipCodecConfig("G722/16000/1",   enabled = true,  priority = 240),
        SipCodecConfig("G729/8000/1",    enabled = true,  priority = 220),
        SipCodecConfig("PCMU/8000/1",    enabled = true,  priority = 200),
        SipCodecConfig("PCMA/8000/1",    enabled = true,  priority = 190),
        SipCodecConfig("AMR-WB/16000/1", enabled = false, priority = 170),
        SipCodecConfig("AMR/8000/1",     enabled = false, priority = 160),
        SipCodecConfig("speex/16000/1",  enabled = false, priority = 140),
        SipCodecConfig("speex/32000/1",  enabled = false, priority = 130),
        SipCodecConfig("speex/8000/1",   enabled = false, priority = 120),
        SipCodecConfig("iLBC/8000/1",    enabled = false, priority = 110),
        SipCodecConfig("GSM/8000/1",     enabled = false, priority = 90),
        SipCodecConfig("G7221/16000/1",  enabled = false, priority = 80),
        SipCodecConfig("G7221/32000/1",  enabled = false, priority = 70),
        SipCodecConfig("L16/16000/1",    enabled = false, priority = 60),
        SipCodecConfig("L16/8000/1",     enabled = false, priority = 50)
    )

    var codecsList by remember(settings.globalCodecs) {
        mutableStateOf(
            if (settings.globalCodecs.isNotEmpty()) settings.globalCodecs else defaultList
        )
    }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 68.dp.toPx() } }

    fun persistCodecs(updated: List<SipCodecConfig>) {
        // Re-assign priorities from position so the engine gets clean priority numbers
        val reIndexed = updated.mapIndexed { i, c -> c.copy(priority = 255 - i) }
        codecsList = reIndexed
        onUpdate(settings.copy(globalCodecs = reIndexed))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            codecsList.forEachIndexed { index, codec ->
                val displayName = codecDisplayName(codec.id)
                val quality = codecQualityBadge(codec.id)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .zIndex(if (index == draggedIndex) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (index == draggedIndex) dragOffset else 0f
                        }
                        .background(
                            if (index == draggedIndex)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (codec.enabled) SettingsAccentPurple.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (codec.enabled) SettingsAccentPurple
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Codec info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (codec.enabled) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (codec.enabled)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            maxLines = 1
                        )
                        Text(
                            quality,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (codec.enabled) SettingsAccentPurple.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        )
                    }

                    // Enable/disable toggle
                    Switch(
                        checked = codec.enabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val updated = codecsList.toMutableList()
                            updated[index] = codec.copy(enabled = isChecked)
                            persistCodecs(updated)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SettingsAccentPurple
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Drag handle
                    Icon(
                        imageVector = Icons.Default.Reorder,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val cur = draggedIndex
                                        if (cur != null) {
                                            if (dragOffset > itemHeightPx / 2 && cur < codecsList.size - 1) {
                                                val updated = codecsList.toMutableList()
                                                val tmp = updated[cur]; updated[cur] = updated[cur + 1]; updated[cur + 1] = tmp
                                                persistCodecs(updated)
                                                draggedIndex = cur + 1; dragOffset -= itemHeightPx
                                            } else if (dragOffset < -itemHeightPx / 2 && cur > 0) {
                                                val updated = codecsList.toMutableList()
                                                val tmp = updated[cur]; updated[cur] = updated[cur - 1]; updated[cur - 1] = tmp
                                                persistCodecs(updated)
                                                draggedIndex = cur - 1; dragOffset += itemHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = { draggedIndex = null; dragOffset = 0f },
                                    onDragCancel = { draggedIndex = null; dragOffset = 0f }
                                )
                            }
                            .padding(10.dp)
                    )
                }

                if (index < codecsList.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }

    // Reset to defaults button
    TextButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            persistCodecs(defaultList)
        },
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Reset to defaults",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Maps a codec ID string to a human-readable display name. */
private fun codecDisplayName(id: String): String = when {
    id.contains("opus", ignoreCase = true)         -> "Opus (48 kHz) — HD Wideband"
    id.contains("G722", ignoreCase = true)         -> "G.722 (16 kHz) — HD Voice"
    id.contains("G729", ignoreCase = true)         -> "G.729 (8 kHz) — Low Bandwidth"
    id.contains("PCMU", ignoreCase = true)         -> "PCMU / G.711u (8 kHz) — PSTN Standard"
    id.contains("PCMA", ignoreCase = true)         -> "PCMA / G.711a (8 kHz) — PSTN Standard"
    id.contains("AMR-WB", ignoreCase = true)       -> "AMR-WB (16 kHz) — Mobile HD"
    id.contains("AMR", ignoreCase = true)          -> "AMR (8 kHz) — Mobile Narrowband"
    id.contains("speex/32000", ignoreCase = true)  -> "Speex (32 kHz) — Ultra-wideband"
    id.contains("speex/16000", ignoreCase = true)  -> "Speex (16 kHz) — Wideband"
    id.contains("speex/8000", ignoreCase = true)   -> "Speex (8 kHz) — Narrowband"
    id.contains("iLBC", ignoreCase = true)         -> "iLBC (8 kHz) — Narrowband"
    id.contains("GSM", ignoreCase = true)          -> "GSM (8 kHz) — Legacy"
    id.contains("G7221", ignoreCase = true) &&
        id.contains("32000", ignoreCase = true)    -> "G.722.1 (32 kHz) — Siren14"
    id.contains("G7221", ignoreCase = true)        -> "G.722.1 (16 kHz) — Siren7"
    id.contains("L16", ignoreCase = true)          -> "L16 PCM — Uncompressed"
    else -> id
}

/** Short quality descriptor for the badge below the codec name. */
private fun codecQualityBadge(id: String): String = when {
    id.contains("opus",    ignoreCase = true) -> "Best quality · adaptive bitrate · FEC support"
    id.contains("G722",   ignoreCase = true) -> "HD voice · 7 kHz audio bandwidth"
    id.contains("G729",   ignoreCase = true) -> "Low bandwidth · 8 kbps · good compat"
    id.contains("PCMU",   ignoreCase = true) -> "Standard · 64 kbps · universal compat"
    id.contains("PCMA",   ignoreCase = true) -> "Standard · 64 kbps · universal compat"
    id.contains("AMR-WB", ignoreCase = true) -> "Mobile wideband · carrier-grade"
    id.contains("AMR",    ignoreCase = true) -> "Mobile narrowband · carrier compat"
    id.contains("speex",  ignoreCase = true) -> "Open-source narrowband/wideband"
    id.contains("iLBC",   ignoreCase = true) -> "Narrowband · bursty-loss resilient"
    id.contains("GSM",    ignoreCase = true) -> "Legacy · 13 kbps · low quality"
    id.contains("G7221",  ignoreCase = true) -> "Siren codec · conferencing"
    id.contains("L16",    ignoreCase = true) -> "Uncompressed PCM · high bandwidth"
    else -> "Unknown codec"
}

// ═════════════════════════════════════════════════════════════
//  NETWORK & NAT PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun NetworkPanel(
    localSettings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSave: () -> Unit
) {
    SectionHeader(
        title = "Network & NAT",
        subtitle = "NAT traversal, ICE settings, and connection tuning"
    )

    OutlinedTextField(
        value = localSettings.stunServer,
        onValueChange = { onUpdate(localSettings.copy(stunServer = it)) },
        label = { Text("STUN Server") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = localSettings.turnServer,
        onValueChange = { onUpdate(localSettings.copy(turnServer = it)) },
        label = { Text("TURN Server") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )

    SettingsDivider()

    SettingsToggleRow(
        label = "ICE / STUN",
        description = "Auto-detect public IP via STUN for NAT traversal",
        checked = localSettings.iceEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(iceEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "RPort (RFC 3581)",
        description = "Include rport parameter in Via headers",
        checked = localSettings.rportEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(rportEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Wake Lock",
        description = "Prevent CPU sleep during active calls",
        checked = localSettings.wakeLockEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(wakeLockEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Background Keep-Alive",
        description = "Maintain connection in background mode",
        checked = localSettings.backgroundKeepAliveEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(backgroundKeepAliveEnabled = it))
        }
    )

    SettingsDivider()

    DropdownSettingsField(
        label = "IPv6 Preference",
        options = listOf("Force IPv4", "Force IPv6", "Dual-stack"),
        selectedOption = localSettings.ipv6Preference,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(ipv6Preference = it))
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}

// ═════════════════════════════════════════════════════════════
//  SECURITY PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun SecurityPanel(
    localSettings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSave: () -> Unit
) {
    SectionHeader(
        title = "Security & Encryption",
        subtitle = "Signalling transport and global security baseline"
    )

    // Info card: encryption is per-account now
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Encryption is configured per-account",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "SRTP, ZRTP, and LIME settings are in each account’s \"Account Details\" screen. This allows different encryption per SIP provider since not all providers support encryption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ Default for new accounts: encryption OFF (safest default)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }

    SettingsDivider()

    // Transport Protocol: global default for new accounts (NOT overriding existing)
    DropdownSettingsField(
        label = "Default Transport (new accounts)",
        options = listOf("UDP", "TCP", "TLS"),
        selectedOption = localSettings.transportProtocol,
        onOptionSelected = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUpdate(localSettings.copy(transportProtocol = it))
        }
    )

    SettingsFieldHint("This sets the default transport used when registering new SIP accounts. Each account can override this in Account Details.")

    SettingsDivider()

    // Post-Quantum: global key-agreement config (harmless to leave on globally)
    SettingsToggleRow(
        label = "Post-Quantum Key Exchange (PQE)",
        description = "Experimental quantum-resistant key agreement (ZRTP Kyber). Harmless to enable globally.",
        checked = localSettings.postQuantumEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(postQuantumEnabled = it))
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}


// ═════════════════════════════════════════════════════════════
//  ADVANCED PANEL
// ═════════════════════════════════════════════════════════════
@Composable
private fun AdvancedPanel(
    localSettings: VoIpSettings,
    onUpdate: (VoIpSettings) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSave: () -> Unit
) {
    SectionHeader(
        title = "Advanced",
        subtitle = "Timers, logging, and enterprise features"
    )

    // Log level
    Text(
        "Native Log Level: ${localSettings.logLevel}",
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = localSettings.logLevel.toFloat(),
        onValueChange = { onUpdate(localSettings.copy(logLevel = it.toInt())) },
        onValueChangeFinished = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        valueRange = 0f..6f,
        steps = 5,
        colors = SliderDefaults.colors(
            thumbColor = SettingsAccentPurple,
            activeTrackColor = SettingsAccentPurple
        )
    )
    SettingsFieldHint("0 = silent, 6 = verbose trace")

    SettingsDivider()

    // Mobile OS Integrations
    Text(
        "Mobile OS Integrations",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp)
    )

    SettingsToggleRow(
        label = "Native Call Integration",
        description = "Use Android Telecom framework for call management",
        checked = localSettings.nativeCallIntegrationEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(nativeCallIntegrationEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Push Notifications (FCM)",
        description = "Receive calls via Firebase Cloud Messaging",
        checked = localSettings.pushNotificationsEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(pushNotificationsEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "DND Sync",
        description = "Reject calls when Do Not Disturb is active",
        checked = localSettings.dndSyncEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(dndSyncEnabled = it))
        }
    )

    SettingsDivider()

    // Enterprise Features
    Text(
        "Enterprise Features",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp)
    )

    SettingsToggleRow(
        label = "Attended Transfer",
        description = "Transfer calls with consultation",
        checked = localSettings.attendedTransferEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(attendedTransferEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Presence (BLF)",
        description = "Busy Lamp Field status monitoring",
        checked = localSettings.blfEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(blfEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "Proximity Sensor",
        description = "Turn off screen during calls",
        checked = localSettings.proximitySensorEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(proximitySensorEnabled = it))
        }
    )

    SettingsToggleRow(
        label = "SIP Messaging",
        description = "Send and receive SIP MESSAGE requests",
        checked = localSettings.sipMessagingEnabled,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUpdate(localSettings.copy(sipMessagingEnabled = it))
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSaveRow(onSave = onSave)
}

// ═════════════════════════════════════════════════════════════
//  SHARED UI COMPONENTS
// ═════════════════════════════════════════════════════════════

private enum class BadgeType { Ok, Warn, Error, Neutral }

@Composable
private fun SettingsStatusBar(
    dotColor: Color,
    text: String,
    badgeText: String,
    badgeType: BadgeType
) {
    val (badgeBg, badgeFg) = when (badgeType) {
        BadgeType.Ok -> Color(0xFFEAF3DE) to Color(0xFF3B6D11)
        BadgeType.Warn -> Color(0xFFFAEEDA) to Color(0xFF854F0B)
        BadgeType.Error -> Color(0xFFFCEBEB) to Color(0xFFA32D2D)
        BadgeType.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = badgeBg
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = badgeFg
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsAccentPurple
            )
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun SettingsFieldHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.padding(start = 4.dp, top = 3.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun SettingsSaveRow(
    onSave: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SettingsAccentPurple)
        ) {
            Text("Save changes", style = MaterialTheme.typography.labelLarge)
        }
        if (secondaryLabel != null && onSecondaryClick != null) {
            OutlinedButton(
                onClick = onSecondaryClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
private fun CodecRow(
    name: String,
    badgeText: String,
    badgeType: BadgeType,
    showDivider: Boolean
) {
    val (badgeBg, badgeFg) = when (badgeType) {
        BadgeType.Ok -> Color(0xFFEAF3DE) to Color(0xFF3B6D11)
        BadgeType.Warn -> Color(0xFFFAEEDA) to Color(0xFF854F0B)
        BadgeType.Error -> Color(0xFFFCEBEB) to Color(0xFFA32D2D)
        BadgeType.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = badgeBg
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = badgeFg
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Retained helpers (same API, preserved for MainScreen.kt)
// ─────────────────────────────────────────────────────────────

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
