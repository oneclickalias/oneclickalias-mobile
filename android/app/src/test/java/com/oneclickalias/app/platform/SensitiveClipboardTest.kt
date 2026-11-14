package com.oneclickalias.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveClipboardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val scope = TestScope()
    private val clipboard = SensitiveClipboard(context, scope, clearAfterMillis = 60_000)

    private fun currentText(): String? = manager.primaryClip?.takeIf {
        it.itemCount > 0
    }?.getItemAt(0)?.text?.toString()

    @Test
    fun `copy puts the text on the clipboard marked as sensitive`() {
        clipboard.copy("Account number", "1234567890123456")

        assertEquals("1234567890123456", currentText())
        val extras = manager.primaryClipDescription!!.extras!!
        assertTrue(extras.getBoolean("android.content.extra.IS_SENSITIVE"))
        assertEquals("Account number", manager.primaryClipDescription!!.label)
    }

    @Test
    fun `clip is cleared after the timeout`() {
        clipboard.copy("Account number", "1234567890123456")

        scope.advanceTimeBy(59_000)
        scope.runCurrent()
        assertEquals("1234567890123456", currentText())

        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertTrue(currentText().isNullOrEmpty())
    }

    @Test
    fun `something the user copied later is left alone`() {
        clipboard.copy("Account number", "1234567890123456")
        manager.setPrimaryClip(ClipData.newPlainText("note", "shopping list"))

        scope.advanceTimeBy(61_000)
        scope.runCurrent()

        assertEquals("shopping list", currentText())
    }

    @Test
    fun `copying again restarts the timer`() {
        clipboard.copy("Account number", "1234567890123456")
        scope.advanceTimeBy(40_000)
        clipboard.copy("Account number", "1234567890123456")

        scope.advanceTimeBy(40_000)
        scope.runCurrent()
        assertEquals("1234567890123456", currentText())

        scope.advanceTimeBy(21_000)
        scope.runCurrent()
        assertTrue(currentText().isNullOrEmpty())
    }
}
