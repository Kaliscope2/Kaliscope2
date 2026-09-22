package nz.kaliscope.tagdemo.core

import java.io.ByteArrayOutputStream

/**
 * The wire protocol shared by the HCE service (phone acting as the card) and
 * the reader-mode app (phone acting as a bus/train validator or campus door
 * reader). Keeping all encode/decode logic here means both Android
 * components stay thin wrappers around code that's actually unit tested on
 * the JVM in this module.
 *
 * This is a project-invented protocol for two devices we control to talk to
 * each other — it has no relationship to, and will not interoperate with,
 * any real transit or access-control system's card protocol.
 */
object Apdu {

    val AID: ByteArray = byteArrayOf(
        0xF0.toByte(), 0x4B, 0x41, 0x4C, 0x49, 0x53, 0x43, 0x4F, 0x50, 0x45,
    ) // F0 + "KALISCOPE", proprietary-range AID per ISO 7816-5

    private const val CLA_ISO = 0x00.toByte()
    private const val CLA_PROPRIETARY = 0x80.toByte()
    private const val INS_SELECT = 0xA4.toByte()

    private const val INS_GET_STATUS = 0x10.toByte()
    private const val INS_TAG_ON = 0x20.toByte()
    private const val INS_TAG_OFF = 0x21.toByte()
    private const val INS_CAMPUS_ACCESS = 0x30.toByte()
    private const val INS_REPORT_LOST = 0x40.toByte()

    private const val MODE_BUS = 0x01.toByte()
    private const val MODE_TRAIN = 0x02.toByte()

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
    val SW_INSTRUCTION_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)

    // ---- Commands (reader -> card) ----

    sealed class ParsedCommand {
        object Select : ParsedCommand()
        object GetStatus : ParsedCommand()
        data class TagOn(val mode: TransportMode, val stopId: String) : ParsedCommand()
        data class TagOff(val stopId: String) : ParsedCommand()
        data class CampusAccess(val doorId: String) : ParsedCommand()
        object ReportLost : ParsedCommand()
        data class Unknown(val raw: ByteArray) : ParsedCommand()
    }

    fun buildSelectCommand(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(CLA_ISO.toInt())
        out.write(INS_SELECT.toInt())
        out.write(0x04) // P1: select by name
        out.write(0x00) // P2
        out.write(AID.size)
        out.write(AID)
        return out.toByteArray()
    }

    fun buildGetStatusCommand(): ByteArray = header(INS_GET_STATUS, 0)

    fun buildTagOnCommand(mode: TransportMode, stopId: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(if (mode == TransportMode.BUS) MODE_BUS.toInt() else MODE_TRAIN.toInt())
        writeLengthPrefixedString(data, stopId)
        return header(INS_TAG_ON, data.size()) + data.toByteArray()
    }

    fun buildTagOffCommand(stopId: String): ByteArray {
        val data = ByteArrayOutputStream()
        writeLengthPrefixedString(data, stopId)
        return header(INS_TAG_OFF, data.size()) + data.toByteArray()
    }

    fun buildCampusAccessCommand(doorId: String): ByteArray {
        val data = ByteArrayOutputStream()
        writeLengthPrefixedString(data, doorId)
        return header(INS_CAMPUS_ACCESS, data.size()) + data.toByteArray()
    }

    fun buildReportLostCommand(): ByteArray = header(INS_REPORT_LOST, 0)

    private fun header(ins: Byte, dataLen: Int): ByteArray =
        byteArrayOf(CLA_PROPRIETARY, ins, 0x00, 0x00, dataLen.toByte())

    fun parseCommand(apdu: ByteArray): ParsedCommand {
        if (apdu.size < 4) return ParsedCommand.Unknown(apdu)
        val cla = apdu[0]
        val ins = apdu[1]

        if (cla == CLA_ISO && ins == INS_SELECT) return ParsedCommand.Select
        if (cla != CLA_PROPRIETARY) return ParsedCommand.Unknown(apdu)

        val reader = ByteReader(apdu, offset = 4)
        if (apdu.size > 4) reader.skipLc()

        return try {
            when (ins) {
                INS_GET_STATUS -> ParsedCommand.GetStatus
                INS_TAG_ON -> {
                    val mode = if (reader.readByte() == MODE_TRAIN) TransportMode.TRAIN else TransportMode.BUS
                    ParsedCommand.TagOn(mode, reader.readLengthPrefixedString())
                }
                INS_TAG_OFF -> ParsedCommand.TagOff(reader.readLengthPrefixedString())
                INS_CAMPUS_ACCESS -> ParsedCommand.CampusAccess(reader.readLengthPrefixedString())
                INS_REPORT_LOST -> ParsedCommand.ReportLost
                else -> ParsedCommand.Unknown(apdu)
            }
        } catch (e: IndexOutOfBoundsException) {
            ParsedCommand.Unknown(apdu)
        }
    }

    // ---- Responses (card -> reader) ----

    fun encodeResponse(response: TapResponse): ByteArray {
        val body = ByteArrayOutputStream()
        val sw = when (response) {
            is TapResponse.Status -> {
                encodeStatus(body, response)
                SW_OK
            }
            is TapResponse.TagOnResponse -> encodeTagOn(body, response.result)
            is TapResponse.TagOffResponse -> encodeTagOff(body, response.result)
            is TapResponse.CampusAccessResponse -> encodeCampusAccess(body, response.result)
            TapResponse.Ack -> SW_OK
            TapResponse.Unsupported -> SW_INSTRUCTION_NOT_SUPPORTED
        }
        return body.toByteArray() + sw
    }

    private fun encodeStatus(out: ByteArrayOutputStream, response: TapResponse.Status) {
        val s = response.status
        writeLong(out, s.balanceCents)
        out.write(if (s.isBlocked) 1 else 0)
        out.write(if (s.tagState == TagState.TAGGED_ON) 1 else 0)
        val trip = s.currentTrip
        if (trip != null) {
            out.write(1)
            out.write(if (trip.mode == TransportMode.TRAIN) MODE_TRAIN.toInt() else MODE_BUS.toInt())
            writeLengthPrefixedString(out, trip.entryStopId)
            writeLong(out, trip.entryTimeMillis)
        } else {
            out.write(0)
        }
        out.write(if (response.campusBlocked) 1 else 0)
    }

    private fun encodeTagOn(out: ByteArrayOutputStream, result: TagOnResult): ByteArray = when (result) {
        is TagOnResult.Success -> {
            out.write(0x01)
            writeLong(out, result.holdCents)
            val closed = result.autoClosedPreviousTrip
            if (closed != null) {
                out.write(1)
                writeLong(out, closed.fareCents)
            } else {
                out.write(0)
            }
            SW_OK
        }
        is TagOnResult.Cancelled -> {
            out.write(0x02)
            writeLengthPrefixedString(out, result.cancelledTrip.entryStopId)
            SW_OK
        }
        is TagOnResult.Denied -> {
            out.write(0x00)
            out.write(result.reason.ordinal)
            SW_CONDITIONS_NOT_SATISFIED
        }
    }

    private fun encodeTagOff(out: ByteArrayOutputStream, result: TagOffResult): ByteArray = when (result) {
        is TagOffResult.Success -> {
            out.write(0x01)
            writeLong(out, result.fareCents)
            writeLong(out, result.newBalanceCents)
            out.write(if (result.capped) 1 else 0)
            out.write(if (result.insufficientFunds) 1 else 0)
            SW_OK
        }
        is TagOffResult.Denied -> {
            out.write(0x00)
            out.write(result.reason.ordinal)
            SW_CONDITIONS_NOT_SATISFIED
        }
    }

    private fun encodeCampusAccess(out: ByteArrayOutputStream, result: AccessResult): ByteArray = when (result) {
        is AccessResult.Granted -> {
            out.write(0x01)
            writeLengthPrefixedString(out, result.doorId)
            SW_OK
        }
        is AccessResult.Denied -> {
            out.write(0x00)
            writeLengthPrefixedString(out, result.doorId)
            out.write(result.reason.ordinal)
            SW_CONDITIONS_NOT_SATISFIED
        }
    }

    // ---- byte helpers ----

    private fun writeLong(out: ByteArrayOutputStream, value: Long) {
        for (i in 7 downTo 0) out.write(((value ushr (i * 8)) and 0xFF).toInt())
    }

    private fun writeLengthPrefixedString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.write(bytes.size.coerceAtMost(255))
        out.write(bytes, 0, bytes.size.coerceAtMost(255))
    }

    private class ByteReader(private val data: ByteArray, private var offset: Int) {
        fun skipLc() {
            offset += 1
        }

        fun readByte(): Byte = data[offset++]

        fun readLong(): Long {
            var value = 0L
            for (i in 0 until 8) value = (value shl 8) or (data[offset++].toLong() and 0xFF)
            return value
        }

        fun readLengthPrefixedString(): String {
            val len = data[offset++].toInt() and 0xFF
            val s = String(data, offset, len, Charsets.UTF_8)
            offset += len
            return s
        }
    }
}
