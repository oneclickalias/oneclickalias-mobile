package com.oneclickalias.app.platform

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AliasClipboardAndLinksTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard = app.getSystemService(ClipboardManager::class.java)

    @Test
    fun `alias clip holds exactly the address, not flagged sensitive`() {
        val systemConfirms = SystemAliasClipboard(app).copy("quiet-otter-1234@alias.test")

        val clip = clipboard.primaryClip!!
        assertEquals(1, clip.itemCount)
        assertEquals("quiet-otter-1234@alias.test", clip.getItemAt(0).text.toString())
        assertNull(clip.description.extras?.get("android.content.extra.IS_SENSITIVE"))
        // Default test SDK is above 33: the system shows its own confirmation.
        assertTrue(systemConfirms)
    }

    @Test
    @Config(sdk = [32])
    fun `before android 13 the app confirms the copy itself`() {
        assertFalse(SystemAliasClipboard(app).copy("quiet-otter-1234@alias.test"))
    }

    @Test
    fun `links open in the browser as a new task`() {
        val opened = BrowserLinks(app).open("https://oneclickalias.com/dashboard/billing".toHttpUrl())

        assertTrue(opened)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://oneclickalias.com/dashboard/billing", intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `no browser installed is reported, not thrown`() {
        shadowOf(app).checkActivities(true)

        assertFalse(BrowserLinks(app).open("https://oneclickalias.com/dashboard/billing".toHttpUrl()))
    }
}
