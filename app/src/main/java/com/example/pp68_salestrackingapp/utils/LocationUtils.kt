package com.example.pp68_salestrackingapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Looper

/**
 * แทนที่ FusedLocationProviderClient (Play Services) ด้วย android.location.LocationManager
 * ของ Android เอง — ไม่ต้องพึ่ง Google Play Services
 */
@SuppressLint("MissingPermission")
fun fetchCurrentLocation(context: Context, onResult: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { onResult(it.latitude, it.longitude) }
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        locationManager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
            if (location != null) {
                onResult(location.latitude, location.longitude)
            } else {
                locationManager.getLastKnownLocation(provider)?.let { onResult(it.latitude, it.longitude) }
            }
        }
    } else {
        val lastKnown = locationManager.getLastKnownLocation(provider)
        if (lastKnown != null) {
            onResult(lastKnown.latitude, lastKnown.longitude)
        } else {
            locationManager.requestSingleUpdate(provider, { location ->
                onResult(location.latitude, location.longitude)
            }, Looper.getMainLooper())
        }
    }
}

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a = Math.sin(deltaPhi / 2).let { it * it } +
            Math.cos(phi1) * Math.cos(phi2) *
            Math.sin(deltaLambda / 2).let { it * it }
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return r * c
}
