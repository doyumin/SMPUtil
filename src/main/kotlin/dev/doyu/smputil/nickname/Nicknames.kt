package dev.doyu.smputil.nickname

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * AdminUtil이 이 키를 직접 읽는다(DESIGN 3장 "다른 플러그인과의 약속"). 플러그인 이름이 바뀌어도 그대로여야 하므로
 * NamespacedKey(plugin, ...)이 아니라 문자열로 만든다.
 */
val NICKNAME_KEY = NamespacedKey("smputil", "nickname")

/**
 * 닉네임의 진실은 플레이어 PDC다. 여기 있는 지도는 접속 중인 플레이어의 사본으로,
 * 네티 스레드(패킷 바꿔치기)가 메인 스레드를 거치지 않고 읽을 수 있게 두는 것이다.
 */
class Nicknames(private val plugin: Plugin, private val server: Server) {
    private val online = ConcurrentHashMap<UUID, String>()

    fun of(player: UUID): String? = online[player]

    fun shownName(player: Player): String = online[player.uniqueId] ?: player.name

    /** 접속할 때, 플레이어 정보 패킷이 나가기 전에 불러야 한다. */
    fun load(player: Player) {
        val nickname = player.persistentDataContainer.get(NICKNAME_KEY, PersistentDataType.STRING)
        if (nickname == null) online.remove(player.uniqueId) else online[player.uniqueId] = nickname
        applyDisplay(player, nickname)
    }

    fun forget(player: Player) {
        online.remove(player.uniqueId)
    }

    /** null이면 진짜 이름으로 되돌린다. 규칙 검사는 호출자가 [NicknameRules]로 먼저 한다. */
    fun set(player: Player, nickname: String?) {
        val container = player.persistentDataContainer
        if (nickname == null) {
            container.remove(NICKNAME_KEY)
            online.remove(player.uniqueId)
        } else {
            container.set(NICKNAME_KEY, PersistentDataType.STRING, nickname)
            online[player.uniqueId] = nickname
        }
        applyDisplay(player, nickname)
        refreshNameTag(player)
    }

    /** 이 플레이어를 뺀 모든 플레이어의 진짜 이름과 닉네임. 접속하지 않은 플레이어는 저장 파일의 PDC를 읽는다. */
    fun namesTakenBesides(player: UUID): List<String> {
        val names = mutableListOf<String>()
        for (other in server.offlinePlayers) {
            if (other.uniqueId == player) continue
            other.name?.let(names::add)
            val nickname = online[other.uniqueId] ?: other.persistentDataContainer.get(NICKNAME_KEY, PersistentDataType.STRING)
            if (nickname != null) names += nickname
        }
        // 처음 접속한 플레이어는 아직 offlinePlayers 목록에 없을 수 있다.
        for (other in server.onlinePlayers) {
            if (other.uniqueId == player) continue
            names += other.name
            online[other.uniqueId]?.let(names::add)
        }
        return names
    }

    /** 채팅은 displayName, 탭 목록은 playerListName을 쓴다. 둘 다 공식 API라 패킷을 건드리지 않는다. */
    private fun applyDisplay(player: Player, nickname: String?) {
        val shown = nickname?.let { Component.text(it) }
        player.displayName(shown)
        player.playerListName(shown)
    }

    /**
     * 머리 위 이름표는 클라이언트가 처음 받은 프로필 이름을 쓰므로, 보는 사람들에게 이 플레이어를 숨겼다가 다시 보여 줘서
     * 플레이어 정보 패킷을 다시 보내게 한다. 숨김은 플러그인별로 따로 세므로, 다른 플러그인(AdminUtil 투명화)이 숨긴 상태는 그대로 남는다.
     * 원래 볼 수 없던 사람에게는 하지 않는다.
     */
    private fun refreshNameTag(player: Player) {
        for (viewer in server.onlinePlayers) {
            if (viewer == player || !viewer.canSee(player)) continue
            viewer.hidePlayer(plugin, player)
            viewer.showPlayer(plugin, player)
        }
    }
}
