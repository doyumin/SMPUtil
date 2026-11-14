package dev.doyu.smputil.suggestion

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server

/** 제안 명령어가 있다는 것을 주기적으로 알린다. 보낼 사람이 없으면 아무것도 하지 않는다. */
class SuggestionReminder(private val server: Server) {
    private val message = Component.text("불편한 점이나 바라는 것이 있나요? ", NamedTextColor.GRAY)
        .append(
            Component.text("/제안", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("눌러서 제안하기")))
                .clickEvent(ClickEvent.runCommand("/제안")),
        )
        .append(Component.text(" 을 눌러 남겨 주세요.", NamedTextColor.GRAY))

    fun remind() {
        // 제안을 못 하는 사람에게 쓸 수 없는 명령어를 안내하지 않는다.
        val audience = server.onlinePlayers.filter { it.hasPermission(SUGGEST_PERMISSION) }
        for (player in audience) player.sendMessage(message)
    }
}
