package nz.kaliscope.tagdemo.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tracks fares charged within a "transit day" and caps further charges once
 * a daily limit is reached, mirroring fare-capping schemes on real smartcards.
 * The transit day rolls over at 4am rather than midnight, since late-night
 * trips should count toward the day they started.
 */
class DailyCapTracker(
    private val dailyCapCents: Long,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) {
    private val totalsByDay = mutableMapOf<Long, Long>()

    private fun transitDayKey(nowMillis: Long): Long {
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val shifted = if (zoned.hour < 4) zoned.minusDays(1) else zoned
        return shifted.toLocalDate().toEpochDay()
    }

    /** Returns the amount that should actually be charged, and whether the cap kicked in. */
    fun chargeableAmount(nowMillis: Long, proposedFareCents: Long): Pair<Long, Boolean> {
        val dayKey = transitDayKey(nowMillis)
        val spentToday = totalsByDay[dayKey] ?: 0L
        val remainingBeforeCap = (dailyCapCents - spentToday).coerceAtLeast(0L)
        val chargeable = proposedFareCents.coerceAtMost(remainingBeforeCap)
        val capped = chargeable < proposedFareCents
        return chargeable to capped
    }

    fun recordCharge(nowMillis: Long, amountCents: Long) {
        val dayKey = transitDayKey(nowMillis)
        totalsByDay[dayKey] = (totalsByDay[dayKey] ?: 0L) + amountCents
    }
}
