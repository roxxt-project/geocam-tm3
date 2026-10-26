package id.geocam.tm3.stamp

import android.location.Location

/** Everything printed on one photo, captured at shutter time. */
data class StampInput(
    val timeMillis: Long,
    val location: Location?,
    val address: String?,
    val note: String,
    val decimalComma: Boolean,
)

object StampText {
    const val LAT_PREFIX = "Lat : "
    const val LONG_PREFIX = "Long: "

    /** Lines in print order. The coordinate lines are highlighted by the renderer. */
    fun lines(input: StampInput): List<String> {
        val lines = mutableListOf(Format.dateTime(input.timeMillis))
        val location = input.location
        if (location == null) {
            lines += "Lokasi belum tersedia"
        } else {
            lines += LAT_PREFIX + "${Format.degrees(location.latitude, input.decimalComma)}"
            lines += LONG_PREFIX + "${Format.degrees(location.longitude, input.decimalComma)}"
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
        return "Lat/Long " + Format.latLon(location.latitude, location.longitude)
    }
}
