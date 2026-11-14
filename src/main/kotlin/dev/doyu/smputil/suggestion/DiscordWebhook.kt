package dev.doyu.smputil.suggestion

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

private val WEBHOOK_PREFIXES = listOf("https://discord.com/api/webhooks/", "https://discordapp.com/api/webhooks/")

private const val EMBED_COLOR = 0x57F287

/** 설정의 웹훅 주소를 검사한다. 틀리면 시작 시 실패시키려고 예외를 던진다. */
fun parseWebhookUrl(value: String): URI {
    require(WEBHOOK_PREFIXES.any { value.startsWith(it) }) { "must start with https://discord.com/api/webhooks/" }
    return URI.create(value)
}

/** 새 제안을 디스코드 채널에 알린다. 웹훅 주소 자체가 비밀이므로 로그에 남기지 않는다. */
class DiscordWebhook(private val url: URI, private val logger: Logger) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** 서버 틱을 막지 않게 비동기로 보낸다. 실패해도 제안은 이미 저장돼 있다. */
    fun announce(suggestion: Suggestion) {
        val request = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(discordPayload(suggestion)))
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete { response, error ->
            when {
                error != null -> logger.log(Level.WARNING, "Could not send suggestion #${suggestion.id} to Discord: ${error.javaClass.simpleName}")
                response.statusCode() !in 200..299 -> logger.warning("Discord rejected suggestion #${suggestion.id}: HTTP ${response.statusCode()}")
            }
        }
    }
}

fun discordPayload(suggestion: Suggestion): String {
    val embed = JsonObject().apply {
        addProperty("title", "새 제안 #${suggestion.id}")
        addProperty("description", suggestion.text)
        add("author", JsonObject().apply { addProperty("name", suggestion.authorName) })
        addProperty("timestamp", suggestion.createdAt.toString())
        addProperty("color", EMBED_COLOR)
    }
    return JsonObject().apply {
        add("embeds", JsonArray().apply { add(embed) })
        // 제안 내용에 @everyone 등이 있어도 실제 멘션이 되지 않게 한다.
        add("allowed_mentions", JsonObject().apply { add("parse", JsonArray()) })
    }.toString()
}
