package com.oneclickalias.app.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.oneclickalias.app.ui.pairing.CameraAccess

/** The camera permission as [CameraAccess], plus the request for it (system dialog). */
class CameraPermission(val access: CameraAccess, val request: () -> Unit)

/**
 * Camera permission through the Activity Result API. Nothing is asked until
 * [CameraPermission.request] runs (a tap). After a refusal the system says
 * whether it may ask again; if not, only the app settings can change it
 * ([CameraAccess.Blocked]). Coming back to the screen (e.g. from the
 * settings) checks the grant again.
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var access by remember {
        mutableStateOf(if (context.hasCameraPermission()) CameraAccess.Granted else CameraAccess.NotAsked)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        access = when {
            granted -> CameraAccess.Granted

            activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                CameraAccess.Denied

            else -> CameraAccess.Blocked
        }
    }
    LifecycleResumeEffect(context) {
        if (context.hasCameraPermission()) access = CameraAccess.Granted
        onPauseOrDispose { }
    }
    return CameraPermission(access) { launcher.launch(Manifest.permission.CAMERA) }
}

/** The system's settings page of this app (permissions). False if no settings app handles it. */
fun openAppSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
