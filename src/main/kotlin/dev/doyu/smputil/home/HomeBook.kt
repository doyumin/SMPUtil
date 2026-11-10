package dev.doyu.smputil.home

import java.util.UUID

/** 월드는 이름으로 적는다. 저장 파일을 사람이 읽고 고칠 수 있게 하기 위해서다. */
data class HomePoint(val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

sealed interface SetHomeResult {
    data object Created : SetHomeResult
    data object Moved : SetHomeResult
    data class LimitReached(val limit: Int) : SetHomeResult
    data object InvalidName : SetHomeResult
}

const val DEFAULT_HOME = "home"

private val HOME_NAME = Regex("[가-힣A-Za-z0-9_]{1,16}")

/**
 * 플레이어별 집 목록. 이름은 대소문자를 가리지 않는다(소문자로 저장).
 * 이름 문자를 제한하는 이유는 YAML 키에 점(.)이 들어가면 경로로 해석되기 때문이다.
 */
class HomeBook(homes: Map<UUID, Map<String, HomePoint>> = emptyMap()) {
    private val homes: MutableMap<UUID, MutableMap<String, HomePoint>> =
        homes.mapValuesTo(mutableMapOf()) { (_, owned) -> owned.toMutableMap() }

    fun set(owner: UUID, name: String, point: HomePoint, limit: Int): SetHomeResult {
        if (!HOME_NAME.matches(name)) return SetHomeResult.InvalidName
        val key = name.lowercase()
        val owned = homes.getOrPut(owner) { mutableMapOf() }
        if (key in owned) {
            owned[key] = point
            return SetHomeResult.Moved
        }
        // 설정으로 상한을 줄여도 이미 있는 집은 지우지 않는다. 새로 만드는 것만 막는다.
        if (owned.size >= limit) return SetHomeResult.LimitReached(limit)
        owned[key] = point
        return SetHomeResult.Created
    }

    fun get(owner: UUID, name: String): HomePoint? = homes[owner]?.get(name.lowercase())

    fun delete(owner: UUID, name: String): Boolean {
        val owned = homes[owner] ?: return false
        val removed = owned.remove(name.lowercase()) != null
        if (owned.isEmpty()) homes.remove(owner)
        return removed
    }

    fun namesOf(owner: UUID): List<String> = homes[owner]?.keys?.sorted().orEmpty()

    fun snapshot(): Map<UUID, Map<String, HomePoint>> = homes.mapValues { (_, owned) -> owned.toMap() }
}
