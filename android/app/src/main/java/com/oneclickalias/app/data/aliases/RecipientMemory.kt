package com.oneclickalias.app.data.aliases

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The recipient the user picked for new aliases when there are several. Only
 * the recipient id is kept; it is not secret. Deleted when the session ends.
 */
interface RecipientMemory {
    suspend fun get(): String?

    suspend fun set(recipientId: String)

    suspend fun clear()
}

/** [RecipientMemory] in the plain `appPreferences` DataStore. */
class DataStoreRecipientMemory(private val dataStore: DataStore<Preferences>) : RecipientMemory {
    override suspend fun get(): String? = dataStore.data.first()[KEY]

    override suspend fun set(recipientId: String) {
        dataStore.edit { it[KEY] = recipientId }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("alias_recipient_id")
    }
}
