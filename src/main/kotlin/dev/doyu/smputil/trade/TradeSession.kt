package dev.doyu.smputil.trade

import java.util.UUID

enum class TradeSide {
    FIRST, SECOND;

    val other: TradeSide get() = if (this == FIRST) SECOND else FIRST
}

enum class TradeEndReason {
    /** 두 사람이 모두 확인했다. */
    EXCHANGED,
    /** 취소 버튼이나 창 닫기. */
    CANCELLED,
    LEFT_GAME,
    DIED,
    /** 플러그인이 꺼진다. */
    SHUTDOWN,
}

/** 한 사람에게 갈 아이템. [receive]는 인벤토리로(넘치면 발밑), [dropHere]는 그 사람이 있던 자리에 떨군다. */
data class Handover<T>(val receive: List<T>, val dropHere: List<T>)

data class TradeSettlement<T>(val reason: TradeEndReason, val first: Handover<T>, val second: Handover<T>) {
    fun of(side: TradeSide): Handover<T> = if (side == TradeSide.FIRST) first else second
}

sealed interface ConfirmResult<out T> {
    data object Waiting : ConfirmResult<Nothing>
    data class Completed<T>(val settlement: TradeSettlement<T>) : ConfirmResult<T>
    data object Closed : ConfirmResult<Nothing>
}

/**
 * 거래 한 건의 상태. 아이템은 거래 화면이 아니라 여기에만 있다. 화면은 이 상태를 보여 주는 복사본이다.
 * 진짜가 한 곳에만 있어야, 어떤 종료 경로에서도 "누구에게 무엇을 돌려줄지"가 이 클래스 하나로 정해진다.
 *
 * - 어느 쪽 칸이든 내용이 바뀌면 두 사람의 확인이 모두 풀린다. 넣었다 빼기 사기를 막는 핵심 규칙이다(DESIGN 6장).
 * - 끝난 뒤의 모든 변경은 무시된다. 닫힘 이벤트가 두 번 와도 아이템이 두 번 나가지 않게 하기 위해서다.
 */
class TradeSession<T>(val first: UUID, val second: UUID, private val capacity: Int) {
    private val offers = mapOf(TradeSide.FIRST to mutableListOf<T>(), TradeSide.SECOND to mutableListOf())
    private val confirmed = mutableSetOf<TradeSide>()

    var isOpen: Boolean = true
        private set

    fun sideOf(player: UUID): TradeSide? = when (player) {
        first -> TradeSide.FIRST
        second -> TradeSide.SECOND
        else -> null
    }

    fun playerOn(side: TradeSide): UUID = if (side == TradeSide.FIRST) first else second

    fun offerOf(side: TradeSide): List<T> = offers.getValue(side).toList()

    fun isConfirmed(side: TradeSide): Boolean = side in confirmed

    /** 칸이 가득 찼거나 이미 끝난 거래면 false. false면 아이템을 받지 않은 것이니 호출자가 그대로 갖고 있어야 한다. */
    fun offer(side: TradeSide, item: T): Boolean {
        if (!isOpen) return false
        val offer = offers.getValue(side)
        if (offer.size >= capacity) return false
        offer += item
        confirmed.clear()
        return true
    }

    /** 자기 칸에서 [index]번째 아이템을 되찾는다. 돌려받은 아이템은 호출자가 그 사람에게 줘야 한다. */
    fun withdraw(side: TradeSide, index: Int): T? {
        if (!isOpen) return null
        val offer = offers.getValue(side)
        if (index !in offer.indices) return null
        confirmed.clear()
        return offer.removeAt(index)
    }

    /** 두 사람이 모두 확인한 순간 교환이 정해진다. */
    fun confirm(side: TradeSide): ConfirmResult<T> {
        if (!isOpen) return ConfirmResult.Closed
        confirmed += side
        if (confirmed.size < TradeSide.entries.size) return ConfirmResult.Waiting
        isOpen = false
        return ConfirmResult.Completed(
            TradeSettlement(
                TradeEndReason.EXCHANGED,
                first = Handover(receive = take(TradeSide.SECOND), dropHere = emptyList()),
                second = Handover(receive = take(TradeSide.FIRST), dropHere = emptyList()),
            ),
        )
    }

    fun unconfirm(side: TradeSide) {
        if (isOpen) confirmed -= side
    }

    /**
     * 교환 없이 끝낸다. [cause]는 거래를 끝나게 한 사람이다.
     * 죽은 사람의 칸은 그 자리에 떨군다. 사망 드롭이 이미 정해진 뒤라 인벤토리에 넣으면 사라질 수 있다(Paper 26.3 순서, Econet에서 확인).
     * 나머지는 모두 각자 자기 칸의 아이템을 돌려받는다.
     * 이미 끝난 거래면 null이다.
     */
    fun end(reason: TradeEndReason, cause: TradeSide): TradeSettlement<T>? {
        if (!isOpen) return null
        require(reason != TradeEndReason.EXCHANGED) { "Exchange happens only through confirm" }
        isOpen = false
        val firstOffer = take(TradeSide.FIRST)
        val secondOffer = take(TradeSide.SECOND)
        fun handover(side: TradeSide, own: List<T>): Handover<T> =
            if (reason == TradeEndReason.DIED && side == cause) Handover(emptyList(), own) else Handover(own, emptyList())
        return TradeSettlement(reason, handover(TradeSide.FIRST, firstOffer), handover(TradeSide.SECOND, secondOffer))
    }

    private fun take(side: TradeSide): List<T> {
        val offer = offers.getValue(side)
        val items = offer.toList()
        offer.clear()
        return items
    }
}
