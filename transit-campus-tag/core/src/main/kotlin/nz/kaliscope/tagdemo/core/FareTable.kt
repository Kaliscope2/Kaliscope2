package nz.kaliscope.tagdemo.core

import kotlin.math.abs

/**
 * Demo zone-based fare calculator. Not a real operator's fare structure —
 * just enough logic to exercise tag-on/tag-off, zone pricing and capping.
 */
object FareTable {
    private val stopZones: Map<String, Int> = mapOf(
        "STOP_A" to 1,
        "STOP_B" to 1,
        "STOP_C" to 2,
        "STOP_D" to 3,
        "STOP_E" to 4,
    )

    fun zoneOf(stopId: String): Int = stopZones[stopId] ?: 1

    fun baseFare(mode: TransportMode): Long = when (mode) {
        TransportMode.BUS -> 220L
        TransportMode.TRAIN -> 260L
    }

    private fun perZoneFare(mode: TransportMode): Long = when (mode) {
        TransportMode.BUS -> 90L
        TransportMode.TRAIN -> 130L
    }

    /** Charged when a trip is auto-closed without a matching tag-off. */
    fun maxFare(mode: TransportMode): Long = when (mode) {
        TransportMode.BUS -> 700L
        TransportMode.TRAIN -> 1100L
    }

    fun calculateFare(mode: TransportMode, entryStopId: String, exitStopId: String): Long {
        val zonesCrossed = abs(zoneOf(exitStopId) - zoneOf(entryStopId))
        val fare = baseFare(mode) + zonesCrossed * perZoneFare(mode)
        return fare.coerceAtMost(maxFare(mode))
    }
}
