package id.geocam.tm3.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/**
 * Reverse geocoding through the phone's built-in Geocoder service.
 * The app itself has no INTERNET permission; when the service cannot answer, the address is skipped.
 */
object AddressLookup {
    /** Blocking. Call from a background thread. */
    fun lookup(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.forLanguageTag("id-ID"))
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return null
            listOfNotNull(address.subLocality, address.locality, address.subAdminArea)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
