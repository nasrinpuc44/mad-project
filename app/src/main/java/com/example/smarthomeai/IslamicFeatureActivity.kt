package com.example.smarthomeai

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

class IslamicFeatureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IslamicFeatureScreen(
                onBackClick = { finish() }
            )
        }
    }
}

// ─── Prayer Times Data ────────────────────────────────────────────────────────

data class CityPrayerTimes(
    val city: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

// Dhaka & Chittagong annual average prayer times (April)
val dhakaPrayerTimes = CityPrayerTimes(
    city = "Dhaka",
    fajr = "4:19 AM",
    sunrise = "5:45 AM",
    dhuhr = "11:57 AM",
    asr = "3:23 PM",
    maghrib = "6:08 PM",
    isha = "7:28 PM"
)

val chittagongPrayerTimes = CityPrayerTimes(
    city = "Chittagong",
    fajr = "4:10 AM",
    sunrise = "5:35 AM",
    dhuhr = "11:48 AM",
    asr = "3:14 PM",
    maghrib = "5:59 PM",
    isha = "7:19 PM"
)

// ─── Real Compass Helper ──────────────────────────────────────────────────────

@Composable
fun rememberCompassBearing(): Float {
    val context = LocalContext.current
    var bearing by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeo = false

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeo = true
                    }
                }
                if (hasGravity && hasGeo) {
                    val R = FloatArray(9)
                    val I = FloatArray(9)
                    if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(R, orientation)
                        // azimuth in degrees (0 = North, clockwise)
                        bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (bearing < 0) bearing += 360f
                    }
                }
            }
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return bearing
}

// Qibla bearing from Dhaka ≈ 277° (West-North-West)
// Qibla bearing from Chittagong ≈ 276°
const val QIBLA_BEARING_DHAKA = 277f
const val QIBLA_BEARING_CHITTAGONG = 276f

// ─── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun IslamicFeatureScreen(onBackClick: () -> Unit) {
    val dbRef = remember {
        FirebaseDatabase.getInstance().getReference("devices/status")
    }

    val coroutineScope = rememberCoroutineScope()
    var showSuccessToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    var azanReminder by remember { mutableStateOf(false) }
    var prayerModeEnabled by remember { mutableStateOf(false) }

    // City selection: 0 = Dhaka, 1 = Chittagong
    var selectedCity by remember { mutableStateOf(0) }

    fun showFeedback(message: String) {
        toastMessage = message
        showSuccessToast = true
        coroutineScope.launch {
            delay(2000)
            showSuccessToast = false
        }
    }

    fun applyPrayerMode() {
        val data = mapOf(
            "mode" to "Prayer Mode",
            "lightOn" to true,
            "lightBrightness" to 30,
            "fanOn" to false,
            "fanSpeed" to "Low",
            "acOn" to false,
            "temperature" to 24
        )
        dbRef.updateChildren(data).addOnCompleteListener {
            if (it.isSuccessful) {
                prayerModeEnabled = true
                showFeedback("✓ Prayer Mode Activated")
            } else {
                showFeedback("✗ Failed to activate Prayer Mode")
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
            AnimatedIslamicTopBar(onBackClick = onBackClick)

            IslamicDateTimeCard()

            Spacer(modifier = Modifier.height(12.dp))

            // ── City Selector ──
            CitySelector(
                selectedCity = selectedCity,
                onCityChange = { selectedCity = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Prayer Times ──
            val prayerData = if (selectedCity == 0) dhakaPrayerTimes else chittagongPrayerTimes
            EnhancedPrayerTimeCard(cityData = prayerData)

            Spacer(modifier = Modifier.height(12.dp))

            // ── Azan Reminder ──
            EnhancedAzanReminderCard(
                azanReminder = azanReminder,
                onReminderChange = {
                    azanReminder = it
                    showFeedback(if (it) "✓ Azan Reminder Enabled" else "✓ Azan Reminder Disabled")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Real Qibla Compass (FIXED) ──
            val qiblaBearing = if (selectedCity == 0) QIBLA_BEARING_DHAKA else QIBLA_BEARING_CHITTAGONG
            RealQiblaCompassCard(qiblaBearing = qiblaBearing)

            Spacer(modifier = Modifier.height(12.dp))

            // ── Prayer Mode ──
            EnhancedPrayerModeCard(
                prayerModeEnabled = prayerModeEnabled,
                onPrayerModeClick = { applyPrayerMode() }
            )

            IslamicQuotesSection()

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showSuccessToast) {
            IslamicCustomToast(message = toastMessage)
        }
    }
}

// ─── City Selector ────────────────────────────────────────────────────────────

@Composable
fun CitySelector(selectedCity: Int, onCityChange: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Dhaka", "Chittagong").forEachIndexed { index, name ->
                val isSelected = selectedCity == index
                Button(
                    onClick = { onCityChange(index) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) IslamicTeal else ChipBg,
                        contentColor = if (isSelected) Color.Black else TextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─── Real Qibla Compass (FIXED) ───────────────────────────────────────────────

@Composable
fun RealQiblaCompassCard(qiblaBearing: Float) {
    // Live compass bearing from device sensor
    val rawDeviceBearing = rememberCompassBearing()

    // Smooth animation for device bearing
    val deviceBearing by animateFloatAsState(
        targetValue = rawDeviceBearing,
        animationSpec = tween(300, easing = LinearEasing),
        label = "device_bearing"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IslamicTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null,
                        tint = IslamicTeal, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Qibla Compass", color = TextPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("West Direction & Qibla", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Compass Drawing (FIXED) ──
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerR = size.minDimension / 2f - 6f
                    val innerR = outerR - 26f

                    // KEY FIX: Helper function for compass-to-canvas angle conversion
                    // Canvas 0° = East (right), Compass 0° = North (up)
                    // So subtract 90° to align properly
                    fun compassToCanvas(bearing: Float): Double =
                        Math.toRadians((bearing - 90.0))

                    // Background circle
                    drawCircle(color = Color(0xFF1A2A2A), radius = outerR, center = Offset(cx, cy))

                    // Outer ring
                    drawCircle(
                        color = IslamicTeal.copy(alpha = 0.5f),
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )

                    // Inner ring
                    drawCircle(
                        color = IslamicTeal.copy(alpha = 0.15f),
                        radius = innerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )

                    // ── Degree tick marks (compass rose rotates opposite to device) ──
                    for (deg in 0 until 360 step 5) {
                        // Rose rotates opposite to device movement
                        val roseBearing = deg.toFloat() - deviceBearing
                        val angle = compassToCanvas(roseBearing)
                        val isMajor = deg % 30 == 0
                        val tickLen = if (isMajor) 14f else 7f
                        val r1 = outerR - 2f
                        val r2 = r1 - tickLen
                        drawLine(
                            color = if (isMajor) TextPrimary.copy(alpha = 0.8f)
                            else TextSecondary.copy(alpha = 0.4f),
                            start = Offset(cx + r1 * cos(angle).toFloat(), cy + r1 * sin(angle).toFloat()),
                            end = Offset(cx + r2 * cos(angle).toFloat(), cy + r2 * sin(angle).toFloat()),
                            strokeWidth = if (isMajor) 2.5f else 1.2f
                        )
                    }

                    // ── Cardinal directions (N/S/E/W) ──
                    val cardinals = listOf(
                        Triple("N", 0f, Color(0xFFFF5252)),   // North - Red
                        Triple("S", 180f, TextSecondary),     // South - Grey
                        Triple("E", 90f, TextSecondary),      // East - Grey
                        Triple("W", 270f, Color(0xFF64B5F6))  // West - Blue
                    )

                    val paint = android.graphics.Paint().apply {
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        textSize = 28f
                    }

                    cardinals.forEach { (label, compassBearing, color) ->
                        // Rose angle = compass bearing minus device bearing
                        val roseBearing = compassBearing - deviceBearing
                        val angle = compassToCanvas(roseBearing)
                        val r = outerR - 40f
                        val tx = cx + r * cos(angle).toFloat()
                        val ty = cy + r * sin(angle).toFloat()

                        drawCircle(
                            color = color.copy(alpha = 0.2f),
                            radius = 13f,
                            center = Offset(tx, ty)
                        )
                        drawContext.canvas.nativeCanvas.apply {
                            paint.color = android.graphics.Color.argb(
                                (color.alpha * 255).toInt(),
                                (color.red * 255).toInt(),
                                (color.green * 255).toInt(),
                                (color.blue * 255).toInt()
                            )
                            drawText(label, tx, ty + 9f, paint)
                        }
                    }

                    // ── West highlight band (points to West direction) ──
                    val westRoseBearing = 270f - deviceBearing
                    val westAngle = compassToCanvas(westRoseBearing)
                    val westX = cx + (innerR - 20f) * cos(westAngle).toFloat()
                    val westY = cy + (innerR - 20f) * sin(westAngle).toFloat()
                    drawLine(
                        color = Color(0xFF64B5F6).copy(alpha = 0.4f),
                        start = Offset(cx, cy),
                        end = Offset(westX, westY),
                        strokeWidth = 20f,
                        cap = StrokeCap.Round
                    )

                    // ── Qibla arrow (green, always points to Qibla) ──
                    val qiblaRoseBearing = qiblaBearing - deviceBearing
                    val qAngle = compassToCanvas(qiblaRoseBearing)
                    val qTipX = cx + (innerR - 15f) * cos(qAngle).toFloat()
                    val qTipY = cy + (innerR - 15f) * sin(qAngle).toFloat()
                    val qTailX = cx - 30f * cos(qAngle).toFloat()
                    val qTailY = cy - 30f * sin(qAngle).toFloat()

                    // Glow effect
                    drawLine(
                        color = IslamicTeal.copy(alpha = 0.25f),
                        start = Offset(qTailX, qTailY),
                        end = Offset(qTipX, qTipY),
                        strokeWidth = 18f,
                        cap = StrokeCap.Round
                    )

                    // Needle
                    drawLine(
                        color = IslamicTeal,
                        start = Offset(qTailX, qTailY),
                        end = Offset(qTipX, qTipY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )

                    // Arrowhead
                    val arrowSize = 18f
                    val qAngleDegrees = Math.toDegrees(qAngle)
                    val arrowLeft = Math.toRadians(qAngleDegrees + 145.0)
                    val arrowRight = Math.toRadians(qAngleDegrees - 145.0)

                    drawLine(
                        color = IslamicTeal,
                        start = Offset(qTipX, qTipY),
                        end = Offset(qTipX + arrowSize * cos(arrowLeft).toFloat(),
                            qTipY + arrowSize * sin(arrowLeft).toFloat()),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = IslamicTeal,
                        start = Offset(qTipX, qTipY),
                        end = Offset(qTipX + arrowSize * cos(arrowRight).toFloat(),
                            qTipY + arrowSize * sin(arrowRight).toFloat()),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )

                    // Kaaba symbol at tip
                    drawCircle(color = IslamicTeal, radius = 8f, center = Offset(qTipX, qTipY))
                    drawCircle(color = Color.Black, radius = 4f, center = Offset(qTipX, qTipY))

                    // Center pivot
                    drawCircle(color = IslamicTeal, radius = 10f, center = Offset(cx, cy))
                    drawCircle(color = Color.Black, radius = 5f, center = Offset(cx, cy))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompassInfoChip(
                    label = "Qibla",
                    value = "${qiblaBearing.toInt()}°",
                    color = IslamicTeal,
                    icon = "🕋"
                )
                CompassInfoChip(
                    label = "West",
                    value = "270°",
                    color = Color(0xFF64B5F6),
                    icon = "⬅"
                )
                CompassInfoChip(
                    label = "Your Direction",
                    value = "${deviceBearing.toInt()}°",
                    color = TextSecondary,
                    icon = "📍"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IslamicTeal.copy(alpha = 0.07f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(IslamicTeal))
                        Text("Green Arrow → Qibla Direction (Kaaba)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF64B5F6)))
                        Text("Blue W → West Direction (for prayer)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF5252)))
                        Text("Red N → North Direction", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CompassInfoChip(label: String, value: String, color: Color, icon: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 16.sp)
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun AnimatedIslamicTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(CardDark)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                tint = IslamicTeal, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text("Islamic Features", color = TextPrimary,
                fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text("Prayer times & Spiritual tools", color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(Icons.Default.Mosque, contentDescription = "Islamic",
            tint = IslamicTeal, modifier = Modifier.size(32.dp))
    }
}

// ─── Date Time Card ───────────────────────────────────────────────────────────

@Composable
fun IslamicDateTimeCard() {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(now.time)
            currentDate = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(now.time)
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(currentTime, color = IslamicTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(currentDate, color = TextSecondary, fontSize = 12.sp)
            }
            Surface(shape = CircleShape, color = IslamicTeal.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null,
                        tint = IslamicTeal, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ─── Prayer Times Card ────────────────────────────────────────────────────────

data class PrayerTime(val name: String, val arabicName: String, val time: String,
                      val icon: String, val isNext: Boolean)

@Composable
fun EnhancedPrayerTimeCard(cityData: CityPrayerTimes) {

    // Determine next prayer based on current time
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
    val nowMinutes = currentHour * 60 + currentMinute

    fun timeToMinutes(t: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            val cal = java.util.Calendar.getInstance()
            cal.time = sdf.parse(t)!!
            cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        } catch (e: Exception) { 0 }
    }

    val rawTimes = listOf(
        Triple("Fajr", "Fajr", cityData.fajr),
        Triple("Dhuhr", "Dhuhr", cityData.dhuhr),
        Triple("Asr", "Asr", cityData.asr),
        Triple("Maghrib", "Maghrib", cityData.maghrib),
        Triple("Isha", "Isha", cityData.isha)
    )

    val icons = listOf("🌙", "☀️", "🌤", "🌅", "🌃")
    var nextIdx = rawTimes.indexOfFirst { timeToMinutes(it.third) > nowMinutes }
    if (nextIdx == -1) nextIdx = 0  // after Isha → next is Fajr

    val prayerTimes = rawTimes.mapIndexed { i, (en, bn, time) ->
        PrayerTime(en, bn, time, icons[i], isNext = i == nextIdx)
    }

    val nextPrayer = prayerTimes[nextIdx]

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
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
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(IslamicTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null,
                            tint = IslamicTeal, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Prayer Times", color = TextPrimary,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(cityData.city, color = IslamicTeal, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = IslamicTeal.copy(alpha = 0.1f)) {
                    Text("Today", color = IslamicTeal, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            // Sunrise info
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1A2A1A),
                modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🌄", fontSize = 16.sp)
                    Text("Sunrise: ${cityData.sunrise}", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Next Prayer Highlight
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = IslamicTeal.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, IslamicTeal.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Next Prayer", color = TextSecondary, fontSize = 11.sp)
                        Text("${nextPrayer.icon} ${nextPrayer.name}",
                            color = IslamicTeal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(nextPrayer.time, color = IslamicTeal, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            prayerTimes.forEach { prayer ->
                EnhancedPrayerTimeRow(
                    name = "${prayer.icon} ${prayer.name}",
                    subName = prayer.arabicName,
                    time = prayer.time,
                    isActive = prayer.isNext
                )
            }
        }
    }
}

@Composable
fun EnhancedPrayerTimeRow(name: String, subName: String, time: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                .background(if (isActive) IslamicTeal else TextSecondary.copy(alpha = 0.4f)))
            Column {
                Text(name, color = if (isActive) TextPrimary else TextSecondary,
                    fontSize = 15.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                Text(subName, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
        Text(time, color = if (isActive) IslamicTeal else TextSecondary,
            fontSize = 15.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─── Azan Reminder Card ───────────────────────────────────────────────────────

@Composable
fun EnhancedAzanReminderCard(azanReminder: Boolean, onReminderChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (azanReminder) IslamicTeal.copy(alpha = 0.15f) else ChipBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null,
                        tint = if (azanReminder) IslamicTeal else TextSecondary,
                        modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("Azan Reminder",
                        color = if (azanReminder) IslamicTeal else TextPrimary,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (azanReminder) "Prayer notifications ON" else "Enable to get prayer alerts",
                        color = TextSecondary, fontSize = 11.sp)
                }
            }
            Switch(
                checked = azanReminder,
                onCheckedChange = onReminderChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = IslamicTeal,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ChipBg
                )
            )
        }
    }
}

// ─── Prayer Mode Card ─────────────────────────────────────────────────────────

@Composable
fun EnhancedPrayerModeCard(prayerModeEnabled: Boolean, onPrayerModeClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(IslamicTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mosque, contentDescription = null,
                        tint = IslamicTeal, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("Prayer Mode",
                        color = if (prayerModeEnabled) IslamicTeal else TextPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Smart home automation for prayer",
                        color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IslamicTeal.copy(alpha = 0.08f))
            ) {
                Text(
                    "• Light dim to 30% brightness\n• Fan turned OFF for silence\n• Perfect environment for prayer",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPrayerModeClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (prayerModeEnabled) IslamicTeal.copy(alpha = 0.2f) else IslamicTeal,
                    contentColor = if (prayerModeEnabled) IslamicTeal else Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                enabled = !prayerModeEnabled
            ) {
                Icon(
                    if (prayerModeEnabled) Icons.Default.CheckCircle else Icons.Default.LightMode,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (prayerModeEnabled) "Prayer Mode Active" else "Activate Prayer Mode",
                    fontWeight = FontWeight.Bold
                )
            }

            if (prayerModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IslamicTeal.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null,
                                tint = IslamicTeal, modifier = Modifier.size(20.dp))
                            Text("💡 Put your phone on silent during prayer time.",
                                color = IslamicTeal, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Islamic Quotes ───────────────────────────────────────────────────────────

@Composable
fun IslamicQuotesSection() {
    val quotes = listOf(
        "🤲 \"Indeed, prayer prohibits immorality and wrongdoing\" - Quran 29:45",
        "🌟 \"The best among you are those who have the best character\"",
        "💚 \"Indeed, with hardship comes ease\" - Quran 94:6",
        "🕋 \"Remember your Lord within yourself\" - Quran 7:205"
    )

    var currentQuote by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000)
            currentQuote = (currentQuote + 1) % quotes.size
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Daily Reminder", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = currentQuote,
                transitionSpec = {
                    fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
                }
            ) { index ->
                Text(
                    quotes[index],
                    color = IslamicTeal,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Toast ────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.IslamicCustomToast(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.contains("✓")) GreenAccent else IslamicTeal
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
                Text(message, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}