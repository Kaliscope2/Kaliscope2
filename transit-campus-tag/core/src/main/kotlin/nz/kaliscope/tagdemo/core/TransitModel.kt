package nz.kaliscope.tagdemo.core

enum class TransportMode { BUS, TRAIN }

data class Trip(
    val mode: TransportMode,
    val entryStopId: String,
    val entryTimeMillis: Long,
)

data class TripSummary(
    val mode: TransportMode,
    val entryStopId: String,
    val exitStopId: String?,
    val entryTimeMillis: Long,
    val exitTimeMillis: Long,
    val fareCents: Long,
    val incomplete: Boolean,
)

data class AccountStatus(
    val balanceCents: Long,
    val isBlocked: Boolean,
    val tagState: TagState,
    val currentTrip: Trip?,
)

enum class TagState { NOT_TAGGED, TAGGED_ON }

enum class DenialReason { INSUFFICIENT_BALANCE, NOT_TAGGED_ON, ACCOUNT_BLOCKED }

sealed class TagOnResult {
    data class Success(val holdCents: Long, val autoClosedPreviousTrip: TripSummary?) : TagOnResult()
    data class Cancelled(val cancelledTrip: Trip) : TagOnResult()
    data class Denied(val reason: DenialReason) : TagOnResult()
}

sealed class TagOffResult {
    data class Success(val fareCents: Long, val newBalanceCents: Long, val capped: Boolean, val insufficientFunds: Boolean) :
        TagOffResult()
    data class Denied(val reason: DenialReason) : TagOffResult()
}
