package com.mksoft.phone.ui.main

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mksoft.phone.R
import com.mksoft.phone.core.sip.RegistrationState
import com.mksoft.phone.theme.GeminiGlowBrush
import com.mksoft.phone.theme.GeminiPrimaryDark

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedTransport by remember { mutableStateOf("TLS") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val activeAccounts by viewModel.activeAccounts.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val primaryAccountId by viewModel.primaryAccountId.collectAsState()

    // Requirement: UI Transition
    // Transition away from the login screen immediately upon receiving RegistrationState.Registered
    LaunchedEffect(activeAccounts) {
        if (activeAccounts.values.any { it.registrationState is com.mksoft.phone.core.sip.RegistrationState.Registered }) {
            onLoginSuccess()
        }
        
        // Handle registration failure
        val failedAccount = activeAccounts.values.find { it.registrationState is com.mksoft.phone.core.sip.RegistrationState.Failed }
        if (failedAccount != null && isLoading) {
            isLoading = false
            isError = true
            errorMessage = failedAccount.lastStatusText.ifEmpty { "Registration failed" }
        }
    }

    // Pre-fill last used account
    val defaultAccount = remember(savedAccounts, primaryAccountId) {
        savedAccounts.find { it.id == primaryAccountId }
    }

    LaunchedEffect(defaultAccount) {
        if (defaultAccount != null && username.isEmpty()) {
            username = defaultAccount.id.replace("sip:", "")
            password = defaultAccount.secret
            selectedTransport = defaultAccount.transport
        }
    }

    // Gorgeous gradient background (Gemini Space)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF06070B),
                        Color(0xFF0F1221),
                        Color(0xFF06070B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo / Icon Header
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x10FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "MK Softphone Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.login_welcome),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login Input Card (Glassmorphic look)
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                modifier = Modifier.fillMaxWidth(),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.01f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Username Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            isError = false
                        },
                        label = { Text(stringResource(R.string.login_username_label), color = Color.White.copy(alpha = 0.6f)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GeminiPrimaryDark,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = GeminiPrimaryDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            isError = false
                        },
                        label = { Text(stringResource(R.string.login_password_label), color = Color.White.copy(alpha = 0.6f)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GeminiPrimaryDark,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = GeminiPrimaryDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transport Selection
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("UDP", "TCP", "TLS").forEach { transport ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedTransport == transport,
                                    onClick = { selectedTransport = transport },
                                    colors = RadioButtonDefaults.colors(selectedColor = GeminiPrimaryDark, unselectedColor = Color.White.copy(alpha = 0.6f))
                                )
                                Text(transport, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))



                    AnimatedVisibility(
                        visible = isError,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Action Login Button with signature Gemini gradient background
                    val errorFields = stringResource(R.string.login_error_fields)
                    val errorDomain = stringResource(R.string.login_error_domain)
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                isError = true
                                errorMessage = errorFields
                                return@Button
                            }
                            
                            val parts = username.split("@", limit = 2)
                            if (parts.size < 2 || parts[1].isBlank()) {
                                isError = true
                                errorMessage = errorDomain
                                return@Button
                            }
                            val sipUser = parts[0]
                            val sipDomain = parts[1]

                            isLoading = true
                            focusManager.clearFocus()
                            viewModel.addSipAccount(sipUser, sipDomain, password, selectedTransport, false) {
                                // We rely on the LaunchedEffect above to call onLoginSuccess()
                                // when RegistrationState becomes Registered.
                                // If it fails, we should stop loading and show error.
                                // But for now, we just stop the spinner if adding failed.
                                // isLoading = false // Actually, we might want to stay loading until registered or timeout.
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(GeminiGlowBrush, CircleShape),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.login_button_label),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.25.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
