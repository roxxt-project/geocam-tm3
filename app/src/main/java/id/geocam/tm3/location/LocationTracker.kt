package id.geocam.tm3.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Streams location fixes about once a second.
 * Uses Google's fused provider when Play Services exist, otherwise plain GNSS/network.
 */
class LocationTracker(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private var fusedCallback: LocationCallback? = null
    private var listener: LocationListener? = null
    private var best: Location? = null

    fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(manager)

    @SuppressLint("MissingPermission") // Callers start tracking only after permission is granted.
    fun start(onUpdate: (Location) -> Unit) {
        stop()
        if (hasPlayServices()) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(onUpdate)
                }
            }
            fusedCallback = callback
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            // Show a recent cached fix immediately; never a stale one from another place.
            fused.lastLocation.addOnSuccessListener { last ->
                if (last != null && ageMillis(last) < 60_000) onUpdate(last)
            }
        } else {
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (isBetter(location)) {
                        best = location
                        onUpdate(location)
                    }
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}

                @Deprecated("Deprecated in Android API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            listener = l
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                runCatching {
                    if (manager.isProviderEnabled(provider)) {
                        manager.requestLocationUpdates(provider, 1_000L, 0f, l, Looper.getMainLooper())
                    }
                }
            }
        }
    }

    fun stop() {
        fusedCallback?.let { fused.removeLocationUpdates(it) }
        fusedCallback = null
        listener?.let { manager.removeUpdates(it) }
        listener = null
        best = null
    }

    /** Prefer the most accurate fix, but never hold on to one older than 5 seconds. */
    private fun isBetter(candidate: Location): Boolean {
        val current = best ?: return true
        val newerByMs = (candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos) / 1_000_000
        return newerByMs > 5_000 || candidate.accuracy <= current.accuracy
    }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun hasPlayServices(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
}
