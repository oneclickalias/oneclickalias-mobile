package com.oneclickalias.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.data.aliases.RecipientDecision
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.aliases.AliasesCallbacks
import com.oneclickalias.app.ui.aliases.AliasesScreen
import com.oneclickalias.app.ui.aliases.AliasesUiState
import com.oneclickalias.app.ui.aliases.CopyCallbacks
import com.oneclickalias.app.ui.aliases.CopyCard
import com.oneclickalias.app.ui.aliases.CopyScreen
import com.oneclickalias.app.ui.aliases.CopyUiState
import com.oneclickalias.app.ui.aliases.HomeFrame
import com.oneclickalias.app.ui.aliases.HomeTab
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.lock.LockScreen
import com.oneclickalias.app.ui.pairing.CameraAccess
import com.oneclickalias.app.ui.pairing.PairingCallbacks
import com.oneclickalias.app.ui.pairing.PairingMessages
import com.oneclickalias.app.ui.pairing.PairingScreen
import com.oneclickalias.app.ui.pairing.PairingUiState
import com.oneclickalias.app.ui.recipients.RecipientsCallbacks
import com.oneclickalias.app.ui.recipients.RecipientsScreen
import com.oneclickalias.app.ui.recipients.RecipientsUiState
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.Recipient
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens to PNG files in `app/build/reports/screens/`, for states a
 * device cannot show or capture: the create-account, lock and account screens
 * with app lock on (`FLAG_SECURE`) and
 * the payment notices on the alias and recipient screens (local test accounts
 * are always paid), and the QR scanner with a neutral grey square instead of
 * the camera (device screenshots would show whatever the camera sees).
 * Phone-sized like a
 * Galaxy A22 (384 x 856 dp).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun renderCreateAccount(name: String, darkTheme: Boolean) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            CreateAccountScreen(
                state = CreateAccountUiState.Ready(accountNumber = "4711000012345678", saved = true),
                onBack = {},
                onRetry = {},
                onCopy = {},
                onSavedChange = {},
                onSubmit = {},
                onLogIn = {},
                onStartOver = {},
                onSaveToPasswordManager = {}
            )
        }
    }

    private fun renderPaymentNotice(name: String, darkTheme: Boolean) = render(name) {
        val home = Recipient("r-home", "home@example.com", true, "2026-11-01T10:00:00Z")
        val aliases = listOf("quiet-otter-4821", "brave-falcon-1907", "gentle-river-3310").mapIndexed { i, word ->
            Alias("a$i", "$word@alias.oneclickalias.com", home.email, home.id, true, "", "")
        }
        OcaTheme(darkTheme = darkTheme) {
            AliasesScreen(
                state = AliasesUiState(
                    aliases = aliases,
                    list = ListStatus.Ready,
                    paymentRequired = true
                ),
                callbacks = AliasesCallbacks()
            )
        }
    }

    private fun renderRecipientsPaymentNotice(name: String, darkTheme: Boolean) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            RecipientsScreen(
                state = RecipientsUiState(
                    recipients = listOf(
                        Recipient("r-home", "home@example.com", true, "2026-11-01T10:00:00Z"),
                        Recipient("r-work", "work@example.com", false, "2026-11-02T10:00:00Z")
                    ),
                    list = ListStatus.Ready,
                    paymentRequired = true
                ),
                callbacks = RecipientsCallbacks()
            )
        }
    }

    private fun renderLock(name: String, darkTheme: Boolean, canAuthenticate: Boolean = true) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            LockScreen(
                state = AppLockState(ready = true, enabled = true, locked = true, canAuthenticate = canAuthenticate),
                onUnlock = {},
                onLogOut = {}
            )
        }
    }

    private fun renderAccountWithAppLock(name: String, darkTheme: Boolean) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            AccountScreen(
                state = AccountUiState(),
                versionName = "0.1.0",
                onLogout = {},
                appLock = AppLockState(ready = true, enabled = true, canAuthenticate = true)
            )
        }
    }

    private fun renderNewAccountPaymentNotice(name: String, darkTheme: Boolean) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            AliasesScreen(
                state = AliasesUiState(
                    list = ListStatus.Ready,
                    paymentRequired = true
                ),
                callbacks = AliasesCallbacks()
            )
        }
    }

    private fun renderPairing(
        name: String,
        darkTheme: Boolean,
        state: PairingUiState,
        camera: CameraAccess = CameraAccess.Granted
    ) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            PairingScreen(
                state = state,
                camera = camera,
                callbacks = PairingCallbacks(),
                cameraPreview = { _, modifier -> Box(modifier.background(Color(0xFF808080))) }
            )
        }
    }

    private fun renderLogin(name: String, darkTheme: Boolean, state: LoginUiState = LoginUiState()) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            LoginScreen(
                state = state,
                onInput = {},
                onToggleReveal = {},
                onConfirmedChange = {},
                onSubmit = {},
                onCreateAccount = {},
                onScanCode = {}
            )
        }
    }

    private val inboxes = listOf("home@example.com", "work@example.com", "shop@example.com").mapIndexed { i, mail ->
        Recipient("r$i", mail, true, "2026-11-01T10:00:00Z")
    }

    private fun alias(n: Int, name: String, to: Recipient) =
        Alias("a$n", "$name@rmails.net", to.email, to.id, true, "", "")

    private fun renderNewAlias(name: String, darkTheme: Boolean, cards: Int) = render(name) {
        val names = listOf("quiet-otter-4821", "brave-falcon-1907", "gentle-river-3310")
        OcaTheme(darkTheme = darkTheme) {
            HomeFrame(tab = HomeTab.NewAlias, onSelectTab = {}) {
                CopyScreen(
                    state = CopyUiState(
                        cards = inboxes.take(cards).mapIndexed { i, inbox ->
                            CopyCard(inbox, alias = alias(i, names[i], inbox))
                        },
                        list = ListStatus.Ready
                    ),
                    callbacks = CopyCallbacks()
                )
            }
        }
    }

    private fun renderAliases(name: String, darkTheme: Boolean) = render(name) {
        val names = listOf(
            "flogging-canon-2062",
            "steed-musky-7169",
            "urethane-amazingly-3822",
            "sapling-mammary-8651",
            "blurb-oven-8962",
            "starry-polyester-9286",
            "psychic-rewrite-3039",
            "steadying-cube-8885"
        )
        OcaTheme(darkTheme = darkTheme) {
            HomeFrame(tab = HomeTab.Aliases, onSelectTab = {}) {
                AliasesScreen(
                    state = AliasesUiState(
                        aliases = names.mapIndexed { i, word -> alias(i, word, inboxes[if (i < 2) 1 else 0]) },
                        list = ListStatus.Ready
                    ),
                    callbacks = AliasesCallbacks()
                )
            }
        }
    }

    private fun renderAccountTab(name: String, darkTheme: Boolean) = render(name) {
        OcaTheme(darkTheme = darkTheme) {
            HomeFrame(tab = HomeTab.Account, onSelectTab = {}) {
                AccountScreen(state = AccountUiState(), versionName = "1.0.0", onLogout = {}, onSignInBrowser = {})
            }
        }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/reports/screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
        // Not a blank page: light and dark pixels both exist.
        val pixels = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
        assertTrue(pixels.distinct().size > 10)
    }

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `create account light`() = renderCreateAccount("create-account-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `create account dark`() = renderCreateAccount("create-account-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `payment notice light`() = renderPaymentNotice("payment-notice-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `payment notice dark`() = renderPaymentNotice("payment-notice-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `recipients payment notice light`() =
        renderRecipientsPaymentNotice("recipients-payment-notice-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `recipients payment notice dark`() =
        renderRecipientsPaymentNotice("recipients-payment-notice-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `lock screen light`() = renderLock("lock-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `lock screen dark`() = renderLock("lock-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `lock screen without screen lock light`() =
        renderLock("lock-no-screen-lock-light", darkTheme = false, canAuthenticate = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `lock screen without screen lock dark`() =
        renderLock("lock-no-screen-lock-dark", darkTheme = true, canAuthenticate = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `account with app lock on light`() = renderAccountWithAppLock("account-app-lock-on-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `account with app lock on dark`() = renderAccountWithAppLock("account-app-lock-on-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `new account payment notice light`() =
        renderNewAccountPaymentNotice("payment-notice-new-account-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `qr scanner light`() = renderPairing(
        "qr-scanner-light",
        darkTheme = false,
        state = PairingUiState.Scanning(problem = PairingMessages.NOT_OURS)
    )

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `qr camera permission light`() = renderPairing(
        "qr-permission-light",
        darkTheme = false,
        state = PairingUiState.Scanning(),
        camera = CameraAccess.NotAsked
    )

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `qr waiting light`() =
        renderPairing("qr-waiting-light", darkTheme = false, state = PairingUiState.Waiting("Casual Hippo"))

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `new account payment notice dark`() =
        renderNewAccountPaymentNotice("payment-notice-new-account-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `qr scanner dark`() = renderPairing(
        "qr-scanner-dark",
        darkTheme = true,
        state = PairingUiState.Scanning(problem = PairingMessages.NOT_OURS)
    )

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `qr camera permission dark`() = renderPairing(
        "qr-permission-dark",
        darkTheme = true,
        state = PairingUiState.Scanning(),
        camera = CameraAccess.NotAsked
    )

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `qr waiting dark`() =
        renderPairing("qr-waiting-dark", darkTheme = true, state = PairingUiState.Waiting("Casual Hippo"))

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `login light`() = renderLogin("login-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `login dark`() = renderLogin("login-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h480dp-night-xxhdpi")
    fun `login with the keyboard open dark`() = renderLogin(
        "login-keyboard-dark",
        darkTheme = true,
        state = LoginUiState(digits = "4711000012345678", error = AuthMessages.WRONG_NUMBER)
    )

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `new alias light`() = renderNewAlias("new-alias-light", darkTheme = false, cards = 2)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `new alias dark`() = renderNewAlias("new-alias-dark", darkTheme = true, cards = 2)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `new alias with one card dark`() = renderNewAlias("new-alias-one-dark", darkTheme = true, cards = 1)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `new alias with three cards dark`() = renderNewAlias("new-alias-three-dark", darkTheme = true, cards = 3)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `aliases light`() = renderAliases("aliases-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `aliases dark`() = renderAliases("aliases-dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `account tab light`() = renderAccountTab("account-tab-light", darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `account tab dark`() = renderAccountTab("account-tab-dark", darkTheme = true)
}
