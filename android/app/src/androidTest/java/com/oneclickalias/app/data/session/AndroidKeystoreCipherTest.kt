package com.oneclickalias.app.data.session

import android.security.keystore.KeyInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device: the real Android Keystore behind [AesGcmTokenCipher].
 * Uses its own key alias, so the app's session key is never touched.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCipherTest {
    private val keys = AndroidKeystoreKeySource(alias = TEST_ALIAS)
    private val cipher = AesGcmTokenCipher(keys)
    private val plain = "access+refresh+device".toByteArray()

    @After
    fun tearDown() {
        keys.delete()
    }

    @Test
    fun roundTrip() {
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun everyEncryptionDiffers() {
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)

        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, cipher.decrypt(a))
        assertArrayEquals(plain, cipher.decrypt(b))
    }

    @Test
    fun tamperedDataFails() {
        val blob = cipher.encrypt(plain)
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 0x01).toByte()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob) }
    }

    @Test
    fun keyIsNotExportableAndAes256() {
        val key = keys.getOrCreate()
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo

        assertNull("key material must not leave the Keystore", key.encoded)
        assertEquals(256, info.keySize)
        assertFalse(info.isUserAuthenticationRequired)
    }

    @Test
    fun keySurvivesANewInstance() {
        val blob = cipher.encrypt(plain)

        assertArrayEquals(plain, AesGcmTokenCipher(AndroidKeystoreKeySource(alias = TEST_ALIAS)).decrypt(blob))
    }

    @Test
    fun deletedKeyMakesOldDataUnreadable() {
        val blob = cipher.encrypt(plain)
        keys.delete()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(blob) }
        assertTrue(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(TEST_ALIAS))
    }

    @Test
    fun encryptedSessionStoreRoundTripsWithTheKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "keystore-test-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = EncryptedSessionStore(PreferenceDataStoreFactory.create(scope = scope) { file }, cipher)
            val session = Session("access", "refresh", "device", 2L, 1L)

            store.write(session)

            assertEquals(session, store.read())
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("refresh"))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private companion object {
        const val TEST_ALIAS = "oneclickalias.session.instrumented-test"
    }
}
