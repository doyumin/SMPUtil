package dev.doyu.smputil.config

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.logging.Logger

const val RELOAD_PERMISSION = "smputil.reload"

/**
 * `config.yml`만 다시 읽는다. 플러그인을 통째로 다시 올리는 `/reload`와 다르다(DESIGN 10장).
 * 값이 틀리면 이전 설정을 그대로 쓰고, 어느 키가 틀렸는지 알려 준다.
 */
class ReloadConfigCommand(private val reload: () -> Unit, private val logger: Logger) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        try {
            reload()
        } catch (e: IllegalArgumentException) {
            logger.warning("config.yml reload failed, keeping the previous settings: ${e.message}")
            sender.sendMessage(Component.text("설정이 잘못되어 이전 설정을 그대로 씁니다: ${e.message}", NamedTextColor.RED))
            return
        }
        logger.info("config.yml reloaded by ${sender.name}")
        sender.sendMessage(Component.text("config.yml을 다시 읽었습니다.", NamedTextColor.GREEN))
    }

    override fun permission(): String = RELOAD_PERMISSION
}
