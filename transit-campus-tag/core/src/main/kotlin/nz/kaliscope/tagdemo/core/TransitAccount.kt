package nz.kaliscope.tagdemo.core

/**
 * Tag-on/tag-off state machine for one rider.
 *
 * Design notes (deliberately mirroring real smartcard behaviour):
 *  - Balance is only checked (not deducted) at tag-on; the fare is deducted at
 *    tag-off once the actual distance/zones travelled are known.
 *  - Tagging on again while already tagged on, within [doubleTapWindowMillis],
 *    is treated as an accidental double-tap and cancels the open trip for free.
 *  - Tagging on again after that window, but before the trip is stale, closes
 *    the previous trip as "incomplete" and charges the mode's maximum fare —
 *    this is what happens when a rider forgets to tag off.
 *  - A blocked account (e.g. reported lost/stolen) can never start a new
 *    trip, but can still tag off to close out a trip already in progress.
 */
class TransitAccount(
    initialBalanceCents: Long,
    dailyCapCents: Long = 1600L,
    private val doubleTapWindowMillis: Long = 60_000L,
) {
    var balanceCents: Long = initialBalanceCents
        private set

    var isBlocked: Boolean = false
        private set

    private var currentTrip: Trip? = null
    private val history = mutableListOf<TripSummary>()
    private val capTracker = DailyCapTracker(dailyCapCents)

    fun block() {
        isBlocked = true
    }

    fun unblock() {
        isBlocked = false
    }

    fun tripHistory(): List<TripSummary> = history.toList()

    // nowMillis kept for symmetry with tagOn/tagOff, which are time-sensitive.
    fun status(@Suppress("UNUSED_PARAMETER") nowMillis: Long): AccountStatus = AccountStatus(
        balanceCents = balanceCents,
        isBlocked = isBlocked,
        tagState = if (currentTrip != null) TagState.TAGGED_ON else TagState.NOT_TAGGED,
        currentTrip = currentTrip,
    )

    fun tagOn(mode: TransportMode, stopId: String, nowMillis: Long): TagOnResult {
        if (isBlocked) return TagOnResult.Denied(DenialReason.ACCOUNT_BLOCKED)

        val open = currentTrip
        if (open != null) {
            val elapsed = nowMillis - open.entryTimeMillis
            if (elapsed <= doubleTapWindowMillis) {
                currentTrip = null
                return TagOnResult.Cancelled(open)
            }
            val closedSummary = closeTripAsIncomplete(open, nowMillis)
            return startTripOrDeny(mode, stopId, nowMillis, closedSummary)
        }

        return startTripOrDeny(mode, stopId, nowMillis, autoClosedPreviousTrip = null)
    }

    private fun startTripOrDeny(
        mode: TransportMode,
        stopId: String,
        nowMillis: Long,
        autoClosedPreviousTrip: TripSummary?,
    ): TagOnResult {
        val requiredHold = FareTable.baseFare(mode)
        if (balanceCents < requiredHold) {
            return TagOnResult.Denied(DenialReason.INSUFFICIENT_BALANCE)
        }
        currentTrip = Trip(mode, stopId, nowMillis)
        return TagOnResult.Success(holdCents = requiredHold, autoClosedPreviousTrip = autoClosedPreviousTrip)
    }

    fun tagOff(stopId: String, nowMillis: Long): TagOffResult {
        val open = currentTrip ?: return TagOffResult.Denied(DenialReason.NOT_TAGGED_ON)

        val rawFare = FareTable.calculateFare(open.mode, open.entryStopId, stopId)
        val (chargeable, capped) = capTracker.chargeableAmount(nowMillis, rawFare)
        val actualCharge = chargeable.coerceAtMost(balanceCents)
        val insufficientFunds = actualCharge < chargeable

        balanceCents -= actualCharge
        capTracker.recordCharge(nowMillis, actualCharge)
        if (insufficientFunds) {
            isBlocked = true
        }

        history.add(
            TripSummary(
                mode = open.mode,
                entryStopId = open.entryStopId,
                exitStopId = stopId,
                entryTimeMillis = open.entryTimeMillis,
                exitTimeMillis = nowMillis,
                fareCents = actualCharge,
                incomplete = false,
            ),
        )
        currentTrip = null

        return TagOffResult.Success(
            fareCents = actualCharge,
            newBalanceCents = balanceCents,
            capped = capped,
            insufficientFunds = insufficientFunds,
        )
    }

    private fun closeTripAsIncomplete(open: Trip, nowMillis: Long): TripSummary {
        val penalty = FareTable.maxFare(open.mode)
        val (chargeable, _) = capTracker.chargeableAmount(nowMillis, penalty)
        val actualCharge = chargeable.coerceAtMost(balanceCents)
        if (actualCharge < chargeable) {
            isBlocked = true
        }

        balanceCents -= actualCharge
        capTracker.recordCharge(nowMillis, actualCharge)

        val summary = TripSummary(
            mode = open.mode,
            entryStopId = open.entryStopId,
            exitStopId = null,
            entryTimeMillis = open.entryTimeMillis,
            exitTimeMillis = nowMillis,
            fareCents = actualCharge,
            incomplete = true,
        )
        history.add(summary)
        currentTrip = null
        return summary
    }
}
