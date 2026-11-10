package dev.doyu.smputil.nickname

import dev.doyu.smputil.config.NicknameSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class NicknameRulesTest {
    private val rules = NicknameRules(NicknameSettings(1..16, Regex("[가-힣A-Za-z0-9_]+")))

    @Test
    fun `korean nickname within limits is accepted`() {
        assertEquals(NicknameCheck.Accepted, rules.check("홍길동", listOf("Steve")))
        assertEquals(NicknameCheck.Accepted, rules.check("가".repeat(16), emptyList()))
    }

    @Test
    fun `length and characters are checked`() {
        assertEquals(NicknameCheck.WrongLength(1..16), rules.check("", emptyList()))
        assertEquals(NicknameCheck.WrongLength(1..16), rules.check("가".repeat(17), emptyList()))
        assertEquals(NicknameCheck.InvalidCharacters, rules.check("홍 길동", emptyList()))
        assertEquals(NicknameCheck.InvalidCharacters, rules.check("ㅋㅋ", emptyList()))
    }

    @Test
    fun `names of other players are taken regardless of case`() {
        assertEquals(NicknameCheck.Taken, rules.check("steve", listOf("Steve")))
        assertEquals(NicknameCheck.Taken, rules.check("철수", listOf("Alex", "철수")))
    }
}
