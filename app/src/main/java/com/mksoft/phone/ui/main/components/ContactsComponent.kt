package com.mksoft.phone.ui.main.components

import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mksoft.phone.data.SipContact
import com.mksoft.phone.theme.DialerCallGreen
import com.mksoft.phone.theme.GeminiPrimaryDark
import java.util.*

@Composable
fun ContactsScreen(
    onCall: (String) -> Unit
) {
    var deviceContacts by remember { mutableStateOf<List<SipContact>>(emptyList()) }
    var hasContactPermission by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
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
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) else UUID.randomUUID().toString()
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
                Log.e("ContactsScreen", "Error reading contacts: ${e.message}")
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
    
    LaunchedEffect(Unit) {
        if (checkPermission()) {
            loadDeviceContacts()
        } else {
            launcher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }
    
    val filteredContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter { contact ->
                contact.displayName.contains(searchQuery, ignoreCase = true) ||
                contact.sipAddress.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Input Block
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { 
                    Text(
                        text = "Search contacts...", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                )
            )

            if (filteredContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (!hasContactPermission) "Contacts permission required" else "No contacts found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasContactPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredContacts) { contact ->
                        Card(
                            onClick = { onCall(contact.sipAddress) },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
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
                                        .background(GeminiPrimaryDark.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.displayName.firstOrNull()?.toString() ?: "?",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = GeminiPrimaryDark
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = contact.sipAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Call button only

                                    IconButton(
                                        onClick = { onCall(contact.sipAddress) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(DialerCallGreen.copy(alpha = 0.1f))
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = "Call", tint = DialerCallGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
