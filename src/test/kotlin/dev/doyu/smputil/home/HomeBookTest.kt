package dev.doyu.smputil.home

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeBookTest {
    private val owner = UUID.randomUUID()
    private val here = HomePoint("world", 1.5, 64.0, -3.25, 90f, 10f)
    private val there = HomePoint("world_nether", 8.0, 70.0, 8.0, 0f, 0f)

    @Test
    fun `limit blocks new homes but not moving an existing one`() {
        val book = HomeBook()

        assertEquals(SetHomeResult.Created, book.set(owner, DEFAULT_HOME, here, limit = 1))
        assertEquals(SetHomeResult.LimitReached(1), book.set(owner, "farm", there, limit = 1))
        assertEquals(SetHomeResult.Moved, book.set(owner, DEFAULT_HOME, there, limit = 1))
        assertEquals(there, book.get(owner, DEFAULT_HOME))
    }

    @Test
    fun `names ignore case`() {
        val book = HomeBook()
        book.set(owner, "Farm", here, limit = 2)

        assertEquals(here, book.get(owner, "FARM"))
        assertEquals(listOf("farm"), book.namesOf(owner))
        assertTrue(book.delete(owner, "farm"))
        assertNull(book.get(owner, "farm"))
        assertFalse(book.delete(owner, "farm"))
    }

    @Test
    fun `names that would break the save file are refused`() {
        val book = HomeBook()

        assertEquals(SetHomeResult.InvalidName, book.set(owner, "a.b", here, limit = 5))
        assertEquals(SetHomeResult.InvalidName, book.set(owner, "x".repeat(17), here, limit = 5))
        assertEquals(SetHomeResult.Created, book.set(owner, "우리집", here, limit = 5))
    }

    @Test
    fun `homes survive a save and load`() {
        val file = Files.createTempDirectory("smputil-homes").resolve("homes.yml").toFile()
        val book = HomeBook()
        book.set(owner, DEFAULT_HOME, here, limit = 2)
        book.set(owner, "우리집", there, limit = 2)

        HomeFile(file).save(book.snapshot())
        val loaded = HomeBook(HomeFile(file).load())

        assertEquals(here, loaded.get(owner, DEFAULT_HOME))
        assertEquals(there, loaded.get(owner, "우리집"))
    }
}
