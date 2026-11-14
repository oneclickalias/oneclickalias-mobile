package com.oneclickalias.app.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** QR login: the camera is asked for, but never required to install the app. */
@RunWith(AndroidJUnit4::class)
class CameraManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_RECEIVERS
    )

    @Test
    fun `camera permission is requested`() {
        assertTrue(info.requestedPermissions!!.contains(Manifest.permission.CAMERA))
    }

    @Test
    fun `no camera feature is required`() {
        val camera = info.reqFeatures.orEmpty().filter { it.name?.startsWith("android.hardware.camera") == true }
        assertEquals(
            setOf("android.hardware.camera", "android.hardware.camera.autofocus"),
            camera.map { it.name }.toSet()
        )
        assertTrue(camera.all { it.flags and FeatureInfo.FLAG_REQUIRED == 0 })
    }

    @Test
    fun `the debug build's scan receiver is only reachable with DUMP, like the adb shell has`() {
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, DebugScanReceiver::class.java),
            0
        )
        assertEquals(Manifest.permission.DUMP, receiver.permission)
    }
}
