package dev.doyu.smputil.stash

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player

const val STASH_PERMISSION = "smputil.stash"

private const val SHOWN_KINDS = 5

class StashCommand(private val stash: ChestStash) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        val message = when (val result = stash.stash(player.inventory, player.location)) {
            is StashResult.Stashed -> stashedMessage(result)
            is StashResult.NothingToCarry -> Component.text("옮길 아이템이 없습니다. (핫바와 쌓이지 않는 아이템은 옮기지 않습니다)", NamedTextColor.YELLOW)
            is StashResult.NoChestNearby -> Component.text("근처에 쓸 수 있는 상자가 없습니다.", NamedTextColor.YELLOW)
            is StashResult.NoRoomOrMatch -> Component.text("근처 상자 중 같은 아이템이 들어 있고 자리가 남은 곳이 없습니다.", NamedTextColor.YELLOW)
        }
        player.sendMessage(message)
    }

    private fun stashedMessage(result: StashResult.Stashed): Component {
        val summary = result.tally.summary(SHOWN_KINDS)
        var message = Component.text("상자 ${result.chests}개에 아이템 ${summary.total}개를 모았습니다: ", NamedTextColor.GREEN)
        summary.shown.forEachIndexed { index, (material, amount) ->
            if (index > 0) message = message.append(Component.text(", ", NamedTextColor.GRAY))
            message = message.append(materialName(material)).append(Component.text(" ×$amount", NamedTextColor.GRAY))
        }
        if (summary.hiddenKinds > 0) message = message.append(Component.text(" 외 ${summary.hiddenKinds}종", NamedTextColor.GRAY))
        return message
    }

    /** 클라이언트 언어로 보이도록 번역 키로 보낸다. */
    private fun materialName(material: Material): Component =
        Component.translatable(material.translationKey(), NamedTextColor.WHITE)

    override fun permission(): String = STASH_PERMISSION
}
