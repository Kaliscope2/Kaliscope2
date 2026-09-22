package nz.kaliscope.tagdemo.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

enum class Role { STUDENT, STAFF, VISITOR }

data class CampusDoor(
    val doorId: String,
    val name: String,
    val allowedRoles: Set<Role>,
    /** Hours are in [0, 24]; a door open all day uses 0..24. */
    val opensHour: Int = 0,
    val closesHour: Int = 24,
)

data class CampusAccessProfile(
    val holderName: String,
    val role: Role,
    val enrolmentActive: Boolean,
)

enum class AccessDenialReason {
    ACCOUNT_BLOCKED,
    ENROLMENT_INACTIVE,
    ROLE_NOT_AUTHORIZED,
    OUTSIDE_ALLOWED_HOURS,
    UNKNOWN_DOOR,
}

sealed class AccessResult {
    data class Granted(val doorId: String) : AccessResult()
    data class Denied(val doorId: String, val reason: AccessDenialReason) : AccessResult()
}

class CampusAccessController(
    private val profile: CampusAccessProfile,
    private val doors: Map<String, CampusDoor>,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) {
    var isBlocked: Boolean = false
        private set

    fun block() {
        isBlocked = true
    }

    fun unblock() {
        isBlocked = false
    }

    fun requestAccess(doorId: String, nowMillis: Long): AccessResult {
        if (isBlocked) return AccessResult.Denied(doorId, AccessDenialReason.ACCOUNT_BLOCKED)
        if (!profile.enrolmentActive) return AccessResult.Denied(doorId, AccessDenialReason.ENROLMENT_INACTIVE)

        val door = doors[doorId] ?: return AccessResult.Denied(doorId, AccessDenialReason.UNKNOWN_DOOR)
        if (profile.role !in door.allowedRoles) {
            return AccessResult.Denied(doorId, AccessDenialReason.ROLE_NOT_AUTHORIZED)
        }

        val hour = Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
        if (hour < door.opensHour || hour >= door.closesHour) {
            return AccessResult.Denied(doorId, AccessDenialReason.OUTSIDE_ALLOWED_HOURS)
        }

        return AccessResult.Granted(doorId)
    }
}
