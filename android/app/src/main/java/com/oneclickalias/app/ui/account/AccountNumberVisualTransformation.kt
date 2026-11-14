package com.oneclickalias.app.ui.account

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows the typed digits in groups of four ("1234 5678 …"), as bullets when
 * [masked]. The field value itself stays digits only.
 */
class AccountNumberVisualTransformation(private val masked: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = if (masked) MASK.toString().repeat(text.length) else text.text
        val grouped = AccountNumberFormat.grouped(digits)
        val mapping = object : OffsetMapping {
            // One separator after every full group of four before the offset.
            override fun originalToTransformed(offset: Int): Int = if (offset <=
                0
            ) {
                0
            } else {
                (offset + (offset - 1) / GROUP_WITH_SEPARATOR_DIGITS).coerceAtMost(grouped.length)
            }

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / (GROUP_WITH_SEPARATOR_DIGITS + 1)).coerceIn(0, text.length)
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }

    override fun equals(other: Any?): Boolean = other is AccountNumberVisualTransformation && other.masked == masked

    override fun hashCode(): Int = masked.hashCode()

    private companion object {
        const val MASK = '•'
        const val GROUP_WITH_SEPARATOR_DIGITS = 4
    }
}
