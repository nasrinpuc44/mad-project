package com.example.smarthomeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import com.example.smarthomeai.utils.LogHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ActivityLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActivityLogScreen(onBackClick = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(onBackClick: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var logs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf<String?>("all") }
    var showDeleteConfirmation by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showSuccessToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val filters = listOf("all", "device_update", "mode_activated")

    fun showFeedback(message: String) {
        toastMessage = message
        showSuccessToast = true
        coroutineScope.launch {
            kotlinx.coroutines.delay(2000)
            showSuccessToast = false
        }
    }

    fun loadLogs() {
        isLoading = true
        coroutineScope.launch {
            val filter = if (selectedFilter == "all") null else selectedFilter
            logs = LogHelper.getActivityLogs(limit = 100, filterAction = filter)
            isLoading = false
        }
    }

    fun deleteLog(logId: String) {
        coroutineScope.launch {
            LogHelper.deleteLog(logId) { success, message ->
                showFeedback(message)
                if (success) loadLogs()
            }
        }
    }

    fun clearAllLogs() {
        coroutineScope.launch {
            LogHelper.deleteAllLogs { success, message ->
                showFeedback(message)
                if (success) loadLogs()
            }
        }
    }

    LaunchedEffect(selectedFilter) { loadLogs() }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(CardDark)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GreenAccent, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Activity Log", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Swipe to delete • Powered by Firestore", color = GreenAccent, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = { showDeleteConfirmation = mapOf("clearAll" to true) },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(EmergencyRed.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = EmergencyRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Filter Chips
            LazyRow(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val displayName = when (filter) {
                        "all" -> "All"
                        "device_update" -> "Device Control"
                        "mode_activated" -> "Modes"
                        else -> filter
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = if (selectedFilter == filter) "all" else filter },
                        label = { Text(displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GreenAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = ChipBg,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            // Logs List
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenAccent)
                    }
                }
                logs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No activities yet", color = TextSecondary)
                            Text("Your device actions will appear here", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it["logId"] as? String ?: UUID.randomUUID().toString() }) { log ->
                            SwipeToDismissLogCard(
                                log = log,
                                onDelete = { deleteLog(log["logId"] as? String ?: "") }
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        showDeleteConfirmation?.let { item ->
            val isClearAll = item["clearAll"] == true
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = null },
                title = {
                    Text(
                        if (isClearAll) "Clear All Logs" else "Delete Log",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (isClearAll) "Are you sure you want to delete ALL activity logs? This cannot be undone."
                        else "Are you sure you want to delete this log entry?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isClearAll) clearAllLogs()
                            showDeleteConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CardDark
            )
        }

        // Success Toast
        if (showSuccessToast) {
            ActivityLogToast(message = toastMessage)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissLogCard(
    log: Map<String, Any>,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart ||
            dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            if (!isDeleting) {
                isDeleting = true
                onDelete()
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EmergencyRed)
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        ActivityLogCard(log = log)
    }
}

@Composable
fun ActivityLogCard(log: Map<String, Any>) {
    val action = log["action"] as? String ?: ""
    val details = log["details"] as? String ?: ""
    val timestamp = log["timestamp"] as? Long ?: 0L

    val timeStr = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

    val icon = when {
        action.contains("mode") -> Icons.Default.AutoAwesome
        action.contains("light") -> Icons.Default.Lightbulb
        action.contains("fan") -> Icons.Default.Air
        action.contains("ac") -> Icons.Default.AcUnit
        else -> Icons.Default.Settings
    }

    val iconColor = when {
        action.contains("mode") -> PurpleAccent
        action.contains("light") -> YellowAccent
        action.contains("fan") -> BlueAccent
        action.contains("ac") -> IslamicTeal
        else -> GreenAccent
    }

    val actionDisplay = when (action) {
        "device_update" -> "Device Control"
        "mode_activated" -> "Mode Activated"
        else -> action.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(actionDisplay, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(details, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                Text(timeStr, color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Surface(shape = RoundedCornerShape(4.dp), color = GreenAccent.copy(alpha = 0.1f)) {
                Text("FS", color = GreenAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
fun ActivityLogToast(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
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
                    if (message.contains("✓")) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(message, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
