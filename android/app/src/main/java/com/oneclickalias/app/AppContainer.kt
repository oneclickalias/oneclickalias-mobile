package com.oneclickalias.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.oneclickalias.app.data.aliases.ClaimDelivery
import com.oneclickalias.app.data.aliases.DataStorePendingClaims
import com.oneclickalias.app.data.aliases.DataStoreRecipientMemory
import com.oneclickalias.app.data.aliases.PendingClaims
import com.oneclickalias.app.data.aliases.RecipientMemory
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.Endpoints
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.lock.AppLock
import com.oneclickalias.app.data.lock.AppLockSetting
import com.oneclickalias.app.data.lock.DataStoreAppLockSetting
import com.oneclickalias.app.data.lock.DeviceAuthenticator
import com.oneclickalias.app.data.session.AesGcmTokenCipher
import com.oneclickalias.app.data.session.AndroidKeystoreKeySource
import com.oneclickalias.app.data.session.EncryptedSessionStore
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.data.session.SessionStore
import com.oneclickalias.app.data.session.sessionDataStore
import com.oneclickalias.app.data.store.appPreferences
import com.oneclickalias.app.platform.AliasClipboard
import com.oneclickalias.app.platform.BiometricAuthenticator
import com.oneclickalias.app.platform.BrowserLinks
import com.oneclickalias.app.platform.ClaimWorker
import com.oneclickalias.app.platform.SecretClipboard
import com.oneclickalias.app.platform.SensitiveClipboard
import com.oneclickalias.app.platform.SystemAliasClipboard
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.app.platform.passwordManagerAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Manual dependency injection: the app-wide singletons, created once in
 * [OcaApplication]. Features take what they need from here. Tests pass their
 * own [endpoints] (MockWebServer), [sessionStore] (no Android Keystore on
 * the JVM), [recipientMemory] and [appLockSetting] (no shared preferences file
 * between tests), a fake [deviceAuthenticator], a fake clock and whether a
 * password manager is set up.
 */
class AppContainer(
    context: Context,
    val endpoints: Endpoints = Endpoints.fromBuildConfig(),
    sessionStore: SessionStore? = null,
    recipientMemory: RecipientMemory? = null,
    pendingClaims: PendingClaims? = null,
    /** Background delivery of pending claims; null uses WorkManager. Tests pass a fake. */
    scheduleClaimDelivery: (() -> Unit)? = null,
    appLockSetting: AppLockSetting? = null,
    /** The system prompt; tests pass a fake. */
    deviceAuthenticator: DeviceAuthenticator? = null,
    /** Monotonic clock for app lock's background time. */
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** Any camera for the QR login; null asks the system. */
    hasCamera: Boolean? = null,
    /** A password manager set as autofill service; null asks the system (at every call). */
    hasPasswordManager: ((Context) -> Boolean)? = null,
    /** Lives as long as the process; for work that must outlast a screen. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val appContext = context.applicationContext

    /** Phones without a camera log in with the account number only (no scan entry). */
    val hasCamera: Boolean = hasCamera ?: appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /**
     * True while the user has a password manager (autofill service) set up; the
     * create-account screen offers "Save to password manager" only then. Pass
     * the activity: the application context has no autofill client, and its
     * `AutofillManager` reports disabled.
     */
    val hasPasswordManager: (Context) -> Boolean = hasPasswordManager ?: ::passwordManagerAvailable

    val httpClient: OkHttpClient by lazy { createHttpClient() }
    val preferences: DataStore<Preferences> = appContext.appPreferences

    /** Shared by every API client (Auth and AMS). */
    val apiTransport: ApiTransport by lazy { ApiTransport(httpClient) }
    val authApi: AuthApi by lazy { AuthApi(apiTransport, endpoints.authBase) }

    /** The recipient picked for new aliases; deleted whenever the session ends. */
    val recipientMemory: RecipientMemory = recipientMemory ?: DataStoreRecipientMemory(preferences)

    /** Copied aliases whose claim has not reached the server yet; deleted whenever the session ends. */
    val pendingClaims: PendingClaims = pendingClaims ?: DataStorePendingClaims(preferences)

    /** App lock on/off; reset whenever the session ends, so the next account starts with it off. */
    val appLockSetting: AppLockSetting = appLockSetting ?: DataStoreAppLockSetting(preferences)

    val sessionManager: SessionManager = SessionManager(
        authApi = authApi,
        store = sessionStore ?: EncryptedSessionStore(
            appContext.sessionDataStore,
            AesGcmTokenCipher(AndroidKeystoreKeySource())
        ),
        // Copied aliases only become active once claimed: try once more before the session goes.
        beforeLogout = { withTimeoutOrNull(LOGOUT_CLAIM_MILLIS) { claimDelivery.deliverAll() } },
        clearAccountData = {
            try {
                this.recipientMemory.clear()
                this.pendingClaims.clear()
            } finally {
                this.appLockSetting.clear()
            }
        }
    )

    /** [MainActivity] attaches itself, so the prompt has a window. */
    val deviceAuthenticator: DeviceAuthenticator = deviceAuthenticator ?: BiometricAuthenticator(appContext)

    val appLock: AppLock = AppLock(
        setting = this.appLockSetting,
        authenticator = this.deviceAuthenticator,
        sessionState = sessionManager.state,
        scope = appScope,
        logout = sessionManager::logout,
        clock = elapsedRealtime
    )

    val amsApi: AmsApi by lazy { AmsApi(apiTransport, endpoints.apiBase, sessionManager) }
    val claimDelivery: ClaimDelivery by lazy {
        ClaimDelivery(amsApi, this.pendingClaims, scheduleClaimDelivery ?: { ClaimWorker.schedule(appContext) })
    }
    val aliasClipboard: AliasClipboard by lazy { SystemAliasClipboard(appContext) }
    val webLinks: WebLinks by lazy { BrowserLinks(appContext) }

    val clipboard: SecretClipboard by lazy { SensitiveClipboard(appContext, appScope) }

    init {
        appScope.launch { sessionManager.restore() }
    }

    private companion object {
        /** How long a logout waits for copied aliases to be delivered. */
        const val LOGOUT_CLAIM_MILLIS = 10_000L
    }
}
