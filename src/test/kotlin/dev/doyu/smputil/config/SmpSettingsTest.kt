package dev.doyu.smputil.config

import org.bukkit.configuration.file.YamlConfiguration
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmpSettingsTest {
    private fun bundledConfig(): YamlConfiguration {
        val text = javaClass.getResource("/config.yml")!!.readText()
        return YamlConfiguration().apply { loadFromString(text) }
    }

    @Test
    fun `bundled config is valid and matches the design defaults`() {
        val settings = readSettings(bundledConfig())

        assertEquals(1..16, settings.nickname.lengths)
        assertTrue(settings.nickname.allowed.matches("홍길동_99"))
        assertTrue(settings.home.enabled)
        assertEquals(1, settings.home.maxHomes)
        assertEquals(Duration.ZERO, settings.teleportWarmup)
        assertEquals(Duration.ofSeconds(15), settings.tpaExpiry)
        assertEquals(Duration.ofSeconds(15), settings.tradeExpiry)
        assertNull(settings.tradeMaxDistance)
    }

    @Test
    fun `nickname longer than the protocol allows is rejected at startup`() {
        val config = bundledConfig().apply { set("nickname.max-length", 17) }

        val error = assertFailsWith<IllegalArgumentException> { readSettings(config) }
        assertTrue("nickname.max-length" in error.message.orEmpty())
    }

    @Test
    fun `wrong values fail instead of falling back to defaults`() {
        val cases = mapOf(
            "nickname.min-length" to 0,
            "nickname.allowed" to "[",
            "home.enabled" to "yes",
            "home.max-homes" to 0,
            "teleport.warmup-seconds" to -1,
            "tpa.expire-seconds" to 0,
            "trade.expire-seconds" to "soon",
            "trade.max-distance" to -5,
        )
        for ((path, value) in cases) {
            val config = bundledConfig().apply { set(path, value) }
            val error = assertFailsWith<IllegalArgumentException>(path) { readSettings(config) }
            assertTrue(path in error.message.orEmpty(), "$path: ${error.message}")
        }
    }

    @Test
    fun `positive trade distance becomes a limit`() {
        val config = bundledConfig().apply { set("trade.max-distance", 10) }

        assertEquals(10.0, readSettings(config).tradeMaxDistance)
    }
}
