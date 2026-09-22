import CoreNFC
import Combine

/// What the reader UI asks for on the next tap.
enum ReaderAction {
    case getStatus
    case tagOn(mode: TransportMode, stopId: String)
    case tagOff(stopId: String)
    case campusAccess(doorId: String)
    case reportLost
}

/// What came back from the last tap, for the UI to render.
enum ReaderResult {
    case status(StatusInfo)
    case tagOn(TagOnOutcome)
    case tagOff(TagOffOutcome)
    case campusAccess(CampusAccessOutcome)
    case ack
    case failure(String)
}

/// Runs this iPhone as an NFC *reader* — the role a bus validator or campus
/// door reader plays — against a card emulated by the Android app in this
/// same sandbox. This is standard, publicly available CoreNFC; it does not
/// and cannot make the iPhone act as the card (see project README).
final class NFCReaderController: NSObject, ObservableObject, NFCTagReaderSessionDelegate {

    @Published var lastResult: ReaderResult?
    @Published var statusMessage: String = "Ready to scan"

    private var session: NFCTagReaderSession?
    private var pendingAction: ReaderAction = .getStatus

    func beginScan(action: ReaderAction) {
        guard NFCTagReaderSession.readingAvailable else {
            lastResult = .failure("This device doesn't support NFC tag reading.")
            return
        }
        pendingAction = action
        session = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self, queue: nil)
        session?.alertMessage = "Hold your iPhone near the card"
        session?.begin()
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        // No-op: nothing to do until a tag is detected.
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async {
            self.statusMessage = "Session ended: \(error.localizedDescription)"
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first, case let .iso7816(iso7816Tag) = tag else {
            session.invalidate(errorMessage: "Not a compatible card")
            return
        }

        session.connect(to: tag) { [weak self] error in
            guard let self else { return }
            if let error {
                session.invalidate(errorMessage: "Connect failed: \(error.localizedDescription)")
                return
            }
            self.sendSelect(to: iso7816Tag, session: session)
        }
    }

    private func sendSelect(to tag: NFCISO7816Tag, session: NFCTagReaderSession) {
        guard let apdu = NFCISO7816APDU(data: Data(Apdu.buildSelectCommand())) else {
            session.invalidate(errorMessage: "Could not build SELECT command")
            return
        }
        tag.sendCommand(apdu: apdu) { [weak self] _, sw1, sw2, error in
            guard let self else { return }
            if let error {
                session.invalidate(errorMessage: "SELECT failed: \(error.localizedDescription)")
                return
            }
            guard sw1 == 0x90, sw2 == 0x00 else {
                session.invalidate(errorMessage: "Card did not accept SELECT (SW \(String(format: "%02X%02X", sw1, sw2)))")
                return
            }
            self.sendAction(self.pendingAction, to: tag, session: session)
        }
    }

    private func sendAction(_ action: ReaderAction, to tag: NFCISO7816Tag, session: NFCTagReaderSession) {
        let commandBytes: [UInt8]
        switch action {
        case .getStatus:
            commandBytes = Apdu.buildGetStatusCommand()
        case let .tagOn(mode, stopId):
            commandBytes = Apdu.buildTagOnCommand(mode: mode, stopId: stopId)
        case let .tagOff(stopId):
            commandBytes = Apdu.buildTagOffCommand(stopId: stopId)
        case let .campusAccess(doorId):
            commandBytes = Apdu.buildCampusAccessCommand(doorId: doorId)
        case .reportLost:
            commandBytes = Apdu.buildReportLostCommand()
        }

        guard let apdu = NFCISO7816APDU(data: Data(commandBytes)) else {
            session.invalidate(errorMessage: "Could not build command")
            return
        }

        tag.sendCommand(apdu: apdu) { [weak self] responseData, sw1, sw2, error in
            guard let self else { return }
            if let error {
                session.invalidate(errorMessage: "Command failed: \(error.localizedDescription)")
                return
            }

            let fullResponse = Array(responseData) + [sw1, sw2]
            let body = Apdu.body(fullResponse)
            let result = self.decode(action: action, body: body, sw1: sw1, sw2: sw2)

            DispatchQueue.main.async {
                self.lastResult = result
            }
            session.alertMessage = "Done"
            session.invalidate()
        }
    }

    private func decode(action: ReaderAction, body: [UInt8], sw1: UInt8, sw2: UInt8) -> ReaderResult {
        do {
            switch action {
            case .getStatus:
                return .status(try Apdu.decodeStatus(body))
            case .tagOn:
                return .tagOn(try Apdu.decodeTagOn(body))
            case .tagOff:
                return .tagOff(try Apdu.decodeTagOff(body))
            case .campusAccess:
                return .campusAccess(try Apdu.decodeCampusAccess(body))
            case .reportLost:
                return sw1 == 0x90 && sw2 == 0x00 ? .ack : .failure("Report-lost was rejected")
            }
        } catch {
            return .failure("Could not decode response: \(error)")
        }
    }
}
