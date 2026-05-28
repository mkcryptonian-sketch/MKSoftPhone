package com.mksoft.phone.ui.main.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mksoft.phone.R
import com.mksoft.phone.core.sip.AccountWrapper
import com.mksoft.phone.core.sip.RegistrationState
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiPrimaryDark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    accounts: Map<String, AccountWrapper>,
    primaryAccountId: String?,
    onDial: (String, String) -> Unit
) {
    var dialUri by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val effectiveAccountId = remember(primaryAccountId, accounts) {
        if (!primaryAccountId.isNullOrEmpty() && accounts.containsKey(primaryAccountId)) {
            primaryAccountId
        } else if (accounts.isNotEmpty()) {
            accounts.keys.first()
        } else {
            ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Watermark Logo
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Watermark Logo",
            alpha = 0.05f,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- TOP SECTION: Registration Status ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.32f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Show registration status only
                val selectedAccount = accounts[effectiveAccountId]
                if (selectedAccount != null) {
                    val isRegistered = selectedAccount.registrationState is RegistrationState.Registered
                    val statusText = when (selectedAccount.registrationState) {
                        is RegistrationState.Registered -> "Registered"
                        is RegistrationState.Registering -> "Registering..."
                        is RegistrationState.Failed -> "Connection Failed"
                        else -> "Disconnected"
                    }
                    
                    Surface(
                        color = (if (isRegistered) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, (if (isRegistered) Color(0xFF4CAF50) else Color(0xFFF44336)).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRegistered) Color(0xFF4CAF50) else Color(0xFFF44336))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isRegistered) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextField(
                        value = dialUri,
                        onValueChange = { dialUri = it },
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 32.sp,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        ),
                        placeholder = {
                            Text(
                                text = "Enter URI or Number",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Light
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // --- BACKSPACE BAR SECTION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dialUri.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dialUri = dialUri.dropLast(1)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- BOTTOM SECTION: Slate Keypad Area ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.68f)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Keypad Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            row.forEach { (digit, letters) ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), CircleShape)
                                        .combinedClickable(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dialUri += digit
                                            },
                                            onLongClick = if (digit == "0") {
                                                {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dialUri += "+"
                                                }
                                            } else null
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = digit,
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 28.sp,
                                                lineHeight = 28.sp
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

                // Bottom Call button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (effectiveAccountId.isNotEmpty() && dialUri.isNotEmpty()) {
                                onDial(effectiveAccountId, dialUri)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(DialerCallGreen)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialerButton(
    digit: String,
    letters: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
