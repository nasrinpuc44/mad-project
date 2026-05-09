package com.example.smarthomeai.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

object LogHelper {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "LogHelper"

    fun addLog(
        action: String,
        details: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No user logged in, cannot save log")
            return
        }

        val logEntry = hashMapOf<String, Any>(
            "action" to action,
            "details" to details,
            "timestamp" to System.currentTimeMillis(),
            "date" to Date(),
            "appVersion" to Constants.APP_VERSION
        )
        logEntry.putAll(additionalData)

        firestore.collection(Constants.FS_USERS)
            .document(userId)
            .collection(Constants.FS_ACTIVITY_LOG)
            .add(logEntry)
            .addOnSuccessListener {
                Log.d(TAG, "Activity logged to Firestore: $action")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error logging activity: ${e.message}")
            }
    }

    fun getDeviceStatusRef() = FirebaseDatabase.getInstance()
        .getReference(Constants.RTDB_USERS)
        .child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
        .child(Constants.RTDB_DEVICES)
        .child(Constants.RTDB_STATUS)

    fun updateDevice(
        key: String,
        value: Any,
        onComplete: (Boolean, String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(false, "User not logged in")
            return
        }

        val dbRef = FirebaseDatabase.getInstance()
            .getReference(Constants.RTDB_USERS)
            .child(userId)
            .child(Constants.RTDB_DEVICES)
            .child(Constants.RTDB_STATUS)

        dbRef.child(key).setValue(value).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                addLog(
                    action = "device_update",
                    details = "$key changed to $value",
                    additionalData = mapOf(
                        "device" to key,
                        "newValue" to value.toString()
                    )
                )
                onComplete(true, "✓ $key updated")
            } else {
                onComplete(false, "✗ Failed to update $key")
            }
        }
    }

    fun applyMode(
        modeName: String,
        settings: Map<String, Any>,
        onComplete: (Boolean, String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(false, "User not logged in")
            return
        }

        val dbRef = FirebaseDatabase.getInstance()
            .getReference(Constants.RTDB_USERS)
            .child(userId)
            .child(Constants.RTDB_DEVICES)
            .child(Constants.RTDB_STATUS)

        dbRef.updateChildren(settings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                addLog(
                    action = "mode_activated",
                    details = "$modeName activated",
                    additionalData = mapOf(
                        "mode" to modeName,
                        "settings" to settings.mapValues { it.value.toString() }
                    )
                )
                onComplete(true, "✓ $modeName activated successfully!")
            } else {
                onComplete(false, "✗ Failed to activate mode")
            }
        }
    }

    suspend fun getActivityLogs(
        limit: Int = 100,
        filterAction: String? = null
    ): List<Map<String, Any>> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return emptyList()

        return try {
            var query = firestore.collection(Constants.FS_USERS)
                .document(userId)
                .collection(Constants.FS_ACTIVITY_LOG)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())

            if (filterAction != null && filterAction != "all") {
                query = query.whereEqualTo("action", filterAction)
            }

            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                data.toMutableMap().apply {
                    put("logId", doc.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting logs: ${e.message}")
            emptyList()
        }
    }

    fun deleteLog(logId: String, onComplete: (Boolean, String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(false, "User not logged in")
            return
        }

        firestore.collection(Constants.FS_USERS)
            .document(userId)
            .collection(Constants.FS_ACTIVITY_LOG)
            .document(logId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Log deleted successfully: $logId")
                onComplete(true, "✓ Log deleted successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting log: ${e.message}")
                onComplete(false, "✗ Failed to delete log: ${e.message}")
            }
    }

    fun deleteAllLogs(onComplete: (Boolean, String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(false, "User not logged in")
            return
        }

        firestore.collection(Constants.FS_USERS)
            .document(userId)
            .collection(Constants.FS_ACTIVITY_LOG)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    onComplete(true, "✓ All logs cleared")
                }.addOnFailureListener { e ->
                    onComplete(false, "✗ Failed to clear logs: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                onComplete(false, "✗ Failed to clear logs: ${e.message}")
            }
    }
}