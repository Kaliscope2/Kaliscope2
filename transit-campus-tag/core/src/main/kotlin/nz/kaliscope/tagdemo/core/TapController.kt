package nz.kaliscope.tagdemo.core

/** What a reader tap actually resolves to, before it gets serialized to APDU bytes. */
sealed class TapResponse {
    data class Status(val status: AccountStatus, val campusBlocked: Boolean) : TapResponse()
    data class TagOnResponse(val result: TagOnResult) : TapResponse()
    data class TagOffResponse(val result: TagOffResult) : TapResponse()
    data class CampusAccessResponse(val result: AccessResult) : TapResponse()
    object Ack : TapResponse()
    object Unsupported : TapResponse()
}

/**
 * Bridges a parsed reader command to the domain logic. Used by both the HCE
 * service (phone acting as the card) and, in tests, directly — so the same
 * logic that will run on a real device is exercised on the JVM.
 */
class TapController(private val credential: UnifiedCredential) {

    fun handle(command: Apdu.ParsedCommand, nowMillis: Long): TapResponse = when (command) {
        is Apdu.ParsedCommand.Select -> TapResponse.Ack

        is Apdu.ParsedCommand.GetStatus -> TapResponse.Status(
            status = credential.transit.status(nowMillis),
            campusBlocked = credential.campus.isBlocked,
        )

        is Apdu.ParsedCommand.TagOn -> TapResponse.TagOnResponse(
            credential.transit.tagOn(command.mode, command.stopId, nowMillis),
        )

        is Apdu.ParsedCommand.TagOff -> TapResponse.TagOffResponse(
            credential.transit.tagOff(command.stopId, nowMillis),
        )

        is Apdu.ParsedCommand.CampusAccess -> TapResponse.CampusAccessResponse(
            credential.campus.requestAccess(command.doorId, nowMillis),
        )

        is Apdu.ParsedCommand.ReportLost -> {
            credential.reportLostOrStolen()
            TapResponse.Ack
        }

        is Apdu.ParsedCommand.Unknown -> TapResponse.Unsupported
    }
}
