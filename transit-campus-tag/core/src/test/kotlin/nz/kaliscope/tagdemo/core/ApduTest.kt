package nz.kaliscope.tagdemo.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApduTest {

    @Test
    fun `select command parses as Select`() {
        val cmd = Apdu.buildSelectCommand()
        assertIs<Apdu.ParsedCommand.Select>(Apdu.parseCommand(cmd))
    }

    @Test
    fun `tag on command round trips through parse`() {
        val cmd = Apdu.buildTagOnCommand(TransportMode.TRAIN, "STOP_C")
        val parsed = Apdu.parseCommand(cmd)
        assertIs<Apdu.ParsedCommand.TagOn>(parsed)
        assertEquals(TransportMode.TRAIN, parsed.mode)
        assertEquals("STOP_C", parsed.stopId)
    }

    @Test
    fun `tag off command round trips through parse`() {
        val cmd = Apdu.buildTagOffCommand("STOP_E")
        val parsed = Apdu.parseCommand(cmd)
        assertIs<Apdu.ParsedCommand.TagOff>(parsed)
        assertEquals("STOP_E", parsed.stopId)
    }

    @Test
    fun `campus access command round trips through parse`() {
        val cmd = Apdu.buildCampusAccessCommand("LIBRARY")
        val parsed = Apdu.parseCommand(cmd)
        assertIs<Apdu.ParsedCommand.CampusAccess>(parsed)
        assertEquals("LIBRARY", parsed.doorId)
    }

    @Test
    fun `report lost command round trips through parse`() {
        val cmd = Apdu.buildReportLostCommand()
        assertIs<Apdu.ParsedCommand.ReportLost>(Apdu.parseCommand(cmd))
    }

    @Test
    fun `garbage bytes parse as Unknown rather than throwing`() {
        val parsed = Apdu.parseCommand(byteArrayOf(0x80.toByte(), 0x20, 0x00, 0x00, 0x05, 0x01, 0x09))
        assertIs<Apdu.ParsedCommand.Unknown>(parsed)
    }

    @Test
    fun `encoded response for a denied tag on ends with conditions-not-satisfied status word`() {
        val response = TapResponse.TagOnResponse(TagOnResult.Denied(DenialReason.INSUFFICIENT_BALANCE))
        val bytes = Apdu.encodeResponse(response)
        val sw = bytes.copyOfRange(bytes.size - 2, bytes.size)
        assertContentEquals(Apdu.SW_CONDITIONS_NOT_SATISFIED, sw)
    }

    @Test
    fun `encoded response for a successful tag off ends with OK status word`() {
        val response = TapResponse.TagOffResponse(
            TagOffResult.Success(fareCents = 310, newBalanceCents = 1690, capped = false, insufficientFunds = false),
        )
        val bytes = Apdu.encodeResponse(response)
        val sw = bytes.copyOfRange(bytes.size - 2, bytes.size)
        assertContentEquals(Apdu.SW_OK, sw)
    }

    @Test
    fun `full tap flow through TapController produces a grant for campus access`() {
        val credential = UnifiedCredential(
            transit = TransitAccount(initialBalanceCents = 2000),
            campus = CampusAccessController(
                CampusAccessProfile("Test Student", Role.STUDENT, enrolmentActive = true),
                mapOf("MAIN_ENTRANCE" to CampusDoor("MAIN_ENTRANCE", "Main", setOf(Role.STUDENT))),
            ),
        )
        val controller = TapController(credential)

        val command = Apdu.parseCommand(Apdu.buildCampusAccessCommand("MAIN_ENTRANCE"))
        val response = controller.handle(command, nowMillis = 0)

        assertIs<TapResponse.CampusAccessResponse>(response)
        assertIs<AccessResult.Granted>(response.result)
    }

    @Test
    fun `reporting lost through the wire protocol blocks both transit and campus`() {
        val credential = UnifiedCredential(
            transit = TransitAccount(initialBalanceCents = 2000),
            campus = CampusAccessController(
                CampusAccessProfile("Test Student", Role.STUDENT, enrolmentActive = true),
                mapOf("MAIN_ENTRANCE" to CampusDoor("MAIN_ENTRANCE", "Main", setOf(Role.STUDENT))),
            ),
        )
        val controller = TapController(credential)

        controller.handle(Apdu.parseCommand(Apdu.buildReportLostCommand()), nowMillis = 0)

        assertEquals(true, credential.transit.isBlocked)
        assertEquals(true, credential.campus.isBlocked)
    }
}
