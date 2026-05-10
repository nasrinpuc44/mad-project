package com.example.smarthomeai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class AdminPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email != "smarthome@gmail.com") {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            AdminPanelScreen(
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            )
        }
    }
}

data class AdminUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val createdAt: Long = 0,
    var deviceStatus: DeviceStatus? = null,
    var profileImageBase64: String? = null  // যোগ করা হয়েছে
)

// Base64 to Bitmap conversion function
fun String.decodeBase64ToBitmap(): Bitmap? {
    return try {
        val bytes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getDecoder().decode(this)
        } else {
            android.util.Base64.decode(this, android.util.Base64.DEFAULT)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

data class DeviceStatus(
    val lightOn: Boolean = false,
    val lightBrightness: Int = 0,
    val fanOn: Boolean = false,
    val fanSpeed: String = "Low",
    val acOn: Boolean = false,
    val temperature: Int = 24
)

data class AdminActivityLog(
    val action: String = "",
    val details: String = "",
    val timestamp: Long = 0,
    val logId: String = "",
    val userName: String = "",
    val userId: String = ""
)

@Composable
fun AdminPanelScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val rtdb = FirebaseDatabase.getInstance().getReference()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }
    var userActivities by remember { mutableStateOf<List<AdminActivityLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<AdminUser?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var isClearingAll by remember { mutableStateOf(false) }
    var isDeletingUser by remember { mutableStateOf(false) }

    val tabs = listOf("👥 Users", "📋 All Activities")

    fun showFeedback(message: String, isError: Boolean = false) {
        toastMessage = message
        showToast = true
        coroutineScope.launch {
            delay(2000)
            showToast = false
        }
    }

    fun loadUsers() {
        isLoading = true
        coroutineScope.launch {
            try {
                val usersList = mutableListOf<AdminUser>()

                val snapshot = firestore.collection("users").get().await()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val uid = doc.id

                    var deviceStatus: DeviceStatus? = null
                    var profileImageBase64: String? = null

                    try {
                        // Get device status from Realtime Database
                        val statusSnapshot = rtdb.child("users").child(uid).child("devices").child("status").get().await()
                        deviceStatus = DeviceStatus(
                            lightOn = statusSnapshot.child("lightOn").getValue(Boolean::class.java) ?: false,
                            lightBrightness = statusSnapshot.child("lightBrightness").getValue(Int::class.java) ?: 0,
                            fanOn = statusSnapshot.child("fanOn").getValue(Boolean::class.java) ?: false,
                            fanSpeed = statusSnapshot.child("fanSpeed").getValue(String::class.java) ?: "Low",
                            acOn = statusSnapshot.child("acOn").getValue(Boolean::class.java) ?: false,
                            temperature = statusSnapshot.child("temperature").getValue(Int::class.java) ?: 24
                        )

                        // Get profile image from Realtime Database
                        val userSnapshot = rtdb.child("users").child(uid).get().await()
                        profileImageBase64 = userSnapshot.child("profileImageBase64").getValue(String::class.java)

                    } catch (e: Exception) { }

                    usersList.add(
                        AdminUser(
                            uid = uid,
                            email = data["email"] as? String ?: "",
                            displayName = data["displayName"] as? String ?: "",
                            createdAt = (data["createdAt"] as? Long) ?: 0,
                            deviceStatus = deviceStatus,
                            profileImageBase64 = profileImageBase64
                        )
                    )
                }

                users = usersList.sortedByDescending { it.createdAt }
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                showFeedback("Error loading users: ${e.message}", true)
            }
        }
    }

    fun loadUserActivities(user: AdminUser) {
        isLoading = true
        selectedUser = user
        coroutineScope.launch {
            try {
                val activities = mutableListOf<AdminActivityLog>()

                val snapshot = firestore.collection("users")
                    .document(user.uid)
                    .collection("activity_log")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(500)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    activities.add(
                        AdminActivityLog(
                            action = data["action"] as? String ?: "",
                            details = data["details"] as? String ?: "",
                            timestamp = (data["timestamp"] as? Long) ?: 0,
                            logId = doc.id,
                            userName = user.displayName,
                            userId = user.uid
                        )
                    )
                }

                userActivities = activities
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                showFeedback("Error loading activities: ${e.message}", true)
            }
        }
    }

    fun deleteUserComplete(user: AdminUser) {
        isDeletingUser = true
        coroutineScope.launch {
            var firestoreSuccess = false
            var rtdbSuccess = false
            val errors = mutableListOf<String>()

            try {
                val userDoc = firestore.collection("users").document(user.uid)
                val logsSnapshot = userDoc.collection("activity_log").get().await()
                val batch = firestore.batch()
                for (logDoc in logsSnapshot.documents) {
                    batch.delete(logDoc.reference)
                }
                batch.commit().await()
                userDoc.delete().await()
                firestoreSuccess = true
            } catch (e: Exception) {
                errors.add("Firestore: ${e.message}")
            }

            try {
                rtdb.child("users").child(user.uid).removeValue().await()
                rtdbSuccess = true
            } catch (e: Exception) {
                errors.add("RTDB: ${e.message}")
            }

            isDeletingUser = false
            showDeleteDialog = null

            if (firestoreSuccess || rtdbSuccess) {
                loadUsers()
                if (selectedUser?.uid == user.uid) {
                    selectedUser = null
                }
                val message = when {
                    firestoreSuccess && rtdbSuccess -> "✓ User ${user.displayName} deleted successfully"
                    firestoreSuccess -> "✓ User ${user.displayName} deleted from Firestore only\n⚠️ Realtime DB: ${errors.joinToString(", ")}"
                    rtdbSuccess -> "✓ User ${user.displayName} deleted from Realtime DB only\n⚠️ Firestore: ${errors.joinToString(", ")}"
                    else -> "✗ Failed to delete user: ${errors.joinToString(", ")}"
                }
                showFeedback(message, !(firestoreSuccess && rtdbSuccess))
            } else {
                showFeedback("✗ Failed to delete user: ${errors.joinToString(", ")}", true)
            }
        }
    }

    fun loadAllActivities() {
        isLoading = true
        coroutineScope.launch {
            try {
                val allActivities = mutableListOf<AdminActivityLog>()

                val usersSnapshot = firestore.collection("users").get().await()

                for (userDoc in usersSnapshot.documents) {
                    val userData = userDoc.data ?: continue
                    val userName = userData["displayName"] as? String ?: userData["email"] as? String ?: "Unknown"
                    val userId = userDoc.id

                    val logsSnapshot = firestore.collection("users")
                        .document(userId)
                        .collection("activity_log")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(200)
                        .get()
                        .await()

                    for (logDoc in logsSnapshot.documents) {
                        val logData = logDoc.data ?: continue
                        allActivities.add(
                            AdminActivityLog(
                                action = logData["action"] as? String ?: "",
                                details = logData["details"] as? String ?: "",
                                timestamp = (logData["timestamp"] as? Long) ?: 0,
                                logId = logDoc.id,
                                userName = userName,
                                userId = userId
                            )
                        )
                    }
                }

                userActivities = allActivities.sortedByDescending { it.timestamp }
                selectedUser = null
                isLoading = false

                if (allActivities.isEmpty()) {
                    showFeedback("No activities found")
                }
            } catch (e: Exception) {
                isLoading = false
                showFeedback("Error loading activities: ${e.message}", true)
            }
        }
    }

    fun clearAllActivities() {
        isClearingAll = true
        coroutineScope.launch {
            try {
                var totalDeleted = 0
                val usersSnapshot = firestore.collection("users").get().await()

                for (userDoc in usersSnapshot.documents) {
                    val userId = userDoc.id
                    val logsSnapshot = firestore.collection("users")
                        .document(userId)
                        .collection("activity_log")
                        .get()
                        .await()

                    val batch = firestore.batch()
                    for (logDoc in logsSnapshot.documents) {
                        batch.delete(logDoc.reference)
                        totalDeleted++
                    }
                    batch.commit().await()
                }

                showClearAllDialog = false
                isClearingAll = false

                if (selectedTab == 1) {
                    loadAllActivities()
                } else if (selectedUser != null) {
                    loadUserActivities(selectedUser!!)
                }

                showFeedback("✓ Cleared $totalDeleted activities from all users")
            } catch (e: Exception) {
                isClearingAll = false
                showClearAllDialog = false
                showFeedback("✗ Failed to clear activities: ${e.message}", true)
            }
        }
    }

    fun clearUserActivities(user: AdminUser) {
        isLoading = true
        coroutineScope.launch {
            try {
                var totalDeleted = 0
                val logsSnapshot = firestore.collection("users")
                    .document(user.uid)
                    .collection("activity_log")
                    .get()
                    .await()

                val batch = firestore.batch()
                for (logDoc in logsSnapshot.documents) {
                    batch.delete(logDoc.reference)
                    totalDeleted++
                }
                batch.commit().await()

                loadUserActivities(user)
                showFeedback("✓ Cleared $totalDeleted activities for ${user.displayName}")
            } catch (e: Exception) {
                isLoading = false
                showFeedback("✗ Failed to clear activities: ${e.message}", true)
            }
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> loadUsers()
            1 -> loadAllActivities()
        }
    }

    LaunchedEffect(Unit) {
        loadUsers()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdminTopBar(
                onLogout = onLogout,
                selectedUser = selectedUser,
                onBackToUsers = {
                    selectedUser = null
                    loadUsers()
                }
            )

            if (selectedUser == null) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A1A2E),
                    contentColor = Color(0xFF00FF88)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, color = if (selectedTab == index) Color(0xFF00FF88) else Color.White.copy(alpha = 0.6f)) }
                        )
                    }
                }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00FF88))
                    }
                }
                selectedUser != null -> {
                    UserActivityView(
                        user = selectedUser!!,
                        activities = userActivities,
                        onBack = { selectedUser = null; loadUsers() },
                        onClearUserActivities = { clearUserActivities(selectedUser!!) }
                    )
                }
                selectedTab == 0 -> {
                    UsersListView(
                        users = users,
                        onUserClick = { loadUserActivities(it) },
                        onDeleteUser = { showDeleteDialog = it }
                    )
                }
                else -> {
                    AllActivitiesView(
                        activities = userActivities,
                        onClearAll = { showClearAllDialog = true }
                    )
                }
            }
        }

        showDeleteDialog?.let { user ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete User", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text("Delete ${user.displayName}?", color = Color.White.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF4444).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "⚠️ This will delete:\n• Firestore user data\n• Activity logs\n• Realtime Database data",
                                color = Color(0xFFFF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { deleteUserComplete(user) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        enabled = !isDeletingUser
                    ) {
                        if (isDeletingUser) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deleting...", color = Color.White)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete User", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Activities", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            "Are you sure you want to delete ALL activity logs from ALL users?",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF4444).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "⚠️ This action cannot be undone!",
                                color = Color(0xFFFF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { clearAllActivities() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                        enabled = !isClearingAll
                    ) {
                        if (isClearingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clearing...", color = Color.White)
                        } else {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Yes, Delete All", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }

        if (showToast) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (toastMessage.contains("✓")) Color(0xFF00FF88) else Color(0xFFFF4444)
                    )
                ) {
                    Text(
                        toastMessage,
                        color = Color.Black,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            LaunchedEffect(showToast) {
                delay(2000)
                showToast = false
            }
        }
    }
}

@Composable
fun AdminTopBar(onLogout: () -> Unit, selectedUser: AdminUser?, onBackToUsers: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedUser != null) {
                IconButton(onClick = onBackToUsers) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00FF88))
                }
            }
            Column {
                Text(
                    if (selectedUser != null) "${selectedUser.displayName}'s Activities" else "Admin Dashboard",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (selectedUser == null) {
                    Text("Manage users & monitor activities", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }

        IconButton(onClick = onLogout) {
            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFFFF4444))
        }
    }
}

@Composable
fun UsersListView(users: List<AdminUser>, onUserClick: (AdminUser) -> Unit, onDeleteUser: (AdminUser) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(users) { user ->
            UserCardWithImage(
                user = user,
                onClick = { onUserClick(user) },
                onDelete = { onDeleteUser(user) }
            )
        }

        if (users.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No users found", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun UserCardWithImage(user: AdminUser, onClick: () -> Unit, onDelete: () -> Unit) {
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val hasImage = !user.profileImageBase64.isNullOrEmpty()

    LaunchedEffect(user.profileImageBase64) {
        if (hasImage) {
            profileBitmap = user.profileImageBase64?.decodeBase64ToBitmap()
        } else {
            profileBitmap = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Image or Initials
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null && hasImage) {
                            androidx.compose.foundation.Image(
                                bitmap = profileBitmap!!.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF00FF88).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.displayName.take(1).uppercase(),
                                    color = Color(0xFF00FF88),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(user.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(user.email, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text(
                            "Joined: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(user.createdAt))}",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444))
                }
            }

            user.deviceStatus?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeviceStatusChip(
                        icon = if (status.lightOn) "💡" else "⚪",
                        label = "Light",
                        isOn = status.lightOn,
                        detail = "${status.lightBrightness}%"
                    )
                    DeviceStatusChip(
                        icon = if (status.fanOn) "🌀" else "⚪",
                        label = "Fan",
                        isOn = status.fanOn,
                        detail = status.fanSpeed
                    )
                    DeviceStatusChip(
                        icon = if (status.acOn) "❄️" else "⚪",
                        label = "AC",
                        isOn = status.acOn,
                        detail = "${status.temperature}°C"
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceStatusChip(icon: String, label: String, isOn: Boolean, detail: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOn) Color(0xFF00FF88).copy(alpha = 0.15f) else Color(0xFF2A2A3E)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 12.sp)
            Text(label, color = if (isOn) Color(0xFF00FF88) else Color.Gray, fontSize = 10.sp)
            Text(detail, color = if (isOn) Color.White else Color.Gray, fontSize = 9.sp)
        }
    }
}

@Composable
fun UserActivityView(
    user: AdminUser,
    activities: List<AdminActivityLog>,
    onBack: () -> Unit,
    onClearUserActivities: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    // Load user profile image for the header
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val hasImage = !user.profileImageBase64.isNullOrEmpty()

    LaunchedEffect(user.profileImageBase64) {
        if (hasImage) {
            profileBitmap = user.profileImageBase64?.decodeBase64ToBitmap()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // User Profile Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Image
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null && hasImage) {
                            androidx.compose.foundation.Image(
                                bitmap = profileBitmap!!.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF00FF88).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.displayName.take(1).uppercase(),
                                    color = Color(0xFF00FF88),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            user.displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            user.email,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Text(
                            "UID: ${user.uid.take(8)}...",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00FF88).copy(alpha = 0.1f))
                ) {
                    Text(
                        "Total Activities: ${activities.size}",
                        color = Color(0xFF00FF88),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.width(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = activities.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear",
                        tint = Color(0xFFFF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", color = Color(0xFFFF4444), fontSize = 12.sp)
                }
            }
        }

        items(activities) { activity ->
            ActivityLogCard(log = activity, showUserName = false)
        }

        if (activities.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No activities yet", color = Color.Gray)
                        Text("User's device actions will appear here", color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear User Activities", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Delete all activities for ${user.displayName}? This cannot be undone.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearUserActivities()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) {
                    Text("Yes, Clear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1A2E)
        )
    }
}

@Composable
fun AllActivitiesView(activities: List<AdminActivityLog>, onClearAll: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00FF88).copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total Activities: ${activities.size}",
                        color = Color(0xFF00FF88),
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4444).copy(alpha = 0.15f),
                            contentColor = Color(0xFFFF4444)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = activities.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(activities) { activity ->
            ActivityLogCard(log = activity, showUserName = true)
        }

        if (activities.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No activities found", color = Color.Gray)
                        Text("User activities will appear here", color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityLogCard(log: AdminActivityLog, showUserName: Boolean) {
    val timeStr = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault()).format(Date(log.timestamp))

    val icon = when {
        log.action == "emergency_alert" -> Icons.Default.Warning
        log.action == "islamic_feature" -> Icons.Default.Mosque
        log.action.contains("mode") || log.action == "mode_activated" -> Icons.Default.AutoAwesome
        log.action.contains("device_update") -> Icons.Default.Settings
        log.details.contains("light", ignoreCase = true) -> Icons.Default.Lightbulb
        log.details.contains("fan", ignoreCase = true) -> Icons.Default.Air
        log.details.contains("ac", ignoreCase = true) -> Icons.Default.AcUnit
        else -> Icons.Default.Info
    }

    val iconColor = when {
        log.action == "emergency_alert" && log.details.contains("Fire") -> Color(0xFFFF4444)
        log.action == "emergency_alert" && log.details.contains("Gas") -> Color(0xFFFFB800)
        log.action == "islamic_feature" -> Color(0xFF14B8A6)
        log.action == "mode_activated" && log.details.contains("Prayer") -> Color(0xFF14B8A6)
        log.action == "mode_activated" -> Color(0xFFA855F7)
        log.details.contains("light", ignoreCase = true) -> Color(0xFFFFB800)
        log.details.contains("fan", ignoreCase = true) -> Color(0xFF3B9BFF)
        log.details.contains("ac", ignoreCase = true) -> Color(0xFF14B8A6)
        else -> Color(0xFFA8FF3E)
    }

    val actionDisplay = when (log.action) {
        "device_update" -> "Device Control"
        "mode_activated" -> "Mode Activated"
        "emergency_alert" -> "Emergency Alert"
        "islamic_feature" -> "Islamic Feature"
        else -> log.action.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                if (showUserName && log.userName.isNotEmpty()) {
                    Text(
                        "👤 ${log.userName}",
                        color = Color(0xFFA8FF3E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(actionDisplay, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(log.details, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2)
                Text(timeStr, color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}