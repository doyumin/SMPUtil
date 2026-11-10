package dev.doyu.smputil.nickname

import dev.doyu.smputil.config.NicknameSettings

sealed interface NicknameCheck {
    data object Accepted : NicknameCheck
    data class WrongLength(val lengths: IntRange) : NicknameCheck
    data object InvalidCharacters : NicknameCheck
    /** 다른 플레이어의 진짜 이름이나 닉네임과 같다. 사칭을 막기 위해 거절한다. */
    data object Taken : NicknameCheck
}

class NicknameRules(private val settings: NicknameSettings) {
    /** [otherNames]에는 이 플레이어를 뺀 다른 플레이어들의 진짜 이름과 닉네임을 모두 넣는다. 대소문자는 무시한다. */
    fun check(candidate: String, otherNames: Collection<String>): NicknameCheck {
        // 프로토콜의 16자 제한이 String.length(UTF-16 단위)로 세므로 같은 기준을 쓴다.
        if (candidate.length !in settings.lengths) return NicknameCheck.WrongLength(settings.lengths)
        if (!settings.allowed.matches(candidate)) return NicknameCheck.InvalidCharacters
        if (otherNames.any { it.equals(candidate, ignoreCase = true) }) return NicknameCheck.Taken
        return NicknameCheck.Accepted
    }
}
