package dev.doyu.smputil.advancement

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent
import org.bukkit.Server
import org.bukkit.advancement.Advancement
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.logging.Level

/**
 * 켜져 있으면 접속 중인 모든 플레이어의 도전과제 진행 상황을 합집합으로 맞춘다(DESIGN 7장).
 * 켤 때, 플레이어가 접속할 때, 누군가 세부 조건을 달성할 때 맞춘다.
 * 켜짐 여부는 `state.yml`에 저장해 재시작해도 유지한다.
 */
class AdvancementSync(private val plugin: Plugin, private val server: Server, private val stateFile: File) : Listener {
    var isEnabled: Boolean = loadEnabled()
        private set

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        saveEnabled()
        if (enabled) syncAll()
    }

    fun syncAll() {
        val iterator = server.advancementIterator()
        while (iterator.hasNext()) sync(iterator.next())
    }

    /**
     * 조건 달성 이벤트는 실제로 주기 전에 불리고, 그 안에서 다른 사람에게 조건을 주면 그 사람의 이벤트가 겹쳐 불린다.
     * 그래서 다음 틱에 그 도전과제 하나만 다시 맞춘다. 맞추기는 몇 번을 해도 결과가 같다.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCriterion(event: PlayerAdvancementCriterionGrantEvent) {
        if (!isEnabled) return
        val advancement = event.advancement
        plugin.server.scheduler.runTask(plugin) { _ -> if (isEnabled) sync(advancement) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (isEnabled) syncAll()
    }

    private fun sync(advancement: Advancement) {
        if (!isShared(advancement.key.key)) return
        val players = server.onlinePlayers.toList()
        if (players.size < 2) return
        val awarded = players.associateWith { it.getAdvancementProgress(advancement).awardedCriteria.toSet() }
        for ((player, missing) in missingCriteria(awarded)) {
            val progress = player.getAdvancementProgress(advancement)
            for (criterion in missing) progress.awardCriteria(criterion)
        }
    }

    private fun loadEnabled(): Boolean =
        stateFile.exists() && YamlConfiguration.loadConfiguration(stateFile).getBoolean(ENABLED_KEY, false)

    private fun saveEnabled() {
        val yaml = if (stateFile.exists()) YamlConfiguration.loadConfiguration(stateFile) else YamlConfiguration()
        yaml.set(ENABLED_KEY, isEnabled)
        try {
            stateFile.parentFile.mkdirs()
            yaml.save(stateFile)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Could not save ${stateFile.name}", e)
        }
    }

    private companion object {
        const val ENABLED_KEY = "advancement-sharing"
    }
}
