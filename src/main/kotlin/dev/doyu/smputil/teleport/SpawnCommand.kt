package dev.doyu.smputil.teleport

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server
import org.bukkit.entity.Player

const val SPAWN_PERMISSION = "smputil.spawn"
private const val WORLD_SPAWN = "world"

/** `/spawn`은 개인 리스폰 지점(침대·리스폰 정박기)이 있으면 그곳, 없으면 월드 스폰. `/spawn world`는 항상 월드 스폰. */
class SpawnCommand(private val server: Server, private val teleporter: Teleporter) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
            return
        }
        val worldOnly = args.firstOrNull()?.equals(WORLD_SPAWN, ignoreCase = true) == true
        // true: 침대가 부서졌거나 막혔으면 null이 된다. 리스폰 정박기 충전은 쓰지 않는다(Paper 26.3 CraftPlayer.getRespawnLocation에서 확인).
        val personal = if (worldOnly) null else player.getRespawnLocation(true)
        if (personal != null) {
            teleporter.teleport(player, personal, Component.text("리스폰 지점으로 이동했습니다.", NamedTextColor.GREEN))
        } else {
            teleporter.teleport(player, server.respawnWorld.spawnLocation, Component.text("월드 스폰으로 이동했습니다.", NamedTextColor.GREEN))
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) listOf(WORLD_SPAWN).filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) } else emptyList()

    override fun permission(): String = SPAWN_PERMISSION
}
