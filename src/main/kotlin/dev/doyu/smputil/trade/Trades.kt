package dev.doyu.smputil.trade

import dev.doyu.smputil.nickname.Nicknames
import dev.doyu.smputil.request.PendingRequest
import dev.doyu.smputil.request.RequestBoard
import dev.doyu.smputil.request.SendResult
import dev.doyu.smputil.request.TakeResult
import dev.doyu.smputil.request.answerButtons
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * 거래 요청과 열린 거래 화면. 모든 일은 메인 스레드에서 한다.
 *
 * 아이템의 행방(DESIGN 6장 표)은 [TradeSession.end]가 정하고, 여기서는 닫힘 경로를 종료 이유로 옮기기만 한다.
 * - 창 닫기·취소 → 각자 돌려받는다.
 * - 접속 종료 → 닫힘이 퇴장·저장보다 먼저라 인벤토리에 넣으면 저장된다(Paper 26.3, Econet에서 확인).
 * - 사망 → 사망 드롭이 정해진 뒤에 닫히므로 죽은 사람의 칸은 그 자리에 떨군다.
 * - 서버 종료 → 플러그인 비활성화가 플레이어 저장보다 먼저라 닫힘 이벤트를 기다리지 않고 [endAll]로 직접 돌려준다.
 */
class Trades(
    private val plugin: Plugin,
    private val server: Server,
    private val board: RequestBoard,
    private val nicknames: Nicknames,
    /** null이면 거리 제한이 없다. */
    private val maxDistance: Double?,
) : Listener {
    private val uiTag = NamespacedKey(plugin, "trade_ui")
    private val screens = mutableMapOf<UUID, TradeScreen>()

    fun request(sender: Player, target: Player) {
        if (sender.uniqueId in screens) return sender.sendMessage(Component.text("이미 거래 중입니다.", NamedTextColor.RED))
        if (target.uniqueId in screens) return sender.sendMessage(Component.text("${nicknames.shownName(target)}님은 다른 사람과 거래 중입니다.", NamedTextColor.RED))
        if (!closeEnough(sender, target)) return sender.sendMessage(tooFar())
        when (val result = board.send(sender.uniqueId, target.uniqueId)) {
            is SendResult.Sent -> {
                result.replaced?.let { replaced -> server.getPlayer(replaced.to)?.sendMessage(Component.text("${nicknames.shownName(sender)}님이 거래 요청을 취소했습니다.", NamedTextColor.GRAY)) }
                sender.sendMessage(Component.text("${nicknames.shownName(target)}님에게 거래를 요청했습니다.", NamedTextColor.GREEN))
                target.sendMessage(Component.text("${nicknames.shownName(sender)}님이 거래를 요청했습니다. ", NamedTextColor.YELLOW).append(answerButtons("/trade accept", "/trade deny")))
            }
            is SendResult.ToSelf -> sender.sendMessage(Component.text("자기 자신과는 거래할 수 없습니다.", NamedTextColor.RED))
            is SendResult.TargetBusy -> sender.sendMessage(Component.text("${nicknames.shownName(target)}님은 다른 요청에 답하는 중입니다. 잠시 뒤에 다시 하세요.", NamedTextColor.RED))
        }
    }

    fun accept(target: Player) {
        when (val result = board.take(target.uniqueId)) {
            is TakeResult.Found -> {
                val requester = server.getPlayer(result.request.from)
                when {
                    requester == null || !target.canSee(requester) -> target.sendMessage(Component.text("요청한 사람이 접속 중이 아닙니다.", NamedTextColor.RED))
                    requester.uniqueId in screens || target.uniqueId in screens -> target.sendMessage(Component.text("둘 중 한 사람이 이미 거래 중입니다.", NamedTextColor.RED))
                    !closeEnough(requester, target) -> target.sendMessage(tooFar())
                    else -> open(requester, target)
                }
            }
            is TakeResult.Expired -> {
                target.sendMessage(Component.text("요청이 이미 만료되었습니다.", NamedTextColor.RED))
                notifyExpired(result.request)
            }
            is TakeResult.Missing -> target.sendMessage(Component.text("받은 거래 요청이 없습니다.", NamedTextColor.RED))
        }
    }

    fun deny(target: Player) {
        when (val result = board.take(target.uniqueId)) {
            is TakeResult.Found -> {
                target.sendMessage(Component.text("거래 요청을 거절했습니다.", NamedTextColor.YELLOW))
                server.getPlayer(result.request.from)?.sendMessage(Component.text("${nicknames.shownName(target)}님이 거래 요청을 거절했습니다.", NamedTextColor.RED))
            }
            is TakeResult.Expired -> {
                target.sendMessage(Component.text("요청이 이미 만료되었습니다.", NamedTextColor.RED))
                notifyExpired(result.request)
            }
            is TakeResult.Missing -> target.sendMessage(Component.text("받은 거래 요청이 없습니다.", NamedTextColor.RED))
        }
    }

    /** 1초마다 부른다. */
    fun sweepExpired() {
        board.removeExpired().forEach(::notifyExpired)
    }

    /** 플러그인이 꺼질 때. 플레이어 저장보다 먼저 일어나므로 인벤토리에 돌려주면 저장된다. */
    fun endAll() {
        for (screen in screens.values.toList()) {
            val settlement = screen.session.end(TradeEndReason.SHUTDOWN, screen.side) ?: continue
            finish(screen.session, settlement, screen.side)
        }
    }

    private fun open(first: Player, second: Player) {
        val session = TradeSession<ItemStack>(first.uniqueId, second.uniqueId, TradeScreen.OFFER_CAPACITY)
        val firstScreen = TradeScreen(first, session, uiTag, server, nicknames.shownName(second))
        val secondScreen = TradeScreen(second, session, uiTag, server, nicknames.shownName(first))
        firstScreen.render()
        secondScreen.render()
        first.openInventory(firstScreen.inventory)
        second.openInventory(secondScreen.inventory)
        // openInventory가 이전 화면을 닫으며 닫힘 이벤트를 부를 수 있으므로, 새 화면은 연 뒤에 등록한다.
        screens[first.uniqueId] = firstScreen
        screens[second.uniqueId] = secondScreen
    }

    /**
     * 화면의 모든 클릭을 취소하고 직접 처리한다. 클릭 종류(shift·숫자키·더블클릭 모으기 등)를 하나씩 따지면 빠뜨리는 경로가 생기기 때문이다.
     * 왼쪽·오른쪽 클릭만 받는다. 내 인벤토리의 아이템을 누르면 한 묶음이 내 칸으로, 내 칸의 아이템을 누르면 내 인벤토리로 돌아온다.
     */
    @EventHandler(priority = EventPriority.LOW)
    fun onClick(event: InventoryClickEvent) {
        val screen = event.view.topInventory.getHolder(false) as? TradeScreen ?: return
        event.isCancelled = true
        if (screens[screen.owner.uniqueId] !== screen) return
        if (event.click != ClickType.LEFT && event.click != ClickType.RIGHT && event.click != ClickType.SHIFT_LEFT && event.click != ClickType.SHIFT_RIGHT) return

        val player = screen.owner
        val session = screen.session
        if (event.clickedInventory == event.view.bottomInventory) {
            val item = event.currentItem
            if (item == null || item.isEmpty) return
            if (!session.offer(screen.side, item.clone())) {
                player.sendMessage(Component.text("거래 칸이 가득 찼습니다.", NamedTextColor.RED))
                return
            }
            player.inventory.setItem(event.slot, null)
            renderBoth(session)
            return
        }

        when (event.rawSlot) {
            TradeScreen.CONFIRM_SLOT -> toggleConfirm(screen)
            TradeScreen.CANCEL_SLOT -> session.end(TradeEndReason.CANCELLED, screen.side)?.let { finish(session, it, screen.side) }
            in TradeScreen.MY_SLOTS -> {
                val item = session.withdraw(screen.side, TradeScreen.MY_SLOTS.indexOf(event.rawSlot)) ?: return
                player.receive(listOf(item))
                renderBoth(session)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is TradeScreen) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val screen = event.inventory.getHolder(false) as? TradeScreen ?: return
        if (screens[screen.owner.uniqueId] !== screen) return
        val reason = when (event.reason) {
            InventoryCloseEvent.Reason.DEATH -> TradeEndReason.DIED
            InventoryCloseEvent.Reason.DISCONNECT -> TradeEndReason.LEFT_GAME
            else -> TradeEndReason.CANCELLED
        }
        screen.session.end(reason, screen.side)?.let { finish(screen.session, it, screen.side) }
    }

    /** 닫힘 이벤트 없이 나가는 경우가 있어도 아이템을 잃지 않게 한 번 더 막는다. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuit(event: PlayerQuitEvent) {
        val screen = screens[event.player.uniqueId]
        if (screen != null) screen.session.end(TradeEndReason.LEFT_GAME, screen.side)?.let { finish(screen.session, it, screen.side) }
        for (request in board.removeInvolving(event.player.uniqueId)) {
            val other = if (request.from == event.player.uniqueId) request.to else request.from
            server.getPlayer(other)?.sendMessage(Component.text("${nicknames.shownName(event.player)}님이 나가서 거래 요청이 취소되었습니다.", NamedTextColor.GRAY))
        }
    }

    private fun toggleConfirm(screen: TradeScreen) {
        val session = screen.session
        if (session.isConfirmed(screen.side)) {
            session.unconfirm(screen.side)
            renderBoth(session)
            return
        }
        when (val result = session.confirm(screen.side)) {
            is ConfirmResult.Waiting -> renderBoth(session)
            is ConfirmResult.Completed -> finish(session, result.settlement, cause = null)
            is ConfirmResult.Closed -> Unit
        }
    }

    private fun renderBoth(session: TradeSession<ItemStack>) {
        screens[session.first]?.render()
        screens[session.second]?.render()
    }

    /**
     * 화면을 먼저 목록에서 빼고 아이템을 준다. 그 뒤에 오는 닫힘 이벤트는 목록에 없으니 무시된다.
     * [cause]는 거래를 끝나게 한 사람이다. 교환이면 null.
     */
    private fun finish(session: TradeSession<ItemStack>, settlement: TradeSettlement<ItemStack>, cause: TradeSide?) {
        val ended = TradeSide.entries.mapNotNull { side ->
            val uuid = session.playerOn(side)
            val screen = screens[uuid]
            if (screen?.session === session) screens.remove(uuid)
            screen?.takeIf { it.session === session }
        }
        for (screen in ended) {
            val player = screen.owner
            val handover = settlement.of(screen.side)
            player.receive(handover.receive)
            for (item in handover.dropHere) player.world.dropItemNaturally(player.location, item)
            player.sendMessage(endMessage(settlement, screen.side, byMe = screen.side == cause))
            if (player.openInventory.topInventory.getHolder(false) === screen) {
                // 닫힘 이벤트 안에서 다른 플레이어의 창을 바로 닫으면 이벤트가 겹치므로 다음 틱에 닫는다.
                plugin.server.scheduler.runTask(plugin) { _ ->
                    if (player.openInventory.topInventory.getHolder(false) === screen) player.closeInventory()
                }
            }
        }
    }

    private fun endMessage(settlement: TradeSettlement<ItemStack>, side: TradeSide, byMe: Boolean): Component = when (settlement.reason) {
        TradeEndReason.EXCHANGED -> Component.text("거래를 마쳤습니다. 받은 아이템 ${settlement.of(side).receive.size}묶음.", NamedTextColor.GREEN)
        TradeEndReason.CANCELLED ->
            Component.text(if (byMe) "거래를 취소했습니다. 올린 아이템은 돌려드렸습니다." else "상대가 거래를 취소했습니다. 올린 아이템은 돌려드렸습니다.", NamedTextColor.YELLOW)
        TradeEndReason.LEFT_GAME -> Component.text("상대가 나가서 거래가 취소되었습니다. 올린 아이템은 돌려드렸습니다.", NamedTextColor.YELLOW)
        TradeEndReason.DIED ->
            Component.text(if (byMe) "거래 중 사망해 거래가 취소되었습니다. 올린 아이템은 죽은 자리에 떨어졌습니다." else "상대가 사망해 거래가 취소되었습니다. 올린 아이템은 돌려드렸습니다.", NamedTextColor.YELLOW)
        TradeEndReason.SHUTDOWN -> Component.text("서버가 꺼져 거래가 취소되었습니다. 올린 아이템은 돌려드렸습니다.", NamedTextColor.YELLOW)
    }

    private fun closeEnough(a: Player, b: Player): Boolean {
        val limit = maxDistance ?: return true
        return a.world == b.world && a.location.distance(b.location) <= limit
    }

    private fun tooFar(): Component = Component.text("거래하려면 ${maxDistance?.toInt()}블록 안에 있어야 합니다.", NamedTextColor.RED)

    private fun notifyExpired(request: PendingRequest) {
        val targetName = server.getPlayer(request.to)?.let(nicknames::shownName) ?: "상대"
        server.getPlayer(request.from)?.sendMessage(Component.text("${targetName}님에게 보낸 거래 요청이 만료되었습니다.", NamedTextColor.GRAY))
    }
}

/** 넘치는 것은 발밑에 떨군다. 교환 자체는 막지 않는다(DESIGN 6장). */
private fun Player.receive(items: List<ItemStack>) {
    if (items.isEmpty()) return
    for (leftover in inventory.addItem(*items.toTypedArray()).values) world.dropItemNaturally(location, leftover)
}
