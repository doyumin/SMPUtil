package dev.doyu.smputil.teleport

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.util.UUID

/**
 * 집·스폰·텔레포트 요청이 함께 쓰는 이동. 대기 시간 동안 다른 블록으로 움직이면 취소한다(DESIGN 4장).
 * 시선만 돌리는 것은 움직임으로 치지 않는다.
 */
class Teleporter(private val plugin: Plugin, private val warmup: Duration) : Listener {
    private class Waiting(val task: BukkitTask, val origin: Location)

    private val waiting = mutableMapOf<UUID, Waiting>()

    /** [destination]은 지금 위치를 복사해 넘긴다. 대기 중에 상대가 움직여도 요청 시점의 위치로 가게 하기 위해서다. */
    fun teleport(player: Player, destination: Location, arrivedMessage: Component) {
        waiting.remove(player.uniqueId)?.task?.cancel()
        if (warmup.isZero) {
            go(player, destination, arrivedMessage)
            return
        }
        val ticks = warmup.toMillis() / MILLIS_PER_TICK
        val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            waiting.remove(player.uniqueId)
            go(player, destination, arrivedMessage)
        }, ticks)
        waiting[player.uniqueId] = Waiting(task, player.location)
        player.sendMessage(Component.text("${warmup.toSeconds()}초 뒤에 이동합니다. 움직이면 취소됩니다.", NamedTextColor.YELLOW))
    }

    private fun go(player: Player, destination: Location, arrivedMessage: Component) {
        if (!player.isOnline) return
        player.teleportAsync(destination).thenAccept { moved ->
            player.sendMessage(if (moved) arrivedMessage else Component.text("이동하지 못했습니다.", NamedTextColor.RED))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun cancelOnMove(event: PlayerMoveEvent) {
        val origin = waiting[event.player.uniqueId]?.origin ?: return
        val to = event.to
        if (to.world == origin.world && to.blockX == origin.blockX && to.blockY == origin.blockY && to.blockZ == origin.blockZ) return
        waiting.remove(event.player.uniqueId)?.task?.cancel()
        event.player.sendMessage(Component.text("움직여서 이동이 취소되었습니다.", NamedTextColor.RED))
    }

    @EventHandler
    fun cancelOnQuit(event: PlayerQuitEvent) {
        waiting.remove(event.player.uniqueId)?.task?.cancel()
    }

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}
