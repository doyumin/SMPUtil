package dev.doyu.smputil.players

import dev.doyu.smputil.nickname.Nicknames
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * 플레이어 인자는 진짜 이름과 한글 닉네임을 모두 받는다(DESIGN 2장).
 * 투명화된 플레이어는 보지 못하는 사람에게 없는 사람으로 취급한다. AdminUtil의 투명화가 hidePlayer를 쓰므로 canSee 하나로 맞물린다.
 */
class PlayerLookup(private val server: Server, private val nicknames: Nicknames) {
    fun findOnline(viewer: CommandSender, name: String): Player? {
        val candidates = server.onlinePlayers.filter { viewer.canSeePlayer(it) }
        return candidates.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: candidates.firstOrNull { nicknames.of(it.uniqueId)?.equals(name, ignoreCase = true) == true }
    }

    /** 닉네임을 먼저 보여 준다. 닉네임이 있는 사람은 진짜 이름도 함께 제안해 영어로도 칠 수 있게 한다. */
    fun suggestOnline(viewer: CommandSender, prefix: String, excludeSelf: Boolean = true): List<String> {
        val visible = server.onlinePlayers.filter { viewer.canSeePlayer(it) && !(excludeSelf && it == viewer) }
        val nicknamesFirst = visible.mapNotNull { nicknames.of(it.uniqueId) } + visible.map { it.name }
        return nicknamesFirst.filter { it.startsWith(prefix, ignoreCase = true) }.distinct()
    }
}

/** 콘솔은 모두 볼 수 있다. */
fun CommandSender.canSeePlayer(target: Player): Boolean = this !is Player || canSee(target)
