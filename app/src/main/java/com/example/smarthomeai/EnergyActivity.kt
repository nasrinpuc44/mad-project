package com.example.smarthomeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class EnergyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnergyScreen(onBackClick = { finish() })
        }
    }
}

@Composable
fun EnergyScreen(onBackClick: () -> Unit) {
    val dbRef = remember {
        FirebaseDatabase.getInstance().getReference("devices/status")
    }

    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf("") }

    var lightOn by remember { mutableStateOf(false) }
    var fanOn by remember { mutableStateOf(false) }
    var acOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lightOn = snapshot.child("lightOn").getValue(Boolean::class.java) ?: false
                fanOn = snapshot.child("fanOn").getValue(Boolean::class.java) ?: false
                acOn = snapshot.child("acOn").getValue(Boolean::class.java) ?: false

                // Update timestamp
                lastUpdated = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        dbRef.addValueEventListener(listener)

        onDispose {
            dbRef.removeEventListener(listener)
        }
    }

    // Energy calculation based on device status
    val lightHours = if (lightOn) 5.0 else 2.0
    val fanHours = if (fanOn) 8.0 else 3.0
    val acHours = if (acOn) 4.0 else 1.0

    val lightUnit = lightHours * 0.06
    val fanUnit = fanHours * 0.075
    val acUnit = acHours * 1.2

    val todayUsage = lightUnit + fanUnit + acUnit
    val weeklyUsage = todayUsage * 7
    val monthlyUsage = todayUsage * 30
    val estimatedBill = monthlyUsage * 8.5

    val mostUsed = listOf(
        "Smart Fan" to fanHours,
        "Smart Light" to lightHours,
        "Air Conditioner" to acHours
    ).sortedByDescending { it.second }

    fun refreshData() {
        isRefreshing = true
        coroutineScope.launch {
            delay(1000)
            isRefreshing = false
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
            AnimatedEnergyTopBar(
                onBackClick = onBackClick,
                onRefresh = { refreshData() },
                isRefreshing = isRefreshing
            )

            // Main Energy Gauge Card
            MainEnergyGaugeCard(
                todayUsage = todayUsage,
                estimatedBill = estimatedBill
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Row
            StatsRow(
                weeklyUsage = weeklyUsage,
                monthlyUsage = monthlyUsage
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Detailed Energy Chart
            EnhancedEnergyChart(
                light = lightUnit.toFloat(),
                fan = fanUnit.toFloat(),
                ac = acUnit.toFloat(),
                lightOn = lightOn,
                fanOn = fanOn,
                acOn = acOn
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Usage Breakdown Card
            UsageBreakdownCard(
                lightHours = lightHours,
                fanHours = fanHours,
                acHours = acHours,
                lightOn = lightOn,
                fanOn = fanOn,
                acOn = acOn,
                lightUnit = lightUnit,
                fanUnit = fanUnit,
                acUnit = acUnit
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Most Used Device Card
            EnhancedMostUsedCard(mostUsed)

            Spacer(modifier = Modifier.height(12.dp))

            // Energy Tips Section
            EnergyTipsSection()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AnimatedEnergyTopBar(
    onBackClick: () -> Unit,
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
                text = "Energy Monitor",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Real-time energy tracking",
                color = TextSecondary,
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
    }
}

@Composable
fun MainEnergyGaugeCard(todayUsage: Double, estimatedBill: Double) {
    val animatedProgress by animateFloatAsState(
        targetValue = (todayUsage.toFloat() / 15f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing)
    )

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Today's Usage",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        String.format("%.2f", todayUsage),
                        color = GreenAccent,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "kWh",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawArc(
                            color = GreenAccent.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = true,
                            style = Stroke(width = 8f)
                        )
                        drawArc(
                            color = GreenAccent,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = true,
                            style = Stroke(width = 8f, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(animatedProgress * 100).toInt()}",
                            color = GreenAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "%",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GreenAccent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Estimated Bill", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        "৳ ${String.format("%.0f", estimatedBill)}",
                        color = GreenAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun StatsRow(weeklyUsage: Double, monthlyUsage: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Weekly",
            value = String.format("%.1f", weeklyUsage),
            unit = "kWh",
            icon = Icons.Outlined.CalendarToday,
            color = BlueAccent,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Monthly",
            value = String.format("%.1f", monthlyUsage),
            unit = "kWh",
            icon = Icons.Outlined.DateRange,
            color = PurpleAccent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = TextSecondary, fontSize = 10.sp)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun EnhancedEnergyChart(
    light: Float,
    fan: Float,
    ac: Float,
    lightOn: Boolean,
    fanOn: Boolean,
    acOn: Boolean
) {
    val maxValue = maxOf(light, fan, ac, 1f)

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
                    "Usage Breakdown",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GreenAccent.copy(alpha = 0.1f)
                ) {
                    Text(
                        "kWh",
                        color = GreenAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedChartBar(
                label = "Smart Light",
                value = light,
                maxValue = maxValue,
                color = YellowAccent,
                isActive = lightOn,
                icon = Icons.Default.Lightbulb
            )

            AnimatedChartBar(
                label = "Smart Fan",
                value = fan,
                maxValue = maxValue,
                color = BlueAccent,
                isActive = fanOn,
                icon = Icons.Default.Air
            )

            AnimatedChartBar(
                label = "Air Conditioner",
                value = ac,
                maxValue = maxValue,
                color = IslamicTeal,
                isActive = acOn,
                icon = Icons.Default.AcUnit
            )
        }
    }
}

@Composable
fun AnimatedChartBar(
    label: String,
    value: Float,
    maxValue: Float,
    color: Color,
    isActive: Boolean,
    icon: ImageVector
) {
    val animatedWidth by animateFloatAsState(
        targetValue = value / maxValue,
        animationSpec = tween(800, easing = FastOutSlowInEasing)
    )

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isActive) color else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    label,
                    color = if (isActive) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                )
            }
            Text(
                String.format("%.2f", value),
                color = if (isActive) color else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawLine(
                color = ChipBg,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width * animatedWidth, size.height / 2),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun UsageBreakdownCard(
    lightHours: Double,
    fanHours: Double,
    acHours: Double,
    lightOn: Boolean,
    fanOn: Boolean,
    acOn: Boolean,
    lightUnit: Double,
    fanUnit: Double,
    acUnit: Double
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
            Text(
                "Detailed Breakdown",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            EnhancedDeviceUsageRow(
                name = "Smart Light",
                hours = lightHours,
                unit = lightUnit,
                isOn = lightOn,
                color = YellowAccent
            )

            EnhancedDeviceUsageRow(
                name = "Smart Fan",
                hours = fanHours,
                unit = fanUnit,
                isOn = fanOn,
                color = BlueAccent
            )

            EnhancedDeviceUsageRow(
                name = "Air Conditioner",
                hours = acHours,
                unit = acUnit,
                isOn = acOn,
                color = IslamicTeal
            )
        }
    }
}

@Composable
fun EnhancedDeviceUsageRow(
    name: String,
    hours: Double,
    unit: Double,
    isOn: Boolean,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
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
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isOn) color else TextSecondary)
                )
                Column {
                    Text(
                        name,
                        color = if (isOn) TextPrimary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isOn) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(
                        if (isOn) "Currently Active" else "Currently Inactive",
                        color = if (isOn) color else TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f", hours),
                    color = if (isOn) color else TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "hours / ${String.format("%.2f", unit)} kWh",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { (hours / 24f).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isOn) color else ChipBg,
            trackColor = ChipBg
        )
    }
}

@Composable
fun EnhancedMostUsedCard(mostUsed: List<Pair<String, Double>>) {
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
                    "Most Used Device",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            mostUsed.forEachIndexed { index, item ->
                val colors = listOf(GreenAccent, BlueAccent, YellowAccent)
                val color = colors[index % colors.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                color = color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            item.first,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            String.format("%.1f", item.second),
                            color = color,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "hours/day",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                if (index < mostUsed.size - 1) {
                    HorizontalDivider(
                        color = Color(0xFF252525),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyTipsSection() {
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
                    "Energy Saving Tips",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GreenAccent.copy(alpha = 0.1f)
                ) {
                    Text(
                        "Save Money",
                        color = GreenAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val tips = listOf(
                "💡 Turn off lights when leaving a room",
                "🌀 Use fan instead of AC when possible",
                "❄️ Keep AC temperature between 24°C - 26°C",
                "🏠 Use Away Mode before leaving home",
                "📱 Monitor usage regularly in this app"
            )

            tips.forEach { tip ->
                EnhancedTipText(text = tip)
            }
        }
    }
}

@Composable
fun EnhancedTipText(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(GreenAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = GreenAccent,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}
