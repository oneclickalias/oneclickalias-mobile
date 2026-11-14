package com.oneclickalias.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.oneclickalias.app.navigation.OcaNavHost
import com.oneclickalias.app.platform.BiometricAuthenticator
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * The only activity. A [FragmentActivity] (still a `ComponentActivity`, no
 * AppCompat theme) because AndroidX Biometric shows its prompt through a
 * fragment of the activity.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Screens paint their own flat background behind both system bars.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No translucent scrim behind three-button navigation.
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        val container = (application as OcaApplication).container
        (container.deviceAuthenticator as? BiometricAuthenticator)?.attach(this)
        setContent {
            OcaTheme { OcaNavHost(container = container) }
        }
    }
}
