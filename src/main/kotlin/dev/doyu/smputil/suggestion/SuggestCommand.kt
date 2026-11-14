package dev.doyu.smputil.suggestion

import dev.doyu.smputil.nickname.Nicknames
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

const val SUGGEST_PERMISSION = "smputil.suggest"

/** 인자 없이 치면 입력창을 열고, `/제안 <내용>`은 한 줄로 바로 보낸다. */
class SuggestCommand(
    private val plugin: Plugin,
    private val book: SuggestionBook,
    private val file: SuggestionFile,
    private val webhook: DiscordWebhook,
    private val nicknames: Nicknames,
    private val maxLength: () -> Int,
    private val logger: Logger,
) : BasicCommand {
    /** 콜백의 사용 횟수·만료가 창마다 따로 세어지도록 열 때마다 새로 만든다. 하나를 같이 쓰면 첫 제출 뒤 모두의 [보내기]가 죽는다. */
    private fun newDialog() = suggestionDialog(maxLength()) { audience, text ->
        // 창의 버튼 콜백이 어느 스레드에서 오든 게임 상태는 서버 스레드에서만 건드린다.
        val player = audience as? Player ?: return@suggestionDialog
        plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) submit(player, text) })
    }

    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        if (args.isNotEmpty()) {
            submit(player, args.joinToString(" "))
            return
        }
        val wait = book.cooldownLeft(player.uniqueId)
        if (wait != null) {
            player.sendMessage(cooldownMessage(wait))
            return
        }
        player.showDialog(newDialog())
    }

    private fun submit(player: Player, text: String) {
        val nickname = nicknames.of(player.uniqueId)
        val authorName = if (nickname == null) player.name else "$nickname (${player.name})"
        when (val result = book.add(player.uniqueId, authorName, text)) {
            is SuggestResult.Accepted -> accept(player, result.suggestion)
            is SuggestResult.Empty -> player.sendMessage(Component.text("제안 내용을 적어 주세요.", NamedTextColor.YELLOW))
            is SuggestResult.TooLong -> player.sendMessage(Component.text("제안은 ${result.limit}자 이내로 적어 주세요.", NamedTextColor.RED))
            is SuggestResult.Cooldown -> player.sendMessage(cooldownMessage(result.remaining))
        }
    }

    private fun accept(player: Player, suggestion: Suggestion) {
        try {
            file.save(book.snapshot())
        } catch (e: Exception) {
            // 메모리에는 남아 있으니 알림은 보내고, 저장 실패는 운영자가 알 수 있게 크게 남긴다.
            logger.log(Level.SEVERE, "Could not save suggestions.yml", e)
        }
        webhook.announce(suggestion)
        player.sendMessage(Component.text("제안 #${suggestion.id}을(를) 받았습니다. 고맙습니다!", NamedTextColor.GREEN))
    }

    private fun cooldownMessage(remaining: Duration) =
        Component.text("${remaining.seconds + 1}초 뒤에 다시 제안할 수 있습니다.", NamedTextColor.RED)

    override fun permission(): String = SUGGEST_PERMISSION
}
