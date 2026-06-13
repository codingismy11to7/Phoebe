package com.phoebe.app.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.openContextMenuOnSecondaryClick(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onOpen) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                    onOpen()
                }
            }
        }
    }
}
