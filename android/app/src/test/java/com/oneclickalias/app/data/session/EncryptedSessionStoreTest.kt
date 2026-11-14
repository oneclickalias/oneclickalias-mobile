package com.oneclickalias.app.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedSessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val session = Session(
        accessToken = "access-secret",
        refreshToken = "refresh-secret",
        deviceId = "9315bf6d-7672-44db-9c09-2dd3212c9b0a",
        accessTokenExpiresAtMillis = 1_000_900_000,
        obtainedAtMillis = 1_000_000_000
    )

    private fun TestScope.dataStore(file: File = folder.root.resolve("session.preferences_pb")) =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })

    @Test
    fun `empty store reads no session`() = runTest(UnconfinedTestDispatcher()) {
        val store = EncryptedSessionStore(dataStore(), AesGcmTokenCipher(SoftwareKeySource()), testDispatcher())

        assertNull(store.read())
    }

    @Test
    fun `written session reads back`() = runTest(UnconfinedTestDispatcher()) {
        val store = EncryptedSessionStore(dataStore(), AesGcmTokenCipher(SoftwareKeySource()), testDispatcher())

        store.write(session)

        assertEquals(session, store.read())
    }

    @Test
    fun `file on disk holds no token in plain text`() = runTest(UnconfinedTestDispatcher()) {
        val file = folder.root.resolve("session.preferences_pb")
        val store = EncryptedSessionStore(dataStore(file), AesGcmTokenCipher(SoftwareKeySource()), testDispatcher())

        store.write(session)

        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("access-secret"))
        assertFalse(raw.contains("refresh-secret"))
        assertFalse(raw.contains(session.deviceId))
    }

    @Test
    fun `clear removes the session`() = runTest(UnconfinedTestDispatcher()) {
        val store = EncryptedSessionStore(dataStore(), AesGcmTokenCipher(SoftwareKeySource()), testDispatcher())
        store.write(session)

        store.clear()

        assertNull(store.read())
    }

    @Test
    fun `corrupt blob is discarded instead of crashing`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = dataStore()
        val store = EncryptedSessionStore(prefs, AesGcmTokenCipher(SoftwareKeySource()), testDispatcher())
        store.write(session)
        prefs.edit { it[byteArrayPreferencesKey(EncryptedSessionStore.KEY_NAME)] = byteArrayOf(1, 2, 3) }

        assertNull(store.read())
        assertNull(prefs.data.first()[byteArrayPreferencesKey(EncryptedSessionStore.KEY_NAME)])
    }

    @Test
    fun `session from an invalidated key is discarded`() = runTest(UnconfinedTestDispatcher()) {
        val keys = SoftwareKeySource()
        val prefs = dataStore()
        val store = EncryptedSessionStore(prefs, AesGcmTokenCipher(keys), testDispatcher())
        store.write(session)
        keys.delete()

        assertNull(store.read())
        assertNull(prefs.data.first()[byteArrayPreferencesKey(EncryptedSessionStore.KEY_NAME)])
    }

    @Test
    fun `decrypted but malformed json is discarded`() = runTest(UnconfinedTestDispatcher()) {
        val cipher = AesGcmTokenCipher(SoftwareKeySource())
        val prefs = dataStore()
        prefs.edit {
            it[byteArrayPreferencesKey(EncryptedSessionStore.KEY_NAME)] =
                cipher.encrypt("{\"v\":1}".toByteArray())
        }
        val store = EncryptedSessionStore(prefs, cipher, testDispatcher())

        assertNull(store.read())
    }

    private fun TestScope.testDispatcher() = coroutineContext[kotlin.coroutines.ContinuationInterceptor]!!
}
