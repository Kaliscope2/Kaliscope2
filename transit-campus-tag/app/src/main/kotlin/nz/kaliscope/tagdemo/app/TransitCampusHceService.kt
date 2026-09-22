package nz.kaliscope.tagdemo.app

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import nz.kaliscope.tagdemo.core.Apdu

/**
 * The "card" side of this demo: when a reader (our own reader app — Android
 * or iOS, never a real bus/train/campus reader) taps this phone, Android
 * routes the raw APDU bytes here. All the actual decision-making lives in
 * the `core` module (already unit tested on the JVM); this class is
 * deliberately a thin wire-up.
 */
class TransitCampusHceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return Apdu.SW_INSTRUCTION_NOT_SUPPORTED

        val command = Apdu.parseCommand(commandApdu)
        Log.d(TAG, "Received command: $command")

        if (command is Apdu.ParsedCommand.Select) {
            return Apdu.SW_OK
        }

        val response = CredentialStore.tapController.handle(command, nowMillis = System.currentTimeMillis())
        CredentialStore.notifyChanged()
        return Apdu.encodeResponse(response)
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated, reason=$reason")
    }

    private companion object {
        const val TAG = "TransitCampusHce"
    }
}
