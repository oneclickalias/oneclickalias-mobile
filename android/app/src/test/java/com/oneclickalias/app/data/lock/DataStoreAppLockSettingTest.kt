package com.oneclickalias.app.data.lock

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAppLockSettingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(folder.root, "app_preferences.preferences_pb")
    }

    @Test
    fun `off by default`() = runTest {
        assertFalse(DataStoreAppLockSetting(dataStore()).enabled.first())
    }

    @Test
    fun `keeps only a boolean`() = runTest {
        val store = dataStore()
        val setting = DataStoreAppLockSetting(store)

        setting.set(true)

        assertTrue(setting.enabled.first())
        assertEquals(mapOf(Pair(booleanPreferencesKey("app_lock_enabled"), true)), store.data.first().asMap())
    }

    @Test
    fun `clear turns it off`() = runTest {
        val setting = DataStoreAppLockSetting(dataStore())
        setting.set(true)

        setting.clear()

        assertFalse(setting.enabled.first())
    }
}
