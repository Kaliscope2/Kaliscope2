import SwiftUI

struct ContentView: View {
    @StateObject private var reader = NFCReaderController()

    @State private var mode: TransportMode = .bus
    @State private var stopId: String = "STOP_A"
    @State private var exitStopId: String = "STOP_C"
    @State private var doorId: String = "MAIN_ENTRANCE"

    var body: some View {
        NavigationView {
            Form {
                Section("Transit — tag on") {
                    Picker("Mode", selection: $mode) {
                        Text("Bus").tag(TransportMode.bus)
                        Text("Train").tag(TransportMode.train)
                    }
                    .pickerStyle(.segmented)
                    TextField("Stop ID", text: $stopId)
                    Button("Tap to tag on") {
                        reader.beginScan(action: .tagOn(mode: mode, stopId: stopId))
                    }
                }

                Section("Transit — tag off") {
                    TextField("Exit stop ID", text: $exitStopId)
                    Button("Tap to tag off") {
                        reader.beginScan(action: .tagOff(stopId: exitStopId))
                    }
                }

                Section("Campus access") {
                    TextField("Door ID", text: $doorId)
                    Button("Tap to request access") {
                        reader.beginScan(action: .campusAccess(doorId: doorId))
                    }
                }

                Section("Other") {
                    Button("Check status") {
                        reader.beginScan(action: .getStatus)
                    }
                    Button("Report card lost / stolen", role: .destructive) {
                        reader.beginScan(action: .reportLost)
                    }
                }

                Section("Last result") {
                    ResultView(result: reader.lastResult)
                }
            }
            .navigationTitle("TagDemo Reader")
        }
    }
}

private struct ResultView: View {
    let result: ReaderResult?

    var body: some View {
        switch result {
        case .none:
            Text("No tap yet").foregroundColor(.secondary)
        case let .status(info):
            VStack(alignment: .leading, spacing: 4) {
                Text("Balance: $\(String(format: "%.2f", Double(info.balanceCents) / 100))")
                Text("Tagged on: \(info.tagged ? "Yes (\(info.currentTripStopId ?? "-"))" : "No")")
                Text("Blocked: \(info.isBlocked ? "Yes" : "No")")
                Text("Campus blocked: \(info.campusBlocked ? "Yes" : "No")")
            }
        case let .tagOn(outcome):
            switch outcome {
            case let .success(hold, closedFare):
                VStack(alignment: .leading) {
                    Text("Tagged on. Hold: $\(String(format: "%.2f", Double(hold) / 100))")
                    if let closedFare {
                        Text("(Auto-closed a forgotten trip: $\(String(format: "%.2f", Double(closedFare) / 100)))")
                            .foregroundColor(.orange)
                    }
                }
            case let .cancelled(stopId):
                Text("Double-tap detected — cancelled trip from \(stopId), no charge").foregroundColor(.orange)
            case let .denied(reason):
                Text("Denied: \(describe(reason))").foregroundColor(.red)
            }
        case let .tagOff(outcome):
            switch outcome {
            case let .success(fare, newBalance, capped, insufficientFunds):
                VStack(alignment: .leading) {
                    Text("Tagged off. Fare: $\(String(format: "%.2f", Double(fare) / 100))")
                    Text("New balance: $\(String(format: "%.2f", Double(newBalance) / 100))")
                    if capped { Text("(Daily cap applied)").foregroundColor(.blue) }
                    if insufficientFunds { Text("Insufficient funds — account blocked").foregroundColor(.red) }
                }
            case let .denied(reason):
                Text("Denied: \(describe(reason))").foregroundColor(.red)
            }
        case let .campusAccess(outcome):
            switch outcome {
            case let .granted(doorId):
                Text("Access granted: \(doorId)").foregroundColor(.green)
            case let .denied(doorId, reason):
                Text("Access denied at \(doorId): \(describe(reason))").foregroundColor(.red)
            }
        case .ack:
            Text("Acknowledged").foregroundColor(.green)
        case let .failure(message):
            Text(message).foregroundColor(.red)
        }
    }

    private func describe(_ reason: DenialReason) -> String {
        switch reason {
        case .insufficientBalance: return "insufficient balance"
        case .notTaggedOn: return "not tagged on"
        case .accountBlocked: return "account blocked"
        }
    }

    private func describe(_ reason: AccessDenialReason) -> String {
        switch reason {
        case .accountBlocked: return "account blocked"
        case .enrolmentInactive: return "enrolment inactive"
        case .roleNotAuthorized: return "role not authorized for this door"
        case .outsideAllowedHours: return "outside allowed hours"
        case .unknownDoor: return "unknown door"
        }
    }
}
