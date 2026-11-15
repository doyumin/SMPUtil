package dev.doyu.smputil.config

import org.bukkit.configuration.file.YamlConfiguration
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CurrentSettingsTest {
    private fun bundledConfig(): YamlConfiguration =
        YamlConfiguration().apply { loadFromString(javaClass.getResource("/config.yml")!!.readText()) }

    @Test
    fun `new valid values are used from then on`() {
        val current = CurrentSettings(readSettings(bundledConfig()))
        assertEquals(Duration.ofSeconds(15), current.value.tpaExpiry)

        current.replaceWith(bundledConfig().apply { set("tpa.expire-seconds", 30) })

        assertEquals(Duration.ofSeconds(30), current.value.tpaExpiry)
    }

    @Test
    fun `a wrong value keeps the previous settings`() {
        val current = CurrentSettings(readSettings(bundledConfig()))
        val before = current.value

        assertFailsWith<IllegalArgumentException> {
            current.replaceWith(bundledConfig().apply { set("home.max-homes", 0) })
        }

        assertSame(before, current.value)
    }
}
