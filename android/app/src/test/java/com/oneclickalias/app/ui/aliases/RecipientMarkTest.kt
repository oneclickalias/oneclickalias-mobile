package com.oneclickalias.app.ui.aliases

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientMarkTest {
    @Test
    fun `the mark is the first letter of the mail provider`() {
        assertEquals('O', recipientInitial("abdu.8524@outlook.de"))
        assertEquals('P', recipientInitial("abdu_8524@protonmail.com"))
    }

    @Test
    fun `without a domain the address itself gives the letter`() {
        assertEquals('N', recipientInitial("no-domain"))
        assertEquals('N', recipientInitial("no-domain@"))
    }

    @Test
    fun `nothing usable gives a neutral mark`() {
        assertEquals('?', recipientInitial(""))
        assertEquals('?', recipientInitial("@."))
    }
}
