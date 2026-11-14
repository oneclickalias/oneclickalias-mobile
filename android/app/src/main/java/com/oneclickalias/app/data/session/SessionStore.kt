package com.oneclickalias.app.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persists the [Session] of this device. */
interface SessionStore {
    /** The stored session, or null if there is none or it could not be read. */
    suspend fun read(): Session?

    suspend fun write(session: Session)

    suspend fun clear()
}

/**
 * [SessionStore] on a dedicated Preferences DataStore holding one encrypted blob
 * (tokens and device id as JSON, sealed by [cipher]). Nothing is stored in
 * plain text. A blob that cannot be decrypted or parsed (corrupt file, key lost
 * or invalidated) is deleted and reads as "no session".
 */
class EncryptedSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val cryptoContext: CoroutineContext = Dispatchers.Default
) : SessionStore {

    override suspend fun read(): Session? {
        val blob = dataStore.data.first()[KEY] ?: return null
        val session = withContext(cryptoContext) {
            try {
                json.decodeFromString(StoredSession.serializer(), cipher.decrypt(blob).decodeToString()).toSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deliberately not logged: the exception may describe the payload.
                null
            }
        }
        if (session == null) clear()
        return session
    }

    override suspend fun write(session: Session) {
        val blob = withContext(cryptoContext) {
            cipher.encrypt(
                json.encodeToString(StoredSession.serializer(), StoredSession.from(session)).encodeToByteArray()
            )
        }
        dataStore.edit { it[KEY] = blob }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    /** On-disk format inside the encrypted blob. Bump [version] on breaking changes. */
    @Serializable
    private data class StoredSession(
        @SerialName("v") val version: Int,
        @SerialName("at") val accessToken: String,
        @SerialName("rt") val refreshToken: String,
        @SerialName("dev") val deviceId: String,
        @SerialName("exp") val accessTokenExpiresAtMillis: Long,
        @SerialName("obt") val obtainedAtMillis: Long
    ) {
        fun toSession(): Session? = if (version != FORMAT_VERSION) {
            null
        } else {
            Session(accessToken, refreshToken, deviceId, accessTokenExpiresAtMillis, obtainedAtMillis)
        }

        override fun toString(): String = "StoredSession(<redacted>)"

        companion object {
            fun from(s: Session) = StoredSession(
                FORMAT_VERSION,
                s.accessToken,
                s.refreshToken,
                s.deviceId,
                s.accessTokenExpiresAtMillis,
                s.obtainedAtMillis
            )
        }
    }

    companion object {
        /** Preferences key of the blob (visible for tests). */
        const val KEY_NAME = "session"
        private const val FORMAT_VERSION = 1
        private val KEY = byteArrayPreferencesKey(KEY_NAME)
        private val json = Json { ignoreUnknownKeys = false }
    }
}
