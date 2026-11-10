package dev.doyu.smputil.nickname

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 채팅은 Paper 기본 렌더러가 displayName을 쓰므로 따로 할 일이 없다.
 * 입장·퇴장·사망 메시지는 바닐라가 진짜 이름으로 만들어 두므로 이벤트에서 닉네임으로 바꾼다.
 */
class NicknameListener(private val nicknames: Nicknames, private val channels: NicknameChannels) : Listener {
    /**
     * PlayerJoinEvent는 플레이어 정보 패킷을 보내기 전에 불린다(Paper 26.3 PlayerList.placeNewPlayer에서 확인).
     * 그래서 여기서 닉네임을 불러오고 파이프라인을 끼우면 첫 패킷부터 닉네임으로 나간다.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun loadOnJoin(event: PlayerJoinEvent) {
        nicknames.load(event.player)
        channels.attach(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun renameJoinMessage(event: PlayerJoinEvent) {
        val player = event.player
        event.joinMessage(event.joinMessage()?.withShownName(player.name, nicknames.shownName(player)))
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun renameQuitMessage(event: PlayerQuitEvent) {
        val player = event.player
        event.quitMessage(event.quitMessage()?.withShownName(player.name, nicknames.shownName(player)))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun forgetOnQuit(event: PlayerQuitEvent) {
        nicknames.forget(event.player)
    }

    /** 죽은 사람과 죽인 사람(플레이어) 모두의 이름이 들어 있을 수 있다. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun renameDeathMessage(event: PlayerDeathEvent) {
        var message = event.deathMessage() ?: return
        val dead = event.player
        message = message.withShownName(dead.name, nicknames.shownName(dead))
        val killer = dead.killer
        if (killer != null) message = message.withShownName(killer.name, nicknames.shownName(killer))
        event.deathMessage(message)
    }
}
