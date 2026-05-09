package com.example.smarthomeai

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Color Palette
val DarkBg        = Color(0xFF0F0F0F)
val CardDark      = Color(0xFF1A1A1A)
val CardDarker    = Color(0xFF181818)
val Card2         = Color(0xFF222222)
val GreenAccent   = Color(0xFFA8FF3E)
val GreenDark     = Color(0xFF7ACC1A)
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF888888)
val ChipBg        = Color(0xFF2A2A2A)
val EmergencyRed  = Color(0xFFFF4444)
val IslamicTeal   = Color(0xFF14B8A6)
val YellowAccent  = Color(0xFFFFB800)
val BlueAccent    = Color(0xFF3B9BFF)
val PurpleAccent  = Color(0xFFA855F7)
val SectionLine   = Color(0xFF2A2A2A)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContent { HomeScreen() }
    }
}

// Convert Base64 to Bitmap
fun String.toBitmapFromBase64(): Bitmap? {
    return try {
        val bytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getDecoder().decode(this)
        } else {
            android.util.Base64.decode(this, android.util.Base64.DEFAULT)
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val auth = Firebase.auth
    val currentUser = auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val databaseRef = FirebaseDatabase.getInstance().getReference("users")

    val userName = remember(currentUser) {
        currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.split("@")?.first()
                ?.replace(".", " ")
                ?.split(" ")
                ?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            ?: "Smart User"
    }

    // Profile image state
    var profileImageBase64 by remember { mutableStateOf<String?>(null) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Device states
    var lampOn by remember { mutableStateOf(false) }
    var fanOn by remember { mutableStateOf(false) }
    var acOn by remember { mutableStateOf(false) }
    var cctvOn by remember { mutableStateOf(false) }

    // Temperature state
    var currentTemperature by remember { mutableStateOf(28.5f) }
    var isLoadingTemp by remember { mutableStateOf(false) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Real-time listener for profile image
    DisposableEffect(Unit) {
        val userId = currentUser?.uid ?: return@DisposableEffect onDispose {}

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val base64 = snapshot.child("profileImageBase64").getValue(String::class.java)
                profileImageBase64 = base64
                if (!base64.isNullOrEmpty()) {
                    profileBitmap = base64.toBitmapFromBase64()
                } else {
                    profileBitmap = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error silently
            }
        }

        databaseRef.child(userId).addValueEventListener(listener)

        onDispose {
            databaseRef.child(userId).removeEventListener(listener)
        }
    }

    fun fetchRealTemperature() {
        isLoadingTemp = true
        coroutineScope.launch {
            delay(1000)
            val baseTemp = if (acOn) 22.0f + Random.nextFloat() * 3 else 28.0f + Random.nextFloat() * 5
            currentTemperature = baseTemp
            isLoadingTemp = false
        }
    }

    LaunchedEffect(acOn) {
        fetchRealTemperature()
    }

    LaunchedEffect(Unit) {
        while(true) {
            delay(30000)
            fetchRealTemperature()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout", color = TextPrimary) },
            text = { Text("Are you sure you want to logout?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    auth.signOut()
                    showLogoutDialog = false
                    context.startActivity(
                        Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    (context as? ComponentActivity)?.finish()
                }) { Text("Yes", color = GreenAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No", color = TextSecondary)
                }
            },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
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
                .padding(bottom = 16.dp)
        ) {
            // Top Bar with Profile Image
            TopBarWithProfileImage(
                userName = userName,
                profileBitmap = profileBitmap,
                onProfileClick = {
                    context.startActivity(Intent(context, ProfileActivity::class.java))
                },
                onLogoutClick = { showLogoutDialog = true },
                onNotifClick = {
                    context.startActivity(Intent(context, NotificationActivity::class.java))
                }
            )

            GreetingBanner(
                activeCount = listOf(lampOn, fanOn, acOn, cctvOn).count { it },
                temperature = currentTemperature,
                isLoading = isLoadingTemp,
                isAcOn = acOn
            )

            SectionDivider()

            SectionHeader(title = "⚡ Quick Control", actionLabel = "Edit")
            QuickControlSection(
                lampOn = lampOn, onLampToggle = { lampOn = it },
                fanOn = fanOn, onFanToggle = { fanOn = it },
                acOn = acOn, onAcToggle = {
                    acOn = it
                    fetchRealTemperature()
                }
            )

            SectionDivider()

            SectionHeader(title = "📡 Device Status", actionLabel = "All (4)")
            DeviceStatusSection(
                lampOn = lampOn, onLampToggle = { lampOn = it },
                fanOn = fanOn, onFanToggle = { fanOn = it },
                acOn = acOn, onAcToggle = {
                    acOn = it
                    fetchRealTemperature()
                },
                cctvOn = cctvOn, onCctvToggle = { cctvOn = it }
            )

            SectionDivider()

            SectionHeader(title = "🧩 Features")
            FeaturesSection(context = context)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ==================== Top Bar with Profile Image (Updated) ====================

@Composable
fun TopBarWithProfileImage(
    userName: String,
    profileBitmap: Bitmap?,
    onProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onNotifClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Profile Avatar with Image - Left side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Profile Image Circle
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    // Show actual profile image
                    androidx.compose.foundation.Image(
                        bitmap = profileBitmap.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Show default person icon
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // User Name
            Column {
                Text(
                    "Welcome back",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    userName,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons - Right side
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconCircleButton(
                icon = Icons.Default.Logout,
                onClick = onLogoutClick,
                backgroundColor = CardDark
            )
            IconCircleButton(
                icon = Icons.Default.Notifications,
                onClick = onNotifClick,
                backgroundColor = CardDark
            )
        }
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color = CardDark
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

// ==================== Greeting Banner ====================

@Composable
fun GreetingBanner(activeCount: Int, temperature: Float, isLoading: Boolean, isAcOn: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GreenAccent.copy(alpha = 0.07f), Color.Transparent),
                        radius = 300f
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                    in 0..11 -> "Good Morning ☀️"
                    in 12..16 -> "Good Afternoon 🌤️"
                    else -> "Good Evening 🌙"
                }
                Text(greeting, color = TextSecondary, fontSize = 12.sp)
                Text(
                    "Your Home,",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                )
                Text(
                    "Smart & Safe",
                    color = GreenAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GreenAccent.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = GreenAccent.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(GreenAccent)
                        )
                        Text(
                            "$activeCount Active",
                            color = GreenAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = GreenAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (isAcOn) Icons.Default.AcUnit else Icons.Default.WbSunny,
                            contentDescription = "Temperature",
                            tint = if (isAcOn) BlueAccent else YellowAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isLoading) "--°C" else String.format("%.1f°C", temperature),
                        color = if (isAcOn) BlueAccent else YellowAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isAcOn) "Cooling: ${String.format("%.1f", temperature)}°C" else "Indoor Temp",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ==================== Section Helpers ====================

@Composable
fun SectionHeader(title: String, actionLabel: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (actionLabel != null) {
            Text(actionLabel, color = GreenAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
fun SectionDivider() {
    Spacer(modifier = Modifier.height(18.dp))
    Box(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, SectionLine, Color.Transparent)
                )
            )
    )
    Spacer(modifier = Modifier.height(18.dp))
}

// ==================== Quick Control ====================

@Composable
fun QuickControlSection(
    lampOn: Boolean, onLampToggle: (Boolean) -> Unit,
    fanOn: Boolean, onFanToggle: (Boolean) -> Unit,
    acOn: Boolean, onAcToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickControlCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Lightbulb,
            label = "Light",
            isOn = lampOn,
            onToggle = onLampToggle
        )
        QuickControlCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Air,
            label = "Fan",
            isOn = fanOn,
            onToggle = onFanToggle
        )
        QuickControlCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AcUnit,
            label = "AC",
            isOn = acOn,
            onToggle = onAcToggle
        )
    }
}

@Composable
fun QuickControlCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val bgColor = if (isOn) GreenAccent.copy(alpha = 0.1f) else CardDark
    val borderColor = if (isOn) GreenAccent.copy(alpha = 0.35f) else Color(0xFF252525)
    val iconTint = if (isOn) GreenAccent else TextSecondary
    val iconBg = if (isOn) GreenAccent.copy(alpha = 0.18f) else ChipBg

    Card(
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        if (isOn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(GreenAccent)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onToggle(!isOn) }
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(label, color = iconTint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .height(20.dp)
                    .width(38.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = GreenAccent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ChipBg,
                    uncheckedBorderColor = ChipBg
                )
            )
        }
    }
}

// ==================== Device Status ====================

@Composable
fun DeviceStatusSection(
    lampOn: Boolean, onLampToggle: (Boolean) -> Unit,
    fanOn: Boolean, onFanToggle: (Boolean) -> Unit,
    acOn: Boolean, onAcToggle: (Boolean) -> Unit,
    cctvOn: Boolean, onCctvToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceStatusCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Lightbulb,
                title = "Smart Light",
                isOn = lampOn,
                onToggle = onLampToggle
            )
            DeviceStatusCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Air,
                title = "Smart Fan",
                isOn = fanOn,
                onToggle = onFanToggle
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceStatusCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AcUnit,
                title = "Air Cond.",
                isOn = acOn,
                onToggle = onAcToggle
            )
            DeviceStatusCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Videocam,
                title = "CCTV",
                isOn = cctvOn,
                onToggle = onCctvToggle
            )
        }
    }
}

@Composable
fun DeviceStatusCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val iconBg = if (isOn) GreenAccent.copy(alpha = 0.15f) else Color(0xFF252525)
    val iconTint = if (isOn) GreenAccent else Color(0xFF555555)

    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (isOn) "● ON" else "○ OFF",
                        color = if (isOn) GreenAccent else Color(0xFF555555),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                modifier = Modifier.height(18.dp).width(34.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = GreenAccent,
                    uncheckedThumbColor = Color(0xFF666666),
                    uncheckedTrackColor = ChipBg,
                    uncheckedBorderColor = ChipBg
                )
            )
        }
    }
}

// ==================== Features Section ====================

@Composable
fun FeaturesSection(context: android.content.Context) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Devices,
                title = "Device Control",
                subtitle = "Manage all",
                accentColor = GreenAccent,
                onClick = {
                    context.startActivity(Intent(context, DeviceControlActivity::class.java))
                }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Tune,
                title = "Automation",
                subtitle = "Smart rules",
                accentColor = BlueAccent,
                onClick = {
                    context.startActivity(Intent(context, AutomationActivity::class.java))
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ShowChart,
                title = "Energy",
                subtitle = "Monitor usage",
                accentColor = YellowAccent,
                onClick = {
                    context.startActivity(Intent(context, EnergyActivity::class.java))
                }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                title = "Emergency",
                subtitle = "SOS & alerts",
                accentColor = EmergencyRed,
                onClick = {
                    context.startActivity(Intent(context, EmergencyActivity::class.java))
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mosque,
                title = "Islamic",
                subtitle = "Prayer & Quran",
                accentColor = IslamicTeal,
                onClick = {
                    context.startActivity(Intent(context, IslamicFeatureActivity::class.java))
                }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "All alerts",
                accentColor = PurpleAccent,
                onClick = {
                    context.startActivity(Intent(context, NotificationActivity::class.java))
                }
            )
        }
        FeatureCard(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Person,
            title = "Profile Settings",
            subtitle = "Account & preferences",
            accentColor = Color(0xFF888888),
            onClick = {
                context.startActivity(Intent(context, ProfileActivity::class.java))
            }
        )
    }
}

@Composable
fun FeatureCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(76.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = TextSecondary, fontSize = 10.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}