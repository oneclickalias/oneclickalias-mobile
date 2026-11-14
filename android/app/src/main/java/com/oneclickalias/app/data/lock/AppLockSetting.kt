package com.oneclickalias.app.data.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the user turned app lock on. Not secret (a plain boolean), reset to
 * off whenever the session ends, so the next account starts with it off.
 */
interface AppLockSetting {
    val enabled: Flow<Boolean>

    suspend fun set(enabled: Boolean)

    suspend fun clear()
}

/** [AppLockSetting] in the plain `appPreferences` DataStore. */
class DataStoreAppLockSetting(private val dataStore: DataStore<Preferences>) : AppLockSetting {
    override val enabled: Flow<Boolean> = dataStore.data.map { it[KEY] == true }

    override suspend fun set(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = booleanPreferencesKey("app_lock_enabled")
    }
}
