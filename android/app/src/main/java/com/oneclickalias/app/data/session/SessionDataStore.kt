package com.oneclickalias.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Holds only the encrypted session blob of [EncryptedSessionStore], apart from
 * the plain `appPreferences`. Excluded from backups like everything else.
 */
val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
