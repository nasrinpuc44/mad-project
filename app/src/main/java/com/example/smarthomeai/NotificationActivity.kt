package com.example.smarthomeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// Notification Types
sealed class NotificationType(val color: Color, val icon: ImageVector, val label: String) {
    object DeviceAlert : NotificationType(
        color = Color(0xFFFFB800),
        icon = Icons.Default.Devices,
        label = "Device Alert"
    )
    object Emergency : NotificationType(
        color = Color(0xFFFF4444),
        icon = Icons.Default.Warning,
        label = "Emergency"
    )
    object Energy : NotificationType(
        color = Color(0xFFA8FF3E),
        icon = Icons.Default.ShowChart,
        label = "Energy Alert"
    )
    object Prayer : NotificationType(
        color = Color(0xFF14B8A6),
        icon = Icons.Default.Mosque,
        label = "Prayer Reminder"
    )
}

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val actionData: String? = null
)

class NotificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotificationScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun NotificationScreen(onBackClick: () -> Unit) {
    val dbRef = remember {
        FirebaseDatabase.getInstance().getReference("devices/status")
    }

    val coroutineScope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showSuccessToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Device states for monitoring
    var lightOn by remember { mutableStateOf(false) }
    var fanOn by remember { mutableStateOf(false) }
    var acOn by remember { mutableStateOf(false) }
    var fanSpeed by remember { mutableStateOf("Low") }
    var lightBrightness by remember { mutableStateOf(50) }
    var temperature by remember { mutableStateOf(24) }

    // Alert states from EmergencyActivity
    var fireAlert by remember { mutableStateOf(false) }
    var gasAlert by remember { mutableStateOf(false) }

    // Track last notification time to avoid duplicates
    var lastFanAlertTime by remember { mutableStateOf(0L) }
    var lastEnergyAlertTime by remember { mutableStateOf(0L) }
    var lastTempAlertTime by remember { mutableStateOf(0L) }

    fun showFeedback(message: String) {
        toastMessage = message
        showSuccessToast = true
        coroutineScope.launch {
            delay(2000)
            showSuccessToast = false
        }
    }

    fun addNotification(notification: NotificationItem) {
        // Check for duplicate within last 5 minutes (except emergency)
        val now = System.currentTimeMillis()
        val exists = notifications.any { it.title == notification.title && (now - it.timestamp) < 300000 }
        if (!exists || notification.type == NotificationType.Emergency) {
            notifications = listOf(notification) + notifications
        }
    }

    // Fan running too long monitoring
    var fanStartTime by remember { mutableStateOf<Long?>(null) }
    var fanMonitoringJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Energy usage tracking
    var energyAlertSent by remember { mutableStateOf(false) }

    // Prayer times monitoring
    var lastPrayerAlertTime by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Monitor device status changes
    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newLightOn = snapshot.child("lightOn").getValue(Boolean::class.java) ?: false
                val newFanOn = snapshot.child("fanOn").getValue(Boolean::class.java) ?: false
                val newAcOn = snapshot.child("acOn").getValue(Boolean::class.java) ?: false
                val newFanSpeed = snapshot.child("fanSpeed").getValue(String::class.java) ?: "Low"
                val newLightBrightness = snapshot.child("lightBrightness").getValue(Int::class.java) ?: 50
                val newTemperature = snapshot.child("temperature").getValue(Int::class.java) ?: 24

                // Device Alert: Fan running too long
                if (newFanOn && !fanOn) {
                    fanStartTime = System.currentTimeMillis()
                    fanMonitoringJob?.cancel()
                    fanMonitoringJob = coroutineScope.launch {
                        delay(3600000) // 1 hour
                        if (fanOn && System.currentTimeMillis() - (fanStartTime ?: 0) >= 3600000) {
                            val now = System.currentTimeMillis()
                            if (now - lastFanAlertTime > 3600000) {
                                lastFanAlertTime = now
                                addNotification(
                                    NotificationItem(
                                        id = UUID.randomUUID().toString(),
                                        type = NotificationType.DeviceAlert,
                                        title = "Fan Running Too Long",
                                        message = "Smart Fan has been running for over 1 hour. Consider turning it off to save energy.",
                                        timestamp = now,
                                        actionData = "fan"
                                    )
                                )
                            }
                        }
                    }
                } else if (!newFanOn && fanOn) {
                    fanMonitoringJob?.cancel()
                    fanStartTime = null
                }

                // Device Alert: Light brightness too high at night
                val calendar = Calendar.getInstance()
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val isNightTime = currentHour >= 22 || currentHour <= 5
                if (newLightOn && newLightBrightness > 80 && isNightTime) {
                    val now = System.currentTimeMillis()
                    addNotification(
                        NotificationItem(
                            id = UUID.randomUUID().toString(),
                            type = NotificationType.DeviceAlert,
                            title = "High Brightness at Night",
                            message = "Light brightness is set to ${newLightBrightness}% at night. Dimming can help you sleep better.",
                            timestamp = now,
                            actionData = "light"
                        )
                    )
                }

                // Energy Alert: High energy usage
                val lightUnit = if (newLightOn) 0.06 else 0.02
                val fanUnit = if (newFanOn) 0.075 else 0.03
                val acUnit = if (newAcOn) 1.2 else 0.2
                val totalUsage = lightUnit + fanUnit + acUnit

                if (totalUsage > 1.0 && !energyAlertSent) {
                    val now = System.currentTimeMillis()
                    if (now - lastEnergyAlertTime > 43200000) { // Every 12 hours
                        lastEnergyAlertTime = now
                        energyAlertSent = true
                        addNotification(
                            NotificationItem(
                                id = UUID.randomUUID().toString(),
                                type = NotificationType.Energy,
                                title = "High Energy Usage Detected",
                                message = String.format("Current consumption: %.2f kWh/hour. Consider turning off unused devices.", totalUsage),
                                timestamp = now
                            )
                        )
                    }
                } else if (totalUsage <= 0.5) {
                    energyAlertSent = false
                }

                // Device Alert: AC temperature too low
                if (newAcOn && newTemperature < 18) {
                    val now = System.currentTimeMillis()
                    if (now - lastTempAlertTime > 1800000) { // Every 30 minutes
                        lastTempAlertTime = now
                        addNotification(
                            NotificationItem(
                                id = UUID.randomUUID().toString(),
                                type = NotificationType.DeviceAlert,
                                title = "AC Temperature Too Low",
                                message = "Temperature set to ${newTemperature}°C. Recommended: 24-26°C for energy efficiency.",
                                timestamp = now,
                                actionData = "ac"
                            )
                        )
                    }
                }

                // Update states
                lightOn = newLightOn
                fanOn = newFanOn
                acOn = newAcOn
                fanSpeed = newFanSpeed
                lightBrightness = newLightBrightness
                temperature = newTemperature
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        dbRef.addValueEventListener(listener)

        onDispose {
            dbRef.removeEventListener(listener)
            fanMonitoringJob?.cancel()
        }
    }

    // Monitor emergency alerts from database
    DisposableEffect(Unit) {
        val emergencyRef = FirebaseDatabase.getInstance().getReference("emergency/alerts")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newFireAlert = snapshot.child("fireAlert").getValue(Boolean::class.java) ?: false
                val newGasAlert = snapshot.child("gasAlert").getValue(Boolean::class.java) ?: false

                if (newFireAlert && !fireAlert) {
                    addNotification(
                        NotificationItem(
                            id = UUID.randomUUID().toString(),
                            type = NotificationType.Emergency,
                            title = "🔥 FIRE ALERT!",
                            message = "Smoke/Fire detected in your home! Take immediate action!",
                            timestamp = System.currentTimeMillis(),
                            actionData = "fire"
                        )
                    )
                }

                if (newGasAlert && !gasAlert) {
                    addNotification(
                        NotificationItem(
                            id = UUID.randomUUID().toString(),
                            type = NotificationType.Emergency,
                            title = "⚠️ GAS LEAK ALERT!",
                            message = "Gas leak detected! Open windows and leave the area immediately!",
                            timestamp = System.currentTimeMillis(),
                            actionData = "gas"
                        )
                    )
                }

                fireAlert = newFireAlert
                gasAlert = newGasAlert
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        emergencyRef.addValueEventListener(listener)

        onDispose {
            emergencyRef.removeEventListener(listener)
        }
    }

    // Prayer times monitoring
    val dhakaTimes = dhakaPrayerTimes
    val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val prayerTimesMap = mapOf(
        "Fajr" to dhakaTimes.fajr,
        "Dhuhr" to dhakaTimes.dhuhr,
        "Asr" to dhakaTimes.asr,
        "Maghrib" to dhakaTimes.maghrib,
        "Isha" to dhakaTimes.isha
    )

    // Check prayer times every minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // Check every minute
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            prayerNames.forEach { prayer ->
                val timeStr = prayerTimesMap[prayer] ?: return@forEach
                val prayerMinutes = convertTimeToMinutes(timeStr)

                // Alert 5 minutes before prayer
                val minutesBefore = 5
                val alertTime = prayerMinutes - minutesBefore

                if (currentMinutes == alertTime) {
                    val lastAlert = lastPrayerAlertTime[prayer] ?: 0L
                    val nowMillis = System.currentTimeMillis()
                    if (nowMillis - lastAlert > 3600000) { // Don't alert again for same prayer within 1 hour
                        lastPrayerAlertTime = lastPrayerAlertTime + (prayer to nowMillis)
                        addNotification(
                            NotificationItem(
                                id = UUID.randomUUID().toString(),
                                type = NotificationType.Prayer,
                                title = "🕌 Prayer Time Reminder",
                                message = "$prayer prayer will start in $minutesBefore minutes. Prepare for Salah.",
                                timestamp = nowMillis,
                                actionData = prayer.lowercase()
                            )
                        )
                    }
                }

                // Alert exactly at prayer time
                if (currentMinutes == prayerMinutes) {
                    val lastAlert = lastPrayerAlertTime["${prayer}_exact"] ?: 0L
                    val nowMillis = System.currentTimeMillis()
                    if (nowMillis - lastAlert > 3600000) {
                        lastPrayerAlertTime = lastPrayerAlertTime + ("${prayer}_exact" to nowMillis)
                        addNotification(
                            NotificationItem(
                                id = UUID.randomUUID().toString(),
                                type = NotificationType.Prayer,
                                title = "🕌 Time for $prayer Prayer",
                                message = "It's time for $prayer prayer. May Allah accept your worship.",
                                timestamp = nowMillis,
                                actionData = prayer.lowercase()
                            )
                        )
                    }
                }
            }
        }
    }

    // Filter notifications
    val filteredNotifications = if (selectedFilter == null) {
        notifications
    } else {
        notifications.filter { it.type.label == selectedFilter }
    }

    // Unread count
    val unreadCount = notifications.count { !it.isRead }

    // Generate demo notifications on first load (for testing)
    LaunchedEffect(Unit) {
        delay(2000)
        if (notifications.isEmpty()) {
            addNotification(
                NotificationItem(
                    id = "demo_1",
                    type = NotificationType.DeviceAlert,
                    title = "Fan Running Too Long",
                    message = "Smart Fan has been running for over 2 hours. Consider turning it off.",
                    timestamp = System.currentTimeMillis() - 3600000,
                    isRead = false
                )
            )
            addNotification(
                NotificationItem(
                    id = "demo_2",
                    type = NotificationType.Energy,
                    title = "High Energy Usage Detected",
                    message = "Your energy usage is 30% higher than yesterday.",
                    timestamp = System.currentTimeMillis() - 7200000,
                    isRead = false
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            AnimatedNotificationTopBar(
                onBackClick = onBackClick,
                unreadCount = unreadCount,
                onClearAll = { showDeleteAllDialog = true },
                onRefresh = {
                    isRefreshing = true
                    coroutineScope.launch {
                        delay(1000)
                        isRefreshing = false
                        showFeedback("✓ Notifications refreshed")
                    }
                },
                isRefreshing = isRefreshing
            )

            // Filter Chips
            FilterChipsSection(
                selectedFilter = selectedFilter,
                onFilterSelect = { filter ->
                    selectedFilter = if (selectedFilter == filter) null else filter
                },
                counts = mapOf(
                    "Device Alert" to notifications.count { it.type == NotificationType.DeviceAlert },
                    "Emergency" to notifications.count { it.type == NotificationType.Emergency },
                    "Energy Alert" to notifications.count { it.type == NotificationType.Energy },
                    "Prayer Reminder" to notifications.count { it.type == NotificationType.Prayer }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Notifications List
            if (filteredNotifications.isEmpty()) {
                EmptyNotificationsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredNotifications) { notification ->
                        AnimatedNotificationCard(
                            notification = notification,
                            onMarkAsRead = {
                                notifications = notifications.map { item ->
                                    if (item.id == notification.id) item.copy(isRead = true)
                                    else item
                                }
                                showFeedback("✓ Marked as read")
                            },
                            onDelete = {
                                notifications = notifications.filter { it.id != notification.id }
                                showFeedback("✓ Notification deleted")
                            }
                        )
                    }
                }
            }
        }

        // Delete All Dialog
        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("Clear All Notifications", color = TextPrimary) },
                text = { Text("Are you sure you want to delete all notifications?", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            notifications = emptyList()
                            showDeleteAllDialog = false
                            showFeedback("✓ All notifications cleared")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Text("Delete All", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CardDark
            )
        }

        // Success Toast
        if (showSuccessToast) {
            NotificationToast(message = toastMessage)
        }
    }
}

fun convertTimeToMinutes(timeStr: String): Int {
    return try {
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(timeStr) ?: return 0
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    } catch (e: Exception) {
        0
    }
}

@Composable
fun AnimatedNotificationTopBar(
    onBackClick: () -> Unit,
    unreadCount: Int,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
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
                tint = GreenAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notifications",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = if (unreadCount > 0) "$unreadCount unread notifications" else "All caught up!",
                color = if (unreadCount > 0) GreenAccent else TextSecondary,
                fontSize = 12.sp
            )
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardDark)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = GreenAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = GreenAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (unreadCount > 0) {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmergencyRed.copy(alpha = 0.1f))
            ) {
                Text("Clear All", color = EmergencyRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FilterChipsSection(
    selectedFilter: String?,
    onFilterSelect: (String) -> Unit,
    counts: Map<String, Int>
) {
    val filters = listOf("Device Alert", "Emergency", "Energy Alert", "Prayer Reminder")
    val colors = listOf(YellowAccent, EmergencyRed, GreenAccent, IslamicTeal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All filter chip
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelect("") },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All", color = if (selectedFilter == null) Color.Black else TextPrimary)
                    if (counts.values.sum() > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "(${counts.values.sum()})",
                            color = if (selectedFilter == null) Color.Black.copy(alpha = 0.7f) else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = GreenAccent,
                selectedLabelColor = Color.Black,
                containerColor = ChipBg,
                labelColor = TextPrimary
            )
        )

        filters.forEachIndexed { index, filter ->
            val count = counts[filter] ?: 0
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelect(filter) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(filter, color = if (selectedFilter == filter) Color.Black else TextPrimary)
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "($count)",
                                color = if (selectedFilter == filter) Color.Black.copy(alpha = 0.7f) else TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors[index],
                    selectedLabelColor = Color.Black,
                    containerColor = ChipBg,
                    labelColor = TextPrimary
                )
            )
        }
    }
}

@Composable
fun AnimatedNotificationCard(
    notification: NotificationItem,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (expanded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .scale(animatedScale)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) CardDark else notification.type.color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!notification.isRead) notification.type.color.copy(alpha = 0.3f) else Color(0xFF252525)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(notification.type.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            notification.type.icon,
                            contentDescription = null,
                            tint = notification.type.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Content
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                notification.title,
                                color = if (!notification.isRead) notification.type.color else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium
                            )
                            if (!notification.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(notification.type.color)
                                )
                            }
                        }

                        Text(
                            notification.message,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatTimestamp(notification.timestamp),
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )

                            if (notification.type == NotificationType.DeviceAlert && notification.actionData != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = notification.type.color.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "Check Device",
                                        color = notification.type.color,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!notification.isRead) {
                        IconButton(
                            onClick = onMarkAsRead,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Done,
                                contentDescription = "Mark as read",
                                tint = GreenAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF252525))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onMarkAsRead,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = notification.type.color,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Mark as Read", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChipBg,
                                contentColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNotificationsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                "No Notifications",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "You're all caught up! New alerts will appear here.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun NotificationToast(message: String) {
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
                containerColor = if (message.contains("✓")) GreenAccent else EmergencyRed
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (message.contains("✓")) Icons.Default.CheckCircle else Icons.Default.Info,
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

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000} min ago"
        diff < 86400000 -> "${diff / 3600000} hour${if (diff / 3600000 > 1) "s" else ""} ago"
        else -> {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}