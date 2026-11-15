package dev.doyu.smputil.stash

import org.bukkit.Location
import org.bukkit.Material
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
class ChestStash(private val nearby: NearbyChests, private val radius: () -> Int) {
    /** [inventory]는 플레이어 인벤토리(0~8 핫바, 9~35 주 칸)이고 [origin]은 그 사람의 위치다. */
    fun stash(inventory: Inventory, origin: Location): StashResult {
        val carried = PLAYER_MAIN_SLOTS.filter { slot -> inventory.getItem(slot)?.let(::canStash) == true }
        if (carried.isEmpty()) return StashResult.NothingToCarry
        val containers = nearby.find(origin, radius(), skipOpened = true).map { it.inventory }
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

    private fun canStash(stack: ItemStack): Boolean = !stack.type.isAir && stack.maxStackSize > 1
}

/** 핫바(0~8)·갑옷·보조손은 건드리지 않는다. */
val PLAYER_MAIN_SLOTS = 9..35
