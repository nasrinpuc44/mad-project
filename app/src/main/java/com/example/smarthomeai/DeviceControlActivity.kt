package com.example.smarthomeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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

class DeviceControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceControlScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun DeviceControlScreen(onBackClick: () -> Unit) {
    val dbRef = remember {
        FirebaseDatabase.getInstance().getReference("devices/status")
    }

    val coroutineScope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    var lightOn by remember { mutableStateOf(false) }
    var lightBrightness by remember { mutableStateOf(50f) }

    var fanOn by remember { mutableStateOf(false) }
    var fanSpeed by remember { mutableStateOf("Low") }

    var acOn by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(24) }

    var isConnected by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    fun showFeedback(message: String) {
        toastMessage = message
        showToast = true
        coroutineScope.launch {
            delay(2000)
            showToast = false
        }
    }

    fun updateDevice(key: String, value: Any) {
        isSyncing = true
        dbRef.child(key).setValue(value).addOnCompleteListener {
            isSyncing = false
            if (it.isSuccessful) {
                showFeedback("✓ $key updated")
            } else {
                showFeedback("✗ Failed to update $key")
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lightOn = snapshot.child("lightOn").getValue(Boolean::class.java) ?: false
                lightBrightness = (snapshot.child("lightBrightness").getValue(Int::class.java) ?: 50).toFloat()
                fanOn = snapshot.child("fanOn").getValue(Boolean::class.java) ?: false
                fanSpeed = snapshot.child("fanSpeed").getValue(String::class.java) ?: "Low"
                acOn = snapshot.child("acOn").getValue(Boolean::class.java) ?: false
                temperature = snapshot.child("temperature").getValue(Int::class.java) ?: 24
                isConnected = true
            }

            override fun onCancelled(error: DatabaseError) {
                isConnected = false
                showFeedback("⚠️ Firebase connection error")
            }
        }

        dbRef.addValueEventListener(listener)

        onDispose {
            dbRef.removeEventListener(listener)
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
            AnimatedDeviceTopBar(onBackClick = onBackClick, isSyncing = isSyncing)

            // Real-time Status Card
            RealTimeStatusCard(
                lightOn = lightOn,
                fanOn = fanOn,
                acOn = acOn,
                isConnected = isConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Light Control Card
            AnimatedDeviceCard(
                title = "Smart Light",
                icon = Icons.Filled.Lightbulb,
                accentColor = YellowAccent,
                iconBgColor = YellowAccent.copy(alpha = 0.15f),
                isOn = lightOn
            ) {
                EnhancedSwitchRow(
                    label = if (lightOn) "Light is ON" else "Light is OFF",
                    isOn = lightOn,
                    onToggle = {
                        lightOn = it
                        updateDevice("lightOn", it)
                    },
                    accentColor = YellowAccent
                )

                if (lightOn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedSliderWithIcon(
                        value = lightBrightness,
                        onValueChange = {
                            lightBrightness = it
                            updateDevice("lightBrightness", it.toInt())
                        },
                        valueRange = 0f..100f,
                        label = "Brightness",
                        unit = "%",
                        icon = Icons.Filled.BrightnessMedium,
                        color = YellowAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fan Control Card
            AnimatedDeviceCard(
                title = "Smart Fan",
                icon = Icons.Filled.Air,
                accentColor = BlueAccent,
                iconBgColor = BlueAccent.copy(alpha = 0.15f),
                isOn = fanOn
            ) {
                EnhancedSwitchRow(
                    label = if (fanOn) "Fan is ON" else "Fan is OFF",
                    isOn = fanOn,
                    onToggle = {
                        fanOn = it
                        updateDevice("fanOn", it)
                    },
                    accentColor = BlueAccent
                )

                if (fanOn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Fan Speed",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    AnimatedSpeedChips(
                        selectedSpeed = fanSpeed,
                        onSpeedChange = {
                            fanSpeed = it
                            updateDevice("fanSpeed", it)
                        },
                        accentColor = BlueAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AC Control Card
            AnimatedDeviceCard(
                title = "Air Conditioner",
                icon = Icons.Filled.AcUnit,
                accentColor = IslamicTeal,
                iconBgColor = IslamicTeal.copy(alpha = 0.15f),
                isOn = acOn
            ) {
                EnhancedSwitchRow(
                    label = if (acOn) "AC is ON" else "AC is OFF",
                    isOn = acOn,
                    onToggle = {
                        acOn = it
                        updateDevice("acOn", it)
                    },
                    accentColor = IslamicTeal
                )

                if (acOn) {
                    Spacer(modifier = Modifier.height(16.dp))

                    TemperatureControl(
                        temperature = temperature,
                        onTemperatureChange = {
                            temperature = it
                            updateDevice("temperature", it)
                        },
                        accentColor = IslamicTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Success/Error Toast
        if (showToast) {
            CustomToast(message = toastMessage)
        }
    }
}

@Composable
fun AnimatedDeviceTopBar(onBackClick: () -> Unit, isSyncing: Boolean) {
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
                text = "Device Control",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Control your smart devices",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = GreenAccent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                Icons.Filled.Sync,
                contentDescription = "Synced",
                tint = GreenAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RealTimeStatusCard(
    lightOn: Boolean,
    fanOn: Boolean,
    acOn: Boolean,
    isConnected: Boolean
) {
    val activeCount = listOf(lightOn, fanOn, acOn).count { it }
    val animatedProgress by animateFloatAsState(
        targetValue = if (activeCount > 0) 1f else 0f,
        animationSpec = tween(500)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeCount > 0) GreenAccent.copy(alpha = 0.08f) else Card2
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (activeCount > 0) GreenAccent.copy(alpha = 0.3f) else Color(0xFF252525)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (activeCount > 0) GreenAccent.copy(alpha = 0.15f) else ChipBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (activeCount > 0) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (activeCount > 0) GreenAccent else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        if (isConnected) "System Online" else "Connecting...",
                        color = if (isConnected) GreenAccent else YellowAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$activeCount Devices Active",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(GreenAccent)
                        .scale(animatedProgress)
                )
            }
        }
    }
}

@Composable
fun AnimatedDeviceCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    iconBgColor: Color,
    isOn: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) accentColor.copy(alpha = 0.05f) else CardDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOn) accentColor.copy(alpha = 0.25f) else Color(0xFF252525)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(iconBgColor)
                            .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isOn) "Status: Running" else "Status: Standby",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun EnhancedSwitchRow(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF151515))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (isOn) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = ChipBg,
                uncheckedBorderColor = ChipBg
            )
        )
    }
}

@Composable
fun AnimatedSliderWithIcon(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    unit: String,
    icon: ImageVector,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text(label, color = TextSecondary, fontSize = 12.sp)
            }
            Text(
                "${value.toInt()}$unit",
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = ChipBg
            )
        )
    }
}

@Composable
fun AnimatedSpeedChips(
    selectedSpeed: String,
    onSpeedChange: (String) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("Low", "Medium", "High").forEach { speed ->
            val isSelected = selectedSpeed == speed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) accentColor else ChipBg)
                    .clickable { onSpeedChange(speed) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    speed,
                    color = if (isSelected) Color.Black else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TemperatureControl(
    temperature: Int,
    onTemperatureChange: (Int) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (temperature > 16) onTemperatureChange(temperature - 1) },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ChipBg)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Temperature", color = TextSecondary, fontSize = 12.sp)
            Text("$temperature°C", color = accentColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(
            onClick = { if (temperature < 30) onTemperatureChange(temperature + 1) },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ChipBg)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextPrimary)
        }
    }
}

@Composable
fun BoxScope.CustomToast(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ChipBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(20.dp))
                Text(message, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}
