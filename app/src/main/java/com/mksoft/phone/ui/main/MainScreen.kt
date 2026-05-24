package com.mksoft.phone.ui.main

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import com.mksoft.phone.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mksoft.phone.core.sip.*
import com.mksoft.phone.data.*
import com.mksoft.phone.R
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

// Navigation targets
enum class ScreenRoute(val routeName: String, val titleRes: Int) {
    Dialer("Dialer", R.string.route_dialer),
    History("Recent Calls", R.string.route_history),
    Contacts("Contacts", R.string.route_contacts),
    Accounts("Accounts", R.string.route_accounts),
    Settings("Settings", R.string.route_settings),
    Recordings("Call Recordings", R.string.route_recordings),
    Warnings("System Logs", R.string.route_warnings),
    AccountDetails("Account Details", R.string.route_account_details)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val engineState by viewModel.engineState.collectAsState()
    val activeAccounts by viewModel.activeAccounts.collectAsState()
    val activeCalls by viewModel.activeCalls.collectAsState()
    val sipLogs by viewModel.sipLogs.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val primaryAccountId by viewModel.primaryAccountId.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenRoute.Dialer) }
    var currentAccount by remember { mutableStateOf<SipAccountConfig?>(null) }
    var isOverlayMinimized by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Auto-register on first launch AND every time the app comes to foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasRecordAudioPermission = granted
                
                if (granted) {
                    android.util.Log.d("MainScreen", "onResume: ensuring SIP registration is active")
                    viewModel.ensureRegistered()
                } else {
                    android.util.Log.w("MainScreen", "SipService start bypassed: RECORD_AUDIO permission not granted yet.")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp).statusBarsPadding()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Prominent Profile Header Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0A0C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_logo),
                                contentDescription = "App Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MK Softphone",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "VoIP Softphone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Profile dropdown & presence status
                    val currentPrimaryAccount = savedAccounts.find { it.id == primaryAccountId }
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            readOnly = true,
                            value = currentPrimaryAccount?.id?.replace("sip:", "") ?: "No Active Profile",
                            onValueChange = {},
                            label = { Text("Active Profile", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (savedAccounts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No profiles created") },
                                    onClick = { expanded = false }
                                )
                            } else {
                                savedAccounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text(account.id.replace("sip:", "")) },
                                        onClick = {
                                            viewModel.setPrimaryAccount(account.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Registration Presence line text
                    if (currentPrimaryAccount != null) {
                        val activeAccountWrapper = activeAccounts[primaryAccountId]
                        val regState = activeAccountWrapper?.registrationState
                        
                        val (statusText, statusColor) = when (regState) {
                            is RegistrationState.Registered -> "Account is ready" to Color(0xFF4CAF50)
                            is RegistrationState.Registering -> "Registering..." to Color(0xFFFFC107)
                            is RegistrationState.Failed -> "Registration failed (SIP ${regState.statusCode})" to Color(0xFFF44336)
                            else -> "Idle" to Color.Gray
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create profile in Accounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Navigation Items
                listOf(
                    ScreenRoute.Dialer to Icons.Filled.Dialpad,
                    ScreenRoute.History to Icons.Filled.History,
                    ScreenRoute.Contacts to Icons.Filled.ContactPhone,
                    ScreenRoute.Accounts to Icons.Filled.AccountBox,
                    ScreenRoute.Settings to Icons.Filled.Settings,
                    ScreenRoute.Recordings to Icons.Filled.Mic,
                    ScreenRoute.Warnings to Icons.Filled.Warning
                ).forEach { (screen, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = icon, contentDescription = stringResource(screen.titleRes)) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.nav_logout)) },
                    label = { Text(stringResource(R.string.nav_logout)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLogoutConfirm = true
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedTextColor = MaterialTheme.colorScheme.error,
                        unselectedIconColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.nav_exit)) },
                    label = { Text(stringResource(R.string.nav_exit)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showExitConfirm = true
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                if (showLogoutConfirm) {
                    AlertDialog(
                        onDismissRequest = { showLogoutConfirm = false },
                        title = { Text(stringResource(R.string.logout_confirm_title)) },
                        text = { Text(stringResource(R.string.logout_confirm_text)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showLogoutConfirm = false
                                    isLoggingOut = true
                                    viewModel.logout {
                                        val activity = context.findActivity()
                                        activity?.finishAffinity()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.nav_logout))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogoutConfirm = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                if (isLoggingOut) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.logging_out_text),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.logging_out_subtext),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text(stringResource(R.string.exit_confirm_title)) },
                        text = { Text(stringResource(R.string.exit_confirm_text)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirm = false
                                    viewModel.stopSipService()
                                    val activity = context.findActivity()
                                    activity?.finishAffinity()
                                }
                            ) {
                                Text(stringResource(R.string.nav_exit))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Engine State Summary in Drawer Bottom
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PJSIP Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (engineState) {
                                            is SipEngineState.Ready -> Color(0xFF4CAF50)
                                            is SipEngineState.Initializing -> Color(0xFFFFC107)
                                            else -> Color(0xFFF44336)
                                        }
                                    )
                            )
                        }
                        Text(
                            text = when (engineState) {
                                is SipEngineState.Ready -> "Engine Operational"
                                is SipEngineState.Initializing -> "Initializing..."
                                is SipEngineState.Error -> "Initialization Failed"
                                else -> "Engine Offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) {
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
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        ) {
                            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                        }

                        // Active Profile Dropdown (Gemini style)
                        val currentPrimaryAccount = savedAccounts.find { it.id == primaryAccountId }
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentPrimaryAccount?.id?.replace("sip:", "")?.substringBefore("@") ?: "Select Profile",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Dropdown Chevron",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .background(Color(0xFF0E101A))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            ) {
                                if (savedAccounts.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No profiles", color = Color.White) },
                                        onClick = { dropdownExpanded = false }
                                    )
                                } else {
                                    savedAccounts.forEach { account ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = account.id.replace("sip:", ""),
                                                    color = if (account.id == primaryAccountId) GeminiPrimaryDark else Color.White
                                                )
                                            },
                                            onClick = {
                                                viewModel.setPrimaryAccount(account.id)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right side status chip and refresh action for Logs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentScreen == ScreenRoute.Warnings) {
                            IconButton(
                                onClick = { viewModel.shutdownEngine(); viewModel.initializeEngine() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            ) {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Restart PJSIP")
                            }
                        }

                        val activeAccountWrapper = activeAccounts[primaryAccountId]
                        val regState = activeAccountWrapper?.registrationState

                        val (statusLabel, statusColor, isPulsing) = when (regState) {
                            is RegistrationState.Registered -> Triple("Ready", Color(0xFF81C784), true)
                            is RegistrationState.Registering -> Triple("Connecting", Color(0xFFFFB74D), true)
                            is RegistrationState.Failed -> Triple("Error", Color(0xFFE57373), false)
                            else -> Triple("Offline", Color.Gray, false)
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
                        val pulseAlpha by if (isPulsing) {
                            infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = keyframes {
                                        durationMillis = 1200
                                    },
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )
                        } else {
                            remember { mutableStateOf(1.0f) }
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = statusColor
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xBE0E101A)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bottomNavs = listOf(
                                ScreenRoute.Dialer to Icons.Filled.Dialpad,
                                ScreenRoute.History to Icons.Filled.History,
                                ScreenRoute.Contacts to Icons.Filled.ContactPhone,
                                ScreenRoute.Accounts to Icons.Filled.AccountBox
                            )
                            bottomNavs.forEach { (screen, icon) ->
                                val isSelected = currentScreen == screen
                                val backgroundAlpha by animateFloatAsState(
                                    targetValue = if (isSelected) 0.15f else 0.0f,
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    label = "bg_alpha"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.2f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "icon_scale"
                                )
                                val tintColor by animateColorAsState(
                                    targetValue = if (isSelected) GeminiPrimaryDark else Color.White.copy(alpha = 0.5f),
                                    animationSpec = tween(durationMillis = 200),
                                    label = "tint_color"
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(GeminiPrimaryDark.copy(alpha = backgroundAlpha))
                                        .clickable { currentScreen = screen }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(screen.titleRes),
                                        tint = tintColor,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .graphicsLayer(
                                                scaleX = iconScale,
                                                scaleY = iconScale
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!hasRecordAudioPermission) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Permission Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Microphone Permission Required",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "MK Softphone needs microphone access to make and receive calls. Please enable it in settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Dynamic Page Loading
                AnimatedContent(
                    targetState = currentScreen,
                    modifier = Modifier.fillMaxSize().weight(1f),
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "ScreenTransition"
                ) { target ->
                    when (target) {
                        ScreenRoute.Dialer -> DialerScreen(
                            accounts = activeAccounts,
                            primaryAccountId = primaryAccountId,
                            onDial = { accountId, destUri -> viewModel.makeSipCall(accountId, destUri) }
                        )
                        ScreenRoute.History -> HistoryScreen(
                            history = callHistory,
                            onClear = { viewModel.clearHistory() },
                            onCall = { number ->
                                if (activeAccounts.isNotEmpty()) {
                                    val accId = primaryAccountId?.takeIf { activeAccounts.containsKey(it) } ?: activeAccounts.keys.first()
                                    viewModel.makeSipCall(accId, number)
                                }
                            }
                        )
                        ScreenRoute.Contacts -> ContactsScreen(
                            contacts = contacts,
                            onAdd = { name, address -> viewModel.addContact(name, address) },
                            onDelete = { id -> viewModel.deleteContact(id) },
                            onCall = { address ->
                                if (activeAccounts.isNotEmpty()) {
                                    val accId = primaryAccountId?.takeIf { activeAccounts.containsKey(it) } ?: activeAccounts.keys.first()
                                    viewModel.makeSipCall(accId, address)
                                }
                            }
                        )
                        ScreenRoute.Accounts -> {
                            val probeState by viewModel.probeState.collectAsState()
                            AccountsScreen(
                                savedAccounts = savedAccounts,
                                activeAccounts = activeAccounts,
                                primaryAccountId = primaryAccountId,
                                probeState = probeState,
                                onAdd = { config -> viewModel.addSipAccount(config) },
                                onRemove = { id -> viewModel.removeSipAccount(id) },
                                onSetPrimary = { id -> viewModel.setPrimaryAccount(id) },
                                onProbe = { domain -> viewModel.probeTransports(domain) },
                                onNavigateToDetails = { account ->
                                    currentAccount = account
                                    currentScreen = ScreenRoute.AccountDetails
                                }
                            )
                        }
                        ScreenRoute.Settings -> SettingsScreen(
                            settings = settings,
                            onSave = { auto, stun, exp, log, proxy, domain ->
                                viewModel.updateSettings(auto, stun, exp, log, proxy, domain)
                            },
                            serviceRunning = serviceRunning, // Hook into actual service state
                            onToggleService = { running ->
                                if (running) viewModel.startSipService() else viewModel.stopSipService()
                            }
                        )
                        ScreenRoute.Recordings -> RecordingsScreen(
                            recordings = recordings,
                            onRefresh = { viewModel.refreshRecordings() }
                        )
                        ScreenRoute.Warnings -> WarningsScreen(logs = sipLogs)
                        ScreenRoute.AccountDetails -> currentAccount?.let { account ->
                            AccountDetailsScreen(
                                account = account,
                                onDismiss = { currentScreen = ScreenRoute.Accounts },
                                onSave = { updated ->
                                    viewModel.addSipAccount(updated)
                                    currentScreen = ScreenRoute.Accounts
                                },
                                onRemove = { id ->
                                    viewModel.removeSipAccount(id)
                                    currentScreen = ScreenRoute.Accounts
                                }
                            )
                        } ?: run {
                            // Fallback if currentAccount is null
                            LaunchedEffect(Unit) { currentScreen = ScreenRoute.Accounts }
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }

                // Call Overlay trigger
                if (activeCalls.isNotEmpty()) {
                    if (isOverlayMinimized) {
                        ActiveCallBar(
                            activeCalls = activeCalls,
                            onClick = { isOverlayMinimized = false }
                        )
                    } else {
                        ActiveCallOverlay(
                            activeCalls = activeCalls,
                            onAnswer = { id -> viewModel.answerActiveCall(id) },
                            onHangup = { id -> viewModel.hangupActiveCall(id) },
                            onHold = { id, hold -> viewModel.toggleHold(id, hold) },
                            onMute = { id, mute -> viewModel.toggleMute(id, mute) },
                            onToggleSpeaker = { id, on -> viewModel.toggleSpeakerphone(id, on) },
                            onToggleBluetooth = { id, on -> viewModel.toggleBluetooth(id, on) },
                            onToggleRecord = { id, record -> viewModel.toggleRecording(id, record) },
                            onSendDtmf = { id, digit -> viewModel.sendDtmf(id, digit) },
                            onAddCall = { 
                                // Navigation to Dialer while keeping call in background
                                currentScreen = ScreenRoute.Dialer
                                scope.launch { drawerState.close() }
                                isOverlayMinimized = true
                            },
                            onConference = { viewModel.mergeCalls() }
                        )
                    }
                }
            }
        }
        }
    }
}

// ==========================================
// SUB-SCREENS & UI COMPONENTS
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    accounts: Map<String, AccountWrapper>,
    primaryAccountId: String?,
    onDial: (String, String) -> Unit
) {
    var dialUri by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf("") }

    LaunchedEffect(primaryAccountId) {
        if (!primaryAccountId.isNullOrEmpty() && accounts.containsKey(primaryAccountId)) {
            selectedAccountId = primaryAccountId
        }
    }

    LaunchedEffect(accounts) {
        if (selectedAccountId.isEmpty() && accounts.isNotEmpty()) {
            selectedAccountId = primaryAccountId?.takeIf { accounts.containsKey(it) } ?: accounts.keys.first()
        }
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Watermark Logo centered and scaled to fit the screen width nicely
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_logo),
            contentDescription = "Watermark Logo",
            alpha = 0.05f,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- TOP SECTION: 32% Height, Display ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.32f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dialUri,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            color = Color.White,
                            fontSize = 36.sp,
                            letterSpacing = 1.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- BACKSPACE BAR SECTION: 48.dp Height ---
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
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- BOTTOM SECTION: 68% Height, Slate Keypad Area ---
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
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
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
                                                fontSize = 28.sp
                                            ),
                                            color = Color.White
                                        )
                                        if (letters.isNotEmpty()) {
                                            Text(
                                                text = letters,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 9.sp,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Call button (Centered white icon in glowing green circle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (selectedAccountId.isNotEmpty() && dialUri.isNotEmpty()) {
                                onDial(selectedAccountId, dialUri)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(DialerCallGreen)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
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
    letters: String = "",
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.02f)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = if (letters.isEmpty()) 32.sp else 28.sp
                ),
                color = Color.White
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<CallHistoryEntry>,
    onClear: () -> Unit,
    onCall: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_no_calls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.history_clear_all), color = MaterialTheme.colorScheme.error)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { entry ->
                    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val formattedDate = sdf.format(Date(entry.timestamp))
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(entry.number) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (entry.isIncoming) {
                                                if (entry.wasAnswered) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                            } else {
                                                Color(0xFFE3F2FD)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (entry.isIncoming) {
                                            if (entry.wasAnswered) Icons.Filled.CallReceived else Icons.Filled.CallMissed
                                        } else {
                                            Icons.Filled.CallMade
                                        },
                                        contentDescription = "Call Direction",
                                        tint = if (entry.isIncoming) {
                                            if (entry.wasAnswered) Color(0xFF4CAF50) else Color(0xFFF44336)
                                        } else {
                                            Color(0xFF2196F3)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = entry.number.substringAfter("sip:"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onCall(entry.number) }) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call Contact",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(
    contacts: List<SipContact>,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCall: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = App Contacts, 1 = Device Contacts
    var deviceContacts by remember { mutableStateOf<List<SipContact>>(emptyList()) }
    var hasContactPermission by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    fun checkPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    fun loadDeviceContacts() {
        if (checkPermission()) {
            val list = mutableListOf<SipContact>()
            try {
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) else java.util.UUID.randomUUID().toString()
                        val name = if (nameCol >= 0) c.getString(nameCol) else "Unknown"
                        val number = if (numCol >= 0) c.getString(numCol) else ""
                        if (number.isNotEmpty()) {
                            val cleanedNumber = number.replace(Regex("[\\s\\-\\(\\)]"), "")
                            if (cleanedNumber.isNotEmpty()) {
                                if (list.none { it.sipAddress == cleanedNumber }) {
                                    list.add(SipContact(id = id, displayName = name, sipAddress = cleanedNumber))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactsScreen", "Error reading contacts: ${e.message}")
            }
            deviceContacts = list
            hasContactPermission = true
        } else {
            hasContactPermission = false
        }
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadDeviceContacts()
        } else {
            hasContactPermission = false
        }
    }
    
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            if (checkPermission()) {
                loadDeviceContacts()
            } else {
                launcher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }
    }
    
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sipAddress by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.contacts_tab_app), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.contacts_tab_device), fontWeight = FontWeight.Bold) }
                )
            }
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                if (selectedTab == 0) {
                    if (contacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.contacts_no_saved),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(contacts) { contact ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCall(contact.sipAddress) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = contact.displayName.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = contact.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = contact.sipAddress.substringAfter("sip:"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { onDelete(contact.id) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete Contact",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            IconButton(onClick = { onCall(contact.sipAddress) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Call,
                                                    contentDescription = "Call Contact",
                                                    tint = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Device Contacts
                    if (!hasContactPermission) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContactPhone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.contacts_perm_required),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.contacts_perm_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) }) {
                                Text(stringResource(R.string.contacts_grant_perm))
                            }
                        }
                    } else if (deviceContacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.contacts_no_device),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(deviceContacts) { contact ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCall(contact.sipAddress) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = contact.displayName.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = contact.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = contact.sipAddress,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onCall(contact.sipAddress) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Call,
                                                contentDescription = "Call Contact",
                                                tint = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Contact FAB (Only for App Contacts)
        if (selectedTab == 0) {
            FloatingActionButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Contact")
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Add SIP Contact") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") }
                        )
                        OutlinedTextField(
                            value = sipAddress,
                            onValueChange = { sipAddress = it },
                            label = { Text("SIP Address") },
                            placeholder = { Text("echo@iptel.org") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && sipAddress.isNotEmpty()) {
                                val cleanAddr = if (sipAddress.startsWith("sip:")) sipAddress else "sip:$sipAddress"
                                onAdd(name, cleanAddr)
                                name = ""
                                sipAddress = ""
                                showDialog = false
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AccountsScreen(
    savedAccounts: List<SipAccountConfig>,
    activeAccounts: Map<String, AccountWrapper>,
    primaryAccountId: String?,
    probeState: MainScreenViewModel.ProbeResult,
    onAdd: (SipAccountConfig) -> Unit,
    onRemove: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onProbe: (String) -> Unit,
    onNavigateToDetails: (SipAccountConfig) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = SIP, 1 = IAX
    var showWizard by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.accounts_tab_sip), fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.accounts_tab_iax), fontWeight = FontWeight.Bold) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 1) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.accounts_iax_coming_soon),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.accounts_iax_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                val filteredAccounts = remember(savedAccounts, selectedTab) {
                    savedAccounts.filter { !it.id.startsWith("iax:") && it.transport != "IAX" }
                }

                if (filteredAccounts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.accounts_no_sip),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.accounts_add_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAccounts) { config ->
                            val activeAcc = activeAccounts[config.id]
                            val regState = activeAcc?.registrationState
                            
                            val (statusText, statusColor) = if (!config.isEnabled) {
                                "Deactivated" to Color.Gray
                            } else {
                                when (regState) {
                                    is RegistrationState.Registered -> "Registered" to Color(0xFF4CAF50)
                                    is RegistrationState.Registering -> "Registering..." to Color(0xFFFFC107)
                                    is RegistrationState.Failed -> "Failed: ${regState.reason}" to Color(0xFFF44336)
                                    else -> "Offline" to Color.Gray
                                }
                            }

                            val displayName = remember(config.id) {
                                val clean = config.id.substringAfter("sip:")
                                if (clean.contains("@")) clean.substringBefore("@") else clean
                            }
                            val firstLetter = remember(displayName) {
                                displayName.firstOrNull()?.toString()?.uppercase() ?: "?"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(alpha = if (config.isEnabled) 1.0f else 0.5f)
                                    .clickable { onNavigateToDetails(config) },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (primaryAccountId == config.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (primaryAccountId == config.id)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Left Profile Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (primaryAccountId == config.id)
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = firstLetter,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (primaryAccountId == config.id)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (regState is RegistrationState.Registered) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Ready",
                                                        tint = Color(0xFF4CAF50),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = config.domain,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (config.isPushEnabled) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "PUSH",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action Area: Default indicator or select button
                                    Box(modifier = Modifier.padding(start = 8.dp)) {
                                        if (primaryAccountId == config.id) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Default",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { onSetPrimary(config.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Set Default",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showWizard = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Account")
                }
            }
        }
    }

    if (showWizard) {
        AccountWizardDialog(
            probeState = probeState,
            onProbe = onProbe,
            onDismiss = { showWizard = false },
            onAdd = onAdd,
            isIax = selectedTab == 1
        )
    }
}

@Composable
fun AccountWizardDialog(
    probeState: MainScreenViewModel.ProbeResult,
    onProbe: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (SipAccountConfig) -> Unit,
    isIax: Boolean
) {
    var step by remember { mutableStateOf(1) }
    
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var useAuthProxy by remember { mutableStateOf(false) }
    var authUsername by remember { mutableStateOf("") }
    var outboundProxy by remember { mutableStateOf("") }
    
    var selectedTransport by remember { mutableStateOf(if (isIax) "IAX" else "UDP") }

    val bestTransport = remember(probeState) {
        if (isIax) "IAX" else {
            when {
                probeState.tls == MainScreenViewModel.ProbeStatus.Found -> "TLS"
                probeState.tcp == MainScreenViewModel.ProbeStatus.Found -> "TCP"
                probeState.udp == MainScreenViewModel.ProbeStatus.Found -> "UDP"
                else -> "UDP"
            }
        }
    }
    
    LaunchedEffect(bestTransport) {
        selectedTransport = bestTransport
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isIax) "IAX Account Wizard" else "SIP Account Wizard",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 1..4) {
                            val color = if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Step $step of 4: " + when (step) {
                            1 -> "Basic Credentials"
                            2 -> "Network Target"
                            3 -> "Optional Proxies"
                            4 -> "Transport Probing"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (step) {
                            1 -> {
                                Text(
                                    text = "Enter your primary VoIP credentials. Address formats like user@domain will auto-fill server targets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { input ->
                                        address = input
                                        if (input.contains("@")) {
                                            username = input.substringBefore("@")
                                            domain = input.substringAfter("@")
                                        } else {
                                            username = input
                                        }
                                    },
                                    label = { Text("SIP Address (username@domain)") },
                                    placeholder = { Text("100@sip.linphone.org") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            2 -> {
                                Text(
                                    text = "Confirm destination registrar. This represents the network target host.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = domain,
                                    onValueChange = { domain = it },
                                    label = { Text("Domain / Registrar Host") },
                                    placeholder = { Text("sip.linphone.org") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            3 -> {
                                Text(
                                    text = "Configure outbound proxy routing and specialized authentication identities if required by your provider.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { useAuthProxy = !useAuthProxy }
                                ) {
                                    Checkbox(checked = useAuthProxy, onCheckedChange = { useAuthProxy = it })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use Auth Username / Outbound Proxy")
                                }
                                if (useAuthProxy) {
                                    OutlinedTextField(
                                        value = authUsername,
                                        onValueChange = { authUsername = it },
                                        label = { Text("Authentication Username") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = outboundProxy,
                                        onValueChange = { outboundProxy = it },
                                        label = { Text("Outbound Proxy (optional)") },
                                        placeholder = { Text("proxy.linphone.org:5060") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            4 -> {
                                Text(
                                    text = "Probe destination port socket connectivity. Found paths indicate network responsiveness.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { onProbe(domain) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                ) {
                                    Text("Start Socket Probing")
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProbeRow("SIP TLS (TCP 5061)", probeState.tls)
                                    ProbeRow("SIP TCP (TCP 5060)", probeState.tcp)
                                    ProbeRow("SIP UDP (UDP 5060)", probeState.udp)
                                    ProbeRow("IAX UDP (UDP 4569)", probeState.iax)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select Transport Protocol", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    listOf("UDP", "TCP", "TLS").forEach { transport ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable(enabled = !isIax) { selectedTransport = transport }
                                        ) {
                                            RadioButton(
                                                selected = selectedTransport == transport,
                                                onClick = { selectedTransport = transport },
                                                enabled = !isIax
                                            )
                                            Text(transport)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        TextButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (step < 4) {
                        Button(
                            onClick = { step++ },
                            enabled = when (step) {
                                1 -> username.isNotEmpty() && password.isNotEmpty()
                                2 -> domain.isNotEmpty()
                                else -> true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Next")
                        }
                    } else {
                        Button(
                            onClick = {
                                val id = if (isIax) "iax:$username@$domain" else "sip:$username@$domain"
                                val config = SipAccountConfig(
                                    id = id,
                                    username = username,
                                    domain = domain,
                                    secret = password,
                                    authUsername = if (useAuthProxy) authUsername else "",
                                    outboundProxy = if (useAuthProxy) outboundProxy else "",
                                    transport = selectedTransport,
                                    isPushEnabled = false
                                )
                                onAdd(config)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Finish & Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProbeRow(label: String, status: MainScreenViewModel.ProbeStatus) {
    val (statusLabel, statusColor, bgColor) = when (status) {
        MainScreenViewModel.ProbeStatus.Found -> Triple("Found", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        MainScreenViewModel.ProbeStatus.NotFound -> Triple("Not Found", Color(0xFFC62828), Color(0xFFFFEBEE))
        MainScreenViewModel.ProbeStatus.Probing -> Triple("Probing...", Color(0xFFEF6C00), Color(0xFFFFF3E0))
        MainScreenViewModel.ProbeStatus.Untested -> Triple("Untested", Color(0xFF37474F), Color(0xFFECEFF1))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = Color.Black)
        Text(text = statusLabel, fontWeight = FontWeight.Bold, color = statusColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsScreen(
    account: SipAccountConfig,
    onDismiss: () -> Unit,
    onSave: (SipAccountConfig) -> Unit,
    onRemove: (String) -> Unit
) {
    var username by remember { mutableStateOf(account.username) }
    var domain by remember { mutableStateOf(account.domain) }
    var password by remember { mutableStateOf(account.secret) }
    var authUsername by remember { mutableStateOf(account.authUsername) }
    var outboundProxy by remember { mutableStateOf(account.outboundProxy) }
    var useCustomProxy by remember { mutableStateOf(account.outboundProxy.isNotEmpty()) }
    var isPushEnabled by remember { mutableStateOf(account.isPushEnabled) }
    var usePushProxy by remember { mutableStateOf(account.usePushProxy) }
    var transport by remember { mutableStateOf(account.transport) }
    var srtpMode by remember { mutableStateOf(account.srtpMode) }
    var zrtpEnabled by remember { mutableStateOf(account.zrtpEnabled) }
    var numberRewriting by remember { mutableStateOf(account.numberRewriting) }
    var isEnabled by remember { mutableStateOf(account.isEnabled) }

    var opusPriority by remember { mutableStateOf(account.opusPriority.toFloat()) }
    var pcmuPriority by remember { mutableStateOf(account.pcmuPriority.toFloat()) }
    var pcmaPriority by remember { mutableStateOf(account.pcmaPriority.toFloat()) }
    var g722Priority by remember { mutableStateOf(account.g722Priority.toFloat()) }
    var gsmPriority by remember { mutableStateOf(account.gsmPriority.toFloat()) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Configuration") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Save Changes") },
                icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                onClick = {
                    val updated = SipAccountConfig(
                        id = account.id,
                        username = username,
                        domain = domain,
                        secret = password,
                        authUsername = authUsername,
                        outboundProxy = if (useCustomProxy) outboundProxy else "",
                        isPushEnabled = isPushEnabled,
                        usePushProxy = usePushProxy,
                        transport = transport,
                        srtpMode = srtpMode,
                        zrtpEnabled = zrtpEnabled,
                        numberRewriting = numberRewriting,
                        opusPriority = opusPriority.toInt(),
                        pcmuPriority = pcmuPriority.toInt(),
                        pcmaPriority = pcmaPriority.toInt(),
                        g722Priority = g722Priority.toInt(),
                        gsmPriority = gsmPriority.toInt(),
                        isEnabled = isEnabled
                    )
                    onSave(updated)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Identity Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = username.ifEmpty { "New Extension" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (domain.isNotEmpty()) "sip:$username@$domain" else "sip:$username",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isEnabled) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else Color.Gray.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isEnabled) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isEnabled) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                }
            }

            // 2. Trunk Activation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isEnabled = !isEnabled }
                            .padding(16.dp)
                    ) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Account Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Enable or disable this SIP trunk configuration dynamically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 3. Credentials & Settings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SIP Credentials",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password / Secret") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Registrar Host / Domain") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = authUsername,
                            onValueChange = { authUsername = it },
                            label = { Text("Authentication Username (optional)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { useCustomProxy = !useCustomProxy }
                        ) {
                            Checkbox(checked = useCustomProxy, onCheckedChange = { useCustomProxy = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Use Custom Outbound Proxy", fontWeight = FontWeight.SemiBold)
                                Text("Override default system proxy settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (useCustomProxy) {
                            OutlinedTextField(
                                value = outboundProxy,
                                onValueChange = { outboundProxy = it },
                                label = { Text("Outbound Proxy") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // 4. Network and Advanced Security
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Network & Media Preferences",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { isPushEnabled = !isPushEnabled }
                        ) {
                            Checkbox(checked = isPushEnabled, onCheckedChange = { isPushEnabled = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Push Notifications", fontWeight = FontWeight.SemiBold)
                                Text("Includes RFC 8599 push tokens in registration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { usePushProxy = !usePushProxy }
                        ) {
                            Checkbox(checked = usePushProxy, onCheckedChange = { usePushProxy = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Enable PUSH Proxy", fontWeight = FontWeight.SemiBold)
                                Text("Routes via Flexisip for 3rd party push support", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column {
                            Text("Transport Preference", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("UDP", "TCP", "TLS").forEach { t ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { transport = t }
                                    ) {
                                        RadioButton(selected = transport == t, onClick = { transport = t })
                                        Text(t, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column {
                            Text("SRTP Mode Selection", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf(0 to "Disabled", 1 to "Optional", 2 to "Mandatory").forEach { (mode, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { srtpMode = mode }
                                    ) {
                                        RadioButton(selected = srtpMode == mode, onClick = { srtpMode = mode })
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = null,
                                enabled = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Enable ZRTP SAS Verification", 
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "ZRTP is unsupported in the current PJSIP build",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        OutlinedTextField(
                            value = numberRewriting,
                            onValueChange = { numberRewriting = it },
                            label = { Text("Number Rewriting Expression (Regex)") },
                            placeholder = { Text("^0([1-9]) => +33$1") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 5. Codec Priorities Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Audio Codec Priorities",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        CodecPrioritySlider("Opus (48kHz)", opusPriority) { opusPriority = it }
                        CodecPrioritySlider("PCMU (G.711u)", pcmuPriority) { pcmuPriority = it }
                        CodecPrioritySlider("PCMA (G.711a)", pcmaPriority) { pcmaPriority = it }
                        CodecPrioritySlider("G.722 (16kHz)", g722Priority) { g722Priority = it }
                        CodecPrioritySlider("GSM (8kHz)", gsmPriority) { gsmPriority = it }
                    }
                }
            }

            // 6. Danger Zone Card ( relocation of account delete )
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Permanently remove this SIP account configuration. This will unregister the extension from the server and clear credentials. This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp)) // Padding for FAB
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the account '${username}'? This will unregister the account from the server and remove all stored configurations. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onRemove(account.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CodecPrioritySlider(label: String, priority: Float, onPriorityChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(priority.toInt().toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = priority,
            onValueChange = onPriorityChange,
            valueRange = 0f..255f,
            steps = 254,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun SettingsScreen(
    settings: VoIpSettings,
    onSave: (Boolean, String, Int, Int, String, String) -> Unit,
    serviceRunning: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    var autoStart by remember { mutableStateOf(settings.autoStartOnBoot) }
    var stunServer by remember { mutableStateOf(settings.stunServer) }
    var expiry by remember { mutableStateOf(settings.registrationExpiry.toString()) }
    var logLevel by remember { mutableStateOf(settings.logLevel.toFloat()) }
    var outboundProxy by remember { mutableStateOf(settings.outboundProxy) }
    var localDomain by remember { mutableStateOf(settings.localDomain) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_engine_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_service_status),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (serviceRunning) stringResource(R.string.settings_service_active) else stringResource(R.string.settings_service_stopped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = serviceRunning,
                            onCheckedChange = { onToggleService(it) }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_auto_start),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.settings_auto_start_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoStart,
                            onCheckedChange = { autoStart = it }
                        )
                    }

                    OutlinedTextField(
                        value = stunServer,
                        onValueChange = { stunServer = it },
                        label = { Text("STUN Server") },
                        placeholder = { Text("stun.l.google.com:19302") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it },
                        label = { Text("Registration Expiry (seconds)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text(
                            text = "Native PJSIP Log Level: ${logLevel.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = logLevel,
                            onValueChange = { logLevel = it },
                            valueRange = 0f..6f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Network & Proxy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add your proxy to override the default proxy settings",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = outboundProxy,
                        onValueChange = { outboundProxy = it },
                        label = { Text("Outbound Proxy URI") },
                        placeholder = { Text("sips:proxy.example.com:5066;transport=tls") },
                        supportingText = {
                            Text(
                                "Applied to every account. Leave empty to use the default system proxy configuration.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = localDomain,
                        onValueChange = { localDomain = it },
                        label = { Text("Local SIP Domain") },
                        placeholder = { Text("sip.yourdomain.com") },
                        supportingText = {
                            Text(
                                "Accounts on this domain use TLS directly. Others route via the outbound proxy over UDP. Leave empty to use the default system transport logic.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    val expVal = expiry.toIntOrNull() ?: 300
                    onSave(autoStart, stunServer, expVal, logLevel.toInt(), outboundProxy.trim(), localDomain.trim())
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.settings_save_changes))
            }
        }
    }
}

@Composable
fun RecordingsScreen(
    recordings: List<File>,
    onRefresh: () -> Unit
) {
    var currentlyPlayingFile by remember { mutableStateOf<File?>(null) }
    val mediaPlayer = remember { android.media.MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    fun playFile(file: File) {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            currentlyPlayingFile = file
            
            mediaPlayer.setOnCompletionListener {
                currentlyPlayingFile = null
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingsScreen", "Error playing file: ${e.message}")
        }
    }

    fun stopPlayback() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        currentlyPlayingFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recordings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            }
        }

        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.recordings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings) { file ->
                    val isPlaying = currentlyPlayingFile == file && mediaPlayer.isPlaying
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) stopPlayback() else playFile(file)
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Stop" else "Play",
                                        tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isPlaying) stopPlayback()
                                        file.delete()
                                        onRefresh()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete recording",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarningsScreen(logs: List<SipLogEntry>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.warnings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.warnings_empty),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(logs) { log ->
                    val color = when (log.level) {
                        1 -> Color(0xFFF44336) // Red for Errors
                        2 -> Color(0xFFFF9800) // Orange for Warning
                        3, 4 -> Color(0xFF81C784) // Greenish/Cyan for Info/Debug
                        else -> Color.White
                    }
                    Text(
                        text = "[${log.threadName}] ${log.message}",
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .then(
                    if (isActive) {
                        Modifier.background(GeminiGlowBrush, CircleShape)
                    } else {
                        Modifier
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    }
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun WaveformVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val barCount = 12
    val animProgresses = (0 until barCount).map { index ->
        if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index % 4) * 120,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        } else {
            remember { mutableStateOf(0.15f) }
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spacing = 6.dp.toPx()
            val barWidth = 6.dp.toPx()
            
            val totalWidth = barCount * barWidth + (barCount - 1) * spacing
            val startX = (width - totalWidth) / 2
            
            for (i in 0 until barCount) {
                val progress = animProgresses[i].value
                val multiplier = when (i) {
                    0, 11 -> 0.3f
                    1, 10 -> 0.5f
                    2, 9 -> 0.7f
                    3, 8 -> 0.9f
                    else -> 1.0f
                }
                val barHeight = height * progress * multiplier
                val x = startX + i * (barWidth + spacing)
                val y = (height - barHeight) / 2
                
                drawRoundRect(
                    brush = GeminiGlowBrush,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    activeCalls: Map<Int, CallWrapper>,
    onAnswer: (Int) -> Unit,
    onHangup: (Int) -> Unit,
    onHold: (Int, Boolean) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onToggleSpeaker: (Int, Boolean) -> Unit,
    onToggleBluetooth: (Int, Boolean) -> Unit,
    onToggleRecord: (Int, Boolean) -> Unit,
    onSendDtmf: (Int, String) -> Unit,
    onAddCall: () -> Unit,
    onConference: () -> Unit
) {
    var showDialpad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedCallId by remember(activeCalls) { 
        mutableStateOf(activeCalls.keys.firstOrNull() ?: -1) 
    }
    
    val call = activeCalls[selectedCallId] ?: activeCalls.values.firstOrNull() ?: return
    if (selectedCallId == -1) selectedCallId = call.callId
    var durationText by remember { mutableStateOf("00:00") }

    var isProximityNear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isProximityNear = distance < maxRange
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && proximitySensor != null) {
            sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    LaunchedEffect(call.connectTimestamp, call.callState) {
        if (call.callState == SipCallState.Confirmed && call.connectTimestamp != null) {
            while (true) {
                val elapsedMs = System.currentTimeMillis() - call.connectTimestamp
                val totalSecs = elapsedMs / 1000
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                durationText = String.format("%02d:%02d", mins, secs)
                kotlinx.coroutines.delay(1000)
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
                // --- TOP SECTION: 28% Height, Glassmorphic (Active Call Info & Icons) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.28f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
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
                            Text(
                                text = call.peerUri.substringAfter("sip:"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White,
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
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.NetworkCell,
                                contentDescription = "Signal",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 5-Control Icons Row (Speaker, Mute, Keypad, Hold, More) wrapped in a translucent card dock
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.03f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Speaker
                            val isSpeakerOn = call.isSpeakerphoneOn
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSpeaker(call.callId, !isSpeakerOn)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSpeakerOn) GeminiPrimaryDark.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.5.dp, if (isSpeakerOn) GeminiPrimaryDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = "Speaker",
                                    tint = if (isSpeakerOn) GeminiPrimaryDark else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 2. Mute
                            val isMuted = call.isMuted
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMute(call.callId, !isMuted)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) GeminiPrimaryDark.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.5.dp, if (isMuted) GeminiPrimaryDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) GeminiPrimaryDark else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 3. Keypad (Toggle DTMF Pad)
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDialpad = !showDialpad
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (showDialpad) GeminiPrimaryDark.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.5.dp, if (showDialpad) GeminiPrimaryDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dialpad,
                                    contentDescription = "Keypad",
                                    tint = if (showDialpad) GeminiPrimaryDark else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 4. Hold
                            val isOnHold = call.isLocalHold
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onHold(call.callId, !isOnHold)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnHold) GeminiPrimaryDark.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.5.dp, if (isOnHold) GeminiPrimaryDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = "Hold",
                                    tint = if (isOnHold) GeminiPrimaryDark else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 5. More (Dropdown Menu following app theme)
                            Box {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showMoreMenu = true
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (showMoreMenu) GeminiPrimaryDark.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(1.5.dp, if (showMoreMenu) GeminiPrimaryDark else Color.Transparent, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                        tint = if (showMoreMenu) GeminiPrimaryDark else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1E1E1E))
                                        .border(1.dp, GeminiPrimaryDark, RoundedCornerShape(8.dp))
                                ) {
                                    val isRecording = call.isRecording
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isRecording) "Stop Recording" else "Record Call",
                                                color = Color.White
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                                contentDescription = null,
                                                tint = if (isRecording) Color.Red else GeminiPrimaryDark
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onToggleRecord(call.callId, !isRecording)
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (call.isBluetoothOn) "Disable Bluetooth" else "Enable Bluetooth",
                                                color = Color.White
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Bluetooth,
                                                contentDescription = null,
                                                tint = if (call.isBluetoothOn) GeminiPrimaryDark else Color.White
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onToggleBluetooth(call.callId, !call.isBluetoothOn)
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Add Call",
                                                color = Color.White
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                tint = GeminiPrimaryDark
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onAddCall()
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Merge Calls (Conference)",
                                                color = if (activeCalls.size > 1) Color.White else Color.Gray
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.CallMerge,
                                                contentDescription = null,
                                                tint = if (activeCalls.size > 1) GeminiPrimaryDark else Color.Gray
                                            )
                                        },
                                        enabled = activeCalls.size > 1,
                                        onClick = {
                                            showMoreMenu = false
                                            onConference()
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
                            .weight(0.72f)
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
                                    color = Color.White
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
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = Color.White,
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
                                                .background(Color.White.copy(alpha = 0.04f))
                                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
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
                                                        fontSize = 26.sp
                                                    ),
                                                    color = Color.White
                                                )
                                                if (letters.isNotEmpty()) {
                                                    Text(
                                                        text = letters,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Normal,
                                                            fontSize = 9.sp,
                                                            letterSpacing = 0.5.sp
                                                        ),
                                                        color = Color.White.copy(alpha = 0.5f)
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
                            .weight(0.72f)
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
                
                if (isProximityNear) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    }
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
    onClick: () -> Unit
) {
    val callCount = activeCalls.size
    val firstCall = activeCalls.values.first()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCE0F2015)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Green.copy(alpha = 0.3f),
                    Color.Green.copy(alpha = 0.05f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (callCount > 1) "$callCount Active Calls" else "Active Call: ${firstCall.peerUri.substringAfter("sip:")}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Tap to return to call screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = "Expand",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


