package id.geocam.tm3.stamp

import android.location.Location
import id.geocam.tm3.geo.Tm3

/** Everything printed on one photo, captured at shutter time. */
data class StampInput(
    val timeMillis: Long,
    val location: Location?,
    val address: String?,
    val note: String,
    val decimalComma: Boolean,
)

object StampText {
    const val ZONE_PREFIX = "TM-3 Zona "

    /** Lines in print order. The TM-3 zone line is highlighted by the renderer. */
    fun lines(input: StampInput): List<String> {
        val lines = mutableListOf(Format.dateTime(input.timeMillis))
        val location = input.location
        if (location == null) {
            lines += "Lokasi belum tersedia"
        } else {
            val point = Tm3.project(location.latitude, location.longitude)
            if (point == null) {
                lines += "Di luar cakupan TM-3"
            } else {
                lines += ZONE_PREFIX + point.zone.name
                lines += "X (E): ${Format.metres(point.easting, input.decimalComma)} m"
                lines += "Y (N): ${Format.metres(point.northing, input.decimalComma)} m"
            }
            lines += "Lat/Long: ${Format.latLon(location.latitude, location.longitude)}"
            val accuracy = if (location.hasAccuracy()) Format.accuracy(location.accuracy, input.decimalComma) else "-"
            lines += "Akurasi: $accuracy" + if (location.isMockCompat()) "  [LOKASI PALSU]" else ""
        }
        input.address?.takeIf { it.isNotBlank() }?.let { lines += it }
        input.note.trim().takeIf { it.isNotEmpty() }?.let { lines += "Catatan: $it" }
        return lines
    }

    /** One-line summary stored in the EXIF image description. */
    fun summary(input: StampInput): String {
        val location = input.location ?: return "GeoCam TM-3"
        val point = Tm3.project(location.latitude, location.longitude) ?: return "GeoCam TM-3"
        return String.format(
            java.util.Locale.US,
            "TM-3 %s X=%.2f Y=%.2f",
            point.zone.name, point.easting, point.northing,
        )
    }
}
