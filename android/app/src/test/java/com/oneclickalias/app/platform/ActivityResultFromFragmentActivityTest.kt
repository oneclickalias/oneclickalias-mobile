package com.oneclickalias.app.platform

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * `MainActivity` is a `FragmentActivity` (for Biometric). Biometric 1.1.0 asks
 * for `androidx.fragment` 1.2.5, whose `FragmentActivity` rejects the request
 * codes of the Activity Result API ("Can only use lower 16 bits for
 * requestCode"). The build therefore uses a current `androidx.fragment`; this
 * test fails if it falls back to the old one.
 */
@RunWith(AndroidJUnit4::class)
class ActivityResultFromFragmentActivityTest {
    @Test
    fun `the activity result api launches from a fragment activity`() {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val launcher = activity.activityResultRegistry.register(
                "test",
                ActivityResultContracts.StartActivityForResult()
            ) {}

            launcher.launch(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()))
        }
    }
}
