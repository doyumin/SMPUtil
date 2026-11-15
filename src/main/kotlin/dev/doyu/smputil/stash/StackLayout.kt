package dev.doyu.smputil.stash

/**
 * 같은 종류끼리 합쳐 [order] 순으로 늘어놓고, 종류마다 최대 스택 단위로 나눈다.
 * Bukkit과 분리한 순수 계산이다: 같은 종류의 개수 합이 전후로 같다는 것(DESIGN 2장)을 무작위로 테스트하기 위해서다.
 * 같은 순서끼리는 [groups]에서의 원래 순서를 유지한다.
 */
fun <K> stackLayout(groups: List<Pair<K, Int>>, order: Comparator<K>, maxStackOf: (K) -> Int): List<Pair<K, Int>> =
    groups
        .sortedWith { a, b -> order.compare(a.first, b.first) }
        .flatMap { (kind, total) -> splitIntoStacks(total, maxStackOf(kind)).map { kind to it } }

/** [total]을 [maxStack] 이하의 덩어리로 나눈다. 가득 찬 것이 앞에 오고 남는 것이 마지막 하나다. */
fun splitIntoStacks(total: Int, maxStack: Int): List<Int> {
    require(total > 0) { "total must be positive" }
    require(maxStack > 0) { "maxStack must be positive" }
    val full = List(total / maxStack) { maxStack }
    val rest = total % maxStack
    return if (rest == 0) full else full + rest
}
