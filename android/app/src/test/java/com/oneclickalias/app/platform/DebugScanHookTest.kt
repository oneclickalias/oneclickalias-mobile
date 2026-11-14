package com.oneclickalias.app.platform

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The adb hook of the QR login exists in debug builds only. The JVM tests run
 * the debug variant (AGP creates no release unit tests), so the release side is
 * checked on the sources here and on the built APK by `assembleRelease`
 * (`checkReleaseHasNoScanHook` in app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class DebugScanHookTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a broadcast text reaches an open scanner`() {
        val texts = mutableListOf<String>()
        compose.setContent { ScanTextInjection(onText = { texts += it }) }
        compose.waitUntil(5_000) { DebugScans.texts.subscriptionCount.value > 0 }

        DebugScanReceiver().onReceive(
            ApplicationProvider.getApplicationContext<Context>(),
            Intent().putExtra(DebugScanReceiver.EXTRA_TEXT, "OCA-PAIR-1-X")
        )

        compose.waitUntil(5_000) { texts.isNotEmpty() }
        assertEquals(listOf("OCA-PAIR-1-X"), texts)
    }

    @Test
    fun `release sources have no way to inject a text`() {
        val src = File("src")
        val release = src.resolve("release").walkTopDown().filter { it.isFile }.toList()
        assertEquals(listOf("ScanTextInjection.kt"), release.map { it.name })
        val stub = release.single().readText()
        assertFalse(stub.contains("DebugScan"))
        assertTrue(stub.contains("fun ScanTextInjection(onText: (String) -> Unit) = Unit"))
        // Only src/debug knows the receiver; main merely calls ScanTextInjection.
        val outsideDebug = src.walkTopDown()
            .filter { it.isFile && !it.path.startsWith("src/debug/") && !it.path.startsWith("src/test/") }
            .filter { it.extension in setOf("kt", "xml") && it.readText().contains("DebugScan") }
            .toList()
        assertEquals(emptyList<File>(), outsideDebug)
    }
}
