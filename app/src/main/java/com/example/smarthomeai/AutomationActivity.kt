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
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutomationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutomationScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun AutomationScreen(onBackClick: () -> Unit) {
    val dbRef = remember {
        FirebaseDatabase.getInstance().getReference("devices/status")
    }

    val coroutineScope = rememberCoroutineScope()
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("No mode selected") }
    var customLightOn by remember { mutableStateOf(false) }
    var customFanOn by remember { mutableStateOf(false) }
    var customAcOn by remember { mutableStateOf(false) }
    var customFanSpeed by remember { mutableStateOf("Low") }
    var customBrightness by remember { mutableStateOf(50f) }
    var customTemp by remember { mutableStateOf(24f) }
    var isApplying by remember { mutableStateOf(false) }

    fun showSuccessMessage(message: String) {
        successMessage = message
        showSuccessDialog = true
        coroutineScope.launch {
            delay(2000)
            showSuccessDialog = false
        }
    }

    fun applyMode(
        modeName: String,
        lightOn: Boolean,
        lightBrightness: Int,
        fanOn: Boolean,
        fanSpeed: String,
        acOn: Boolean,
        temperature: Int
    ) {
        isApplying = true
        val data = mapOf(
            "mode" to modeName,
            "lightOn" to lightOn,
            "lightBrightness" to lightBrightness,
            "fanOn" to fanOn,
            "fanSpeed" to fanSpeed,
            "acOn" to acOn,
            "temperature" to temperature
        )

        dbRef.updateChildren(data).addOnCompleteListener {
            isApplying = false
            if (it.isSuccessful) {
                selectedMode = modeName
                showSuccessMessage("✓ $modeName activated successfully!")
            } else {
                showSuccessMessage("✗ Failed to activate mode")
            }
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
            AnimatedTopBar(onBackClick = onBackClick)

            // Current Mode Banner with Animation
            AnimatedCurrentModeBanner(selectedMode = selectedMode)

            Spacer(modifier = Modifier.height(8.dp))

            // Modes Section Header
            SectionHeaderAutomation(title = "⚡ Smart Modes", subtitle = "One-tap automation")

            // Mode Cards
            ModeCard(
                title = "Study Mode",
                subtitle = "Light ON • Fan Medium • 90% Brightness",
                icon = Icons.Filled.MenuBook,
                iconBgColor = BlueAccent,
                gradientColors = listOf(Color(0xFF1E3A5F), CardDark),
                onClick = {
                    applyMode("Study Mode", true, 90, true, "Medium", false, 24)
                }
            )

            ModeCard(
                title = "Sleep Mode",
                subtitle = "Dim Light • Fan Low • 20% Brightness",
                icon = Icons.Filled.Bedtime,
                iconBgColor = PurpleAccent,
                gradientColors = listOf(Color(0xFF2D1B4E), CardDark),
                onClick = {
                    applyMode("Sleep Mode", true, 20, true, "Low", false, 24)
                }
            )

            ModeCard(
                title = "Prayer Mode",
                subtitle = "Soft Light • Silent • 35% Brightness",
                icon = Icons.Filled.Mosque,
                iconBgColor = Color(0xFF14B8A6),
                gradientColors = listOf(Color(0xFF134E4A), CardDark),
                onClick = {
                    applyMode("Prayer Mode", true, 35, false, "Low", false, 24)
                }
            )

            ModeCard(
                title = "Away Mode",
                subtitle = "All Devices OFF • Energy Saving",
                icon = Icons.Filled.DirectionsRun,
                iconBgColor = EmergencyRed,
                gradientColors = listOf(Color(0xFF4A0E0E), CardDark),
                onClick = {
                    applyMode("Away Mode", false, 0, false, "Low", false, 24)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Mode Section
            AnimatedCustomModeSection(
                customLightOn = customLightOn,
                onLightToggle = { customLightOn = it },
                customFanOn = customFanOn,
                onFanToggle = { customFanOn = it },
                customAcOn = customAcOn,
                onAcToggle = { customAcOn = it },
                customBrightness = customBrightness,
                onBrightnessChange = { customBrightness = it },
                customFanSpeed = customFanSpeed,
                onFanSpeedChange = { customFanSpeed = it },
                customTemp = customTemp,
                onTempChange = { customTemp = it },
                onApply = {
                    applyMode(
                        "Custom Mode",
                        customLightOn,
                        customBrightness.toInt(),
                        customFanOn,
                        customFanSpeed,
                        customAcOn,
                        customTemp.toInt()
                    )
                },
                isApplying = isApplying
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Success Animation Dialog
        if (showSuccessDialog) {
            SuccessToast(message = successMessage)
        }
    }
}

@Composable
fun AnimatedTopBar(onBackClick: () -> Unit) {
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

        Column {
            Text(
                text = "Automation",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Smart rules for your home",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AnimatedCurrentModeBanner(selectedMode: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (selectedMode != "No mode selected") 1f else 0f,
        animationSpec = tween(500)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedMode != "No mode selected")
                GreenAccent.copy(alpha = 0.1f)
            else Card2
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selectedMode != "No mode selected")
                GreenAccent.copy(alpha = 0.3f)
            else Color(0xFF252525)
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
                        .background(
                            if (selectedMode != "No mode selected")
                                GreenAccent.copy(alpha = 0.15f)
                            else ChipBg
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (selectedMode != "No mode selected") Icons.Filled.CheckCircle
                        else Icons.Filled.Tune,
                        contentDescription = null,
                        tint = if (selectedMode != "No mode selected") GreenAccent else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        "Active Mode",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        selectedMode,
                        color = if (selectedMode != "No mode selected") GreenAccent else TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedMode != "No mode selected") {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GreenAccent)
                        .scale(animatedProgress)
                )
            }
        }
    }
}

@Composable
fun SectionHeaderAutomation(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = gradientColors
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBgColor.copy(alpha = 0.2f))
                        .border(1.dp, iconBgColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = iconBgColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Apply",
                tint = GreenAccent,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun AnimatedCustomModeSection(
    customLightOn: Boolean,
    onLightToggle: (Boolean) -> Unit,
    customFanOn: Boolean,
    onFanToggle: (Boolean) -> Unit,
    customAcOn: Boolean,
    onAcToggle: (Boolean) -> Unit,
    customBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    customFanSpeed: String,
    onFanSpeedChange: (String) -> Unit,
    customTemp: Float,
    onTempChange: (Float) -> Unit,
    onApply: () -> Unit,
    isApplying: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GreenAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Custom",
                        tint = GreenAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        "Custom Mode",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Create your own automation",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Light Control
            DeviceControlCard(
                title = "Smart Light",
                icon = Icons.Filled.Lightbulb,
                accentColor = YellowAccent,
                enabled = customLightOn,
                onEnabledChange = onLightToggle
            ) {
                if (customLightOn) {
                    SliderWithLabel(
                        value = customBrightness,
                        onValueChange = onBrightnessChange,
                        label = "Brightness",
                        unit = "%",
                        color = YellowAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fan Control
            DeviceControlCard(
                title = "Smart Fan",
                icon = Icons.Filled.Air,
                accentColor = BlueAccent,
                enabled = customFanOn,
                onEnabledChange = onFanToggle
            ) {
                if (customFanOn) {
                    SpeedChipSelector(
                        selectedSpeed = customFanSpeed,
                        onSpeedChange = onFanSpeedChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AC Control
            DeviceControlCard(
                title = "Air Conditioner",
                icon = Icons.Filled.AcUnit,
                accentColor = Color(0xFF14B8A6),
                enabled = customAcOn,
                onEnabledChange = onAcToggle
            ) {
                if (customAcOn) {
                    SliderWithLabel(
                        value = customTemp,
                        onValueChange = onTempChange,
                        label = "Temperature",
                        unit = "°C",
                        color = Color(0xFF14B8A6),
                        range = 16f..30f
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor = Color.Black
                ),
                enabled = !isApplying
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Applying...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Apply Custom Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceControlCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) accentColor.copy(alpha = 0.08f) else Color(0xFF151515)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) accentColor.copy(alpha = 0.3f) else Color(0xFF252525)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = title,
                            tint = if (enabled) accentColor else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        title,
                        color = if (enabled) TextPrimary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = ChipBg
                    )
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun SliderWithLabel(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    unit: String,
    color: Color,
    range: ClosedFloatingPointRange<Float> = 0f..100f
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                "${value.toInt()}$unit",
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = ChipBg
            )
        )
    }
}

@Composable
fun SpeedChipSelector(
    selectedSpeed: String,
    onSpeedChange: (String) -> Unit
) {
    Column {
        Text(
            "Fan Speed",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Low", "Medium", "High").forEach { speed ->
                val isSelected = selectedSpeed == speed
                FilterChip(
                    selected = isSelected,
                    onClick = { onSpeedChange(speed) },
                    label = {
                        Text(
                            speed,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BlueAccent,
                        selectedLabelColor = Color.Black,
                        containerColor = ChipBg,
                        labelColor = TextPrimary
                    ),
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.SuccessToast(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GreenAccent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
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