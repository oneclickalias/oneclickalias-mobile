package com.oneclickalias.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Non-sensitive app settings. Secrets (account number, tokens) never go in
 * here; they need Keystore-backed encryption.
 */
val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")
