package dev.doyu.smputil.request

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestBoardTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private val clock = MutableClock(Instant.parse("2026-11-10T00:00:00Z"))
    private val board = RequestBoard(clock, Duration.ofSeconds(15))
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    @Test
    fun `accepted request is found once`() {
        assertIs<SendResult.Sent>(board.send(alice, bob))

        val taken = assertIs<TakeResult.Found>(board.take(bob))
        assertEquals(alice, taken.request.from)
        assertIs<TakeResult.Missing>(board.take(bob))
    }

    @Test
    fun `request expires after its lifetime`() {
        board.send(alice, bob)
        clock.now = clock.now.plusSeconds(14)
        assertTrue(board.removeExpired().isEmpty())

        clock.now = clock.now.plusSeconds(1)
        assertEquals(listOf(alice), board.removeExpired().map { it.from })
        assertIs<TakeResult.Missing>(board.take(bob))
    }

    @Test
    fun `taking an expired request reports it as expired`() {
        board.send(alice, bob)
        clock.now = clock.now.plusSeconds(15)

        assertIs<TakeResult.Expired>(board.take(bob))
    }

    @Test
    fun `target holds only one pending request`() {
        board.send(alice, bob)

        assertIs<SendResult.TargetBusy>(board.send(carol, bob))
    }

    @Test
    fun `busy target frees up once the request expires`() {
        board.send(alice, bob)
        clock.now = clock.now.plusSeconds(15)

        assertIs<SendResult.Sent>(board.send(carol, bob))
    }

    @Test
    fun `requester holds only one pending request and the new one replaces the old`() {
        board.send(alice, bob)

        val sent = assertIs<SendResult.Sent>(board.send(alice, carol))
        assertEquals(bob, sent.replaced?.to)
        assertIs<TakeResult.Missing>(board.take(bob))
        assertIs<TakeResult.Found>(board.take(carol))
    }

    @Test
    fun `sending again to the same target refreshes the request`() {
        board.send(alice, bob)
        clock.now = clock.now.plusSeconds(10)

        val sent = assertIs<SendResult.Sent>(board.send(alice, bob))
        assertNull(sent.replaced)
        clock.now = clock.now.plusSeconds(10)
        assertIs<TakeResult.Found>(board.take(bob))
    }

    @Test
    fun `cannot request yourself`() {
        assertIs<SendResult.ToSelf>(board.send(alice, alice))
    }

    @Test
    fun `leaving removes requests sent and received`() {
        board.send(alice, bob)
        board.send(carol, alice)

        assertEquals(2, board.removeInvolving(alice).size)
        assertIs<TakeResult.Missing>(board.take(bob))
        assertIs<TakeResult.Missing>(board.take(alice))
    }
}
