package dev.doyu.smputil.request

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

/** 요청을 받은 사람에게 보내는 클릭할 수 있는 [수락] [거절]. */
fun answerButtons(acceptCommand: String, denyCommand: String): Component =
    Component.text()
        .append(button("[수락]", NamedTextColor.GREEN, acceptCommand))
        .append(Component.space())
        .append(button("[거절]", NamedTextColor.RED, denyCommand))
        .build()

private fun button(label: String, color: NamedTextColor, command: String): Component =
    Component.text(label, color, TextDecoration.BOLD)
        .clickEvent(ClickEvent.runCommand(command))
        .hoverEvent(HoverEvent.showText(Component.text(command)))
