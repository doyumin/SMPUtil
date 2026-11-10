package dev.doyu.smputil.players

import dev.doyu.smputil.nickname.Nicknames
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server

const val PLAYERS_PERMISSION = "smputil.players"
const val PLAYERS_UUID_PERMISSION = "smputil.players.uuid"

/** 닉네임(진짜 이름), 핑. 볼 수 없는 투명화 플레이어는 빼고, 수도 그 기준으로 센다(DESIGN 8장). */
class PlayersCommand(private val server: Server, private val nicknames: Nicknames) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        val visible = server.onlinePlayers.filter { sender.canSeePlayer(it) }.sortedBy { nicknames.shownName(it).lowercase() }
        val showUuid = sender.hasPermission(PLAYERS_UUID_PERMISSION)
        sender.sendMessage(Component.text("접속자 ${visible.size}명", NamedTextColor.YELLOW))
        visible.forEachIndexed { index, player ->
            val nickname = nicknames.of(player.uniqueId)
            val name = if (nickname == null) player.name else "$nickname (${player.name})"
            sender.sendMessage(
                Component.text("${index + 1}. ", NamedTextColor.GOLD)
                    .append(Component.text(name, NamedTextColor.GREEN))
                    .append(Component.text("  ${player.ping}ms", NamedTextColor.GRAY)),
            )
            if (showUuid) {
                sender.sendMessage(
                    Component.text("   ${player.uniqueId}", NamedTextColor.DARK_GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("눌러서 복사")))
                        .clickEvent(ClickEvent.copyToClipboard(player.uniqueId.toString())),
                )
            }
        }
    }

    override fun permission(): String = PLAYERS_PERMISSION
}
