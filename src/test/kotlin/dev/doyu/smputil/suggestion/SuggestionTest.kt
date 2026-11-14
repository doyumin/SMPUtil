package dev.doyu.smputil.suggestion

import com.google.gson.JsonParser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): Clock = this
}

class SuggestionTest {
    private val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun book(initial: List<Suggestion> = emptyList()) = SuggestionBook(initial, clock, maxLength = 20, cooldown = Duration.ofSeconds(60))

    @Test
    fun `accepted suggestions get increasing ids and tidy whitespace`() {
        val book = book()

        val first = assertIs<SuggestResult.Accepted>(book.add(alice, "Alice", "  집  \n  늘려 줘 "))
        val second = assertIs<SuggestResult.Accepted>(book.add(bob, "Bob", "상점"))

        assertEquals(1, first.suggestion.id)
        assertEquals("집 늘려 줘", first.suggestion.text)
        assertEquals(2, second.suggestion.id)
    }

    @Test
    fun `blank and too long text is rejected`() {
        val book = book()

        assertEquals(SuggestResult.Empty, book.add(alice, "Alice", "   \n "))
        assertEquals(SuggestResult.TooLong(20), book.add(alice, "Alice", "가".repeat(21)))
        assertTrue(book.snapshot().isEmpty())
    }

    @Test
    fun `same player waits for the cooldown but others do not`() {
        val book = book()
        book.add(alice, "Alice", "하나")

        clock.now += Duration.ofSeconds(20)
        val wait = assertIs<SuggestResult.Cooldown>(book.add(alice, "Alice", "둘"))
        assertEquals(Duration.ofSeconds(40), wait.remaining)
        assertIs<SuggestResult.Accepted>(book.add(bob, "Bob", "다른 사람은 바로"))

        clock.now += Duration.ofSeconds(40)
        assertIs<SuggestResult.Accepted>(book.add(alice, "Alice", "이제 됨"))
    }

    @Test
    fun `cooldown and ids continue after a restart`() {
        val saved = listOf(Suggestion(7, alice, "Alice", "예전 제안", clock.instant()))
        val book = book(saved)

        assertIs<SuggestResult.Cooldown>(book.add(alice, "Alice", "바로 또"))
        assertEquals(8, assertIs<SuggestResult.Accepted>(book.add(bob, "Bob", "새 제안")).suggestion.id)
    }

    @Test
    fun `suggestions survive a save and load`() {
        val file = SuggestionFile(createTempDirectory("smputil-suggestions").resolve("suggestions.yml").toFile())
        val book = book()
        book.add(alice, "홍길동 (Alice)", "줄바꿈: 콜론, \"따옴표\" 포함")
        book.add(bob, "Bob", "둘째")

        file.save(book.snapshot())

        assertEquals(book.snapshot(), file.load())
    }

    @Test
    fun `discord payload carries the text and blocks mentions`() {
        val suggestion = Suggestion(3, alice, "홍길동 (Alice)", "@everyone 집 늘려 줘", Instant.parse("2026-01-01T00:00:00Z"))

        val json = JsonParser.parseString(discordPayload(suggestion)).asJsonObject
        val embed = json.getAsJsonArray("embeds")[0].asJsonObject

        assertEquals("새 제안 #3", embed.get("title").asString)
        assertEquals("@everyone 집 늘려 줘", embed.get("description").asString)
        assertEquals("홍길동 (Alice)", embed.getAsJsonObject("author").get("name").asString)
        assertEquals(0, json.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size())
    }

    @Test
    fun `only discord webhook addresses are accepted`() {
        parseWebhookUrl("https://discord.com/api/webhooks/123/abc")
        assertFailsWith<IllegalArgumentException> { parseWebhookUrl("http://discord.com/api/webhooks/123/abc") }
        assertFailsWith<IllegalArgumentException> { parseWebhookUrl("https://example.com/api/webhooks/123/abc") }
    }
}
