package com.mksoft.phone.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiGlowBrush
import com.mksoft.phone.theme.GeminiPrimaryDark
import com.mksoft.phone.theme.GeminiSecondaryDark
import com.mksoft.phone.theme.GeminiTertiaryDark

data class PermissionStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color,
    val isGranted: (Context) -> Boolean,
    val requestAction: (Context) -> Unit
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    fun batteryGranted() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    } else true

    fun overlayGranted() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        Settings.canDrawOverlays(context) else true

    fun dndGranted() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .isNotificationPolicyAccessGranted
    } else true

    val steps = remember {
        listOf(
            PermissionStep(
                icon = Icons.Filled.BatteryFull,
                title = "Background Calling",
                description = "Allows the app to keep your SIP connection alive in the background so you never miss an incoming call — even when your screen is off.",
                accentColor = DialerCallGreen,
                isGranted = { batteryGranted() },
                requestAction = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .apply { data = Uri.parse("package:${ctx.packageName}") }
                            )
                        } catch (_: Exception) {}
                    }
                }
            ),
            PermissionStep(
                icon = Icons.Filled.Layers,
                title = "Display Over Other Apps",
                description = "Required to show the in-call screen on top of other apps and display incoming call alerts when the phone is locked.",
                accentColor = GeminiPrimaryDark,
                isGranted = { overlayGranted() },
                requestAction = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    .apply { data = Uri.parse("package:${ctx.packageName}") }
                            )
                        } catch (_: Exception) {}
                    }
                }
            ),
            PermissionStep(
                icon = Icons.Filled.NotificationsActive,
                title = "Do Not Disturb Access",
                description = "Lets the app ring through Do Not Disturb mode for critical incoming calls, ensuring you're reachable when it matters most.",
                accentColor = GeminiTertiaryDark,
                isGranted = { dndGranted() },
                requestAction = { ctx ->
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } catch (_: Exception) {}
                }
            )
        )
    }

    // Recheck grant states on each recomposition (user may have returned from settings)
    var grantStates by remember { mutableStateOf(steps.map { it.isGranted(context) }) }

    LaunchedEffect(Unit) {
        // Recheck every second while onboarding is visible
        while (true) {
            grantStates = steps.map { it.isGranted(context) }
            kotlinx.coroutines.delay(1000)
        }
    }

    val allGranted = grantStates.all { it }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle radial glow behind the content
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GeminiPrimaryDark.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Header icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GeminiPrimaryDark.copy(alpha = 0.15f))
                    .border(1.dp, GeminiPrimaryDark.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = GeminiPrimaryDark,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Set Up Permissions",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Grant these permissions so the app can reliably handle calls in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            steps.forEachIndexed { index, step ->
                PermissionCard(
                    step = step,
                    isGranted = grantStates[index],
                    onClick = { step.requestAction(context) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary CTA
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(GeminiGlowBrush, CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                if (allGranted) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Set — Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text("Continue Without Full Access", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    step: PermissionStep,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "border_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = if (isGranted) 0.4f else 0.15f,
        targetValue  = if (isGranted) 0.4f else 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "border_alpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) DialerCallGreen.copy(alpha = 0.5f)
                        else step.accentColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isGranted) DialerCallGreen.copy(alpha = 0.15f)
                        else step.accentColor.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else step.icon,
                    contentDescription = null,
                    tint = if (isGranted) DialerCallGreen else step.accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                if (!isGranted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, step.accentColor.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = step.accentColor
                        )
                    ) {
                        Text("Grant Access", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}
