package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.example.data.model.ConstructionSite
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

data class GpsLocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isMock: Boolean = false
)

data class SiteProximityResult(
    val site: ConstructionSite,
    val distanceMeters: Float,
    val isWithinGeofence: Boolean,
    val accuracyMeters: Float
)

class GpsLocationManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000L): Flow<GpsLocationData?> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // Try getting last known location first for immediate UI display
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                trySend(
                    GpsLocationData(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracy = it.accuracy,
                        altitude = it.altitude,
                        timestamp = it.time
                    )
                )
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(true)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    GpsLocationData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        altitude = loc.altitude,
                        timestamp = loc.time
                    )
                )
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            // Fallback to LocationManager if Google Play Services fails
            fallbackLocationManager(trySend = { trySend(it) })
        }

        awaitClose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager(trySend: (GpsLocationData?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            if (lm.isProviderEnabled(provider)) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    trySend(
                        GpsLocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            altitude = loc.altitude,
                            timestamp = loc.time
                        )
                    )
                    break
                }
            }
        }
    }

    companion object {
        fun computeDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }

        fun checkSiteProximity(
            currentLocation: GpsLocationData,
            site: ConstructionSite
        ): SiteProximityResult {
            val distance = computeDistanceMeters(
                currentLocation.latitude,
                currentLocation.longitude,
                site.latitude,
                site.longitude
            )
            return SiteProximityResult(
                site = site,
                distanceMeters = distance,
                isWithinGeofence = distance <= site.geofenceRadiusMeters,
                accuracyMeters = currentLocation.accuracy
            )
        }

        fun formatDistance(meters: Float): String {
            return if (meters < 1000f) {
                "${meters.roundToInt()} m"
            } else {
                String.format(java.util.Locale.US, "%.2f km", meters / 1000f)
            }
        }
    }
}
