package com.oneclickalias.app.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.R
import com.oneclickalias.app.data.lock.AuthPurpose
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowFingerprintManager

/**
 * The real prompt on Android 8.1 (API 27), where the library cannot use the
 * system's BiometricPrompt and starts the screen-lock check itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [27])
class BiometricAuthenticatorTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun screenLock(secure: Boolean, fingerprints: Int = 0) {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(secure)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_FINGERPRINT, fingerprints > 0)
        // FingerprintManager is no longer in the compile SDK; reach its shadow by service name.
        val fingerprint = Shadow.extract<ShadowFingerprintManager>(context.getSystemService("fingerprint"))
        fingerprint.setIsHardwareDetected(fingerprints > 0)
        fingerprint.setDefaultFingerprints(fingerprints)
    }

    private fun activity(): FragmentActivity {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.Theme_OneClickAlias)
        return controller.setup().get()
    }

    @Test
    fun `without a screen lock the prompt is not available`() {
        screenLock(secure = false)

        assertFalse(BiometricAuthenticator(context).canAuthenticate())
    }

    @Test
    fun `a pin, pattern or password is enough`() {
        screenLock(secure = true)

        assertTrue(BiometricAuthenticator(context).canAuthenticate())
    }

    @Test
    fun `on android 8 the screen lock check opens and its success confirms`() {
        // androidx.core 1.19 reports no fingerprint hardware below API 29 (FingerprintManager left
        // the SDK), so the library skips its own fingerprint dialog and asks for the screen lock.
        screenLock(secure = true, fingerprints = 1)
        val activity = activity()
        val authenticator = BiometricAuthenticator(context).apply { attach(activity) }

        val result = scope.async { authenticator.authenticate(AuthPurpose.Unlock) }
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val started = shadowOf(activity).nextStartedActivityForResult

        assertEquals("android.app.action.CONFIRM_DEVICE_CREDENTIAL", started.intent.action)
        assertNull(ShadowDialog.getLatestDialog())
        assertTrue(result.isActive)

        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(result.isCompleted)
        @OptIn(ExperimentalCoroutinesApi::class)
        assertTrue(result.getCompleted())
    }

    @Test
    fun `cancelling the screen lock check is not a confirmation`() {
        screenLock(secure = true)
        val activity = activity()
        val authenticator = BiometricAuthenticator(context).apply { attach(activity) }

        val result = scope.async { authenticator.authenticate(AuthPurpose.Enable) }
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val started = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(result.isCompleted)
        @OptIn(ExperimentalCoroutinesApi::class)
        assertFalse(result.getCompleted())
    }

    @Test
    fun `without an activity the prompt reports not confirmed`() {
        screenLock(secure = true)
        val result = scope.async { BiometricAuthenticator(context).authenticate(AuthPurpose.Unlock) }
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(result.isCompleted)
        @OptIn(ExperimentalCoroutinesApi::class)
        assertFalse(result.getCompleted())
    }
}
