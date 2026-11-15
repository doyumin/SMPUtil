package dev.doyu.smputil.stash

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID

sealed interface FindResult {
    data class Found(val chests: Int, val total: Int, val nearestBlocks: Int) : FindResult
    data object NotFound : FindResult
}

/**
 * 근처 상자 중 [Material]이 든 곳을 요청한 사람에게만 보이는 테두리 발광으로 표시한다(DESIGN 11장 "찾기").
 * 표시용 엔티티는 저장되지 않게 만들고, 사라질 때·퇴장·플러그인 종료 때 모두 지운다.
 */
class ChestFinder(
    private val plugin: Plugin,
    private val nearby: NearbyChests,
    private val radius: () -> Int,
    private val highlightSeconds: () -> Int,
) : Listener {
    private class Highlight(val displays: List<BlockDisplay>, val expiry: BukkitTask)

    private val active = HashMap<UUID, Highlight>()

    fun find(player: Player, material: Material): FindResult {
        clear(player.uniqueId)
        val holding = nearby.find(player.location, radius(), skipOpened = false).mapNotNull { chest ->
            val count = chest.inventory.contents.filterNotNull().filter { it.type == material }.sumOf { it.amount }
            if (count > 0) chest to count else null
        }
        if (holding.isEmpty()) return FindResult.NotFound

        val shown = holding.take(MAX_SHOWN)
        val displays = shown.flatMap { (chest, _) -> chest.blocks.map { block -> highlight(player, block.location, block.blockData) } }
        val expiry = plugin.server.scheduler.runTaskLater(plugin, Runnable { clear(player.uniqueId) }, highlightSeconds() * TICKS_PER_SECOND)
        active[player.uniqueId] = Highlight(displays, expiry)

        val nearest = Math.sqrt(holding.first().first.distanceSquared).toInt()
        return FindResult.Found(holding.size, holding.sumOf { it.second }, nearest)
    }

    private fun highlight(player: Player, at: Location, data: BlockData): BlockDisplay {
        val display = spawnOutline(at, data)
        player.showEntity(plugin, display)
        return display
    }

    /** 아무에게도 보이지 않는 상태로 만든다. 보여 줄 사람은 호출한 쪽이 정한다. */
    fun spawnOutline(at: Location, data: BlockData): BlockDisplay =
        at.world.spawn(at, BlockDisplay::class.java) { display ->
            display.block = data
            display.isGlowing = true
            display.glowColorOverride = Color.YELLOW
            // 월드에 저장되지 않고, 요청한 사람 말고는 보이지 않게 한다.
            display.isPersistent = false
            display.isVisibleByDefault = false
            display.transformation = Transformation(Vector3f(-GROW, -GROW, -GROW), AxisAngle4f(), Vector3f(1 + 2 * GROW, 1 + 2 * GROW, 1 + 2 * GROW), AxisAngle4f())
        }

    fun clear(player: UUID) {
        val highlight = active.remove(player) ?: return
        highlight.expiry.cancel()
        for (display in highlight.displays) display.remove()
    }

    fun clearAll() {
        for (player in active.keys.toList()) clear(player)
    }

    @EventHandler
    fun clearOnQuit(event: PlayerQuitEvent) = clear(event.player.uniqueId)

    private companion object {
        const val MAX_SHOWN = 10
        const val TICKS_PER_SECOND = 20L
        /** 상자 그림과 겹쳐 깜빡이지 않도록 살짝 크게 그린다. */
        const val GROW = 0.01f
    }
}
