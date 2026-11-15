package dev.doyu.smputil

import dev.doyu.smputil.advancement.AdvancementSync
import dev.doyu.smputil.advancement.SyncAdvancementCommand
import dev.doyu.smputil.config.CurrentSettings
import dev.doyu.smputil.config.ReloadConfigCommand
import dev.doyu.smputil.config.readSettings
import dev.doyu.smputil.home.DeleteHomeCommand
import dev.doyu.smputil.home.HomeBook
import dev.doyu.smputil.home.HomeCommand
import dev.doyu.smputil.home.HomeFile
import dev.doyu.smputil.home.Homes
import dev.doyu.smputil.home.SetHomeCommand
import dev.doyu.smputil.nickname.NickCommand
import dev.doyu.smputil.nickname.NicknameChannels
import dev.doyu.smputil.nickname.NicknameListener
import dev.doyu.smputil.nickname.NicknameRules
import dev.doyu.smputil.nickname.Nicknames
import dev.doyu.smputil.players.PlayerLookup
import dev.doyu.smputil.players.PlayersCommand
import dev.doyu.smputil.request.RequestBoard
import dev.doyu.smputil.suggestion.DiscordWebhook
import dev.doyu.smputil.suggestion.SuggestCommand
import dev.doyu.smputil.suggestion.SuggestionBook
import dev.doyu.smputil.suggestion.SuggestionFile
import dev.doyu.smputil.suggestion.SuggestionReminder
import dev.doyu.smputil.teleport.SpawnCommand
import dev.doyu.smputil.teleport.TeleportRequests
import dev.doyu.smputil.teleport.Teleporter
import dev.doyu.smputil.teleport.TpAcceptCommand
import dev.doyu.smputil.teleport.TpDenyCommand
import dev.doyu.smputil.teleport.TpaCommand
import dev.doyu.smputil.trade.TradeCommand
import dev.doyu.smputil.trade.Trades
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Clock

class SMPUtilPlugin : JavaPlugin() {
    private var trades: Trades? = null
    private var nicknameChannels: NicknameChannels? = null
    private var reminderTask: BukkitTask? = null

    override fun onEnable() {
        saveDefaultConfig()
        // 시작할 때 설정이 틀리면 여기서 예외가 나 플러그인이 켜지지 않는다.
        val current = CurrentSettings(readSettings(config))
        val clock = Clock.systemUTC()
        dataFolder.mkdirs()

        val nicknames = Nicknames(this, server)
        val channels = NicknameChannels(nicknames, logger)
        nicknameChannels = channels
        val lookup = PlayerLookup(server, nicknames)
        val teleporter = Teleporter(this) { current.value.teleportWarmup }
        val homeFile = HomeFile(dataFolder.resolve("homes.yml"))
        val homes = Homes(HomeBook(homeFile.load()), homeFile, { current.value.home }, server, logger)
        val teleportRequests = TeleportRequests(RequestBoard(clock) { current.value.tpaExpiry }, server, nicknames, teleporter)
        val trades = Trades(this, server, RequestBoard(clock) { current.value.tradeExpiry }, nicknames) { current.value.tradeMaxDistance }
        this.trades = trades
        val suggestionFile = SuggestionFile(dataFolder.resolve("suggestions.yml"))
        val suggestionBook = SuggestionBook(suggestionFile.load(), clock, { current.value.suggestion.maxLength }, { current.value.suggestion.cooldown })
        val discordWebhook = DiscordWebhook({ current.value.suggestion.discordWebhook }, logger)
        val advancementSync = AdvancementSync(this, server, dataFolder.resolve("state.yml"))

        // 이미 접속해 있는 플레이어(서버가 켜진 채 플러그인만 올라온 경우)에게도 닉네임을 적용한다.
        for (player in server.onlinePlayers) {
            nicknames.load(player)
            channels.attach(player)
        }

        server.pluginManager.registerEvents(NicknameListener(nicknames, channels), this)
        server.pluginManager.registerEvents(teleporter, this)
        server.pluginManager.registerEvents(teleportRequests, this)
        server.pluginManager.registerEvents(trades, this)
        server.pluginManager.registerEvents(advancementSync, this)
        server.scheduler.runTaskTimer(this, { _ ->
            teleportRequests.sweepExpired()
            trades.sweepExpired()
        }, TICKS_PER_SECOND, TICKS_PER_SECOND)

        val reminder = SuggestionReminder(server)
        scheduleReminder(current, reminder)
        val reloadSettings = {
            reloadConfig()
            current.replaceWith(config)
            scheduleReminder(current, reminder)
        }

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register("nick", "한글 닉네임을 정합니다.", listOf("닉네임"), NickCommand(nicknames, { NicknameRules(current.value.nickname) }, lookup))
            commands.register("home", "집으로 이동합니다.", listOf("집"), HomeCommand(homes, teleporter))
            commands.register("sethome", "지금 위치를 집으로 정합니다.", listOf("집설정"), SetHomeCommand(homes))
            commands.register("delhome", "집을 지웁니다.", listOf("집삭제"), DeleteHomeCommand(homes))
            commands.register("spawn", "리스폰 지점이나 월드 스폰으로 이동합니다.", listOf("스폰"), SpawnCommand(server, teleporter))
            commands.register("tpa", "다른 플레이어에게 텔레포트를 요청합니다.", listOf("텔포"), TpaCommand(teleportRequests, lookup))
            commands.register("tpaccept", "받은 텔레포트 요청을 수락합니다.", listOf("수락"), TpAcceptCommand(teleportRequests))
            commands.register("tpdeny", "받은 텔레포트 요청을 거절합니다.", listOf("거절"), TpDenyCommand(teleportRequests))
            commands.register("trade", "다른 플레이어와 거래합니다.", listOf("거래"), TradeCommand(trades, lookup))
            commands.register("syncadv", "도전과제 공유 상태를 보거나 켜고 끕니다.", listOf("도전과제공유"), SyncAdvancementCommand(advancementSync))
            commands.register("players", "접속자 목록을 봅니다.", listOf("접속자"), PlayersCommand(server, nicknames))
            commands.register("suggest", "서버에 바라는 것을 제안합니다.", listOf("제안"), SuggestCommand(this, suggestionBook, suggestionFile, discordWebhook, nicknames, { current.value.suggestion.maxLength }, logger))
            commands.register("smpreload", "config.yml만 다시 읽습니다.", listOf("설정리로드"), ReloadConfigCommand(reloadSettings, logger))
        }
    }

    /** 간격이 바뀔 수 있어 다시 읽을 때마다 이전 타이머를 끊고 새로 건다. 0이면 걸지 않는다. */
    private fun scheduleReminder(current: CurrentSettings, reminder: SuggestionReminder) {
        reminderTask?.cancel()
        val interval = current.value.suggestion.reminderInterval
        reminderTask = interval?.let {
            val ticks = it.seconds * TICKS_PER_SECOND
            server.scheduler.runTaskTimer(this, Runnable { reminder.remind() }, ticks, ticks)
        }
    }

    override fun onDisable() {
        // 플레이어 저장보다 먼저 일어나므로, 여기서 돌려준 아이템은 저장된다.
        trades?.endAll()
        val channels = nicknameChannels ?: return
        for (player in server.onlinePlayers) channels.detach(player)
    }

    private companion object {
        const val TICKS_PER_SECOND = 20L
    }
}
