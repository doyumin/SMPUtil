package dev.doyu.smputil.suggestion

import dev.doyu.smputil.nickname.Nicknames
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.logging.Level
import java.util.logging.Logger

const val SUGGEST_PERMISSION = "smputil.suggest"

class SuggestCommand(
    private val book: SuggestionBook,
    private val file: SuggestionFile,
    /** null이면 디스코드 알림을 쓰지 않는다. */
    private val webhook: DiscordWebhook?,
    private val nicknames: Nicknames,
    private val logger: Logger,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        if (args.isEmpty()) {
            player.sendMessage(Component.text("사용법: /제안 <내용>", NamedTextColor.YELLOW))
            return
        }

        val nickname = nicknames.of(player.uniqueId)
        val authorName = if (nickname == null) player.name else "$nickname (${player.name})"
        when (val result = book.add(player.uniqueId, authorName, args.joinToString(" "))) {
            is SuggestResult.Accepted -> accept(player, result.suggestion)
            is SuggestResult.Empty -> player.sendMessage(Component.text("제안 내용을 적어 주세요.", NamedTextColor.YELLOW))
            is SuggestResult.TooLong -> player.sendMessage(Component.text("제안은 ${result.limit}자 이내로 적어 주세요.", NamedTextColor.RED))
            is SuggestResult.Cooldown -> player.sendMessage(Component.text("${result.remaining.seconds + 1}초 뒤에 다시 제안할 수 있습니다.", NamedTextColor.RED))
        }
    }

    private fun accept(player: Player, suggestion: Suggestion) {
        try {
            file.save(book.snapshot())
        } catch (e: Exception) {
            // 메모리에는 남아 있으니 알림은 보내고, 저장 실패는 운영자가 알 수 있게 크게 남긴다.
            logger.log(Level.SEVERE, "Could not save suggestions.yml", e)
        }
        webhook?.announce(suggestion)
        player.sendMessage(Component.text("제안 #${suggestion.id}을(를) 받았습니다. 고맙습니다!", NamedTextColor.GREEN))
    }

    override fun permission(): String = SUGGEST_PERMISSION
}
