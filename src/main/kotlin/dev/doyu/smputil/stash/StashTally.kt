package dev.doyu.smputil.stash

/** 무엇을 몇 개 옮겼는지 센다. Bukkit과 분리해 결과 메시지에 쓸 요약을 테스트한다. */
class StashTally<K> {
    private val counts = linkedMapOf<K, Int>()

    val total: Int get() = counts.values.sum()

    fun add(kind: K, amount: Int) {
        require(amount > 0) { "amount must be positive" }
        counts.merge(kind, amount, Int::plus)
    }

    /** 많이 옮긴 순으로 [limit]종까지 보여 주고, 나머지는 종류 수만 센다. */
    fun summary(limit: Int): Summary<K> {
        val sorted = counts.entries.sortedByDescending { it.value }
        return Summary(sorted.take(limit).map { it.key to it.value }, hiddenKinds = (sorted.size - limit).coerceAtLeast(0), total = total)
    }

    data class Summary<K>(val shown: List<Pair<K, Int>>, val hiddenKinds: Int, val total: Int)
}

/** 반경 [radius] 안에 걸치는 청크 좌표 범위. 청크는 16블록이라 4비트 시프트다(음수에서도 바닥 나눗셈이 되도록 산술 시프트). */
fun chunkRange(center: Int, radius: Int): IntRange = ((center - radius) shr 4)..((center + radius) shr 4)
