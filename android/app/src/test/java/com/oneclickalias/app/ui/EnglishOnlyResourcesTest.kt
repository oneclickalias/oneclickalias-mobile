package com.oneclickalias.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The UI is English on every device. Library
 * strings (e.g. Material's sheet descriptions for screen readers) must not
 * switch to the system language.
 */
@RunWith(AndroidJUnit4::class)
class EnglishOnlyResourcesTest {
    @Test
    @Config(qualifiers = "de")
    fun `material strings stay english on a german device`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            "Drag handle",
            context.getString(androidx.compose.material3.R.string.m3c_bottom_sheet_drag_handle_description)
        )
    }
}
