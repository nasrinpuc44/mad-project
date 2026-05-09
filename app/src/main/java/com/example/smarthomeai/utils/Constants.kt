package com.example.smarthomeai.utils

object Constants {
    const val APP_VERSION = "1.0.0"

    // Realtime Database Paths
    const val RTDB_USERS = "users"
    const val RTDB_DEVICES = "devices"
    const val RTDB_STATUS = "status"
    const val RTDB_EMERGENCY = "emergency"
    const val RTDB_ALERTS = "alerts"

    // Cloud Firestore Collections
    const val FS_USERS = "users"
    const val FS_ACTIVITY_LOG = "activity_log"

    // Device Limits
    const val MAX_LIGHT_BRIGHTNESS = 100
    const val MIN_TEMPERATURE = 16
    const val MAX_TEMPERATURE = 30

    // Validation
    val VALID_FAN_SPEEDS = listOf("Low", "Medium", "High")
}