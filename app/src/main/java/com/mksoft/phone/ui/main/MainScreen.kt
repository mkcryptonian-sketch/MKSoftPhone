package com.mksoft.phone.ui.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mksoft.phone.R
import com.mksoft.phone.core.sip.*
import com.mksoft.phone.data.*
import com.mksoft.phone.theme.*
import com.mksoft.phone.ui.main.components.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

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
    val isConferenceActive by viewModel.isConferenceActive.collectAsState()
    val sipLogs by viewModel.sipLogs.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val primaryAccountId by viewModel.primaryAccountId.collectAsState()
    val callStats by viewModel.callStats.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenRoute.Dialer) }
    var selectedSettingsSection by remember { mutableStateOf(SettingsSection.Identity) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var currentAccount by remember { mutableStateOf<SipAccountConfig?>(null) }

    LaunchedEffect(currentScreen) {
        if (currentScreen == ScreenRoute.Settings) {
            isSettingsExpanded = true
        }
    }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var showAccountDropdown by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    // Auto-register on first launch AND every time the app comes to foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.ensureRegistered()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == ScreenRoute.Recordings) {
            viewModel.refreshRecordings()
        }
    }

    if (currentScreen != ScreenRoute.Dialer) {
        BackHandler {
            if (currentScreen == ScreenRoute.AccountDetails) {
                currentScreen = ScreenRoute.Accounts
            } else {
                currentScreen = ScreenRoute.Dialer
            }
        }
    }

    // Handle logout transition
    if (isLoggingOut) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GeminiPrimaryDark)
                Spacer(Modifier.height(16.dp))
                Text("Logging out...", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen != ScreenRoute.AccountDetails,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                // Premium Styled Header with Signature Gradient Accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Background gradient glow accent at the top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(GeminiGlowBrush)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(72.dp),
                            border = BorderStroke(1.5.dp, GeminiGlowBrush)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(56.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "MK Softphone",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            if (activeAccounts.isNotEmpty()) "${activeAccounts.size} Account(s) Active" else "System Offline",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Tight Content Column - Scrollable to prevent overflow
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (activeAccounts.isNotEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            val selectedAccount = activeAccounts[primaryAccountId] ?: activeAccounts.values.firstOrNull()
                            
                            OutlinedCard(
                                onClick = { showAccountDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selectedAccount?.registrationState is RegistrationState.Registered) 
                                                    Color(0xFF4CAF50) else Color(0xFFF44336)
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Active Line",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            selectedAccount?.let { "${it.username}@${it.domain}" } ?: "None",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
 
                            DropdownMenu(
                                expanded = showAccountDropdown,
                                onDismissRequest = { showAccountDropdown = false },
                                modifier = Modifier.width(260.dp)
                            ) {
                                activeAccounts.values.forEach { account ->
                                    val accId = "sip:${account.username}@${account.domain}"
                                    DropdownMenuItem(
                                        text = { Text("${account.username}@${account.domain}", fontSize = 13.sp) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (account.registrationState is RegistrationState.Registered) 
                                                            Color(0xFF4CAF50) else Color(0xFFF44336)
                                                    )
                                            )
                                        },
                                        onClick = {
                                            viewModel.setPrimaryAccount(accId)
                                            showAccountDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), thickness = 0.5.dp)
                    }
 
                    // Accounts Item
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(stringResource(ScreenRoute.Accounts.titleRes), fontSize = 13.sp) },
                        selected = currentScreen == ScreenRoute.Accounts,
                        onClick = {
                            currentScreen = ScreenRoute.Accounts
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = GeminiPrimaryDark.copy(alpha = 0.1f),
                            selectedIconColor = GeminiPrimaryDark,
                            selectedTextColor = GeminiPrimaryDark
                        )
                    )
 
                    Spacer(modifier = Modifier.height(4.dp))
 
                    // Settings Collapsible Item Group
                    val isSettingsActive = currentScreen == ScreenRoute.Settings
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Settings", fontSize = 13.sp) },
                        selected = isSettingsActive,
                        badge = {
                            Icon(
                                imageVector = if (isSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSettingsActive) GeminiPrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        onClick = {
                            isSettingsExpanded = !isSettingsExpanded
                            if (currentScreen != ScreenRoute.Settings) {
                                currentScreen = ScreenRoute.Settings
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = GeminiPrimaryDark.copy(alpha = 0.1f),
                            selectedIconColor = GeminiPrimaryDark,
                            selectedTextColor = GeminiPrimaryDark
                        )
                    )
 
                    if (isSettingsExpanded) {
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .animateContentSize()
                        ) {
                            SettingsSection.entries.forEach { section ->
                                val isSelected = isSettingsActive && selectedSettingsSection == section
                                NavigationDrawerItem(
                                    icon = { Icon(imageVector = section.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(section.label, fontSize = 12.sp) },
                                    selected = isSelected,
                                    onClick = {
                                        selectedSettingsSection = section
                                        currentScreen = ScreenRoute.Settings
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 1.dp)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = GeminiPrimaryDark.copy(alpha = 0.08f),
                                        selectedIconColor = GeminiPrimaryDark,
                                        selectedTextColor = GeminiPrimaryDark,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }
                    }
 
                    Spacer(modifier = Modifier.height(4.dp))
 
                    // Recordings Item
                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(stringResource(ScreenRoute.Recordings.titleRes), fontSize = 13.sp) },
                        selected = currentScreen == ScreenRoute.Recordings,
                        onClick = {
                            currentScreen = ScreenRoute.Recordings
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = GeminiPrimaryDark.copy(alpha = 0.1f),
                            selectedIconColor = GeminiPrimaryDark,
                            selectedTextColor = GeminiPrimaryDark
                        )
                    )
                }

                // Footer items
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Logout", fontSize = 12.sp) },
                        selected = false,
                        onClick = { showLogoutConfirm = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = MaterialTheme.colorScheme.error, unselectedIconColor = MaterialTheme.colorScheme.error)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Exit", fontSize = 12.sp) },
                        selected = false,
                        onClick = { showExitConfirm = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (currentScreen != ScreenRoute.AccountDetails) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                stringResource(currentScreen.titleRes),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {},
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            bottomBar = {
                if (currentScreen != ScreenRoute.AccountDetails) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        val navItems = listOf(
                            ScreenRoute.Dialer to Icons.Default.Dialpad,
                            ScreenRoute.History to Icons.Default.History,
                            ScreenRoute.Contacts to Icons.Default.Contacts
                        )
                        navItems.forEach { (screen, icon) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = stringResource(screen.titleRes)) },
                                label = { Text(stringResource(screen.titleRes)) },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GeminiPrimaryDark,
                                    selectedTextColor = GeminiPrimaryDark,
                                    indicatorColor = GeminiPrimaryDark.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                // Render screens
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { target ->
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
                                probeResult = probeState,
                                onAddAccount = { config -> viewModel.addSipAccount(config) },
                                onRemoveAccount = { id -> viewModel.removeSipAccount(id) },
                                onSetPrimary = { id -> viewModel.setPrimaryAccount(id) },
                                onProbe = { domain -> viewModel.probeTransports(domain) },
                                onEditAccount = { account ->
                                    currentAccount = account
                                    currentScreen = ScreenRoute.AccountDetails
                                }
                            )
                        }
                        ScreenRoute.Settings -> SettingsScreen(
                            settings = settings,
                            onUpdateSettings = { newSettings ->
                                viewModel.updateSettings(
                                    autoStart = newSettings.autoStartOnBoot,
                                    stunServer = newSettings.stunServer,
                                    expiry = newSettings.registrationExpiry,
                                    logLevel = newSettings.logLevel,
                                    outboundProxy = newSettings.outboundProxy,
                                    localDomain = newSettings.localDomain,
                                    aecEnabled = newSettings.aecEnabled,
                                    agcEnabled = newSettings.agcEnabled,
                                    wakeLockEnabled = newSettings.wakeLockEnabled,
                                    backgroundKeepAliveEnabled = newSettings.backgroundKeepAliveEnabled,
                                    transportProtocol = newSettings.transportProtocol,
                                    turnServer = newSettings.turnServer,
                                    iceEnabled = newSettings.iceEnabled,
                                    keepAliveInterval = newSettings.keepAliveInterval,
                                    rportEnabled = newSettings.rportEnabled,
                                    ipv6Preference = newSettings.ipv6Preference,
                                    dtmfMethod = newSettings.dtmfMethod,
                                    echoCancellationType = newSettings.echoCancellationType,
                                    nativeCallIntegrationEnabled = newSettings.nativeCallIntegrationEnabled,
                                    pushNotificationsEnabled = newSettings.pushNotificationsEnabled,
                                    dndSyncEnabled = newSettings.dndSyncEnabled,
                                    attendedTransferEnabled = newSettings.attendedTransferEnabled,
                                    blfEnabled = newSettings.blfEnabled,
                                    videoEnabled = newSettings.videoEnabled,
                                    limeEnabled = newSettings.limeEnabled,
                                    proximitySensorEnabled = newSettings.proximitySensorEnabled,
                                    zrtpSasDisplayEnabled = newSettings.zrtpSasDisplayEnabled,
                                    sipMessagingEnabled = newSettings.sipMessagingEnabled,
                                    postQuantumEnabled = newSettings.postQuantumEnabled
                                )
                            },
                            serviceRunning = serviceRunning,
                            onToggleService = { running ->
                                if (running) viewModel.startSipService() else viewModel.stopSipService()
                            },
                            selectedSection = selectedSettingsSection
                        )
                        ScreenRoute.Recordings -> RecordingsScreen(
                            recordings = recordings,
                            onRefresh = { viewModel.refreshRecordings() },
                            onDelete = { file -> viewModel.deleteRecording(file) }
                        )
                        // Messaging and Chat screens removed
                        ScreenRoute.AccountDetails -> currentAccount?.let { account ->
                            AccountDetailsScreen(
                                account = account,
                                onBack = { currentScreen = ScreenRoute.Accounts },
                                onSave = { updated ->
                                    viewModel.addSipAccount(updated)
                                    currentScreen = ScreenRoute.Accounts
                                },
                                onSetPrimary = { id ->
                                    viewModel.setPrimaryAccount(id)
                                }
                            )
                        } ?: run {
                            LaunchedEffect(Unit) { currentScreen = ScreenRoute.Accounts }
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }

                // Call Overlay
                if (activeCalls.isNotEmpty()) {
                    val transferSession by viewModel.transferSession.collectAsState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        ActiveCallOverlay(
                            activeCalls = activeCalls,
                            isConferenceActive = isConferenceActive,
                            callStats = callStats,
                            transferSession = transferSession,
                            onAnswer = { id -> viewModel.answerActiveCall(id) },
                            onHangup = { id -> viewModel.hangupActiveCall(id) },
                            onHold = { id, hold -> viewModel.toggleHold(id, hold) },
                            onMute = { id, mute -> viewModel.toggleMute(id, mute) },
                            onToggleSpeaker = { id, on -> viewModel.toggleSpeakerphone(id, on) },
                            onToggleBluetooth = { id, on -> viewModel.toggleBluetooth(id, on) },
                            onToggleRecord = { id, rec -> viewModel.toggleRecording(id, rec) },
                            onSendDtmf = { id, digit -> viewModel.sendDtmf(id, digit) },
                            onAddCall = { /* TODO: Add Call navigation */ },
                            onConference = { viewModel.mergeCalls() },
                            onTransferCall = { id, dest -> viewModel.transferCall(id, dest) },
                            onCompleteTransfer = { viewModel.completeTransfer() },
                            onCancelTransfer = { viewModel.cancelTransfer() }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout? This will disconnect all active accounts.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        isLoggingOut = true
                        viewModel.logout {
                            isLoggingOut = false
                            context.findActivity()?.finish()
                        }
                    }
                ) { Text("Logout", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit") },
            text = { Text("Do you want to close the application? The SIP service will continue to run in the background.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        context.findActivity()?.finish()
                    }
                ) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
