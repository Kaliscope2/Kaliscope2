package nz.kaliscope.tagdemo.core

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CampusAccessControllerTest {

    private val doors = mapOf(
        "MAIN_ENTRANCE" to CampusDoor("MAIN_ENTRANCE", "Main Entrance", setOf(Role.STUDENT, Role.STAFF, Role.VISITOR)),
        "STAFF_ROOM" to CampusDoor("STAFF_ROOM", "Staff Room", setOf(Role.STAFF)),
        "LIBRARY" to CampusDoor("LIBRARY", "Library", setOf(Role.STUDENT, Role.STAFF), opensHour = 8, closesHour = 20),
    )

    private fun controller(role: Role = Role.STUDENT, enrolmentActive: Boolean = true) =
        CampusAccessController(CampusAccessProfile("Test Student", role, enrolmentActive), doors, ZoneOffset.UTC)

    private fun atHour(hour: Int): Long = java.time.Instant.parse("2026-03-02T${"%02d".format(hour)}:00:00Z").toEpochMilli()

    @Test
    fun `granted when role and hours are valid`() {
        val result = controller().requestAccess("LIBRARY", atHour(10))
        assertEquals(AccessResult.Granted("LIBRARY"), result)
    }

    @Test
    fun `denied when role is not authorized for the door`() {
        val result = controller(role = Role.STUDENT).requestAccess("STAFF_ROOM", atHour(10))
        assertEquals(AccessResult.Denied("STAFF_ROOM", AccessDenialReason.ROLE_NOT_AUTHORIZED), result)
    }

    @Test
    fun `denied outside the door's allowed hours`() {
        val result = controller().requestAccess("LIBRARY", atHour(23))
        assertEquals(AccessResult.Denied("LIBRARY", AccessDenialReason.OUTSIDE_ALLOWED_HOURS), result)
    }

    @Test
    fun `denied for an unknown door`() {
        val result = controller().requestAccess("NOT_A_REAL_DOOR", atHour(10))
        assertEquals(AccessResult.Denied("NOT_A_REAL_DOOR", AccessDenialReason.UNKNOWN_DOOR), result)
    }

    @Test
    fun `denied when enrolment is inactive`() {
        val result = controller(enrolmentActive = false).requestAccess("MAIN_ENTRANCE", atHour(10))
        assertEquals(AccessResult.Denied("MAIN_ENTRANCE", AccessDenialReason.ENROLMENT_INACTIVE), result)
    }

    @Test
    fun `denied once blocked`() {
        val c = controller()
        c.block()
        val result = c.requestAccess("MAIN_ENTRANCE", atHour(10))
        assertEquals(AccessResult.Denied("MAIN_ENTRANCE", AccessDenialReason.ACCOUNT_BLOCKED), result)
    }

    @Test
    fun `unblock restores access`() {
        val c = controller()
        c.block()
        c.unblock()
        val result = c.requestAccess("MAIN_ENTRANCE", atHour(10))
        assertIs<AccessResult.Granted>(result)
    }
}
