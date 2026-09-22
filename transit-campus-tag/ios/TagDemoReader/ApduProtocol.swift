import Foundation

/// Swift port of the wire protocol defined in the `core` Kotlin module
/// (`Apdu.kt`). Every byte layout here must stay in lockstep with that file
/// — this is a project-invented protocol for two devices we control to talk
/// to each other, not a standard, so nothing enforces the match except us.
enum TransportMode: UInt8 {
    case bus = 0x01
    case train = 0x02
}

enum DenialReason: UInt8 {
    case insufficientBalance = 0
    case notTaggedOn = 1
    case accountBlocked = 2
}

enum AccessDenialReason: UInt8 {
    case accountBlocked = 0
    case enrolmentInactive = 1
    case roleNotAuthorized = 2
    case outsideAllowedHours = 3
    case unknownDoor = 4
}

struct StatusInfo {
    let balanceCents: Int64
    let isBlocked: Bool
    let tagged: Bool
    let currentTripStopId: String?
    let currentTripMode: TransportMode?
    let campusBlocked: Bool
}

enum TagOnOutcome {
    case success(holdCents: Int64, autoClosedFareCents: Int64?)
    case cancelled(stopId: String)
    case denied(DenialReason)
}

enum TagOffOutcome {
    case success(fareCents: Int64, newBalanceCents: Int64, capped: Bool, insufficientFunds: Bool)
    case denied(DenialReason)
}

enum CampusAccessOutcome {
    case granted(doorId: String)
    case denied(doorId: String, reason: AccessDenialReason)
}

enum ApduError: Error {
    case malformedResponse
}

enum Apdu {

    /// F0 + "KALISCOPE" ASCII — a proprietary-range AID (ISO 7816-5), not a
    /// real registered transit/access AID.
    static let aid: [UInt8] = [0xF0, 0x4B, 0x41, 0x4C, 0x49, 0x53, 0x43, 0x4F, 0x50, 0x45]

    static let aidHex: String = aid.map { String(format: "%02X", $0) }.joined()

    private static let claISO: UInt8 = 0x00
    private static let claProprietary: UInt8 = 0x80
    private static let insSelect: UInt8 = 0xA4

    private static let insGetStatus: UInt8 = 0x10
    private static let insTagOn: UInt8 = 0x20
    private static let insTagOff: UInt8 = 0x21
    private static let insCampusAccess: UInt8 = 0x30
    private static let insReportLost: UInt8 = 0x40

    static let swOK: [UInt8] = [0x90, 0x00]
    static let swConditionsNotSatisfied: [UInt8] = [0x69, 0x85]

    // MARK: - Commands (reader -> card)

    static func buildSelectCommand() -> [UInt8] {
        [claISO, insSelect, 0x04, 0x00, UInt8(aid.count)] + aid
    }

    static func buildGetStatusCommand() -> [UInt8] {
        header(insGetStatus, dataLen: 0)
    }

    static func buildTagOnCommand(mode: TransportMode, stopId: String) -> [UInt8] {
        var data: [UInt8] = [mode.rawValue]
        data += lengthPrefixed(stopId)
        return header(insTagOn, dataLen: data.count) + data
    }

    static func buildTagOffCommand(stopId: String) -> [UInt8] {
        let data = lengthPrefixed(stopId)
        return header(insTagOff, dataLen: data.count) + data
    }

    static func buildCampusAccessCommand(doorId: String) -> [UInt8] {
        let data = lengthPrefixed(doorId)
        return header(insCampusAccess, dataLen: data.count) + data
    }

    static func buildReportLostCommand() -> [UInt8] {
        header(insReportLost, dataLen: 0)
    }

    private static func header(_ ins: UInt8, dataLen: Int) -> [UInt8] {
        [claProprietary, ins, 0x00, 0x00, UInt8(dataLen)]
    }

    private static func lengthPrefixed(_ s: String) -> [UInt8] {
        let bytes = Array(s.utf8.prefix(255))
        return [UInt8(bytes.count)] + bytes
    }

    // MARK: - Responses (card -> reader)
    //
    // Each `data` here is the FULL response including the trailing 2-byte
    // status word, exactly as `NFCISO7816Tag.sendCommand` + appended SW1/SW2
    // hand it back.

    static func decodeStatus(_ data: [UInt8]) throws -> StatusInfo {
        var reader = ByteReader(data)
        let balance = try reader.readInt64()
        let isBlocked = try reader.readByte() != 0
        let tagged = try reader.readByte() != 0
        let hasTrip = try reader.readByte() != 0
        var stopId: String? = nil
        var mode: TransportMode? = nil
        if hasTrip {
            mode = TransportMode(rawValue: try reader.readByte())
            stopId = try reader.readLengthPrefixedString()
            _ = try reader.readInt64() // entry time, not currently surfaced in the UI
        }
        let campusBlocked = try reader.readByte() != 0
        return StatusInfo(
            balanceCents: balance,
            isBlocked: isBlocked,
            tagged: tagged,
            currentTripStopId: stopId,
            currentTripMode: mode,
            campusBlocked: campusBlocked
        )
    }

    static func decodeTagOn(_ data: [UInt8]) throws -> TagOnOutcome {
        var reader = ByteReader(data)
        let resultCode = try reader.readByte()
        switch resultCode {
        case 0x01:
            let hold = try reader.readInt64()
            let hasClosed = try reader.readByte() != 0
            let closedFare = hasClosed ? try reader.readInt64() : nil
            return .success(holdCents: hold, autoClosedFareCents: closedFare)
        case 0x02:
            let stopId = try reader.readLengthPrefixedString()
            return .cancelled(stopId: stopId)
        default:
            guard let reason = DenialReason(rawValue: try reader.readByte()) else {
                throw ApduError.malformedResponse
            }
            return .denied(reason)
        }
    }

    static func decodeTagOff(_ data: [UInt8]) throws -> TagOffOutcome {
        var reader = ByteReader(data)
        let resultCode = try reader.readByte()
        switch resultCode {
        case 0x01:
            let fare = try reader.readInt64()
            let newBalance = try reader.readInt64()
            let capped = try reader.readByte() != 0
            let insufficientFunds = try reader.readByte() != 0
            return .success(fareCents: fare, newBalanceCents: newBalance, capped: capped, insufficientFunds: insufficientFunds)
        default:
            guard let reason = DenialReason(rawValue: try reader.readByte()) else {
                throw ApduError.malformedResponse
            }
            return .denied(reason)
        }
    }

    static func decodeCampusAccess(_ data: [UInt8]) throws -> CampusAccessOutcome {
        var reader = ByteReader(data)
        let resultCode = try reader.readByte()
        switch resultCode {
        case 0x01:
            let doorId = try reader.readLengthPrefixedString()
            return .granted(doorId: doorId)
        default:
            let doorId = try reader.readLengthPrefixedString()
            guard let reason = AccessDenialReason(rawValue: try reader.readByte()) else {
                throw ApduError.malformedResponse
            }
            return .denied(doorId: doorId, reason: reason)
        }
    }

    /// Strips the trailing 2-byte status word off a full response.
    static func body(_ data: [UInt8]) -> [UInt8] {
        data.count >= 2 ? Array(data.dropLast(2)) : data
    }

    private struct ByteReader {
        private let data: [UInt8]
        private var offset = 0

        init(_ data: [UInt8]) {
            self.data = data
        }

        mutating func readByte() throws -> UInt8 {
            guard offset < data.count else { throw ApduError.malformedResponse }
            defer { offset += 1 }
            return data[offset]
        }

        mutating func readInt64() throws -> Int64 {
            guard offset + 8 <= data.count else { throw ApduError.malformedResponse }
            var value: Int64 = 0
            for i in 0..<8 {
                value = (value << 8) | Int64(data[offset + i])
            }
            offset += 8
            return value
        }

        mutating func readLengthPrefixedString() throws -> String {
            let len = Int(try readByte())
            guard offset + len <= data.count else { throw ApduError.malformedResponse }
            let bytes = Array(data[offset..<(offset + len)])
            offset += len
            return String(decoding: bytes, as: UTF8.self)
        }
    }
}
