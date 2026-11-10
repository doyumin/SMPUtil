package dev.doyu.smputil.nickname

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import net.minecraft.network.HandlerNames
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 클라이언트로 나가는 플레이어 정보 패킷의 프로필 이름만 닉네임으로 바꾼다. 이름표가 바닐라와 똑같이 동작하게 하기 위해서다(DESIGN 3장).
 * 서버 안의 진짜 이름(GameProfile)은 그대로라 바닐라 명령어가 영어 이름을 계속 읽는다.
 * 스킨(프로필 속성)은 그대로 보낸다.
 *
 * NMS 클래스를 직접 쓴다. 리플렉션으로 필드를 찾으면 버전이 바뀔 때 실행 중에 조용히 깨지지만, 이렇게 하면 컴파일에서 깨진다.
 * 플레이어 정보 패킷은 묶음(bundle) 패킷 안에 담겨 나가지 않으므로 묶음은 열어 보지 않는다(Paper 26.3 서버 코드에서 확인).
 */
class NicknameRewriter(private val nicknameOf: (UUID) -> String?, private val logger: Logger) : ChannelOutboundHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val rewritten = if (msg is ClientboundPlayerInfoUpdatePacket) withNicknames(msg) else msg
        ctx.write(rewritten, promise)
    }

    private fun withNicknames(packet: ClientboundPlayerInfoUpdatePacket): ClientboundPlayerInfoUpdatePacket {
        // 프로필 이름은 ADD_PLAYER에만 실린다.
        if (ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER !in packet.actions()) return packet
        if (packet.entries().none { nicknameOf(it.profileId()) != null }) return packet
        return try {
            val entries = packet.entries().map { entry ->
                val nickname = nicknameOf(entry.profileId())
                val profile = entry.profile()
                if (nickname == null || profile == null) {
                    entry
                } else {
                    ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(),
                        GameProfile(profile.id(), nickname, profile.properties()),
                        entry.listed(),
                        entry.latency(),
                        entry.gameMode(),
                        entry.displayName(),
                        entry.showHat(),
                        entry.listOrder(),
                        entry.chatSession(),
                    )
                }
            }
            ClientboundPlayerInfoUpdatePacket(packet.actions(), entries)
        } catch (e: RuntimeException) {
            // 바꿔치기에 실패해도 접속은 유지되어야 한다. 이름표만 진짜 이름으로 보인다.
            logger.log(Level.WARNING, "Could not rewrite player info packet; sending it unchanged", e)
            packet
        }
    }
}

/** 플레이어마다 연결 파이프라인에 [NicknameRewriter]를 끼우고 뺀다. */
class NicknameChannels(private val nicknames: Nicknames, private val logger: Logger) {
    fun attach(player: Player) {
        val pipeline = (player as CraftPlayer).handle.connection.connection.channel.pipeline()
        if (pipeline.get(HANDLER_NAME) != null) return
        // packet_handler(Connection)가 쓴 패킷이 인코더로 가는 길목에 끼운다.
        pipeline.addBefore(HandlerNames.PACKET_HANDLER, HANDLER_NAME, NicknameRewriter(nicknames::of, logger))
    }

    fun detach(player: Player) {
        val pipeline = (player as CraftPlayer).handle.connection.connection.channel.pipeline()
        if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME)
    }

    private companion object {
        const val HANDLER_NAME = "smputil_nickname"
    }
}
