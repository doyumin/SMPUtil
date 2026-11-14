package dev.doyu.smputil.config

import org.bukkit.configuration.ConfigurationSection
import java.time.Duration

data class SmpSettings(
    val nickname: NicknameSettings,
    val home: HomeSettings,
    val teleportWarmup: Duration,
    val tpaExpiry: Duration,
    val tradeExpiry: Duration,
    /** null이면 거리 제한이 없다. */
    val tradeMaxDistance: Double?,
)

data class NicknameSettings(
    val lengths: IntRange,
    val allowed: Regex,
)

data class HomeSettings(
    val enabled: Boolean,
    val maxHomes: Int,
)

/** 마인크래프트 프로토콜이 플레이어 이름에 허용하는 최대 길이(ByteBufCodecs.PLAYER_NAME = stringUtf8(16), Paper 26.3 서버 코드에서 확인). */
const val PROTOCOL_NAME_LIMIT = 16

/**
 * 틀린 값을 조용히 기본값으로 덮지 않고, 어떤 키가 틀렸는지 밝혀 시작 시 실패한다.
 * 빠진 키는 Bukkit이 jar에 든 config.yml 값으로 채우므로 새 버전에서 키가 늘어도 기존 설정 파일이 그대로 동작한다.
 */
fun readSettings(config: ConfigurationSection): SmpSettings {
    val minLength = config.requireWholeNumber("nickname.min-length")
    val maxLength = config.requireWholeNumber("nickname.max-length")
    require(minLength >= 1) { "nickname.min-length must be at least 1" }
    require(maxLength >= minLength) { "nickname.max-length must not be less than nickname.min-length" }
    // 16자를 넘는 이름은 패킷 인코딩에서 예외가 나 받는 쪽 접속이 끊긴다.
    require(maxLength <= PROTOCOL_NAME_LIMIT) { "nickname.max-length must be at most $PROTOCOL_NAME_LIMIT (protocol limit)" }
    val allowedPattern = config.requireString("nickname.allowed")
    val allowed = try {
        Regex(allowedPattern)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("nickname.allowed is not a valid regular expression: ${e.message}", e)
    }

    require(config.isBoolean("home.enabled")) { "home.enabled must be true or false" }
    val maxHomes = config.requireWholeNumber("home.max-homes")
    require(maxHomes >= 1) { "home.max-homes must be at least 1" }

    val warmup = config.requireWholeNumber("teleport.warmup-seconds")
    require(warmup >= 0) { "teleport.warmup-seconds must not be negative" }

    val maxDistance = config.requireWholeNumber("trade.max-distance")
    require(maxDistance >= 0) { "trade.max-distance must not be negative" }

    return SmpSettings(
        nickname = NicknameSettings(minLength..maxLength, allowed),
        home = HomeSettings(config.getBoolean("home.enabled"), maxHomes),
        teleportWarmup = Duration.ofSeconds(warmup.toLong()),
        tpaExpiry = Duration.ofSeconds(config.requirePositive("tpa.expire-seconds").toLong()),
        tradeExpiry = Duration.ofSeconds(config.requirePositive("trade.expire-seconds").toLong()),
        tradeMaxDistance = if (maxDistance == 0) null else maxDistance.toDouble(),
    )
}

private fun ConfigurationSection.requireString(path: String): String =
    requireNotNull(getString(path)?.takeIf { it.isNotBlank() }) { "$path is missing" }

private fun ConfigurationSection.requireWholeNumber(path: String): Int {
    require(isInt(path)) { "$path must be a whole number" }
    return getInt(path)
}

private fun ConfigurationSection.requirePositive(path: String): Int {
    val value = requireWholeNumber(path)
    require(value > 0) { "$path must be positive" }
    return value
}
