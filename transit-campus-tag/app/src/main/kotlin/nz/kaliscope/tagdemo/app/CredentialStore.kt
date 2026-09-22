package nz.kaliscope.tagdemo.app

import nz.kaliscope.tagdemo.core.CampusAccessController
import nz.kaliscope.tagdemo.core.CampusAccessProfile
import nz.kaliscope.tagdemo.core.CampusDoor
import nz.kaliscope.tagdemo.core.Role
import nz.kaliscope.tagdemo.core.TapController
import nz.kaliscope.tagdemo.core.TransitAccount
import nz.kaliscope.tagdemo.core.UnifiedCredential

/**
 * Holds the one [UnifiedCredential] this phone carries for as long as the
 * app process is alive, so both [TransitCampusHceService] (handling taps
 * from a reader) and [MainActivity] (showing status to the holder) see the
 * same state.
 *
 * A real deployment would persist this (and never store demo doors/fares
 * hardcoded like this — those come from the operator and campus's own
 * systems). For this sandbox, in-memory is enough to demo a session.
 */
object CredentialStore {

    private val demoDoors = mapOf(
        "MAIN_ENTRANCE" to CampusDoor(
            doorId = "MAIN_ENTRANCE",
            name = "Main Entrance",
            allowedRoles = setOf(Role.STUDENT, Role.STAFF, Role.VISITOR),
        ),
        "LIBRARY" to CampusDoor(
            doorId = "LIBRARY",
            name = "Library",
            allowedRoles = setOf(Role.STUDENT, Role.STAFF),
            opensHour = 8,
            closesHour = 20,
        ),
        "STAFF_ROOM" to CampusDoor(
            doorId = "STAFF_ROOM",
            name = "Staff Room",
            allowedRoles = setOf(Role.STAFF),
        ),
    )

    val credential: UnifiedCredential = UnifiedCredential(
        transit = TransitAccount(initialBalanceCents = 2000),
        campus = CampusAccessController(
            profile = CampusAccessProfile(holderName = "Demo Student", role = Role.STUDENT, enrolmentActive = true),
            doors = demoDoors,
        ),
    )

    val tapController: TapController = TapController(credential)

    private var listener: (() -> Unit)? = null

    fun setListener(l: (() -> Unit)?) {
        listener = l
    }

    /** Called by the HCE service after handling a tap, so a visible UI can refresh. */
    fun notifyChanged() {
        listener?.invoke()
    }
}
