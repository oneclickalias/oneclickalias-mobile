package com.oneclickalias.app.data.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingQrTest {
    private val code = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U"

    @Test
    fun `a version 1 payload yields its code`() {
        assertEquals(PairingQr.Valid(code), PairingQr.parse("OCA-PAIR-1-$code"))
    }

    @Test
    fun `another version asks for an app update`() {
        assertEquals(PairingQr.UnsupportedVersion, PairingQr.parse("OCA-PAIR-2-$code"))
        assertEquals(PairingQr.UnsupportedVersion, PairingQr.parse("OCA-PAIR-10-$code"))
        assertEquals(PairingQr.UnsupportedVersion, PairingQr.parse("OCA-PAIR-X"))
    }

    @Test
    fun `anything else is not ours`() {
        for (text in listOf(
            "",
            "https://oneclickalias.com/",
            "OCA-PAIR",
            "OCA-PAIR-1-",
            "OCA-PAIR-1-${code.drop(1)}",
            "OCA-PAIR-1-${code}A",
            "OCA-PAIR-1-${code.lowercase()}",
            "OCA-PAIR-1-${code.replaceFirst('M', '1')}",
            "oca-pair-1-$code"
        )) {
            assertEquals(text, PairingQr.NotOurs, PairingQr.parse(text))
        }
    }

    @Test
    fun `nothing is normalised`() {
        assertEquals(PairingQr.NotOurs, PairingQr.parse(" OCA-PAIR-1-$code"))
        assertEquals(PairingQr.NotOurs, PairingQr.parse("OCA-PAIR-1-$code "))
        assertEquals(PairingQr.NotOurs, PairingQr.parse("OCA-PAIR-1-$code\n"))
    }

    @Test
    fun `the code is not printed`() {
        assertEquals(false, PairingQr.Valid(code).toString().contains(code))
    }
}
