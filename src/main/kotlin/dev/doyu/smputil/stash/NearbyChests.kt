package dev.doyu.smputil.stash

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.TileState
import org.bukkit.inventory.Inventory

/** 근처 상자 하나. 큰 상자는 두 블록이 같은 인벤토리를 돌려주므로 하나로 센다. */
class NearbyChest(val inventory: Inventory, val blocks: List<Block>, val distanceSquared: Double)

sealed interface ChestInspection {
    data object NotAChest : ChestInspection
    /** 상자이지만 잠겼거나 데이터가 붙었거나(또는 열려 있어) 건드릴 수 없다. */
    data object OffLimits : ChestInspection
    data class Usable(val inventory: Inventory) : ChestInspection
}

/**
 * 상자 정리 기능(모으기·정렬·찾기)이 함께 쓰는 "어떤 상자를 건드려도 되는가"(DESIGN 11장).
 * `CHEST`·`BARREL`만 쓰고, 잠긴 상자와 플러그인 데이터(PDC)가 붙은 상자는 늘 제외한다.
 */
class NearbyChests {
    /** [skipOpened]가 참이면 지금 누가 열어 둔 상자는 뺀다(옮기거나 재배열하면 보는 사람 화면과 어긋난다). 읽기만 하면 거짓으로 둔다. */
    fun find(origin: Location, radius: Int, skipOpened: Boolean): List<NearbyChest> {
        val world = origin.world
        val reachSquared = radius.toDouble() * radius
        val found = LinkedHashMap<InventoryKey, Builder>()
        for (chunkX in chunkRange(origin.blockX, radius)) {
            for (chunkZ in chunkRange(origin.blockZ, radius)) {
                // 이 명령 때문에 청크를 불러오지 않는다.
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                for (state in world.getChunkAt(chunkX, chunkZ).getTileEntities(false)) {
                    if (state !is Container || !isUsable(state, skipOpened)) continue
                    val distanceSquared = state.location.add(CENTER, CENTER, CENTER).distanceSquared(origin)
                    if (distanceSquared > reachSquared) continue
                    val inventory = state.inventory
                    val key = inventory.location?.let { InventoryKey(it.x, it.y, it.z) } ?: continue
                    val entry = found.getOrPut(key) { Builder(inventory) }
                    entry.blocks += state.block
                    if (distanceSquared < entry.distanceSquared) entry.distanceSquared = distanceSquared
                }
            }
        }
        return found.values.map { NearbyChest(it.inventory, it.blocks, it.distanceSquared) }.sortedBy { it.distanceSquared }
    }

    fun inspect(block: Block, skipOpened: Boolean): ChestInspection {
        if (block.type != Material.CHEST && block.type != Material.BARREL) return ChestInspection.NotAChest
        val state = block.getState(false) as? Container ?: return ChestInspection.NotAChest
        return if (isUsable(state, skipOpened)) ChestInspection.Usable(state.inventory) else ChestInspection.OffLimits
    }

    private fun isUsable(state: Container, skipOpened: Boolean): Boolean {
        if (state.type != Material.CHEST && state.type != Material.BARREL) return false
        if (state.isLocked) return false
        // 다른 기능이 특수하게 쓰는 상자(예: 판매 상자)는 데이터가 붙어 있다. 코드로 알지 않고 데이터로 비켜 간다.
        if ((state as TileState).persistentDataContainer.keys.isNotEmpty()) return false
        return !skipOpened || state.inventory.viewers.isEmpty()
    }

    private data class InventoryKey(val x: Double, val y: Double, val z: Double)

    private class Builder(val inventory: Inventory) {
        val blocks = mutableListOf<Block>()
        var distanceSquared = Double.MAX_VALUE
    }

    private companion object {
        const val CENTER = 0.5
    }
}
