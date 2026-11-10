package dev.doyu.smputil.trade

import dev.doyu.smputil.players.PlayerLookup
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

const val TRADE_PERMISSION = "smputil.trade"
private val ACCEPT = listOf("accept", "수락")
private val DENY = listOf("deny", "거절")

/** `/trade <플레이어>`, `/trade accept`, `/trade deny`. */
class TradeCommand(private val trades: Trades, private val lookup: PlayerLookup) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender as? Player
        if (sender == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        if (args.size != 1) {
            sender.sendMessage(Component.text("사용법: /trade <플레이어|accept|deny>", NamedTextColor.YELLOW))
            return
        }
        val argument = args[0]
        when {
            ACCEPT.any { it.equals(argument, ignoreCase = true) } -> trades.accept(sender)
            DENY.any { it.equals(argument, ignoreCase = true) } -> trades.deny(sender)
            else -> {
                val target = lookup.findOnline(sender, argument)
                if (target == null) {
                    sender.sendMessage(Component.text("${argument}님은 접속 중이 아닙니다.", NamedTextColor.RED))
                    return
                }
                trades.request(sender, target)
            }
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        if (args.size > 1) return emptyList()
        val prefix = args.firstOrNull().orEmpty()
        return lookup.suggestOnline(source.sender, prefix) + (ACCEPT + DENY).filter { it.startsWith(prefix, ignoreCase = true) }
    }

    override fun permission(): String = TRADE_PERMISSION
}
