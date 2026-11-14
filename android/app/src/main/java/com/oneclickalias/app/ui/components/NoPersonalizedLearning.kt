package com.oneclickalias.app.ui.components

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard not to learn from what is typed in [content]
 * (`IME_FLAG_NO_PERSONALIZED_LEARNING`), the flag incognito modes use. Compose
 * has no `KeyboardOptions` switch for it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoPersonalizedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val modified = PlatformTextInputMethodRequest { outAttributes: EditorInfo ->
                val connection: InputConnection = request.createInputConnection(outAttributes)
                outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                connection
            }
            nextHandler.startInputMethod(modified)
        },
        content = content
    )
}
