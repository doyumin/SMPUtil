package dev.doyu.smputil.nickname

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.TranslationArgument

/**
 * 서버가 만든 메시지(사망·입장·퇴장) 안에서 진짜 이름만 담은 글자 조각을 닉네임으로 바꾼다.
 * 바닐라는 이 메시지들에 진짜 이름(GameProfile)을 넣는다. 문자열 치환이 아니라 조각 단위로 바꾸는 이유는
 * 진짜 이름이 "a"처럼 짧을 때 다른 단어 속 글자까지 바뀌지 않게 하기 위해서다.
 */
fun Component.withShownName(realName: String, shownName: String): Component {
    if (realName == shownName) return this
    var result = this
    if (result is TextComponent && result.content() == realName) result = result.content(shownName)
    if (result is TranslatableComponent) {
        result = result.arguments(
            result.arguments().map { argument ->
                val value = argument.value()
                if (value is Component) TranslationArgument.component(value.withShownName(realName, shownName)) else argument
            },
        )
    }
    return result.children(result.children().map { it.withShownName(realName, shownName) })
}
