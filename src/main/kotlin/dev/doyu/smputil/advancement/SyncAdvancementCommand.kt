package dev.doyu.smputil.advancement

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

const val SYNCADV_PERMISSION = "smputil.syncadv"
const val SYNCADV_TOGGLE_PERMISSION = "smputil.syncadv.toggle"
private val ON = listOf("on", "켜기")
private val OFF = listOf("off", "끄기")

/** 상태 보기는 누구나, 켜고 끄기는 관리자만. */
class SyncAdvancementCommand(private val sync: AdvancementSync) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        val argument = args.firstOrNull()
        if (argument == null) {
            val state = if (sync.isEnabled) Component.text("켜짐", NamedTextColor.GREEN) else Component.text("꺼짐", NamedTextColor.RED)
            sender.sendMessage(Component.text("도전과제 공유: ", NamedTextColor.YELLOW).append(state))
            return
        }
        val enable = when {
            ON.any { it.equals(argument, ignoreCase = true) } -> true
            OFF.any { it.equals(argument, ignoreCase = true) } -> false
            else -> {
                sender.sendMessage(Component.text("사용법: /syncadv [on|off]", NamedTextColor.YELLOW))
                return
            }
        }
        if (!sender.hasPermission(SYNCADV_TOGGLE_PERMISSION)) {
            sender.sendMessage(Component.text("도전과제 공유는 관리자만 켜고 끌 수 있습니다.", NamedTextColor.RED))
            return
        }
        sync.setEnabled(enable)
        sender.sendMessage(Component.text(if (enable) "도전과제 공유를 켰습니다. 접속 중인 플레이어의 진행 상황을 맞췄습니다." else "도전과제 공유를 껐습니다.", NamedTextColor.GREEN))
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        if (args.size > 1 || !source.sender.hasPermission(SYNCADV_TOGGLE_PERMISSION)) return emptyList()
        return (ON + OFF).filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }
    }

    override fun permission(): String = SYNCADV_PERMISSION
}
