package com.oneclickalias.app.testing

import com.oneclickalias.app.data.aliases.PendingClaims
import com.oneclickalias.app.data.aliases.RecipientMemory
import com.oneclickalias.app.data.lock.AppLockSetting
import com.oneclickalias.app.data.lock.AuthPurpose
import com.oneclickalias.app.data.lock.DeviceAuthenticator
import com.oneclickalias.app.platform.AliasClipboard
import com.oneclickalias.app.platform.WebLinks
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl

/** [RecipientMemory] in a field. */
class InMemoryRecipientMemory(@Volatile var remembered: String? = null) : RecipientMemory {
    override suspend fun get(): String? = remembered

    override suspend fun set(recipientId: String) {
        remembered = recipientId
    }

    override suspend fun clear() {
        remembered = null
    }
}

/** Records copies; [systemConfirms] plays Android 13+ (true) or older versions (false). */
class FakeAliasClipboard(var systemConfirms: Boolean = true) : AliasClipboard {
    val copies: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun copy(alias: String): Boolean {
        copies += alias
        return systemConfirms
    }
}

/** Records opened links; [browserInstalled] false plays a device without a browser. */
class FakeWebLinks(var browserInstalled: Boolean = true) : WebLinks {
    val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun open(url: HttpUrl): Boolean {
        opened += url.toString()
        return browserInstalled
    }
}

/** [AppLockSetting] in a flow. */
class InMemoryAppLockSetting(enabled: Boolean = false) : AppLockSetting {
    val value = MutableStateFlow(enabled)

    override val enabled: Flow<Boolean> = value

    override suspend fun set(enabled: Boolean) {
        value.value = enabled
    }

    override suspend fun clear() {
        value.value = false
    }
}

/**
 * Plays the system prompt: [authenticate] waits until the test calls
 * [answer]. [available] false plays a phone without a screen lock.
 */
class FakeDeviceAuthenticator(@Volatile var available: Boolean = true) : DeviceAuthenticator {
    val prompts: MutableList<AuthPurpose> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    val showing: Boolean get() = pending?.isActive == true

    override fun canAuthenticate(): Boolean = available

    override suspend fun authenticate(purpose: AuthPurpose): Boolean {
        prompts += purpose
        val answer = CompletableDeferred<Boolean>()
        pending = answer
        return answer.await()
    }

    /** true: fingerprint, face or screen lock accepted; false: cancelled or failed. */
    fun answer(success: Boolean) {
        checkNotNull(pending) { "no prompt open" }.complete(success)
    }
}

/** [PendingClaims] in memory. */
class InMemoryPendingClaims : PendingClaims {
    val ids: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    override suspend fun all(): Set<String> = synchronized(ids) { ids.toSet() }

    override suspend fun add(id: String) {
        ids += id
    }

    override suspend fun remove(id: String) {
        ids -= id
    }

    override suspend fun clear() {
        ids.clear()
    }
}
