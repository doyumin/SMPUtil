package dev.doyu.smputil.advancement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancementUnionTest {
    @Test
    fun `everyone receives what anyone has`() {
        val missing = missingCriteria(
            mapOf(
                "alice" to setOf("a", "b"),
                "bob" to setOf("b", "c"),
                "carol" to setOf("a", "b", "c"),
            ),
        )

        assertEquals(mapOf("alice" to setOf("c"), "bob" to setOf("a")), missing)
    }

    @Test
    fun `nothing to give when nobody has anything`() {
        assertEquals(emptyMap(), missingCriteria(mapOf("alice" to emptySet<String>(), "bob" to emptySet())))
    }

    @Test
    fun `recipe unlocks are not shared`() {
        assertFalse(isShared("recipes/misc/torch"))
        assertTrue(isShared("story/mine_stone"))
    }
}
