package dev.doyu.smputil.request

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PendingRequest(val from: UUID, val to: UUID, val expiresAt: Instant)

sealed interface SendResult {
    /** [replaced]는 같은 요청자가 전에 보낸, 이번 요청으로 밀려난 요청이다. */
    data class Sent(val request: PendingRequest, val replaced: PendingRequest?) : SendResult
    data object ToSelf : SendResult
    /** 다른 사람이 이미 대상에게 요청해 두었다. 남의 요청을 가로채지 않기 위해 거절한다. */
    data class TargetBusy(val existing: PendingRequest) : SendResult
}

sealed interface TakeResult {
    data class Found(val request: PendingRequest) : TakeResult
    data class Expired(val request: PendingRequest) : TakeResult
    data object Missing : TakeResult
}

/**
 * 누가 누구에게 언제까지 유효한 요청을 보냈는지. 텔레포트 요청과 거래 요청이 같은 규칙(DESIGN 5·6장)을 쓴다.
 * 한 대상에게 대기 중인 요청은 하나, 한 요청자가 보낸 대기 요청도 하나다.
 * 만료는 주입한 [clock]으로만 판정한다. 시간이 걸리는 규칙을 테스트에서 고정하기 위해서다.
 */
class RequestBoard(private val clock: Clock, private val lifetime: Duration) {
    private val byTarget = mutableMapOf<UUID, PendingRequest>()

    fun send(from: UUID, to: UUID): SendResult {
        if (from == to) return SendResult.ToSelf
        dropExpired()
        val existing = byTarget[to]
        if (existing != null && existing.from != from) return SendResult.TargetBusy(existing)
        val replaced = byTarget.values.firstOrNull { it.from == from && it.to != to }
        if (replaced != null) byTarget.remove(replaced.to)
        val request = PendingRequest(from, to, clock.instant().plus(lifetime))
        byTarget[to] = request
        return SendResult.Sent(request, replaced)
    }

    /** 대상이 수락·거절할 때 부른다. 어느 쪽이든 요청은 사라진다. */
    fun take(to: UUID): TakeResult {
        val request = byTarget.remove(to) ?: return TakeResult.Missing
        return if (isExpired(request)) TakeResult.Expired(request) else TakeResult.Found(request)
    }

    fun sentBy(from: UUID): PendingRequest? = byTarget.values.firstOrNull { it.from == from && !isExpired(it) }

    /** 만료된 요청을 지우고 돌려준다. 요청자에게 만료를 알리기 위해 주기적으로 부른다. */
    fun removeExpired(): List<PendingRequest> {
        val expired = byTarget.values.filter(::isExpired)
        for (request in expired) byTarget.remove(request.to)
        return expired
    }

    /** 접속을 끊은 사람이 보냈거나 받은 요청을 지우고 돌려준다. */
    fun removeInvolving(player: UUID): List<PendingRequest> {
        val involved = byTarget.values.filter { it.from == player || it.to == player }
        for (request in involved) byTarget.remove(request.to)
        return involved.filterNot(::isExpired)
    }

    private fun dropExpired() {
        byTarget.values.removeIf(::isExpired)
    }

    private fun isExpired(request: PendingRequest): Boolean = !clock.instant().isBefore(request.expiresAt)
}
