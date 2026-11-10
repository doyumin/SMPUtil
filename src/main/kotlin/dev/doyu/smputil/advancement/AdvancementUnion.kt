package dev.doyu.smputil.advancement

/**
 * 한 도전과제에 대해, 각 플레이어가 받지 못한 세부 조건을 계산한다. 결과는 누군가 달성한 조건의 합집합에서 각자 가진 것을 뺀 것이다.
 * 빠진 것이 없는 플레이어는 결과에 넣지 않는다.
 */
fun <P> missingCriteria(awarded: Map<P, Set<String>>): Map<P, Set<String>> {
    val union = awarded.values.flatten().toSet()
    return awarded.mapValues { (_, own) -> union - own }.filterValues { it.isNotEmpty() }
}

/** 레시피 해금은 공유하지 않는다(DESIGN 7장). 바닐라 레시피 도전과제는 모두 `recipes/` 아래에 있다. */
fun isShared(advancementKey: String): Boolean = !advancementKey.startsWith("recipes/")
