package id.geocam.tm3.stamp

import android.location.Location
import android.os.Build
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Number and date formatting shared by the live overlay and the photo stamp. */
object Format {
    private val indonesian: Locale = Locale.forLanguageTag("id-ID")
    private const val HOUR_MS = 3_600_000

    /** 236.205,15 (Indonesian) or 236205.15 (CAD/GIS friendly). */
    fun metres(value: Double, decimalComma: Boolean): String =
        if (decimalComma) DecimalFormat("#,##0.00", DecimalFormatSymbols(indonesian)).format(value)
        else String.format(Locale.US, "%.2f", value)

    fun accuracy(metres: Float, decimalComma: Boolean): String =
        "±" + String.format(if (decimalComma) indonesian else Locale.US, "%.1f", metres) + " m"

    /** Lat/long always use a dot so the comma can separate the pair. */
    fun latLon(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    /** "Sen, 26 Okt 2026  19:18:42 WIB" in the device time zone. */
    fun dateTime(millis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("EEE, d MMM yyyy  HH:mm:ss", indonesian)
        format.timeZone = zone
        return format.format(Date(millis)) + " " + zoneLabel(zone, millis)
    }

    fun zoneLabel(zone: TimeZone, millis: Long): String =
        when (val offset = zone.getOffset(millis)) {
            7 * HOUR_MS -> "WIB"
            8 * HOUR_MS -> "WITA"
            9 * HOUR_MS -> "WIT"
            else -> {
                val minutes = offset / 60_000
                String.format(Locale.US, "UTC%s%02d:%02d", if (minutes < 0) "-" else "+", abs(minutes) / 60, abs(minutes) % 60)
            }
        }
}

/** True when the fix comes from a mock-location app. */
fun Location.isMockCompat(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock
    else @Suppress("DEPRECATION") isFromMockProvider
