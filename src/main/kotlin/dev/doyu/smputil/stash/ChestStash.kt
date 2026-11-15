package dev.doyu.smputil.stash

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.block.TileState
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

sealed interface StashResult {
    data class Stashed(val chests: Int, val tally: StashTally<Material>) : StashResult
    /** 옮길 만한 아이템(핫바 밖, 쌓을 수 있는 것)이 없다. */
    data object NothingToCarry : StashResult
    data object NoChestNearby : StashResult
    /** 상자는 있지만 같은 아이템이 든 곳이 없거나 가득 차 하나도 넣지 못했다. */
    data object NoRoomOrMatch : StashResult
}

/** 설계는 DESIGN 11장. 상자에서는 아무것도 꺼내지 않고, 넣고 남은 양만 내 칸에 남긴다. */
class ChestStash(private val radius: () -> Int) {
    /** [inventory]는 플레이어 인벤토리(0~8 핫바, 9~35 주 칸)이고 [origin]은 그 사람의 위치다. */
    fun stash(inventory: Inventory, origin: Location): StashResult {
        val carried = MAIN_SLOTS.filter { slot -> inventory.getItem(slot)?.let(::canStash) == true }
        if (carried.isEmpty()) return StashResult.NothingToCarry
        val containers = nearbyContainers(origin)
        if (containers.isEmpty()) return StashResult.NoChestNearby

        val tally = StashTally<Material>()
        var chestsUsed = 0
        for (container in containers) {
            val held = container.contents.filterNotNull()
            var usedThisOne = false
            for (slot in carried) {
                val stack = inventory.getItem(slot) ?: continue
                if (held.none { it.isSimilar(stack) }) continue
                // 한 스택을 넣으므로 돌려받는 남는 양은 많아야 한 항목이다. 그만큼만 내 칸에 남기면 총합이 보존된다.
                val leftover = container.addItem(stack.clone()).values.firstOrNull()
                val moved = stack.amount - (leftover?.amount ?: 0)
                if (moved <= 0) continue
                inventory.setItem(slot, leftover)
                tally.add(stack.type, moved)
                usedThisOne = true
            }
            if (usedThisOne) chestsUsed++
        }
        return if (chestsUsed == 0) StashResult.NoRoomOrMatch else StashResult.Stashed(chestsUsed, tally)
    }

    /** 가까운 순. 큰 상자는 두 블록이 같은 인벤토리를 돌려주므로 하나로 센다. */
    private fun nearbyContainers(origin: Location): List<Inventory> {
        val world = origin.world
        val reach = radius()
        val reachSquared = reach.toDouble() * reach
        val nearest = HashMap<InventoryKey, Pair<Inventory, Double>>()
        for (chunkX in chunkRange(origin.blockX, reach)) {
            for (chunkZ in chunkRange(origin.blockZ, reach)) {
                // 이 명령 때문에 청크를 불러오지 않는다.
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                for (state in world.getChunkAt(chunkX, chunkZ).getTileEntities(false)) {
                    if (state !is Container || !isStashable(state)) continue
                    val distanceSquared = state.location.add(CENTER, CENTER, CENTER).distanceSquared(origin)
                    if (distanceSquared > reachSquared) continue
                    val inventory = state.inventory
                    val key = inventory.location?.let { InventoryKey(it.x, it.y, it.z) } ?: continue
                    val known = nearest[key]
                    if (known == null || distanceSquared < known.second) nearest[key] = inventory to distanceSquared
                }
            }
        }
        return nearest.values.sortedBy { it.second }.map { it.first }
    }

    private fun isStashable(state: Container): Boolean {
        if (state.type != Material.CHEST && state.type != Material.BARREL) return false
        if (state.isLocked) return false
        // 다른 기능이 특수하게 쓰는 상자(예: 판매 상자)는 데이터가 붙어 있다. 코드로 알지 않고 데이터로 비켜 간다.
        if ((state as TileState).persistentDataContainer.keys.isNotEmpty()) return false
        return state.inventory.viewers.isEmpty()
    }

    private fun canStash(stack: ItemStack): Boolean = !stack.type.isAir && stack.maxStackSize > 1

    private data class InventoryKey(val x: Double, val y: Double, val z: Double)

    private companion object {
        /** 핫바(0~8)·갑옷·보조손은 건드리지 않는다. */
        val MAIN_SLOTS = 9..35
        const val CENTER = 0.5
    }
}
