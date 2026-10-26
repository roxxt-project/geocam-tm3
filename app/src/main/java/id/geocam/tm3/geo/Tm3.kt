package id.geocam.tm3.geo

import kotlin.math.atan2
import kotlin.math.atanh
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** A TM-3 zone, e.g. "48.2" with central meridian 106.5°E. */
data class Tm3Zone(val name: String, val centralMeridian: Double)

/** A projected TM-3 coordinate in metres. */
data class Tm3Point(val zone: Tm3Zone, val easting: Double, val northing: Double)

/**
 * Indonesian TM-3 projection (BPN): Transverse Mercator, 3° zones,
 * k0 = 0.9999, false easting 200 000 m, false northing 1 500 000 m, WGS84 ellipsoid.
 *
 * Uses the 6th-order Krüger series (Karney 2011): sub-millimetre accurate within a zone.
 */
object Tm3 {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9999
    private const val FALSE_EASTING = 200_000.0
    private const val FALSE_NORTHING = 1_500_000.0

    /** Westmost and eastmost longitudes covered by the 16 zones (46.2 to 54.1). */
    const val MIN_LON = 93.0
    const val MAX_LON = 141.0

    private val e = sqrt(F * (2 - F))
    private val rectifyingRadius: Double
    private val alpha: DoubleArray

    init {
        val n = F / (2 - F)
        val n2 = n * n
        val n3 = n2 * n
        val n4 = n3 * n
        val n5 = n4 * n
        val n6 = n5 * n
        rectifyingRadius = A / (1 + n) * (1 + n2 / 4 + n4 / 64 + n6 / 256)
        alpha = doubleArrayOf(
            n / 2 - 2.0 / 3 * n2 + 5.0 / 16 * n3 + 41.0 / 180 * n4 - 127.0 / 288 * n5 + 7891.0 / 37800 * n6,
            13.0 / 48 * n2 - 3.0 / 5 * n3 + 557.0 / 1440 * n4 + 281.0 / 630 * n5 - 1983433.0 / 1935360 * n6,
            61.0 / 240 * n3 - 103.0 / 140 * n4 + 15061.0 / 26880 * n5 + 167603.0 / 181440 * n6,
            49561.0 / 161280 * n4 - 179.0 / 168 * n5 + 6601661.0 / 7257600 * n6,
            34729.0 / 80640 * n5 - 3418889.0 / 1995840 * n6,
            212378941.0 / 319334400 * n6,
        )
    }

    /** Zone for a longitude, or null outside Indonesia's TM-3 coverage. */
    fun zoneFor(longitude: Double): Tm3Zone? {
        if (longitude < MIN_LON || longitude > MAX_LON) return null
        val index = min(floor((longitude - MIN_LON) / 3).toInt(), 15)
        val cm = 94.5 + 3 * index
        // Name = UTM zone number + ".1" (west half) or ".2" (east half) of that UTM zone.
        val utm = floor((cm + 180) / 6).toInt() + 1
        val utmCm = utm * 6 - 183.0
        return Tm3Zone("$utm.${if (cm < utmCm) 1 else 2}", cm)
    }

    /** Projects WGS84 latitude/longitude (degrees). Returns null outside TM-3 coverage. */
    fun project(latitude: Double, longitude: Double): Tm3Point? {
        val zone = zoneFor(longitude) ?: return null
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude - zone.centralMeridian)
        val s = sin(phi)
        val t = sinh(atanh(s) - e * atanh(e * s))
        val xiPrime = atan2(t, cos(lambda))
        val etaPrime = atanh(sin(lambda) / sqrt(1 + t * t))
        var xi = xiPrime
        var eta = etaPrime
        for (j in alpha.indices) {
            val k = 2.0 * (j + 1)
            xi += alpha[j] * sin(k * xiPrime) * cosh(k * etaPrime)
            eta += alpha[j] * cos(k * xiPrime) * sinh(k * etaPrime)
        }
        return Tm3Point(
            zone = zone,
            easting = FALSE_EASTING + K0 * rectifyingRadius * eta,
            northing = FALSE_NORTHING + K0 * rectifyingRadius * xi,
        )
    }
}
