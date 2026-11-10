package dev.doyu.smputil.nickname

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class NameInMessagesTest {
    private fun plain(component: Component): String = PlainTextComponentSerializer.plainText().serialize(component)

    @Test
    fun `names inside translation arguments are replaced`() {
        val message = Component.translatable("death.attack.player", Component.text("a"), Component.text("Steve"))

        val renamed = message.withShownName("a", "홍길동").withShownName("Steve", "철수")

        val arguments = (renamed as net.kyori.adventure.text.TranslatableComponent).arguments().map { plain(it.asComponent()) }
        assertEquals(listOf("홍길동", "철수"), arguments)
    }

    @Test
    fun `only whole name pieces are replaced, not letters inside other words`() {
        val message = Component.text("a").append(Component.text(" was slain by a zombie"))

        assertEquals("홍길동 was slain by a zombie", plain(message.withShownName("a", "홍길동")))
    }
}
