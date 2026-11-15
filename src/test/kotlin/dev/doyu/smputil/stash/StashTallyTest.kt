package dev.doyu.smputil.stash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StashTallyTest {
    @Test
    fun `same kind is added up and the biggest come first`() {
        val tally = StashTally<String>()
        tally.add("stone", 32)
        tally.add("iron", 10)
        tally.add("stone", 20)
        tally.add("coal", 3)

        val summary = tally.summary(limit = 2)

        assertEquals(listOf("stone" to 52, "iron" to 10), summary.shown)
        assertEquals(1, summary.hiddenKinds)
        assertEquals(65, summary.total)
    }

    @Test
    fun `nothing hidden when everything fits`() {
        val tally = StashTally<String>().apply { add("a", 1) }

        assertEquals(0, tally.summary(limit = 5).hiddenKinds)
    }

    @Test
    fun `zero or negative amounts are a bug`() {
        assertFailsWith<IllegalArgumentException> { StashTally<String>().add("a", 0) }
    }

    @Test
    fun `chunk range covers the radius including negative coordinates`() {
        assertEquals(-1..0, chunkRange(center = 0, radius = 10))
        assertEquals(0..1, chunkRange(center = 16, radius = 10))
        assertEquals(-1..-1, chunkRange(center = -1, radius = 0))
        assertEquals(-3..-3, chunkRange(center = -40, radius = 5))
        assertEquals(-4..-2, chunkRange(center = -40, radius = 10))
    }
}
