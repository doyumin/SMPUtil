package dev.doyu.smputil.suggestion

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Duration

private const val TEXT_KEY = "text"
private const val FIELD_WIDTH = 300
private const val FIELD_HEIGHT = 110
private const val FIELD_MAX_LINES = 6

/**
 * `/제안`을 인자 없이 치면 여는 입력창. [보내기]를 눌러야만 제출되고, 닫거나 [취소]하면 아무것도 남지 않는다.
 * 책과 깃펜 방식은 쓰지 않는다: 책은 실제 아이템이라 모든 종료 경로에서 증발·복제를 막아야 한다(DESIGN 2장).
 */
fun suggestionDialog(maxLength: Int, onSubmit: (Audience, String) -> Unit): Dialog {
    val input = DialogInput.text(TEXT_KEY, Component.text("제안 내용"))
        .width(FIELD_WIDTH)
        .maxLength(maxLength)
        .multiline(TextDialogInput.MultilineOptions.create(FIELD_MAX_LINES, FIELD_HEIGHT))
        .build()
    val send = ActionButton.builder(Component.text("보내기", NamedTextColor.GREEN))
        .action(
            DialogAction.customClick(
                { response, audience -> onSubmit(audience, response.getText(TEXT_KEY).orEmpty()) },
                // 열어 둔 채 자리를 비운 창으로 한참 뒤에 제출되는 일이 없게 하고, 한 창에서 한 번만 보낼 수 있게 한다.
                ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build(),
            ),
        )
        .build()
    val cancel = ActionButton.builder(Component.text("취소", NamedTextColor.GRAY)).build()

    return Dialog.create { factory ->
        factory.empty()
            .base(
                DialogBase.builder(Component.text("서버에 제안하기"))
                    .body(listOf(DialogBody.plainMessage(Component.text("불편한 점이나 바라는 것을 자유롭게 적어 주세요. 최대 ${maxLength}자."))))
                    .inputs(listOf(input))
                    .build(),
            )
            .type(DialogType.confirmation(send, cancel))
    }
}
