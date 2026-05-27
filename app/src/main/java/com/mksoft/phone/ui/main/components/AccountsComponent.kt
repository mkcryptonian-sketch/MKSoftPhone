package com.mksoft.phone.ui.main.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import com.mksoft.phone.core.sip.AccountWrapper
import com.mksoft.phone.core.sip.RegistrationState
import com.mksoft.phone.data.SipAccountConfig
import com.mksoft.phone.data.SipCodecConfig
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiPrimaryDark
import com.mksoft.phone.ui.main.MainScreenViewModel

@Composable
fun AccountsScreen(
    savedAccounts: List<SipAccountConfig>,
    activeAccounts: Map<String, AccountWrapper>,
    primaryAccountId: String?,
    probeResult: MainScreenViewModel.ProbeResult,
    onAddAccount: (SipAccountConfig) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onProbe: (String) -> Unit,
    onEditAccount: (SipAccountConfig) -> Unit
) {
    var showWizard by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (savedAccounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No registered SIP accounts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedAccounts) { account ->
                        val wrapper = activeAccounts[account.id]
                        val isPrimary = account.id == primaryAccountId
                        
                        Card(
                            onClick = { onEditAccount(account) },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = if (isPrimary) BorderStroke(2.dp, GeminiPrimaryDark) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (wrapper?.registrationState is RegistrationState.Registered)
                                                DialerCallGreen.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (wrapper?.registrationState is RegistrationState.Registered)
                                            Icons.Filled.CheckCircle
                                        else
                                            Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = if (wrapper?.registrationState is RegistrationState.Registered)
                                            DialerCallGreen
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.username,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = account.domain,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    val statusText = when (val state = wrapper?.registrationState) {
                                        is RegistrationState.Registered -> "Registered"
                                        is RegistrationState.Registering -> "Registering..."
                                        is RegistrationState.Failed -> "Failed: ${state.reason}"
                                        else -> "Offline"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (wrapper?.registrationState is RegistrationState.Registered)
                                            DialerCallGreen
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (isPrimary) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = "Primary",
                                        tint = GeminiPrimaryDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                IconButton(onClick = { onRemoveAccount(account.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showWizard = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = GeminiPrimaryDark,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Account")
        }
    }

    if (showWizard) {
        AccountWizardDialog(
            probeResult = probeResult,
            onProbe = onProbe,
            onDismiss = { showWizard = false },
            onSave = {
                onAddAccount(it)
                showWizard = false
            }
        )
    }
}

@Composable
fun AccountWizardDialog(
    probeResult: MainScreenViewModel.ProbeResult,
    onProbe: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SipAccountConfig) -> Unit,
    isEdit: Boolean = false
) {
    var username by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("TLS") }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEdit) "Edit Account" else "Add SIP Account",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = domain,
                    onValueChange = { 
                        domain = it
                        if (it.contains(".")) onProbe(it)
                    },
                    label = { Text("Domain (e.g. sip.provider.com)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Probe status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProbeRow("TLS", probeResult.tls)
                    ProbeRow("TCP", probeResult.tcp)
                    ProbeRow("UDP", probeResult.udp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text("Transport Protocol", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("UDP", "TCP", "TLS").forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = transport == t,
                                onClick = { transport = t }
                            )
                            Text(t, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val id = "sip:$username@$domain"
                            onSave(SipAccountConfig(
                                id = id,
                                username = username,
                                domain = domain,
                                secret = password,
                                transport = transport
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiPrimaryDark)
                    ) {
                        Text("Save Account")
                    }
                }
            }
        }
    }
}

@Composable
fun ProbeRow(label: String, status: MainScreenViewModel.ProbeStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when (status) {
            MainScreenViewModel.ProbeStatus.Found -> DialerCallGreen
            MainScreenViewModel.ProbeStatus.NotFound -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            MainScreenViewModel.ProbeStatus.Probing -> GeminiPrimaryDark
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        }
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (status == MainScreenViewModel.ProbeStatus.Found) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun AccountDetailsScreen(
    account: SipAccountConfig,
    onBack: () -> Unit,
    onSave: (SipAccountConfig) -> Unit,
    onSetPrimary: (String) -> Unit
) {
    var editedAccount by remember(account) { mutableStateOf(account) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Account Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings Card
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("SIP Credentials", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = editedAccount.username,
                    onValueChange = { editedAccount = editedAccount.copy(username = it) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedAccount.domain,
                    onValueChange = { editedAccount = editedAccount.copy(domain = it) },
                    label = { Text("Domain") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedAccount.secret,
                    onValueChange = { editedAccount = editedAccount.copy(secret = it) },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Advanced Settings Card (Codecs Priorities)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Audio Codec Priorities", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select audio codecs and adjust priority by moving them up or down. By default, the best voice quality codecs are prioritized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                var codecsList by remember(editedAccount.id) {
                    mutableStateOf(editedAccount.getNormalizedCodecs())
                }
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffset by remember { mutableStateOf(0f) }
                val density = LocalDensity.current
                val itemHeightPx = remember(density) { with(density) { 68.dp.toPx() } }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    codecsList.forEachIndexed { index, codec ->
                        val displayName = when {
                            codec.id.contains("opus", ignoreCase = true) -> "Opus (48kHz) [HD Voice]"
                            codec.id.contains("G722", ignoreCase = true) -> "G.722 (16kHz) [HD Voice]"
                            codec.id.contains("G729", ignoreCase = true) -> "G.729 (8kHz) [Low Bandwidth]"
                            codec.id.contains("PCMU", ignoreCase = true) -> "PCMU (G.711u) [Standard]"
                            codec.id.contains("PCMA", ignoreCase = true) -> "PCMA (G.711a) [Standard]"
                            codec.id.contains("GSM", ignoreCase = true) -> "GSM (8kHz) [Legacy]"
                            codec.id.contains("speex/16000", ignoreCase = true) -> "Speex (16kHz) [Wideband]"
                            codec.id.contains("speex/32000", ignoreCase = true) -> "Speex (32kHz) [Ultra-wide]"
                            codec.id.contains("speex/8000", ignoreCase = true) -> "Speex (8kHz) [Narrowband]"
                            codec.id.contains("iLBC", ignoreCase = true) -> "iLBC (8kHz) [Narrowband]"
                            codec.id.contains("AMR-WB", ignoreCase = true) -> "AMR-WB (16kHz) [Wideband]"
                            codec.id.contains("AMR", ignoreCase = true) -> "AMR (8kHz) [Narrowband]"
                            else -> codec.id
                        }
                        
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
                                    else
                                        Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = codec.enabled,
                                    onCheckedChange = { isChecked ->
                                        val updated = codecsList.toMutableList()
                                        updated[index] = codec.copy(enabled = isChecked)
                                        codecsList = updated
                                        editedAccount = editedAccount.copy(codecs = updated)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (codec.enabled) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (codec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        "Priority ${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            Icon(
                                imageVector = Icons.Default.Reorder,
                                contentDescription = "Drag to reorder",
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
                                                
                                                val currentDragged = draggedIndex
                                                if (currentDragged != null) {
                                                    if (dragOffset > itemHeightPx / 2 && currentDragged < codecsList.size - 1) {
                                                        val updated = codecsList.toMutableList()
                                                        val temp = updated[currentDragged]
                                                        updated[currentDragged] = updated[currentDragged + 1]
                                                        updated[currentDragged + 1] = temp
                                                        codecsList = updated
                                                        editedAccount = editedAccount.copy(codecs = updated)
                                                        draggedIndex = currentDragged + 1
                                                        dragOffset -= itemHeightPx
                                                    } else if (dragOffset < -itemHeightPx / 2 && currentDragged > 0) {
                                                        val updated = codecsList.toMutableList()
                                                        val temp = updated[currentDragged]
                                                        updated[currentDragged] = updated[currentDragged - 1]
                                                        updated[currentDragged - 1] = temp
                                                        codecsList = updated
                                                        editedAccount = editedAccount.copy(codecs = updated)
                                                        draggedIndex = currentDragged - 1
                                                        dragOffset += itemHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedIndex = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                                    .padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onSave(editedAccount) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = GeminiPrimaryDark)
        ) {
            Text("Save Changes", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { onSetPrimary(account.id) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape
        ) {
            Text("Set as Primary Account")
        }
    }
}

@Composable
fun CodecPrioritySlider(
    name: String,
    priority: Float,
    enabled: Boolean,
    onPriorityChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(priority.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = GeminiPrimaryDark)
        }
        Slider(
            value = priority,
            onValueChange = onPriorityChange,
            valueRange = 0f..255f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = GeminiPrimaryDark,
                activeTrackColor = GeminiPrimaryDark
            )
        )
    }
}
