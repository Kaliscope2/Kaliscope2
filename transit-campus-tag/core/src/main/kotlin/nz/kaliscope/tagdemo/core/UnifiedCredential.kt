package nz.kaliscope.tagdemo.core

/**
 * One phone, one credential, two subsystems. This is the piece a real
 * deployment could never actually give you without the transit operator and
 * the campus both provisioning your phone through their own systems — here
 * it just demonstrates that a single "lost/stolen" action locks both sides
 * at once, the way a real unified credential would.
 */
class UnifiedCredential(
    val transit: TransitAccount,
    val campus: CampusAccessController,
) {
    var reportedLost: Boolean = false
        private set

    fun reportLostOrStolen() {
        reportedLost = true
        transit.block()
        campus.block()
    }

    fun reinstate() {
        reportedLost = false
        transit.unblock()
        campus.unblock()
    }
}
