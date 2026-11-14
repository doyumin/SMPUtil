package dev.doyu.smputil.suggestion

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class Suggestion(
    val id: Int,
    val author: UUID,
    /** 제안한 때의 표시 이름. 나중에 닉네임이 바뀌어도 그때의 기록이 남는다. */
    val authorName: String,
    val text: String,
    val createdAt: Instant,
)

sealed interface SuggestResult {
    data class Accepted(val suggestion: Suggestion) : SuggestResult
    data object Empty : SuggestResult
    data class TooLong(val limit: Int) : SuggestResult
    data class Cooldown(val remaining: Duration) : SuggestResult
}

/**
 * 제안 목록과 규칙(길이, 도배 방지). Bukkit과 분리해 단위 테스트한다.
 * 쿨다운은 저장된 제안의 시각에서 계산하므로 재시작해도 이어진다.
 */
class SuggestionBook(
    initial: List<Suggestion>,
    private val clock: Clock,
    private val maxLength: Int,
    private val cooldown: Duration,
) {
    private val suggestions = initial.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    fun add(author: UUID, authorName: String, rawText: String): SuggestResult {
        // 줄바꿈·연속 공백은 Discord 메시지와 YAML을 어지럽히므로 한 칸으로 줄인다.
        val text = rawText.trim().replace(WHITESPACE_RUN, " ")
        if (text.isEmpty()) return SuggestResult.Empty
        if (text.length > maxLength) return SuggestResult.TooLong(maxLength)

        val now = clock.instant()
        val last = suggestions.lastOrNull { it.author == author }
        if (last != null) {
            val remaining = cooldown - Duration.between(last.createdAt, now)
            if (remaining.isPositive) return SuggestResult.Cooldown(remaining)
        }

        val suggestion = Suggestion(nextId++, author, authorName, text, now)
        suggestions += suggestion
        return SuggestResult.Accepted(suggestion)
    }

    fun snapshot(): List<Suggestion> = suggestions.toList()

    private companion object {
        val WHITESPACE_RUN = Regex("\\s+")
    }
}
