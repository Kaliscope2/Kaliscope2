package nz.kaliscope.tagdemo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransitAccountTest {

    private val t0 = 1_700_000_000_000L // arbitrary fixed epoch millis

    @Test
    fun `happy path tag on then tag off charges the zone fare`() {
        val account = TransitAccount(initialBalanceCents = 2000)

        val on = account.tagOn(TransportMode.BUS, "STOP_A", t0)
        assertIs<TagOnResult.Success>(on)

        val off = account.tagOff("STOP_C", t0 + 5 * 60_000)
        assertIs<TagOffResult.Success>(off)
        // base 220 + 1 zone crossed (A=1 -> C=2) * 90 = 310
        assertEquals(310L, off.fareCents)
        assertEquals(2000L - 310L, off.newBalanceCents)
        assertFalse(off.capped)
        assertFalse(off.insufficientFunds)
    }

    @Test
    fun `tag on denied when balance below base fare`() {
        val account = TransitAccount(initialBalanceCents = 100)
        val result = account.tagOn(TransportMode.TRAIN, "STOP_A", t0)
        assertEquals(TagOnResult.Denied(DenialReason.INSUFFICIENT_BALANCE), result)
    }

    @Test
    fun `tag off without a prior tag on is denied`() {
        val account = TransitAccount(initialBalanceCents = 2000)
        val result = account.tagOff("STOP_A", t0)
        assertEquals(TagOffResult.Denied(DenialReason.NOT_TAGGED_ON), result)
    }

    @Test
    fun `blocked account cannot tag on`() {
        val account = TransitAccount(initialBalanceCents = 2000)
        account.block()
        val result = account.tagOn(TransportMode.BUS, "STOP_A", t0)
        assertEquals(TagOnResult.Denied(DenialReason.ACCOUNT_BLOCKED), result)
    }

    @Test
    fun `blocked account can still tag off to close an in-progress trip`() {
        val account = TransitAccount(initialBalanceCents = 2000)
        account.tagOn(TransportMode.BUS, "STOP_A", t0)
        account.block()

        val result = account.tagOff("STOP_B", t0 + 60_000)
        assertIs<TagOffResult.Success>(result)
    }

    @Test
    fun `double tap within grace window cancels the trip for free`() {
        val account = TransitAccount(initialBalanceCents = 2000)
        account.tagOn(TransportMode.BUS, "STOP_A", t0)

        val result = account.tagOn(TransportMode.BUS, "STOP_A", t0 + 5_000)
        assertIs<TagOnResult.Cancelled>(result)
        assertEquals(TagState.NOT_TAGGED, account.status(t0).tagState)
        assertEquals(2000L, account.balanceCents) // nothing charged
    }

    @Test
    fun `forgetting to tag off auto-closes the trip at max fare on next tag on`() {
        val account = TransitAccount(initialBalanceCents = 2000)
        account.tagOn(TransportMode.BUS, "STOP_A", t0)

        val twoHoursLater = t0 + 2 * 60 * 60_000
        val result = account.tagOn(TransportMode.BUS, "STOP_D", twoHoursLater)

        assertIs<TagOnResult.Success>(result)
        val closed = result.autoClosedPreviousTrip
        assertEquals(true, closed?.incomplete)
        assertEquals(FareTable.maxFare(TransportMode.BUS), closed?.fareCents)

        val history = account.tripHistory()
        assertEquals(1, history.size)
        assertTrue(history[0].incomplete)
    }

    @Test
    fun `daily cap limits total fares charged in a transit day`() {
        val account = TransitAccount(initialBalanceCents = 100_00, dailyCapCents = 500)

        // All within the same UTC calendar day, comfortably clear of the 4am
        // transit-day rollover, so every trip below counts toward one cap.
        var time = java.time.Instant.parse("2026-03-02T10:00:00Z").toEpochMilli()
        repeat(5) {
            account.tagOn(TransportMode.TRAIN, "STOP_A", time)
            val off = account.tagOff("STOP_E", time + 60_000)
            assertIs<TagOffResult.Success>(off)
            time += 60 * 60_000 // space trips out by an hour, still same transit day
        }

        assertEquals(100_00L - 500L, account.balanceCents)
    }

    @Test
    fun `top up for demo increases balance`() {
        val account = TransitAccount(initialBalanceCents = 500)
        account.topUpForDemo(1000)
        assertEquals(1500L, account.balanceCents)
    }

    @Test
    fun `insufficient funds at tag off blocks the account`() {
        val account = TransitAccount(initialBalanceCents = 300)
        account.tagOn(TransportMode.BUS, "STOP_A", t0)

        val off = account.tagOff("STOP_D", t0 + 60_000)
        assertIs<TagOffResult.Success>(off)
        assertTrue(off.insufficientFunds)
        assertEquals(0L, off.newBalanceCents)
        assertTrue(account.isBlocked)
    }
}
