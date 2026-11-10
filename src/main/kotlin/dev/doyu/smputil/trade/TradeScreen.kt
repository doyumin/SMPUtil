package dev.doyu.smputil.trade

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 한 사람이 보는 거래 화면. 왼쪽 4×5칸이 내 칸, 오른쪽 4×5칸이 상대 칸, 맨 아랫줄이 버튼이다(DESIGN 6장).
 * 두 사람이 각자 자기 화면을 갖는다. 둘 다 "왼쪽 = 내 칸"으로 보이게 하기 위해서다.
 *
 * 화면에 있는 아이템은 모두 [TradeSession]의 복사본이다. 화면의 모든 클릭·드래그는 [Trades]가 취소하고 직접 처리하므로 복사본이 밖으로 나갈 수 없다.
 * 그래도 복사본과 버튼에는 UI 태그를 단다. 플레이어의 같은 아이템과 합쳐지거나 같은 것으로 취급되지 않게 하기 위해서다.
 */
class TradeScreen(
    val owner: Player,
    val session: TradeSession<ItemStack>,
    private val uiTag: NamespacedKey,
    server: Server,
    partnerName: String,
) : InventoryHolder {
    val side: TradeSide = requireNotNull(session.sideOf(owner.uniqueId)) { "${owner.name} is not part of this trade" }
    private val inventory: Inventory = server.createInventory(this, SIZE, Component.text("거래: $partnerName"))

    override fun getInventory(): Inventory = inventory

    fun render() {
        inventory.clear()
        session.offerOf(side).forEachIndexed { index, item -> inventory.setItem(MY_SLOTS[index], tagged(item.clone())) }
        session.offerOf(side.other).forEachIndexed { index, item -> inventory.setItem(THEIR_SLOTS[index], tagged(item.clone())) }
        for (slot in FILLER_SLOTS) inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")))

        val mine = if (session.isConfirmed(side)) {
            button(Material.LIME_CONCRETE, Component.text("확인함 (누르면 확인 해제)", NamedTextColor.GREEN))
        } else {
            button(Material.WHITE_CONCRETE, Component.text("확인", NamedTextColor.WHITE))
        }
        inventory.setItem(CONFIRM_SLOT, mine)
        inventory.setItem(CANCEL_SLOT, button(Material.BARRIER, Component.text("거래 취소", NamedTextColor.RED)))
        val theirs = if (session.isConfirmed(side.other)) {
            button(Material.LIME_CONCRETE, Component.text("상대가 확인함", NamedTextColor.GREEN))
        } else {
            button(Material.RED_CONCRETE, Component.text("상대가 아직 확인 전", NamedTextColor.RED))
        }
        inventory.setItem(PARTNER_STATUS_SLOT, theirs)
    }

    private fun button(material: Material, name: Component): ItemStack {
        val item = ItemStack.of(material)
        item.editMeta { meta -> meta.displayName(name.decoration(TextDecoration.ITALIC, false)) }
        return tagged(item)
    }

    private fun tagged(item: ItemStack): ItemStack {
        item.editPersistentDataContainer { container -> container.set(uiTag, PersistentDataType.BOOLEAN, true) }
        return item
    }

    companion object {
        const val SIZE = 54
        private const val OFFER_ROWS = 5
        private const val OFFER_COLUMNS = 4
        const val OFFER_CAPACITY = OFFER_ROWS * OFFER_COLUMNS

        val MY_SLOTS: List<Int> = (0 until OFFER_ROWS).flatMap { row -> (0 until OFFER_COLUMNS).map { column -> row * 9 + column } }
        val THEIR_SLOTS: List<Int> = MY_SLOTS.map { it + OFFER_COLUMNS + 1 }
        const val CONFIRM_SLOT = 47
        const val CANCEL_SLOT = 49
        const val PARTNER_STATUS_SLOT = 51
        private val FILLER_SLOTS: List<Int> =
            (0 until SIZE).filterNot { it in MY_SLOTS || it in THEIR_SLOTS || it == CONFIRM_SLOT || it == CANCEL_SLOT || it == PARTNER_STATUS_SLOT }
    }
}
