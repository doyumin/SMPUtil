package dev.doyu.smputil.trade

import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TradeSessionTest {
    private data class Stack(val type: String, val count: Int)

    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    @Test
    fun `both confirming exchanges the offers`() {
        val session = TradeSession<Stack>(first, second, capacity = 4)
        session.offer(TradeSide.FIRST, Stack("diamond", 3))
        session.offer(TradeSide.SECOND, Stack("emerald", 10))

        assertIs<ConfirmResult.Waiting>(session.confirm(TradeSide.FIRST))
        val done = assertIs<ConfirmResult.Completed<Stack>>(session.confirm(TradeSide.SECOND))

        assertEquals(listOf(Stack("emerald", 10)), done.settlement.first.receive)
        assertEquals(listOf(Stack("diamond", 3)), done.settlement.second.receive)
        assertFalse(session.isOpen)
    }

    @Test
    fun `any change to either side clears both confirmations`() {
        val session = TradeSession<Stack>(first, second, capacity = 4)
        session.offer(TradeSide.FIRST, Stack("diamond", 3))
        session.confirm(TradeSide.SECOND)

        session.withdraw(TradeSide.FIRST, 0)

        assertFalse(session.isConfirmed(TradeSide.SECOND))
        session.confirm(TradeSide.FIRST)
        session.offer(TradeSide.SECOND, Stack("dirt", 1))
        assertFalse(session.isConfirmed(TradeSide.FIRST))
    }

    @Test
    fun `cancelling returns each side its own offer`() {
        val session = TradeSession<Stack>(first, second, capacity = 4)
        session.offer(TradeSide.FIRST, Stack("diamond", 3))
        session.offer(TradeSide.SECOND, Stack("emerald", 10))

        val settlement = session.end(TradeEndReason.CANCELLED, TradeSide.SECOND)!!

        assertEquals(listOf(Stack("diamond", 3)), settlement.first.receive)
        assertEquals(listOf(Stack("emerald", 10)), settlement.second.receive)
    }

    @Test
    fun `the dead side drops its offer where it died and the other gets theirs back`() {
        val session = TradeSession<Stack>(first, second, capacity = 4)
        session.offer(TradeSide.FIRST, Stack("diamond", 3))
        session.offer(TradeSide.SECOND, Stack("emerald", 10))

        val settlement = session.end(TradeEndReason.DIED, TradeSide.FIRST)!!

        assertEquals(Handover(emptyList(), listOf(Stack("diamond", 3))), settlement.first)
        assertEquals(Handover(listOf(Stack("emerald", 10)), emptyList()), settlement.second)
    }

    @Test
    fun `a trade ends only once`() {
        val session = TradeSession<Stack>(first, second, capacity = 4)
        session.offer(TradeSide.FIRST, Stack("diamond", 3))

        session.end(TradeEndReason.CANCELLED, TradeSide.FIRST)

        assertNull(session.end(TradeEndReason.LEFT_GAME, TradeSide.SECOND))
        assertIs<ConfirmResult.Closed>(session.confirm(TradeSide.FIRST))
        assertFalse(session.offer(TradeSide.FIRST, Stack("dirt", 1)))
        assertNull(session.withdraw(TradeSide.FIRST, 0))
    }

    @Test
    fun `full offer refuses more items`() {
        val session = TradeSession<Stack>(first, second, capacity = 1)

        assertTrue(session.offer(TradeSide.FIRST, Stack("dirt", 1)))
        assertFalse(session.offer(TradeSide.FIRST, Stack("dirt", 1)))
    }

    /**
     * 무작위로 올리고, 빼고, 확인하고, 끝낸다. 어떤 경로로 끝나든 아이템의 총합은 거래 전과 같아야 한다(DESIGN 6장).
     * 플레이어 인벤토리·발밑을 리스트로 흉내 내고, 거래 칸에 올린 것은 인벤토리에서 빠진다.
     */
    @Test
    fun `items are never created or destroyed whatever happens`() {
        val random = Random(20261110)
        repeat(2_000) { round ->
            val inventories = mapOf(
                TradeSide.FIRST to MutableList(random.nextInt(0, 8)) { Stack("item${random.nextInt(5)}", random.nextInt(1, 65)) },
                TradeSide.SECOND to MutableList(random.nextInt(0, 8)) { Stack("item${random.nextInt(5)}", random.nextInt(1, 65)) },
            )
            val ground = mutableListOf<Stack>()
            fun total(): Map<String, Int> =
                (inventories.values.flatten() + ground).groupBy { it.type }.mapValues { (_, stacks) -> stacks.sumOf { it.count } }
            val before = total()
            val session = TradeSession<Stack>(first, second, capacity = random.nextInt(1, 6))
            var settlement: TradeSettlement<Stack>? = null

            while (settlement == null) {
                val side = TradeSide.entries.random(random)
                val inventory = inventories.getValue(side)
                when (random.nextInt(6)) {
                    0, 1 -> if (inventory.isNotEmpty()) {
                        val index = random.nextInt(inventory.size)
                        if (session.offer(side, inventory[index])) inventory.removeAt(index)
                    }
                    2 -> {
                        val offered = session.offerOf(side)
                        if (offered.isNotEmpty()) session.withdraw(side, random.nextInt(offered.size))?.let(inventory::add)
                    }
                    3, 4 -> {
                        val result = session.confirm(side)
                        if (result is ConfirmResult.Completed) settlement = result.settlement
                    }
                    else -> if (random.nextInt(4) == 0) {
                        val reason = listOf(TradeEndReason.CANCELLED, TradeEndReason.LEFT_GAME, TradeEndReason.DIED, TradeEndReason.SHUTDOWN).random(random)
                        settlement = session.end(reason, side)
                    }
                }
            }
            for (side in TradeSide.entries) {
                inventories.getValue(side) += settlement.of(side).receive
                ground += settlement.of(side).dropHere
            }

            assertEquals(before, total(), "round $round ended by ${settlement.reason}")
            assertTrue(session.offerOf(TradeSide.FIRST).isEmpty() && session.offerOf(TradeSide.SECOND).isEmpty())
        }
    }
}
