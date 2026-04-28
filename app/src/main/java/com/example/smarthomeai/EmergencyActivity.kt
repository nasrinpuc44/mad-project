package com.example.smarthomeai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Color Palette
private val EmergencyDark = Color(0xFFCC0000)

class EmergencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmergencyScreen(
                onBackClick = { finish() },
                onSendSms = { phone, message ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phone")
                        putExtra("sms_body", message)
                    }
                    startActivity(intent)
                },
                onOpenMap = { lat, lng ->
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(My Live Location)")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                onMakeCall = { phone ->
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phone")
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun EmergencyScreen(
    onBackClick: () -> Unit,
    onSendSms: (String, String) -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onMakeCall: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fireAlert by remember { mutableStateOf(false) }
    var gasAlert by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var isSosPressed by remember { mutableStateOf(false) }
    var pulseAnimation by remember { mutableStateOf(false) }

    // Emergency contacts list - starts with mandatory 999
    var contacts by remember {
        mutableStateOf<List<EmergencyContact>>(
            listOf(
                EmergencyContact("National Emergency", "999", "emergency", isEmergencyNumber = true)
            )
        )
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showSuccessToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    val latitude = 23.8103
    val longitude = 90.4125
    val locationLink = "https://maps.google.com/?q=$latitude,$longitude"
    val emergencyMessage = "⚠️ EMERGENCY ALERT! ⚠️\n\nI need immediate assistance. My live location: $locationLink\n\nTime: ${java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.getDefault()).format(java.util.Date())}"

    fun showFeedback(message: String) {
        toastMessage = message
        showSuccessToast = true
        coroutineScope.launch {
            delay(2000)
            showSuccessToast = false
        }
    }

    // Pulse animation for SOS button
    LaunchedEffect(isSosPressed) {
        while (isSosPressed) {
            pulseAnimation = true
            delay(500)
            pulseAnimation = false
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Animated Top Bar
            AnimatedEmergencyTopBar(onBackClick = onBackClick)

            // SOS Button Section
            SosButtonSection(
                isSosPressed = isSosPressed,
                pulseAnimation = pulseAnimation,
                onSosPress = {
                    isSosPressed = true
                    showAlertDialog = true
                    // Send SOS to ALL emergency contacts including 999 and user-added ones
                    contacts.forEach { contact ->
                        onSendSms(contact.phone, emergencyMessage)
                    }
                    showFeedback("✓ SOS Alert sent to all emergency contacts")
                    coroutineScope.launch {
                        delay(3000)
                        isSosPressed = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Alert Status Card
            EnhancedAlertStatusCard(
                fireAlert = fireAlert,
                gasAlert = gasAlert,
                onFireChange = {
                    fireAlert = it
                    if (it) showFeedback("⚠️ Fire Alert Activated!")
                },
                onGasChange = {
                    gasAlert = it
                    if (it) showFeedback("⚠️ Gas Leak Alert Activated!")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Location Card - opens Google Maps
            EnhancedLocationCard(
                latitude = latitude,
                longitude = longitude,
                onOpenMap = { onOpenMap(latitude, longitude) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Emergency Contacts Section
            EnhancedContactListCard(
                contacts = contacts,
                onAddClick = {
                    editIndex = null
                    showAddDialog = true
                },
                onEditClick = { index ->
                    // Prevent editing of mandatory 999 contact
                    if (!contacts[index].isEmergencyNumber) {
                        editIndex = index
                        showAddDialog = true
                    } else {
                        showFeedback("⚠️ Cannot edit National Emergency number (999)")
                    }
                },
                onSmsClick = { contact ->
                    onSendSms(contact.phone, emergencyMessage)
                    showFeedback("✓ SMS composer opened for ${contact.name}")
                },
                onCallClick = { contact ->
                    onMakeCall(contact.phone)
                    showFeedback("📞 Calling ${contact.name}...")
                },
                onDeleteClick = { index ->
                    // Prevent deletion of mandatory 999 contact
                    if (!contacts[index].isEmergencyNumber) {
                        contacts = contacts.toMutableList().apply { removeAt(index) }
                        showFeedback("✓ Contact deleted")
                    } else {
                        showFeedback("⚠️ Cannot delete National Emergency number (999)")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Alert Dialog
        if (showAlertDialog) {
            EmergencyAlertDialog(
                onDismiss = {
                    showAlertDialog = false
                    isSosPressed = false
                }
            )
        }

        // Add/Edit Contact Dialog
        if (showAddDialog) {
            AddEditContactDialog(
                oldContact = editIndex?.let { contacts[it] },
                onDismiss = { showAddDialog = false },
                onSave = { contact ->
                    contacts = if (editIndex == null) {
                        contacts + contact
                    } else {
                        contacts.toMutableList().also {
                            it[editIndex!!] = contact
                        }
                    }
                    showAddDialog = false
                    showFeedback(if (editIndex == null) "✓ Contact added" else "✓ Contact updated")
                }
            )
        }

        // Success Toast
        if (showSuccessToast) {
            CustomToast(message = toastMessage)
        }
    }
}

@Composable
fun AnimatedEmergencyTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardDark)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = EmergencyRed,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Emergency",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "SOS & Safety Features",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SosButtonSection(
    isSosPressed: Boolean,
    pulseAnimation: Boolean,
    onSosPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SOS EMERGENCY",
            color = EmergencyRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(if (pulseAnimation) 1.05f else 1f),
            contentAlignment = Alignment.Center
        ) {
            // Pulse rings
            if (pulseAnimation) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.05f))
                )
            }

            Button(
                onClick = onSosPress,
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "SOS",
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "SOS",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Press for Help",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Emergency contacts will receive your location",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun EnhancedAlertStatusCard(
    fireAlert: Boolean,
    gasAlert: Boolean,
    onFireChange: (Boolean) -> Unit,
    onGasChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Alert Status",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (fireAlert || gasAlert)
                        EmergencyRed.copy(alpha = 0.1f)
                    else GreenAccent.copy(alpha = 0.1f)
                ) {
                    Text(
                        if (fireAlert || gasAlert) "DANGER" else "SAFE",
                        color = if (fireAlert || gasAlert) EmergencyRed else GreenAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            EnhancedAlertSwitchRow(
                title = "Fire Alert",
                subtitle = "Smoke / Flame detection",
                icon = Icons.Default.Whatshot,
                checked = fireAlert,
                onChange = onFireChange,
                alertColor = EmergencyRed
            )

            Spacer(modifier = Modifier.height(12.dp))

            EnhancedAlertSwitchRow(
                title = "Gas Leak Alert",
                subtitle = "Natural gas / LPG detection",
                icon = Icons.Default.CrisisAlert,
                checked = gasAlert,
                onChange = onGasChange,
                alertColor = YellowAccent
            )
        }
    }
}

@Composable
fun EnhancedAlertSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    alertColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) alertColor.copy(alpha = 0.1f) else ChipBg
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) alertColor.copy(alpha = 0.3f) else Color(0xFF252525)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (checked) alertColor.copy(alpha = 0.15f)
                            else Color(0xFF252525)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = if (checked) alertColor else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        title,
                        color = if (checked) alertColor else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        subtitle,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = alertColor,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ChipBg
                )
            )
        }
    }
}

@Composable
fun EnhancedLocationCard(
    latitude: Double,
    longitude: Double,
    onOpenMap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlueAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = BlueAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "Live Location",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GreenAccent.copy(alpha = 0.1f)
                ) {
                    Text(
                        "GPS Ready",
                        color = GreenAccent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "📍 Coordinates",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Lat: $latitude",
                            color = BlueAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Lng: $longitude",
                            color = BlueAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in Google Maps", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EnhancedContactListCard(
    contacts: List<EmergencyContact>,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onSmsClick: (EmergencyContact) -> Unit,
    onCallClick: (EmergencyContact) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        "Emergency Contacts",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = GreenAccent)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No contacts added",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            "Tap + to add emergency contacts",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                contacts.forEachIndexed { index, contact ->
                    EnhancedContactRow(
                        contact = contact,
                        isEmergencyNumber = contact.isEmergencyNumber,
                        onSmsClick = { onSmsClick(contact) },
                        onCallClick = { onCallClick(contact) },
                        onEditClick = { onEditClick(index) },
                        onDeleteClick = { onDeleteClick(index) }
                    )

                    if (index < contacts.size - 1) {
                        HorizontalDivider(
                            color = Color(0xFF252525),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedContactRow(
    contact: EmergencyContact,
    isEmergencyNumber: Boolean,
    onSmsClick: () -> Unit,
    onCallClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEmergencyNumber) EmergencyRed.copy(alpha = 0.2f)
                        else EmergencyRed.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isEmergencyNumber) Icons.Default.Warning else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isEmergencyNumber) EmergencyRed else EmergencyRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isEmergencyNumber) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isEmergencyNumber) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = EmergencyRed.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "National",
                                color = EmergencyRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    contact.phone,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GreenAccent.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = GreenAccent, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onSmsClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BlueAccent.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Sms, contentDescription = "SMS", tint = BlueAccent, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YellowAccent.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (isEmergencyNumber) TextSecondary else YellowAccent, modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = { if (!isEmergencyNumber) showDeleteDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isEmergencyNumber) Color.Transparent else EmergencyRed.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (isEmergencyNumber) TextSecondary else EmergencyRed, modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact", color = TextPrimary) },
            text = { Text("Are you sure you want to delete ${contact.name}?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteClick()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = EmergencyRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardDark
        )
    }
}

@Composable
fun EmergencyAlertDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed)
                Text("SOS Alert Sent!", color = TextPrimary)
            }
        },
        text = {
            Column {
                Text(
                    "Emergency contacts have been notified with your live location.",
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GreenAccent.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "💡 Tip: Keep your phone's GPS on for accurate location sharing",
                        color = GreenAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = GreenAccent)
            }
        },
        containerColor = CardDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
fun AddEditContactDialog(
    oldContact: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit
) {
    var name by remember { mutableStateOf(oldContact?.name ?: "") }
    var phone by remember { mutableStateOf(oldContact?.phone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (oldContact == null) "Add Emergency Contact" else "Edit Contact",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    placeholder = { Text("e.g., Father, Mother, Friend") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenAccent,
                        unfocusedBorderColor = Color(0xFF252525),
                        cursorColor = GreenAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g., 017XXXXXXXX or 999") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenAccent,
                        unfocusedBorderColor = Color(0xFF252525),
                        cursorColor = GreenAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onSave(EmergencyContact(name, phone, isEmergencyNumber = false))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = Color.Black
                ),
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardDark
    )
}

@Composable
fun CustomToast(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.contains("✓") || message.contains("📞")) GreenAccent else EmergencyRed
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when {
                        message.contains("✓") -> Icons.Default.CheckCircle
                        message.contains("📞") -> Icons.Default.Call
                        message.contains("⚠️") -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    message,
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

data class EmergencyContact(
    val name: String,
    val phone: String,
    val iconType: String = "default",
    val isEmergencyNumber: Boolean = false
)