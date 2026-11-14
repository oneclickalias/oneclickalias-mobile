package com.oneclickalias.app.platform

import android.content.Context
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class PasswordManagerSaveTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val number = "4711000012345678"
    private lateinit var save: PasswordManagerSave

    private fun autofill(context: Context = ApplicationProvider.getApplicationContext()) =
        shadowOf(context.getSystemService(AutofillManager::class.java))

    @After
    fun tearDown() {
        autofill().setEnabled(false)
    }

    private fun showField() {
        compose.setContent {
            save = rememberPasswordManagerSave()
            PasswordManagerSaveField(save)
        }
        compose.waitForIdle()
    }

    @Test
    fun `available only with an enabled autofill service`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        autofill(context).setAutofillSupported(true)

        autofill(context).setEnabled(false)
        assertFalse(passwordManagerAvailable(context))

        autofill(context).setEnabled(true)
        assertTrue(passwordManagerAvailable(context))

        autofill(context).setAutofillSupported(false)
        assertFalse(passwordManagerAvailable(context))
    }

    @Test
    fun `the hidden field is an empty password field only autofill sees`() {
        showField()
        val field = requireNotNull(save.field)

        // Newer platforms add their own hint ("passwordAuto") for password input types.
        assertTrue(field.autofillHints.orEmpty().contains(View.AUTOFILL_HINT_PASSWORD))
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, field.importantForAutofill)
        assertEquals(
            InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            field.inputType and InputType.TYPE_MASK_VARIATION
        )
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, field.importantForAccessibility)
        assertFalse(field.isFocusable)
        assertEquals("", field.text.toString())
    }

    @Test
    fun `save puts the number in only for the commit, then empties the field`() {
        autofill().setEnabled(true)
        showField()
        val field = requireNotNull(save.field)
        val values = mutableListOf<String>()
        compose.runOnUiThread {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    values += s.toString()
                }
            })
            save.save(number)
        }
        // Empty while the service looks at the field, so the number counts as changed.
        assertEquals("", field.text.toString())

        // No answer from the service (Robolectric has none): the timeout commits.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PasswordManagerSave.RESPONSE_TIMEOUT_MILLIS))

        // (Clearing an already empty field may report "" first.)
        assertEquals(listOf(number, ""), values.dropWhile { it.isEmpty() })
        assertEquals("", field.text.toString())
    }

    @Test
    fun `the number is never logged or printed`() {
        autofill().setEnabled(true)
        showField()
        ShadowLog.clear()

        compose.runOnUiThread { save.save(number) }
        assertFalse(save.toString().contains(number))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PasswordManagerSave.RESPONSE_TIMEOUT_MILLIS))

        val logged = ShadowLog.getLogs().filter {
            (it.msg ?: "").contains(number) ||
                (it.msg ?: "").contains("4711 0000")
        }
        assertEquals(emptyList<ShadowLog.LogItem>(), logged)
        assertFalse(save.toString().contains(number))
    }

    @Test
    fun `leaving the screen drops the field`() {
        var shown by mutableStateOf(true)
        compose.setContent {
            save = rememberPasswordManagerSave()
            if (shown) PasswordManagerSaveField(save)
        }
        compose.waitForIdle()
        assertTrue(save.field != null)

        shown = false
        compose.waitForIdle()

        assertNull(save.field)
    }
}
