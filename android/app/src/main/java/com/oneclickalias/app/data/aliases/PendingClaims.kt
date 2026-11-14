package com.oneclickalias.app.data.aliases

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * IDs of copied ready aliases whose claim has not reached the server yet
 * (docs/flows.md, "New alias tab"). Only the IDs are kept; they are not
 * secret. Deleted when the session ends.
 */
interface PendingClaims {
    suspend fun all(): Set<String>

    suspend fun add(id: String)

    suspend fun remove(id: String)

    suspend fun clear()
}

/** [PendingClaims] in the plain `appPreferences` DataStore. */
class DataStorePendingClaims(private val dataStore: DataStore<Preferences>) : PendingClaims {
    override suspend fun all(): Set<String> = dataStore.data.first()[KEY].orEmpty()

    override suspend fun add(id: String) {
        dataStore.edit { it[KEY] = it[KEY].orEmpty() + id }
    }

    override suspend fun remove(id: String) {
        dataStore.edit { it[KEY] = it[KEY].orEmpty() - id }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("pending_alias_claims")
    }
}
