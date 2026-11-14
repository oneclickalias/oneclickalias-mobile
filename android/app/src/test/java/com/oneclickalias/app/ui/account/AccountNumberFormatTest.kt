package com.oneclickalias.app.ui.account

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountNumberFormatTest {
    @Test
    fun `sanitize keeps ascii digits only and caps at 16`() {
        assertEquals("1234567890123456", AccountNumberFormat.sanitize("1234 5678-9012 3456"))
        assertEquals("1234567890123456", AccountNumberFormat.sanitize(" 1234-5678-9012-3456-7890 "))
        assertEquals("12", AccountNumberFormat.sanitize("a1b2c"))
        // Arabic-Indic and full-width digits are not account-number digits.
        assertEquals("", AccountNumberFormat.sanitize("١٢３"))
    }

    @Test
    fun `grouped splits into blocks of four`() {
        assertEquals("1234 5678 9012 3456", AccountNumberFormat.grouped("1234567890123456"))
        assertEquals("1234 5", AccountNumberFormat.grouped("12345"))
        assertEquals("", AccountNumberFormat.grouped(""))
    }

    @Test
    fun `spoken form reads digit by digit in groups`() {
        assertEquals("1 2 3 4, 5 6 7 8", AccountNumberFormat.spoken("12345678"))
    }

    @Test
    fun `complete means exactly sixteen digits`() {
        assertTrue(AccountNumberFormat.isComplete("1234567890123456"))
        assertFalse(AccountNumberFormat.isComplete("123456789012345"))
        assertFalse(AccountNumberFormat.isComplete("123456789012345a"))
    }

    @Test
    fun `visual transformation groups and optionally masks`() {
        val shown = AccountNumberVisualTransformation(masked = false).filter(AnnotatedString("123456789"))
        val masked = AccountNumberVisualTransformation(masked = true).filter(AnnotatedString("123456789"))

        assertEquals("1234 5678 9", shown.text.text)
        assertEquals("•••• •••• •", masked.text.text)
    }

    @Test
    fun `cursor offsets map both ways for every length`() {
        val digits = "1234567890123456"
        for (length in 0..16) {
            val mapping = AccountNumberVisualTransformation(masked = false)
                .filter(AnnotatedString(digits.take(length))).offsetMapping
            val transformedLength = AccountNumberFormat.grouped(digits.take(length)).length
            for (offset in 0..length) {
                val transformed = mapping.originalToTransformed(offset)
                assertTrue("o2t($offset) in bounds for $length", transformed in 0..transformedLength)
                assertEquals("round trip at $offset of $length", offset, mapping.transformedToOriginal(transformed))
            }
            for (t in 0..transformedLength) {
                assertTrue("t2o($t) in bounds for $length", mapping.transformedToOriginal(t) in 0..length)
            }
        }
    }
}
