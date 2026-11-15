package dev.doyu.smputil.stash

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

const val SORT_PERMISSION = "smputil.sort"

private const val REACH = 5.0

/** 바라보는 상자를 정렬한다. 상자를 보고 있지 않으면 내 주 인벤토리 칸을 정렬한다(DESIGN 11장). */
class SortCommand(private val nearby: NearbyChests, private val sorter: InventorySorter) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        val looked = player.rayTraceBlocks(REACH)?.hitBlock
        val inspection = if (looked == null) ChestInspection.NotAChest else nearby.inspect(looked, skipOpened = true)
        when (inspection) {
            is ChestInspection.Usable -> report(player, "상자", sorter.sort(inspection.inventory, 0 until inspection.inventory.size))
            is ChestInspection.OffLimits -> player.sendMessage(Component.text("이 상자는 정렬할 수 없습니다. (잠겼거나, 다른 사람이 열어 뒀거나, 특수 상자입니다)", NamedTextColor.RED))
            is ChestInspection.NotAChest -> report(player, "인벤토리", sorter.sort(player.inventory, PLAYER_MAIN_SLOTS))
        }
    }

    private fun report(player: Player, target: String, outcome: SortOutcome) {
        val message = when (outcome) {
            is SortOutcome.Sorted -> Component.text("${target}를 정렬했습니다.", NamedTextColor.GREEN)
            is SortOutcome.AlreadySorted -> Component.text("${target}는 이미 정렬되어 있습니다.", NamedTextColor.YELLOW)
            is SortOutcome.Refused -> Component.text("${target}를 안전하게 정렬할 수 없어 그대로 두었습니다.", NamedTextColor.RED)
        }
        player.sendMessage(message)
    }

    override fun permission(): String = SORT_PERMISSION
}
