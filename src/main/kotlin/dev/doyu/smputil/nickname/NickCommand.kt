package dev.doyu.smputil.nickname

import dev.doyu.smputil.players.PlayerLookup
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

const val NICK_SELF_PERMISSION = "smputil.nick.self"
const val NICK_OTHERS_PERMISSION = "smputil.nick.others"
private const val RESET = "reset"

/**
 * `/nick <닉네임|reset>`은 본인, `/nick <플레이어> <닉네임|reset>`은 다른 사람.
 * 접속 중인 플레이어만 바꿀 수 있다. Paper는 오프라인 플레이어의 PDC를 읽기 전용으로만 준다.
 */
class NickCommand(
    private val nicknames: Nicknames,
    private val currentRules: () -> NicknameRules,
    private val lookup: PlayerLookup,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        when (args.size) {
            1 -> {
                if (sender !is Player) {
                    sender.sendMessage(Component.text("콘솔에서는 /nick <플레이어> <닉네임|reset> 으로 씁니다.", NamedTextColor.RED))
                    return
                }
                if (!sender.hasPermission(NICK_SELF_PERMISSION)) {
                    sender.sendMessage(Component.text("본인 닉네임을 바꿀 권한이 없습니다. 관리자에게 부탁하세요.", NamedTextColor.RED))
                    return
                }
                change(sender, sender, args[0])
            }
            2 -> {
                if (!sender.hasPermission(NICK_OTHERS_PERMISSION)) {
                    sender.sendMessage(Component.text("다른 플레이어의 닉네임을 바꿀 권한이 없습니다.", NamedTextColor.RED))
                    return
                }
                val target = lookup.findOnline(sender, args[0])
                if (target == null) {
                    sender.sendMessage(Component.text("${args[0]}님은 접속 중이 아닙니다.", NamedTextColor.RED))
                    return
                }
                change(sender, target, args[1])
            }
            else -> sender.sendMessage(Component.text("사용법: /nick <닉네임|reset> 또는 /nick <플레이어> <닉네임|reset>", NamedTextColor.YELLOW))
        }
    }

    private fun change(sender: CommandSender, target: Player, requested: String) {
        val self = sender == target
        if (requested.equals(RESET, ignoreCase = true)) {
            nicknames.set(target, null)
            sender.sendMessage(Component.text(if (self) "닉네임을 지웠습니다." else "${target.name}님의 닉네임을 지웠습니다.", NamedTextColor.GREEN))
            if (!self) target.sendMessage(Component.text("닉네임이 지워졌습니다.", NamedTextColor.YELLOW))
            return
        }
        when (val check = currentRules().check(requested, nicknames.namesTakenBesides(target.uniqueId))) {
            is NicknameCheck.Accepted -> {
                nicknames.set(target, requested)
                sender.sendMessage(Component.text(if (self) "닉네임을 ${requested}(으)로 바꿨습니다." else "${target.name}님의 닉네임을 ${requested}(으)로 바꿨습니다.", NamedTextColor.GREEN))
                if (!self) target.sendMessage(Component.text("닉네임이 ${requested}(으)로 바뀌었습니다.", NamedTextColor.YELLOW))
            }
            is NicknameCheck.WrongLength -> sender.sendMessage(Component.text("닉네임은 ${check.lengths.first}~${check.lengths.last}자여야 합니다.", NamedTextColor.RED))
            is NicknameCheck.InvalidCharacters -> sender.sendMessage(Component.text("닉네임에 쓸 수 없는 글자가 있습니다.", NamedTextColor.RED))
            is NicknameCheck.Taken -> sender.sendMessage(Component.text("다른 플레이어의 이름이나 닉네임과 같아서 쓸 수 없습니다.", NamedTextColor.RED))
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        val sender = source.sender
        return when {
            args.size <= 1 -> {
                val players = if (sender.hasPermission(NICK_OTHERS_PERMISSION)) lookup.suggestOnline(sender, args.firstOrNull().orEmpty(), excludeSelf = false) else emptyList()
                players + listOf(RESET).filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }
            }
            args.size == 2 && sender.hasPermission(NICK_OTHERS_PERMISSION) -> listOf(RESET).filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }

    override fun canUse(sender: CommandSender): Boolean =
        sender.hasPermission(NICK_SELF_PERMISSION) || sender.hasPermission(NICK_OTHERS_PERMISSION)
}
