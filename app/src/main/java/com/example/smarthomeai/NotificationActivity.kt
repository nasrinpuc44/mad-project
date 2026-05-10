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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

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
    object Mode : NotificationType(
        color = Color(0xFFA855F7),
        icon = Icons.Default.AutoAwesome,
        label = "Mode"
    )
    object Islamic : NotificationType(
        color = Color(0xFF14B8A6),
        icon = Icons.Default.Mosque,
        label = "Islamic Feature"
    )
}

data class NotificationItem(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,
    val actionData: String? = null,
    val modeName: String? = null
)

@Composable
fun NotificationScreen(onBackClick: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = currentUser?.uid ?: ""
    val coroutineScope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showSuccessToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    fun showFeedback(message: String) {
        toastMessage = message
        showSuccessToast = true
        coroutineScope.launch {
            delay(2000)
            showSuccessToast = false
        }
    }

    // Mark multiple notifications as read
    fun markMultipleAsRead(notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return

        coroutineScope.launch {
            try {
                val batch = firestore.batch()
                val collectionRef = firestore.collection("users")
                    .document(userId)
                    .collection("notifications")

                for (id in notificationIds) {
                    batch.update(collectionRef.document(id), "isRead", true)
                }
                batch.commit().await()

                // Update local state
                notifications = notifications.map {
                    if (notificationIds.contains(it.id)) it.copy(isRead = true)
                    else it
                }
            } catch (e: Exception) {
                // Silently fail - don't show error to user
            }
        }
    }

    fun loadNotifications() {
        isLoading = true
        coroutineScope.launch {
            try {
                val notificationsList = mutableListOf<NotificationItem>()
                val unreadIds = mutableListOf<String>()

                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val isRead = data["isRead"] as? Boolean ?: false

                    notificationsList.add(
                        NotificationItem(
                            id = doc.id,
                            type = data["type"] as? String ?: "",
                            title = data["title"] as? String ?: "",
                            message = data["message"] as? String ?: "",
                            timestamp = (data["timestamp"] as? Long) ?: 0,
                            isRead = isRead,
                            actionData = data["actionData"] as? String,
                            modeName = data["modeName"] as? String
                        )
                    )

                    // Collect unread notification IDs to mark as read
                    if (!isRead) {
                        unreadIds.add(doc.id)
                    }
                }

                notifications = notificationsList
                isLoading = false

                // Mark all notifications as read after loading (user has seen them)
                if (unreadIds.isNotEmpty()) {
                    markMultipleAsRead(unreadIds)
                }

            } catch (e: Exception) {
                isLoading = false
                showFeedback("Error loading notifications: ${e.message}")
            }
        }
    }

    fun markAsRead(notificationId: String) {
        coroutineScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .document(notificationId)
                    .update("isRead", true)
                    .await()

                notifications = notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true)
                    else it
                }
                showFeedback("✓ Marked as read")
            } catch (e: Exception) {
                showFeedback("✗ Failed to mark as read")
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        coroutineScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .document(notificationId)
                    .delete()
                    .await()

                notifications = notifications.filter { it.id != notificationId }
                showFeedback("✓ Notification deleted")
            } catch (e: Exception) {
                showFeedback("✗ Failed to delete notification")
            }
        }
    }

    fun deleteAllNotifications() {
        coroutineScope.launch {
            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("notifications")
                    .get()
                    .await()

                val batch = firestore.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()

                notifications = emptyList()
                showDeleteAllDialog = false
                showFeedback("✓ All notifications cleared")
            } catch (e: Exception) {
                showFeedback("✗ Failed to clear notifications")
            }
        }
    }

    fun getNotificationType(type: String): Pair<Color, ImageVector> {
        return when (type) {
            "device_alert" -> Pair(Color(0xFFFFB800), Icons.Default.Devices)
            "emergency" -> Pair(Color(0xFFFF4444), Icons.Default.Warning)
            "energy_alert" -> Pair(Color(0xFFA8FF3E), Icons.Default.ShowChart)
            "prayer_reminder" -> Pair(Color(0xFF14B8A6), Icons.Default.Mosque)
            "mode" -> Pair(Color(0xFFA855F7), Icons.Default.AutoAwesome)
            "islamic_feature" -> Pair(Color(0xFF14B8A6), Icons.Default.Mosque)
            else -> Pair(Color(0xFFA8FF3E), Icons.Default.Info)
        }
    }

    val filteredNotifications = if (selectedFilter == null) {
        notifications
    } else {
        notifications.filter {
            when (selectedFilter) {
                "Device Alert" -> it.type == "device_alert"
                "Emergency" -> it.type == "emergency"
                "Energy Alert" -> it.type == "energy_alert"
                "Prayer Reminder" -> it.type == "prayer_reminder"
                "Mode" -> it.type == "mode"
                "Islamic Feature" -> it.type == "islamic_feature"
                else -> true
            }
        }
    }

    val unreadCount = notifications.count { !it.isRead }

    LaunchedEffect(Unit) {
        loadNotifications()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedNotificationTopBar(
                onBackClick = onBackClick,
                unreadCount = unreadCount,
                onClearAll = { showDeleteAllDialog = true },
                onRefresh = {
                    isRefreshing = true
                    loadNotifications()
                    coroutineScope.launch {
                        delay(1000)
                        isRefreshing = false
                        showFeedback("✓ Notifications refreshed")
                    }
                },
                isRefreshing = isRefreshing,
                hasNotifications = notifications.isNotEmpty()
            )

            FilterChipsSection(
                selectedFilter = selectedFilter,
                onFilterSelect = { filter ->
                    selectedFilter = if (selectedFilter == filter) null else filter
                },
                counts = mapOf(
                    "Device Alert" to notifications.count { it.type == "device_alert" },
                    "Emergency" to notifications.count { it.type == "emergency" },
                    "Energy Alert" to notifications.count { it.type == "energy_alert" },
                    "Prayer Reminder" to notifications.count { it.type == "prayer_reminder" },
                    "Mode" to notifications.count { it.type == "mode" },
                    "Islamic Feature" to notifications.count { it.type == "islamic_feature" }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenAccent)
                    }
                }
                filteredNotifications.isEmpty() -> {
                    EmptyNotificationsState()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredNotifications) { notification ->
                            val (iconColor, icon) = getNotificationType(notification.type)
                            AnimatedNotificationCard(
                                notification = notification,
                                icon = icon,
                                iconColor = iconColor,
                                onMarkAsRead = { markAsRead(notification.id) },
                                onDelete = { deleteNotification(notification.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("Clear All Notifications", color = TextPrimary) },
                text = { Text("Are you sure you want to delete all notifications?", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = { deleteAllNotifications() },
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

        if (showSuccessToast) {
            NotificationToast(message = toastMessage)
        }
    }
}

@Composable
fun AnimatedNotificationTopBar(
    onBackClick: () -> Unit,
    unreadCount: Int,
    onClearAll: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    hasNotifications: Boolean
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

        if (hasNotifications) {
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
    val filters = listOf("Device Alert", "Emergency", "Energy Alert", "Prayer Reminder", "Mode", "Islamic Feature")
    val colors = listOf(YellowAccent, EmergencyRed, GreenAccent, IslamicTeal, PurpleAccent, IslamicTeal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    icon: ImageVector,
    iconColor: Color,
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
            containerColor = if (notification.isRead) CardDark else iconColor.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!notification.isRead) iconColor.copy(alpha = 0.3f) else Color(0xFF252525)
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                notification.title,
                                color = if (!notification.isRead) iconColor else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium
                            )
                            if (!notification.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(iconColor)
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

                            if (notification.type == "mode" && notification.modeName != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = iconColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        notification.modeName!!,
                                        color = iconColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

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
                                containerColor = iconColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !notification.isRead
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