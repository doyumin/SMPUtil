package dev.doyu.smputil.teleport

import dev.doyu.smputil.nickname.Nicknames
import dev.doyu.smputil.players.PlayerLookup
import dev.doyu.smputil.request.PendingRequest
import dev.doyu.smputil.request.RequestBoard
import dev.doyu.smputil.request.SendResult
import dev.doyu.smputil.request.TakeResult
import dev.doyu.smputil.request.answerButtons
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

const val TPA_PERMISSION = "smputil.tpa"

/**
 * `/tpa`: 요청자가 대상에게 간다(DESIGN 5장).
 * 목적지는 수락하는 순간의 대상 위치다. 대기 시간 동안 대상이 움직여도 따라가지 않는다.
 * 어느 한쪽이 나가면 요청은 사라진다. 나간 사람을 옮길 수 없고, 나간 사람에게 갈 수도 없기 때문이다.
 */
class TeleportRequests(
    private val board: RequestBoard,
    private val server: Server,
    private val nicknames: Nicknames,
    private val teleporter: Teleporter,
) : Listener {
    fun request(sender: Player, target: Player) {
        when (val result = board.send(sender.uniqueId, target.uniqueId)) {
            is SendResult.Sent -> {
                result.replaced?.let { replaced -> server.getPlayer(replaced.to)?.sendMessage(Component.text("${nicknames.shownName(sender)}님이 텔레포트 요청을 취소했습니다.", NamedTextColor.GRAY)) }
                sender.sendMessage(Component.text("${nicknames.shownName(target)}님에게 텔레포트를 요청했습니다.", NamedTextColor.GREEN))
                target.sendMessage(Component.text("${nicknames.shownName(sender)}님이 나에게 오려고 합니다. ", NamedTextColor.YELLOW).append(answerButtons("/tpaccept", "/tpdeny")))
            }
            is SendResult.ToSelf -> sender.sendMessage(Component.text("자기 자신에게는 요청할 수 없습니다.", NamedTextColor.RED))
            is SendResult.TargetBusy -> sender.sendMessage(Component.text("${nicknames.shownName(target)}님은 다른 요청에 답하는 중입니다. 잠시 뒤에 다시 하세요.", NamedTextColor.RED))
        }
    }

    fun accept(target: Player) {
        when (val result = board.take(target.uniqueId)) {
            is TakeResult.Found -> {
                val requester = server.getPlayer(result.request.from)
                if (requester == null) {
                    target.sendMessage(Component.text("요청한 사람이 접속 중이 아닙니다.", NamedTextColor.RED))
                    return
                }
                target.sendMessage(Component.text("${nicknames.shownName(requester)}님의 요청을 수락했습니다.", NamedTextColor.GREEN))
                requester.sendMessage(Component.text("${nicknames.shownName(target)}님이 요청을 수락했습니다.", NamedTextColor.GREEN))
                teleporter.teleport(requester, target.location, Component.text("${nicknames.shownName(target)}님에게 이동했습니다.", NamedTextColor.GREEN))
            }
            is TakeResult.Expired -> {
                target.sendMessage(Component.text("요청이 이미 만료되었습니다.", NamedTextColor.RED))
                notifyExpired(result.request)
            }
            is TakeResult.Missing -> target.sendMessage(Component.text("받은 텔레포트 요청이 없습니다.", NamedTextColor.RED))
        }
    }

    fun deny(target: Player) {
        when (val result = board.take(target.uniqueId)) {
            is TakeResult.Found -> {
                target.sendMessage(Component.text("요청을 거절했습니다.", NamedTextColor.YELLOW))
                server.getPlayer(result.request.from)?.sendMessage(Component.text("${nicknames.shownName(target)}님이 텔레포트 요청을 거절했습니다.", NamedTextColor.RED))
            }
            is TakeResult.Expired -> {
                target.sendMessage(Component.text("요청이 이미 만료되었습니다.", NamedTextColor.RED))
                notifyExpired(result.request)
            }
            is TakeResult.Missing -> target.sendMessage(Component.text("받은 텔레포트 요청이 없습니다.", NamedTextColor.RED))
        }
    }

    /** 1초마다 부른다. */
    fun sweepExpired() {
        board.removeExpired().forEach(::notifyExpired)
    }

    @EventHandler
    fun dropOnQuit(event: PlayerQuitEvent) {
        val leaving = event.player
        for (request in board.removeInvolving(leaving.uniqueId)) {
            val other = if (request.from == leaving.uniqueId) request.to else request.from
            server.getPlayer(other)?.sendMessage(Component.text("${nicknames.shownName(leaving)}님이 나가서 텔레포트 요청이 취소되었습니다.", NamedTextColor.GRAY))
        }
    }

    private fun notifyExpired(request: PendingRequest) {
        val target = server.getPlayer(request.to)
        val targetName = target?.let(nicknames::shownName) ?: "상대"
        server.getPlayer(request.from)?.sendMessage(Component.text("${targetName}님에게 보낸 텔레포트 요청이 만료되었습니다.", NamedTextColor.GRAY))
    }
}

class TpaCommand(private val requests: TeleportRequests, private val lookup: PlayerLookup) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender as? Player
        if (sender == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        if (args.size != 1) {
            sender.sendMessage(Component.text("사용법: /tpa <플레이어>", NamedTextColor.YELLOW))
            return
        }
        val target = lookup.findOnline(sender, args[0])
        if (target == null) {
            sender.sendMessage(Component.text("${args[0]}님은 접속 중이 아닙니다.", NamedTextColor.RED))
            return
        }
        requests.request(sender, target)
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) lookup.suggestOnline(source.sender, args.firstOrNull().orEmpty()) else emptyList()

    override fun permission(): String = TPA_PERMISSION
}

class TpAcceptCommand(private val requests: TeleportRequests) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player ?: return source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
        requests.accept(player)
    }

    override fun permission(): String = TPA_PERMISSION
}

class TpDenyCommand(private val requests: TeleportRequests) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player ?: return source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
        requests.deny(player)
    }

    override fun permission(): String = TPA_PERMISSION
}
