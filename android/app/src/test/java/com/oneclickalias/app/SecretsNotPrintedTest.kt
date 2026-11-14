package com.oneclickalias.app

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.data.session.SignOutNotice
import com.oneclickalias.app.ui.devices.DeviceSignOut
import com.oneclickalias.app.ui.devices.DevicesUiState
import com.oneclickalias.app.ui.screens.AccountUiState
import com.oneclickalias.app.ui.screens.CreateAccountUiState
import com.oneclickalias.app.ui.screens.LoginUiState
import com.oneclickalias.app.ui.screens.NumberConfirmUiState
import com.oneclickalias.contracts.auth.Device
import com.oneclickalias.contracts.auth.LoginResponse
import com.oneclickalias.contracts.auth.RefreshResponse
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Every state or session object that could end up in a log line, crash report
 * or test output must print without the account number or any token.
 */
class SecretsNotPrintedTest {
    private val number = "4711000012345678"
    private val access = "eyJhbGciOiJFZERTQSJ9.access"
    private val refresh = "rft_refresh-token"
    private val device = "9315bf6d-7672-44db-9c09-2dd3212c9b0a"
    private val secrets = listOf(number, number.take(8), number.takeLast(8), access, refresh, device)

    private val printable: List<Any> = listOf(
        Session(access, refresh, device, 1L, 2L),
        SessionState.Loading,
        SessionState.SignedIn,
        SessionState.SignedOut(SignOutNotice.SessionEnded),
        CreateAccountUiState.Ready(accountNumber = number, saved = true, copied = true, submitting = true),
        CreateAccountUiState.Interrupted,
        LoginUiState(digits = number, revealed = true, needsConfirmation = true, confirmed = true),
        AccountUiState(loggingOut = true),
        NumberConfirmUiState(digits = number, revealed = true, submitting = true),
        DevicesUiState(
            devices = listOf(Device(device, "brave otter", "2026-11-05T10:00:00Z", current = true)),
            signingOut = DeviceSignOut(Device(device, "brave otter", "2026-11-05T10:00:00Z", current = true))
        ),
        SessionState.SignedOut(SignOutNotice.AccountDeletionRequested),
        ApiResult.Success(LoginResponse(access, refresh, 900, device)),
        ApiResult.Success(RefreshResponse(access, refresh, 900)),
        ApiResult.Success(number)
    )

    @Test
    fun `no secret appears in toString`() {
        for (value in printable) {
            val text = value.toString()
            for (secret in secrets) {
                assertFalse("${value::class.simpleName} prints a secret: $text", text.contains(secret))
            }
        }
    }

    @Test
    fun `copies keep redacting`() {
        val ready = CreateAccountUiState.Ready(accountNumber = number).copy(saved = true)
        val login = LoginUiState().copy(digits = number)

        assertFalse(ready.toString().contains(number))
        assertFalse(login.toString().contains(number))
        assertFalse(Session(access, refresh, device, 1L, 2L).copy(accessToken = "x").toString().contains(refresh))
    }
}
