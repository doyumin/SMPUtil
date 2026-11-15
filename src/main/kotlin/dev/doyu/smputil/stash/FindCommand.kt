package dev.doyu.smputil.stash

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player

const val FIND_PERMISSION = "smputil.find"

private const val SUGGESTION_LIMIT = 50

/** 인자 없이 치면 손에 든 아이템을, 아이템 ID(`diamond`)를 주면 그것을 찾는다. 한글 이름은 클라이언트 번역이라 서버가 모른다. */
class FindCommand(private val finder: ChestFinder) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        val material = if (args.isEmpty()) player.inventory.itemInMainHand.type else Material.matchMaterial(args[0])
        if (material == null || !material.isItem || material.isAir) {
            val hint = if (args.isEmpty()) "손에 아이템을 들거나 /찾기 <아이템 ID>(예: diamond)를 쓰세요." else "${args[0]}(이)라는 아이템을 모릅니다. Tab으로 아이템 ID를 확인하세요."
            player.sendMessage(Component.text(hint, NamedTextColor.YELLOW))
            return
        }

        val name = Component.translatable(material.translationKey(), NamedTextColor.WHITE)
        val message = when (val result = finder.find(player, material)) {
            is FindResult.Found -> Component.text("", NamedTextColor.GREEN).append(name)
                .append(Component.text("이(가) 든 상자 ${result.chests}개를 찾았습니다 (합계 ${result.total}개, 가장 가까운 곳 ${result.nearestBlocks}블록). 노란 테두리를 따라가세요.", NamedTextColor.GREEN))
            is FindResult.NotFound -> Component.text("근처에 ", NamedTextColor.YELLOW).append(name).append(Component.text("이(가) 든 상자가 없습니다.", NamedTextColor.YELLOW))
        }
        player.sendMessage(message)
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        if (args.size > 1) return emptyList()
        val typed = args.firstOrNull().orEmpty().removePrefix("minecraft:")
        return Material.entries.asSequence()
            .filter { it.isItem && !it.isAir && !it.isLegacy }
            .map { it.key.key }
            .filter { it.startsWith(typed, ignoreCase = true) }
            .take(SUGGESTION_LIMIT)
            .toList()
    }

    override fun permission(): String = FIND_PERMISSION
}
