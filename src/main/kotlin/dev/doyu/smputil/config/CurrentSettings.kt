package dev.doyu.smputil.config

import org.bukkit.configuration.ConfigurationSection

/**
 * 지금 적용 중인 설정. 기능들은 값을 생성자에 박지 않고 쓸 때마다 여기서 읽어, 다시 읽기(`/smpreload`)가 곧바로 반영된다.
 * 새 설정이 틀리면 바꾸지 않는다: 시작할 때는 실패해도 되지만, 운영 중에 설정 실수로 서버 기능이 멈추면 안 된다.
 */
class CurrentSettings(initial: SmpSettings) {
    @Volatile
    var value: SmpSettings = initial
        private set

    /** 틀린 값이 있으면 [IllegalArgumentException]을 던지고 이전 설정을 그대로 둔다. */
    fun replaceWith(config: ConfigurationSection) {
        value = readSettings(config)
    }
}
