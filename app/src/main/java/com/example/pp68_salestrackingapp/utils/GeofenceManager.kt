package com.example.pp68_salestrackingapp.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.pp68_salestrackingapp.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun addGeofenceForActivity(
        activityId: String,
        companyName: String,
        lat: Double,
        lng: Double,
        plannedDate: String = "",
        plannedTime: String = "",
        radiusMeters: Float = 500f
    ) {
        if (!hasLocationPermission()) {
            Log.w("GeofenceManager", "ไม่มีสิทธิ์ใช้งานพิกัดตำแหน่งเพื่อตั้ง Geofence")
            return
        }

        val requestId = "ACT_${activityId}|${companyName}|${plannedDate}|${plannedTime}"

        val geofence = Geofence.Builder()
            .setRequestId(requestId)
            .setCircularRegion(lat, lng, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(10000) // 10 seconds loitering for DWELL
            .setNotificationResponsiveness(5000) // 5 seconds responsiveness
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, getGeofencePendingIntent())
            .addOnSuccessListener {
                Log.d("GeofenceManager", "เพิ่ม Geofence สำเร็จสำหรับกิจกรรม $activityId ที่ $companyName ($lat, $lng)")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "เพิ่ม Geofence ไม่สำเร็จ: ${e.message}", e)
            }
    }

    fun removeGeofenceForActivity(activityIdPrefix: String) {
        geofencingClient.removeGeofences(listOf(activityIdPrefix))
            .addOnSuccessListener {
                Log.d("GeofenceManager", "ลบ Geofence $activityIdPrefix สำเร็จ")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "ลบ Geofence ไม่สำเร็จ: ${e.message}")
            }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation
    }
}
