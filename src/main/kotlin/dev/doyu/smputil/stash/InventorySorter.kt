package dev.doyu.smputil.stash

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

sealed interface SortOutcome {
    data object Sorted : SortOutcome
    data object AlreadySorted : SortOutcome
    /** 합친 결과가 칸에 맞지 않거나 총합이 달라질 것 같아 아무것도 바꾸지 않았다. */
    data object Refused : SortOutcome
}

/** 설계는 DESIGN 11장 "정렬". 칸 범위 안의 아이템만 합치고 아이템 ID 순으로 늘어놓는다. */
class InventorySorter {
    private class Group(val sample: ItemStack, var total: Int)

    fun sort(inventory: Inventory, slots: IntRange): SortOutcome {
        val current = slots.map { slot -> inventory.getItem(slot)?.takeIf { !it.type.isAir } }
        val groups = mutableListOf<Group>()
        for (stack in current.filterNotNull()) {
            val group = groups.firstOrNull { it.sample.isSimilar(stack) }
            if (group == null) groups += Group(stack.clone().also { it.amount = 1 }, stack.amount) else group.total += stack.amount
        }

        val layout = stackLayout(
            groups.map { it.sample to it.total },
            order = compareBy { it.type.key.asString() },
            maxStackOf = { it.maxStackSize },
        )
        val before = current.filterNotNull().sumOf { it.amount }
        // 총합이 같고 칸에 들어갈 때만 쓴다. 어긋나면 아이템을 잃기 전에 멈춘다.
        if (layout.sumOf { it.second } != before || layout.size > current.size) return SortOutcome.Refused

        val desired = List(current.size) { index -> layout.getOrNull(index)?.let { (sample, amount) -> sample.clone().also { it.amount = amount } } }
        if (desired.indices.all { sameStack(desired[it], current[it]) }) return SortOutcome.AlreadySorted

        for ((index, slot) in slots.withIndex()) inventory.setItem(slot, desired[index])
        return SortOutcome.Sorted
    }

    private fun sameStack(a: ItemStack?, b: ItemStack?): Boolean =
        if (a == null || b == null) a == null && b == null else a.amount == b.amount && a.isSimilar(b)
}
