package dev.doyu.smputil.home

import dev.doyu.smputil.config.HomeSettings
import dev.doyu.smputil.teleport.Teleporter
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.entity.Player
import java.util.logging.Level
import java.util.logging.Logger

const val HOME_PERMISSION = "smputil.home"

/** 세 명령어가 집 목록·저장·설정을 함께 쓴다. */
class Homes(
    private val book: HomeBook,
    private val file: HomeFile,
    val settings: HomeSettings,
    private val server: Server,
    private val logger: Logger,
) {
    fun set(player: Player, name: String): SetHomeResult {
        val location = player.location
        val result = book.set(player.uniqueId, name, HomePoint(location.world.name, location.x, location.y, location.z, location.yaw, location.pitch), settings.maxHomes)
        if (result == SetHomeResult.Created || result == SetHomeResult.Moved) save()
        return result
    }

    fun delete(player: Player, name: String): Boolean {
        val deleted = book.delete(player.uniqueId, name)
        if (deleted) save()
        return deleted
    }

    fun locationOf(player: Player, name: String): HomeLocation {
        val home = book.get(player.uniqueId, name) ?: return HomeLocation.Missing
        val world = server.getWorld(home.world) ?: return HomeLocation.WorldGone(home.world)
        return HomeLocation.Found(Location(world, home.x, home.y, home.z, home.yaw, home.pitch))
    }

    fun namesOf(player: Player): List<String> = book.namesOf(player.uniqueId)

    private fun save() {
        try {
            file.save(book.snapshot())
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Could not save homes.yml", e)
        }
    }
}

sealed interface HomeLocation {
    data class Found(val location: Location) : HomeLocation
    data class WorldGone(val world: String) : HomeLocation
    data object Missing : HomeLocation
}

/** 꺼져 있거나 플레이어가 아니면 메시지를 보내고 null. */
private fun usableBy(source: CommandSourceStack, homes: Homes): Player? {
    val player = source.sender as? Player
    if (player == null) {
        source.sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.", NamedTextColor.RED))
        return null
    }
    if (!homes.settings.enabled) {
        player.sendMessage(Component.text("이 서버에서는 집 기능이 꺼져 있습니다.", NamedTextColor.RED))
        return null
    }
    return player
}

class HomeCommand(private val homes: Homes, private val teleporter: Teleporter) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = usableBy(source, homes) ?: return
        val name = args.firstOrNull() ?: DEFAULT_HOME
        when (val home = homes.locationOf(player, name)) {
            is HomeLocation.Found -> teleporter.teleport(player, home.location, Component.text("${name}(으)로 이동했습니다.", NamedTextColor.GREEN))
            is HomeLocation.WorldGone -> player.sendMessage(Component.text("집이 있던 월드(${home.world})가 없어졌습니다.", NamedTextColor.RED))
            is HomeLocation.Missing -> {
                val names = homes.namesOf(player)
                val hint = if (names.isEmpty()) "/sethome 으로 집을 정하세요." else "내 집: ${names.joinToString(", ")}"
                player.sendMessage(Component.text("${name}(이)라는 집이 없습니다. $hint", NamedTextColor.RED))
            }
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        val player = source.sender as? Player ?: return emptyList()
        return if (args.size <= 1) homes.namesOf(player).filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) } else emptyList()
    }

    override fun permission(): String = HOME_PERMISSION
}

class SetHomeCommand(private val homes: Homes) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = usableBy(source, homes) ?: return
        val name = args.firstOrNull() ?: DEFAULT_HOME
        val message = when (val result = homes.set(player, name)) {
            is SetHomeResult.Created -> Component.text("여기를 ${name}(으)로 정했습니다.", NamedTextColor.GREEN)
            is SetHomeResult.Moved -> Component.text("${name}의 위치를 여기로 옮겼습니다.", NamedTextColor.GREEN)
            is SetHomeResult.LimitReached -> Component.text("집은 ${result.limit}개까지 정할 수 있습니다. /delhome 으로 지우거나 같은 이름으로 옮기세요.", NamedTextColor.RED)
            is SetHomeResult.InvalidName -> Component.text("집 이름은 한글·영문·숫자·밑줄 16자 이내입니다.", NamedTextColor.RED)
        }
        player.sendMessage(message)
    }

    override fun permission(): String = HOME_PERMISSION
}

class DeleteHomeCommand(private val homes: Homes) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = usableBy(source, homes) ?: return
        val name = args.firstOrNull()
        if (name == null) {
            player.sendMessage(Component.text("사용법: /delhome <이름>", NamedTextColor.YELLOW))
            return
        }
        if (homes.delete(player, name)) {
            player.sendMessage(Component.text("${name}을(를) 지웠습니다.", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("${name}(이)라는 집이 없습니다.", NamedTextColor.RED))
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        val player = source.sender as? Player ?: return emptyList()
        return if (args.size <= 1) homes.namesOf(player).filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) } else emptyList()
    }

    override fun permission(): String = HOME_PERMISSION
}
