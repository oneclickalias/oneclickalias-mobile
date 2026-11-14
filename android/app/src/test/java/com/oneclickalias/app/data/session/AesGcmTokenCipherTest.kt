package com.oneclickalias.app.data.session

import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTokenCipherTest {
    private val keys = SoftwareKeySource()
    private val cipher = AesGcmTokenCipher(keys)
    private val plain = "refresh-token-and-friends".toByteArray()

    @Test
    fun `round trip returns the plaintext`() {
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun `every encryption uses a fresh iv`() {
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)

        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(2, 14).contentEquals(b.copyOfRange(2, 14)))
    }

    @Test
    fun `blob carries a version byte and no plaintext`() {
        val blob = cipher.encrypt(plain)

        assertEquals(1, blob[0].toInt())
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("refresh-token"))
    }

    @Test
    fun `tampered ciphertext fails`() {
        val blob = cipher.encrypt(plain)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob) }
    }

    @Test
    fun `truncated or foreign blobs fail`() {
        val blob = cipher.encrypt(plain)

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob.copyOf(10)) }
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(ByteArray(0)) }
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(byteArrayOf(9) + blob.copyOfRange(1, blob.size))
        }
    }

    @Test
    fun `data encrypted with a lost key fails`() {
        val blob = cipher.encrypt(plain)
        keys.delete()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob) }
    }

    @Test
    fun `broken key is replaced on encrypt`() {
        val broken = object : SecretKeySource {
            var calls = 0
            val good = SoftwareKeySource()
            override fun getOrCreate() = if (calls++ ==
                0
            ) {
                javax.crypto.spec.SecretKeySpec(ByteArray(3), "AES")
            } else {
                good.getOrCreate()
            }
            override fun delete() {}
        }
        val retrying = AesGcmTokenCipher(broken)

        assertArrayEquals(plain, retrying.decrypt(retrying.encrypt(plain)))
    }
}
