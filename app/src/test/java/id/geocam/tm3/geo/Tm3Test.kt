package id.geocam.tm3.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reference values from PROJ: +proj=tmerc +k=0.9999 +x_0=200000 +y_0=1500000 +ellps=WGS84. */
class Tm3Test {

    private data class Case(val lat: Double, val lon: Double, val zone: String, val e: Double, val n: Double)

    private val cases = listOf(
        Case(-6.175392, 106.827153, "48.2", 236205.1533, 817191.1781), // Jakarta
        Case(3.595196, 98.672226, "47.1", 330232.5581, 1897585.2237), // Medan
        Case(-8.65, 115.216667, "50.1", 168818.9068, 543543.9277), // Denpasar
        Case(-0.502106, 117.153709, "50.2", 50138.3922, 1444470.0990), // Samarinda
        Case(-2.533333, 140.716667, "54.1", 335303.9349, 1219841.2132), // Jayapura
        Case(5.54829, 95.323753, "46.2", 291266.9919, 2113519.4543), // Banda Aceh
        Case(-5.147665, 119.432732, "50.2", 303409.4452, 930766.7113), // Makassar
        Case(-0.789275, 113.921327, "49.2", 358206.9959, 1412708.1253), // Palangka Raya
    )

    @Test
    fun matchesProjWithinOneMillimetre() {
        for (c in cases) {
            val p = Tm3.project(c.lat, c.lon)!!
            assertEquals(c.zone, p.zone.name)
            assertEquals(c.e, p.easting, 0.001)
            assertEquals(c.n, p.northing, 0.001)
        }
    }

    @Test
    fun allSixteenZoneNames() {
        val names = (0 until 16).map { Tm3.zoneFor(94.5 + 3 * it)!!.name }
        assertEquals(
            listOf(
                "46.2", "47.1", "47.2", "48.1", "48.2", "49.1", "49.2", "50.1",
                "50.2", "51.1", "51.2", "52.1", "52.2", "53.1", "53.2", "54.1",
            ),
            names,
        )
    }

    @Test
    fun outsideCoverageIsNull() {
        assertNull(Tm3.zoneFor(92.9))
        assertNull(Tm3.zoneFor(141.1))
        assertEquals("54.1", Tm3.zoneFor(141.0)!!.name)
    }
}
