package dev.doyu.smputil.stash

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StackLayoutTest {
    private val maxStack = mapOf("sword" to 1, "pearl" to 16, "stone" to 64)

    @Test
    fun `full stacks come first and the remainder is last`() {
        assertEquals(listOf(64, 64, 22), splitIntoStacks(150, 64))
        assertEquals(listOf(64, 64), splitIntoStacks(128, 64))
        assertEquals(listOf(1, 1, 1), splitIntoStacks(3, 1))
        assertEquals(listOf(5), splitIntoStacks(5, 16))
    }

    @Test
    fun `layout is ordered by kind and split per kind`() {
        // 입력은 종류별로 이미 합친 총합이다(InventorySorter가 합쳐서 넘긴다).
        val layout = stackLayout(
            listOf("stone" to 130, "pearl" to 20),
            order = naturalOrder(),
            maxStackOf = { maxStack.getValue(it) },
        )

        assertEquals(listOf("pearl" to 16, "pearl" to 4, "stone" to 64, "stone" to 64, "stone" to 2), layout)
    }

    @Test
    fun `equal kinds keep their original order`() {
        val layout = stackLayout(
            listOf("b" to 1, "a" to 1, "b" to 2),
            order = Comparator { x, y -> x.first().compareTo(y.first()) },
            maxStackOf = { 64 },
        )

        assertEquals(listOf("a" to 1, "b" to 1, "b" to 2), layout)
    }

    @Test
    fun `random inventories keep every kind's total and never exceed a stack`() {
        val random = Random(2026)
        repeat(500) {
            val kinds = maxStack.keys.toList()
            val groups = kinds.filter { random.nextBoolean() }.map { it to random.nextInt(1, 400) }

            val layout = stackLayout(groups, order = naturalOrder(), maxStackOf = { maxStack.getValue(it) })

            for ((kind, total) in groups) {
                assertEquals(total, layout.filter { it.first == kind }.sumOf { it.second }, "total of $kind")
            }
            assertTrue(layout.all { (kind, amount) -> amount in 1..maxStack.getValue(kind) })
            assertEquals(layout.map { it.first }, layout.map { it.first }.sorted())
            // 합친 결과는 아무리 많아도 원래 스택 수보다 늘지 않는다(들어갈 칸이 있다).
            assertTrue(layout.size <= groups.sumOf { (kind, total) -> (total + maxStack.getValue(kind) - 1) / maxStack.getValue(kind) })
        }
    }

    @Test
    fun `nothing to split is a bug`() {
        assertFailsWith<IllegalArgumentException> { splitIntoStacks(0, 64) }
        assertFailsWith<IllegalArgumentException> { splitIntoStacks(5, 0) }
    }
}
