package nz.kaliscope.tagdemo.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import nz.kaliscope.tagdemo.core.TagState

class MainActivity : AppCompatActivity() {

    private lateinit var balanceText: TextView
    private lateinit var tagStateText: TextView
    private lateinit var campusStateText: TextView
    private lateinit var historyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        balanceText = findViewById(R.id.balanceText)
        tagStateText = findViewById(R.id.tagStateText)
        campusStateText = findViewById(R.id.campusStateText)
        historyText = findViewById(R.id.historyText)

        findViewById<Button>(R.id.topUpButton).setOnClickListener {
            // Demo-only top-up: a real card would never let the app itself
            // credit balance; that always comes from a payment flow.
            CredentialStore.credential.transit.topUpForDemo(1000)
            refreshUi()
        }

        findViewById<Button>(R.id.lostStolenButton).setOnClickListener {
            val button = findViewById<Button>(R.id.lostStolenButton)
            if (CredentialStore.credential.reportedLost) {
                CredentialStore.credential.reinstate()
                button.setText(R.string.report_lost_button)
            } else {
                CredentialStore.credential.reportLostOrStolen()
                button.setText(R.string.reinstate_button)
            }
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        CredentialStore.setListener { runOnUiThread { refreshUi() } }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        CredentialStore.setListener(null)
    }

    private fun refreshUi() {
        val status = CredentialStore.credential.transit.status(System.currentTimeMillis())
        balanceText.text = getString(R.string.balance_label) + ": $" + "%.2f".format(status.balanceCents / 100.0)
        tagStateText.text = getString(R.string.tag_state_label) + ": " +
            if (status.tagState == TagState.TAGGED_ON) "Tagged on (${status.currentTrip?.entryStopId})" else "Not tagged on"
        campusStateText.text = getString(R.string.campus_label) + ": " +
            if (CredentialStore.credential.campus.isBlocked) "Blocked" else "Active"

        historyText.text = CredentialStore.credential.transit.tripHistory().joinToString("\n") { trip ->
            val exit = trip.exitStopId ?: "(never tagged off)"
            "${trip.mode} ${trip.entryStopId} -> $exit : $${"%.2f".format(trip.fareCents / 100.0)}" +
                if (trip.incomplete) " [incomplete]" else ""
        }
    }
}
