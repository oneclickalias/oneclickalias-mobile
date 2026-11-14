package com.oneclickalias.app.data.aliases

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreRecipientMemoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(folder.root, "app_preferences.preferences_pb")
    }

    @Test
    fun `nothing is remembered at first`() = runTest {
        assertNull(DataStoreRecipientMemory(dataStore()).get())
    }

    @Test
    fun `remembers only the recipient id`() = runTest {
        val store = dataStore()
        val memory = DataStoreRecipientMemory(store)

        memory.set("r-work")

        assertEquals("r-work", memory.get())
        val prefs = store.data.first().asMap()
        assertEquals(mapOf(Pair(stringPreferencesKey("alias_recipient_id"), "r-work")), prefs)
    }

    @Test
    fun `clear forgets the choice`() = runTest {
        val memory = DataStoreRecipientMemory(dataStore())
        memory.set("r-work")

        memory.clear()

        assertNull(memory.get())
    }
}
